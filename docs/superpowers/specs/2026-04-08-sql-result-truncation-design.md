# SQL 查询结果截断与续取设计

## 1. 背景

当前 SQL 相关 MCP tool 会把查询结果完整返回给大模型。结果集较大时，单次 tool 响应可能显著占用上下文，影响后续推理质量，严重时会直接撑爆 context。

现有实现中，SQL 执行与结果返回没有“出参预算”边界：

- `JdbcExecutor` 负责执行 SQL 并返回完整数据
- `MysqlOptionService` 直接把完整数据挂到 tool 返回值

这会导致查询行为正确，但返回行为不可控。

## 2. 目标

- 为 SQL tool 增加统一的返回字符阈值控制
- 超过阈值时返回事实型元信息与部分预览数据
- 为被截断的大结果生成 `resultId`，支持后续按窗口续取
- 保持 SQL 执行语义不变，不因截断修改数据库查询本身
- 保持 tool 返回对大模型中立，不输出带行为引导的建议语句

## 3. 非目标

- 不改造 SQL 语义，不自动添加 `LIMIT`、`OFFSET`、`WHERE`
- 不做跨进程持久化缓存
- 不支持服务重启后的结果续取
- 不为小结果引入缓存

## 4. 方案概览

### 4.1 职责拆分

- `JdbcExecutor`
  继续负责执行 SQL 并返回完整结果，不感知 tool 截断逻辑
- `MysqlOptionService`
  负责 tool 语义、超长结果判定、截断包装、新增续取 tool
- `ToolResponseLimitConfig`
  负责读取返回阈值与缓存配置
- `SqlResultCacheService`
  负责缓存被截断的大结果，并提供按 `offset` 的窗口读取

### 4.2 总体流程

1. SQL tool 正常执行查询，拿到完整结果
2. 将结果序列化为 JSON 字符串，计算字符长度
3. 若长度未超过阈值，按现有语义直接返回
4. 若长度超过阈值：
   - 生成 `resultId`
   - 把完整结果快照写入内存缓存
   - 从前往后切一段不超过阈值的预览窗口
   - 返回带元信息的截断响应
5. 模型若需要更多内容，可调用 `fetchSqlResultPage`

## 5. 配置设计

新增配置前缀：`tool.response-limit`

建议配置项：

```yaml
tool:
  response-limit:
    enabled: true
    max-chars: 12000
    cache-max-entries: 100
    cache-ttl-minutes: 10
    min-preview-rows: 1
```

配置语义：

- `enabled`
  是否启用截断与续取机制
- `max-chars`
  单次 tool 返回允许的最大字符数
- `cache-max-entries`
  缓存中允许保留的最大截断结果数量
- `cache-ttl-minutes`
  单个 `resultId` 的有效时长
- `min-preview-rows`
  预览窗口至少返回的行数。若单行序列化后已超过阈值，仍允许返回该单行，避免空预览

## 6. 返回协议

### 6.1 原 SQL tool 未截断

行为保持现状：

- `executeSql`
- `executeSqlOnDefault`
- `executeSqlWithDataSource`

查询结果未超阈值时，继续返回完整结果。

### 6.2 原 SQL tool 截断后返回

当结果超过阈值时，返回包装对象而不是完整结果。建议结构如下：

```json
{
  "datasource": "primary",
  "truncated": true,
  "resultId": "sqlr_xxx",
  "maxChars": 12000,
  "originalChars": 58734,
  "totalRows": 963,
  "returnedRows": 48,
  "nextOffset": 48,
  "hasMore": true,
  "preview": [
    { "id": 1, "name": "a" }
  ]
}
```

字段说明：

- `datasource`
  当前结果所属数据源
- `truncated`
  是否发生截断
- `resultId`
  本次大结果快照标识，用于续取
- `maxChars`
  本次返回使用的字符阈值
- `originalChars`
  完整结果序列化后的字符数
- `totalRows`
  完整结果行数。非结果集场景可为空
- `returnedRows`
  当前返回的预览行数
- `nextOffset`
  下一次续取应使用的起始偏移量
- `hasMore`
  是否还有剩余数据
- `preview`
  当前返回的预览数据

返回字段只表达事实，不包含“建议缩小查询范围”等带引导倾向的文案。

## 7. 新增 Tool 设计

新增 tool：`fetchSqlResultPage`

用途：读取已经截断并缓存的大结果后续窗口。

### 7.1 入参

```json
{
  "resultId": "sqlr_xxx",
  "offset": 48,
  "maxChars": 12000
}
```

字段说明：

- `resultId`
  原 SQL tool 返回的结果标识
- `offset`
  本次续取的起始行偏移量
- `maxChars`
  可选。单次续取允许的最大字符数。仅允许小于等于服务端默认值

