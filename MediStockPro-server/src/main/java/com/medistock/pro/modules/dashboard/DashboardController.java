package com.medistock.pro.modules.dashboard;

import com.medistock.pro.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 首页仪表盘 (P002)
 */
@Tag(name = "仪表盘")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "仪表盘聚合: 库存指标/待办/近7日趋势")
    @GetMapping
    public Result<Map<String, Object>> overview(@RequestParam(required = false) Long warehouseId) {
        return Result.success(dashboardService.overview(warehouseId));
    }
}
