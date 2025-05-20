package org.jim.mcpmysqlserver.config.extension;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author James Smith
 */
@Component
@ConfigurationProperties
@Data
@Accessors(chain = true)
public class ExtensionConfig {

    private List<Extension> extensions;

}
