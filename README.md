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
      "command": "java -jar /Users/xin.y/IdeaProjects/mcp-mysql-server/target/mcp-mysql-server-0.0.1-SNAPSHOT.jar",
      "args": [
      ]
    }
    
  }
}
```

## 功能特点

- 支持执行任意 SQL 语句，不做限制
- 支持多数据源配置
- 支持动态切换数据源
- 支持用户自定义数据源配置

## 扩展功能 (Extension Functionality)

本应用支持通过 Groovy 脚本来扩展其功能。这些扩展可以在运行时通过特定的工具调用来执行。

### 配置文件

扩展功能通过 `src/main/resources/extension.yml` 文件进行配置。文件定义了一个扩展列表，每个扩展可以包含以下参数：

| 参数           | 类型    | 是否必需/可选 | 描述                                                                                                                               | 示例                                                    |
|----------------|---------|---------------|------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `name`         | String  | 必需          | 扩展的唯一名称，用于调用扩展。                                                                                                       | `zstdDecode`                                            |
| `description`  | String  | 可选          | 扩展功能的描述。                                                                                                                     | `"解码业务快照数据"`                                      |
| `enabled`      | Boolean | 可选          | 是否启用该扩展，默认为 `true`。设置为 `false` 则禁用。                                                                                 | `false`                                                 |
| `prompt`       | String  | 可选          | 使用此扩展时给用户的建议提示或说明。                                                                                                   | `"decode the snapshot_data..."`                         |
| `script`       | String  | 可选          | 内联的 Groovy 脚本代码。如果提供了此参数，则会忽略 `mainFileName` 及对应的脚本文件。                                                       | `` `def greet(name) { return "Hello, $name!"; }; greet('Java')` `` |
| `mainFileName` | String  | 条件性必需    | Groovy 脚本文件的名称 (例如 `main.groovy`)。如果未提供 `script` 参数，则此参数为必需。脚本文件必须位于 `src/main/resources/groovy/<name>/script/` 目录下。 | `main.groovy`                                           |

**脚本和依赖项:**

*   **脚本文件**: 如果不使用内联 `script`，则每个扩展的 Groovy 脚本文件应放置在 `src/main/resources/groovy/<extension_name>/script/` 目录下，并通过 `mainFileName` 参数指定文件名。例如，对于名为 `zstdDecode` 的扩展，其主脚本文件（如 `main.groovy`）应位于 `src/main/resources/groovy/zstdDecode/script/main.groovy`。
*   **依赖 JAR 包**: 如果扩展需要外部 Java 库 (JARs)，这些 JAR 文件应放置在 `src/main/resources/groovy/<extension_name>/dependency/` 目录下。服务启动时会自动加载这些依赖。例如，`zstdDecode` 扩展的依赖 `zstd-jni-1.5.5-10.jar` 位于 `src/main/resources/groovy/zstdDecode/dependency/zstd-jni-1.5.5-10.jar`。

### 重要提示：运行带有扩展的应用

目前，为了确保扩展功能（特别是依赖加载）能正确工作，MCP JSON 配置中的 `command` 需要指向 Maven Wrapper 来通过 `spring-boot:run` 启动应用，而不是直接通过 `java -jar` 执行 JAR 包。这是因为直接运行 JAR 包时，扩展的类路径和依赖加载可能存在问题 (参见代码中 `GroovyService.java` 的 `FIXME` 标记)。

请使用类似如下的 MCP JSON 配置：

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "/path/to/your/project/mvnw -f /path/to/your/project/pom.xml spring-boot:run",
      "args": []
    }
  }
}
```
**注意**: 请将 `/path/to/your/project/` 替换为您项目的实际绝对路径。

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

## Extension Functionality

This application supports extending its functionality using Groovy scripts. These extensions can be executed at runtime via specific tool invocations.

### Configuration File

Extensions are configured via the `src/main/resources/extension.yml` file. This file defines a list of extensions, where each extension can have the following parameters:

| Parameter      | Type    | Required/Optional | Description                                                                                                                               | Example                                                 |
|----------------|---------|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `name`         | String  | Required          | The unique name of the extension, used to invoke it.                                                                                      | `zstdDecode`                                            |
| `description`  | String  | Optional          | A description of what the extension does.                                                                                                 | `"解码业务快照数据"` (Decodes business snapshot data)     |
| `enabled`      | Boolean | Optional          | Whether the extension is enabled. Defaults to `true`. Set to `false` to disable.                                                          | `false`                                                 |
| `prompt`       | String  | Optional          | A suggested prompt or instruction for the user when using this extension.                                                                 | `"decode the snapshot_data..."`                         |
| `script`       | String  | Optional          | Inline Groovy script code. If this is provided, `mainFileName` and its corresponding script file are ignored.                              | `` `def greet(name) { return "Hello, $name!"; }; greet('Java')` `` |
| `mainFileName` | String  | Conditionally Req.| The name of the Groovy script file (e.g., `main.groovy`). Required if the `script` parameter is not provided. The script file must be located in `src/main/resources/groovy/<name>/script/`. | `main.groovy`                                           |

**Scripts and Dependencies:**

*   **Script Files**: If not using an inline `script`, each extension's Groovy script file should be placed in the `src/main/resources/groovy/<extension_name>/script/` directory. The filename is specified by the `mainFileName` parameter. For example, for an extension named `zstdDecode`, its main script file (e.g., `main.groovy`) should be at `src/main/resources/groovy/zstdDecode/script/main.groovy`.
*   **Dependency JARs**: If an extension requires external Java libraries (JARs), these files should be placed in the `src/main/resources/groovy/<extension_name>/dependency/` directory. The service will automatically load these dependencies upon startup. For example, the `zstdDecode` extension's dependency `zstd-jni-1.5.5-10.jar` is located at `src/main/resources/groovy/zstdDecode/dependency/zstd-jni-1.5.5-10.jar`.

### Important Note: Running the Application with Extensions

Currently, to ensure that extension functionality (especially dependency loading) works correctly, the `command` in your MCP JSON configuration needs to point to the Maven Wrapper to start the application via `spring-boot:run`, rather than executing the JAR file directly with `java -jar`. This is because running directly from the JAR may cause issues with classpath and dependency loading for extensions (see the `FIXME` comment in `GroovyService.java` in the codebase).

Please use an MCP JSON configuration similar to the following:

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "/path/to/your/project/mvnw -f /path/to/your/project/pom.xml spring-boot:run",
      "args": []
    }
  }
}
```
**Note**: Remember to replace `/path/to/your/project/` with the actual absolute path to your project.

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
