# 数据源运行期重载设计

- 日期：2026-07-26
- 状态：已实现
- 影响面：`config/`、`service/`、`controller/`、`mcp/`

## 1. 问题

改完 `datasource.yml` 必须重启服务才能生效。重启会断开所有 MCP 客户端会话，也会丢掉全部已建立的连接池。

## 2. 判断：要的不是 CRUD，是 reload

用户的表述是「动态新增或删除数据库连接」，但实际动作是**编辑 yml 然后重启**。痛点在「重启」，不在「编辑」。

若提供 `POST /api/datasource/add` 这类 CRUD 接口，会立刻引入三个问题：

1. **持久化悖论**：内存中新增的连接重启即失效；要留住就得回写 yml；SnakeYAML dump 会铲平配置文件里的全部注释。
2. **双真相**：yml 一份、内存一份，两者迟早不同步。
3. **凭据入链路**：做成 MCP tool 后 `addDataSource(url, user, password)` 的明文密码会落进对话记录与 MCP 日志。

reload 一次性消除这三条：**yml 永远是唯一真相**。增删的编辑界面早已存在（文本编辑器、Agent 的文件工具），缺的只是让改动生效的动词。

因此本设计**明确不提供** add/remove 接口。这不是范围裁剪，是有意的设计边界。

## 3. 关键前提（实现前已验证）

| 事实 | 意义 |
|---|---|
| `DataSourceService.getDataSource()` 每次重新查表，不缓存引用 | 换池即刻生效 |
| MCP tool schema 中数据源名是**入参**而非 tool 名 | 增删数据源不改变 tool 列表，客户端无需重连 |
| `JdbcExecutor.executeSql(DataSource, sql)` 按参数接收 | 全仓库无任何一处长期持有 DataSource 引用 |
| `secondaryDataSources` / `primaryDataSource` 的唯一消费者是 `DataSourceService` | 可干净替换，不牵连别处 |

最脆弱的假设是后两条。一旦有人 `@Autowired DataSource` 并长期持有，重载换池后该引用将握住已关闭的池。护栏是：`@Primary` Bean 本身就是代理，**即使被持有也每次重新解析目标**。

## 4. 架构

```
        datasource.yml  ← 唯一真相
              │ 启动读一次 + reload 时再读
              ▼
    DataSourceConfigLoader.readDataSourcesFromDisk()
              │
              ▼
    DataSourceReloadService     ← 职责：读盘、算 diff、定施加顺序
              │ register / unregister / setDefaultName
              ▼
    ┌────────────────────────────────────────┐
    │  DataSourceRegistry                    │  ← 职责：持池、生命周期
    │  ConcurrentHashMap<name, Entry>        │
    │  volatile String defaultName           │
    │  ScheduledExecutor gracefulCloser      │
    └────────────────────────────────────────┘
        ▲                          ▲
        │ getDataSource(name)      │ getTargetDataSource() 每次解析
        │                          │
  DataSourceService      RegistryBackedDataSource ← @Primary Bean
        ▲
   ┌────┴───────────────────────┐
MysqlOptionService      DataSourceController / DataSourceAdminTools
```

包依赖单向：`config → service`。`DataSourceRegistry` 刻意不引用 config 包任何类型，配置以裸 Map 注入；`DataSourceService` 也去掉了对 `DataSourceConfig` 的依赖。因此重构后 **service 包对 config 包零依赖**，比重构前更干净。

## 5. 核心决策

### 5.1 默认数据源用代理，不用具体池

`primaryDataSource` 若直接返回某个 `HikariDataSource`，它就是不可替换的单例——重载时既换不掉也关不得。改为返回 `DelegatingDataSource` 子类，Bean 身份恒定、目标每次调用解析，默认库遂可热切换。

`afterPropertiesSet()` 被覆写为空：目标按调用解析，不在初始化期强制校验，避免启动时数据库不可达导致容器启动失败。

### 5.2 启动期不校验连接，重载期校验

启动期某个库不可达不应阻止整个服务启动，这与重构前 `DataSourceBuilder` 的惰性行为一致。重载期用户刚改完配置，需要立即拿到反馈，因此实际建连一次。

`register(name, properties, validate)` 的布尔参数即此语义分叉。

### 5.3 关闭连接池必须延迟且异步

HikariCP 的 `close()` 实测行为：`softEvictConnections()` → 等 `addConnectionExecutor`（loginTimeout 秒）→ **assassin executor 强杀在途连接** → 等 `closeConnectionExecutor` 10 秒。即会阻塞十余秒并中断正在执行的查询。

因此摘除数据源分两步：先从注册表移除（新查询立刻拿不到），旧池交给单线程 ScheduledExecutor 在 `datasource.reload-grace-seconds`（默认 30）后关闭。

### 5.4 配置快照必须在驱动补全之前留存

`createDataSource` 会为缺失 `driver-class-name` 的配置自动补全驱动名。若快照留在补全之后，下一次重载拿 yml 原文与含驱动名的快照比对，会把每个数据源都误判为「已变更」，导致每次重载都无谓重建全部连接池。

因此 `register()` 先 `new LinkedHashMap<>(properties)` 存快照，`createDataSource()` 内部再复制一份做补全，绝不污染入参。

### 5.5 施加顺序：先增改 → 再切默认 → 最后移除

注册表拒绝移除当前默认数据源。若 yml 把默认库从 db1 换成 db2 并删掉 db1，先移除就会失败。必须先把默认指针切到 db2，db1 才可被移除。

### 5.6 两类失败的不对称处理

- **配置文件解析失败或结果为空** → 整体中止，一个数据源都不动。这是用户手误，应当全量拒绝。
- **单个数据库连不上** → 部分成功，其余照常生效，失败项进 `failed` 明细。某个库临时宕机是常态，不该阻塞其它库的配置更新。

### 5.7 重载不得触碰 Spring Environment

启动期走 `propertySources.addFirst()`。若重载复用此路径，属性源会随重载次数无限堆积。因此重载直接用 SnakeYAML 读原始嵌套结构——顺带使拿到的 Map 形状（kebab-case 键、嵌套 hikari 段）与注册表快照完全一致，可直接 `equals` 比对。

读盘使用 `SafeConstructor`，禁止 YAML 标签实例化任意类型。

## 6. 返回契约

```json
{
  "configPath": "datasource.yml",
  "success": true,
  "added": ["db3"],
  "updated": ["db1"],
  "removed": ["db2"],
  "unchanged": ["db4"],
  "failed": {},
  "defaultDataSource": "db1",
  "datasources": ["db1", "db3", "db4"]
}
```

解析失败时无 added/updated/removed 字段，代之以 `success: false` 与 `error`。

## 7. 已知边界

- **驱动**：fat jar 只打了 MySQL、PostgreSQL、SQLite、Apache IoTDB 四种驱动，而 `DatabaseTypeDetector` 另外声称支持 Oracle / SQL Server / H2。加这三类数据源必然失败，错误信息明说「驱动未打包进当前 jar，需在 pom.xml 加入依赖后重新构建」，不甩裸栈。
- **命令行参数**：`--sql.security.enabled`、`--server.port` 等不在 `datasource.yml` 内，不归重载管。
- **不做文件监听**：配置何时生效保持显式。自动重载会让「当前跑的是哪一版配置」变成隐式状态，出错难以定位。
- **无鉴权**：`/api/datasource/reload` 与本包其它接口一样无鉴权，服务默认绑定 `0.0.0.0`。但重载端点不接收任何连接参数、只重读本地文件，因此相对既有接口未扩大攻击面。收敛暴露面应通过 `server.address: 127.0.0.1`，属独立议题。
