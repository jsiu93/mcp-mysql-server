package org.jim.mcpmysqlserver.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 截断结果快照，负责固定一次查询的大结果内容与失效时间。
 *
 * <p>线程安全性：记录本身不可变，内部行数据在创建后不再修改。</p>
 *
 * @param resultId 快照标识
 * @param datasourceName 数据源名称
 * @param rows 完整结果行
 * @param originalChars 完整结果字符数
 * @param createdAt 创建时间
 * @param expiresAt 失效时间
 * @author Codex
 */
public record TruncatedResultSnapshot(String resultId,
                                      String datasourceName,
                                      List<Map<String, Object>> rows,
                                      int originalChars,
                                      Instant createdAt,
                                      Instant expiresAt) {
}
