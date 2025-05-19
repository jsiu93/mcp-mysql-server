# MCP MySQL Server

[中文文档](#中文文档) | [English Documentation](#english-documentation)

## 中文文档

一个基于 Spring AI 的MCP，支持执行任意 SQL。

## 环境要求

- JDK 21 或更高版本
- Maven 3.8.0 或更高版本
- MySQL 5.7 或更高版本（作为目标数据库）

## MCP JSON 配置

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/xin.y/IdeaProjects/mcp-mysql-server/target/mcp-mysql-server-0.0.1-SNAPSHOT.jar"
      ],
      "env": {}
    }
  }
}
```

## 功能特点

- 支持执行任意 SQL 语句，不做限制
- 支持多数据源配置
- 支持动态切换数据源
- 支持用户自定义数据源配置

## 数据源配置

### 默认配置

应用默认使用 `src/main/resources/datasource.yml` 中的数据源配置。

### 自定义配置

用户可以通过命令行参数 `--datasource.config=<配置文件路径>` 指定自定义配置文件：

```bash
java -jar mcp-mysql-server-0.0.1-SNAPSHOT.jar --datasource.config=/path/to/your-datasource.yml
```

### 配置文件格式

数据源配置文件使用 YAML 格式，示例如下：

```yaml
datasource:
  datasources:
    # 第一个数据源
    db1:
      url: jdbc:mysql://localhost:3306/db1
      username: root
      password: password
      default: true  # 标记为默认数据源

    # 第二个数据源
    db2:
      url: jdbc:mysql://localhost:3306/db2
      username: root
      password: password
```

配置说明：

- 所有数据源配置在 `datasource.datasources` 下
- 可以通过 `default: true` 标记默认数据源，如果没有标记则默认使用第一个
- 用户只需要配置 `url`、`username`、`password` 即可，其他配置都有合理的默认值

## 使用方法

### 构建项目

```bash
./mvnw clean package
```

### 运行项目

```bash
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar
```

### 指定自定义数据源配置

```bash
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar --datasource.config=/path/to/your-datasource.yml
```

## English Documentation

A Spring AI MCP server that supports executing arbitrary SQL.

## Environment Requirements

- JDK 21 or higher
- Maven 3.8.0 or higher
- MySQL 5.7 or higher (as target database)

## MCP JSON Configuration

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/xin.y/IdeaProjects/mcp-mysql-server/target/mcp-mysql-server-0.0.1-SNAPSHOT.jar"
      ],
      "env": {}
    }
  }
}
```

## Features

- Supports executing any SQL statement without restrictions
- Supports multiple datasource configurations
- Supports dynamic datasource switching
- Supports user-defined datasource configuration

## Datasource Configuration

### Default Configuration

The application uses the datasource configuration in `src/main/resources/datasource.yml` by default.

### Custom Configuration

Users can specify a custom configuration file using the command line parameter
`--datasource.config=<configuration file path>`:

```bash
java -jar mcp-mysql-server-0.0.1-SNAPSHOT.jar --datasource.config=/path/to/your-datasource.yml
```

### Configuration File Format

The datasource configuration file uses YAML format, for example:

```yaml
datasource:
  datasources:
    # First datasource
    db1:
      url: jdbc:mysql://localhost:3306/db1
      username: root
      password: password
      default: true  # Mark as default datasource

    # Second datasource
    db2:
      url: jdbc:mysql://localhost:3306/db2
      username: root
      password: password
```

Configuration notes:

- All datasource configurations are under `datasource.datasources`
- You can mark a default datasource with `default: true`, if not marked, the first one will be used as default
- Users only need to configure `url`, `username`, and `password`, other configurations have reasonable defaults

## Usage

### Build the Project

```bash
./mvnw clean package
```

### Run the Project

```bash
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar
```

### Specify Custom Datasource Configuration

```bash
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar --datasource.config=/path/to/your-datasource.yml
```
