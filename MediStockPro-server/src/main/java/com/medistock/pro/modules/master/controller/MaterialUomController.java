package com.medistock.pro.modules.master.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.master.entity.MaterialUom;
import com.medistock.pro.modules.master.service.MaterialUomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 包装换算 (P012)
 */
@Tag(name = "包装换算")
@RestController
@RequestMapping("/api/v1/material-uoms")
@RequiredArgsConstructor
public class MaterialUomController {

    private final MaterialUomService uomService;

    @Operation(summary = "换算列表 (可按物资过滤)")
    @GetMapping
    @SaCheckPermission("MATERIAL_UOM_MANAGE_VIEW")
    public Result<List<MaterialUom>> list(@RequestParam(required = false) Long materialId) {
        return Result.success(uomService.listByMaterial(materialId));
    }

    @Operation(summary = "新增换算")
    @PostMapping
    @SaCheckPermission("MATERIAL_UOM_MANAGE_EDIT")
    public Result<MaterialUom> create(@RequestBody MaterialUom uom) {
        return Result.success(uomService.create(uom));
    }

    @Operation(summary = "编辑换算 (仅率/状态)")
    @PutMapping("/{id}")
    @SaCheckPermission("MATERIAL_UOM_MANAGE_EDIT")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaterialUom patch) {
        uomService.update(id, patch);
        return Result.success();
    }

    @Operation(summary = "启用/停用")
    @PostMapping("/{id}/status")
    @SaCheckPermission("MATERIAL_UOM_MANAGE_EDIT")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        uomService.changeStatus(id, body.get("status"));
        return Result.success();
    }
}
