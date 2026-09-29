package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.service.InventoryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 库存查询
 */
@Tag(name = "库存查询")
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;

    @Operation(summary = "库存总览: 可用/锁定/在途/近效期/低库存")
    @GetMapping("/summary")
    @SaCheckPermission("INVENTORY_VIEW")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Long warehouseId) {
        return Result.success(inventoryQueryService.summary(warehouseId));
    }

    @Operation(summary = "库存明细")
    @GetMapping("/details")
    @SaCheckPermission("INVENTORY_DETAIL_VIEW")
    public Result<PageResult<Map<String, Object>>> details(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) Long warehouseId,
                                                           @RequestParam(required = false) Long categoryId,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String stockState) {
        return Result.success(PageResult.of(inventoryQueryService.details(page, size, warehouseId, categoryId, keyword, stockState)));
    }

    @Operation(summary = "批次库存")
    @GetMapping("/batches")
    @SaCheckPermission("BATCH_MANAGE_VIEW")
    public Result<PageResult<Map<String, Object>>> batches(@RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) Long warehouseId,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status) {
        return Result.success(PageResult.of(inventoryQueryService.batches(page, size, warehouseId, keyword, status)));
    }

    @Operation(summary = "批次冻结/解冻")
    @PostMapping("/batches/{id}/freeze")
    @SaCheckPermission("BATCH_MANAGE_EDIT")
    public Result<Void> freeze(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        inventoryQueryService.freeze(id, Boolean.TRUE.equals(body.get("freeze")));
        return Result.success();
    }

    @Operation(summary = "效期预警: 已过期 + N天内到期批次")
    @GetMapping("/expiry-alerts")
    @SaCheckPermission("INVENTORY_VIEW")
    public Result<java.util.List<Map<String, Object>>> expiryAlerts(@RequestParam(required = false) Long warehouseId,
                                                                    @RequestParam(required = false) Integer days) {
        return Result.success(inventoryQueryService.expiryAlerts(warehouseId, days));
    }

    @Operation(summary = "库存流水")
    @GetMapping({"/transactions", "/ledger"})
    @SaCheckPermission("INVENTORY_LEDGER_VIEW")
    public Result<PageResult<Map<String, Object>>> transactions(@RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int size,
                                                                @RequestParam(required = false) Long warehouseId,
                                                                @RequestParam(required = false) Long materialId,
                                                                @RequestParam(required = false) String sourceType,
                                                                @RequestParam(required = false) String sourceNo) {
        return Result.success(PageResult.of(inventoryQueryService.transactions(page, size, warehouseId, materialId, sourceType, sourceNo)));
    }
}
