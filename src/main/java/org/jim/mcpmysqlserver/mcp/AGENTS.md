# Parent

- `../../../../../../../AGENTS.md`

# Members

- `ClientStdio.java`: 本地 stdio MCP 客户端示例。
- `DataSourceAdminTools.java`: 数据源拓扑管理工具集，当前只有 `reloadDataSources()`。刻意不提供 addDataSource/removeDataSource 这类凭据入参工具——数据源的增删由用户或 Agent 编辑 `datasource.yml` 完成，本工具只让改动免重启生效，从而避免明文密码进入对话记录，也避免配置出现内存与文件两份真相。
- `MysqlOptionService.java`: MCP tool 实现，负责 SQL 执行、结果截断与 Groovy 扩展调用。与 DataSourceAdminTools 职责分离：一个查数据，一个改数据源拓扑。

# 设计要点

数据源名称是 tool 的**入参**而非 tool 名，因此运行期增删数据源不改变 tool schema，MCP 客户端无需重连。新增 tool 时若把数据源名编进 tool 名，将直接破坏这一性质。

[PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
