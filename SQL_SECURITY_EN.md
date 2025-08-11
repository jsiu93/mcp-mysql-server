# SQL Security Control

SQL Security Control prevents AI models from executing dangerous database operations such as UPDATE, DELETE, DROP, etc.,
ensuring data safety through keyword detection before SQL execution.

## Configuration

Add the following configuration to `application.yml`:

```yaml
sql:
  security:
    # Enable/disable security check
    enabled: true
    # Dangerous keywords list (case-insensitive)
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

## Error Response

When AI models attempt to execute prohibited operations, they will receive error messages:

```json
{
  "error": "Detected dangerous SQL operation keyword 'DELETE'. This operation has been prohibited for data security. To execute this operation, please: 1) Set sql.security.enabled=false in application.yml to completely disable SQL security check, or 2) Remove 'delete' keyword from sql.security.dangerous-keywords list. Restart the service after configuration changes.",
  "detected_keyword": "delete",
  "sql_security_enabled": true
}
```
