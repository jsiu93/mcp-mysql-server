# Windows 环境 MCP JSON 配置指南

## 概述

本文档提供了在Windows环境下配置MCP服务的JSON配置文件示例。请根据您的实际项目路径和需求选择合适的配置方式。

## 配置方式选择

### 方式一：Maven Wrapper 启动（推荐）
- 优点：无需预先构建JAR包，开发调试方便
- 缺点：首次启动可能较慢

### 方式二：JAR 包启动
- 优点：启动速度快，适合生产环境
- 缺点：需要先构建JAR包

## 1. Maven Wrapper 启动配置

### 基础配置（使用默认数据源）

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\mvnw.cmd",
      "args": [
        "-q",
        "-f",
        "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\pom.xml",
        "spring-boot:run"
      ]
    }
  }
}
```

### 使用自定义数据源配置

#### MySQL 配置
```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\mvnw.cmd",
      "args": [
        "-q",
        "-Dspring-boot.run.arguments=--datasource.config=C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\src\\main\\resources\\datasource-example.yml",
        "-f",
        "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\pom.xml",
        "spring-boot:run"
      ]
    }
  }
}
```

#### PostgreSQL 配置
```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\mvnw.cmd",
      "args": [
        "-q",
        "-Dspring-boot.run.arguments=--datasource.config=C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\src\\main\\resources\\datasource-postgresql-example.yml",
        "-f",
        "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\pom.xml",
        "spring-boot:run"
      ]
    }
  }
}
```

#### 自定义配置文件
```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\mvnw.cmd",
      "args": [
        "-q",
        "-Dspring-boot.run.arguments=--datasource.config=C:\\path\\to\\your\\custom-datasource.yml",
        "-f",
        "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\pom.xml",
        "spring-boot:run"
      ]
    }
  }
}
```

## 2. JAR 包启动配置

### 基础配置（不使用扩展功能）

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\target\\mcp-mysql-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

### 使用扩展功能

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-Dloader.path=C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\src\\main\\resources\\groovy",
        "-jar",
        "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\target\\mcp-mysql-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

### 使用自定义数据源配置

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\target\\mcp-mysql-server-0.0.1-SNAPSHOT.jar",
        "--datasource.config=C:\\path\\to\\your\\custom-datasource.yml"
      ]
    }
  }
}
```

### 完整配置（扩展功能 + 自定义数据源）

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-Dloader.path=C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\src\\main\\resources\\groovy",
        "-jar",
        "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\target\\mcp-mysql-server-0.0.1-SNAPSHOT.jar",
        "--datasource.config=C:\\path\\to\\your\\custom-datasource.yml"
      ]
    }
  }
}
```

## 3. 路径配置说明

### 重要提示
1. **使用双反斜杠** (`\\`) 或 **正斜杠** (`/`) 作为路径分隔符
2. **替换实际路径**：将示例中的 `C:\\Users\\YourUsername\\mcp\\mcp-mysql-server` 替换为您的实际项目路径
3. **确保文件存在**：配置前请确认所有路径中的文件都存在

### 路径示例对照表

| 组件 | Linux/Mac 示例 | Windows 示例 |
|------|---------------|-------------|
| Maven Wrapper | `./mvnw` | `mvnw.cmd` 或 `C:\\path\\to\\mvnw.cmd` |
| POM 文件 | `/path/to/pom.xml` | `C:\\path\\to\\pom.xml` |
| JAR 文件 | `/path/to/app.jar` | `C:\\path\\to\\app.jar` |
| 配置文件 | `/path/to/config.yml` | `C:\\path\\to\\config.yml` |

## 4. 常见问题解决

### 问题1：找不到 mvnw.cmd
**解决方案**：确保使用完整路径，例如：
```json
"command": "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\mvnw.cmd"
```

### 问题2：路径包含空格
**解决方案**：路径包含空格时无需额外引号，JSON会自动处理：
```json
"command": "C:\\Program Files\\Java\\bin\\java"
```

### 问题3：配置文件路径错误
**解决方案**：使用绝对路径，确保文件存在：
```json
"-Dspring-boot.run.arguments=--datasource.config=C:\\full\\path\\to\\your-config.yml"
```

## 5. 验证配置

### 检查文件是否存在
在命令提示符中运行以下命令验证路径：

```cmd
# 检查 Maven Wrapper
dir "C:\Users\YourUsername\mcp\mcp-mysql-server\mvnw.cmd"

# 检查 JAR 文件
dir "C:\Users\YourUsername\mcp\mcp-mysql-server\target\mcp-mysql-server-0.0.1-SNAPSHOT.jar"

# 检查配置文件
dir "C:\path\to\your\custom-datasource.yml"
```

### 测试启动
配置完成后，可以在命令行中手动测试启动命令：

```cmd
# Maven 方式
C:\Users\YourUsername\mcp\mcp-mysql-server\mvnw.cmd -q -f C:\Users\YourUsername\mcp\mcp-mysql-server\pom.xml spring-boot:run

# JAR 方式
java -jar C:\Users\YourUsername\mcp\mcp-mysql-server\target\mcp-mysql-server-0.0.1-SNAPSHOT.jar
```

## 6. 推荐配置模板

### 开发环境推荐配置
```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\mvnw.cmd",
      "args": [
        "-q",
        "-Dspring-boot.run.arguments=--datasource.config=C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\my-datasource.yml",
        "-f",
        "C:\\Users\\YourUsername\\mcp\\mcp-mysql-server\\pom.xml",
        "spring-boot:run"
      ]
    }
  }
}
```

### 生产环境推荐配置
```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\mcp\\mcp-mysql-server-0.0.1-SNAPSHOT.jar",
        "--datasource.config=C:\\mcp\\config\\production-datasource.yml"
      ]
    }
  }
}
```
