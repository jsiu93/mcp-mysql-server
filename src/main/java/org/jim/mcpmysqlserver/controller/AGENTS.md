# Parent

- `../../../../../../../AGENTS.md`

# Members

- `DataSourceController.java`: `/api/datasource` 下的 HTTP 旁路操作面，是 MCP 协议之外的第二入口，供 curl 与脚本使用。提供 list/test/execute/reload/executeGroovyScript/executeSqlOnDefault。其中 `POST /reload` 与 mcp/DataSourceAdminTools 的 reloadDataSources tool 共用同一个 DataSourceReloadService，两条入口不得各自实现重载逻辑。

# 边界说明

本包全部接口无鉴权，服务默认绑定 `0.0.0.0`。`/execute` 在 `sql.security.enabled=false` 时可执行任意 SQL，同网段任何主机可直接调用。部署时应以 `server.address: 127.0.0.1` 收敛到回环地址，或在前置代理上补鉴权。新增接口前先确认是否会扩大这一暴露面。

[PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
