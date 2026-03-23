package org.jim.mcpmysqlserver.validator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jim.mcpmysqlserver.config.SqlSecurityConfig;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQL安全验证器
 * 用于检查SQL语句是否包含危险操作关键字
 * @author yangxin
 */
@Component
@Slf4j
public class SqlSecurityValidator {

    private final SqlSecurityConfig sqlSecurityConfig;
    // 启动时预编译所有关键字的正则，避免每次验证重复编译
    private final Map<String, Pattern> keywordPatterns;

    public SqlSecurityValidator(SqlSecurityConfig sqlSecurityConfig) {
        this.sqlSecurityConfig = sqlSecurityConfig;
        this.keywordPatterns = sqlSecurityConfig.getDangerousKeywords().stream()
                .collect(Collectors.toMap(
                        k -> k,
                        k -> Pattern.compile("\\b" + Pattern.quote(k.toLowerCase()) + "\\b", Pattern.CASE_INSENSITIVE)
                ));
        log.info("SQL安全验证器初始化完成，预编译 {} 个关键字正则", keywordPatterns.size());
    }

    /**
     * 验证SQL语句是否安全
     * @param sql 要验证的SQL语句
     * @return SQL安全验证结果
     */
    public SqlValidationResult validateSql(String sql) {
        // 如果未启用安全检查，直接通过
        if (!sqlSecurityConfig.isEnabled()) {
            log.debug("SQL security check is disabled, allowing SQL execution: {}", sql);
            return SqlValidationResult.success();
        }

        // 检查SQL是否为空
        if (StringUtils.isBlank(sql)) {
            return SqlValidationResult.failure("SQL statement cannot be empty", null);
        }

        // 清理SQL语句：去除多余空格、换行符、注释等
        String cleanedSql = cleanSql(sql);
        log.debug("Validating SQL: {}", cleanedSql);

        // 检查是否包含危险关键字（使用预编译正则）
        for (Map.Entry<String, Pattern> entry : keywordPatterns.entrySet()) {
            if (entry.getValue().matcher(cleanedSql).find()) {
                String keyword = entry.getKey();
                String errorMessage = String.format(
                        """
                        Dangerous SQL operation keyword '%s' detected. This operation has been blocked for data security.
                        To execute this type of operation, please configure in application.yml:
                        1) Set sql.security.enabled=false to completely disable SQL security checks, or
                        2) Remove the '%s' keyword from the sql.security.dangerous-keywords list.
                        Please restart the service after modifying the configuration.
                        """,
                        keyword.toUpperCase(), keyword.toLowerCase()
                );

                log.warn("SQL validation failed: detected dangerous keyword '{}' in SQL: {}", keyword, cleanedSql);
                return SqlValidationResult.failure(errorMessage, keyword);
            }
        }

        log.debug("SQL validation passed: {}", cleanedSql);
        return SqlValidationResult.success();
    }

    /**
     * 清理SQL语句，移除注释、多余空格等
     * @param sql 原始SQL语句
     * @return 清理后的SQL语句
     */
    private String cleanSql(String sql) {
        if (StringUtils.isBlank(sql)) {
            return "";
        }

        // 移除单行注释 (-- 注释)
        sql = sql.replaceAll("--[^\r\n]*", "");

        // 移除多行注释 (/* 注释 */)
        sql = sql.replaceAll("/\\*.*?\\*/", "");

        // 统一换行符并移除多余空格
        sql = sql.replaceAll("\\s+", " ").trim();

        return sql;
    }

    /**
     * SQL验证结果类
     */
    public record SqlValidationResult(boolean valid, String errorMessage, String detectedKeyword) {

        public static SqlValidationResult success() {
            return new SqlValidationResult(true, null, null);
        }

        public static SqlValidationResult failure(String errorMessage, String detectedKeyword) {
            return new SqlValidationResult(false, errorMessage, detectedKeyword);
        }
    }
}
