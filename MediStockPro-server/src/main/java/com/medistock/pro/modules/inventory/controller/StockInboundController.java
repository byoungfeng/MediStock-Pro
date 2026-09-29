package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.InboundDTO;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.inventory.entity.StockInbound;
import com.medistock.pro.modules.inventory.service.StockInboundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 入库单
 */
@Tag(name = "入库单")
@RestController
@RequestMapping("/api/v1/stock-inbounds")
@RequiredArgsConstructor
public class StockInboundController {

    private final StockInboundService stockInboundService;

    @Operation(summary = "入库单列表")
    @GetMapping
    @SaCheckPermission("STOCK_INBOUND_VIEW")
    public Result<PageResult<StockInbound>> page(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) Long warehouseId,
                                                 @RequestParam(required = false) String keyword) {
        return Result.success(PageResult.of(stockInboundService.pageQuery(page, size, status, warehouseId, keyword)));
    }

    @Operation(summary = "入库单详情(含明细)")
    @GetMapping("/{id}")
    @SaCheckPermission("STOCK_INBOUND_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(stockInboundService.detail(id));
    }

    @Operation(summary = "创建入库单(草稿)")
    @PostMapping
    @SaCheckPermission("STOCK_INBOUND_EXECUTE")
    public Result<StockInbound> create(@Valid @RequestBody InboundDTO dto) {
        return Result.success(stockInboundService.create(dto));
    }

    @Operation(summary = "确认入库(库存事务, 幂等)")
    @PostMapping("/{id}/confirm")
    @SaCheckPermission("STOCK_INBOUND_EXECUTE")
    public Result<Void> confirm(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        stockInboundService.confirm(id, dto.version());
        return Result.success();
    }
}
