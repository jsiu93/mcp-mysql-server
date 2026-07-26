package org.jim.mcpmysqlserver.config;

import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.service.DataSourceRegistry;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * [INPUT]: 依赖同包 DataSourceConfigLoader 的 readDataSourcesFromDisk 读盘能力，
 *          依赖 service/DataSourceRegistry 的 register/unregister/setDefaultName 写能力。
 * [OUTPUT]: 对外提供 DataSourceReloadService#reload()，返回逐库分类的重载报告。
 * [POS]: config 包的运行期配置生效者，DataSourceConfigLoader 的运行期对偶。
 *        职责只有差异计算与施加顺序，连接池的建立、校验、关闭一律委托注册表。
 *        被 controller/DataSourceController 与 mcp/DataSourceAdminTools 两个入口共用，是"免重启"能力的唯一实现点。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 *
 * @author yangxin
 */
@Service
@Slf4j
public class DataSourceReloadService {

    private final DataSourceRegistry registry;
    private final DataSourceConfigLoader configLoader;

    public DataSourceReloadService(DataSourceRegistry registry, DataSourceConfigLoader configLoader) {
        this.registry = registry;
        this.configLoader = configLoader;
    }

    /**
     * 重新读取配置文件并把差异施加到运行中的注册表。
     *
     * <p>两类失败刻意不对称处理：</p>
     * <ul>
     *   <li>配置文件解析失败或解析结果为空——整体中止，一个数据源都不动。这是用户的手误，应当全量拒绝。</li>
     *   <li>单个数据库连不上——部分成功，其余照常生效，失败项进 failed 明细。
     *       某个库临时宕机是常态，不该因此阻塞其它库的配置更新。</li>
     * </ul>
     *
     * @return 重载报告，含 added/updated/removed/unchanged/failed 与默认数据源变化
     */
    public synchronized Map<String, Object> reload() {
        String configPath = configLoader.getConfigPath();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("configPath", configPath);

        Map<String, Map<String, Object>> desired;
        try {
            desired = configLoader.readDataSourcesFromDisk();
        } catch (Exception e) {
            log.error("重载失败，配置文件无法解析：{}", e.getMessage(), e);
            return abort(report, "配置文件解析失败，本次重载未改变任何数据源：" + e.getMessage());
        }

        if (CollectionUtils.isEmpty(desired)) {
            return abort(report, "配置文件中没有任何数据源，拒绝重载以免服务失去全部连接");
        }

        Map<String, Map<String, Object>> current = registry.snapshotProperties();
        List<String> added = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        Map<String, String> failed = new LinkedHashMap<>();

        // 1. 新增与变更。新池校验通过后才替换旧池，单库失败绝不影响其它库
        for (Map.Entry<String, Map<String, Object>> entry : desired.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> properties = entry.getValue();
            Map<String, Object> live = current.get(name);

            if (live != null && live.equals(properties)) {
                unchanged.add(name);
                continue;
            }

            try {
                registry.register(name, properties, true);
                if (live == null) {
                    added.add(name);
                } else {
                    updated.add(name);
                }
            } catch (Exception e) {
                failed.put(name, e.getMessage());
                log.error("重载数据源 [{}] 失败：{}", name, e.getMessage(), e);
            }
        }

        // 2. 切换默认数据源。必须排在移除之前——注册表拒绝移除当前默认数据源
        String desiredDefault = resolveDefaultName(desired);
        String previousDefault = registry.getDefaultName();
        if (!Objects.equals(desiredDefault, previousDefault)) {
            try {
                registry.setDefaultName(desiredDefault);
                report.put("defaultChangedFrom", previousDefault);
            } catch (Exception e) {
                failed.put(desiredDefault, "无法切换为默认数据源：" + e.getMessage());
                log.error("切换默认数据源到 [{}] 失败：{}", desiredDefault, e.getMessage(), e);
            }
        }

        // 3. 移除配置中已不存在的数据源，连接池延迟关闭
        for (String name : current.keySet()) {
            if (desired.containsKey(name)) {
                continue;
            }
            try {
                registry.unregister(name);
                removed.add(name);
            } catch (Exception e) {
                failed.put(name, e.getMessage());
                log.error("移除数据源 [{}] 失败：{}", name, e.getMessage(), e);
            }
        }

        report.put("success", failed.isEmpty());
        report.put("added", added);
        report.put("updated", updated);
        report.put("removed", removed);
        report.put("unchanged", unchanged);
        report.put("failed", failed);
        report.put("defaultDataSource", registry.getDefaultName());
        report.put("datasources", registry.names());

        log.info("数据源重载完成：新增={} 变更={} 移除={} 未变={} 失败={}", added, updated, removed, unchanged, failed.keySet());
        return report;
    }

    /**
     * 从新配置中解析默认数据源：优先取标记 default: true 的，否则取书写顺序的第一个。
     * 与 DataSourceConfig#getDefaultDataSourceName 保持同一语义，但作用在刚读到的磁盘配置上。
     */
    private String resolveDefaultName(Map<String, Map<String, Object>> desired) {
        for (Map.Entry<String, Map<String, Object>> entry : desired.entrySet()) {
            Map<String, Object> properties = entry.getValue();
            if (properties != null && Boolean.TRUE.equals(properties.get("default"))) {
                return entry.getKey();
            }
        }
        return desired.keySet().iterator().next();
    }

    /**
     * 整体中止：注册表保持原样，报告里说明原因。
     */
    private Map<String, Object> abort(Map<String, Object> report, String reason) {
        report.put("success", false);
        report.put("error", reason);
        report.put("defaultDataSource", registry.getDefaultName());
        report.put("datasources", registry.names());
        log.warn("重载中止：{}", reason);
        return report;
    }
}
