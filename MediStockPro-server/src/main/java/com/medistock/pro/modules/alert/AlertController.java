package com.medistock.pro.modules.alert;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预警中心 (P046-P048)
 */
@Tag(name = "预警中心")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @Operation(summary = "库存预警: 低库存/断货")
    @GetMapping("/alerts/stock-level")
    @SaCheckPermission("ALERT_VIEW")
    public Result<List<Map<String, Object>>> stockLevel(@RequestParam(required = false) Long warehouseId) {
        return Result.success(alertService.stockLevel(warehouseId));
    }

    @Operation(summary = "到货预警: 逾期订单")
    @GetMapping("/alerts/arrival")
    @SaCheckPermission("ALERT_VIEW")
    public Result<List<Map<String, Object>>> arrival() {
        return Result.success(alertService.arrival());
    }

    @Operation(summary = "供应商绩效: 准时率/合格率/规模")
    @GetMapping("/reports/supplier-performance")
    @SaCheckPermission("REPORT_SUPPLIER")
    public Result<List<Map<String, Object>>> supplierPerformance() {
        return Result.success(alertService.supplierPerformance());
    }

    public record HandleReq(@NotBlank(message = "action 必填") String action) {
    }

    @Operation(summary = "预警记录分页 (持久化)")
    @GetMapping("/alerts")
    @SaCheckPermission("ALERT_VIEW")
    public Result<PageResult<Alert>> page(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String type,
                                          @RequestParam(required = false) String status) {
        Page<Alert> p = alertService.pageAlerts(page, size, type, status);
        return Result.success(PageResult.of(p));
    }

    @Operation(summary = "处置预警 (HANDLED/IGNORED)")
    @PostMapping("/alerts/{id}/handle")
    @SaCheckPermission("ALERT_VIEW")
    public Result<Void> handle(@PathVariable Long id, @Valid @RequestBody HandleReq req) {
        alertService.handle(id, req.action());
        return Result.success();
    }
}
