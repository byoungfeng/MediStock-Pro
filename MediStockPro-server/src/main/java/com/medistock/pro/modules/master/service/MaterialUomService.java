package com.medistock.pro.modules.master.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.master.entity.MaterialUom;
import com.medistock.pro.modules.master.mapper.MaterialUomMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 包装换算 (P012): 同物资 源单位→目标单位 唯一, 换算率>0 (DDL CHECK 兜底)
 */
@Service
public class MaterialUomService extends ServiceImpl<MaterialUomMapper, MaterialUom> {

    public List<MaterialUom> listByMaterial(Long materialId) {
        return list(new LambdaQueryWrapper<MaterialUom>()
                .eq(materialId != null, MaterialUom::getMaterialId, materialId)
                .orderByAsc(MaterialUom::getId));
    }

    public MaterialUom create(MaterialUom uom) {
        validate(uom);
        Long dup = baseMapper.selectCount(new LambdaQueryWrapper<MaterialUom>()
                .eq(MaterialUom::getMaterialId, uom.getMaterialId())
                .eq(MaterialUom::getFromUom, uom.getFromUom())
                .eq(MaterialUom::getToUom, uom.getToUom()));
        if (dup > 0) {
            throw new BizException(ErrorCode.DUPLICATE_KEY,
                    "换算关系已存在: " + uom.getFromUom() + "→" + uom.getToUom());
        }
        uom.setStatus(1);
        save(uom);
        return uom;
    }

    public void update(Long id, MaterialUom patch) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "换算关系不存在: " + id);
        }
        if (patch.getRate() != null && patch.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "换算率必须大于 0");
        }
        patch.setId(id);
        patch.setMaterialId(null); // 换算三元组创建后不可改, 改即删旧建新
        patch.setFromUom(null);
        patch.setToUom(null);
        updateById(patch);
    }

    public void changeStatus(Long id, Integer status) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "换算关系不存在: " + id);
        }
        MaterialUom update = new MaterialUom();
        update.setId(id);
        update.setStatus(status);
        updateById(update);
    }

    private void validate(MaterialUom uom) {
        if (uom.getMaterialId() == null || uom.getFromUom() == null || uom.getToUom() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "物资/源单位/目标单位必填");
        }
        if (uom.getFromUom().equals(uom.getToUom())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "源单位与目标单位不能相同");
        }
        if (uom.getRate() == null || uom.getRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "换算率必须大于 0");
        }
    }
}
