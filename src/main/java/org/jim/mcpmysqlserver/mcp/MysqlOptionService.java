package org.jim.mcpmysqlserver.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jim.mcpmysqlserver.config.ToolResponseLimitConfig;
import org.jim.mcpmysqlserver.config.extension.Extension;
import org.jim.mcpmysqlserver.config.extension.GroovyService;
import org.jim.mcpmysqlserver.service.DataSourceService;
import org.jim.mcpmysqlserver.service.JdbcExecutor;
import org.jim.mcpmysqlserver.service.SqlResultCacheService;
import org.jim.mcpmysqlserver.service.TruncatedResultSnapshot;
import org.jim.mcpmysqlserver.validator.SqlSecurityValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 数据库操作服务，负责执行 SQL tool 并在结果过大时提供截断与续取能力。
 *
 * <p>线程安全性：实例由 Spring 单例管理，内部只持有线程安全执行器与缓存服务引用。</p>
 *
 * @author yangxin
 */
@Service
@Slf4j
public class MysqlOptionService {

    private final DataSourceService dataSourceService;
    private final ToolResponseLimitConfig toolResponseLimitConfig;
    private final SqlResultCacheService sqlResultCacheService;
    private final ObjectMapper objectMapper;
    private final SqlSecurityValidator sqlSecurityValidator;
    private final JdbcExecutor jdbcExecutor;
    private final ExecutorService executorService;

    @Resource
    private GroovyService groovyService;

    public MysqlOptionService(DataSourceService dataSourceService,
                              ToolResponseLimitConfig toolResponseLimitConfig,
                              SqlResultCacheService sqlResultCacheService,
                              SqlSecurityValidator sqlSecurityValidator,
                              JdbcExecutor jdbcExecutor) {
        this.dataSourceService = dataSourceService;
        this.toolResponseLimitConfig = toolResponseLimitConfig;
        this.sqlResultCacheService = sqlResultCacheService;
        this.sqlSecurityValidator = sqlSecurityValidator;
        this.jdbcExecutor = jdbcExecutor;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // Java 21 虚拟线程用于隔离多数据源 I/O，避免单个慢查询拖住其他数据源。
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        log.info("DatabaseOptionService 初始化完成，使用虚拟线程执行器");
    }

    @PreDestroy
    public void destroy() {
        log.info("关闭虚拟线程执行器");
        executorService.close();
    }

