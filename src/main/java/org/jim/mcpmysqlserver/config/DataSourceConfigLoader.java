package org.jim.mcpmysqlserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.List;

/**
 * 数据源配置加载器，支持从命令行参数指定的配置文件加载配置
 * @author yangxin
 */
@Configuration
@Slf4j
public class DataSourceConfigLoader implements InitializingBean {

    private static final String DATASOURCE_CONFIG_OPTION = "datasource.config";
    private static final String DEFAULT_CONFIG_PATH = "classpath:datasource.yml";

    private final Environment environment;
    private final ApplicationArguments applicationArguments;
    private final ResourceLoader resourceLoader;

    @Autowired
    public DataSourceConfigLoader(Environment environment, ApplicationArguments applicationArguments, ResourceLoader resourceLoader) {
        this.environment = environment;
        this.applicationArguments = applicationArguments;
        this.resourceLoader = resourceLoader;
        log.info("DataSourceConfigLoader initialized");
    }

    /**
     * 加载数据源配置
     * 优先从命令行参数指定的配置文件加载，如果没有指定则使用默认配置文件
     * 在Bean初始化后自动调用
     */
    public void loadDataSourceConfig() {
        String configPath = getConfigPath();
        log.info("Loading datasource configuration from: {}", configPath);

        try {
            Resource resource;
            if (configPath.startsWith("classpath:")) {
                // 使用默认配置
                log.info("Using default datasource configuration from: {}", configPath);
                // 加载默认配置文件
                resource = resourceLoader.getResource(configPath);
            } else {
                // 使用外部配置文件
                File configFile = new File(configPath);
                if (!configFile.exists() || !configFile.isFile()) {
                    log.warn("Specified datasource config file not found: {}, using default configuration", configPath);
                    // 使用默认配置文件
                    resource = resourceLoader.getResource(DEFAULT_CONFIG_PATH);
                    log.info("Falling back to default datasource configuration: {}", DEFAULT_CONFIG_PATH);
                } else {
                    resource = new FileSystemResource(configFile);
                    log.info("Loading external datasource configuration from: {}", configFile.getAbsolutePath());
                }
            }

            // 加载配置文件
            YamlPropertySourceFactory factory = new YamlPropertySourceFactory();
            PropertySource<?> propertySource = factory.createPropertySource("externalDatasourceConfig", new EncodedResource(resource));

            // 将配置添加到环境中，优先级高于默认配置
            if (environment instanceof ConfigurableEnvironment) {
                MutablePropertySources propertySources = ((ConfigurableEnvironment) environment).getPropertySources();
                propertySources.addFirst(propertySource);
                log.info("External datasource configuration loaded successfully");
            } else {
                log.warn("Unable to add external datasource configuration to environment");
            }
        } catch (Exception e) {
            log.error("Failed to load external datasource configuration: {}", e.getMessage(), e);
            log.info("Using default datasource configuration");
        }
    }

    /**
     * 获取配置文件路径
     * 优先从命令行参数获取，如果没有指定则使用默认路径
     * @return 配置文件路径
     */
    private String getConfigPath() {
        // 从命令行参数获取配置文件路径
        List<String> configValues = applicationArguments.getOptionValues(DATASOURCE_CONFIG_OPTION);
        if (!CollectionUtils.isEmpty(configValues) && configValues.size() == 1) {
            String configPath = configValues.get(0);
            if (configPath != null && !configPath.trim().isEmpty()) {
                return configPath.trim();
            }
        }

        // 如果没有指定，使用默认路径
        return DEFAULT_CONFIG_PATH;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        loadDataSourceConfig();
    }
}
