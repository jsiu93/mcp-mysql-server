package org.jim.mcpmysqlserver.service;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.util.DatabaseTypeDetector;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyNameAliases;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * [INPUT]: 依赖 util/DatabaseTypeDetector 的 JDBC URL 驱动推断，依赖 Spring Boot Binder 与 HikariCP 构造连接池。
 *          刻意不依赖 config 包的任何类型，配置以裸 Map 由外部注入，从而消除 config/service 的包级循环。
 * [OUTPUT]: 对外提供 DataSourceRegistry（bootstrap/register/unregister/getDataSource/names/details/setDefaultName/
 *          snapshotProperties/shutdown）与嵌套 record Entry。
 * [POS]: service 层唯一的连接池持有者与生命周期管理者，运行期数据源真相的单一来源。
 *        DataSourceService 只读它做定位；config/DataSourceReloadService 只写它做增删；
 *        config/DynamicDataSourceConfig 的 @Primary 代理通过它解析默认库。
 *        连接池的创建、校验、优雅关闭全部收敛于此，别处不得自行 new HikariDataSource。
 * [PROTOCOL]: 变更时更新此头部，然后检查 AGENTS.md
 *
 * @author yangxin
 */
@Slf4j
public class DataSourceRegistry {

    /**
     * 兼容旧调用方的默认数据源别名，指向当前默认数据源而非某个具体名称
     */
    public static final String PRIMARY_ALIAS = "primary";

    /**
     * 连接校验的超时秒数，与 Hikari 默认 connectionTimeout(10s) 同量级
     */
    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    /**
     * 单个已注册数据源：配置快照 + 实际连接池。
     * 配置快照保留用户在 yml 中写下的原始内容（不含自动补全的驱动名），供重载时做差异比对。
     */
    public record Entry(String name, Map<String, Object> properties, HikariDataSource dataSource) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * 当前默认数据源名称。volatile 保证 reload 线程的切换对查询线程立即可见。
     */
    private volatile String defaultName;

    /**
     * 摘除数据源后延迟关闭连接池的宽限秒数。
     * HikariCP 的 close() 会强杀在途连接（softEvict → assassin executor），因此必须延迟且异步执行。
     */
    private final long graceSeconds;

    private final ScheduledExecutorService gracefulCloser;

