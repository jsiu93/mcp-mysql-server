package org.jim.mcpmysqlserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tool 返回限制配置，负责约束单次 SQL 结果回包大小与缓存生命周期。
 *
 *
 * @author Codex
 */
@Data
@Component
@ConfigurationProperties(prefix = "tool.response-limit")
public class ToolResponseLimitConfig {

    /**
     * 是否启用 SQL 结果截断与续取能力。
     */
    private boolean enabled = true;

    /**
     * 单次 tool 返回允许的最大字符数。
     */
    private int maxChars = 12000;

    /**
     * 缓存中允许保留的最大截断结果数量。
     */
    private int cacheMaxEntries = 100;

    /**
     * 截断结果在内存中的缓存时长，单位分钟。
     */
    private long cacheTtlMinutes = 10;

    /**
     * 预览页至少返回的行数，避免首屏完全为空。
     */
    private int minPreviewRows = 1;
}
