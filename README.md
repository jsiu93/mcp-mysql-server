# MCP MySQL Server

基于 Spring Boot 与 Spring AI MCP Server 的数据库工具服务，支持多数据源与多数据库类型。通过 MCP Tools + Resources 提供 SQL 执行与数据处理能力，默认启用 HTTP/Streamable 传输，支持可选 stdio 模式。

## 功能概览

- 多数据源配置与动态切换（默认数据源 + 命名数据源）。
- JDBC URL 自动识别数据库类型并设置驱动。
- MCP Tools：`executeSql`、`executeSqlOnDefault`、`executeSqlWithDataSource`、`fetchSqlResultPage`、`listDataSources`、`reloadDataSources`、`getAllExtensions`、`executeGroovyScript`。
- MCP Resources：`mcp://datasources/config`、`mcp://database/tables`、`mcp://extensions/list`、`mcp://sql/templates`。
- SQL 安全校验（可配置关键字拦截）。

## 运行环境

- JDK 21
- Maven（推荐使用 `./mvnw`）

## 数据源配置

默认加载 `src/main/resources/datasource.yml`，可用命令行覆盖：

```bash
java -jar target/mcp-mysql-server-*.jar --datasource.config=/path/to/datasource.yml
```

最小示例：

```yaml
datasource:
  datasources:
    primary:
      url: jdbc:mysql://localhost:3306/app
      username: root
      password: "password"
      default: true
    sqlite_demo:
      url: jdbc:sqlite:/data/demo.db
      username: ""
      password: ""
      driver-class-name: org.sqlite.JDBC
```

## 启动方式

开发运行：

```bash
./mvnw spring-boot:run
```

打包运行：

```bash
./mvnw clean package
java -jar target/mcp-mysql-server-*.jar
```

可选 stdio 模式（加载 `application-stdio.yml`）：

```bash
java -jar target/mcp-mysql-server-*.jar --spring.profiles.active=stdio
```

默认 HTTP MCP 入口：`http://localhost:6789/mcp`。stdio 配置默认端口为 `6788`（见 `application-stdio.yml`）。

## Groovy 扩展

扩展由 `src/main/resources/extension.yml` 注册，脚本与依赖位于：

- 脚本：`src/main/resources/groovy/<extension>/script/`
- 依赖：`src/main/resources/groovy/<extension>/dependency/`

调用方式：先用 `getAllExtensions` 获取扩展，再用 `executeGroovyScript` 处理结果字段（如 Base64、加密字段、压缩数据）。

## SQL 安全控制

`sql.security.enabled` 与 `sql.security.dangerous-keywords` 位于 `application.yml`，示例参考：

- `src/main/resources/sql-security-config-example.yml`

## 大结果截断与续取

SQL 结果超过 `tool.response-limit.max-chars` 时，tool 不再直接返回完整结果，而是返回带 `resultId` 的预览结构。调用方可以继续使用 `fetchSqlResultPage` 读取后续窗口。

默认配置位于 `application.yml` 与 `application-stdio.yml`：

```yaml
tool:
  response-limit:
    enabled: true
    max-chars: 12000
    cache-max-entries: 100
    cache-ttl-minutes: 10
    min-preview-rows: 1
```

## 数据源免重启重载

改完 `datasource.yml` 不用重启服务，触发一次重载即可：

```bash
curl -X POST localhost:6789/api/datasource/reload
```

也可以在 MCP 会话中调用 `reloadDataSources` 工具。配置文件是唯一真相，因此**没有**新增/删除数据源的接口——新增就是往文件里加一段配置再重载。

配置未变的数据源连接池不受影响；被移除的数据源会保留 `datasource.reload-grace-seconds`（默认 30 秒）让在途查询跑完再关闭；配置语法错误整体中止不改动任何数据源，单个数据库连不上则部分成功并在 `failed` 中报告。详见 `DATASOURCE.md`。

## 调试接口（HTTP）

用于本地调试与连通性检查：

- `GET /api/datasource/list`
- `GET /api/datasource/test?name=<datasource>`
- `POST /api/datasource/execute?datasource=<name>&sql=...`
- `GET /api/datasource/executeSqlOnDefault?sql=...`
- `GET /api/datasource/executeGroovyScript?extensionName=...&input=...`

## 运行说明

- 日志输出到 `logs/mcp-server.log`。
- 作为子进程运行时，父进程退出会触发服务自动关闭。
