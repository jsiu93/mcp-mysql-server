package org.jim.mcpmysqlserver.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库类型检测器测试类
 * 
 * @author yangxin
 */
class DatabaseTypeDetectorTest {

    @Test
    void testDetectMySQLDatabase() {
        String mysqlUrl = "jdbc:mysql://localhost:3306/testdb";
        DatabaseTypeDetector.DatabaseType dbType = DatabaseTypeDetector.detectDatabaseType(mysqlUrl);
        
        assertEquals(DatabaseTypeDetector.DatabaseType.MYSQL, dbType);
        assertEquals("com.mysql.cj.jdbc.Driver", DatabaseTypeDetector.getDriverClassName(mysqlUrl));
        assertEquals("MySQL", DatabaseTypeDetector.getDatabaseDisplayName(mysqlUrl));
        assertTrue(DatabaseTypeDetector.isSupportedDatabase(mysqlUrl));
    }

    @Test
    void testDetectPostgreSQLDatabase() {
        String postgresUrl = "jdbc:postgresql://localhost:5432/testdb";
        DatabaseTypeDetector.DatabaseType dbType = DatabaseTypeDetector.detectDatabaseType(postgresUrl);
        
        assertEquals(DatabaseTypeDetector.DatabaseType.POSTGRESQL, dbType);
        assertEquals("org.postgresql.Driver", DatabaseTypeDetector.getDriverClassName(postgresUrl));
        assertEquals("PostgreSQL", DatabaseTypeDetector.getDatabaseDisplayName(postgresUrl));
        assertTrue(DatabaseTypeDetector.isSupportedDatabase(postgresUrl));
    }

    @Test
    void testDetectOracleDatabase() {
        String oracleUrl = "jdbc:oracle:thin:@localhost:1521:testdb";
        DatabaseTypeDetector.DatabaseType dbType = DatabaseTypeDetector.detectDatabaseType(oracleUrl);
        
        assertEquals(DatabaseTypeDetector.DatabaseType.ORACLE, dbType);
        assertEquals("oracle.jdbc.OracleDriver", DatabaseTypeDetector.getDriverClassName(oracleUrl));
        assertEquals("Oracle", DatabaseTypeDetector.getDatabaseDisplayName(oracleUrl));
        assertTrue(DatabaseTypeDetector.isSupportedDatabase(oracleUrl));
    }

    @Test
    void testDetectSQLServerDatabase() {
        String sqlServerUrl = "jdbc:sqlserver://localhost:1433;databaseName=testdb";
        DatabaseTypeDetector.DatabaseType dbType = DatabaseTypeDetector.detectDatabaseType(sqlServerUrl);
        
        assertEquals(DatabaseTypeDetector.DatabaseType.SQL_SERVER, dbType);
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver", DatabaseTypeDetector.getDriverClassName(sqlServerUrl));
        assertEquals("SQL Server", DatabaseTypeDetector.getDatabaseDisplayName(sqlServerUrl));
        assertTrue(DatabaseTypeDetector.isSupportedDatabase(sqlServerUrl));
    }

    @Test
    void testDetectH2Database() {
        String h2Url = "jdbc:h2:mem:testdb";
        DatabaseTypeDetector.DatabaseType dbType = DatabaseTypeDetector.detectDatabaseType(h2Url);
        
        assertEquals(DatabaseTypeDetector.DatabaseType.H2, dbType);
        assertEquals("org.h2.Driver", DatabaseTypeDetector.getDriverClassName(h2Url));
        assertEquals("H2", DatabaseTypeDetector.getDatabaseDisplayName(h2Url));
        assertTrue(DatabaseTypeDetector.isSupportedDatabase(h2Url));
    }

    @Test
    void testDetectUnknownDatabase() {
        String unknownUrl = "jdbc:unknown://localhost:1234/testdb";
        DatabaseTypeDetector.DatabaseType dbType = DatabaseTypeDetector.detectDatabaseType(unknownUrl);
        
        assertEquals(DatabaseTypeDetector.DatabaseType.UNKNOWN, dbType);
        assertEquals("com.mysql.cj.jdbc.Driver", DatabaseTypeDetector.getDriverClassName(unknownUrl));
        assertEquals("Unknown", DatabaseTypeDetector.getDatabaseDisplayName(unknownUrl));
        assertFalse(DatabaseTypeDetector.isSupportedDatabase(unknownUrl));
    }

    @Test
    void testNullOrEmptyUrl() {
        // 测试null URL
        DatabaseTypeDetector.DatabaseType dbType1 = DatabaseTypeDetector.detectDatabaseType(null);
        assertEquals(DatabaseTypeDetector.DatabaseType.MYSQL, dbType1);
        assertEquals("com.mysql.cj.jdbc.Driver", DatabaseTypeDetector.getDriverClassName(null));
        
        // 测试空字符串URL
        DatabaseTypeDetector.DatabaseType dbType2 = DatabaseTypeDetector.detectDatabaseType("");
        assertEquals(DatabaseTypeDetector.DatabaseType.MYSQL, dbType2);
        assertEquals("com.mysql.cj.jdbc.Driver", DatabaseTypeDetector.getDriverClassName(""));
        
        // 测试空白字符串URL
        DatabaseTypeDetector.DatabaseType dbType3 = DatabaseTypeDetector.detectDatabaseType("   ");
        assertEquals(DatabaseTypeDetector.DatabaseType.MYSQL, dbType3);
        assertEquals("com.mysql.cj.jdbc.Driver", DatabaseTypeDetector.getDriverClassName("   "));
    }

    @Test
    void testCaseInsensitiveDetection() {
        // 测试大小写不敏感
        String upperCaseUrl = "JDBC:POSTGRESQL://localhost:5432/testdb";
        DatabaseTypeDetector.DatabaseType dbType = DatabaseTypeDetector.detectDatabaseType(upperCaseUrl);
        
        assertEquals(DatabaseTypeDetector.DatabaseType.POSTGRESQL, dbType);
        assertEquals("org.postgresql.Driver", DatabaseTypeDetector.getDriverClassName(upperCaseUrl));
    }
}
