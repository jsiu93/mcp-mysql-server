package org.jim.mcpmysqlserver.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @author yangxin
 */
@Service
@Slf4j
public class MysqlOptionService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlOptionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Tool(description = "Execute SQL query and return the result as a string.")
    public String executeSql(String sql) {
        log.info("Executing SQL: {}", sql);
        try {
            // 判断SQL类型
            String sqlType = getSqlType(sql);

            if ("SELECT".equals(sqlType)) {
                // 查询操作，返回结果集
                List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
                return formatResults(results);
            } else {
                // 非查询操作，返回影响的行数
                int rowsAffected = jdbcTemplate.update(sql);
                return String.format("操作成功，影响了 %d 行数据", rowsAffected);
            }
        } catch (Exception e) {
            log.error("执行SQL出错: {}", e.getMessage(), e);
            return "SQL执行错误: " + e.getMessage();
        }
    }

    private String getSqlType(String sql) {
        String trimmedSql = sql.trim().toUpperCase();
        if (trimmedSql.startsWith("SELECT")) {
            return "SELECT";
        } else if (trimmedSql.startsWith("INSERT")) {
            return "INSERT";
        } else if (trimmedSql.startsWith("UPDATE")) {
            return "UPDATE";
        } else if (trimmedSql.startsWith("DELETE")) {
            return "DELETE";
        } else {
            return "OTHER";
        }
    }

    private String formatResults(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "查询结果为空";
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(results);
        } catch (JsonProcessingException e) {
            log.error("格式化结果出错", e);
            return "结果格式化错误: " + e.getMessage();
        }
    }
}
