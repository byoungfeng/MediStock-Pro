package com.medistock.pro.modules.master.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.modules.master.entity.Warehouse;
import com.medistock.pro.modules.master.mapper.WarehouseMapper;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 仓库
 */
@Service
@RequiredArgsConstructor
public class WarehouseService extends ServiceImpl<WarehouseMapper, Warehouse> {

    private final RbacService rbacService;

    /** 数据权限 (P008): null 不限; 空列表 = 无任何仓库可见 */
    private List<Long> scope() {
        return rbacService.currentUserWarehouseScope();
    }

    /** 库房树 (按当前用户数据权限过滤) */
    public List<Warehouse> tree() {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .eq(Warehouse::getStatus, 1)
                .in(scope != null, Warehouse::getId, scope)
                .orderByAsc(Warehouse::getCode)
                .list();
    }
}