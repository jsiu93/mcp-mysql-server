package org.jim.mcpmysqlserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SQL安全配置类
 * @author yangxin
 */
@Data
@Component
@ConfigurationProperties(prefix = "sql.security")
public class SqlSecurityConfig {

    /**
     * 是否启用SQL安全检查
     */
    private boolean enabled = true;

    /**
     * 危险操作关键字列表（不区分大小写）
     */
    private List<String> dangerousKeywords = List.of(
            "update", "delete", "insert", "replace",
            "drop", "create", "alter", "truncate",
            "grant", "revoke", "shutdown", "restart",
            "call", "execute", "commit", "rollback"
    );
}
