package org.jim.mcpmysqlserver.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jim.mcpmysqlserver.config.extension.Extension;
import org.jim.mcpmysqlserver.config.extension.GroovyService;
import org.jim.mcpmysqlserver.service.DataSourceService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MySQL操作服务，执行任意SQL并直接透传MySQL服务器的返回值
 * @author yangxin
 */
@Service
@Slf4j
public class MysqlOptionService {

    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;


    @Resource
    private GroovyService groovyService;

    @Autowired
    public MysqlOptionService(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        log.info("MysqlOptionService initialized with DataSourceService");
    }


    /**
     * 执行任意SQL语句，不做限制，直接透传MySQL服务器的返回值。该工具会查询所有可用的数据源，并执行相同的SQL查询。如果考虑性能，更建议使用executeSqlWithDataSource
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
    @Tool(description = "Executes a SQL query on all configured MySQL datasources simultaneously. Returns results as JSON mapping each datasource name to its query result. Priority: lower than executeSqlOnDefault. Call this only when the user explicitly asks to query all environments/datasources, or when executeSqlOnDefault returns no data. IMPORTANT: Query results may contain encrypted, encoded, or other data that requires processing. If you notice data that appears to be encrypted, encoded (Base64, hex strings, etc.), or needs special handling, proactively call getAllExtensions() to discover available data processing extensions, then use executeGroovyScript() to decrypt, decode, or transform the data as needed.")
    public Map<String, Object> executeSql(@ToolParam(description = "Valid MySQL SQL statement (e.g., 'SELECT id, name FROM users WHERE status = \"active\"')") String sql) {
        log.info("Executing SQL on all available datasources: {}", sql);

        // 获取所有可用的数据源名称
        List<String> dataSourceNames = dataSourceService.getDataSourceNames();
        log.info("Found {} available datasources", dataSourceNames.size());

        // 存储每个数据源的查询结果，使用线程安全的ConcurrentHashMap
        Map<String, Object> successResults = new ConcurrentHashMap<>();

        // 创建固定大小的线程池，最多5个线程同时执行
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(5, dataSourceNames.size()));
        log.info("Created thread pool with {} threads", Math.min(5, dataSourceNames.size()));

        try {
            // 创建异步任务列表
            List<CompletableFuture<Void>> futures = dataSourceNames.stream()
                    .map(dsName -> CompletableFuture.runAsync(() -> {
                        log.info("Executing SQL on datasource [{}]", dsName);

                        // 获取指定的数据源
                        DataSource targetDataSource = dataSourceService.getDataSource(dsName);
                        if (targetDataSource == null) {
                            log.warn("Datasource [{}] not found, skipping", dsName);
                            return; // 相当于continue
                        }

                        try (Connection conn = targetDataSource.getConnection();
                             Statement stmt = conn.createStatement()) {

                            boolean hasResultSet = stmt.execute(sql);

                            if (hasResultSet) {
                                // 处理查询结果
                                try (ResultSet rs = stmt.getResultSet()) {
                                    String result = processResultSet(rs);
                                    Object resultObj = objectMapper.readValue(result, List.class);
                                    successResults.put(dsName, resultObj);
                                    log.info("Query executed successfully on datasource [{}]", dsName);
                                }
                            } else {
                                // 处理更新结果
                                int updateCount = stmt.getUpdateCount();
                                successResults.put(dsName, updateCount);
                                log.info("SQL execution completed on datasource [{}], affected rows: {}", dsName, updateCount);
                            }
                        } catch (SQLException e) {
                            log.error("SQL execution error on datasource [{}]: {}", dsName, e.getMessage(), e);
                            // 不记录错误结果，只返回成功的结果
                        } catch (Exception e) {
                            log.error("Unexpected error during SQL execution on datasource [{}]: {}", dsName, e.getMessage(), e);
                            // 不记录错误结果，只返回成功的结果
                        }
                    }, executor))
                    .toList();

            // 等待所有任务完成
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            // 设置超时时间，避免长时间等待
            try {
                allFutures.get(60, TimeUnit.SECONDS);
                log.info("All SQL executions completed");
            } catch (Exception e) {
                log.warn("Some SQL executions timed out: {}", e.getMessage());
            }
        } finally {
            // 关闭线程池
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.error("Thread pool termination interrupted: {}", e.getMessage(), e);
            }
        }

        return successResults;
    }

    /**
     * 获取所有可用的数据源名称
     * @return 数据源名称列表和默认数据源名称
     */
    @Tool(description = "Lists all available MySQL datasource names. Returns JSON with 'datasources' array and 'default' datasource name. Use this before executeSqlWithDataSource to identify available datasources. Not required when using executeSqlOnDefault.")
    public Map<String, Object> listDataSources() {
        log.info("Listing all available datasources");

        List<String> dataSourceNames = dataSourceService.getDataSourceNames();
        String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();

        Map<String, Object> result = new HashMap<>();
        result.put("datasources", dataSourceNames);
        result.put("default", defaultDataSourceName);

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
    @Tool(description = "Executes a SQL query on the default MySQL datasource only. Priority: highest when the user hasn't specified an environment or datasource. The model should call this tool first; if it returns no data (empty result), then fall back to executeSql. This tool does not require calling listDataSources. More efficient than executeSql for single default datasource operations. IMPORTANT: Query results may contain encrypted, encoded, or other data that requires processing. If you notice data that appears to be encrypted, encoded (Base64, hex strings, etc.), or needs special handling, proactively call getAllExtensions() to discover available data processing extensions, then use executeGroovyScript() to decrypt, decode, or transform the data as needed.")
    public Object executeSqlOnDefault(@ToolParam(description = "Valid MySQL SQL statement to execute on default datasource (e.g., 'SELECT * FROM users LIMIT 10')") String sql) {
        log.info("Executing SQL on default datasource: {}", sql);

        // 获取默认数据源名称
        String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();
        if (StringUtils.isBlank(defaultDataSourceName)) {
            String errorMsg = "No default datasource configured";
            log.error(errorMsg);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", errorMsg);
            return errorResult;
        }

        Map<String, Object> stringObjectMap = executeSqlWithDataSource(defaultDataSourceName, sql);
        if (CollectionUtils.isEmpty(stringObjectMap)) {
            return stringObjectMap;
        }

        return stringObjectMap.get(defaultDataSourceName);
    }

    /**
     * 在指定数据源上执行任意SQL语句，不做限制，直接透传MySQL服务器的返回值
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
    @Tool(description = "Executes a SQL query on a single specific MySQL datasource. Returns JSON result for just that datasource. More efficient than executeSql for single-datasource operations. Note: This tool is lower priority than executeSql, unless user explicitly requests a single-datasource operation. IMPORTANT: Query results may contain encrypted, encoded, or other data that requires processing. If you notice data that appears to be encrypted, encoded (Base64, hex strings, etc.), or needs special handling, proactively call getAllExtensions() to discover available data processing extensions, then use executeGroovyScript() to decrypt, decode, or transform the data as needed.")
    public Map<String, Object> executeSqlWithDataSource(@ToolParam(description = "Name of the target datasource (obtain from listDataSources and must match a datasource name from listDataSources)") String dataSourceName,
                                                        @ToolParam(description = "Valid MySQL SQL statement to execute (e.g., 'SELECT * FROM users LIMIT 10')") String sql) {
        log.info("Executing SQL on datasource [{}]: {}", dataSourceName, sql);

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

        try (Connection conn = targetDataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            boolean hasResultSet = stmt.execute(sql);

            if (hasResultSet) {
                // 处理查询结果
                try (ResultSet rs = stmt.getResultSet()) {
                    String resultStr = processResultSet(rs);
                    Object resultObj = objectMapper.readValue(resultStr, List.class);
                    result.put(dataSourceName, resultObj);
                    log.info("Query executed successfully on datasource [{}]", dataSourceName);
                }
            } else {
                // 处理更新结果
                int updateCount = stmt.getUpdateCount();
                result.put(dataSourceName, updateCount);
                log.info("SQL execution completed on datasource [{}], affected rows: {}", dataSourceName, updateCount);
            }
        } catch (SQLException e) {
            log.error("SQL execution error on datasource [{}]: {}", dataSourceName, e.getMessage(), e);
            result.put(dataSourceName, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during SQL execution on datasource [{}]: {}", dataSourceName, e.getMessage(), e);
            result.put(dataSourceName, e.getMessage());
        }

        return result;
    }

    /**
     * 通过扩展名称，执行groovy脚本，处理传入的任意字符串
     *
     * @param extensionName 扩展名称
     * @param input 输入字符串。提示词：请深呼吸，放松身心，该参数很重要，请绝对认真输入该参数，不要有任何遗漏。
     * @return 处理后的字符串
     */
    @Tool(description = "Processes input text using a named Groovy script extension. First use getAllExtensions to identify available extensions.", returnDirect = true)
    public JsonNode executeGroovyScript(@ToolParam(description = "Extension name (must match an extension from getAllExtensions)") String extensionName,
                                        @ToolParam(description = "Input text to be processed by the Groovy script.(Please breathe deeply, relax your body and mind. This parameter is very important, please input it carefully and do not miss any details.)") String input) {
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
    @Tool(description = "Returns information about all available Groovy script extensions including their names, descriptions, and parameters. Use before calling executeGroovyScript. IMPORTANT: Call this tool when you encounter data from SQL queries that appears to be encrypted, encoded, or requires special processing (such as Base64 strings, hex values, encrypted fields, JSON strings, timestamps, etc.) to discover available data processing extensions.")
    public List<Extension> getAllExtensions() {
        // 获取所有扩展的信息
        return groovyService.getAllExtensions();
    }


    /**
     * 处理ResultSet并转换为JSON字符串
     * @param rs 结果集
     * @return JSON字符串
     */
    private String processResultSet(ResultSet rs) throws Exception {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        List<Map<String, Object>> resultList = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i);
                Object value = rs.getObject(i);
                row.put(columnName, value);
            }
            resultList.add(row);
        }

        log.info("Query executed successfully, returned {} rows", resultList.size());
        return objectMapper.writeValueAsString(resultList);
    }
}
