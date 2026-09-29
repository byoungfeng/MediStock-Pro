package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.inventory.entity.ScrapOrder;
import com.medistock.pro.modules.inventory.service.ScrapService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scrap-orders")
@RequiredArgsConstructor
public class ScrapController {

    private final ScrapService scrapService;

    @GetMapping
    @SaCheckPermission("STOCK_SCRAP_VIEW")
    public Result<PageResult<ScrapOrder>> page(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) Long warehouseId,
                                               @RequestParam(required = false) String status) {
        return Result.success(scrapService.pageQuery(page, size, warehouseId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("STOCK_SCRAP_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(scrapService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("STOCK_SCRAP_CREATE")
    public Result<ScrapOrder> create(@Valid @RequestBody CreateReq req) {
        return Result.success(scrapService.create(req.warehouseId(), req.reason(), req.items()));
    }

    @PostMapping("/{id}/approve")
    @SaCheckPermission("STOCK_SCRAP_APPROVE")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        scrapService.approve(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission("STOCK_SCRAP_CREATE")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        scrapService.cancel(id, dto.version());
        return Result.success();
    }

    public record CreateReq(@NotNull Long warehouseId, @NotNull String reason,
                            @NotEmpty @Valid List<ScrapService.Line> items) {}
}