    public DataSourceRegistry(long graceSeconds) {
        this.graceSeconds = Math.max(0L, graceSeconds);
        this.gracefulCloser = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "datasource-graceful-closer");
            thread.setDaemon(true);
            return thread;
        });
        log.info("数据源注册表初始化完成，连接池关闭宽限期={}秒", this.graceSeconds);
    }

    // ========================= 启动引导 =========================

    /**
     * 启动期一次性建立所有数据源。
     *
     * <p>刻意不做连接校验：某个库临时不可达不应阻止整个服务启动，这与重构前 DataSourceBuilder 的惰性行为一致。
     * 连接校验只发生在运行期重载时，那里用户刚改完配置，需要立即拿到反馈。</p>
     *
     * @param configured 数据源名称到属性的映射，需保持 yml 中的书写顺序
     * @param preferredDefaultName 配置中标记或推断出的默认数据源名称
     */
    public synchronized void bootstrap(Map<String, Map<String, Object>> configured, String preferredDefaultName) {
        if (CollectionUtils.isEmpty(configured)) {
            log.warn("启动时未配置任何数据源，注册表为空");
            return;
        }

        for (Map.Entry<String, Map<String, Object>> configuredEntry : configured.entrySet()) {
            String name = configuredEntry.getKey();
            try {
                register(name, configuredEntry.getValue(), false);
            } catch (Exception e) {
                log.error("启动时初始化数据源 [{}] 失败：{}", name, e.getMessage(), e);
            }
        }

        if (preferredDefaultName != null && entries.containsKey(preferredDefaultName)) {
            this.defaultName = preferredDefaultName;
        } else {
            // 按 yml 书写顺序退化，避免 ConcurrentHashMap 的无序迭代导致默认库随机
            this.defaultName = configured.keySet().stream()
                    .filter(entries::containsKey)
                    .findFirst()
                    .orElse(null);
            if (this.defaultName != null) {
                log.warn("配置的默认数据源 [{}] 不可用，退化为 [{}]", preferredDefaultName, this.defaultName);
            }
        }

        log.info("数据源注册表引导完成，共 {} 个数据源 {}，默认数据源 [{}]", entries.size(), names(), defaultName);
    }

    // ========================= 增删改 =========================

    /**
     * 注册数据源。同名已存在时构成替换：新池就位后旧池才进入延迟关闭，切换过程对查询无空窗。
     *
     * @param name 数据源名称
     * @param properties 数据源属性（url/username/password/driver-class-name/hikari...）
     * @param validate 是否在注册前实际建连校验。启动期传 false，运行期重载传 true
     * @throws IllegalStateException 驱动缺失或连接校验失败时抛出，且注册表状态保持不变
     */
    public synchronized void register(String name, Map<String, Object> properties, boolean validate) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        if (CollectionUtils.isEmpty(properties)) {
            throw new IllegalArgumentException("数据源 [" + name + "] 没有任何配置属性");
        }

        assertDriverAvailable(name, properties);

        // 先保存用户原始配置快照，createDataSource 内部的驱动补全不得污染它，否则下次重载会误判为"已变更"
        Map<String, Object> snapshot = new LinkedHashMap<>(properties);
        HikariDataSource created = createDataSource(name, properties);

        if (validate) {
            try {
                validateConnection(name, created);
            } catch (RuntimeException validationError) {
                closeQuietly(name, created);
                throw validationError;
            }
        }

        Entry previous = entries.put(name, new Entry(name, snapshot, created));
        if (previous != null) {
            log.info("数据源 [{}] 已被新连接池替换", name);
            scheduleClose(name, previous.dataSource());
        } else {
            log.info("数据源 [{}] 注册成功", name);
        }
    }

    /**
     * 摘除数据源。先从注册表移除（新查询立刻拿不到它），旧池延迟关闭让在途查询跑完。
     *
     * @param name 数据源名称
     * @return 被摘除的条目，不存在时返回 null
     * @throws IllegalStateException 试图摘除当前默认数据源时抛出
     */
    public synchronized Entry unregister(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源名称不能为空");
        }
        if (name.equals(defaultName)) {
            throw new IllegalStateException("不能移除默认数据源 [" + name + "]，请先在配置中把 default: true 指向其它数据源");
        }

        Entry removed = entries.remove(name);
        if (removed == null) {
            log.warn("数据源 [{}] 不在注册表中，无需移除", name);
            return null;
        }
        scheduleClose(name, removed.dataSource());
        return removed;
    }

    /**
     * 切换默认数据源。目标必须已注册，避免把默认指针指向空。
     *
     * @param name 新的默认数据源名称
     */
    public synchronized void setDefaultName(String name) {
        if (!entries.containsKey(name)) {
            throw new IllegalStateException("数据源 [" + name + "] 未注册，无法设为默认数据源");
        }
        String previous = this.defaultName;
        this.defaultName = name;
        log.info("默认数据源由 [{}] 切换为 [{}]", previous, name);
    }

    // ========================= 查询 =========================

    /**
     * 按名称精确查找数据源。别名解析（null/""/primary）由 DataSourceService 负责，此处只做直查。
     *
     * @param name 数据源名称
     * @return 数据源，未注册时返回 null
     */
    public DataSource getDataSource(String name) {
        Entry entry = entries.get(name);
        return entry == null ? null : entry.dataSource();
    }

    /**
     * @return 当前默认数据源，注册表为空时返回 null
     */
    public DataSource getDefaultDataSource() {
        String name = this.defaultName;
        return name == null ? null : getDataSource(name);
    }

    public String getDefaultName() {
        return this.defaultName;
    }

    /**
     * @return 已注册数据源名称的有序列表
     */
    public List<String> names() {
        List<String> allNames = new ArrayList<>(entries.keySet());
        Collections.sort(allNames);
        return allNames;
    }

    /**
     * 导出当前生效的配置快照，供重载时与磁盘配置做差异比对。
     *
     * @return 数据源名称到属性快照的映射
     */
    public Map<String, Map<String, Object>> snapshotProperties() {
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
        entries.forEach((name, entry) -> snapshot.put(name, entry.properties()));
        return snapshot;
    }

    /**
     * 汇总数据源元信息。刻意只暴露名称与数据库类型，绝不外泄 username/password。
     *
     * @return 按名称排序的数据源详情
     */
    public List<Map<String, Object>> details() {
        List<Map<String, Object>> allDetails = new ArrayList<>();
        String currentDefault = this.defaultName;

        for (Entry entry : entries.values()) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("name", entry.name());

            String url = (String) entry.properties().get("url");
            if (url != null) {
                DatabaseTypeDetector.DatabaseType dbType = DatabaseTypeDetector.detectDatabaseType(url);
                detail.put("databaseType", dbType.getDisplayName());
                detail.put("driverClassName", dbType.getDriverClassName());
            } else {
                detail.put("databaseType", "Unknown");
                detail.put("driverClassName", "Unknown");
                log.warn("数据源 [{}] 没有配置 URL，无法检测数据库类型", entry.name());
            }

            detail.put("isDefault", entry.name().equals(currentDefault));
            allDetails.add(detail);
        }

        allDetails.sort((left, right) -> ((String) left.get("name")).compareTo((String) right.get("name")));
        return allDetails;
    }

    // ========================= 生命周期 =========================

    /**
     * 容器关闭时立即关闭全部连接池，不再等待宽限期。
     */
    public synchronized void shutdown() {
        log.info("关闭数据源注册表，共 {} 个连接池", entries.size());
        gracefulCloser.shutdownNow();
        entries.values().forEach(entry -> closeQuietly(entry.name(), entry.dataSource()));
        entries.clear();
        this.defaultName = null;
    }

    // ========================= 内部实现 =========================

    /**
     * 校验驱动是否在当前 jar 内。fat jar 只打了部分驱动，缺失时必须给出可操作的错误而不是裸栈。
     */
    private void assertDriverAvailable(String name, Map<String, Object> properties) {
        Object configuredDriver = properties.get("driver-class-name");
        String driverClassName = configuredDriver instanceof String value && !value.isBlank()
                ? value
                : DatabaseTypeDetector.getDriverClassName((String) properties.get("url"));

        try {
            Class.forName(driverClassName, false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC 驱动 [" + driverClassName + "] 未打包进当前 jar，数据源 [" + name
                    + "] 无法创建；需要在 pom.xml 中加入该驱动依赖后重新构建");
        }
    }

    /**
     * 实际建连一次，确认地址、账号、网络都通。拿到物理连接即视为通过。
     *
     * <p>isValid 在部分驱动（如 IoTDB）实现不完整，因此只作补充探测，失败仅告警不否决。</p>
     */
    private void validateConnection(String name, HikariDataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            try {
                if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                    log.warn("数据源 [{}] 的 isValid 探测返回 false，但物理连接已建立，视为可用", name);
                }
            } catch (Throwable probeError) {
                log.warn("数据源 [{}] 不支持 isValid 探测：{}", name, probeError.getMessage());
            }
            log.info("数据源 [{}] 连接校验通过", name);
        } catch (SQLException e) {
            throw new IllegalStateException("连接失败：" + e.getMessage(), e);
        }
    }

    /**
     * 根据属性创建 HikariCP 连接池。由 DynamicDataSourceConfig 迁入，保持原有默认池参数不变。
     */
    private HikariDataSource createDataSource(String dsName, Map<String, Object> dsProperties) {
        // 复制一份再补全驱动名，不污染调用方传入的 Map
        Map<String, Object> effectiveProperties = new LinkedHashMap<>(dsProperties);
        if (!effectiveProperties.containsKey("driver-class-name")) {
            String url = (String) effectiveProperties.get("url");
            String driverClassName = DatabaseTypeDetector.getDriverClassName(url);
            effectiveProperties.put("driver-class-name", driverClassName);
            log.info("为数据源 [{}] 自动检测到数据库类型: {}，使用驱动: {}",
                    dsName, DatabaseTypeDetector.getDatabaseDisplayName(url), driverClassName);
        }

        DataSourceProperties dataSourceProperties = new DataSourceProperties();
        ConfigurationPropertySource source = new MapConfigurationPropertySource(effectiveProperties);
        ConfigurationPropertyNameAliases aliases = new ConfigurationPropertyNameAliases();
        aliases.addAliases("url", "jdbc-url");
        aliases.addAliases("username", "user");
        Binder binder = new Binder(source.withAliases(aliases));
        binder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(dataSourceProperties));

        HikariDataSource dataSource = dataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();

        // 默认池参数，用户在 hikari 段落中的配置会在下面覆盖它们
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(3);
        dataSource.setIdleTimeout(300000);
        dataSource.setConnectionTimeout(10000);
        dataSource.setMaxLifetime(1800000);
        dataSource.setPoolName(dsName + "-HikariCP");

        Object hikariNode = effectiveProperties.get("hikari");
        if (hikariNode instanceof Map<?, ?> hikariProperties && !hikariProperties.isEmpty()) {
            ConfigurationPropertySource hikariSource = new MapConfigurationPropertySource(hikariProperties);
            new Binder(hikariSource).bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(dataSource));
        }

        return dataSource;
    }

    /**
     * 延迟异步关闭连接池。同步关闭会阻塞调用线程十余秒并强杀在途查询，绝不可在 HTTP 线程上直接调用。
     */
    private void scheduleClose(String name, HikariDataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        if (graceSeconds == 0L) {
            closeQuietly(name, dataSource);
            return;
        }
        log.info("数据源 [{}] 已摘除，将在 {} 秒后关闭连接池，期间在途查询照常完成", name, graceSeconds);
        try {
            gracefulCloser.schedule(() -> closeQuietly(name, dataSource), graceSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("宽限关闭调度失败，直接关闭数据源 [{}]：{}", name, e.getMessage());
            closeQuietly(name, dataSource);
        }
    }

    private void closeQuietly(String name, HikariDataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        try {
            dataSource.close();
            log.info("数据源 [{}] 连接池已关闭", name);
        } catch (Exception e) {
            log.error("关闭数据源 [{}] 连接池失败：{}", name, e.getMessage(), e);
        }
    }
}
