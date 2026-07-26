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
 * [INPUT]: 依赖 Spring Boot 的 @ConfigurationProperties 绑定，绑定源由同包 DataSourceConfigLoader 在启动期注入 Environment。
 * [OUTPUT]: 对外提供 DataSourceConfig（getDatasources/getDefaultDataSourceName/getDefaultDataSourceProperties/
 *          getReloadGraceSeconds）。
 * [POS]: config 包的启动期引导输入，仅在 DynamicDataSourceConfig 建立注册表时被读一次。
 *        运行期数据源真相在 service/DataSourceRegistry，本类内容在重载后不再更新，任何运行期判断都不得读它。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 *
 * <p>数据源配置类，从 datasource.yml 或用户指定的配置文件读取配置。
 * 用户可以通过命令行参数 --datasource.config=&lt;配置文件路径&gt; 指定配置文件。</p>
 *
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
     * 重载时摘除数据源后，延迟关闭其连接池的宽限秒数。
     * HikariCP 的 close() 会强杀在途连接，留出宽限期让正在执行的查询跑完。
     * 对应配置项 datasource.reload-grace-seconds。
     */
    private long reloadGraceSeconds = 30L;

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
