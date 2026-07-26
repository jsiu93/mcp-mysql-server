package org.jim.mcpmysqlserver.controller;

import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.config.DataSourceReloadService;
import org.jim.mcpmysqlserver.mcp.MysqlOptionService;
import org.jim.mcpmysqlserver.service.DataSourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [INPUT]: 依赖 service/DataSourceService 的数据源元信息、mcp/MysqlOptionService 的 SQL 执行、
 *          config/DataSourceReloadService 的配置重载能力。
 * [OUTPUT]: 对外提供 /api/datasource 下的 list/test/execute/reload/executeGroovyScript/executeSqlOnDefault HTTP 接口。
 * [POS]: controller 包的唯一入口，是 MCP 协议之外的旁路操作面，供 curl 与脚本使用。
 *        与 mcp/DataSourceAdminTools 暴露同一套重载能力，两者共用 DataSourceReloadService，不得各自实现。
 *        注意：本控制器无鉴权，服务默认绑定 0.0.0.0，部署时应以 server.address 收敛到回环地址。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 *
 * <p>数据源控制器，提供测试接口。</p>
 *
 * @author yangxin
 */
@RestController
@RequestMapping("/api/datasource")
@Slf4j
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final MysqlOptionService mysqlOptionService;
    private final DataSourceReloadService dataSourceReloadService;

    @Autowired
    public DataSourceController(DataSourceService dataSourceService,
                                MysqlOptionService mysqlOptionService,
                                DataSourceReloadService dataSourceReloadService) {
        this.dataSourceService = dataSourceService;
        this.mysqlOptionService = mysqlOptionService;
        this.dataSourceReloadService = dataSourceReloadService;
    }

    /**
     * 重新加载数据源配置文件，使新增/删除/修改的数据库连接免重启生效
     * @return 逐库分类的重载报告
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload() {
        log.info("Reloading datasource configuration from disk");
        return ResponseEntity.ok(dataSourceReloadService.reload());
    }


    @RequestMapping("/executeGroovyScript")
    public ResponseEntity<Object> executeGroovyScript(@RequestParam String extensionName, @RequestParam String input) {
        Object result = mysqlOptionService.executeGroovyScript(extensionName, input);
        return ResponseEntity.ok(result);
    }


    @RequestMapping("/executeSqlOnDefault")
    public ResponseEntity<Object> executeSqlOnDefault(@RequestParam String sql) {
        Object result = mysqlOptionService.executeSqlOnDefault(sql);
        return ResponseEntity.ok(result);
    }


    /**
     * 获取所有数据源名称
     * @return 数据源名称列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listDataSources() {
        log.info("Listing all datasources");
        List<String> dataSourceNames = dataSourceService.getDataSourceNames();
        String defaultDataSourceName = dataSourceService.getDefaultDataSourceName();

        Map<String, Object> result = new HashMap<>();
        result.put("datasources", dataSourceNames);
        result.put("default", defaultDataSourceName);

        return ResponseEntity.ok(result);
    }

    /**
     * 测试数据源连接
     * @param name 数据源名称
     * @return 测试结果
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testDataSource(@RequestParam(required = false) String name) {
        String dsName = name;
        if (dsName == null || dsName.isEmpty()) {
            dsName = dataSourceService.getDefaultDataSourceName();
        }

        log.info("Testing datasource connection: {}", dsName);

        Map<String, Object> result = new HashMap<>();
        result.put("datasource", dsName);

        try {
            Map<String, Object> testResult;
            if ("primary".equals(dsName) || dataSourceService.getDefaultDataSourceName().equals(dsName)) {
                testResult = mysqlOptionService.executeSql("SELECT 1 AS test");
            } else {
                testResult = mysqlOptionService.executeSqlWithDataSource(dsName, "SELECT 1 AS test");
            }

            result.put("status", "success");
            result.put("result", testResult);
            log.info("Datasource [{}] test successful", dsName);
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            log.error("Datasource [{}] test failed: {}", dsName, e.getMessage(), e);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 执行SQL
     * @param sql SQL语句
     * @param datasource 数据源名称（可选）
     * @return 执行结果
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeSql(
            @RequestParam String sql,
            @RequestParam(required = false) String datasource) {

        log.info("Executing SQL on datasource [{}]: {}", datasource, sql);

        Map<String, Object> result = new HashMap<>();
        result.put("sql", sql);
        result.put("datasource", datasource != null ? datasource : dataSourceService.getDefaultDataSourceName());

        try {
            Map<String, Object> sqlResult;
            String defaultName = dataSourceService.getDefaultDataSourceName();
            if (datasource == null || datasource.isEmpty() ||
                    "primary".equals(datasource) ||
                    defaultName.equals(datasource)) {
                sqlResult = mysqlOptionService.executeSqlWithDataSource(defaultName, sql);
            } else {
                sqlResult = mysqlOptionService.executeSqlWithDataSource(datasource, sql);
            }

            result.put("status", "success");
            result.put("result", sqlResult);
            log.info("SQL execution successful");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            log.error("SQL execution failed: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok(result);
    }
}
