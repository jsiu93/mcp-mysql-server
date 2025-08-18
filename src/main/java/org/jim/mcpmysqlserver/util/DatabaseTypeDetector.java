package org.jim.mcpmysqlserver.util;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据库类型检测工具类
 * 根据JDBC URL自动检测数据库类型并返回相应的驱动类名
 * 
 * @author yangxin
 */
@Slf4j
public class DatabaseTypeDetector {

    /**
     * 数据库类型枚举
     */
    public enum DatabaseType {
        MYSQL("MySQL", "com.mysql.cj.jdbc.Driver"),
        POSTGRESQL("PostgreSQL", "org.postgresql.Driver"),
        ORACLE("Oracle", "oracle.jdbc.OracleDriver"),
        SQL_SERVER("SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver"),
        H2("H2", "org.h2.Driver"),
        IOTDB("Apache IoTDB", "org.apache.iotdb.jdbc.IoTDBDriver"),
        UNKNOWN("Unknown", "com.mysql.cj.jdbc.Driver"); // 默认使用MySQL驱动

        private final String displayName;
        private final String driverClassName;

        DatabaseType(String displayName, String driverClassName) {
            this.displayName = displayName;
            this.driverClassName = driverClassName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDriverClassName() {
            return driverClassName;
        }
    }

    /**
     * 根据JDBC URL检测数据库类型
     * 
     * @param jdbcUrl JDBC连接URL
     * @return 数据库类型
     */
    public static DatabaseType detectDatabaseType(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            log.warn("JDBC URL为空，返回默认数据库类型: {}", DatabaseType.MYSQL.getDisplayName());
            return DatabaseType.MYSQL;
        }

        String lowerUrl = jdbcUrl.toLowerCase().trim();
        
        if (lowerUrl.startsWith("jdbc:postgresql:")) {
            log.info("检测到PostgreSQL数据库URL");
            return DatabaseType.POSTGRESQL;
        } else if (lowerUrl.startsWith("jdbc:mysql:")) {
            log.info("检测到MySQL数据库URL");
            return DatabaseType.MYSQL;
        } else if (lowerUrl.startsWith("jdbc:oracle:")) {
            log.info("检测到Oracle数据库URL");
            return DatabaseType.ORACLE;
        } else if (lowerUrl.startsWith("jdbc:sqlserver:")) {
            log.info("检测到SQL Server数据库URL");
            return DatabaseType.SQL_SERVER;
        } else if (lowerUrl.startsWith("jdbc:h2:")) {
            log.info("检测到H2数据库URL");
            return DatabaseType.H2;
        } else if (lowerUrl.startsWith("jdbc:iotdb:")) {
            log.info("检测到Apache IoTDB数据库URL");
            return DatabaseType.IOTDB;
        } else {
            log.warn("无法识别数据库类型，URL: {}，返回默认类型: {}", jdbcUrl, DatabaseType.MYSQL.getDisplayName());
            return DatabaseType.UNKNOWN;
        }
    }

    /**
     * 根据JDBC URL获取驱动类名
     * 
     * @param jdbcUrl JDBC连接URL
     * @return 驱动类名
     */
    public static String getDriverClassName(String jdbcUrl) {
        DatabaseType dbType = detectDatabaseType(jdbcUrl);
        String driverClassName = dbType.getDriverClassName();
        log.info("为数据库类型 {} 返回驱动类名: {}", dbType.getDisplayName(), driverClassName);
        return driverClassName;
    }

    /**
     * 检查指定的数据库类型是否支持
     * 
     * @param jdbcUrl JDBC连接URL
     * @return 是否支持该数据库类型
     */
    public static boolean isSupportedDatabase(String jdbcUrl) {
        DatabaseType dbType = detectDatabaseType(jdbcUrl);
        boolean isSupported = dbType != DatabaseType.UNKNOWN;
        log.info("数据库类型 {} 支持状态: {}", dbType.getDisplayName(), isSupported ? "支持" : "不支持");
        return isSupported;
    }

    /**
     * 获取数据库类型的显示名称
     * 
     * @param jdbcUrl JDBC连接URL
     * @return 数据库类型显示名称
     */
    public static String getDatabaseDisplayName(String jdbcUrl) {
        DatabaseType dbType = detectDatabaseType(jdbcUrl);
        return dbType.getDisplayName();
    }
}
