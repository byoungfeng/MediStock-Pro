package com.medistock.pro.modules.master.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.master.entity.Warehouse;
import com.medistock.pro.modules.master.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仓库
 */
@Tag(name = "仓库")
@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @Operation(summary = "库房树(登录即可, 按数据权限过滤)")
    @GetMapping("/tree")
    public Result<List<Warehouse>> tree() {
        return Result.success(warehouseService.tree());
    }

    @Operation(summary = "列表")
    @GetMapping
    @SaCheckPermission("ORG_MANAGE_VIEW")
    public Result<List<Warehouse>> list() {
        return Result.success(warehouseService.list());
    }

    @Operation(summary = "新增")
    @PostMapping
    @SaCheckPermission("ORG_MANAGE_CREATE")
    public Result<Warehouse> create(@Valid @RequestBody Warehouse warehouse) {
        warehouseService.save(warehouse);
        return Result.success(warehouse);
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    @SaCheckPermission("ORG_MANAGE_EDIT")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Warehouse warehouse) {
        warehouse.setId(id);
        warehouseService.updateById(warehouse);
        return Result.success();
    }

    @Operation(summary = "停用/删除(逻辑)")
    @DeleteMapping("/{id}")
    @SaCheckPermission("ORG_MANAGE_EDIT")
    public Result<Void> delete(@PathVariable Long id) {
        warehouseService.removeById(id);
        return Result.success();
    }
}
