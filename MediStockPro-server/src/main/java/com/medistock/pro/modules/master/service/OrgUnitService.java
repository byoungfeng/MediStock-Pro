package com.medistock.pro.modules.master.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.master.entity.OrgUnit;
import com.medistock.pro.modules.master.mapper.OrgUnitMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 组织机构 (P005): 树形 CRUD, 编码唯一, 有下级禁停用
 */
@Service
public class OrgUnitService extends ServiceImpl<OrgUnitMapper, OrgUnit> {

    /** 全量列表 (前端组树; 组织量级小无需分页) */
    public List<OrgUnit> listAll() {
        return list(new LambdaQueryWrapper<OrgUnit>().orderByAsc(OrgUnit::getCode));
    }

    /** 启用组织下拉选项 (业务单据选科室用, 登录即可) */
    public List<OrgUnit> listEnabled() {
        return list(new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getStatus, 1)
                .orderByAsc(OrgUnit::getCode));
    }

    public OrgUnit create(OrgUnit unit) {
        Long dup = baseMapper.selectCount(new LambdaQueryWrapper<OrgUnit>().eq(OrgUnit::getCode, unit.getCode()));
        if (dup > 0) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "组织编码已存在: " + unit.getCode());
        }
        if (unit.getParentId() != null && getById(unit.getParentId()) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "上级组织不存在: " + unit.getParentId());
        }
        unit.setStatus(1);
        save(unit);
        return unit;
    }

    public void update(Long id, OrgUnit patch) {
        OrgUnit existing = getById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织不存在: " + id);
        }
        if (patch.getParentId() != null && patch.getParentId().equals(id)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上级组织不能是自身");
        }
        patch.setId(id);
        patch.setCode(null); // 编码创建后不可改
        updateById(patch);
    }

    /** 停用: 有有效下级时禁止 */
    public void changeStatus(Long id, Integer status) {
        OrgUnit existing = getById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "组织不存在: " + id);
        }
        if (status != null && status == 0) {
            Long children = baseMapper.selectCount(new LambdaQueryWrapper<OrgUnit>()
                    .eq(OrgUnit::getParentId, id).eq(OrgUnit::getStatus, 1));
            if (children > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "存在有效下级组织, 禁止停用");
            }
        }
        OrgUnit update = new OrgUnit();
        update.setId(id);
        update.setStatus(status);
        updateById(update);
    }
}
