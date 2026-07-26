package org.jim.mcpmysqlserver.mcp;

import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.config.DataSourceReloadService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * [INPUT]: 依赖 config/DataSourceReloadService 的 reload 能力。
 * [OUTPUT]: 对外提供 MCP tool reloadDataSources()。
 * [POS]: mcp 包的数据源管理工具集，与 MysqlOptionService（SQL 执行工具集）职责分离——
 *        一个改数据源拓扑，一个查数据。刻意不提供 addDataSource/removeDataSource 之类的凭据入参工具：
 *        数据源的增删由用户或 Agent 编辑 datasource.yml 完成，本工具只负责让改动免重启生效，
 *        从而避免明文密码进入对话记录，也避免配置出现内存与文件两份真相。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 *
 * @author yangxin
 */
@Service
@Slf4j
public class DataSourceAdminTools {

    private final DataSourceReloadService dataSourceReloadService;

    public DataSourceAdminTools(DataSourceReloadService dataSourceReloadService) {
        this.dataSourceReloadService = dataSourceReloadService;
    }

    /**
     * 重新加载数据源配置文件，使新增/删除/修改的数据库连接立即生效。
     *
     * @return 逐库分类的重载报告
     */
    @Tool(description = """
            Purpose: Re-read the datasource config file from disk and apply changes to the running server, with no restart.

            Use This Tool:
            - Right after the datasource config file (datasource.yml) was edited to add, remove, or modify a database connection
            - When listDataSources() does not show a datasource the user says they just configured

            Do Not Use This Tool:
            - To execute SQL; this tool only reloads connection definitions
            - To discover datasources without any config change; call listDataSources() instead

            Input Rules:
            - Takes no arguments. The file path is fixed at server startup via --datasource.config
            - This tool cannot create a connection from parameters; edit the config file first, then call this

            Return Rules:
            - added / updated / removed / unchanged: datasource names by change category
            - failed: map of datasource name to error message; a database that is unreachable fails alone and does not block the others
            - success=false with an 'error' field means the file could not be parsed and nothing was changed at all
            - Datasources whose config did not change keep their existing connection pool untouched
            - Removed datasources keep serving in-flight queries for a grace period before their pool closes
            """)
    public Map<String, Object> reloadDataSources() {
        log.info("MCP tool 触发数据源配置重载");
        return dataSourceReloadService.reload();
    }
}
