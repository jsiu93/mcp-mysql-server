# Parent

- `../../../../../AGENTS.md`

# Members

- `CorsConfig.java`: CORS 配置，控制 HTTP 暴露头与跨域策略。
- `DataSourceConfig.java`: 绑定数据源配置并解析默认数据源。
- `DataSourceConfigLoader.java`: 加载外部或默认 `datasource.yml`。
- `DynamicDataSourceConfig.java`: 创建主从数据源 Bean。
- `ExtensionConfigLoader.java`: 加载外部或默认 `extension.yml`。
- `ParentProcessWatchdog.java`: 监控父进程并在退出时关闭服务。
- `SqlSecurityConfig.java`: 绑定 SQL 安全校验配置。
- `ToolResponseLimitConfig.java`: 绑定 SQL tool 回包截断与缓存配置。
- `YamlPropertySourceFactory.java`: 支持 YAML 资源作为 Spring 属性源。
- `extension/`: Groovy 扩展配置子目录。
