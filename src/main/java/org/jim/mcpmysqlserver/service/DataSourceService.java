package org.jim.mcpmysqlserver.service;

import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.config.DataSourceConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源管理服务
 * @author yangxin
 */
@Service
@Slf4j
public class DataSourceService {

    private final DataSourceConfig dataSourceConfig;
    private final ApplicationContext applicationContext;

    @Autowired
    public DataSourceService(DataSourceConfig dataSourceConfig, ApplicationContext applicationContext) {
        this.dataSourceConfig = dataSourceConfig;
        this.applicationContext = applicationContext;
        log.info("DataSourceService initialized with ApplicationContext");
    }

    /**
     * 获取数据源
     * @param name 数据源名称
     * @return 数据源
     */
    public DataSource getDataSource(String name) {
        // 如果未指定数据源名称，返回默认数据源
        if (name == null || name.isEmpty()) {
            log.debug("No datasource name specified, returning primary datasource");
            return getPrimaryDataSource();
        }

        // 如果指定了"primary"，为兼容性考虑，返回默认数据源
        if ("primary".equals(name)) {
            log.debug("Datasource name 'primary' specified, returning primary datasource");
            return getPrimaryDataSource();
        }

        // 如果指定了默认数据源名称，返回默认数据源
        String defaultDsName = dataSourceConfig.getDefaultDataSourceName();
        if (name.equals(defaultDsName)) {
            log.debug("Datasource name '{}' is the default datasource, returning primary datasource", name);
            return getPrimaryDataSource();
        }

        // 从 Spring 容器中获取所有数据源
        Map<String, DataSource> secondaryDataSources;
        try {
            secondaryDataSources = applicationContext.getBean("secondaryDataSources", Map.class);
        } catch (Exception e) {
            log.error("Failed to get secondaryDataSources bean: {}", e.getMessage());
            log.warn("Datasource [{}] not found, using default datasource instead", name);
            return getPrimaryDataSource();
        }

        // 从数据源集合中获取指定名称的数据源
        DataSource dataSource = secondaryDataSources.get(name);
        if (dataSource != null) {
            log.debug("Found datasource [{}] in Spring context", name);
            return dataSource;
        }

        // 如果无法找到，返回默认数据源
        log.warn("Datasource [{}] not found, using default datasource instead", name);
        return getPrimaryDataSource();
    }

    /**
     * 获取默认数据源
     * @return 默认数据源
     */
    private DataSource getPrimaryDataSource() {
        try {
            return applicationContext.getBean(DataSource.class);
        } catch (Exception e) {
            log.error("Failed to get primary datasource: {}", e.getMessage(), e);
            throw new IllegalStateException("Primary datasource not available", e);
        }
    }

    /**
     * 获取所有可用的数据源名称
     * @return 数据源名称列表
     */
    public List<String> getDataSourceNames() {
        // 获取配置文件中的数据源名称
        Set<String> configuredNames = dataSourceConfig.getDatasources().keySet();

        // 创建结果列表
        List<String> allNames = new ArrayList<>(configuredNames);

        // 排序后返回
        Collections.sort(allNames);
        log.debug("Available datasource names: {}", allNames);
        return allNames;
    }

    /**
     * 获取默认数据源名称
     * @return 默认数据源名称
     */
    public String getDefaultDataSourceName() {
        return dataSourceConfig.getDefaultDataSourceName();
    }
}
