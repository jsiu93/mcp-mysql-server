# Data Source Configuration Documentation

This document provides detailed instructions on how to configure and manage data sources for MCP MySQL Server.

## Configuration File Location

### Default Configuration File

The application uses the following configuration file by default:
```
src/main/resources/datasource.yml
```

### Custom Configuration File

Users can specify a custom data source configuration file via command line arguments:

#### Maven Wrapper Startup Method

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/your-datasource.yml
```

#### JAR Package Startup Method

```bash
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar --datasource.config=/path/to/your-datasource.yml
```

## Configuration File Format

The data source configuration file uses YAML format with the following basic structure:

```yaml
datasource:
datasources:
    # Data source configuration items
    datasource_name:
      url: jdbc:mysql://host:port/database
      username: your_username
      password: your_password
      default: true  # Optional, mark as default data source
```
> Tip: SQLite URLs use the format `jdbc:sqlite:/path/to/database.db`. You can leave `username` and `password` empty and explicitly set `driver-class-name: org.sqlite.JDBC`.

## Configuration Parameters

### Required Parameters

| Parameter  | Type   | Description                              | Example                                 |
|:-----------|:-------|:----------------------------------------|:---------------------------------------|
| `url`      | String | JDBC connection string                   | `jdbc:mysql://localhost:3306/mydb`    |
| `username` | String | Database username                        | `root`                                 |
| `password` | String | Database password                        | `password123`                          |

> SQLite data sources only require the `url`; credentials can remain empty strings.

### Optional Parameters

| Parameter | Type    | Description                                           | Default | Example |
|:----------|:--------|:-----------------------------------------------------|:--------|:--------|
| `default` | Boolean | Whether to set as default data source. If not specified, the first data source will be set as default | false   | true    |

## Configuration Examples

### Single Data Source Configuration

```yaml
datasource:
  datasources:
    production:
      url: jdbc:mysql://prod-server:3306/production_db
      username: prod_user
      password: prod_password
      default: true
```

### Multiple Data Sources Configuration

```yaml
datasource:
  datasources:
    # Production database
    production:
      url: jdbc:mysql://prod-server:3306/production_db
      username: prod_user
      password: prod_password
      default: true  # Set as default data source

    # Testing database
    testing:
      url: jdbc:mysql://test-server:3306/test_db
      username: test_user
      password: test_password

    # Development database
    development:
      url: jdbc:mysql://localhost:3306/dev_db
      username: dev_user
      password: dev_password

    # Analytics database
    analytics:
      url: jdbc:mysql://analytics-server:3306/analytics_db
      username: analytics_user
      password: analytics_password
```

### SQLite Data Source Configuration

```yaml
datasource:
  datasources:
    sqlite_demo:
      url: jdbc:sqlite:/opt/data/sqlite-demo.db
      username: ''
      password: ''
      driver-class-name: org.sqlite.JDBC
```

SQLite stores data in a single file, so prefer absolute paths and ensure the running user has read/write permissions.

## Data Source Usage

### Default Data Source

- If a data source is marked as `default: true` in the configuration, it will be set as the default
- If no default data source is explicitly marked, the first data source will automatically become the default
- When executing SQL, if no data source is specified, the default data source will be used

### Specifying Data Source

When executing SQL queries, you can specify using a particular data source:
- Use `executeSqlWithDataSource` tool to specify a specific data source
- Use `executeSql` tool to execute on all data sources
- Use `executeSqlOnDefault` tool to execute only on the default data source

## Changing Data Sources Without a Restart

Editing the configuration file no longer requires restarting the server. Edit `datasource.yml`, then trigger a reload:

```bash
curl -X POST localhost:6789/api/datasource/reload
```

Or call the `reloadDataSources` tool from an MCP session.

**Behavior**

- The configuration file is the single source of truth. There is deliberately **no** add/remove endpoint — adding a data source means adding a block to the file, then reloading.
- Data sources whose configuration is unchanged keep their existing connection pool untouched; they are never rebuilt.
- A removed data source keeps serving in-flight queries for a grace period before its pool closes. The grace period is controlled by `datasource.reload-grace-seconds` (default 30).
- A malformed configuration file **aborts the entire reload** and changes nothing. A single unreachable database **fails alone**, is reported under `failed`, and the rest of the file still takes effect.
- MCP tool schemas do not enumerate data source names, so a reload never requires MCP clients to reconnect.

