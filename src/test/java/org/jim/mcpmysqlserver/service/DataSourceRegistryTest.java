package org.jim.mcpmysqlserver.service;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * [INPUT]: 依赖 DataSourceRegistry 的注册、默认库切换、配置快照与连接池生命周期能力，
 *          依赖 SQLite 内存库提供可重复的真实 JDBC 建连环境。
 * [OUTPUT]: 提供注册表启动、校验、替换、摘除、元信息脱敏的单元回归测试。
 * [POS]: service 测试模块的数据源生命周期边界守卫，确保失败注册具备原子性且配置快照保持用户原貌。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 */
class DataSourceRegistryTest {

    private final List<DataSourceRegistry> registries = new ArrayList<>();

    @AfterEach
    void shutdownRegistries() {
        registries.forEach(DataSourceRegistry::shutdown);
    }

    @Test
    void bootstrapRegistersAllEntriesHonoursPreferredDefaultAndFallsBackToFirstConfiguredEntry() {
        LinkedHashMap<String, Map<String, Object>> preferredConfig = new LinkedHashMap<>();
        preferredConfig.put("first", sqliteProperties());
        Map<String, Object> flaggedDefault = sqliteProperties();
        flaggedDefault.put("default", true);
        preferredConfig.put("flagged", flaggedDefault);

        DataSourceRegistry preferredRegistry = newRegistry();
        preferredRegistry.bootstrap(preferredConfig, "flagged");

        assertEquals(List.of("first", "flagged"), preferredRegistry.names());
        assertEquals("flagged", preferredRegistry.getDefaultName());

        LinkedHashMap<String, Map<String, Object>> fallbackConfig = new LinkedHashMap<>();
        fallbackConfig.put("z-first-in-yaml", sqliteProperties());
        fallbackConfig.put("a-second-in-yaml", sqliteProperties());

        DataSourceRegistry absentPreferredRegistry = newRegistry();
        absentPreferredRegistry.bootstrap(fallbackConfig, null);
        assertEquals("z-first-in-yaml", absentPreferredRegistry.getDefaultName());

        DataSourceRegistry unknownPreferredRegistry = newRegistry();
        unknownPreferredRegistry.bootstrap(fallbackConfig, "missing");
        assertEquals("z-first-in-yaml", unknownPreferredRegistry.getDefaultName());
    }

    @Test
    void registerWithValidationSucceedsForSqliteMemoryDatabase() {
        DataSourceRegistry registry = newRegistry();

        registry.register("sqlite", sqliteProperties(), true);

        HikariDataSource dataSource = assertInstanceOf(
                HikariDataSource.class,
                registry.getDataSource("sqlite")
        );
        assertFalse(dataSource.isClosed());
    }

    @Test
    void registerWithFailedValidationLeavesRegistryUnchanged() {
        DataSourceRegistry registry = newRegistry();
        registry.bootstrap(singleConfig("stable", sqliteProperties()), "stable");
        DataSource stableDataSource = registry.getDataSource("stable");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> registry.register("unreachable", unreachablePostgresProperties(), true)
        );

