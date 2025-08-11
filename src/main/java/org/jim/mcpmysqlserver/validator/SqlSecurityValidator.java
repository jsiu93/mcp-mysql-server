package org.jim.mcpmysqlserver.validator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jim.mcpmysqlserver.config.SqlSecurityConfig;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * SQL安全验证器
 * 用于检查SQL语句是否包含危险操作关键字
 * @author yangxin
 */
@Component
@Slf4j
public class SqlSecurityValidator {

    private final SqlSecurityConfig sqlSecurityConfig;

    public SqlSecurityValidator(SqlSecurityConfig sqlSecurityConfig) {
        this.sqlSecurityConfig = sqlSecurityConfig;
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

        // 检查是否包含危险关键字
        for (String keyword : sqlSecurityConfig.getDangerousKeywords()) {
            if (containsDangerousKeyword(cleanedSql, keyword)) {
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
     * 检查SQL中是否包含指定的危险关键字
     * @param sql 清理后的SQL语句
     * @param keyword 危险关键字
     * @return 是否包含危险关键字
     */
    private boolean containsDangerousKeyword(String sql, String keyword) {
        if (StringUtils.isBlank(sql) || StringUtils.isBlank(keyword)) {
            return false;
        }

        // 构建词边界正则表达式，确保匹配完整单词而不是子字符串
        // 例如：避免在 "description" 中误匹配 "update"
        String pattern = "\\b" + Pattern.quote(keyword.toLowerCase()) + "\\b";
        Pattern compiledPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

        return compiledPattern.matcher(sql.toLowerCase()).find();
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