**Cases that still require a restart**

- Adding a database driver not bundled in the current JAR (only MySQL, PostgreSQL, SQLite, and Apache IoTDB are bundled). This requires a `pom.xml` change and a rebuild.
- Changing command-line arguments such as `--sql.security.enabled` or `--server.port`, which do not live in `datasource.yml`.

**Example response**

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

## MCP Configuration Integration

### MCP Configuration for Maven Wrapper Startup

MCP JSON configuration using custom data source configuration file:

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "/your-path/mcp-mysql-server/mvnw",
      "args": [
        "-q",
        "-Dspring-boot.run.arguments=--datasource.config=/your-path/your-datasource.yml",
        "-f",
        "/your-path/mcp-mysql-server/pom.xml",
        "spring-boot:run"
      ]
    }
  }
}
```

### MCP Configuration for JAR Package Startup

#### Using Default Configuration File

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-jar",
        "/your-path/mcp-mysql-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

#### Using Custom Configuration File

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-jar",
        "/your-path/mcp-mysql-server-0.0.1-SNAPSHOT.jar",
        "--datasource.config=/your-path/your-datasource.yml"
      ]
    }
  }
}
```

#### Using Both Extensions and Custom Data Source

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-Dloader.path=/your-path/src/main/resources/groovy",
        "-jar",
        "/your-path/mcp-mysql-server-0.0.1-SNAPSHOT.jar",
        "--datasource.config=/your-path/your-datasource.yml"
      ]
    }
  }
}
```

## Connection String Details

### Basic Format

```
jdbc:mysql://[host]:[port]/[database]?[parameters]
```

### Common Parameters

| Parameter                  | Description                | Example Value            |
|:--------------------------|:---------------------------|:------------------------|
| `useSSL`                  | Whether to use SSL connection | `false` or `true`       |
| `serverTimezone`          | Server timezone            | `UTC` or `Asia/Shanghai` |
| `characterEncoding`       | Character encoding         | `utf8mb4`               |
| `useUnicode`              | Whether to use Unicode     | `true`                  |
| `allowPublicKeyRetrieval` | Allow public key retrieval | `true`                  |

### Complete Example

```yaml
datasource:
  datasources:
    mydb:
      url: jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4&useUnicode=true
      username: root
      password: password
      default: true
```

## Security Considerations

### Password Protection

1. **Environment Variables**: It's recommended to store sensitive information like passwords in environment variables
2. **File Permissions**: Ensure configuration files have appropriate access permissions
3. **Version Control**: Do not commit configuration files containing sensitive information to version control systems

### Connection Security

1. **SSL Connections**: Enable SSL for production environments
2. **Network Restrictions**: Restrict network access to database servers
3. **User Permissions**: Create dedicated database users for applications, granting minimal necessary permissions

## Troubleshooting

### Common Connection Issues

1. **Connection Timeout**
   - Check network connectivity
   - Verify host and port
   - Confirm firewall settings

2. **Authentication Failure**
   - Verify username and password
   - Check user permissions
   - Confirm database user account status

3. **Database Does Not Exist**
   - Confirm database name spelling
   - Verify database has been created
   - Check if user has access permissions to the database

### Configuration File Issues

1. **YAML Format Errors**
   - Check if indentation is correct (use spaces, not tabs)
   - Verify YAML syntax
   - Confirm if special characters need quotes

2. **File Path Errors**
   - Use absolute paths
   - Check if file exists
   - Verify file permissions

### Debugging Recommendations

1. **Enable Verbose Logging**: Enable DEBUG level logging when starting the application
2. **Connection Testing**: Use MySQL client tools to test connections
3. **Step-by-step Troubleshooting**: Start with simple configurations and gradually add complex parameters

## Best Practices

1. **Environment Isolation**: Use different configuration files for different environments (development, testing, production)
2. **Naming Conventions**: Use meaningful data source names
3. **Documentation**: Add comments for each data source explaining its purpose
4. **Regular Reviews**: Regularly check and update data source configurations
5. **Configuration Backup**: Regularly backup important configuration files
