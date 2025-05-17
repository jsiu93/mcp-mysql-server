package org.jim.mcpmysqlserver.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据源配置类，从 datasource.yml 或用户指定的配置文件读取配置
 * 用户可以通过命令行参数 --datasource.config=<配置文件路径> 指定配置文件
 * @author yangxin
 */
@Configuration
@ConfigurationProperties(prefix = "datasource")
@Data
@Slf4j
@DependsOn("dataSourceConfigLoader")
public class DataSourceConfig {

    /**
     * 所有数据源配置
     */
    private Map<String, Map<String, Object>> datasources = new LinkedHashMap<>();

    /**
     * 构造函数，打印日志信息
     */
    public DataSourceConfig() {
        log.info("DataSourceConfig initialized");
    }

    /**
     * 获取默认数据源名称
     * 如果有数据源标记为 default: true，则返回该数据源名称
     * 否则返回第一个数据源名称
     * @return 默认数据源名称
     */
    public String getDefaultDataSourceName() {
        if (CollectionUtils.isEmpty(datasources)) {
            log.warn("No datasources configured, datasources map is empty");
            return null;
        }

        log.info("Datasources configured: {}", datasources.keySet());
        log.debug("Datasources details: {}", datasources);

        // 先查找标记为 default: true 的数据源
        for (Map.Entry<String, Map<String, Object>> entry : datasources.entrySet()) {
            Map<String, Object> dsProps = entry.getValue();
            if (dsProps != null && Boolean.TRUE.equals(dsProps.get("default"))) {
                log.info("Found default datasource: {}", entry.getKey());
                return entry.getKey();
            }
        }

        // 如果没有标记的默认数据源，返回第一个
        String firstDs = datasources.keySet().iterator().next();
        log.info("No datasource marked as default, using first one: {}", firstDs);
        return firstDs;
    }

    /**
     * 获取默认数据源配置
     * @return 默认数据源配置
     */
    public Map<String, Object> getDefaultDataSourceProperties() {
        String defaultName = getDefaultDataSourceName();
        if (defaultName == null) {
            return new HashMap<>();
        }
        return datasources.getOrDefault(defaultName, new HashMap<>());
    }
}
