package org.jim.mcpmysqlserver.config;

import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.service.DataSourceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;

/**
 * [INPUT]: 依赖同包 DataSourceConfig 的启动期数据源配置，依赖 service/DataSourceRegistry 承载连接池，
 *          依赖 Spring 的 DelegatingDataSource 做默认数据源代理。
 * [OUTPUT]: 对外提供 dataSourceRegistry Bean 与 @Primary 的 primaryDataSource Bean。
 * [POS]: config 包的数据源装配入口。重构后它只负责"接线"，连接池的创建与生命周期已全部下沉到 DataSourceRegistry。
 *        原先的 secondaryDataSources Map Bean 已删除——注册表统一持有含默认库在内的全部数据源，不再区分主从两套容器。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 *
 * @author yangxin
 */
@Configuration
@Slf4j
public class DynamicDataSourceConfig {

    /**
     * 建立数据源注册表并完成启动期引导。
     *
     * <p>destroyMethod 指向 shutdown，容器关闭时立即回收全部连接池。</p>
     *
     * @param dataSourceConfig 启动期配置
     * @return 数据源注册表
     */
    @Bean(destroyMethod = "shutdown")
    public DataSourceRegistry dataSourceRegistry(DataSourceConfig dataSourceConfig) {
        DataSourceRegistry registry = new DataSourceRegistry(dataSourceConfig.getReloadGraceSeconds());
        registry.bootstrap(dataSourceConfig.getDatasources(), dataSourceConfig.getDefaultDataSourceName());
        return registry;
    }

    /**
     * 默认数据源。返回代理而非具体连接池，使默认数据源可以在运行期重载时切换。
     *
     * @param registry 数据源注册表
     * @return @Primary 数据源代理
     */
    @Bean
    @Primary
    public DataSource primaryDataSource(DataSourceRegistry registry) {
        log.info("注册 @Primary 数据源代理，实际目标由 DataSourceRegistry 在每次调用时解析");
        return new RegistryBackedDataSource(registry);
    }

    /**
     * 默认数据源代理：Bean 身份恒定，目标每次调用重新解析。
     *
     * <p>这是"默认数据源可热切换"的支点。若直接把某个 HikariDataSource 注册为 @Primary，
     * 它就成了不可替换的单例，重载时既换不掉也关不得。</p>
     */
    static class RegistryBackedDataSource extends DelegatingDataSource {

        private final DataSourceRegistry registry;

        RegistryBackedDataSource(DataSourceRegistry registry) {
            this.registry = registry;
        }

        @Override
        public DataSource getTargetDataSource() {
            DataSource target = registry.getDefaultDataSource();
            if (target == null) {
                throw new IllegalStateException("当前没有可用的默认数据源");
            }
            return target;
        }

        @Override
        public void afterPropertiesSet() {
            // 目标按调用解析，刻意不在初始化期强制校验：
            // 启动时数据库不可达不应导致容器启动失败，这与重构前的惰性行为一致
        }
    }
}
