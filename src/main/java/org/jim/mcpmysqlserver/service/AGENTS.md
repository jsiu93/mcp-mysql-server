# Parent

- `../../../../../../../AGENTS.md`

# Members

- `DataSourceRegistry.java`: 运行期数据源真相的唯一持有者。以 ConcurrentHashMap 承载全部连接池（含默认库，不再区分主从两套容器），volatile 字段记录默认库指针使其可热切换；写路径整体 synchronized，读路径无锁。创建连接池、驱动缺失预检、建连校验、宽限期异步关闭四件事全部收敛于此，别处不得自行 new HikariDataSource。刻意不依赖 config 包任何类型，配置以裸 Map 注入，使 service 包对 config 包零依赖。
- `DataSourceService.java`: 数据源定位门面，只负责别名解析（null/""/primary → 当前默认库）这一层语义，池的持有与生命周期一概委托 DataSourceRegistry。
- `JdbcExecutor.java`: JDBC 执行器，负责 SQL 执行与结果集转换。按参数接收 DataSource 而不持有引用——这是重载能安全换池的前提，任何缓存 DataSource 的改动都会让旧引用握住已关闭的池。
- `SqlResultCacheService.java`: 超长 SQL 结果快照缓存与失效清理服务。
- `TruncatedResultSnapshot.java`: 截断结果不可变快照模型。

[PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
