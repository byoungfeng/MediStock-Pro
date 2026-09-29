package com.medistock.pro.modules.master.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.master.entity.MaterialCategory;
import com.medistock.pro.modules.master.service.MaterialCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 物资分类 (P009)
 */
@Tag(name = "物资分类")
@RestController
@RequestMapping("/api/v1/material-categories")
@RequiredArgsConstructor
public class MaterialCategoryController {

    private final MaterialCategoryService categoryService;

    @Operation(summary = "分类列表 (前端组树)")
    @GetMapping
    @SaCheckPermission("MATERIAL_CATEGORY_MANAGE_VIEW")
    public Result<List<MaterialCategory>> list() {
        return Result.success(categoryService.listAll());
    }

    @Operation(summary = "启用分类选项 (库存筛选等业务场景, 登录即可)")
    @GetMapping("/options")
    public Result<List<MaterialCategory>> options() {
        return Result.success(categoryService.listEnabled());
    }

    @Operation(summary = "新增分类")
    @PostMapping
    @SaCheckPermission("MATERIAL_CATEGORY_MANAGE_EDIT")
    public Result<MaterialCategory> create(@RequestBody MaterialCategory category) {
        return Result.success(categoryService.create(category));
    }

    @Operation(summary = "编辑分类")
    @PutMapping("/{id}")
    @SaCheckPermission("MATERIAL_CATEGORY_MANAGE_EDIT")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaterialCategory patch) {
        categoryService.update(id, patch);
        return Result.success();
    }

    @Operation(summary = "启用/停用 (有物资或下级禁停用)")
    @PostMapping("/{id}/status")
    @SaCheckPermission("MATERIAL_CATEGORY_MANAGE_EDIT")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        categoryService.changeStatus(id, body.get("status"));
        return Result.success();
    }
}