    /**
     * 在所有可用数据源上执行同一条 SQL，并对超长结果做统一截断包装。
     *
     * @param sql 要执行的 SQL 语句
     * @return 所有成功数据源的查询结果，或错误信息
     */
    @Tool(description = """
            Purpose: Run the same SQL on every configured datasource.

            Use This Tool:
            - When the user wants to compare results across multiple datasources
            - When the user explicitly asks for all datasources

            Do Not Use This Tool:
            - When the user already specified a datasource name
            - When only the default datasource is needed

            Input Rules:
            - SQL must match the target database dialect
            - Mutating SQL may be blocked when sql.security.enabled=true

            Return Rules:
            - Normal result: each datasource returns its result directly
            - Large result: returns truncated=true, resultId, preview, nextOffset, hasMore
            - To continue reading a truncated result, call fetchSqlResultPage(resultId, nextOffset, maxChars)
            """)
    public Map<String, Object> executeSql(@ToolParam(description = """
            Valid SQL statement compatible with target database dialect
            Examples:
            - MySQL/PostgreSQL: SELECT id, name FROM users WHERE status = 'active'
            - SQL Server: SELECT id, name FROM users WHERE status = 'active'
            - Oracle: SELECT id, name FROM users WHERE status = 'active' AND ROWNUM <= 10
            """) String sql) {
        log.info("Executing SQL on all available datasources: {}", sql);

        Map<String, Object> errorResult = validateSqlAndGetErrorResult(sql);
        if (errorResult != null) {
            return errorResult;
        }

        List<String> dataSourceNames = dataSourceService.getDataSourceNames();
        log.info("Found {} available datasources", dataSourceNames.size());

        Map<String, Object> successResults = new ConcurrentHashMap<>();
        try {
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(dataSourceNames.stream()
                    .map(dsName -> CompletableFuture.runAsync(() -> executeSqlOnNamedDatasource(dsName, sql, successResults), executorService))
                    .toArray(CompletableFuture[]::new));
            allFutures.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            log.error("执行全数据源 SQL 失败，原因={}", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("执行全数据源 SQL 被中断，原因={}", e.getMessage(), e);
        } catch (TimeoutException e) {
            log.error("执行全数据源 SQL 超时，原因={}", e.getMessage(), e);
        }
        return successResults;
    }

    /**
     * 获取所有可用的数据源名称和数据库类型信息。
     *
     * @return 数据源名称列表、默认数据源名称和每个数据源的数据库类型
     */
    @Tool(description = """
            Purpose: Lists all available datasource names with database type information

            Alternative: Use mcp://datasources/config resource for the same information

            Returns:
            - JSON with 'datasources' array containing datasource details
            - 'default' datasource name
            - Database type for each datasource for optimal SQL syntax selection

            Usage:
            - Use before executeSqlWithDataSource to identify available datasources
            - Not required when using executeSqlOnDefault
            - Provides same information as mcp://datasources/config resource
            """)
    public Map<String, Object> listDataSources() {
        log.info("Listing all available datasources with database type information");

        List<Map<String, Object>> dataSourceDetails = dataSourceService.getDataSourceDetails();
        Map<String, Object> result = new HashMap<>();
        result.put("datasources", dataSourceDetails);
        result.put("default", dataSourceService.getDefaultDataSourceName());

        log.info("返回数据源信息，总数据源数量={}", dataSourceDetails.size());
        return result;
    }

    /**
     * 在默认数据源上执行 SQL，并沿用单数据源查询的截断语义。
     *
     * @param sql 要执行的 SQL 语句
     * @return 默认数据源查询结果
     */
    @Tool(description = """
            Purpose: Run SQL on the default datasource only.

            Use This Tool:
            - When the user did not specify a datasource
            - When only the default datasource should be queried

            Do Not Use This Tool:
            - When the user explicitly named a datasource
            - When the user wants to compare multiple datasources

            Input Rules:
            - SQL must match the default datasource dialect
            - Mutating SQL may be blocked when sql.security.enabled=true

            Return Rules:
            - Normal result: returns the query result directly
            - Empty result: returns "No data returned from SQL query"
            - Large result: returns truncated=true, resultId, preview, nextOffset, hasMore
            - To continue reading a truncated result, call fetchSqlResultPage(resultId, nextOffset, maxChars)
            """)
    public JsonNode executeSqlOnDefault(@ToolParam(description = """
            Valid SQL statement compatible with default datasource dialect
            Examples:
            - MySQL/PostgreSQL: SELECT * FROM users LIMIT 10
            - SQL Server: SELECT TOP 10 * FROM users
            - Oracle: SELECT * FROM users WHERE ROWNUM <= 10
            """) String sql) {
        log.info("Executing SQL on default datasource: {}", sql);

        Map<String, Object> errorResult = validateSqlAndGetErrorResult(sql);
        if (errorResult != null) {
            return objectMapper.valueToTree(errorResult);
        }

        String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();
        if (StringUtils.isBlank(defaultDataSourceName)) {
            Map<String, Object> result = new HashMap<>();
            result.put("error", "No default datasource configured");
            return objectMapper.valueToTree(result);
        }

        Map<String, Object> result = executeSqlWithDataSource(defaultDataSourceName, sql);
        if (CollectionUtils.isEmpty(result)) {
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("message", "No data returned from SQL query");
            return objectMapper.valueToTree(emptyResult);
        }
        return objectMapper.valueToTree(result.get(defaultDataSourceName));
    }

    /**
     * 在指定数据源上执行 SQL，并在结果超长时返回预览窗口与 resultId。
     *
     * @param dataSourceName 数据源名称
     * @param sql 要执行的 SQL 语句
     * @return 指定数据源的查询结果
     */
    @Tool(description = """
            Purpose: Run SQL on one specified datasource.

            Use This Tool:
            - When the user explicitly named a datasource
            - When you already know which datasource should be queried

            Input Rules:
            - datasourceName must come from listDataSources()
            - SQL must match that datasource dialect
            - Mutating SQL may be blocked when sql.security.enabled=true

            Return Rules:
            - Normal result: returns {datasourceName: result}
            - Large result: returns truncated=true, resultId, preview, nextOffset, hasMore
            - To continue reading a truncated result, call fetchSqlResultPage(resultId, nextOffset, maxChars)
            - If datasourceName is invalid, returns an error
            """)
    public Map<String, Object> executeSqlWithDataSource(@ToolParam(description = """
            Target datasource name. Must match a name returned by listDataSources()
            """) String dataSourceName,
                                                        @ToolParam(description = """
            Valid SQL statement compatible with target datasource dialect
            Examples:
            - MySQL/PostgreSQL: SELECT * FROM users LIMIT 10
            - SQL Server: SELECT TOP 10 * FROM users
            - Oracle: SELECT * FROM users WHERE ROWNUM <= 10
            """) String sql) {
        log.info("Executing SQL on datasource [{}]: {}", dataSourceName, sql);

        Map<String, Object> errorResult = validateSqlAndGetErrorResult(sql);
        if (errorResult != null) {
            return errorResult;
        }

        Map<String, Object> result = new HashMap<>();
        DataSource targetDataSource = dataSourceService.getDataSource(dataSourceName);
        if (targetDataSource == null) {
            String errorMsg = "Datasource [" + dataSourceName + "] not found";
            log.error(errorMsg);
            result.put(dataSourceName, errorMsg);
            return result;
        }

        JdbcExecutor.SqlResult sqlResult = jdbcExecutor.executeSql(targetDataSource, sql);
        if (sqlResult.success()) {
            result.put(dataSourceName, limitSqlToolResult(dataSourceName, sqlResult.data()));
            log.info("executeSqlWithDataSource Query executed successfully on datasource [{}]", dataSourceName);
            return result;
        }

        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("error", sqlResult.errorMessage());
        errorInfo.put("success", false);
        result.put(dataSourceName, errorInfo);
        log.error("executeSqlWithDataSource SQL execution error on datasource [{}]: {}", dataSourceName, sqlResult.errorMessage());
        return result;
    }

    /**
     * 续取被截断的大结果窗口，保证分页读取来自同一份缓存快照。
     *
     * @param resultId 截断结果标识
     * @param offset 起始偏移
     * @param maxChars 本次读取字符预算
     * @return 当前分页结果或错误信息
     */
    @Tool(description = """
            Purpose: Read the next window from a previously truncated SQL result.

            Use This Tool:
            - Only after another SQL tool returned truncated=true

            Input Rules:
            - resultId must come from the earlier truncated response
            - offset should usually use the previous nextOffset
            - maxChars cannot exceed the server-side response limit

            Return Rules:
            - page contains the current row window
            - nextOffset is the row offset for the next read
            - hasMore=false means the cached result has been fully read
            - If resultId is missing or expired, returns an error
            """)
    public Map<String, Object> fetchSqlResultPage(@ToolParam(description = """
            Result identifier returned by a previous truncated SQL tool response
            """) String resultId,
                                                  @ToolParam(description = """
            Zero-based row offset to start reading from
            """) Integer offset,
                                                  @ToolParam(description = """
            Optional maximum character budget for this page; values above the server limit will be clamped
            """) Integer maxChars) {
        if (StringUtils.isBlank(resultId)) {
            return buildErrorResponse("resultId is required", null);
        }
        if (offset == null || offset < 0) {
            return buildErrorResponse("offset must be greater than or equal to 0", resultId);
        }

        int pageMaxChars;
        try {
            pageMaxChars = resolvePageMaxChars(maxChars);
        } catch (IllegalArgumentException e) {
            return buildErrorResponse(e.getMessage(), resultId);
        }

        return sqlResultCacheService.get(resultId)
                .map(snapshot -> buildPageResponse(snapshot, offset, pageMaxChars))
                .orElseGet(() -> buildErrorResponse("Result not found or expired", resultId));
    }

    /**
     * 通过扩展名称执行 Groovy 脚本，处理传入的任意字符串。
     *
     * @param extensionName 扩展名称
     * @param input 输入字符串
     * @return 处理后的 JSON 结果
     */
    @Tool(description = """
            Purpose: Process input text using a named Groovy script extension

            Prerequisites:
            - MUST call getAllExtensions() first to identify available extensions

            Use Cases:
            - Decrypt encrypted data (e.g., SM4, AES)
            - Decode encoded data (e.g., Base64, zstd compression)
            - Transform data formats (e.g., JSON parsing, timestamp conversion)
            - Any custom data processing logic

            Returns:
            - JsonNode containing processed result
            - Error message if extension not found or processing fails
            """, returnDirect = true)
    public JsonNode executeGroovyScript(@ToolParam(description = """
            Extension name (MUST match a name from getAllExtensions() response)
            """) String extensionName,
                                        @ToolParam(description = """
            Input text to be processed by the Groovy script
            CRITICAL: This parameter is extremely important - input it carefully without missing any details
            """) String input) {
        Object result = groovyService.executeGroovyScript(extensionName, input);
        if (result instanceof String resultText) {
            try {
                return objectMapper.readTree(resultText);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse Groovy script result as JSON: {}", e.getMessage(), e);
                return objectMapper.createObjectNode().put("error", "Invalid JSON result from Groovy script");
            }
        }
        return objectMapper.valueToTree(result);
    }

    /**
     * 获取所有扩展的信息。
     *
     * @return 所有扩展定义
     */
    @Tool(description = """
            Purpose: Get information about all available Groovy script extensions

            Alternative: Use mcp://extensions/list resource for the same information

            When to Call:
            - BEFORE using executeGroovyScript() to identify available extensions
            - When SQL results contain encrypted/encoded/special data:
              * Base64 strings
              * Hex values
              * Encrypted fields
              * Compressed data (e.g., zstd)
              * JSON strings needing parsing
              * Timestamps needing conversion

            Returns:
            - List of Extension objects with:
              * name: Extension identifier for executeGroovyScript()
              * description: What the extension does
              * parameters: Required input parameters
            - Same information available via mcp://extensions/list resource
            """)
    public List<Extension> getAllExtensions() {
        return groovyService.getAllExtensions();
    }

    /**
     * 统一执行单数据源查询，确保多数据源聚合路径和单数据源路径共享同一套结果包装规则。
     *
     * @param datasourceName 数据源名称
     * @param sql 查询语句
     * @param successResults 聚合结果容器
     */
    private void executeSqlOnNamedDatasource(String datasourceName, String sql, Map<String, Object> successResults) {
        DataSource targetDataSource = dataSourceService.getDataSource(datasourceName);
        if (targetDataSource == null) {
            log.warn("Datasource [{}] not found, skipping", datasourceName);
            return;
        }

        JdbcExecutor.SqlResult result = jdbcExecutor.executeSql(targetDataSource, sql);
        if (result.success()) {
            successResults.put(datasourceName, limitSqlToolResult(datasourceName, result.data()));
            log.info("Query executed successfully on datasource [{}]", datasourceName);
            return;
        }

        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("error", result.errorMessage());
        errorInfo.put("success", false);
        successResults.put(datasourceName, errorInfo);
        log.error("SQL execution error on datasource [{}]: {}", datasourceName, result.errorMessage());
    }

    /**
     * 在返回前统一裁剪大结果，确保模型先拿到受控窗口与续取入口。
     *
     * @param datasourceName 数据源名称
     * @param data SQL 执行结果
     * @return 原结果或截断包装结果
     */
    @SuppressWarnings("unchecked")
    private Object limitSqlToolResult(String datasourceName, Object data) {
        if (!toolResponseLimitConfig.isEnabled()) {
            return data;
        }
        if (!(data instanceof List<?> rows) || rows.isEmpty() || !rows.stream().allMatch(Map.class::isInstance)) {
            return data;
        }

        List<Map<String, Object>> mappedRows = rows.stream()
                .map(row -> (Map<String, Object>) row)
                .toList();
        int originalChars = calculateJsonChars(mappedRows);
        if (originalChars <= toolResponseLimitConfig.getMaxChars()) {
            return data;
        }

        TruncatedResultSnapshot snapshot = sqlResultCacheService.save(datasourceName, mappedRows, originalChars);
        log.info("SQL 结果超长，生成截断快照，datasource={}, resultId={}, rows={}, chars={}",
                datasourceName, snapshot.resultId(), mappedRows.size(), originalChars);
        return buildPreviewResponse(snapshot, 0, toolResponseLimitConfig.getMaxChars());
    }

    /**
     * 根据当前快照构造首屏预览，保证元信息和预览窗口使用同一个字符预算。
     *
     * @param snapshot 截断结果快照
     * @param offset 起始偏移
     * @param maxChars 单次返回字符预算
     * @return 首屏预览响应
     */
    private Map<String, Object> buildPreviewResponse(TruncatedResultSnapshot snapshot, int offset, int maxChars) {
        List<Map<String, Object>> previewRows = sliceRows(snapshot, offset, maxChars, true);
        int nextOffset = Math.min(offset + previewRows.size(), snapshot.rows().size());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasource", snapshot.datasourceName());
        response.put("truncated", true);
        response.put("resultId", snapshot.resultId());
        response.put("maxChars", maxChars);
        response.put("originalChars", snapshot.originalChars());
        response.put("totalRows", snapshot.rows().size());
        response.put("returnedRows", previewRows.size());
        response.put("nextOffset", nextOffset);
        response.put("hasMore", nextOffset < snapshot.rows().size());
        response.put("preview", previewRows);
        return response;
    }

    /**
     * 构造后续分页响应，确保偏移语义稳定且不会重新执行 SQL。
     *
     * @param snapshot 截断结果快照
     * @param offset 起始偏移
     * @param maxChars 单次返回字符预算
     * @return 当前分页响应
     */
    private Map<String, Object> buildPageResponse(TruncatedResultSnapshot snapshot, int offset, int maxChars) {
        if (offset >= snapshot.rows().size()) {
            return buildEmptyPageResponse(snapshot.resultId(), offset);
        }

        List<Map<String, Object>> pageRows = sliceRows(snapshot, offset, maxChars, false);
        int nextOffset = Math.min(offset + pageRows.size(), snapshot.rows().size());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("resultId", snapshot.resultId());
        response.put("offset", offset);
        response.put("returnedRows", pageRows.size());
        response.put("nextOffset", nextOffset);
        response.put("hasMore", nextOffset < snapshot.rows().size());
        response.put("page", pageRows);
        return response;
    }

    /**
     * 按字符预算切出连续窗口，保证窗口响应尽量大但仍落在统一结构里。
     *
     * @param snapshot 截断结果快照
     * @param offset 起始偏移
     * @param maxChars 单次返回字符预算
     * @param previewMode 是否构造首屏预览
     * @return 当前窗口行
     */
    private List<Map<String, Object>> sliceRows(TruncatedResultSnapshot snapshot, int offset, int maxChars, boolean previewMode) {
        List<Map<String, Object>> pageRows = new ArrayList<>();
        for (int index = offset; index < snapshot.rows().size(); index++) {
            pageRows.add(snapshot.rows().get(index));
            if (pageRows.size() <= toolResponseLimitConfig.getMinPreviewRows()) {
                continue;
            }
            if (calculateJsonChars(buildCandidateResponse(snapshot, offset, pageRows, maxChars, previewMode)) <= maxChars) {
                continue;
            }
            pageRows.remove(pageRows.size() - 1);
            break;
        }

        if (pageRows.isEmpty()) {
            pageRows.add(snapshot.rows().get(offset));
        }
        return pageRows;
    }

    /**
     * 预估分页候选响应大小，避免切窗逻辑与最终返回结构不一致。
     *
     * @param snapshot 截断结果快照
     * @param offset 起始偏移
     * @param pageRows 候选窗口行
     * @param maxChars 单次返回字符预算
     * @param previewMode 是否构造首屏预览
     * @return 候选响应结构
     */
    private Map<String, Object> buildCandidateResponse(TruncatedResultSnapshot snapshot,
                                                       int offset,
                                                       List<Map<String, Object>> pageRows,
                                                       int maxChars,
                                                       boolean previewMode) {
        int nextOffset = Math.min(offset + pageRows.size(), snapshot.rows().size());
        Map<String, Object> response = new LinkedHashMap<>();
        if (previewMode) {
            response.put("datasource", snapshot.datasourceName());
            response.put("truncated", true);
            response.put("resultId", snapshot.resultId());
            response.put("maxChars", maxChars);
            response.put("originalChars", snapshot.originalChars());
            response.put("totalRows", snapshot.rows().size());
            response.put("returnedRows", pageRows.size());
            response.put("nextOffset", nextOffset);
            response.put("hasMore", nextOffset < snapshot.rows().size());
            response.put("preview", pageRows);
            return response;
        }

        response.put("resultId", snapshot.resultId());
        response.put("offset", offset);
        response.put("returnedRows", pageRows.size());
        response.put("nextOffset", nextOffset);
        response.put("hasMore", nextOffset < snapshot.rows().size());
        response.put("page", pageRows);
        return response;
    }

    /**
     * 统一处理 SQL 安全校验失败响应，确保被拦截请求直接在入口返回。
     *
     * @param sql 要校验的 SQL
     * @return 失败响应，或 null
     */
    private Map<String, Object> validateSqlAndGetErrorResult(String sql) {
        SqlSecurityValidator.SqlValidationResult validationResult = sqlSecurityValidator.validateSql(sql);
        if (validationResult.valid()) {
            return null;
        }

        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("error", validationResult.errorMessage());
        errorResult.put("detected_keyword", validationResult.detectedKeyword());
        errorResult.put("sql_security_enabled", true);
        log.warn("SQL validation failed: {}", validationResult.errorMessage());
        return errorResult;
    }

    /**
     * 统一计算 JSON 字符数，避免分页逻辑散落 Jackson 异常处理。
     *
     * @param value 待序列化对象
     * @return JSON 字符数
     */
    private int calculateJsonChars(Object value) {
        try {
            return objectMapper.writeValueAsString(value).length();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize SQL tool result", e);
        }
    }

    /**
     * 解析单次分页字符预算，保证客户端请求不会突破服务端上限。
     *
     * @param requestedMaxChars 请求方指定预算
     * @return 实际生效预算
     */
    private int resolvePageMaxChars(Integer requestedMaxChars) {
        if (requestedMaxChars == null) {
            return toolResponseLimitConfig.getMaxChars();
        }
        if (requestedMaxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be greater than 0");
        }
        return Math.min(requestedMaxChars, toolResponseLimitConfig.getMaxChars());
    }

    /**
     * 构造统一错误响应，保证续取失败时模型拿到的是结构化事实信息。
     *
     * @param error 错误信息
     * @param resultId 关联结果标识
     * @return 错误响应
     */
    private Map<String, Object> buildErrorResponse(String error, String resultId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("error", error);
        if (StringUtils.isNotBlank(resultId)) {
            response.put("resultId", resultId);
        }
        return response;
    }

    /**
     * 对越界偏移返回空页，避免空窗口再引入额外异常分支。
     *
     * @param resultId 结果标识
     * @param offset 请求偏移
     * @return 空分页响应
     */
    private Map<String, Object> buildEmptyPageResponse(String resultId, int offset) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("resultId", resultId);
        response.put("offset", offset);
        response.put("returnedRows", 0);
        response.put("nextOffset", offset);
        response.put("hasMore", false);
        response.put("page", List.of());
        return response;
    }
}
