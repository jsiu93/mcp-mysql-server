# Parent

- `../../../../../../../AGENTS.md`

# Members

- `CorsConfig.java`: **整个文件已被注释掉，当前不产生任何 Bean，不生效**。保留作为跨域配置的历史参考；若要启用需整体解注释并重新评估 `allowedOriginPatterns("*")` 加 `allowCredentials(true)` 的组合风险。
- `DataSourceConfig.java`: 启动期数据源配置的绑定载体，仅在 DynamicDataSourceConfig 建立注册表时被读一次。运行期真相在 service/DataSourceRegistry，本类内容在重载后不再更新，任何运行期判断都不得读它。另持有 `reload-grace-seconds`。
- `DataSourceConfigLoader.java`: 配置文件入口。启动期把外部 yml 注入 Environment 供绑定；运行期以 `readDataSourcesFromDisk()` 提供不触碰 Environment 的原样读盘（重载绝不能再 addFirst，否则属性源随重载次数无限堆积）。读盘用 SnakeYAML SafeConstructor，禁止 YAML 标签实例化任意类型。
- `DataSourceReloadService.java`: 运行期配置生效者，DataSourceConfigLoader 的对偶。职责只有差异计算与施加顺序：先增改（新池校验通过才替换旧池）、再切默认库、最后移除——切默认必须排在移除之前，否则注册表会拒绝移除当前默认库。解析失败整体中止不动任何库，单库连不上则部分成功并进 failed 明细。
- `DynamicDataSourceConfig.java`: 数据源装配入口，重构后只负责接线。`primaryDataSource` 返回 RegistryBackedDataSource 代理而非具体连接池——Bean 身份恒定、目标每次调用重新解析，这是默认数据源可热切换的支点。原 `secondaryDataSources` Map Bean 已删除。
- `ExtensionConfigLoader.java`: 加载外部或默认 `extension.yml`。
- `ParentProcessWatchdog.java`: 监控父进程并在退出时关闭服务。
- `SqlSecurityConfig.java`: 绑定 SQL 安全校验配置。
- `ToolResponseLimitConfig.java`: 绑定 SQL tool 回包截断与缓存配置。
- `YamlPropertySourceFactory.java`: 支持 YAML 资源作为 Spring 属性源。
- `extension/`: Groovy 扩展配置子目录。

[PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
