package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /** 收发存汇总: 期初/期间入/期间出/期末 (默认近30天) */
    @GetMapping("/inout-summary")
    @SaCheckPermission("REPORT_VIEW")
    public Result<List<Map<String, Object>>> inoutSummary(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.minusDays(30);
        return Result.success(reportService.inoutSummary(warehouseId, fromDate, toDate));
    }

    /** P049 采购分析 */
    @GetMapping("/purchase")
    @SaCheckPermission("REPORT_VIEW")
    public Result<Map<String, Object>> purchaseAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        return Result.success(reportService.purchaseAnalysis(from != null ? from : toDate.minusDays(30), toDate));
    }

    /** P050 库存分析 */
    @GetMapping("/inventory")
    @SaCheckPermission("REPORT_VIEW")
    public Result<Map<String, Object>> inventoryAnalysis(@RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.inventoryAnalysis(warehouseId));
    }

    /** P051 领用分析 */
    @GetMapping("/issue")
    @SaCheckPermission("REPORT_VIEW")
    public Result<Map<String, Object>> issueAnalysis(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate toDate = to != null ? to : LocalDate.now();
        return Result.success(reportService.issueAnalysis(warehouseId, from != null ? from : toDate.minusDays(30), toDate));
    }

    /** P052 近效期分析 */
    @GetMapping("/expiry")
    @SaCheckPermission("REPORT_VIEW")
    public Result<List<Map<String, Object>>> expiryAnalysis(@RequestParam(required = false) Long warehouseId) {
        return Result.success(reportService.expiryAnalysis(warehouseId));
    }

    /** P054 库存变动趋势 */
    @GetMapping("/movement-trend")
    @SaCheckPermission("REPORT_VIEW")
    public Result<List<Map<String, Object>>> movementTrend(@RequestParam(required = false) Long warehouseId,
                                                           @RequestParam(defaultValue = "30") int days) {
        return Result.success(reportService.movementTrend(warehouseId, days));
    }
}
