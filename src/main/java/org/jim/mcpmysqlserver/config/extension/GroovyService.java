package org.jim.mcpmysqlserver.config.extension;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
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


    public String executeGroovyScript(String extensionName, String input) {
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
        String scriptPath = groovyBasePath + "/script/"+ extension.getMainFileName();
        String jarPathDir = groovyBasePath + "/dependency/";

        log.info("the script path is: {} and the jar path is: {}", scriptPath, jarPathDir);

        URLClassLoader customClassLoader = null;
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            List<URL> jarUrls = new ArrayList<>();
            URL jarDirUrl = GroovyService.class.getClassLoader().getResource(jarPathDir);
            log.info("jarDirUrl: {}", jarDirUrl);

            if (jarDirUrl != null) {
                if ("file".equals(jarDirUrl.getProtocol())) {
                    // Handle file system resources (development mode)
                    File dependencyDir = new File(jarDirUrl.toURI());
                    if (dependencyDir.exists() && dependencyDir.isDirectory()) {
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

                            log.info("找到{}个依赖JAR包，扩展名: {}, 文件列表: {}", jarUrls.size(), extensionName,
                                    jarUrls.stream().map(URL::toString).toList());
                        }
                    } else {
                        log.warn("依赖目录不存在或不是一个目录: {}", dependencyDir);
                    }
                } else if (jarDirUrl.getProtocol().startsWith("jar")) {
                    // Handle JAR resources (production mode)
                    try {
                        // Get the classloader to look for jars in the classpath
                        ClassLoader classLoader = GroovyService.class.getClassLoader();

                        // 从所有扩展中查找指定的扩展
                        log.info("从打包的JAR文件中加载依赖: 扩展名={}", extensionName);

                        // 尝试在目录下搜索所有.jar文件
                        try {
                            // 使用默认名称策略 - 扩展名下的所有jar文件
                            String resourcePath = "groovy/" + extensionName + "/dependency/";
                            log.info("搜索JAR包路径: {}", resourcePath);

                            // 如果扩展配置了特定的依赖项，优先使用配置的依赖项
                            if (StringUtils.isNotBlank(extension.getScriptPathDependency())) {
                                resourcePath = extension.getScriptPathDependency();
                                log.info("使用扩展配置的依赖路径: {}", resourcePath);
                            }

                            // 特定处理zstdDecode扩展的依赖项
                            if (extensionName.equals("zstdDecode")) {
                                // 尝试获取特定的JAR文件 - 先从配置中获取，如果配置中没有则使用默认值
                                String jarFileName = "zstd-jni-1.5.5-10.jar"; // 默认值
                                log.info("加载zstdDecode扩展的依赖JAR: {}", jarFileName);

                                String jarResourcePath = resourcePath + jarFileName;
                                URL jarUrl = classLoader.getResource(jarResourcePath);
                                if (jarUrl != null) {
                                    jarUrls.add(jarUrl);
                                    log.info("成功找到JAR: {}", jarUrl);
                                } else {
                                    log.warn("无法找到JAR文件: {}, 扩展名: {}",
                                            jarResourcePath, extensionName);
                                }
                            } else {
                                log.info("尝试为扩展 {} 自动发现依赖JAR", extensionName);
                                // 这里可以添加更通用的JAR发现逻辑
                            }
                        } catch (Exception e) {
                            log.error("搜索JAR依赖时出错: {}", e.getMessage(), e);
                        }
                    } catch (Exception e) {
                        log.error("访问JAR资源时出错: {}", e.getMessage(), e);
                    }
                } else {
                    log.warn("不支持的协议 {} 依赖目录: {}",
                            jarDirUrl.getProtocol(), jarPathDir);
                }
            } else {
                log.warn("在类路径中找不到JAR目录: {} 扩展名: {}", jarPathDir, extensionName);
            }

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
            try (java.io.InputStream inputStream = scriptUrl.openStream()) {
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
