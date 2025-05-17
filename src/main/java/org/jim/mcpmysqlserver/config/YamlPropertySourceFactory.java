package org.jim.mcpmysqlserver.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * YAML 配置文件加载工厂
 * @author yangxin
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource encodedResource) throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(encodedResource.getResource());

        Properties properties = factory.getObject();
        if (properties == null) {
            throw new IllegalStateException("Failed to load yaml properties from " + encodedResource.getResource().getFilename());
        }

        String sourceName = name != null ? name : encodedResource.getResource().getFilename();
        return new PropertiesPropertySource(sourceName, properties);
    }
}
