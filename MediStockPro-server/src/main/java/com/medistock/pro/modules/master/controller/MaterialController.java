package com.medistock.pro.modules.master.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.master.entity.Material;
import com.medistock.pro.modules.master.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 物资主数据
 */
@Tag(name = "物资主数据")
@RestController
@RequestMapping("/api/v1/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @Operation(summary = "分页查询")
    @GetMapping
    @SaCheckPermission("MATERIAL_MANAGE_VIEW")
    public Result<PageResult<Material>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Integer status) {
        Page<Material> result = materialService.pageQuery(page, size, keyword, categoryId, status);
        return Result.success(PageResult.of(result));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    @SaCheckPermission("MATERIAL_MANAGE_VIEW")
    public Result<Material> detail(@PathVariable Long id) {
        return Result.success(materialService.getById(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    @SaCheckPermission("MATERIAL_MANAGE_CREATE")
    public Result<Material> create(@Valid @RequestBody Material material) {
        materialService.create(material);
        return Result.success(material);
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    @SaCheckPermission("MATERIAL_MANAGE_EDIT")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody Material material) {
        material.setId(id);
        materialService.update(material);
        return Result.success();
    }

    @Operation(summary = "停用/删除(逻辑)")
    @DeleteMapping("/{id}")
    @SaCheckPermission("MATERIAL_MANAGE_EDIT")
    public Result<Void> delete(@PathVariable Long id) {
        materialService.removeWithRefCheck(id);
        return Result.success();
    }

    @Operation(summary = "批量属性修改 (P011, 冲突跳过并返回明细)")
    @PostMapping("/batch-attrs")
    @SaCheckPermission("MATERIAL_ATTRIBUTE_MANAGE_EDIT")
    public Result<MaterialService.BatchAttrResult> batchAttrs(@RequestBody MaterialService.BatchAttrUpdate req) {
        return Result.success(materialService.batchUpdateAttrs(req));
    }
}
