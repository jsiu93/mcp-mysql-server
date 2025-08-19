package org.jim.mcpmysqlserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.config.extension.Extension;
import org.jim.mcpmysqlserver.config.extension.GroovyService;
import org.jim.mcpmysqlserver.mcp.MysqlOptionService;
import org.jim.mcpmysqlserver.service.DataSourceService;
import org.jim.mcpmysqlserver.util.PortUtils;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 应用主类
 * @author yangxin
 */
@SpringBootApplication
@Slf4j
public class McpMysqlServerApplication {

    /**
     * 应用默认端口
     */
    private static final int DEFAULT_PORT = 9433;

    /**
     * 应用启动入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 从命令行参数或环境变量中获取端口号，默认为9433
        int port = getPortFromArgs(args, DEFAULT_PORT);

        // 检查端口是否已被占用
        if (PortUtils.isPortInUse(port)) {
            // 获取一个随机可用端口
            int randomPort = PortUtils.findAvailablePort();
            if (randomPort <= 0) {
                log.error("Failed to find an available port. Exiting...");
                System.exit(1);
                return;
            }

            // 设置系统属性，使Spring Boot使用新的端口
            System.setProperty("server.port", String.valueOf(randomPort));
        }

        // 获取最终使用的端口号（可能是随机分配的）
        String finalPort = System.getProperty("server.port", String.valueOf(port));
        SpringApplication.run(McpMysqlServerApplication.class, args);
    }

    /**
     * 从命令行参数中获取端口号
     * 支持两种格式：--server.port=9433 或 -Dserver.port=9433
     *
     * @param args 命令行参数
     * @param defaultPort 默认端口
     * @return 解析到的端口号，如果未指定则返回默认端口
     */
    private static int getPortFromArgs(String[] args, int defaultPort) {
        // 检查命令行参数
        if (args != null) {
            for (String arg : args) {
                // 检查--server.port=xxx格式
                if (arg.startsWith("--server.port=")) {
                    try {
                        return Integer.parseInt(arg.substring("--server.port=".length()));
                    } catch (NumberFormatException e) {
                        //log.warn("Invalid port number in argument: {}", arg);
                    }
                }
                // 检查-Dserver.port=xxx格式
                else if (arg.startsWith("-Dserver.port=")) {
                    try {
                        return Integer.parseInt(arg.substring("-Dserver.port=".length()));
                    } catch (NumberFormatException e) {
                        //log.warn("Invalid port number in argument: {}", arg);
                    }
                }
            }
        }

        // 检查系统属性
        String systemPort = System.getProperty("server.port");
        if (systemPort != null && !systemPort.isEmpty()) {
            try {
                return Integer.parseInt(systemPort);
            } catch (NumberFormatException e) {
                //log.warn("Invalid port number in system property: {}", systemPort);
            }
        }

        // 返回默认端口
        return defaultPort;
    }


