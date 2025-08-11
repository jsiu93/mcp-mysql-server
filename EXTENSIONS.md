# 扩展功能详细文档

MCP MySQL Server 支持通过 Groovy 脚本来扩展其功能。这些扩展可以在运行时通过特定的工具调用来执行。

## 配置文件

扩展功能通过 `src/main/resources/extension.yml` 文件进行配置。此文件定义了一个扩展列表，每个扩展可以包含以下参数：

| 参数             | 类型        | 是否必需/可选 | 描述                                                                                                                  | 示例                                                                 |
|:---------------|:----------|:--------|:--------------------------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------|
| `name`         | `String`  | 必需      | 扩展的唯一名称，用于调用该扩展。                                                                                                    | `zstdDecode`                                                       |
| `description`  | `String`  | 可选      | 扩展功能的简要描述。                                                                                                          | `"解码业务快照数据"`                                                       |
| `enabled`      | `Boolean` | 可选      | 是否启用该扩展。默认为 `true`。设置为 `false` 则禁用。                                                                                 | `false`                                                            |
| `prompt`       | `String`  | 可选      | 当使用此扩展时，提供给 AI 模型的建议提示或说明。                                                                                          | `"decode the snapshot_data..."`                                    |
| `script`       | `String`  | 可选      | 内联的 Groovy 脚本代码。如果提供了此参数，则会忽略 `mainFileName` 及对应的脚本文件。**请注意，对于复杂的脚本或需要外部依赖的扩展，推荐使用 `mainFileName` 指定脚本文件。**         | `` `def greet(name) { return "Hello, $name!"; }; greet('Java')` `` |
| `mainFileName` | `String`  | 条件性必需   | Groovy 脚本文件的名称（例如 `main.groovy`）。如果未提供 `script` 参数，则此参数为必需。脚本文件必须位于 `src/main/resources/groovy/<name>/script/` 目录下。 | `main.groovy`                                                      |

## 脚本和依赖项管理

### 脚本文件

如果不使用内联 `script`，则每个扩展的 Groovy 脚本文件应放置在 `src/main/resources/groovy/<extension_name>/script/` 目录下。脚本文件的具体名称通过 `mainFileName` 参数指定。

例如，对于名为 `zstdDecode` 的扩展，其主脚本文件（如 `main.groovy`）应位于：
```
/src/main/resources/groovy/zstdDecode/script/main.groovy
```

### 依赖 JAR 包

如果扩展需要外部 Java 库（JARs），请将这些 JAR 文件放置在 `src/main/resources/groovy/<extension_name>/dependency/` 目录下。服务启动时会自动加载这些依赖。

例如，`zstdDecode` 扩展的依赖 `zstd-jni-1.5.5-10.jar` 应位于：
```
/src/main/resources/groovy/zstdDecode/dependency/zstd-jni-1.5.5-10.jar
```

## 目录结构示例

```
src/main/resources/
├── groovy/
│   ├── extension1/
│   │   ├── script/
│   │   │   └── main.groovy
│   │   └── dependency/
│   │       ├── lib1.jar
│   │       └── lib2.jar
│   └── extension2/
│       ├── script/
│       │   └── main.groovy
│       └── dependency/
│           └── lib3.jar
└── extension.yml
```

## 配置示例

```yaml
extensions:
  - name: zstdDecode
    description: "解码ZSTD压缩的业务快照数据"
    enabled: true
    prompt: "使用此扩展解码压缩的快照数据"
    mainFileName: main.groovy

  - name: simpleGreeting
    description: "简单问候功能"
    enabled: true
    script: |
      def greet(name) {
          return "Hello, $name!"
      }
      greet(input)
```

## 扩展开发指南

### 脚本编写规范

1. **入口函数**：扩展脚本应该有一个主要的处理函数
2. **输入参数**：通过 `input` 变量获取传入的参数
3. **返回值**：脚本应该返回处理结果
4. **异常处理**：建议在脚本中包含适当的异常处理

### 示例脚本

```groovy
// main.groovy
import java.util.Base64

def processData(inputData) {
    try {
        // 处理逻辑
        if (!inputData) {
            return "Error: 输入数据为空"
        }
        
        // 示例：Base64解码
        byte[] decoded = Base64.getDecoder().decode(inputData)
        return new String(decoded, "UTF-8")
        
    } catch (Exception e) {
        return "Error: ${e.message}"
    }
}

// 处理输入数据
processData(input)
```

## 重要提示

### 运行带有扩展的应用

现在两种启动方式都支持扩展功能：

1. **Maven Wrapper 方式**：使用 `spring-boot:run` 启动应用
2. **JAR 包方式**：使用 `java -jar` 启动，需要添加 `-Dloader.path` 参数指定扩展依赖目录

### JAR 包启动配置

使用扩展功能时的 JAR 包启动命令：

```bash
java -Dloader.path=/path/to/your/project/src/main/resources/groovy -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar
```

对应的 MCP JSON 配置：

```json
{
  "mcpServers": {
    "mcp-mysql-server": {
      "command": "java",
      "args": [
        "-Dloader.path=/your-path/src/main/resources/groovy",
        "-jar",
        "/your-path/mcp-mysql-server-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

## 安全性考虑

1. **代码审查**：在部署扩展之前，请仔细审查Groovy脚本代码
2. **依赖管理**：确保引入的JAR包来源可信
3. **权限控制**：扩展脚本将在应用进程中执行，请谨慎授予权限
4. **输入验证**：在扩展脚本中对输入数据进行充分验证

## 故障排除

### 常见问题

1. **扩展无法加载**
   - 检查 `extension.yml` 配置格式是否正确
   - 验证脚本文件路径是否存在
   - 确认文件权限

2. **依赖包找不到**
   - 检查JAR包是否放置在正确的 `dependency/` 目录下
   - 验证启动时是否正确指定了 `-Dloader.path` 参数

3. **脚本执行错误**
   - 查看应用日志获取详细错误信息
   - 检查Groovy语法是否正确
   - 验证输入数据格式

### 调试建议

1. 启用详细日志记录
2. 在脚本中添加调试输出
3. 使用简单的测试脚本验证扩展机制