### 7.2 出参

```json
{
  "resultId": "sqlr_xxx",
  "offset": 48,
  "returnedRows": 50,
  "nextOffset": 98,
  "hasMore": true,
  "page": [
    { "id": 49, "name": "b" }
  ]
}
```

字段说明：

- `offset`
  本次读取的起点
- `returnedRows`
  实际返回的行数
- `nextOffset`
  下一次续取建议使用的偏移量
- `hasMore`
  是否还有剩余数据
- `page`
  当前窗口的数据

设计上使用 `offset`，不使用 `pageNo`。这样可以消除最后一页、页码换算、空页回退等额外分支。

## 8. 缓存设计

### 8.1 缓存内容

仅缓存发生截断的大结果，缓存对象建议包含：

- `resultId`
- `datasourceName`
- `rows`
- `originalChars`
- `createdAt`
- `expiresAt`

`rows` 使用不可变快照，保证翻页时读到的是同一份结果。

### 8.2 生命周期

- 默认 TTL 为 10 分钟
- 默认最多保留 100 条大结果
- 超过最大条目数时按最旧结果淘汰
- 过期结果在读取时懒清理即可，不需要复杂后台任务

### 8.3 一致性

`fetchSqlResultPage` 只读取缓存，不重跑 SQL。这样可以保证：

- 第 1 次返回和后续分页来自同一快照
- 数据库内容变化不会导致跨页内容错位

## 9. 边界与异常语义

### 9.1 `resultId` 不存在或已过期

返回错误结构：

```json
{
  "success": false,
  "resultId": "sqlr_xxx",
  "error": "Result not found or expired"
}
```

### 9.2 `offset` 越界

不报错，返回空页：

```json
{
  "resultId": "sqlr_xxx",
  "offset": 999999,
  "returnedRows": 0,
  "nextOffset": 999999,
  "hasMore": false,
  "page": []
}
```

这样可以避免为“空窗口”引入额外异常分支。

### 9.3 `maxChars` 非法

- 未传时使用服务端默认值
- 小于等于 0 时返回参数错误
- 大于服务端默认值时自动压到服务端默认值

### 9.4 非结果集语句

`UPDATE`、`DELETE` 等返回影响行数时，不进入截断与缓存逻辑，保持现有返回。

## 10. 对现有 Tool 的影响

### 10.1 `executeSql`

对每个数据源的查询结果独立判定是否截断。某些数据源可能直返，某些数据源可能返回带 `resultId` 的包装对象。

### 10.2 `executeSqlWithDataSource`

单数据源查询时，若结果超阈值，直接返回截断包装对象。

### 10.3 `executeSqlOnDefault`

内部仍调用默认数据源查询逻辑。若发生截断，返回对应包装对象的 `JsonNode` 版本。

## 11. 实现建议

### 11.1 优先保持最小改动

- 不改 `JdbcExecutor` 的执行入口和成功失败语义
- 在 `MysqlOptionService` 内新增一个统一包装方法
- 用一个小型缓存服务承接 `resultId` 生命周期

### 11.2 建议新增类型

- `ToolResponseLimitConfig`
- `SqlResultCacheService`
- `TruncatedResultSnapshot`

必要时可补一个小的返回构造器方法，用于统一组装：

- 截断预览响应
- 分页续取响应
- 缓存失效错误响应

## 12. 测试策略

至少覆盖以下场景：

1. 小结果直返
   不生成 `resultId`，不进入缓存

2. 大结果截断
   返回 `truncated=true`、`resultId`、`preview`、`nextOffset`

3. 连续续取
   使用同一个 `resultId` 连续读取，直到 `hasMore=false`

4. `resultId` 过期或不存在
   返回明确错误

5. `offset` 越界
   返回空页，不抛异常

6. 非结果集 SQL
   保持原有返回，不进入缓存

## 13. 风险与取舍

### 13.1 内存占用

缓存大结果会增加内存压力，因此只缓存被截断结果，并通过 TTL 和最大条目数兜底。

### 13.2 序列化开销

需要对结果做 JSON 序列化以估算字符数。这会增加一次 CPU 开销，但实现简单、行为确定，适合作为第一版。

### 13.3 进程内状态

`resultId` 是进程内能力，服务重启后失效。这是可接受取舍，因为目标是解决单轮或短时多轮上下文问题，不是构建长期导出系统。

## 14. 结论

推荐方案是：

- 对现有 SQL tool 增加统一字符阈值控制
- 超限时返回中立的事实型截断结构
- 同时提供 `fetchSqlResultPage` 进行结果续取

该方案改动集中、兼容性可控，既能阻止超大报文直接进入模型上下文，也保留了模型继续读取完整结果的能力。