    @Bean
    public ToolCallbackProvider databaseToolCallbackProvider(MysqlOptionService mysqlOptionService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mysqlOptionService)
                .build();
    }

    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> myResources(
            DataSourceService dataSourceService,
            GroovyService groovyService,
            MysqlOptionService mysqlOptionService) {

        McpSchema.Annotations highPriorityAnnotations = new McpSchema.Annotations(
                List.of(McpSchema.Role.USER, McpSchema.Role.ASSISTANT), 0.9
        );

        McpSchema.Annotations mediumPriorityAnnotations = new McpSchema.Annotations(
                List.of(McpSchema.Role.USER, McpSchema.Role.ASSISTANT), 0.7
        );

        List<McpServerFeatures.SyncResourceSpecification> resources = new ArrayList<>();

        // 1. 数据源配置信息Resource
        var dataSourceResource = new McpSchema.Resource(
                "mcp://datasources/config",
                "数据源配置信息",
                "提供所有配置的数据源详细信息，包括数据库类型、连接状态等",
                "application/json",
                highPriorityAnnotations
        );

        var dataSourceSpec = new McpServerFeatures.SyncResourceSpecification(dataSourceResource, (exchange, request) -> {
            try {
                Map<String, Object> dataSourceInfo = new HashMap<>();

                // 获取数据源详细信息
                List<Map<String, Object>> dataSourceDetails = dataSourceService.getDataSourceDetails();
                String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();

                dataSourceInfo.put("datasources", dataSourceDetails);
                dataSourceInfo.put("defaultDataSource", defaultDataSourceName);
                dataSourceInfo.put("totalCount", dataSourceDetails.size());
                dataSourceInfo.put("lastUpdated", java.time.Instant.now().toString());

                String jsonContent = new ObjectMapper().writeValueAsString(dataSourceInfo);
                return new McpSchema.ReadResourceResult(
                        List.of(new McpSchema.TextResourceContents(request.uri(), "application/json", jsonContent)));
            } catch (Exception e) {
                log.error("Failed to generate datasource info: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to generate datasource info", e);
            }
        });
        resources.add(dataSourceSpec);

       // 2. 数据库表结构信息Resource
        var tableStructureResource = new McpSchema.Resource(
                "mcp://database/tables",
                "数据库表结构信息",
                "提供默认数据源中所有表的结构信息，包括表名、字段信息等",
                "application/json",
                highPriorityAnnotations
        );

        var tableStructureSpec = new McpServerFeatures.SyncResourceSpecification(tableStructureResource, (exchange, request) -> {
            try {
                Map<String, Object> tableInfo = new HashMap<>();
                String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();

                // 获取数据库类型
                List<Map<String, Object>> dataSourceDetails = dataSourceService.getDataSourceDetails();
                String databaseType = dataSourceDetails.stream()
                        .filter(ds -> defaultDataSourceName.equals(ds.get("name")))
                        .map(ds -> (String) ds.get("databaseType"))
                        .findFirst()
                        .orElse("Unknown");

                String tableQuery;

                // 根据数据库类型选择合适的查询语句
                switch (databaseType.toLowerCase()) {
                    case "postgresql":
                        tableQuery = "SELECT " +
                                "table_name as TABLE_NAME, " +
                                "'' as TABLE_COMMENT, " +
                                "0 as TABLE_ROWS, " +
                                "null as CREATE_TIME, " +
                                "null as UPDATE_TIME " +
                                "FROM information_schema.tables " +
                                "WHERE table_schema = 'public' " +
                                "ORDER BY table_name";
                        break;
                    case "oracle":
                        tableQuery = "SELECT " +
                                "table_name as TABLE_NAME, " +
                                "'' as TABLE_COMMENT, " +
                                "num_rows as TABLE_ROWS, " +
                                "created as CREATE_TIME, " +
                                "last_analyzed as UPDATE_TIME " +
                                "FROM user_tables " +
                                "ORDER BY table_name";
                        break;
                    case "sql server":
                        tableQuery = "SELECT " +
                                "TABLE_NAME, " +
                                "'' as TABLE_COMMENT, " +
                                "0 as TABLE_ROWS, " +
                                "null as CREATE_TIME, " +
                                "null as UPDATE_TIME " +
                                "FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_TYPE = 'BASE TABLE' " +
                                "ORDER BY TABLE_NAME";
                        break;
                    case "h2":
                        tableQuery = "SELECT " +
                                "TABLE_NAME, " +
                                "REMARKS as TABLE_COMMENT, " +
                                "0 as TABLE_ROWS, " +
                                "null as CREATE_TIME, " +
                                "null as UPDATE_TIME " +
                                "FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_SCHEMA = SCHEMA() " +
                                "ORDER BY TABLE_NAME";
                        break;
                    case "mysql":
                    default:
                        tableQuery = "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS, CREATE_TIME, UPDATE_TIME " +
                                "FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_SCHEMA = DATABASE() " +
                                "ORDER BY TABLE_NAME";
                        break;
                }

                Map<String, Object> tablesResult = mysqlOptionService.executeSqlWithDataSource(
                        defaultDataSourceName,
                        tableQuery
                );

                tableInfo.put("tables", tablesResult.get(defaultDataSourceName));
                tableInfo.put("databaseName", defaultDataSourceName);
                tableInfo.put("databaseType", databaseType);
                tableInfo.put("lastUpdated", java.time.Instant.now().toString());

                String jsonContent = new ObjectMapper().writeValueAsString(tableInfo);
                return new McpSchema.ReadResourceResult(
                        List.of(new McpSchema.TextResourceContents(request.uri(), "application/json", jsonContent)));
            } catch (Exception e) {
                log.error("Failed to generate table structure info: {}", e.getMessage(), e);
                // 如果查询失败，提供通用的备选方案
                try {
                    String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();
                    Map<String, Object> fallbackResult = mysqlOptionService.executeSqlWithDataSource(
                            defaultDataSourceName,
                            "SELECT 'Please use appropriate SQL for your database type' as message"
                    );

                    Map<String, Object> errorInfo = new HashMap<>();
                    errorInfo.put("error", "无法获取表结构信息，可能是权限不足或数据库类型不支持");
                    errorInfo.put("suggestion", "请根据您的数据库类型使用合适的SQL语句查询表信息");
                    errorInfo.put("databaseName", defaultDataSourceName);
                    errorInfo.put("commonQueries", Map.of(
                            "MySQL", "SHOW TABLES",
                            "PostgreSQL", "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                            "Oracle", "SELECT table_name FROM user_tables",
                            "SQL Server", "SELECT name FROM sys.tables",
                            "H2", "SHOW TABLES"
                    ));

                    String jsonContent = new ObjectMapper().writeValueAsString(errorInfo);
                    return new McpSchema.ReadResourceResult(
                            List.of(new McpSchema.TextResourceContents(request.uri(), "application/json", jsonContent)));
                } catch (Exception jsonEx) {
                    throw new RuntimeException("Failed to generate table structure info", jsonEx);
                }
            }
        });
        resources.add(tableStructureSpec);
        // 3. 扩展功能信息Resource
        var extensionsResource = new McpSchema.Resource(
                "mcp://extensions/list",
                "Groovy扩展功能列表",
                "提供所有可用的Groovy扩展功能信息，包括功能描述、使用方法等",
                "application/json",
                mediumPriorityAnnotations
        );

        var extensionsSpec = new McpServerFeatures.SyncResourceSpecification(extensionsResource, (exchange, request) -> {
            try {
                Map<String, Object> extensionInfo = new HashMap<>();

                List<Extension> extensions = groovyService.getAllExtensions();

                // 过滤启用的扩展并格式化信息
                List<Map<String, Object>> enabledExtensions = extensions.stream()
                        .filter(ext -> ext.getEnabled() != null && ext.getEnabled())
                        .map(ext -> {
                            Map<String, Object> extInfo = new HashMap<>();
                            extInfo.put("name", ext.getName());
                            extInfo.put("description", ext.getDescription());
                            extInfo.put("prompt", ext.getPrompt());
                            extInfo.put("enabled", ext.getEnabled());
                            return extInfo;
                        })
                        .toList();

                extensionInfo.put("extensions", enabledExtensions);
                extensionInfo.put("totalCount", extensions.size());
                extensionInfo.put("enabledCount", enabledExtensions.size());
                extensionInfo.put("lastUpdated", java.time.Instant.now().toString());

                String jsonContent = new ObjectMapper().writeValueAsString(extensionInfo);
                return new McpSchema.ReadResourceResult(
                        List.of(new McpSchema.TextResourceContents(request.uri(), "application/json", jsonContent)));
            } catch (Exception e) {
                log.error("Failed to generate extensions info: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to generate extensions info", e);
            }
        });
        resources.add(extensionsSpec);

        // 4. 常用SQL模板Resource
        var sqlTemplatesResource = new McpSchema.Resource(
                "mcp://sql/templates",
                "常用SQL查询模板",
                "提供常用的SQL查询模板和示例，帮助快速构建查询语句",
                "application/json",
                mediumPriorityAnnotations
        );

        var sqlTemplatesSpec = new McpServerFeatures.SyncResourceSpecification(sqlTemplatesResource, (exchange, request) -> {
            try {
                Map<String, Object> templatesInfo = new HashMap<>();

                // 定义常用SQL模板
                List<Map<String, Object>> templates = List.of(
                        Map.of(
                                "category", "基础查询",
                                "name", "查看表结构",
                                "template", "DESCRIBE {table_name}",
                                "description", "查看指定表的字段结构",
                                "example", "DESCRIBE users"
                        ),
                        Map.of(
                                "category", "基础查询",
                                "name", "查看所有表",
                                "template", "SHOW TABLES",
                                "description", "列出当前数据库中的所有表",
                                "example", "SHOW TABLES"
                        ),
                        Map.of(
                                "category", "数据查询",
                                "name", "分页查询",
                                "template", "SELECT * FROM {table_name} LIMIT {offset}, {limit}",
                                "description", "分页查询表数据",
                                "example", "SELECT * FROM users LIMIT 0, 10"
                        ),
                        Map.of(
                                "category", "数据查询",
                                "name", "条件查询",
                                "template", "SELECT * FROM {table_name} WHERE {condition}",
                                "description", "根据条件查询数据",
                                "example", "SELECT * FROM users WHERE status = 'active'"
                        ),
                        Map.of(
                                "category", "统计查询",
                                "name", "记录计数",
                                "template", "SELECT COUNT(*) as total FROM {table_name}",
                                "description", "统计表中的记录总数",
                                "example", "SELECT COUNT(*) as total FROM users"
                        ),
                        Map.of(
                                "category", "统计查询",
                                "name", "分组统计",
                                "template", "SELECT {group_field}, COUNT(*) as count FROM {table_name} GROUP BY {group_field}",
                                "description", "按字段分组统计",
                                "example", "SELECT status, COUNT(*) as count FROM users GROUP BY status"
                        ),
                        Map.of(
                                "category", "系统信息",
                                "name", "数据库版本",
                                "template", "SELECT VERSION() as version",
                                "description", "查看数据库版本信息",
                                "example", "SELECT VERSION() as version"
                        ),
                        Map.of(
                                "category", "系统信息",
                                "name", "当前时间",
                                "template", "SELECT NOW() as current_time",
                                "description", "获取当前数据库时间",
                                "example", "SELECT NOW() as current_time"
                        )
                );

                templatesInfo.put("templates", templates);
                templatesInfo.put("totalCount", templates.size());
                templatesInfo.put("categories", templates.stream()
                        .map(t -> (String) t.get("category"))
                        .distinct()
                        .sorted()
                        .toList());
                templatesInfo.put("usage", "将模板中的 {参数} 替换为实际值，例如 {table_name} 替换为实际的表名");
                templatesInfo.put("lastUpdated", java.time.Instant.now().toString());

                String jsonContent = new ObjectMapper().writeValueAsString(templatesInfo);
                return new McpSchema.ReadResourceResult(
                        List.of(new McpSchema.TextResourceContents(request.uri(), "application/json", jsonContent)));
            } catch (Exception e) {
                log.error("Failed to generate SQL templates info: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to generate SQL templates info", e);
            }
        });
        resources.add(sqlTemplatesSpec);

        log.info("Initialized {} MCP resources", resources.size());
        return resources;
    }

    @Bean
    public List<McpServerFeatures.SyncPromptSpecification> myPrompts() {
        var prompt = new McpSchema.Prompt("greeting", "A friendly greeting prompt", List.of(new McpSchema.PromptArgument("name", "The name to greet", true)));

        var promptSpecification = new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, getPromptRequest) -> {
            String nameArgument = (String) getPromptRequest.arguments().get("name");
            if (nameArgument == null) {
                nameArgument = "friend";
            }
            var userMessage = new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent("Hello " + nameArgument + "! How can I assist you today?"));
            return new McpSchema.GetPromptResult("A personalized greeting message", List.of(userMessage));
        });

        return List.of(promptSpecification);
    }


/*    @Bean
    public List<McpServerFeatures.SyncCompletionSpecification> myCompletions() {
        var completion = new McpServerFeatures.SyncCompletionSpecification(
                "code-completion",
                "Provides code completion suggestions",
                (exchange, request) -> {
                    // Implementation that returns completion suggestions
                    return new McpSchema.CompleteResult(List.of(
                            new McpSchema.CompleteResult.CompleteCompletion ("suggestion1", "First suggestion"),
                            new McpSchema.Completion("suggestion2", "Second suggestion")
                    ));
                }
        );

        return List.of(completion);
    }*/

    @Bean
    public BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> rootsChangeHandler() {
        return (exchange, roots) -> {
            //log.info("Registering root resources: {}", roots);
        };
    }

    @Bean
    public ApplicationRunner extensionInfoLogger(GroovyService groovyService) {
        return args -> {
            log.info("=== Extension Information ===");
            groovyService.getAllExtensions();
            log.info("=== Extension Information End ===");
        };
    }

}
