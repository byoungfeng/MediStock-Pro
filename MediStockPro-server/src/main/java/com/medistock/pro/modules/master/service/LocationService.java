package com.medistock.pro.modules.master.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.master.entity.Location;
import com.medistock.pro.modules.master.mapper.LocationMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class LocationService extends ServiceImpl<LocationMapper, Location> {

    public List<Location> listByWarehouse(Long warehouseId) {
        return list(new LambdaQueryWrapper<Location>()
                .eq(warehouseId != null, Location::getWarehouseId, warehouseId)
                .orderByAsc(Location::getWarehouseId).orderByAsc(Location::getCode));
    }

    public Location create(Location location) {
        if (location.getWarehouseId() == null || !StringUtils.hasText(location.getCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "仓库与库位编码必填");
        }
        assertCodeUnique(location.getWarehouseId(), location.getCode(), null);
        location.setId(null);
        if (location.getStatus() == null) {
            location.setStatus(1);
        }
        save(location);
        return location;
    }

    public Location update(Long id, Location location) {
        Location existing = getById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "库位不存在: " + id);
        }
        assertCodeUnique(existing.getWarehouseId(), location.getCode(), id);
        location.setId(id);
        location.setWarehouseId(existing.getWarehouseId()); // 库位不允许跨仓迁移
        updateById(location);
        return getById(id);
    }

    /** 停用/启用 */
    public void changeStatus(Long id, Integer status) {
        Location existing = getById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "库位不存在: " + id);
        }
        existing.setStatus(status);
        updateById(existing);
    }

    private void assertCodeUnique(Long warehouseId, String code, Long excludeId) {
        Long count = count(new LambdaQueryWrapper<Location>()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getCode, code)
                .ne(excludeId != null, Location::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "仓内库位编码已存在: " + code);
        }
    }
}
