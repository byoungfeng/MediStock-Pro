package com.medistock.pro.modules.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.auth.dto.LoginDTO;
import com.medistock.pro.modules.system.entity.*;
import com.medistock.pro.modules.system.mapper.*;
import com.medistock.pro.modules.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserService sysUserService;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleWarehouseMapper roleWarehouseMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Map<String, Object> login(LoginDTO dto) {
        SysUser user = sysUserService.getByUsername(dto.getUsername());
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.AUTH_FAILED);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        StpUtil.login(user.getId());

        // 更新最后登录时间
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        sysUserService.updateById(update);

        Map<String, Object> result = new HashMap<>();
        result.put("token", StpUtil.getTokenValue());
        result.put("user", user);
        return result;
    }

    public Map<String, Object> userinfo() {
        Long userId = Long.valueOf(StpUtil.getLoginIdAsString());
        SysUser user = sysUserService.getById(userId);

        // 装配角色
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
        List<SysRole> roles = roleIds.isEmpty() ? List.of() : roleMapper.selectBatchIds(roleIds);

        // 数据范围: 任一角色 ALL 即不限
        String dataScope = roles.stream().map(SysRole::getDataScope)
                .filter(StringUtils::hasText)
                .reduce((a, b) -> "ALL".equals(a) || "ALL".equals(b) ? "ALL" : a)
                .orElse("SELF");

        // 仓库绑定 (仅 WAREHOUSE 范围时有值)
        List<Long> warehouseIds = "WAREHOUSE".equals(dataScope) && !roleIds.isEmpty()
                ? roleWarehouseMapper.selectList(new LambdaQueryWrapper<SysRoleWarehouse>()
                        .in(SysRoleWarehouse::getRoleId, roleIds))
                        .stream().map(SysRoleWarehouse::getWarehouseId).distinct().toList()
                : List.of();

        // 权限码
        List<Long> permIds = roleIds.isEmpty() ? List.of()
                : rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .in(RolePermission::getRoleId, roleIds))
                        .stream().map(RolePermission::getPermissionId).distinct().toList();
        List<String> permissions = permIds.isEmpty() ? List.of()
                : permissionMapper.selectBatchIds(permIds)
                        .stream().map(SysPermission::getPermCode).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("roles", roles.stream().map(r -> {
            Map<String, Object> role = new HashMap<>();
            role.put("id", r.getId());
            role.put("code", r.getCode());
            role.put("name", r.getName());
            role.put("dataScope", r.getDataScope());
            return role;
        }).toList());
        result.put("permissions", permissions);
        result.put("dataScope", dataScope);
        result.put("warehouseIds", warehouseIds);
        return result;
    }

    public void logout() {
        StpUtil.logout();
    }
}