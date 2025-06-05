package org.jim.mcpmysqlserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.mcp.MysqlOptionService;
import org.jim.mcpmysqlserver.util.PortUtils;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

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
            log.warn("Port {} is already in use. Trying to start with a random available port...", port);
            // 获取一个随机可用端口
            int randomPort = PortUtils.findAvailablePort();
            if (randomPort <= 0) {
                log.error("Failed to find an available port. Exiting...");
                System.exit(1);
                return;
            }

            // 设置系统属性，使Spring Boot使用新的端口
            log.info("Using random available port: {}", randomPort);
            System.setProperty("server.port", String.valueOf(randomPort));
        }

        // 获取最终使用的端口号（可能是随机分配的）
        String finalPort = System.getProperty("server.port", String.valueOf(port));
        log.info("Starting application on port {}", finalPort);
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
                        log.warn("Invalid port number in argument: {}", arg);
                    }
                }
                // 检查-Dserver.port=xxx格式
                else if (arg.startsWith("-Dserver.port=")) {
                    try {
                        return Integer.parseInt(arg.substring("-Dserver.port=".length()));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid port number in argument: {}", arg);
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
                log.warn("Invalid port number in system property: {}", systemPort);
            }
        }

        // 返回默认端口
        return defaultPort;
    }


    @Bean
    public ToolCallbackProvider mysqlToolCallbackProvider(MysqlOptionService mysqlOptionService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mysqlOptionService)
                .build();
    }

    @Bean
    public List<McpServerFeatures.SyncResourceSpecification> myResources() {
        McpSchema.Annotations annotations = new McpSchema.Annotations(
                List.of(McpSchema.Role.USER, McpSchema.Role.ASSISTANT), 0.9
        );

        var systemInfoResource = new McpSchema.Resource("file:///Users/xin.y/Documents/table.json", "测试数据库与表信息", "列出数据库与表信息", "application/json", annotations);
        var resourceSpecification = new McpServerFeatures.SyncResourceSpecification(systemInfoResource, (exchange, request) -> {
            try {
                var systemInfo = Map.of("test1", "test1111");
                String jsonContent = new ObjectMapper().writeValueAsString(systemInfo);
                return new McpSchema.ReadResourceResult(
                        List.of(new McpSchema.TextResourceContents(request.uri(), "application/json", jsonContent)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate system info", e);
            }
        });


        return List.of(resourceSpecification);
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
            log.info("Registering root resources: {}", roots);
        };
    }

}
