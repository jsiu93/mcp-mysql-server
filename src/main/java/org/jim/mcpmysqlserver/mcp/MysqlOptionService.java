package org.jim.mcpmysqlserver.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jim.mcpmysqlserver.config.extension.Extension;
import org.jim.mcpmysqlserver.config.extension.GroovyService;
import org.jim.mcpmysqlserver.service.DataSourceService;
import org.jim.mcpmysqlserver.service.JdbcExecutor;
import org.jim.mcpmysqlserver.validator.SqlSecurityValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.util.HashMap;
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
 * 数据库操作服务，支持多种数据库类型（MySQL、PostgreSQL、Oracle、SQL Server、H2等）
 * 执行任意SQL并直接透传数据库服务器的返回值
 * @author yangxin
 */
@Service
@Slf4j
public class MysqlOptionService {

    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;
    private final SqlSecurityValidator sqlSecurityValidator;
    private final JdbcExecutor jdbcExecutor;
    private final ExecutorService executorService;

    @Resource
    private GroovyService groovyService;

    public MysqlOptionService(DataSourceService dataSourceService, SqlSecurityValidator sqlSecurityValidator, JdbcExecutor jdbcExecutor) {
        this.dataSourceService = dataSourceService;
        this.sqlSecurityValidator = sqlSecurityValidator;
        this.jdbcExecutor = jdbcExecutor;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // Java 21 虚拟线程：I/O 密集型任务无需固定线程池，每个任务独立虚拟线程
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        log.info("DatabaseOptionService 初始化完成，使用虚拟线程执行器");
    }

    @PreDestroy
    public void destroy() {
        log.info("关闭虚拟线程执行器");
        executorService.close();
    }


    /**
     * 执行任意SQL语句，不做限制，直接透传数据库服务器的返回值。该工具会查询所有可用的数据源，并执行相同的SQL查询。如果考虑性能，更建议使用executeSqlWithDataSource
     * 在所有可用的数据源上执行相同的SQL查询
     * 使用异步多线程方式执行，最多5个线程同时执行
     * <p>
     * 注意！该工具调用优先级最高，如果用户明确要求根据数据源名称执行SQL，则该工具不会被调用。
     * <p>
     * 重要提示：返回的查询结果可能包含加密、编码或其他需要处理的数据字段。如果发现数据看起来像是加密的、编码的或需要特殊处理的（如Base64、十六进制字符串、密文等），
     * 请主动调用getAllExtensions()查看可用的数据处理扩展工具，然后使用executeGroovyScript()调用相应的解密、解码或数据转换扩展来处理这些字段。
     * 常见需要处理的数据类型包括：加密字段、Base64编码、URL编码、JSON字符串、时间戳转换等。
     *
     * @param sql 要执行的SQL语句
     * @return 所有成功的数据源的查询结果，格式为 {"datasourceName": result, ...}
     */
    @Tool(description = """
            Purpose: Execute SQL query on all configured datasources simultaneously

            Prerequisites:
            - See mcp://datasources/config resource for available datasources and their SQL dialects
            - Alternative: Call listDataSources() to get datasource information programmatically

            Returns:
            - Format: Map<String, Object> with datasource names as keys and query results as values
            - Success: Each datasource's result under its name
            - Failure: Error message for failed datasources

            Data Processing:
            - If results contain encrypted/encoded data (Base64, hex, encrypted fields):
              1. Call getAllExtensions() to discover processing tools
              2. Use executeGroovyScript() to decrypt/decode the data

            Performance Note:
            - For single datasource operations, consider executeSqlWithDataSource() for better performance
            """)
    public Map<String, Object> executeSql(@ToolParam(description = """
            Valid SQL statement compatible with target database dialect
            Examples:
            - MySQL/PostgreSQL: SELECT id, name FROM users WHERE status = 'active'
            - SQL Server: SELECT id, name FROM users WHERE status = 'active'
            - Oracle: SELECT id, name FROM users WHERE status = 'active' AND ROWNUM <= 10
            """) String sql) {
        log.info("Executing SQL on all available datasources: {}", sql);

        // SQL安全验证
        Map<String, Object> errorResult = validateSqlAndGetErrorResult(sql);
        if (errorResult != null) {
            return errorResult;
        }

        // 获取所有可用的数据源名称
        List<String> dataSourceNames = dataSourceService.getDataSourceNames();
        log.info("Found {} available datasources", dataSourceNames.size());

        // 存储每个数据源的查询结果，使用线程安全的ConcurrentHashMap
        Map<String, Object> successResults = new ConcurrentHashMap<>();

        try {
            // 等待所有任务完成
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(dataSourceNames.stream()
                    .map(dsName -> CompletableFuture.runAsync(() -> {
                        log.info("Executing SQL on datasource [{}]", dsName);

                        // 获取指定的数据源
                        DataSource targetDataSource = dataSourceService.getDataSource(dsName);
                        if (targetDataSource == null) {
                            log.warn("Datasource [{}] not found, skipping", dsName);
                            return;
                        }

                        // 使用JdbcExecutor执行SQL
                        JdbcExecutor.SqlResult result = jdbcExecutor.executeSql(targetDataSource, sql);
                        if (result.success()) {
                            successResults.put(dsName, result.data());
                            log.info("Query executed successfully on datasource [{}]", dsName);
                            return;
                        }

                        log.error("SQL execution error on datasource [{}]: {}", dsName, result.errorMessage());

                        // 将错误信息返回给MCP客户端，让模型能够根据错误调整SQL语句
                        Map<String, Object> errorInfo = new HashMap<>();
                        errorInfo.put("error", result.errorMessage());
                        errorInfo.put("success", false);
                        successResults.put(dsName, errorInfo);
                    }, executorService)).toArray(CompletableFuture[]::new)
            );

            // 设置超时时间，避免长时间等待
            allFutures.get(60, TimeUnit.SECONDS);

        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            log.error("Error executing SQL on all datasources: {}", e.getMessage(), e);
        }

        return successResults;
    }

