package com.medistock.pro.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.system.entity.*;
import com.medistock.pro.modules.system.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RBAC 管理: 用户/角色/权限点 查询与装配
 */
@Service
@RequiredArgsConstructor
public class RbacService {

    private final SysUserService sysUserService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleWarehouseMapper roleWarehouseMapper;

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    // ==================== 数据权限 (P008) ====================

    /** 角色的仓库绑定 */
    public List<Long> roleWarehouseIds(Long roleId) {
        return roleWarehouseMapper.selectList(new LambdaQueryWrapper<SysRoleWarehouse>()
                        .eq(SysRoleWarehouse::getRoleId, roleId))
                .stream().map(SysRoleWarehouse::getWarehouseId).toList();
    }

    /** 配置角色数据范围 + 仓库绑定 (全量替换) */
    @Transactional(rollbackFor = Exception.class)
    public void assignDataScope(Long roleId, String dataScope, List<Long> warehouseIds) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在: " + roleId);
        }
        role.setDataScope(StringUtils.hasText(dataScope) ? dataScope : "ALL");
        roleMapper.updateById(role);
        roleWarehouseMapper.delete(new LambdaQueryWrapper<SysRoleWarehouse>()
                .eq(SysRoleWarehouse::getRoleId, roleId));
        if ("WAREHOUSE".equals(role.getDataScope()) && warehouseIds != null) {
            for (Long whId : warehouseIds.stream().distinct().toList()) {
                SysRoleWarehouse rw = new SysRoleWarehouse();
                rw.setRoleId(roleId);
                rw.setWarehouseId(whId);
                roleWarehouseMapper.insert(rw);
            }
        }
    }

    /**
     * 当前登录用户的仓库数据范围:
     * null = 不限制 (任一角色 dataScope=ALL); 否则 = 各 WAREHOUSE 角色绑定仓库的并集
     */
    public List<Long> currentUserWarehouseScope() {
        Object loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return List.of(); // 未登录: 无数据范围
        }
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, Long.parseLong(loginId.toString())))
                .stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        if (roles.stream().anyMatch(r -> "ALL".equals(r.getDataScope()) || !StringUtils.hasText(r.getDataScope()))) {
            return null;
        }
        return roleWarehouseMapper.selectList(new LambdaQueryWrapper<SysRoleWarehouse>()
                        .in(SysRoleWarehouse::getRoleId, roleIds))
                .stream().map(SysRoleWarehouse::getWarehouseId).distinct().toList();
    }

    // ==================== 用户 ====================

    public PageResult<Map<String, Object>> userPage(int page, int size, String keyword) {
        Page<SysUser> p = sysUserService.page(new Page<>(page, size), new LambdaQueryWrapper<SysUser>()
                .and(StringUtils.hasText(keyword), w -> w.like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getName, keyword))
                .orderByAsc(SysUser::getId));
        List<Map<String, Object>> records = p.getRecords().stream().map(u -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("name", u.getName());
            row.put("orgId", u.getOrgId());
            row.put("phone", u.getPhone());
            row.put("status", u.getStatus());
            row.put("roleIds", userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                            .eq(SysUserRole::getUserId, u.getId()))
                    .stream().map(SysUserRole::getRoleId).toList());
            return row;
        }).toList();
        return PageResult.of(p.getTotal(), records);
    }

    @Transactional(rollbackFor = Exception.class)
    public SysUser createUser(String username, String password, String name, Long orgId, List<Long> roleIds) {
        if (sysUserService.getByUsername(username) != null) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "用户名已存在: " + username);
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setEmployeeNo(String.format("%04d", sysUserService.count() + 1)); // 工号非空约束, 自动递增
        user.setPassword(ENCODER.encode(password));
        user.setName(name);
        user.setOrgId(orgId);
        user.setStatus(1);
        sysUserService.save(user);
        assignRoles(user.getId(), roleIds);
        return user;
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    // ==================== 角色 ====================

    public List<Map<String, Object>> roleList() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId))
                .stream().map(r -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", r.getId());
                    row.put("code", r.getCode());
                    row.put("name", r.getName());
                    row.put("dataScope", r.getDataScope());
                    row.put("status", r.getStatus());
                    row.put("remark", r.getRemark());
                    row.put("permIds", rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                                    .eq(RolePermission::getRoleId, r.getId()))
                            .stream().map(RolePermission::getPermissionId).toList());
                    return row;
                }).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public SysRole createRole(String code, String name, String dataScope, String remark, List<Long> permIds) {
        Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
        if (count > 0) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "角色编码已存在: " + code);
        }
        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(name);
        role.setDataScope(StringUtils.hasText(dataScope) ? dataScope : "SELF");
        role.setRemark(remark);
        role.setStatus(1);
        roleMapper.insert(role);
        assignPerms(role.getId(), permIds);
        return role;
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignPerms(Long roleId, List<Long> permIds) {
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
        for (Long permId : permIds) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            rolePermissionMapper.insert(rp);
        }
    }

    // ==================== 权限点 ====================

    public List<SysPermission> permList() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getId));
    }
}
