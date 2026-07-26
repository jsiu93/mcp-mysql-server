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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [INPUT]: 依赖 Spring 的 Environment/ApplicationArguments/ResourceLoader 定位配置文件，
 *          依赖同包 YamlPropertySourceFactory 做启动期属性源转换，依赖 SnakeYAML 做运行期原样读盘。
 * [OUTPUT]: 对外提供 DataSourceConfigLoader（loadDataSourceConfig/getConfigPath/readDataSourcesFromDisk）。
 * [POS]: config 包的配置文件入口。启动期把外部 yml 注入 Environment 供 DataSourceConfig 绑定；
 *        运行期为 DataSourceReloadService 提供不触碰 Environment 的原样读盘能力——
 *        重载绝不能再往 Environment addFirst，否则属性源会随重载次数无限堆积。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 *
 * <p>数据源配置加载器，支持从命令行参数指定的配置文件加载配置。</p>
 *
 * @author yangxin
 */
@Configuration
@Slf4j
public class DataSourceConfigLoader implements InitializingBean {

    private static final String DATASOURCE_CONFIG_OPTION = "datasource.config";
    private static final String DEFAULT_CONFIG_PATH = "classpath:datasource.yml";
    private static final String ROOT_NODE = "datasource";
    private static final String DATASOURCES_NODE = "datasources";

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
            Resource resource = resolveResource(configPath);

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
     * 运行期重新读取配置文件中的数据源定义，不触碰 Spring Environment。
     *
     * <p>刻意直接用 SnakeYAML 读原始嵌套结构，而不复用启动期的 PropertySource 路径：
     * 一来避免属性源随重载次数堆积，二来拿到的 Map 形状（含 kebab-case 键与嵌套 hikari 段）
     * 与 DataSourceConfig 绑定出来的完全一致，可以直接与注册表快照做 equals 比对。</p>
     *
     * @return 数据源名称到属性的映射，保持 yml 中的书写顺序
     * @throws IllegalStateException 文件缺失、不是合法 YAML、或缺少 datasource.datasources 节点时抛出
     */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> readDataSourcesFromDisk() {
        String configPath = getConfigPath();
        Resource resource = resolveResource(configPath);

        Object root;
        try (InputStream input = resource.getInputStream()) {
            // SafeConstructor 禁止 YAML 标签实例化任意类型，杜绝配置文件被篡改后的反序列化风险
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
        } catch (IOException e) {
            throw new IllegalStateException("读取配置文件 [" + configPath + "] 失败：" + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("配置文件 [" + configPath + "] 不是合法的 YAML：" + e.getMessage(), e);
        }

        if (!(root instanceof Map)) {
            throw new IllegalStateException("配置文件 [" + configPath + "] 内容不是合法的 YAML 映射结构");
        }
        Object datasourceNode = ((Map<String, Object>) root).get(ROOT_NODE);
        if (!(datasourceNode instanceof Map)) {
            throw new IllegalStateException("配置文件 [" + configPath + "] 缺少 " + ROOT_NODE + " 根节点");
        }
        Object datasourcesNode = ((Map<String, Object>) datasourceNode).get(DATASOURCES_NODE);
        if (!(datasourcesNode instanceof Map)) {
            throw new IllegalStateException("配置文件 [" + configPath + "] 缺少 " + ROOT_NODE + "." + DATASOURCES_NODE + " 节点");
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        ((Map<Object, Object>) datasourcesNode).forEach((name, properties) -> {
            if (properties instanceof Map) {
                result.put(String.valueOf(name), new LinkedHashMap<>((Map<String, Object>) properties));
            } else {
                log.warn("配置文件中数据源 [{}] 的内容不是映射结构，已跳过", name);
            }
        });
        return result;
    }

    /**
     * 获取配置文件路径
     * 优先从命令行参数获取，如果没有指定则使用默认路径
     * @return 配置文件路径
     */
    public String getConfigPath() {
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

    /**
     * 把配置路径解析为可读资源。外部文件不存在时回落到 jar 内默认配置，与启动期行为保持一致。
     *
     * @param configPath 配置文件路径
     * @return 可读资源
     */
    private Resource resolveResource(String configPath) {
        if (configPath.startsWith("classpath:")) {
            log.info("Using default datasource configuration from: {}", configPath);
            return resourceLoader.getResource(configPath);
        }

        File configFile = new File(configPath);
        if (!configFile.exists() || !configFile.isFile()) {
            log.warn("Specified datasource config file not found: {}, using default configuration", configPath);
            log.info("Falling back to default datasource configuration: {}", DEFAULT_CONFIG_PATH);
            return resourceLoader.getResource(DEFAULT_CONFIG_PATH);
        }

        log.info("Loading external datasource configuration from: {}", configFile.getAbsolutePath());
        return new FileSystemResource(configFile);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        loadDataSourceConfig();
    }
}
