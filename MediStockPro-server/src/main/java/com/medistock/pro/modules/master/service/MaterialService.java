package com.medistock.pro.modules.master.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.master.entity.Material;
import com.medistock.pro.modules.master.mapper.MaterialMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物资主数据
 */
@Service
public class MaterialService extends ServiceImpl<MaterialMapper, Material> {

    public Page<Material> pageQuery(int page, int size, String keyword, Long categoryId, Integer status) {
        return lambdaQuery()
                .and(StringUtils.hasText(keyword), q -> q
                        .like(Material::getCode, keyword)
                        .or().like(Material::getName, keyword))
                .eq(categoryId != null, Material::getCategoryId, categoryId)
                .eq(status != null, Material::getStatus, status)
                .orderByDesc(Material::getCreatedAt)
                .page(new Page<>(page, size));
    }

    public void create(Material material) {
        assertCodeUnique(material.getCode(), null);
        save(material);
    }

    public void update(Material material) {
        assertCodeUnique(material.getCode(), material.getId());
        updateById(material);
    }

    /** 编码唯一 (P0: 幂等基石, DB 唯一索引兜底) */
    private void assertCodeUnique(String code, Long excludeId) {
        boolean exists = lambdaQuery()
                .eq(Material::getCode, code)
                .ne(excludeId != null, Material::getId, excludeId)
                .exists();
        if (exists) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "物资编码已存在: " + code);
        }
    }

    // ==================== 批量属性修改 (P011) ====================

    @Autowired
    @Lazy
    private com.medistock.pro.modules.inventory.mapper.InventoryBatchMapper inventoryBatchMapper;

    @Autowired
    @Lazy
    private com.medistock.pro.modules.inventory.mapper.InventoryTxnMapper inventoryTxnMapper;

    /**
     * 删除前引用校验: 已产生库存批次或库存流水(任何已执行单据都会写流水)的物资禁止删除。
     */
    public void removeWithRefCheck(Long id) {
        Long batchCnt = inventoryBatchMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.medistock.pro.modules.inventory.entity.InventoryBatch>()
                .eq(com.medistock.pro.modules.inventory.entity.InventoryBatch::getMaterialId, id));
        if (batchCnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该物资已存在库存批次记录, 禁止删除");
        }
        Long txnCnt = inventoryTxnMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.medistock.pro.modules.inventory.entity.InventoryTxn>()
                .eq(com.medistock.pro.modules.inventory.entity.InventoryTxn::getMaterialId, id));
        if (txnCnt > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该物资已被业务单据引用, 禁止删除");
        }
        removeById(id);
    }

    public record BatchAttrUpdate(List<Long> ids, Integer isHighValue, Integer batchManaged,
                                  Integer expiryManaged, Integer udiManaged, Integer safetyQty,
                                  Integer maxQty, Long categoryId) {
    }

    public record BatchAttrResult(int updated, List<Map<String, Object>> conflicts) {
    }

    /**
     * 批量修改物资属性 (冲突检测):
     * 关闭批号/效期管理时, 若存在带批号/效期的在库批次 -> 冲突跳过, 其余照常更新。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public BatchAttrResult batchUpdateAttrs(BatchAttrUpdate req) {
        if (req.ids() == null || req.ids().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "物资 ids 必填");
        }
        List<Map<String, Object>> conflicts = new ArrayList<>();
        int updated = 0;
        for (Long id : req.ids().stream().distinct().toList()) {
            Material m = getById(id);
            if (m == null) {
                continue;
            }
            String conflict = detectConflict(m, req);
            if (conflict != null) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("materialId", m.getId());
                c.put("code", m.getCode());
                c.put("name", m.getName());
                c.put("reason", conflict);
                conflicts.add(c);
                continue;
            }
            if (req.isHighValue() != null) m.setIsHighValue(req.isHighValue());
            if (req.batchManaged() != null) m.setBatchManaged(req.batchManaged());
            if (req.expiryManaged() != null) m.setExpiryManaged(req.expiryManaged());
            if (req.udiManaged() != null) m.setUdiManaged(req.udiManaged());
            if (req.safetyQty() != null) m.setSafetyQty(req.safetyQty());
            if (req.maxQty() != null) m.setMaxQty(req.maxQty());
            if (req.categoryId() != null) m.setCategoryId(req.categoryId());
            updateById(m);
            updated++;
        }
        return new BatchAttrResult(updated, conflicts);
    }

    /** 返回冲突原因; 无冲突返回 null */
    private String detectConflict(Material m, BatchAttrUpdate req) {
        if (Integer.valueOf(0).equals(req.batchManaged()) && Integer.valueOf(1).equals(m.getBatchManaged())) {
            Long cnt = inventoryBatchMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.medistock.pro.modules.inventory.entity.InventoryBatch>()
                    .eq(com.medistock.pro.modules.inventory.entity.InventoryBatch::getMaterialId, m.getId())
                    .gt(com.medistock.pro.modules.inventory.entity.InventoryBatch::getOnHand, 0));
            if (cnt > 0) {
                return "存在 " + cnt + " 个在库批次, 不可关闭批号管理";
            }
        }
        if (Integer.valueOf(0).equals(req.expiryManaged()) && Integer.valueOf(1).equals(m.getExpiryManaged())) {
            Long cnt = inventoryBatchMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.medistock.pro.modules.inventory.entity.InventoryBatch>()
                    .eq(com.medistock.pro.modules.inventory.entity.InventoryBatch::getMaterialId, m.getId())
                    .gt(com.medistock.pro.modules.inventory.entity.InventoryBatch::getOnHand, 0)
                    .isNotNull(com.medistock.pro.modules.inventory.entity.InventoryBatch::getExpiryDate));
            if (cnt > 0) {
                return "存在 " + cnt + " 个带效期的在库批次, 不可关闭效期管理";
            }
        }
        return null;
    }
}
