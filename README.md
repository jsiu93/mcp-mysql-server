# MCP MySQL Server

[中文文档](#中文文档) | [English Documentation](#english-documentation)

## 快速上手

### 1. MCP JSON 配置

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "/Users/xin.y/IdeaProjects/mcp-mysql-server/mvnw",
      "args": [
        "-f",
        "/Users/xin.y/IdeaProjects/mcp-mysql-server/pom.xml",
        "spring-boot:run"
      ]
    }
  }
}
```

### 2. 数据源配置（最小配置）

创建 `datasource.yml` 文件:

```yaml
datasource:
  datasources:
    default:
      url: jdbc:mysql://localhost:3306/your_db
      username: root
      password: your_password
      default: true
```

### 3. 扩展功能（可选）

创建 `extension.yml` 文件并添加扩展:

```yaml
extensions:
  - name: yourExtension
    description: "您的扩展描述"
    mainFileName: main.groovy  # 放置在 src/main/resources/groovy/yourExtension/script/ 目录下
```

---

## MCP MySQL Server

一个基于 Spring AI 的MCP，支持执行任意 SQL。

## 环境要求

- JDK 21 或更高版本
- Maven 3.8.0 或更高版本
- MySQL 5.7 或更高版本（作为目标数据库）

## 功能特点

- 支持执行任意 SQL 语句，不做限制
- 支持多数据源配置
- 支持动态切换数据源
- 支持用户自定义数据源配置

## 扩展功能 (Extension Functionality)

支持通过 Groovy 脚本来扩展其功能。这些扩展可以在运行时通过特定的工具调用来执行。

### 配置文件

扩展功能通过 `src/main/resources/extension.yml` 文件进行配置。文件定义了一个扩展列表，每个扩展可以包含以下参数：

| 参数           | 类型    | 是否必需/可选 | 描述                                                                                                                   | 示例                                                    |
|----------------|---------|---------------|----------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `name`         | String  | 必需          | 扩展的唯一名称，用于调用扩展。                                                                                                      | `zstdDecode`                                            |
| `description`  | String  | 可选          | 扩展功能的描述。                                                                                                             | `"解码业务快照数据"`                                      |
| `enabled`      | Boolean | 可选          | 是否启用该扩展，默认为 `true`。设置为 `false` 则禁用。                                                                                  | `false`                                                 |
| `prompt`       | String  | 可选          | 使用此扩展时给AI模型的建议提示或说明。                                                                                                 | `"decode the snapshot_data..."`                         |
| `script`       | String  | 可选          | 内联的 Groovy 脚本代码。如果提供了此参数，则会忽略 `mainFileName` 及对应的脚本文件。                                                               | `` `def greet(name) { return "Hello, $name!"; }; greet('Java')` `` |
| `mainFileName` | String  | 条件性必需    | Groovy 脚本文件的名称 (例如 `main.groovy`)。如果未提供 `script` 参数，则此参数为必需。脚本文件必须位于 `src/main/resources/groovy/<name>/script/` 目录下。 | `main.groovy`                                           |

**脚本和依赖项:**

*   **脚本文件**: 如果不使用内联 `script`，则每个扩展的 Groovy 脚本文件应放置在 `src/main/resources/groovy/<extension_name>/script/` 目录下，并通过 `mainFileName` 参数指定文件名。例如，对于名为 `zstdDecode` 的扩展，其主脚本文件（如 `main.groovy`）应位于 `src/main/resources/groovy/zstdDecode/script/main.groovy`。
*   **依赖 JAR 包**: 如果扩展需要外部 Java 库 (JARs)，这些 JAR 文件应放置在 `src/main/resources/groovy/<extension_name>/dependency/` 目录下。服务启动时会自动加载这些依赖。例如，`zstdDecode` 扩展的依赖 `zstd-jni-1.5.5-10.jar` 位于 `src/main/resources/groovy/zstdDecode/dependency/zstd-jni-1.5.5-10.jar`。

### 重要提示：运行带有扩展的应用

为了确保扩展功能（特别是依赖加载）能正确工作，请使用 Maven Wrapper 通过 `spring-boot:run` 启动应用，而不是直接通过 `java -jar` 执行 JAR 包。这是因为直接运行 JAR 包时，扩展的类路径和依赖加载可能存在问题。

请使用如上所示的 MCP JSON 配置，注意将路径替换为您项目的实际绝对路径。

## 数据源配置

### 默认配置

应用默认使用 `src/main/resources/datasource.yml` 中的数据源配置。

### 自定义配置

用户可以通过命令行参数指定自定义配置文件：

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/your-datasource.yml
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

### 运行项目（推荐方式）

```bash
./mvnw spring-boot:run
```

### 指定自定义数据源配置

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/your-datasource.yml
```

