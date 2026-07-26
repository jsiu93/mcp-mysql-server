package org.jim.mcpmysqlserver.config;

import com.zaxxer.hikari.HikariDataSource;
import org.jim.mcpmysqlserver.service.DataSourceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * [INPUT]: 依赖 DataSourceReloadService 的差异施加流程、DataSourceRegistry 的运行期状态，
 *          依赖 Mockito 隔离 DataSourceConfigLoader 的磁盘读取边界。
 * [OUTPUT]: 提供重载分类、整体中止、部分成功与默认库切换顺序的单元回归测试。
 * [POS]: config 测试模块的热重载原子性守卫，证明未变连接池保持身份且旧默认库可安全摘除。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 */
class DataSourceReloadServiceTest {

    private final List<DataSourceRegistry> registries = new ArrayList<>();

    @AfterEach
    void shutdownRegistries() {
        registries.forEach(DataSourceRegistry::shutdown);
    }

    @Test
    void reloadClassifiesAddedRemovedUnchangedAndUpdatedWithoutChurningUnchangedPool() {
        DataSourceRegistry registry = newRegistry();
        Map<String, Object> unchangedProperties = sqliteProperties();
        unchangedProperties.put("default", true);

        LinkedHashMap<String, Map<String, Object>> current = new LinkedHashMap<>();
        current.put("unchanged", unchangedProperties);
        current.put("updated", sqliteProperties());
        current.put("removed", sqliteProperties());
        registry.bootstrap(current, "unchanged");

        DataSource unchangedBefore = registry.getDataSource("unchanged");
        HikariDataSource updatedBefore = hikariDataSource(registry, "updated");
        HikariDataSource removedBefore = hikariDataSource(registry, "removed");

        LinkedHashMap<String, Map<String, Object>> desired = new LinkedHashMap<>();
        desired.put("unchanged", new LinkedHashMap<>(unchangedProperties));
        desired.put("updated", sqlitePropertiesWithMaximumPoolSize(4));
        desired.put("added", sqliteProperties());

        Map<String, Object> report = reloadService(registry, desired).reload();

        assertTrue((Boolean) report.get("success"));
        assertEquals(List.of("added"), report.get("added"));
        assertEquals(List.of("updated"), report.get("updated"));
        assertEquals(List.of("removed"), report.get("removed"));
        assertEquals(List.of("unchanged"), report.get("unchanged"));
        assertTrue(assertInstanceOf(Map.class, report.get("failed")).isEmpty());
        assertSame(unchangedBefore, registry.getDataSource("unchanged"));
        assertTrue(updatedBefore.isClosed());
        assertTrue(removedBefore.isClosed());
    }

    @Test
    void reloadAbortsAndPreservesRegistryWhenDiskReadThrows() {
        DataSourceRegistry registry = registryWithTwoDataSources();
        List<String> namesBefore = registry.names();
        String defaultBefore = registry.getDefaultName();
        Map<String, Map<String, Object>> propertiesBefore = registry.snapshotProperties();
        Map<String, DataSource> identitiesBefore = captureDataSourceIdentities(registry);

        DataSourceConfigLoader configLoader = mock(DataSourceConfigLoader.class);
        when(configLoader.getConfigPath()).thenReturn("broken-datasource.yml");
        when(configLoader.readDataSourcesFromDisk())
                .thenThrow(new IllegalStateException("invalid yaml"));

        Map<String, Object> report = new DataSourceReloadService(registry, configLoader).reload();

        assertFalse((Boolean) report.get("success"));
        assertNotNull(report.get("error"));
        assertFalse(report.containsKey("added"));
        assertFalse(report.containsKey("updated"));
        assertFalse(report.containsKey("removed"));
        assertRegistryStateUnchanged(
                registry,
                namesBefore,
                defaultBefore,
                propertiesBefore,
                identitiesBefore
        );
    }

    @Test
    void reloadAbortsAndPreservesRegistryWhenDesiredConfigIsEmpty() {
        DataSourceRegistry registry = registryWithTwoDataSources();
        List<String> namesBefore = registry.names();
        String defaultBefore = registry.getDefaultName();
        Map<String, Map<String, Object>> propertiesBefore = registry.snapshotProperties();
        Map<String, DataSource> identitiesBefore = captureDataSourceIdentities(registry);

        Map<String, Object> report = reloadService(registry, new LinkedHashMap<>()).reload();

        assertFalse((Boolean) report.get("success"));
        assertNotNull(report.get("error"));
        assertFalse(report.containsKey("added"));
        assertFalse(report.containsKey("updated"));
        assertFalse(report.containsKey("removed"));
        assertRegistryStateUnchanged(
                registry,
                namesBefore,
                defaultBefore,
                propertiesBefore,
                identitiesBefore
        );
    }

