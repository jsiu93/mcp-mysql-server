# SQL安全控制功能

SQL安全控制功能可以防止AI模型执行危险的数据库操作，如UPDATE、DELETE、DROP等，确保数据安全。该功能通过关键字检测机制，在SQL执行前进行安全验证。

## 配置

在 `application.yml` 文件中添加以下配置：

```yaml
sql:
  security:
    # 是否启用安全检查
    enabled: true
    # 危险关键字列表（不区分大小写）
    dangerous-keywords:
      - update
      - delete
      - insert
      - replace
      - drop
      - create
      - alter
      - truncate
      - grant
      - revoke
      - shutdown
      - restart
      - call
      - execute
      - commit
      - rollback
```

## 错误提示

当AI模型尝试执行被禁止的操作时，会收到错误提示：

```json
{
  "error": "Detected dangerous SQL operation keyword 'DELETE'. This operation has been prohibited for data security. To execute this operation, please: 1) Set sql.security.enabled=false in application.yml to completely disable SQL security check, or 2) Remove 'delete' keyword from sql.security.dangerous-keywords list. Restart the service after configuration changes.",
  "detected_keyword": "delete",
  "sql_security_enabled": true
}
```
