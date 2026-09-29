package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.inventory.entity.ReturnOrder;
import com.medistock.pro.modules.inventory.service.ReturnService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/return-orders")
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService returnService;

    @GetMapping
    @SaCheckPermission("STOCK_RETURN_VIEW")
    public Result<PageResult<ReturnOrder>> page(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) Long warehouseId,
                                                @RequestParam(required = false) String status) {
        return Result.success(returnService.pageQuery(page, size, warehouseId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("STOCK_RETURN_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(returnService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("STOCK_RETURN_CREATE")
    public Result<ReturnOrder> create(@Valid @RequestBody CreateReq req) {
        return Result.success(returnService.create(req.issueId(), req.warehouseId(), req.reason(), req.items()));
    }

    @PostMapping("/{id}/confirm")
    @SaCheckPermission("STOCK_RETURN_CONFIRM")
    public Result<Void> confirm(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        returnService.confirm(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission("STOCK_RETURN_CREATE")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        returnService.cancel(id, dto.version());
        return Result.success();
    }

    public record CreateReq(@NotNull Long issueId, @NotNull Long warehouseId, String reason,
                            @NotEmpty @Valid List<ReturnService.Line> items) {}
}
