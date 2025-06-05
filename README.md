# MCP MySQL Server

一个基于 Spring AI 的MCP，可执行任意 SQL。

[中文文档](README.md) | [English Documentation](README_EN.md)

## 快速上手

### 1\. MCP JSON 配置

#### 推荐方式 (Maven Wrapper)

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

#### JAR 包方式 (可选，不支持扩展)

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-jar",
        "/Users/yangxin/IdeaProjects/mcp-mysql-server/target/mcp-mysql-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

### 2\. 数据源配置（最小配置）

修改 `mcp-mysql-server/src/main/resources/datasource.yml` 文件，添加你的数据源信息：

```yaml
datasource:
  datasources:
    # 第一个数据源示例
    your_db1_name:
      url: jdbc:mysql://localhost:3306/db1
      username: root
      password: password
      default: true  # 标记为默认数据源，如果未标记，则默认使用第一个
```

-----

### 扩展功能（可选）

参考 `mcp-mysql-server/src/main/resources/extension.yml` 文件进行配置，以添加或管理你的扩展功能：

```yaml
extensions:
  - name: yourExtension
    description: "您的扩展描述"
    mainFileName: main.groovy  # 放置在 src/main/resources/groovy/yourExtension/script/ 目录下
```

-----


## 环境要求

在运行之前，请确保你的环境满足以下条件：

* **JDK 21** 或更高版本
* **Maven**
* **MySQL**（作为目标数据库）

## 功能特点

* **无限制 SQL 执行**：支持执行任意 SQL 语句，没有任何限制。
* **多数据源支持**：支持配置和管理多个数据库数据源。
* **动态数据源切换**：能够在运行时动态切换不同的数据源。
* **用户自定义数据源**：允许用户自定义和配置自己的数据源。

-----

## 扩展功能

MCP MySQL Server 支持通过 Groovy 脚本来扩展其功能。这些扩展可以在运行时通过特定的工具调用来执行。

### 配置文件

扩展功能通过 `src/main/resources/extension.yml` 文件进行配置。此文件定义了一个扩展列表，每个扩展可以包含以下参数：

| 参数             | 类型        | 是否必需/可选 | 描述                                                                                                                  | 示例                                                                 |
|:---------------|:----------|:--------|:--------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------|
| `name`         | `String`  | 必需      | 扩展的唯一名称，用于调用该扩展。                                                                                                    | `zstdDecode`                                                       |
| `description`  | `String`  | 可选      | 扩展功能的简要描述。                                                                                                          | `"解码业务快照数据"`                                                       |
| `enabled`      | `Boolean` | 可选      | 是否启用该扩展。默认为 `true`。设置为 `false` 则禁用。                                                                                 | `false`                                                            |
| `prompt`       | `String`  | 可选      | 当使用此扩展时，提供给 AI 模型的建议提示或说明。                                                                                          | `"decode the snapshot_data..."`                                    |
| `script`       | `String`  | 可选      | 内联的 Groovy 脚本代码。如果提供了此参数，则会忽略 `mainFileName` 及对应的脚本文件。**请注意，对于复杂的脚本或需要外部依赖的扩展，推荐使用 `mainFileName` 指定脚本文件。**         | `` `def greet(name) { return "Hello, $name!"; }; greet('Java')` `` |
| `mainFileName` | `String`  | 条件性必需   | Groovy 脚本文件的名称（例如 `main.groovy`）。如果未提供 `script` 参数，则此参数为必需。脚本文件必须位于 `src/main/resources/groovy/<name>/script/` 目录下。 | `main.groovy`                                                      |

**脚本和依赖项管理：**

* **脚本文件**：如果不使用内联 `script`，则每个扩展的 Groovy 脚本文件应放置在
  `src/main/resources/groovy/<extension_name>/script/` 目录下。脚本文件的具体名称通过 `mainFileName` 参数指定。例如，对于名为
  `zstdDecode` 的扩展，其主脚本文件（如 `main.groovy`）应位于 `/src/main/resources/groovy/zstdDecode/script/main.groovy`。
* **依赖 JAR 包**：如果扩展需要外部 Java 库（JARs），请将这些 JAR 文件放置在
  `src/main/resources/groovy/<extension_name>/dependency/` 目录下。服务启动时会自动加载这些依赖。例如，`zstdDecode` 扩展的依赖
  `zstd-jni-1.5.5-10.jar` 应位于 `/src/main/resources/groovy/zstdDecode/dependency/zstd-jni-1.5.5-10.jar`。

### 重要提示：运行带有扩展的应用

为确保扩展功能（特别是依赖加载）能正确工作，请**务必使用 Maven Wrapper 通过 `spring-boot:run` 启动应用**，而不是直接通过
`java -jar` 执行 JAR 包。这是因为直接运行 JAR 包时，扩展的类路径和依赖加载可能存在问题。

请参考“MCP JSON 配置”部分中的推荐方式，并务必将路径替换为你项目的实际绝对路径。

-----

## 数据源配置

### 默认配置

应用默认使用 `src/main/resources/datasource.yml` 中的数据源配置。

### 自定义配置

用户可以通过命令行参数指定自定义数据源配置文件，这在不同的部署环境或测试场景下非常有用：

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/your-datasource.yml
```

### 配置文件格式

数据源配置文件采用 YAML 格式，示例如下：

```yaml
datasource:
  datasources:
    # 第一个数据源示例
    your_db1_name:
      url: jdbc:mysql://localhost:3306/db1
      username: root
      password: password
      default: true  # 标记为默认数据源，如果未标记，则默认使用第一个

    # 第二个数据源示例
    your_db2_name:
      url: jdbc:mysql://localhost:3306/db2
      username: root
      password: password
```

-----

## 使用方法

### 构建项目

在项目根目录执行以下命令，编译并打包项目：

```bash
./mvnw clean package
```

### 运行项目（推荐方式）

推荐使用 Maven Wrapper 启动应用，以确保所有功能（包括扩展）正常工作：

```bash
./mvnw spring-boot:run
```

### 运行项目并指定自定义数据源配置

如果你需要使用自定义的数据源配置文件，可以在运行命令中添加参数：

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/your-datasource.yml
```

对应的 MCP JSON 配置如下：

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "/your-path/mcp-mysql-server/mvnw",
      "args": [
        "-Dspring-boot.run.arguments=--datasource.config=/your-path/your-datasource.yml",
        "-f",
        "/your-path/mcp-mysql-server/pom.xml",
        "spring-boot:run"
      ]
    }
  }
}
```

### 使用 JAR 包运行（该方式不支持扩展）

```bash
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar
```

对应的 MCP JSON 配置如下：

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
