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
 * 扩展配置加载器，支持从命令行参数指定的配置文件加载扩展配置
 * 使用方式：java -jar app.jar --extension.config=/path/to/extension.yml
 * @author yangxin
 */
@Configuration
@Slf4j
public class ExtensionConfigLoader implements InitializingBean {

    private static final String EXTENSION_CONFIG_OPTION = "extension.config";
    private static final String DEFAULT_CONFIG_PATH = "classpath:extension.yml";

    private final Environment environment;
    private final ApplicationArguments applicationArguments;
    private final ResourceLoader resourceLoader;

    @Autowired
    public ExtensionConfigLoader(Environment environment, ApplicationArguments applicationArguments, ResourceLoader resourceLoader) {
        this.environment = environment;
        this.applicationArguments = applicationArguments;
        this.resourceLoader = resourceLoader;
        log.info("ExtensionConfigLoader 初始化完成");
    }

    /**
     * 加载扩展配置文件
     */
    private void loadExtensionConfig() {
        String configPath = getConfigPath();
        log.info("准备加载扩展配置文件: {}", configPath);

        try {
            Resource resource;
            if (configPath.startsWith("classpath:")) {
                // 使用默认配置
                log.info("使用默认扩展配置文件: {}", configPath);
                resource = resourceLoader.getResource(configPath);
            } else {
                // 使用外部配置文件
                File configFile = new File(configPath);
                if (!configFile.exists() || !configFile.isFile()) {
                    log.warn("指定的扩展配置文件不存在: {}，使用默认配置", configPath);
                    resource = resourceLoader.getResource(DEFAULT_CONFIG_PATH);
                    log.info("回退到默认扩展配置: {}", DEFAULT_CONFIG_PATH);
                } else {
                    resource = new FileSystemResource(configFile);
                    log.info("加载外部扩展配置文件: {}", configFile.getAbsolutePath());
                }
            }

            // 加载配置文件
            YamlPropertySourceFactory factory = new YamlPropertySourceFactory();
            PropertySource<?> propertySource = factory.createPropertySource("externalExtensionConfig", new EncodedResource(resource));

            // 将配置添加到环境中，优先级高于默认配置
            if (environment instanceof ConfigurableEnvironment) {
                MutablePropertySources propertySources = ((ConfigurableEnvironment) environment).getPropertySources();
                propertySources.addFirst(propertySource);
                log.info("外部扩展配置加载成功");
            } else {
                log.warn("无法将外部扩展配置添加到环境中");
            }
        } catch (Exception e) {
            log.error("加载外部扩展配置失败: {}", e.getMessage(), e);
            log.info("使用默认扩展配置");
        }
    }

    /**
     * 获取配置文件路径
     * 优先从命令行参数获取，如果没有指定则使用默认路径
     * @return 配置文件路径
     */
    private String getConfigPath() {
        // 从命令行参数获取配置文件路径
        List<String> configValues = applicationArguments.getOptionValues(EXTENSION_CONFIG_OPTION);
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
        loadExtensionConfig();
    }
}
