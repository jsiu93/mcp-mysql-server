package org.jim.mcpmysqlserver.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 主动监控父进程是否存在，如果父进程退出，则自动关闭Spring Boot应用。
 * @author James Smith
 */
@Configuration
@Slf4j
public class ParentProcessWatchdog {

    @Resource
    private ConfigurableApplicationContext context;

    private ScheduledExecutorService ppidMonitorScheduler;

    private long ppid = -1;

    @PostConstruct
    public void init() {
        // 获取父进程ID
        try {
            ppid = ProcessHandle.current().parent()
                    // 如果使用Maven Wrapper启动，目前观察到parent()是Maven Wrapper的PID（ps -p 61028 -o ppid,command -ww）。还要往上找一层。
                    .flatMap(ProcessHandle::parent)
                    .map(ProcessHandle::pid)
                    .orElse(-1L);
            log.info("parent process id: {}", ppid);
        } catch (Exception e) { // Catch SecurityException or others if restricted
            log.error("Could not get PPID using ProcessHandle: {}", e.getMessage());
        }

        if (ppid == -1) {
            log.error("Warning: Could not determine parent PID. Automatic shutdown on parent exit disabled.");
            return;
        }

        log.info("Monitoring parent process with PID: {}", ppid);
        ppidMonitorScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PPID-Monitor");
            // 确保这个线程不会阻止应用退出
            t.setDaemon(true);
            return t;
        });

        // 每5秒检查一次
        ppidMonitorScheduler.scheduleAtFixedRate(this::checkParentProcess, 5, 5, TimeUnit.SECONDS);

    }

    private void checkParentProcess() {
        if (ppid == -1) {
            return;
        }

        boolean parentExists;
        parentExists = ProcessHandle.of(ppid).map(ProcessHandle::isAlive).orElse(false);
        if (parentExists) {
            return;
        }

        log.info("Parent process (PID: {}) no longer exists. Shutting down Spring Boot application...", ppid);
        // 关闭 Spring Boot 应用
        if (context != null) {
            log.info("Closing Spring Application Context...");
            SpringApplication.exit(context, () -> 0);
        } else {
            log.error("Spring Application Context is null. will exit directly.");
            // 如果 context 未初始化
            System.exit(0);
        }

        // 停止调度器
        if (ppidMonitorScheduler != null && !ppidMonitorScheduler.isShutdown()) {
            ppidMonitorScheduler.shutdownNow();
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Spring Boot application shutting down...");
        if (ppidMonitorScheduler != null && !ppidMonitorScheduler.isShutdown()) {
            ppidMonitorScheduler.shutdownNow();
        }
    }
}
