package com.medistock.pro.modules.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.purchase.entity.SupplierMaterial;
import com.medistock.pro.modules.purchase.service.SupplierMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 供应商物资报价 (P015)
 */
@Tag(name = "供应商物资报价")
@RestController
@RequestMapping("/api/v1/supplier-materials")
@RequiredArgsConstructor
public class SupplierMaterialController {

    private final SupplierMaterialService supplierMaterialService;

    @Operation(summary = "供应商报价列表")
    @GetMapping
    @SaCheckPermission("SUPPLIER_MATERIAL_MANAGE_VIEW")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Long supplierId) {
        return Result.success(supplierMaterialService.listBySupplier(supplierId));
    }

    @Operation(summary = "新增报价")
    @PostMapping
    @SaCheckPermission("SUPPLIER_MATERIAL_MANAGE_EDIT")
    public Result<SupplierMaterial> create(@RequestBody SupplierMaterial sm) {
        return Result.success(supplierMaterialService.create(sm));
    }

    @Operation(summary = "调价/维护")
    @PutMapping("/{id}")
    @SaCheckPermission("SUPPLIER_MATERIAL_MANAGE_EDIT")
    public Result<Void> update(@PathVariable Long id, @RequestBody SupplierMaterial patch) {
        supplierMaterialService.update(id, patch);
        return Result.success();
    }

    @Operation(summary = "启用/停用")
    @PostMapping("/{id}/status")
    @SaCheckPermission("SUPPLIER_MATERIAL_MANAGE_EDIT")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        supplierMaterialService.changeStatus(id, body.get("status"));
        return Result.success();
    }
}
