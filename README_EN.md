# MCP MySQL Server

A Spring AI-based MCP capable of executing any SQL query.

[中文文档](README.md) | [English Documentation](README_EN.md)

## Quick Start

### 1\. MCP JSON Configuration

#### Recommended Method (Maven Wrapper)

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

#### JAR Package Method (Optional, extensions not supported)

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

### 2\. Data Source Configuration (Minimal Configuration)

Modify the `mcp-mysql-server/src/main/resources/datasource.yml` file to add your data source information:

```yaml
datasource:
  datasources:
    # First data source example
    your_db1_name:
      url: jdbc:mysql://localhost:3306/db1
      username: root
      password: password
      default: true  # Mark as default data source, if not marked, the first one will be used by default
```

-----

### Extensions (Optional)

Refer to the `mcp-mysql-server/src/main/resources/extension.yml` file to add or manage your extensions:

```yaml
extensions:
  - name: yourExtension
    description: "Your extension description"
    mainFileName: main.groovy  # Place in the src/main/resources/groovy/yourExtension/script/ directory
```

-----

## Environment Requirements

Before running, please ensure your environment meets the following conditions:

* **JDK 21** or higher
* **Maven**
* **MySQL** (as the target database)

## Features

* **Unrestricted SQL Execution**: Support for executing any SQL statement without limitations.
* **Multiple Data Source Support**: Support for configuring and managing multiple database data sources.
* **Dynamic Data Source Switching**: Ability to dynamically switch between different data sources at runtime.
* **User-Defined Data Sources**: Allow users to customize and configure their own data sources.

-----

## Extensions

MCP MySQL Server supports extending its functionality through Groovy scripts. These extensions can be executed at runtime via specific tool calls.

### Configuration File

Extensions are configured through the `src/main/resources/extension.yml` file. This file defines a list of extensions, each of which can include the following parameters:

| Parameter      | Type       | Required/Optional | Description                                                                                                                     | Example                                                             |
|:---------------|:-----------|:------------------|:--------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------|
| `name`         | `String`   | Required          | Unique name of the extension, used to call the extension.                                                                       | `zstdDecode`                                                        |
| `description`  | `String`   | Optional          | Brief description of the extension functionality.                                                                               | `"Decode business snapshot data"`                                   |
| `enabled`      | `Boolean`  | Optional          | Whether to enable the extension. Default is `true`. Set to `false` to disable.                                                  | `false`                                                             |
| `prompt`       | `String`   | Optional          | Suggested prompt or instruction provided to the AI model when using this extension.                                             | `"decode the snapshot_data..."`                                     |
| `script`       | `String`   | Optional          | Inline Groovy script code. If this parameter is provided, `mainFileName` and its corresponding script file will be ignored. **Note: For complex scripts or extensions requiring external dependencies, it is recommended to use `mainFileName` to specify the script file.** | `` `def greet(name) { return "Hello, $name!"; }; greet('Java')` `` |
| `mainFileName` | `String`   | Conditionally Required | Name of the Groovy script file (e.g., `main.groovy`). This parameter is required if the `script` parameter is not provided. The script file must be located in the `src/main/resources/groovy/<name>/script/` directory. | `main.groovy`                                                       |

**Script and Dependency Management:**

* **Script Files**: If not using an inline `script`, each extension's Groovy script file should be placed in the 
  `src/main/resources/groovy/<extension_name>/script/` directory. The specific name of the script file is specified through the `mainFileName` parameter. For example, for an extension named 
  `zstdDecode`, its main script file (e.g., `main.groovy`) should be located at `/src/main/resources/groovy/zstdDecode/script/main.groovy`.
* **Dependency JAR Packages**: If the extension requires external Java libraries (JARs), please place these JAR files in the
  `src/main/resources/groovy/<extension_name>/dependency/` directory. These dependencies will be automatically loaded when the service starts. For example, the dependency
  `zstd-jni-1.5.5-10.jar` for the `zstdDecode` extension should be located at `/src/main/resources/groovy/zstdDecode/dependency/zstd-jni-1.5.5-10.jar`.

### Important Note: Running Applications with Extensions

To ensure that extension functionality (especially dependency loading) works correctly, please **be sure to use Maven Wrapper with `spring-boot:run` to start the application** rather than directly executing the JAR package via 
`java -jar`. This is because when running the JAR package directly, there may be issues with the extension's classpath and dependency loading.

Please refer to the recommended method in the "MCP JSON Configuration" section, and be sure to replace the paths with the actual absolute paths of your project.

-----

## Data Source Configuration

### Default Configuration

By default, the application uses the data source configuration in `src/main/resources/datasource.yml`.

### Custom Configuration

Users can specify a custom data source configuration file via command line arguments, which is very useful in different deployment environments or testing scenarios:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/your-datasource.yml
```

### Configuration File Format

The data source configuration file uses YAML format, as shown in the example below:

```yaml
datasource:
  datasources:
    # First data source example
    your_db1_name:
      url: jdbc:mysql://localhost:3306/db1
      username: root
      password: password
      default: true  # Mark as default data source, if not marked, the first one will be used by default

    # Second data source example
    your_db2_name:
      url: jdbc:mysql://localhost:3306/db2
      username: root
      password: password
```

-----

## Usage

### Build the Project

Execute the following command in the project root directory to compile and package the project:

```bash
./mvnw clean package
```

### Run the Project (Recommended Method)

It is recommended to use Maven Wrapper to start the application to ensure all functionality (including extensions) works properly:

```bash
./mvnw spring-boot:run
```

### Run the Project with a Custom Data Source Configuration

If you need to use a custom data source configuration file, you can add parameters to the run command:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--datasource.config=/path/to/your-datasource.yml
```

The corresponding MCP JSON configuration is as follows:

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

### Run Using JAR Package (This method does not support extensions)

```bash
java -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar
```

The corresponding MCP JSON configuration is as follows:

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