    /**
     * 获取所有可用的数据源名称和数据库类型信息
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

        List<String> dataSourceNames = dataSourceService.getDataSourceNames();
        String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();

        // 获取每个数据源的详细信息，包括数据库类型
        List<Map<String, Object>> dataSourceDetails = dataSourceService.getDataSourceDetails();

        Map<String, Object> result = new HashMap<>();
        result.put("datasources", dataSourceDetails);
        result.put("default", defaultDataSourceName);

        log.info("返回数据源信息: 默认数据源={}, 总数据源数量={}", defaultDataSourceName, dataSourceDetails.size());

        return result;
    }

    /**
     * 在默认数据源上执行SQL语句，适用于用户未明确指定数据源的情况
     * 该工具是executeSql和executeSqlWithDataSource的轻量级替代方案，仅查询标记为default的数据源
     * <p>
     * 重要提示：返回的查询结果可能包含加密、编码或其他需要处理的数据字段。如果发现数据看起来像是加密的、编码的或需要特殊处理的（如Base64、十六进制字符串、密文等），
     * 请主动调用getAllExtensions()查看可用的数据处理扩展工具，然后使用executeGroovyScript()调用相应的解密、解码或数据转换扩展来处理这些字段。
     * 常见需要处理的数据类型包括：加密字段、Base64编码、URL编码、JSON字符串、时间戳转换等。
     *
     * @param sql 要执行的SQL语句
     * @return 默认数据源的查询结果，格式为 {"defaultDataSourceName": result}
     */
    @Tool(description = """
            Purpose: Execute SQL query on the default datasource only

            Priority:
            - HIGHEST priority when user hasn't specified environment/datasource
            - Try this first; if returns empty, fallback to executeSql()

            Prerequisites:
            - NONE - does not require getDataSourcesInfo()

            Database Dialect:
            - See mcp://datasources/config resource for current default datasource type and configuration
            - Must match the SQL dialect of the default datasource

            Returns:
            - Format: JsonNode containing query results from default datasource
            - Empty result: Returns message "No data returned from SQL query"
            - Error: Returns error details

            Data Processing:
            - If results contain encrypted/encoded data (Base64, hex, encrypted fields):
              1. Call getAllExtensions() to discover processing tools
              2. Use executeGroovyScript() to decrypt/decode the data
            """)
    public JsonNode executeSqlOnDefault(@ToolParam(description = """
            Valid SQL statement compatible with default datasource dialect
            Examples:
            - MySQL/PostgreSQL: SELECT * FROM users LIMIT 10
            - SQL Server: SELECT TOP 10 * FROM users
            - Oracle: SELECT * FROM users WHERE ROWNUM <= 10
            """) String sql) {
        log.info("Executing SQL on default datasource: {}", sql);

        // SQL安全验证
        Object errorResult1 = validateSqlAndGetErrorResult(sql);
        if (errorResult1 != null) {
            return objectMapper.valueToTree(errorResult1);
        }

        // 获取默认数据源名称
        String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();
        if (StringUtils.isBlank(defaultDataSourceName)) {
            String errorMsg = "No default datasource configured";
            log.error(errorMsg);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", errorMsg);
            return objectMapper.valueToTree(errorResult);
        }

        Map<String, Object> stringObjectMap = executeSqlWithDataSource(defaultDataSourceName, sql);
        if (CollectionUtils.isEmpty(stringObjectMap)) {
            log.warn("No results returned from SQL execution on default datasource [{}]", defaultDataSourceName);
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("message", "No data returned from SQL query");
            return objectMapper.valueToTree(emptyResult);
        }

        return objectMapper.valueToTree(stringObjectMap.get(defaultDataSourceName));
    }

