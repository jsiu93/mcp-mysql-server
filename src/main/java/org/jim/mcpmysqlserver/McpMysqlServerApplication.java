package org.jim.mcpmysqlserver;

import org.jim.mcpmysqlserver.mcp.MysqlOptionService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * @author yangxin
 */
@SpringBootApplication
public class McpMysqlServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpMysqlServerApplication.class, args);
    }


    @Bean
    public ToolCallbackProvider mysqlToolCallbackProvider(MysqlOptionService mysqlOptionService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mysqlOptionService)
                .build();
    }
}
