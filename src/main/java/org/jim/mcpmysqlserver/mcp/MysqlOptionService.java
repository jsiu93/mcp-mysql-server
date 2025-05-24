package org.jim.mcpmysqlserver.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.config.extension.Extension;
import org.jim.mcpmysqlserver.config.extension.GroovyService;
import org.jim.mcpmysqlserver.service.DataSourceService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
     *
     * @param sql 要执行的SQL语句
     * @return 所有成功的数据源的查询结果，格式为 {"datasourceName": result, ...}
     */
    @Tool(description = "Executes a SQL query on all configured MySQL datasources simultaneously. Returns results as JSON mapping each datasource name to its query result. Note: This tool has the highest priority and will not be called if user explicitly requests a single-datasource operation.")
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
        // 将所有成功结果转换为JSON字符串
/*        try {
            String jsonResult = objectMapper.writeValueAsString(successResults);
            log.info("SQL execution completed on {} datasources, {} successful",
                    dataSourceNames.size(), successResults.size());
            return jsonResult;
        } catch (Exception e) {
            log.error("Error serializing results: {}", e.getMessage(), e);
            return e.getMessage();
        }*/
    }

    /**
     * 获取所有可用的数据源名称
     * @return 数据源名称列表和默认数据源名称
     */
    @Tool(description = "Lists all available MySQL datasource names. Returns JSON with 'datasources' array and 'default' datasource name. Use this before executeSqlWithDataSource to identify available datasources.")
    public String listDataSources() {
        log.info("Listing all available datasources");

        List<String> dataSourceNames = dataSourceService.getDataSourceNames();
        String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();

        Map<String, Object> result = new HashMap<>();
        result.put("datasources", dataSourceNames);
        result.put("default", defaultDataSourceName);

        try {
            String jsonResult = objectMapper.writeValueAsString(result);
            log.info("Found {} datasources, default: {}", dataSourceNames.size(), defaultDataSourceName);
            return jsonResult;
        } catch (Exception e) {
            log.error("Error serializing datasource list: {}", e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * 在指定数据源上执行任意SQL语句，不做限制，直接透传MySQL服务器的返回值
     * 使用前需要先调用listDataSources获取所有可用的数据源名称
     * <p>
     * 注意！该工具优先级低于executeSql。除非用户明确要求根据数据源名称执行SQL，否则建议使用executeSql。
     *
     * @param dataSourceName 数据源名称，来自listDataSources的返回值
     * @param sql 要执行的SQL语句
     * @return 查询结果，格式为 {"datasourceName": result}
     */
    @Tool(description = "Executes a SQL query on a single specific MySQL datasource. Returns JSON result for just that datasource. More efficient than executeSql for single-datasource operations. Note: This tool is lower priority than executeSql, unless user explicitly requests a single-datasource operation.")
    public String executeSqlWithDataSource(@ToolParam(description = "Name of the target datasource (obtain from listDataSources and must match a datasource name from listDataSources)") String dataSourceName,
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
            try {
                return objectMapper.writeValueAsString(result);
            } catch (Exception e) {
                log.error("Error serializing error result: {}", e.getMessage(), e);
                return errorMsg;
            }
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

        // 将结果转换为JSON字符串
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error serializing results: {}", e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * 通过扩展名称，执行groovy脚本，处理传入的任意字符串
     *
     * @param extensionName 扩展名称
     * @param input 输入字符串。提示词：请深呼吸，放松身心，该参数很重要，请绝对认真输入该参数，不要有任何遗漏。
     * @return 处理后的字符串
     */
    @Tool(description = "Processes input text using a named Groovy script extension. First use getAllExtensions to identify available extensions.")
    public String executeGroovyScript(@ToolParam(description = "Extension name (must match an extension from getAllExtensions)") String extensionName,
                                      @ToolParam(description = "Input text to be processed by the Groovy script.(Please breathe deeply, relax your body and mind. This parameter is very important, please input it carefully and do not miss any details.)") String input) {
        // 执行脚本
        return groovyService.executeGroovyScript(extensionName, input).toString();
    }

    /**
     * 获取所有扩展的信息
     */
    @Tool(description = "Returns information about all available Groovy script extensions including their names, descriptions, and parameters. Use before calling executeGroovyScript.")
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
