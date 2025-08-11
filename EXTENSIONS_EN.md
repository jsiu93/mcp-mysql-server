# Extensions Documentation

MCP MySQL Server supports extending its functionality through Groovy scripts. These extensions can be executed at runtime via specific tool calls.

## Configuration File

Extensions are configured through the `src/main/resources/extension.yml` file. This file defines a list of extensions, each of which can include the following parameters:

| Parameter      | Type       | Required/Optional | Description                                                                                                                     | Example                                                             |
|:---------------|:-----------|:------------------|:--------------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------|
| `name`         | `String`   | Required          | Unique name of the extension, used to call the extension.                                                                       | `zstdDecode`                                                        |
| `description`  | `String`   | Optional          | Brief description of the extension functionality.                                                                               | `"Decode business snapshot data"`                                   |
| `enabled`      | `Boolean`  | Optional          | Whether to enable the extension. Default is `true`. Set to `false` to disable.                                                  | `false`                                                             |
| `prompt`       | `String`   | Optional          | Suggested prompt or instruction provided to the AI model when using this extension.                                             | `"decode the snapshot_data..."`                                     |
| `script`       | `String`   | Optional          | Inline Groovy script code. If this parameter is provided, `mainFileName` and its corresponding script file will be ignored. **Note: For complex scripts or extensions requiring external dependencies, it is recommended to use `mainFileName` to specify the script file.** | `` `def greet(name) { return "Hello, $name!"; }; greet('Java')` `` |
| `mainFileName` | `String`   | Conditionally Required | Name of the Groovy script file (e.g., `main.groovy`). This parameter is required if the `script` parameter is not provided. The script file must be located in the `src/main/resources/groovy/<name>/script/` directory. | `main.groovy`                                                       |

## Script and Dependency Management

### Script Files

If not using an inline `script`, each extension's Groovy script file should be placed in the `src/main/resources/groovy/<extension_name>/script/` directory. The specific name of the script file is specified through the `mainFileName` parameter.

For example, for an extension named `zstdDecode`, its main script file (e.g., `main.groovy`) should be located at:
```
/src/main/resources/groovy/zstdDecode/script/main.groovy
```

### Dependency JAR Packages

If the extension requires external Java libraries (JARs), please place these JAR files in the `src/main/resources/groovy/<extension_name>/dependency/` directory. These dependencies will be automatically loaded when the service starts.

For example, the dependency `zstd-jni-1.5.5-10.jar` for the `zstdDecode` extension should be located at:
```
/src/main/resources/groovy/zstdDecode/dependency/zstd-jni-1.5.5-10.jar
```

## Directory Structure Example

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

## Configuration Example

```yaml
extensions:
  - name: zstdDecode
    description: "Decode ZSTD compressed business snapshot data"
    enabled: true
    prompt: "Use this extension to decode compressed snapshot data"
    mainFileName: main.groovy

  - name: simpleGreeting
    description: "Simple greeting functionality"
    enabled: true
    script: |
      def greet(name) {
          return "Hello, $name!"
      }
      greet(input)
```

## Extension Development Guide

### Script Writing Standards

1. **Entry Function**: Extension scripts should have a main processing function
2. **Input Parameters**: Get passed parameters through the `input` variable
3. **Return Value**: Scripts should return processing results
4. **Exception Handling**: It's recommended to include proper exception handling in scripts

### Example Script

```groovy
// main.groovy
import java.util.Base64

def processData(inputData) {
    try {
        // Processing logic
        if (!inputData) {
            return "Error: Input data is empty"
        }
        
        // Example: Base64 decoding
        byte[] decoded = Base64.getDecoder().decode(inputData)
        return new String(decoded, "UTF-8")
        
    } catch (Exception e) {
        return "Error: ${e.message}"
    }
}

// Process input data
processData(input)
```

## Important Notes

### Running Applications with Extensions

Both startup methods now support extension functionality:

1. **Maven Wrapper Method**: Start the application using `spring-boot:run`
2. **JAR Package Method**: Start using `java -jar`, requires adding the `-Dloader.path` parameter to specify the extension dependency directory

### JAR Package Startup Configuration

JAR package startup command when using extension features:

```bash
java -Dloader.path=/path/to/your/project/src/main/resources/groovy -jar target/mcp-mysql-server-0.0.1-SNAPSHOT.jar
```

Corresponding MCP JSON configuration:

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

## Security Considerations

1. **Code Review**: Please carefully review Groovy script code before deploying extensions
2. **Dependency Management**: Ensure that introduced JAR packages come from trusted sources
3. **Permission Control**: Extension scripts will execute within the application process, please grant permissions carefully
4. **Input Validation**: Perform adequate validation of input data in extension scripts

## Troubleshooting

### Common Issues

1. **Extension Cannot Load**
   - Check if the `extension.yml` configuration format is correct
   - Verify that script file paths exist
   - Confirm file permissions

2. **Dependencies Not Found**
   - Check if JAR packages are placed in the correct `dependency/` directory
   - Verify that the `-Dloader.path` parameter was correctly specified at startup

3. **Script Execution Errors**
   - Check application logs for detailed error information
   - Verify Groovy syntax is correct
   - Validate input data format

### Debugging Recommendations

1. Enable detailed logging
2. Add debug output in scripts
3. Use simple test scripts to verify extension mechanisms
