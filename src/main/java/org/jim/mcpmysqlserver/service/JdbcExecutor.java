package org.jim.mcpmysqlserver.service;

import lombok.extern.slf4j.Slf4j;
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
import java.util.Base64;
import java.util.stream.IntStream;
import java.nio.charset.StandardCharsets;
import org.apache.tsfile.utils.Binary;

/**
 * JDBC执行器服务，负责处理所有JDBC相关操作
 * 使用JDK8 Stream API处理数据
 * @author yangxin
 */
@Service
@Slf4j
public class JdbcExecutor {


    /**
     * 在指定数据源上执行SQL语句
     *
     * @param dataSource 数据源
     * @param sql SQL语句
     * @return SQL执行结果
     */
    public SqlResult executeSql(DataSource dataSource, String sql) {
        log.debug("Executing SQL: {}", sql);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            boolean hasResultSet = stmt.execute(sql);

            if (hasResultSet) {
                // 处理查询结果
                try (ResultSet rs = stmt.getResultSet()) {
                    List<Map<String, Object>> resultList = processResultSet(rs);
                    log.debug("Query executed successfully, returned {} rows", resultList.size());
                    return SqlResult.success(resultList);
                }
            } else {
                // 处理更新结果
                int updateCount = stmt.getUpdateCount();
                log.debug("SQL execution completed, affected rows: {}", updateCount);
                return SqlResult.success(updateCount);
            }
        } catch (SQLException e) {
            log.error("SQL execution error: {}", e.getMessage(), e);
            return SqlResult.error(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during SQL execution: {}", e.getMessage(), e);
            return SqlResult.error(e.getMessage());
        }
    }

    /**
     * 处理ResultSet并转换为List<Map<String, Object>>
     * 使用JDK8 Stream API处理数据
     *
     * @param rs 结果集
     * @return 结果列表
     */
    private List<Map<String, Object>> processResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        // 处理列名
        List<String> columnNames = IntStream.rangeClosed(1, columnCount)
                .mapToObj(i -> {
                    try {
                        return metaData.getColumnLabel(i);
                    } catch (SQLException e) {
                        log.error("Error getting column label for index {}: {}", i, e.getMessage());
                        return "column_" + i; // fallback column name
                    }
                })
                .toList();

        List<Map<String, Object>> resultList = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new HashMap<>(columnCount);
            for (int i = 0; i < columnCount; i++) {
                row.put(columnNames.get(i), normalizeValue(rs.getObject(i + 1)));
            }
            resultList.add(row);
        }

        return resultList;
    }

    /**
     * 统一处理特殊类型的值，例如 IoTDB 的 Binary。
     * null 保持为 null，由 Jackson 序列化为 JSON null
     */
    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }

        // IoTDB 1.x typically returns org.apache.tsfile.utils.Binary; some versions use org.apache.iotdb.tsfile.utils.Binary
        if (value instanceof Binary tsBinary) {
            return decodeBinary(tsBinary.getValues());
        }
        if (value instanceof org.apache.iotdb.tsfile.utils.Binary iotdbBinary) {
            return decodeBinary(iotdbBinary.getValues());
        }

        return value;
    }

    private String decodeBinary(byte[] rawBytes) {
        try {
            log.debug("Decoding IoTDB Binary raw bytes: {}", rawBytes.length);
            return new String(rawBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decode IoTDB Binary to UTF-8 string: {}", e.getMessage());
            return Base64.getEncoder().encodeToString(rawBytes);
        }
    }

    /**
     * SQL执行结果封装类
     */
    public record SqlResult(boolean success, Object data, String errorMessage) {

        public static SqlResult success(Object data) {
            return new SqlResult(true, data, null);
        }

        public static SqlResult error(String errorMessage) {
            return new SqlResult(false, null, errorMessage);
        }

    }


}
