package com.medistock.pro.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.system.entity.SysPermission;
import com.medistock.pro.modules.system.entity.SysRole;
import com.medistock.pro.modules.system.entity.SysUser;
import com.medistock.pro.modules.system.service.RbacService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统管理: 用户/角色/权限点
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class RbacController {

    private final RbacService rbacService;

    // ==================== 用户 ====================

    @GetMapping("/users")
    @SaCheckPermission("USER_MANAGE_VIEW")
    public Result<PageResult<Map<String, Object>>> userPage(@RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "10") int size,
                                                            @RequestParam(required = false) String keyword) {
        return Result.success(rbacService.userPage(page, size, keyword));
    }

    @PostMapping("/users")
    @SaCheckPermission("USER_MANAGE_EDIT")
    public Result<SysUser> createUser(@Valid @RequestBody CreateUserReq req) {
        return Result.success(rbacService.createUser(req.username(), req.password(), req.name(),
                req.orgId(), req.roleIds() == null ? List.of() : req.roleIds()));
    }

    @PutMapping("/users/{id}/roles")
    @SaCheckPermission("USER_MANAGE_EDIT")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignReq req) {
        rbacService.assignRoles(id, req.ids());
        return Result.success();
    }

    // ==================== 角色 ====================

    @GetMapping("/roles")
    @SaCheckPermission("ROLE_MANAGE_VIEW")
    public Result<List<Map<String, Object>>> roleList() {
        return Result.success(rbacService.roleList());
    }

    @PostMapping("/roles")
    @SaCheckPermission("ROLE_MANAGE_EDIT")
    public Result<SysRole> createRole(@Valid @RequestBody CreateRoleReq req) {
        return Result.success(rbacService.createRole(req.code(), req.name(), req.dataScope(),
                req.remark(), req.permIds() == null ? List.of() : req.permIds()));
    }

    @PutMapping("/roles/{id}/perms")
    @SaCheckPermission("ROLE_MANAGE_EDIT")
    public Result<Void> assignPerms(@PathVariable Long id, @Valid @RequestBody AssignReq req) {
        rbacService.assignPerms(id, req.ids());
        return Result.success();
    }

    // ==================== 数据权限 (P008) ====================

    @GetMapping("/roles/{id}/warehouses")
    @SaCheckPermission("ROLE_MANAGE_VIEW")
    public Result<List<Long>> roleWarehouses(@PathVariable Long id) {
        return Result.success(rbacService.roleWarehouseIds(id));
    }

    @PutMapping("/roles/{id}/data-scope")
    @SaCheckPermission("DATA_SCOPE_MANAGE")
    public Result<Void> assignDataScope(@PathVariable Long id, @RequestBody DataScopeReq req) {
        rbacService.assignDataScope(id, req.dataScope(), req.warehouseIds() == null ? List.of() : req.warehouseIds());
        return Result.success();
    }

    // ==================== 权限点 ====================

    @GetMapping("/permissions")
    @SaCheckPermission("ROLE_MANAGE_VIEW")
    public Result<List<SysPermission>> permList() {
        return Result.success(rbacService.permList());
    }

    public record CreateUserReq(@NotBlank String username, @NotBlank String password,
                                @NotBlank String name, Long orgId, List<Long> roleIds) {}

    public record CreateRoleReq(@NotBlank String code, @NotBlank String name,
                                String dataScope, String remark, List<Long> permIds) {}

    public record AssignReq(@NotNull List<Long> ids) {}

    public record DataScopeReq(String dataScope, List<Long> warehouseIds) {}
}
