package com.medistock.pro.modules.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.purchase.entity.Supplier;
import com.medistock.pro.modules.purchase.entity.SupplierQualification;
import com.medistock.pro.modules.purchase.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @SaCheckPermission("SUPPLIER_VIEW")
    public Result<PageResult<Supplier>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Integer status) {
        return Result.success(supplierService.pageQuery(page, size, keyword, status));
    }

    @GetMapping("/all")
    @SaCheckPermission("SUPPLIER_VIEW")
    public Result<List<Supplier>> all() {
        return Result.success(supplierService.lambdaQuery().eq(Supplier::getStatus, 1)
                .orderByAsc(Supplier::getCode).list());
    }

    @PostMapping
    @SaCheckPermission("SUPPLIER_EDIT")
    public Result<Supplier> create(@Valid @RequestBody Supplier supplier) {
        return Result.success(supplierService.create(supplier));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("SUPPLIER_EDIT")
    public Result<Supplier> update(@PathVariable Long id, @Valid @RequestBody Supplier supplier) {
        return Result.success(supplierService.update(id, supplier));
    }

    @GetMapping("/{id}/qualifications")
    @SaCheckPermission("SUPPLIER_VIEW")
    public Result<List<SupplierQualification>> qualifications(@PathVariable Long id) {
        return Result.success(supplierService.qualifications(id));
    }

    @PostMapping("/{id}/qualifications")
    @SaCheckPermission("SUPPLIER_EDIT")
    public Result<SupplierQualification> addQualification(@PathVariable Long id,
                                                          @Valid @RequestBody SupplierQualification qual) {
        return Result.success(supplierService.addQualification(id, qual));
    }
}