        assertTrue(error.getMessage().contains("连接失败"));
        assertFalse(registry.names().contains("unreachable"));
        assertEquals(List.of("stable"), registry.names());
        assertEquals("stable", registry.getDefaultName());
        assertSame(stableDataSource, registry.getDataSource("stable"));
    }

    @Test
    void registerWithUnknownDriverReturnsActionableError() {
        DataSourceRegistry registry = newRegistry();
        Map<String, Object> properties = sqliteProperties();
        properties.put("driver-class-name", "com.example.NoSuchDriver");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> registry.register("unknown-driver", properties, true)
        );

        assertTrue(error.getMessage().contains("com.example.NoSuchDriver"));
        assertTrue(error.getMessage().contains("pom.xml"));
        assertTrue(error.getMessage().contains("重新构建"));
        assertFalse(registry.names().contains("unknown-driver"));
    }

    @Test
    void snapshotPropertiesPreservesExactUserPropertiesWithoutDetectedDriver() {
        DataSourceRegistry registry = newRegistry();
        Map<String, Object> originalProperties = sqliteProperties();

        registry.register("sqlite", originalProperties, false);

        Map<String, Object> snapshot = registry.snapshotProperties().get("sqlite");
        assertNotNull(snapshot);
        assertEquals(originalProperties, snapshot);
        assertFalse(snapshot.containsKey("driver-class-name"));
        assertFalse(originalProperties.containsKey("driver-class-name"));
    }

    @Test
    void unregisterRejectsCurrentDefaultRemovesNonDefaultAndReturnsNullForUnknownName() {
        DataSourceRegistry registry = newRegistry();
        LinkedHashMap<String, Map<String, Object>> configured = new LinkedHashMap<>();
        configured.put("default-db", sqliteProperties());
        configured.put("removable-db", sqliteProperties());
        registry.bootstrap(configured, "default-db");

        assertThrows(IllegalStateException.class, () -> registry.unregister("default-db"));

        DataSourceRegistry.Entry removed = registry.unregister("removable-db");
        assertNotNull(removed);
        assertEquals("removable-db", removed.name());
        assertTrue(removed.dataSource().isClosed());
        assertEquals(List.of("default-db"), registry.names());
        assertNull(registry.unregister("unknown-db"));
    }

    @Test
    void setDefaultNameRejectsUnregisteredNameAndSwitchesToRegisteredDataSource() {
        DataSourceRegistry registry = newRegistry();
        LinkedHashMap<String, Map<String, Object>> configured = new LinkedHashMap<>();
        configured.put("db1", sqliteProperties());
        configured.put("db2", sqliteProperties());
        registry.bootstrap(configured, "db1");
        DataSource db2 = registry.getDataSource("db2");

        assertThrows(IllegalStateException.class, () -> registry.setDefaultName("missing"));

        registry.setDefaultName("db2");
        assertEquals("db2", registry.getDefaultName());
        assertSame(db2, registry.getDefaultDataSource());
    }

    @Test
    void detailsExposeOnlySafeMetadataAndNeverLeakCredentials() {
        DataSourceRegistry registry = newRegistry();
        String password = "top-secret-password";
        Map<String, Object> properties = sqliteProperties();
        properties.put("username", "test-user");
        properties.put("password", password);
        registry.register("secured", properties, false);

        Set<String> allowedKeys = Set.of("name", "databaseType", "driverClassName", "isDefault");
        List<Map<String, Object>> details = registry.details();

        assertEquals(1, details.size());
        for (Map<String, Object> detail : details) {
            assertTrue(allowedKeys.containsAll(detail.keySet()));
            for (Object value : detail.values()) {
                assertNotEquals(password, value);
            }
        }
    }

    @Test
    void registerWithSameNameReplacesPoolAndClosesPreviousPoolImmediately() {
        DataSourceRegistry registry = newRegistry();
        registry.register("replaceable", sqliteProperties(), false);
        HikariDataSource first = assertInstanceOf(
                HikariDataSource.class,
                registry.getDataSource("replaceable")
        );

        registry.register("replaceable", sqliteProperties(), false);
        HikariDataSource second = assertInstanceOf(
                HikariDataSource.class,
                registry.getDataSource("replaceable")
        );

        assertNotSame(first, second);
        assertTrue(first.isClosed());
        assertFalse(second.isClosed());
    }

    private DataSourceRegistry newRegistry() {
        DataSourceRegistry registry = new DataSourceRegistry(0L);
        registries.add(registry);
        return registry;
    }

    private static LinkedHashMap<String, Map<String, Object>> singleConfig(
            String name,
            Map<String, Object> properties
    ) {
        LinkedHashMap<String, Map<String, Object>> configured = new LinkedHashMap<>();
        configured.put(name, properties);
        return configured;
    }

    private static Map<String, Object> sqliteProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", "jdbc:sqlite::memory:");
        return properties;
    }

    private static Map<String, Object> unreachablePostgresProperties() {
        Map<String, Object> hikari = new LinkedHashMap<>();
        hikari.put("connection-timeout", 1000L);

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", "jdbc:postgresql://127.0.0.1:1/nope");
        properties.put("username", "test");
        properties.put("password", "test");
        properties.put("hikari", hikari);
        return properties;
    }
}