    private Map<String, Object> validateSqlAndGetErrorResult(String sql) {
        SqlSecurityValidator.SqlValidationResult validationResult = sqlSecurityValidator.validateSql(sql);
        if (validationResult.valid()) {
            return null;
        }

        log.warn("SQL validation failed: {}", validationResult.errorMessage());
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("error", validationResult.errorMessage());
        errorResult.put("detected_keyword", validationResult.detectedKeyword());
        errorResult.put("sql_security_enabled", true);
        return errorResult;
    }

    /**
     * 获取默认数据源的数据库类型信息，用于动态生成Tool描述
     * @return 默认数据源的数据库类型描述
     */
    private String getDefaultDataSourceDialectInfo() {
        try {
            String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();
            if (StringUtils.isBlank(defaultDataSourceName)) {
                return "Unknown database type (no default datasource configured)";
            }

            List<Map<String, Object>> dataSourceDetails = dataSourceService.getDataSourceDetails();
            String databaseType = dataSourceDetails.stream()
                    .filter(ds -> defaultDataSourceName.equals(ds.get("name")))
                    .map(ds -> (String) ds.get("databaseType"))
                    .findFirst()
                    .orElse("Unknown");

            return String.format("%s database (datasource: %s)", databaseType, defaultDataSourceName);
        } catch (Exception e) {
            log.warn("Failed to get default datasource dialect info: {}", e.getMessage());
            return "Unknown database type (error retrieving datasource info)";
        }
    }

