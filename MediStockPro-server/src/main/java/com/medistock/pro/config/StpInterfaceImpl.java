package com.medistock.pro.config;

import cn.dev33.satoken.stp.StpInterface;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medistock.pro.modules.system.entity.RolePermission;
import com.medistock.pro.modules.system.entity.SysPermission;
import com.medistock.pro.modules.system.entity.SysRole;
import com.medistock.pro.modules.system.entity.SysUserRole;
import com.medistock.pro.modules.system.mapper.RolePermissionMapper;
import com.medistock.pro.modules.system.mapper.SysPermissionMapper;
import com.medistock.pro.modules.system.mapper.SysRoleMapper;
import com.medistock.pro.modules.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限装配: 用户 → 角色 → 权限点 (DB 驱动, 状态启用才生效)
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<Long> roleIds = activeRoleIds(loginId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> permIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .in(RolePermission::getRoleId, roleIds))
                .stream().map(RolePermission::getPermissionId).distinct().toList();
        if (permIds.isEmpty()) {
            return Collections.emptyList();
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .in(SysPermission::getId, permIds)
                        .eq(SysPermission::getStatus, 1))
                .stream().map(SysPermission::getPermCode).distinct().toList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<Long> roleIds = activeRoleIds(loginId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getId, roleIds)
                        .eq(SysRole::getStatus, 1))
                .stream().map(SysRole::getCode).toList();
    }

    private List<Long> activeRoleIds(Object loginId) {
        Long userId = Long.valueOf(String.valueOf(loginId));
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).distinct().toList();
    }
}
