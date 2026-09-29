package com.medistock.pro.modules.master.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.master.entity.OrgUnit;
import com.medistock.pro.modules.master.service.OrgUnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 组织机构 (P005)
 */
@Tag(name = "组织机构")
@RestController
@RequestMapping("/api/v1/org-units")
@RequiredArgsConstructor
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    @Operation(summary = "组织列表 (前端组树)")
    @GetMapping
    @SaCheckPermission("ORG_MANAGE_VIEW")
    public Result<List<OrgUnit>> list() {
        return Result.success(orgUnitService.listAll());
    }

    @Operation(summary = "启用组织选项 (业务单据选科室, 登录即可)")
    @GetMapping("/options")
    public Result<List<OrgUnit>> options() {
        return Result.success(orgUnitService.listEnabled());
    }

    @Operation(summary = "新增组织")
    @PostMapping
    @SaCheckPermission("ORG_MANAGE_CREATE")
    public Result<OrgUnit> create(@RequestBody OrgUnit unit) {
        return Result.success(orgUnitService.create(unit));
    }

    @Operation(summary = "编辑组织")
    @PutMapping("/{id}")
    @SaCheckPermission("ORG_MANAGE_EDIT")
    public Result<Void> update(@PathVariable Long id, @RequestBody OrgUnit patch) {
        orgUnitService.update(id, patch);
        return Result.success();
    }

    @Operation(summary = "启用/停用 (有下级禁停用)")
    @PostMapping("/{id}/status")
    @SaCheckPermission("ORG_MANAGE_EDIT")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        orgUnitService.changeStatus(id, body.get("status"));
        return Result.success();
    }
}