    /**
     * 在指定数据源上执行任意SQL语句，不做限制，直接透传数据库服务器的返回值
     * 使用前需要先调用listDataSources获取所有可用的数据源名称
     * <p>
     * 注意！该工具优先级低于executeSql。除非用户明确要求根据数据源名称执行SQL，否则建议使用executeSql。
     * <p>
     * 重要提示：返回的查询结果可能包含加密、编码或其他需要处理的数据字段。如果发现数据看起来像是加密的、编码的或需要特殊处理的（如Base64、十六进制字符串、密文等），
     * 请主动调用getAllExtensions()查看可用的数据处理扩展工具，然后使用executeGroovyScript()调用相应的解密、解码或数据转换扩展来处理这些字段。
     * 常见需要处理的数据类型包括：加密字段、Base64编码、URL编码、JSON字符串、时间戳转换等。
     *
     * @param dataSourceName 数据源名称，来自listDataSources的返回值
     * @param sql 要执行的SQL语句
     * @return 查询结果，格式为 {"datasourceName": result}
     */
    @Tool(description = """
            Purpose: Execute SQL query on a specific named datasource

            Priority:
            - LOWER than executeSql() unless user explicitly requests single-datasource operation
            - More efficient than executeSql() for single datasource queries

            Prerequisites:
            - See mcp://datasources/config resource for valid datasource names and SQL dialects
            - Alternative: Call listDataSources() to get datasource information programmatically

            Returns:
            - Format: Map<String, Object> with single entry {datasourceName: result}
            - Success: Query results under datasource name key
            - Error: Error message if datasource not found or query fails

            Data Processing:
            - If results contain encrypted/encoded data (Base64, hex, encrypted fields):
              1. Call getAllExtensions() to discover processing tools
              2. Use executeGroovyScript() to decrypt/decode the data
            """)
    public Map<String, Object> executeSqlWithDataSource(@ToolParam(description = """
                                                                Target datasource name (MUST match a name from getDataSourcesInfo() response)
                                                                """) String dataSourceName,
                                                        @ToolParam(description = """
                                                                Valid SQL statement compatible with target datasource dialect
                                                                Examples:
                                                                - MySQL/PostgreSQL: SELECT * FROM users LIMIT 10
                                                                - SQL Server: SELECT TOP 10 * FROM users
                                                                - Oracle: SELECT * FROM users WHERE ROWNUM <= 10
                                                                """) String sql) {
        log.info("Executing SQL on datasource [{}]: {}", dataSourceName, sql);

        // SQL安全验证
        Map<String, Object> errorResult = validateSqlAndGetErrorResult(sql);
        if (errorResult != null) {
            return errorResult;
        }

        // 存储查询结果
        Map<String, Object> result = new HashMap<>();

        // 获取指定的数据源
        DataSource targetDataSource = dataSourceService.getDataSource(dataSourceName);
        if (targetDataSource == null) {
            String errorMsg = "Datasource [" + dataSourceName + "] not found";
            log.error(errorMsg);
            result.put(dataSourceName, errorMsg);
            return result;
        }

        // 使用JdbcExecutor执行SQL
        JdbcExecutor.SqlResult sqlResult = jdbcExecutor.executeSql(targetDataSource, sql);

        if (sqlResult.success()) {
            result.put(dataSourceName, sqlResult.data());
            log.info("executeSqlWithDataSource Query executed successfully on datasource [{}]", dataSourceName);
            return result;
        }

        log.error("executeSqlWithDataSource SQL execution error on datasource [{}]: {}", dataSourceName, sqlResult.errorMessage());

        // 将错误信息返回给MCP客户端，让模型能够根据错误调整SQL语句
        Map<String, Object> errorInfo = new HashMap<>();
        errorInfo.put("error", sqlResult.errorMessage());
        errorInfo.put("success", false);
        result.put(dataSourceName, errorInfo);

        return result;
    }

    /**
     * 通过扩展名称，执行groovy脚本，处理传入的任意字符串
     *
     * @param extensionName 扩展名称
     * @param input 输入字符串。提示词：请深呼吸，放松身心，该参数很重要，请绝对认真输入该参数，不要有任何遗漏。
     * @return 处理后的字符串
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
            """, returnDirect = true)public JsonNode executeGroovyScript(@ToolParam(description =  """
                                                Extension name (MUST match a name from getAllExtensions() response)
                                                """) String extensionName,
                                        @ToolParam(description = """
                                                Input text to be processed by the Groovy script
                                                CRITICAL: This parameter is extremely important - input it carefully without missing any details
                                                """) String input) {
        // 执行脚本
        Object o = groovyService.executeGroovyScript(extensionName, input);
        if (o instanceof String) {
            try {
                // 尝试将结果转换为JsonNode
                return objectMapper.readTree((String) o);
            } catch (Exception e) {
                log.error("Failed to parse Groovy script result as JSON: {}", e.getMessage(), e);
                return objectMapper.createObjectNode().put("error", "Invalid JSON result from Groovy script");
            }
        } else {
            // 如果不是字符串，直接返回结果
            return objectMapper.valueToTree(o);
        }
    }

    /**
     * 获取所有扩展的信息
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
        // 获取所有扩展的信息
        return groovyService.getAllExtensions();
    }



}
