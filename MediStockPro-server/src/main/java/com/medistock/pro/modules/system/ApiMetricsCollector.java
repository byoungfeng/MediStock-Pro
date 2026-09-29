package com.medistock.pro.modules.system;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 接口监控指标 (P059): 内存滑动聚合, 按 METHOD + URI模板(数字段归一化为 {id}) 分组。
 * 重启清零; 演示规模足够, 免建表。
 */
@Component
public class ApiMetricsCollector {

    public static class EndpointStat {
        public final AtomicLong count = new AtomicLong();
        public final AtomicLong errorCount = new AtomicLong();
        public final AtomicLong totalMs = new AtomicLong();
        public final AtomicLong maxMs = new AtomicLong();
        public final Map<Integer, AtomicLong> statusDist = new ConcurrentHashMap<>();
    }

    private final Map<String, EndpointStat> stats = new ConcurrentHashMap<>();
    private volatile LocalDateTime since = LocalDateTime.now();

    /** 记录一次请求 */
    public void record(String method, String uri, int httpStatus, long durationMs) {
        String key = method + " " + normalize(uri);
        EndpointStat s = stats.computeIfAbsent(key, k -> new EndpointStat());
        s.count.incrementAndGet();
        if (httpStatus >= 400) {
            s.errorCount.incrementAndGet();
        }
        s.totalMs.addAndGet(durationMs);
        s.maxMs.accumulateAndGet(durationMs, Math::max);
        s.statusDist.computeIfAbsent(httpStatus, k -> new AtomicLong()).incrementAndGet();
    }

    /** 聚合快照 (按请求数降序) */
    public Map<String, Object> snapshot() {
        List<Map<String, Object>> endpoints = new ArrayList<>();
        long totalCount = 0, totalError = 0;
        for (Map.Entry<String, EndpointStat> e : stats.entrySet()) {
            EndpointStat s = e.getValue();
            long count = s.count.get();
            long error = s.errorCount.get();
            totalCount += count;
            totalError += error;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("endpoint", e.getKey());
            row.put("count", count);
            row.put("errorCount", error);
            row.put("successRate", count == 0 ? 1.0 : Math.round((count - error) * 10000.0 / count) / 100.0);
            row.put("avgMs", count == 0 ? 0 : s.totalMs.get() / count);
            row.put("maxMs", s.maxMs.get());
            Map<String, Long> dist = new LinkedHashMap<>();
            s.statusDist.forEach((k, v) -> dist.put(String.valueOf(k), v.get()));
            row.put("statusDist", dist);
            endpoints.add(row);
        }
        endpoints.sort(Comparator.comparingLong(r -> -((Number) ((Map<?, ?>) r).get("count")).longValue()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("since", since);
        result.put("totalCount", totalCount);
        result.put("totalError", totalError);
        result.put("successRate", totalCount == 0 ? 100.0
                : Math.round((totalCount - totalError) * 10000.0 / totalCount) / 100.0);
        result.put("endpoints", endpoints);
        return result;
    }

    /** 清零重计 */
    public void reset() {
        stats.clear();
        since = LocalDateTime.now();
    }

    /** /api/v1/users/123/roles -> /api/v1/users/{id}/roles */
    private String normalize(String uri) {
        return uri.replaceAll("/\\d+", "/{id}");
    }
}
