package org.jim.mcpmysqlserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * [INPUT]: 依赖同包 DataSourceRegistry 的数据源定位与元信息能力。
 *          重构后不再依赖 ApplicationContext 与 config/DataSourceConfig，service 包对 config 包零依赖。
 * [OUTPUT]: 对外提供 DataSourceService（getDataSource/getDataSourceNames/getDefaultDataSourceName/getDataSourceDetails）。
 * [POS]: service 层的数据源定位门面，负责别名解析（null/""/primary → 默认数据源）这一层语义，
 *        真正的池持有与生命周期归 DataSourceRegistry。mcp/MysqlOptionService 与 controller 只经由它取数据源。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 *
 * @author yangxin
 */
@Service
@Slf4j
public class DataSourceService {

    private final DataSourceRegistry registry;

    public DataSourceService(DataSourceRegistry registry) {
        this.registry = registry;
        log.info("DataSourceService 初始化完成，数据源定位委托给 DataSourceRegistry");
    }

    /**
     * 获取数据源。空名称与历史别名 "primary" 均指向当前默认数据源。
     *
     * @param name 数据源名称
     * @return 数据源，未注册时返回 null
     */
    public DataSource getDataSource(String name) {
        if (name == null || name.isEmpty() || DataSourceRegistry.PRIMARY_ALIAS.equals(name)) {
            log.debug("数据源名称为空或为别名 [{}]，返回默认数据源", name);
            return registry.getDefaultDataSource();
        }

        DataSource dataSource = registry.getDataSource(name);
        if (dataSource == null) {
            log.warn("数据源 [{}] 未注册，返回空", name);
        }
        return dataSource;
    }

    /**
     * @return 所有可用的数据源名称
     */
    public List<String> getDataSourceNames() {
        return registry.names();
    }

    /**
     * @return 默认数据源名称，注册表为空时返回 null
     */
    public String getDefaultDataSourceName() {
        return registry.getDefaultName();
    }

    /**
     * @return 所有数据源的详细信息（名称、数据库类型、驱动、是否默认），不含任何凭据
     */
    public List<Map<String, Object>> getDataSourceDetails() {
        return registry.details();
    }
}