    @Test
    void reloadAppliesValidDatasourceAndReportsInvalidDatasourceAsPartialFailure() {
        DataSourceRegistry registry = newRegistry();
        LinkedHashMap<String, Map<String, Object>> desired = new LinkedHashMap<>();
        desired.put("good", sqliteProperties());
        desired.put("bad", unreachablePostgresProperties());

        Map<String, Object> report = reloadService(registry, desired).reload();

        assertFalse((Boolean) report.get("success"));
        assertEquals(List.of("good"), report.get("added"));
        Map<?, ?> failed = assertInstanceOf(Map.class, report.get("failed"));
        assertTrue(failed.containsKey("bad"));
        assertFalse(String.valueOf(failed.get("bad")).isBlank());
        assertNotNull(registry.getDataSource("good"));
        assertFalse(registry.names().contains("bad"));
    }

    @Test
    void reloadSwitchesDefaultAndReportsPreviousDefault() {
        DataSourceRegistry registry = registryWithTwoDataSources();
        LinkedHashMap<String, Map<String, Object>> desired = new LinkedHashMap<>();
        desired.put("db1", sqliteProperties());
        Map<String, Object> db2Properties = sqliteProperties();
        db2Properties.put("default", true);
        desired.put("db2", db2Properties);

        Map<String, Object> report = reloadService(registry, desired).reload();

        assertTrue((Boolean) report.get("success"));
        assertEquals("db2", registry.getDefaultName());
        assertEquals("db1", report.get("defaultChangedFrom"));
        assertSame(registry.getDataSource("db2"), registry.getDefaultDataSource());
    }

    @Test
    void reloadSwitchesDefaultBeforeRemovingOldDefault() {
        DataSourceRegistry registry = registryWithTwoDataSources();
        HikariDataSource oldDefault = hikariDataSource(registry, "db1");

        LinkedHashMap<String, Map<String, Object>> desired = new LinkedHashMap<>();
        desired.put("db2", sqliteProperties());

        Map<String, Object> report = reloadService(registry, desired).reload();

        assertTrue((Boolean) report.get("success"));
        assertEquals(List.of("db1"), report.get("removed"));
        assertEquals("db2", registry.getDefaultName());
        assertTrue(assertInstanceOf(Map.class, report.get("failed")).isEmpty());
        assertFalse(registry.names().contains("db1"));
        assertTrue(oldDefault.isClosed());
    }

    private DataSourceRegistry newRegistry() {
        DataSourceRegistry registry = new DataSourceRegistry(0L);
        registries.add(registry);
        return registry;
    }

    private DataSourceRegistry registryWithTwoDataSources() {
        DataSourceRegistry registry = newRegistry();
        LinkedHashMap<String, Map<String, Object>> configured = new LinkedHashMap<>();
        configured.put("db1", sqliteProperties());
        configured.put("db2", sqliteProperties());
        registry.bootstrap(configured, "db1");
        return registry;
    }

    private static DataSourceReloadService reloadService(
            DataSourceRegistry registry,
            Map<String, Map<String, Object>> desired
    ) {
        DataSourceConfigLoader configLoader = mock(DataSourceConfigLoader.class);
        when(configLoader.getConfigPath()).thenReturn("test-datasource.yml");
        when(configLoader.readDataSourcesFromDisk()).thenReturn(desired);
        return new DataSourceReloadService(registry, configLoader);
    }

    private static Map<String, DataSource> captureDataSourceIdentities(DataSourceRegistry registry) {
        Map<String, DataSource> identities = new LinkedHashMap<>();
        for (String name : registry.names()) {
            identities.put(name, registry.getDataSource(name));
        }
        return identities;
    }

    private static void assertRegistryStateUnchanged(
            DataSourceRegistry registry,
            List<String> expectedNames,
            String expectedDefault,
            Map<String, Map<String, Object>> expectedProperties,
            Map<String, DataSource> expectedIdentities
    ) {
        assertEquals(expectedNames, registry.names());
        assertEquals(expectedDefault, registry.getDefaultName());
        assertEquals(expectedProperties, registry.snapshotProperties());
        expectedIdentities.forEach(
                (name, dataSource) -> assertSame(dataSource, registry.getDataSource(name))
        );
    }

    private static HikariDataSource hikariDataSource(DataSourceRegistry registry, String name) {
        return assertInstanceOf(HikariDataSource.class, registry.getDataSource(name));
    }

    private static Map<String, Object> sqliteProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", "jdbc:sqlite::memory:");
        return properties;
    }

    private static Map<String, Object> sqlitePropertiesWithMaximumPoolSize(int maximumPoolSize) {
        Map<String, Object> hikari = new LinkedHashMap<>();
        hikari.put("maximum-pool-size", maximumPoolSize);

        Map<String, Object> properties = sqliteProperties();
        properties.put("hikari", hikari);
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
