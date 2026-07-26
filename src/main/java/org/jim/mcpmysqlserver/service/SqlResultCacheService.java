package org.jim.mcpmysqlserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jim.mcpmysqlserver.config.ToolResponseLimitConfig;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 截断结果缓存服务，负责为超长 SQL 结果生成稳定快照并提供短期续取能力。
 *
 *
 * @author Codex
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SqlResultCacheService {

    private final ToolResponseLimitConfig toolResponseLimitConfig;
    private final Map<String, TruncatedResultSnapshot> snapshotStore = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> snapshotOrder = new ConcurrentLinkedDeque<>();

    /**
     * 生成大结果快照，确保后续分页读取看到的是同一份不可变数据。
     *
     * @param datasourceName 数据源名称
     * @param rows 完整结果行
     * @param originalChars 完整结果字符数
     * @return 新建的结果快照
     */
    public TruncatedResultSnapshot save(String datasourceName, List<Map<String, Object>> rows, int originalChars) {
        // 在写入时复制数据，避免调用方后续修改破坏分页一致性。
        List<Map<String, Object>> immutableRows = rows.stream()
                .map(this::copyRow)
                .toList();
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plusSeconds(Math.max(toolResponseLimitConfig.getCacheTtlMinutes(), 0) * 60);
        TruncatedResultSnapshot snapshot = new TruncatedResultSnapshot(
                "sqlr_" + UUID.randomUUID().toString().replace("-", ""),
                datasourceName,
                immutableRows,
                originalChars,
                createdAt,
                expiresAt
        );
        snapshotStore.put(snapshot.resultId(), snapshot);
        snapshotOrder.addLast(snapshot.resultId());
        evictOverflowSnapshots();
        return snapshot;
    }

    /**
     * 读取结果快照，并在入口处清理已失效内容，保证续取只看到仍有效的数据。
     *
     * @param resultId 快照标识
     * @return 仍有效的快照
     */
    public Optional<TruncatedResultSnapshot> get(String resultId) {
        // 每次读取顺带清理过期项，避免额外后台线程复杂度。
        evictExpiredSnapshots();
        TruncatedResultSnapshot snapshot = snapshotStore.get(resultId);
        if (snapshot == null || isExpired(snapshot)) {
            remove(resultId);
            return Optional.empty();
        }
        return Optional.of(snapshot);
    }

    /**
     * 删除指定结果快照，确保失效结果不会继续暴露给后续 tool 调用。
     *
     * @param resultId 快照标识
     */
    public void remove(String resultId) {
        snapshotStore.remove(resultId);
        snapshotOrder.remove(resultId);
    }

    private Map<String, Object> copyRow(Map<String, Object> row) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(row));
    }

    private void evictExpiredSnapshots() {
        List<String> expiredIds = new ArrayList<>();
        for (Map.Entry<String, TruncatedResultSnapshot> entry : snapshotStore.entrySet()) {
            if (isExpired(entry.getValue())) {
                expiredIds.add(entry.getKey());
            }
        }
        expiredIds.forEach(this::remove);
    }

    private void evictOverflowSnapshots() {
        int maxEntries = Math.max(toolResponseLimitConfig.getCacheMaxEntries(), 1);
        while (snapshotStore.size() > maxEntries) {
            String oldestResultId = snapshotOrder.pollFirst();
            if (oldestResultId == null) {
                return;
            }
            snapshotStore.remove(oldestResultId);
            log.info("清理超限 SQL 结果快照，resultId={}", oldestResultId);
        }
    }

    private boolean isExpired(TruncatedResultSnapshot snapshot) {
        return !snapshot.expiresAt().isAfter(Instant.now());
    }
}
