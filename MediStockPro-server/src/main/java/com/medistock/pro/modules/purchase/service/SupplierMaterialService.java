package com.medistock.pro.modules.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.master.entity.Material;
import com.medistock.pro.modules.master.mapper.MaterialMapper;
import com.medistock.pro.modules.purchase.entity.SupplierMaterial;
import com.medistock.pro.modules.purchase.mapper.SupplierMaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 供应商物资报价 (P015): (供应商,物资) 唯一, 调价直接覆盖 updated_at 留痕时间
 */
@Service
@RequiredArgsConstructor
public class SupplierMaterialService extends ServiceImpl<SupplierMaterialMapper, SupplierMaterial> {

    private final MaterialMapper materialMapper;

    /** 按供应商列出报价 (附物资编码/名称) */
    public List<Map<String, Object>> listBySupplier(Long supplierId) {
        List<SupplierMaterial> rows = list(new LambdaQueryWrapper<SupplierMaterial>()
                .eq(supplierId != null, SupplierMaterial::getSupplierId, supplierId)
                .orderByAsc(SupplierMaterial::getId));
        Map<Long, Material> materials = rows.isEmpty() ? Map.of()
                : materialMapper.selectBatchIds(rows.stream().map(SupplierMaterial::getMaterialId).distinct().toList())
                .stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        return rows.stream().map(r -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", r.getId());
            row.put("supplierId", r.getSupplierId());
            row.put("materialId", r.getMaterialId());
            Material m = materials.get(r.getMaterialId());
            row.put("materialCode", m == null ? null : m.getCode());
            row.put("materialName", m == null ? null : m.getName());
            row.put("spec", m == null ? null : m.getSpec());
            row.put("price", r.getPrice());
            row.put("taxRate", r.getTaxRate());
            row.put("leadDays", r.getLeadDays());
            row.put("moq", r.getMoq());
            row.put("status", r.getStatus());
            row.put("updatedAt", r.getUpdatedAt());
            return row;
        }).toList();
    }

    /** 新增报价: (供应商,物资) 唯一, 重复走 update */
    public SupplierMaterial create(SupplierMaterial sm) {
        if (sm.getSupplierId() == null || sm.getMaterialId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "供应商/物资必填");
        }
        Long dup = baseMapper.selectCount(new LambdaQueryWrapper<SupplierMaterial>()
                .eq(SupplierMaterial::getSupplierId, sm.getSupplierId())
                .eq(SupplierMaterial::getMaterialId, sm.getMaterialId()));
        if (dup > 0) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "该物资报价已存在, 请直接调价");
        }
        sm.setStatus(1);
        save(sm);
        return sm;
    }

    /** 调价/交期/MOQ/税率 (三元组不可改) */
    public void update(Long id, SupplierMaterial patch) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报价不存在: " + id);
        }
        patch.setId(id);
        patch.setSupplierId(null);
        patch.setMaterialId(null);
        updateById(patch);
    }

    public void changeStatus(Long id, Integer status) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报价不存在: " + id);
        }
        SupplierMaterial update = new SupplierMaterial();
        update.setId(id);
        update.setStatus(status);
        updateById(update);
    }
}
