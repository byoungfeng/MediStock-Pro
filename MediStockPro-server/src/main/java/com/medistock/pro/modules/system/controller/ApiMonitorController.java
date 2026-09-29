package com.medistock.pro.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.system.ApiMetricsCollector;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 接口监控 (P059): 请求数/成功率/耗时/状态码分布 (内存聚合, 重启清零)
 */
@Tag(name = "接口监控")
@RestController
@RequestMapping("/api/v1/api-monitoring")
@RequiredArgsConstructor
public class ApiMonitorController {

    private final ApiMetricsCollector metricsCollector;

    @Operation(summary = "监控快照")
    @GetMapping
    @SaCheckPermission("API_MONITOR_VIEW")
    public Result<Map<String, Object>> snapshot() {
        return Result.success(metricsCollector.snapshot());
    }

    @Operation(summary = "清零重计")
    @PostMapping("/reset")
    @SaCheckPermission("API_MONITOR_VIEW")
    public Result<Void> reset() {
        metricsCollector.reset();
        return Result.success();
    }
}
