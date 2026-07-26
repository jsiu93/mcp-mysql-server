package org.jim.mcpmysqlserver.mcp;

import org.jim.mcpmysqlserver.config.DataSourceConfig;
import org.jim.mcpmysqlserver.config.SqlSecurityConfig;
import org.jim.mcpmysqlserver.config.ToolResponseLimitConfig;
import org.jim.mcpmysqlserver.service.DataSourceService;
import org.jim.mcpmysqlserver.service.JdbcExecutor;
import org.jim.mcpmysqlserver.service.SqlResultCacheService;
import org.jim.mcpmysqlserver.validator.SqlSecurityValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [INPUT]: 依赖 MysqlOptionService 的 SQL 回包截断与缓存分页能力，以 StubJdbcExecutor 隔离真实数据库执行。
 * [OUTPUT]: 提供小结果透传、大结果截断、分页续取、过期与更新计数的单元回归测试。
 * [POS]: mcp 测试模块的结果预算语义守卫；每个用例独立创建服务与缓存，不共享可变状态。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 */
class MysqlOptionServiceTest {

    @Test
    void shouldReturnOriginalRowsWhenResultFitsBudget() {
        List<Map<String, Object>> rows = List.of(row("id", 1, "name", "alpha"));
        MysqlOptionService service = createService(500, 10, JdbcExecutor.SqlResult.success(rows));

        Map<String, Object> result = service.executeSqlWithDataSource("primary", "select 1");

        assertEquals(rows, result.get("primary"));
    }

    @Test
    void shouldReturnTruncatedPreviewAndFetchNextPage() {
        List<Map<String, Object>> rows = List.of(
                row("id", 1, "payload", "A".repeat(90)),
                row("id", 2, "payload", "B".repeat(90)),
                row("id", 3, "payload", "C".repeat(90))
        );
        MysqlOptionService service = createService(220, 10, JdbcExecutor.SqlResult.success(rows));

        Map<String, Object> result = service.executeSqlWithDataSource("primary", "select * from large_table");
        Map<String, Object> preview = castMap(result.get("primary"));

        assertTrue((Boolean) preview.get("truncated"));
        assertEquals("primary", preview.get("datasource"));
        assertEquals(rows.size(), preview.get("totalRows"));
        assertTrue((Integer) preview.get("returnedRows") >= 1);
        assertTrue((Boolean) preview.get("hasMore"));

        String resultId = (String) preview.get("resultId");
        assertNotNull(resultId);

        Map<String, Object> page = service.fetchSqlResultPage(resultId, (Integer) preview.get("nextOffset"), null);

        assertEquals(resultId, page.get("resultId"));
        assertTrue((Integer) page.get("returnedRows") >= 1);
        assertInstanceOf(List.class, page.get("page"));
    }

    @Test
    void shouldReturnEmptyPageWhenOffsetIsOutOfRange() {
        List<Map<String, Object>> rows = List.of(
                row("id", 1, "payload", "A".repeat(90)),
                row("id", 2, "payload", "B".repeat(90))
        );
        MysqlOptionService service = createService(220, 10, JdbcExecutor.SqlResult.success(rows));

        Map<String, Object> result = service.executeSqlWithDataSource("primary", "select * from t");
        Map<String, Object> preview = castMap(result.get("primary"));
        String resultId = (String) preview.get("resultId");

        Map<String, Object> page = service.fetchSqlResultPage(resultId, 999, null);

        assertEquals(0, page.get("returnedRows"));
        assertFalse((Boolean) page.get("hasMore"));
        assertEquals(List.of(), page.get("page"));
    }

    @Test
    void shouldReturnExpiredErrorWhenSnapshotExpired() {
        List<Map<String, Object>> rows = List.of(
                row("id", 1, "payload", "A".repeat(90)),
                row("id", 2, "payload", "B".repeat(90))
        );
        MysqlOptionService service = createService(220, 0, JdbcExecutor.SqlResult.success(rows));

        Map<String, Object> result = service.executeSqlWithDataSource("primary", "select * from expiring_table");
        Map<String, Object> preview = castMap(result.get("primary"));
        String resultId = (String) preview.get("resultId");

        Map<String, Object> page = service.fetchSqlResultPage(resultId, 0, null);

        assertEquals(false, page.get("success"));
        assertEquals("Result not found or expired", page.get("error"));
        assertEquals(resultId, page.get("resultId"));
    }

    @Test
    void shouldKeepUpdateCountUntouched() {
        MysqlOptionService service = createService(120, 10, JdbcExecutor.SqlResult.success(3));

        Map<String, Object> result = service.executeSqlWithDataSource("primary", "update t set a = 1");

        assertEquals(3, result.get("primary"));
    }

    private MysqlOptionService createService(int maxChars, long cacheTtlMinutes, JdbcExecutor.SqlResult sqlResult) {
        ToolResponseLimitConfig responseLimitConfig = new ToolResponseLimitConfig();
        responseLimitConfig.setEnabled(true);
        responseLimitConfig.setMaxChars(maxChars);
        responseLimitConfig.setCacheTtlMinutes(cacheTtlMinutes);
        responseLimitConfig.setCacheMaxEntries(10);
        responseLimitConfig.setMinPreviewRows(1);

        SqlSecurityConfig sqlSecurityConfig = new SqlSecurityConfig();
        sqlSecurityConfig.setEnabled(false);
        SqlSecurityValidator sqlSecurityValidator = new SqlSecurityValidator(sqlSecurityConfig);

        DataSourceService dataSourceService = new StubDataSourceService();
        SqlResultCacheService cacheService = new SqlResultCacheService(responseLimitConfig);
        JdbcExecutor jdbcExecutor = new StubJdbcExecutor(sqlResult);
        return new MysqlOptionService(dataSourceService, responseLimitConfig, cacheService, sqlSecurityValidator, jdbcExecutor);
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> row = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(String.valueOf(values[index]), values[index + 1]);
        }
        return row;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static final class StubJdbcExecutor extends JdbcExecutor {

        private final SqlResult sqlResult;

        private StubJdbcExecutor(SqlResult sqlResult) {
            this.sqlResult = sqlResult;
        }

        @Override
        public SqlResult executeSql(DataSource dataSource, String sql) {
            return sqlResult;
        }
    }

    private static final class StubDataSourceService extends DataSourceService {

        private final DataSource dataSource = new NoopDataSource();

        private StubDataSourceService() {
            super(new DataSourceConfig(), new StaticApplicationContext());
        }

        @Override
        public DataSource getDataSource(String name) {
            return dataSource;
        }

        @Override
        public String getDefaultDataSourceName() {
            return "primary";
        }

        @Override
        public List<String> getDataSourceNames() {
            return List.of("primary");
        }
    }

    private static final class NoopDataSource extends AbstractDataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new UnsupportedOperationException("Not used in stub executor");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException("Not used in stub executor");
        }
    }
}
