# PostgreSQL 支持文档

## 概述

本项目现已支持 PostgreSQL 数据库，除了原有的 MySQL 支持外，您现在可以：

- 使用 PostgreSQL 作为主数据源
- 同时配置 MySQL 和 PostgreSQL 数据源
- 自动检测数据库类型并设置正确的驱动程序

## 支持的数据库类型

| 数据库类型 | JDBC URL 前缀 | 驱动类名 | 状态 |
|-----------|--------------|---------|------|
| MySQL | `jdbc:mysql:` | `com.mysql.cj.jdbc.Driver` | ✅ 支持 |
| PostgreSQL | `jdbc:postgresql:` | `org.postgresql.Driver` | ✅ 支持 |
| Oracle | `jdbc:oracle:` | `oracle.jdbc.OracleDriver` | ✅ 支持 |
| SQL Server | `jdbc:sqlserver:` | `com.microsoft.sqlserver.jdbc.SQLServerDriver` | ✅ 支持 |
| H2 | `jdbc:h2:` | `org.h2.Driver` | ✅ 支持 |

## PostgreSQL 配置示例

### 1. 纯 PostgreSQL 配置

创建配置文件 `my-postgres-config.yml`：

```yaml
datasource:
  datasources:
    primary:
      url: jdbc:postgresql://localhost:5432/mydb?connectTimeout=10&socketTimeout=10&useUnicode=true&characterEncoding=UTF-8
      username: postgres
      password: your_password
      default: true
      hikari:
        pool-name: PostgreSQLHikariCP
        maximum-pool-size: 10
        minimum-idle: 5
```

### 2. MySQL 和 PostgreSQL 混合配置

```yaml
datasource:
  datasources:
    # MySQL 主数据源
    mysql_primary:
      url: jdbc:mysql://localhost:3306/mysql_db?connectTimeout=10000&socketTimeout=10000&useUnicode=true&characterEncoding=UTF-8&useTimezone=true&serverTimezone=Asia/Shanghai&allowMultiQueries=true
      username: root
      password: mysql_password
      default: true
      
    # PostgreSQL 辅助数据源
    postgres_secondary:
      url: jdbc:postgresql://localhost:5432/postgres_db?connectTimeout=10&socketTimeout=10&useUnicode=true&characterEncoding=UTF-8
      username: postgres
      password: postgres_password
      
    # 另一个 PostgreSQL 数据源
    postgres_analytics:
      url: jdbc:postgresql://analytics-server:5432/analytics_db
      username: analytics_user
      password: analytics_password
```

## 启动应用

### 使用自定义 PostgreSQL 配置

```bash
# Maven Wrapper 方式
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/my-postgres-config.yml

# JAR 包方式
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar --datasource.config=/path/to/my-postgres-config.yml
```

### 使用提供的示例配置

```bash
# 使用 PostgreSQL 示例配置
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=src/main/resources/datasource-postgresql-example.yml

# 使用混合配置示例
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=src/main/resources/datasource-example.yml
```

## PostgreSQL 连接参数说明

### 常用连接参数

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| `connectTimeout` | 连接超时时间（秒） | `10` |
| `socketTimeout` | Socket 超时时间（秒） | `10` |
| `useUnicode` | 使用 Unicode | `true` |
| `characterEncoding` | 字符编码 | `UTF-8` |

### PostgreSQL 特有参数

| 参数 | 说明 | 示例 |
|------|------|------|
| `ssl` | 启用 SSL 连接 | `ssl=true` |
| `sslmode` | SSL 模式 | `sslmode=require` |
| `currentSchema` | 默认模式 | `currentSchema=public` |
| `ApplicationName` | 应用程序名称 | `ApplicationName=mcp-server` |

### 完整的 PostgreSQL URL 示例

```
jdbc:postgresql://localhost:5432/mydb?connectTimeout=10&socketTimeout=10&useUnicode=true&characterEncoding=UTF-8&ssl=false&currentSchema=public&ApplicationName=mcp-server
```

## 自动驱动检测

系统会根据 JDBC URL 自动检测数据库类型：

- 无需手动指定 `driver-class-name`
- 支持大小写不敏感的 URL 检测
- 未知数据库类型会默认使用 MySQL 驱动

## 测试连接

### 使用 REST API 测试

```bash
# 测试默认数据源
curl -X GET "http://localhost:9433/api/datasource/test"

# 测试指定数据源
curl -X GET "http://localhost:9433/api/datasource/test?datasource=postgres_secondary"
```

### 执行 SQL 查询

```bash
# 在默认数据源执行查询
curl -X POST "http://localhost:9433/api/datasource/execute" \
  -d "sql=SELECT version()" \
  -H "Content-Type: application/x-www-form-urlencoded"

# 在指定 PostgreSQL 数据源执行查询
curl -X POST "http://localhost:9433/api/datasource/execute" \
  -d "sql=SELECT version()&datasource=postgres_secondary" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

## 故障排除

### 常见问题

1. **连接被拒绝**
   - 检查 PostgreSQL 服务是否运行
   - 验证主机名和端口号
   - 确认防火墙设置

2. **认证失败**
   - 验证用户名和密码
   - 检查 PostgreSQL 的 `pg_hba.conf` 配置
   - 确认用户有访问数据库的权限

3. **驱动未找到**
   - 确认 PostgreSQL 驱动已添加到依赖中
   - 检查 Maven 依赖是否正确下载

### 日志查看

应用启动时会输出数据库检测信息：

```
INFO  - 为数据源 [postgres_primary] 自动检测到数据库类型: PostgreSQL，使用驱动: org.postgresql.Driver
INFO  - Datasource [postgres_primary] created successfully
```

## 性能优化建议

### HikariCP 连接池配置

```yaml
hikari:
  pool-name: PostgreSQLHikariCP
  maximum-pool-size: 20        # 最大连接数
  minimum-idle: 5              # 最小空闲连接数
  connection-timeout: 30000    # 连接超时（毫秒）
  idle-timeout: 600000         # 空闲超时（毫秒）
  max-lifetime: 1800000        # 连接最大生命周期（毫秒）
  leak-detection-threshold: 60000  # 连接泄漏检测阈值（毫秒）
```

### PostgreSQL 特定优化

```yaml
url: jdbc:postgresql://localhost:5432/mydb?connectTimeout=10&socketTimeout=10&useUnicode=true&characterEncoding=UTF-8&prepareThreshold=0&preparedStatementCacheQueries=256&preparedStatementCacheSizeMiB=5
```

## 更多信息

- [PostgreSQL JDBC 驱动文档](https://jdbc.postgresql.org/documentation/)
- [HikariCP 配置文档](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
- [Spring Boot 数据源配置](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.sql.datasource)
