package org.jim.mcpmysqlserver.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyNameAliases;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态数据源配置
 * @author yangxin
 */
@Configuration
@Slf4j
public class DynamicDataSourceConfig {

    @Resource
    private DataSourceConfig dataSourceConfig;

    /**
     * 默认数据源
     */
    @Bean
    @Primary
    public DataSource primaryDataSource() {
        String defaultDsName = dataSourceConfig.getDefaultDataSourceName();
        if (defaultDsName == null) {
            throw new IllegalStateException("No datasource configured");
        }

        log.info("Initializing default datasource [{}] from custom configuration", defaultDsName);
        Map<String, Object> dsProperties = dataSourceConfig.getDefaultDataSourceProperties();
        return createDataSource(defaultDsName, dsProperties);
    }

    /**
     * 创建所有配置的非默认数据源并注入到Spring容器
     * @return 数据源名称到数据源的映射
     */
    @Bean
    public Map<String, DataSource> secondaryDataSources() {
        Map<String, DataSource> dataSources = new HashMap<>();
        String defaultDsName = dataSourceConfig.getDefaultDataSourceName();

        // 遍历配置的数据源
        for (Map.Entry<String, Map<String, Object>> entry : dataSourceConfig.getDatasources().entrySet()) {
            String dsName = entry.getKey();

            // 跳过默认数据源，因为它已经由primaryDataSource()方法创建
            if (dsName.equals(defaultDsName)) {
                continue;
            }

            Map<String, Object> dsProperties = entry.getValue();
            try {
                log.info("Initializing configured datasource: {}", dsName);
                DataSource ds = createDataSource(dsName, dsProperties);
                dataSources.put(dsName, ds);
                log.info("Datasource [{}] initialized successfully", dsName);
            } catch (Exception e) {
                log.error("Failed to initialize datasource [{}]: {}", dsName, e.getMessage(), e);
            }
        }

        return dataSources;
    }

    /**
     * 根据配置创建数据源
     * @param dsName 数据源名称
     * @param dsProperties 数据源属性
     * @return 数据源
     */
    private DataSource createDataSource(String dsName, Map<String, Object> dsProperties) {
        try {
            if (CollectionUtils.isEmpty(dsProperties)) {
                log.warn("No properties provided for datasource {}", dsName);
                return null;
            }

            // 设置默认驱动类名，如果用户没有配置
            if (!dsProperties.containsKey("driver-class-name")) {
                dsProperties.put("driver-class-name", "com.mysql.cj.jdbc.Driver");
                log.info("Using default driver-class-name: com.mysql.cj.jdbc.Driver for datasource {}", dsName);
            }

            // 创建数据源属性
            DataSourceProperties dataSourceProperties = new DataSourceProperties();
            ConfigurationPropertySource source = new MapConfigurationPropertySource(dsProperties);
            ConfigurationPropertyNameAliases aliases = new ConfigurationPropertyNameAliases();
            aliases.addAliases("url", "jdbc-url");
            aliases.addAliases("username", "user");
            Binder binder = new Binder(source.withAliases(aliases));

            // 绑定基本属性
            binder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(dataSourceProperties));

            // 创建HikariDataSource
            HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder()
                    .type(HikariDataSource.class)
                    .build();

            // 设置默认的Hikari配置
            dataSource.setMaximumPoolSize(10);
            dataSource.setMinimumIdle(5);
            dataSource.setPoolName(dsName + "HikariCP");

            // 绑定Hikari特定属性，如果用户配置了则覆盖默认值
            Map<String, Object> hikariProperties = (Map<String, Object>) dsProperties.get("hikari");
            if (!CollectionUtils.isEmpty(hikariProperties)) {
                ConfigurationPropertySource hikariSource = new MapConfigurationPropertySource(hikariProperties);
                Binder hikariBinder = new Binder(hikariSource);
                hikariBinder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(dataSource));
            }

            log.info("Datasource [{}] created successfully", dsName);
            return dataSource;
        } catch (Exception e) {
            log.error("Failed to create datasource [{}]: {}", dsName, e.getMessage(), e);
            throw e;
        }
    }
}
