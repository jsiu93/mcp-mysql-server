package org.jim.mcpmysqlserver.config.extension;

import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
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
    public Object executeGroovyScript(String extensionName, String input) {
        //input = "KLUv/WDTDSUkAFZxskbgsDoHSOiEGGOMMYa1QHgJnMjkdu4zF/OgORFkHke5mwuNMSNJzopsWp8nbBVEv3VJ5Cv+2/0BA4tuIoIRYEwaMcYYY0wIkgCqAKMAGZfyxu+b2s9HrMG3Tm18H4V3R4kmcba4r5gvL8cnuZsvKDHpR3PbDbQhx3m4kPN8wE7MJTpl6k0gb+wvEXzWdvtyvCIhyzGJSVxnu3H/Kj2myLfbm5uPIXdE61MBAhskjZPdbMbXwehH04MzvqjJb3DzJTkRcAgn40tqkcPzk3k/iW6Q2hrFITXKhuynVDyAlRAIOEoifvUoYjcQE+tq9VhLegJWC/FKYy70FC0YNHVmVjtZxVgBwXHehNV6unSWmhuZa13hSm1sajRnZ2HoIEasMxW1UIRMFfHSY2LqarXapFBZZACZWElVVivt1Wmv9PMi1lsnvabVUaB0mglV5qx5NhM7O1MY2jecqYuZbgG2sly6TIeVvNlunbALQRFnXd73EP9REL5ESwqDqrMWNy0vblDli14XsuVzMyqvVKbVUSx0FWjqAe0prPSbzdRkaPdiq7SETAXQyV5SDq0uq7nBcHaYEC290zCTstBOAq2hMKqHs6/EqDFXis46LaWHnAdDaxVgK4tKd2mlX8iWqpuRL4ko2kxG+0XKfDfnW5cZHqIMk+j0wYA0IIHI4/QBgjkWsx6p9psHykdtU5cgVsOWpIn6B785LsRxfF/+EXZCn4dPYh6Q+j4wNMebwBQm1sRRKnGMSioRbLeQ/eA8IMjjAUNQZ39En0LMrTHJdfACytLkl3c+8Ps+7+s+7vNQtNZiwbtj1CjN9e4YcWEH6j6dp/M+wNYLKSvIpMA0sqj8e5ujUW/Os6nJkf+SDwTkfTxPxwO9z/vgkK01QE/qERvImPy8TnqrhepBsekvKx0O/DzOo9GQslLWDAof0vPsLFRUmrO7bDoYg9IDgLuoMbFDSJGIiIwkqaQDUITIGNGcB9JRDMYooDLEEEyIUAQSK4FIYCNEYWJspgVYDKkDSFl5LV7sQA/kGEkzBQzXHfH2ZRdfULdxRjtVoKKFxSGXM6EOCTDIUk4A+GJAS9JBdJnAUUouDBY7QoIKZ2H1zKh3GJCFALjEhd8k5UY7w/TNytCKrMitAECcdpJe7hRd3sFQyhbdDdQXcdAjY3welIUeCFQmstBGVdeILcODMCw2EXhP8aRgpcJ84S9XovGrgrVfbB5nrbuQvwNY+RRn9fOBznHjLWh+rmVNbytMipPM/n7mqM9rJ99FjAl6B8vc9w6eYIFjENGjO7pgCUROIY1oGc++reF0Kb2gq2Qq/dYlGHJMZWh3Qt1LFg3R0PsuVQQ68u8ToX3p6SmagJdddXxe31RJVpF3w681X3Zlx7k2k0EacWdu2kCrTpjQYjmwxLFJoxSo8Dp9FwgCnSgRss8YDEqHrJWnlcp5wzyj/t94bGRXHXcQrWXmnMBAiJSUbc1tVtU9BnB80cyWBTU2VtRK2HKzxYF1AVIPAgkCKxbVE8yEjPZPXQa6sLe0CDiQJalTGqo=";


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

        log.info("Loading Groovy script from path: {} for extension: {}", scriptPath, extensionName);

        try {
            // 检查依赖是否已通过 PropertiesLauncher 加载到 classpath
            boolean dependenciesAvailable = checkDependenciesInClasspath(extension);
            if (dependenciesAvailable) {
                log.info("Dependencies already loaded in classpath via PropertiesLauncher for extension: {}", extensionName);
                // 使用默认的 ScriptEngineManager，依赖已通过 PropertiesLauncher 加载
                ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
                log.info("Using default ScriptEngineManager with classpath dependencies for extension: {}", extensionName);
                return executeGroovyScriptWithEngine(extension, extensionName, scriptEngineManager, input);
            } else {
                log.info("Dependencies not found in classpath for extension: {}, attempting dynamic loading", extensionName);
                return loadWithDynamicClassLoader(extension, extensionName, jarPathDir, input);
            }

        } catch (Exception e) {
            log.error("An unexpected error occurred while executing Groovy script '{}': {}", extension.getScriptPath(), e.getMessage(), e);
            throw new RuntimeException("An unexpected error occurred: " + e.getMessage(), e);
        }
    }

    public List<Extension> getAllExtensions() {
        List<Extension> extensions = extensionConfig.getExtensions();
        log.info("Loaded extensions count: {}", extensions != null ? extensions.size() : 0);
        if (extensions != null) {
            extensions.forEach(ext -> log.info("Extension loaded: name={}, enabled={}, description={}",
                ext.getName(), ext.getEnabled(), ext.getDescription()));
        }
        return extensions;
    }

    /**
     * 检查扩展的依赖是否已在 classpath 中可用
     * 通过尝试加载已知的依赖类来验证
     */
    private boolean checkDependenciesInClasspath(Extension extension) {
        // 对于 zstdDecode 扩展，检查 zstd-jni 库是否可用
        if ("zstdDecode".equals(extension.getName())) {
            try {
                Class.forName("com.github.luben.zstd.Zstd");
                log.info("Zstd dependency found in classpath for extension: {}", extension.getName());
                return true;
            } catch (ClassNotFoundException e) {
                log.error("Zstd dependency not found in classpath for extension: {}", extension.getName());
                return false;
            }
        }

        // 对于 SM4Decrypt 扩展，检查 BouncyCastle 库是否可用（hutool SM4 的关键依赖）
        if ("SM4Decrypt".equals(extension.getName())) {
            try {
                Class.forName("org.bouncycastle.crypto.Digest");
                log.info("BouncyCastle dependency found in classpath for extension: {}", extension.getName());
                return true;
            } catch (ClassNotFoundException e) {
                log.error("BouncyCastle dependency not found in classpath for extension: {}, attempting dynamic loading", extension.getName());
                return false;
            }
        }

        // 对于其他扩展，可以添加相应的检查逻辑
        // 或者通过扫描 dependency 目录来动态检查
        return true; // 默认假设依赖已加载
    }

    /**
     * 使用动态类加载器加载依赖（后备方案）
     */
    private Object loadWithDynamicClassLoader(Extension extension, String extensionName, String jarPathDir, String input) {
        URLClassLoader customClassLoader = null;
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            URL resource = GroovyService.class.getClassLoader().getResource(jarPathDir);
            List<URL> jarUrls = new ArrayList<>();
            log.info("Attempting dynamic loading for jarDirUrl: {}", resource);

            if (resource == null) {
                log.warn("Dependency directory not found: {}", jarPathDir);
                return null;
            }

            String protocol = resource.getProtocol();

            if ("file".equals(protocol)) {
                log.info("Loading dependencies from file system for extension: {}", extensionName);
                File dependencyDir = new File(resource.toURI());
                if (!(dependencyDir.exists() && dependencyDir.isDirectory())) {
                    log.warn("Dependency directory does not exist: {}", dependencyDir.getAbsolutePath());
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

                    log.info("Found {} dependency JARs for extension: {}, files: {}",
                            jarUrls.size(), extensionName,
                            jarUrls.stream().map(URL::toString).toList());
                }
            }

            ScriptEngineManager scriptEngineManager;
            if (!CollectionUtils.isEmpty(jarUrls)) {
                customClassLoader = new URLClassLoader(jarUrls.toArray(new URL[0]), originalContextClassLoader);
                Thread.currentThread().setContextClassLoader(customClassLoader);
                log.info("Custom classloader created with {} JARs for extension: {}", jarUrls.size(), extensionName);

                scriptEngineManager = new ScriptEngineManager(customClassLoader);
                log.info("Created ScriptEngineManager with custom classloader for extension: {}", extensionName);
            } else {
                log.info("No dependency JARs found for extension: {}, using default ScriptEngineManager", extensionName);
                scriptEngineManager = new ScriptEngineManager();
            }

            // 执行脚本逻辑
            return executeGroovyScriptWithEngine(extension, extensionName, scriptEngineManager, input);

        } catch (Exception e) {
            log.error("Error in dynamic class loading for extension: {}", extensionName, e);
            return null;
        } finally {
            // 恢复原始的上下文类加载器
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            if (customClassLoader != null) {
                try {
                    customClassLoader.close();
                } catch (IOException e) {
                    log.warn("Failed to close custom class loader for extension: {}", extensionName, e);
                }
            }
        }
    }

    /**
     * 执行 Groovy 脚本的核心逻辑
     */
    private Object executeGroovyScriptWithEngine(Extension extension, String extensionName, ScriptEngineManager scriptEngineManager, String input) {
        try {
            String groovyBasePath = "groovy/" + extension.getName();
            String scriptPath = groovyBasePath + "/script/" + extension.getMainFileName();

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
            return result;

        } catch (Exception e) {
            log.error("Error executing Groovy script for extension: {}", extensionName, e);
            throw new RuntimeException("Error executing Groovy script: " + e.getMessage(), e);
        }
    }
}
