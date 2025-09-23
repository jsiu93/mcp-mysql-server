package org.jim.mcpmysqlserver.config.extension;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 扩展配置类，从 extension.yml 或用户指定的配置文件读取扩展配置
 * 用户可以通过命令行参数 --extension.config=<配置文件路径> 指定配置文件
 * @author James Smith
 */
@Component
@ConfigurationProperties
@Data
@Accessors(chain = true)
@Slf4j
@DependsOn("extensionConfigLoader")
public class ExtensionConfig {

    private List<Extension> extensions;

    /**
     * 构造函数，打印日志信息
     */
    public ExtensionConfig() {
        log.info("ExtensionConfig 初始化完成");
    }

}