### 使用 JAR 包运行（可能导致扩展功能问题）

也可以直接通过 JAR 包运行，但可能导致扩展功能不正常：

```bash
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar
```

## MCP MySQL Server

A Spring AI MCP server that supports executing arbitrary SQL.

## MCP JSON Configuration (Copy to use)

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "/Users/xin.y/IdeaProjects/mcp-mysql-server/mvnw",
      "args": [
        "-f",
        "/Users/xin.y/IdeaProjects/mcp-mysql-server/pom.xml",
        "spring-boot:run"
      ]
    }
  }
}
```

## Environment Requirements

- JDK 21 or higher
- Maven 3.8.0 or higher
- MySQL 5.7 or higher (as target database)

## Features

- Supports executing any SQL statement without restrictions
- Supports multiple datasource configurations
- Supports dynamic datasource switching
- Supports user-defined datasource configuration

## Extension Functionality

This application supports extending its functionality using Groovy scripts. These extensions can be executed at runtime via specific tool invocations.

### Configuration File

Extensions are configured via the `src/main/resources/extension.yml` file. This file defines a list of extensions, where each extension can have the following parameters:

| Parameter      | Type    | Required/Optional | Description                                                                                                                                                                                  | Example                                                 |
|----------------|---------|-------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `name`         | String  | Required          | The unique name of the extension, used to invoke it.                                                                                                                                         | `zstdDecode`                                            |
| `description`  | String  | Optional          | A description of what the extension does.                                                                                                                                                    | `"解码业务快照数据"` (Decodes business snapshot data)     |
| `enabled`      | Boolean | Optional          | Whether the extension is enabled. Defaults to `true`. Set to `false` to disable.                                                                                                             | `false`                                                 |
| `prompt`       | String  | Optional          | A suggested prompt or instruction for the AI when using this extension.                                                                                                                      | `"decode the snapshot_data..."`                         |
| `script`       | String  | Optional          | Inline Groovy script code. If this is provided, `mainFileName` and its corresponding script file are ignored.                                                                                | `` `def greet(name) { return "Hello, $name!"; }; greet('Java')` `` |
| `mainFileName` | String  | Conditionally Req.| The name of the Groovy script file (e.g., `main.groovy`). Required if the `script` parameter is not provided. The script file must be located in `src/main/resources/groovy/<name>/script/`. | `main.groovy`                                           |

**Scripts and Dependencies:**

*   **Script Files**: If not using an inline `script`, each extension's Groovy script file should be placed in the `src/main/resources/groovy/<extension_name>/script/` directory. The filename is specified by the `mainFileName` parameter. For example, for an extension named `zstdDecode`, its main script file (e.g., `main.groovy`) should be at `src/main/resources/groovy/zstdDecode/script/main.groovy`.
*   **Dependency JARs**: If an extension requires external Java libraries (JARs), these files should be placed in the `src/main/resources/groovy/<extension_name>/dependency/` directory. The service will automatically load these dependencies upon startup. For example, the `zstdDecode` extension's dependency `zstd-jni-1.5.5-10.jar` is located at `src/main/resources/groovy/zstdDecode/dependency/zstd-jni-1.5.5-10.jar`.

### Important Note: Running the Application with Extensions

To ensure that extension functionality (especially dependency loading) works correctly, please use Maven Wrapper to start the application via `spring-boot:run`, rather than executing the JAR file directly with `java -jar`. This is because running directly from the JAR may cause issues with classpath and dependency loading for extensions.

Please use the MCP JSON configuration shown above, remembering to replace the paths with the actual absolute path to your project.

## Datasource Configuration

### Default Configuration

The application uses the datasource configuration in `src/main/resources/datasource.yml` by default.

### Custom Configuration

Users can specify a custom configuration file using the command line parameter:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/your-datasource.yml
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

### Run the Project (Recommended)

```bash
./mvnw spring-boot:run
```

### Specify Custom Datasource Configuration

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/your-datasource.yml
```

### Running with JAR file (May cause extension issues)

You can also run directly with the JAR file, but this may cause issues with extensions:

```bash
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar
```
