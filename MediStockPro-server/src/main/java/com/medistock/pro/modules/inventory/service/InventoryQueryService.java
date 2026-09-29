package com.medistock.pro.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.inventory.entity.InventoryBatch;
import com.medistock.pro.modules.inventory.entity.InventoryTxn;
import com.medistock.pro.modules.inventory.mapper.InventoryBatchMapper;
import com.medistock.pro.modules.inventory.mapper.InventoryTxnMapper;
import com.medistock.pro.modules.master.entity.Material;
import com.medistock.pro.modules.master.entity.Warehouse;
import com.medistock.pro.modules.master.mapper.MaterialMapper;
import com.medistock.pro.modules.master.mapper.WarehouseMapper;
import com.medistock.pro.modules.system.entity.SysUser;
import com.medistock.pro.modules.system.mapper.SysUserMapper;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存查询 (只读): 总览/明细/批次/流水
 */
@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private final InventoryBatchMapper batchMapper;
    private final InventoryTxnMapper txnMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final SysUserMapper sysUserMapper;
    private final RbacService rbacService;
    private final com.medistock.pro.modules.master.service.MaterialCategoryService materialCategoryService;

    @Value("${medistock.expiry-alert-days:90}")
    private int expiryAlertDays;

    /** 数据权限 (P008): null 不限; 空列表 = 无任何仓库可见 */
    private List<Long> scope() {
        return rbacService.currentUserWarehouseScope();
    }

    public Map<String, Object> summary(Long warehouseId) {
        Map<String, Object> summary = new HashMap<>(batchMapper.summary(warehouseId, expiryAlertDays, scope()));
        summary.put("expiryAlertDays", expiryAlertDays);
        return summary;
    }

    /** 库存明细: 批次分页 + 物资/仓库名称 (Java 侧组装, 避免大 join 分页); categoryId 含后代分类 */
    public Page<Map<String, Object>> details(int page, int size, Long warehouseId, Long categoryId, String keyword, String stockState) {
        List<Long> categoryMaterialIds = null;
        if (categoryId != null) {
            List<Long> categoryIds = materialCategoryService.selfAndDescendantIds(categoryId);
            categoryMaterialIds = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                            .in(Material::getCategoryId, categoryIds))
                    .stream().map(Material::getId).toList();
            if (categoryMaterialIds.isEmpty()) {
                return new Page<>(page, size, 0);
            }
        }
        Page<InventoryBatch> batchPage = batchPage(page, size, warehouseId, keyword, null, stockState, categoryMaterialIds);
        return assemble(batchPage);
    }

    public Page<Map<String, Object>> batches(int page, int size, Long warehouseId, String keyword, String status) {
        Page<InventoryBatch> batchPage = batchPage(page, size, warehouseId, keyword, status, null, null);
        return assemble(batchPage);
    }

    private Page<InventoryBatch> batchPage(int page, int size, Long warehouseId, String keyword,
                                           String status, String stockState, List<Long> categoryMaterialIds) {
        List<Long> materialIds = categoryMaterialIds;
        if (StringUtils.hasText(keyword)) {
            List<Long> keywordIds = materialMapper.selectList(new LambdaQueryWrapper<Material>()
                            .like(Material::getCode, keyword).or().like(Material::getName, keyword))
                    .stream().map(Material::getId).toList();
            if (keywordIds.isEmpty()) {
                return new Page<>(page, size, 0);
            }
            // 关键词与分类取交集
            materialIds = materialIds == null ? keywordIds
                    : keywordIds.stream().filter(new java.util.HashSet<>(materialIds)::contains).toList();
            if (materialIds.isEmpty()) {
                return new Page<>(page, size, 0);
            }
        }
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return new Page<>(page, size, 0); // 无仓库数据权限
        }
        LambdaQueryWrapper<InventoryBatch> qw = new LambdaQueryWrapper<InventoryBatch>()
                .eq(warehouseId != null, InventoryBatch::getWarehouseId, warehouseId)
                .in(scope != null, InventoryBatch::getWarehouseId, scope)
                .eq(StringUtils.hasText(status), InventoryBatch::getStatus, status)
                .in(materialIds != null, InventoryBatch::getMaterialId, materialIds)
                .orderByAsc(InventoryBatch::getExpiryDate)
                .orderByDesc(InventoryBatch::getId);
        // 库存状态筛选: LOW不足 / NEAR_EXPIRY近效期 / OVER超储
        if ("LOW".equals(stockState)) {
            qw.apply("on_hand - locked_qty < COALESCE((SELECT safety_qty FROM material WHERE id = material_id), 0)");
        } else if ("NEAR_EXPIRY".equals(stockState)) {
            qw.apply("expiry_date IS NOT NULL AND on_hand > 0 AND expiry_date <= DATE_ADD(CURDATE(), INTERVAL " + expiryAlertDays + " DAY)");
        }
        return batchMapper.selectPage(new Page<>(page, size), qw);
    }

    private Page<Map<String, Object>> assemble(Page<InventoryBatch> batchPage) {
        List<InventoryBatch> records = batchPage.getRecords();
        Map<Long, Material> materials = records.isEmpty() ? Map.of()
                : materialMapper.selectBatchIds(records.stream().map(InventoryBatch::getMaterialId).distinct().toList())
                .stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        Map<Long, Warehouse> warehouses = records.isEmpty() ? Map.of()
                : warehouseMapper.selectBatchIds(records.stream().map(InventoryBatch::getWarehouseId).distinct().toList())
                .stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));

        Page<Map<String, Object>> result = new Page<>(batchPage.getCurrent(), batchPage.getSize(), batchPage.getTotal());
        result.setRecords(records.stream().map(b -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", b.getId());
            row.put("warehouseId", b.getWarehouseId());
            row.put("warehouseName", warehouses.getOrDefault(b.getWarehouseId(), new Warehouse()).getName());
            row.put("materialId", b.getMaterialId());
            Material m = materials.get(b.getMaterialId());
            row.put("materialCode", m == null ? null : m.getCode());
            row.put("materialName", m == null ? null : m.getName());
            row.put("spec", m == null ? null : m.getSpec());
            row.put("manufacturer", m == null ? null : m.getManufacturer());
            row.put("safetyQty", m == null ? null : m.getSafetyQty());
            row.put("batchNo", b.getBatchNo());
            row.put("productionDate", b.getProductionDate());
            row.put("expiryDate", b.getExpiryDate());
            row.put("onHand", b.getOnHand());
            row.put("lockedQty", b.getLockedQty());
            row.put("availableQty", b.getOnHand() - b.getLockedQty());
            row.put("inTransitQty", b.getInTransitQty());
            row.put("unitCost", b.getUnitCost());
            row.put("status", b.getStatus());
            row.put("version", b.getVersion());
            return row;
        }).toList());
        return result;
    }

    /** 批次冻结/解冻: 冻结批次不进 FEFO 推荐 */
    public void freeze(Long id, boolean freeze) {
        InventoryBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "批次不存在: " + id);
        }
        int rows = batchMapper.update(null, new LambdaUpdateWrapper<InventoryBatch>()
                .eq(InventoryBatch::getId, id)
                .eq(InventoryBatch::getVersion, batch.getVersion())
                .in(InventoryBatch::getStatus, "NORMAL", "FROZEN")
                .set(InventoryBatch::getStatus, freeze ? "FROZEN" : "NORMAL")
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "批次状态已变化, 请刷新后重试");
        }
    }

    /** 效期预警: 已过期 + N 天内到期且有库存的批次, 按剩余天数升序 */
    public List<Map<String, Object>> expiryAlerts(Long warehouseId, Integer days) {
        int d = days != null ? days : expiryAlertDays;
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return List.of();
        }
        List<InventoryBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<InventoryBatch>()
                .eq(warehouseId != null, InventoryBatch::getWarehouseId, warehouseId)
                .in(scope != null, InventoryBatch::getWarehouseId, scope)
                .isNotNull(InventoryBatch::getExpiryDate)
                .gt(InventoryBatch::getOnHand, 0)
                .le(InventoryBatch::getExpiryDate, java.time.LocalDate.now().plusDays(d))
                .orderByAsc(InventoryBatch::getExpiryDate));
        Page<InventoryBatch> wrapper = new Page<>(1, Math.max(batches.size(), 1), batches.size());
        wrapper.setRecords(batches);
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Map<String, Object>> rows = new java.util.ArrayList<>(assemble(wrapper).getRecords());
        for (Map<String, Object> row : rows) {
            java.time.LocalDate expiry = (java.time.LocalDate) row.get("expiryDate");
            long daysToExpiry = java.time.temporal.ChronoUnit.DAYS.between(today, expiry);
            row.put("daysToExpiry", daysToExpiry);
            row.put("bucket", daysToExpiry < 0 ? "EXPIRED"
                    : daysToExpiry <= 30 ? "D30"
                    : daysToExpiry <= 60 ? "D60" : "D90");
        }
        return rows;
    }

    public Page<Map<String, Object>> transactions(int page, int size, Long warehouseId, Long materialId,
                                                  String sourceType, String sourceNo) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return new Page<>(page, size, 0);
        }
        Page<InventoryTxn> txnPage = txnMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<InventoryTxn>()
                        .eq(warehouseId != null, InventoryTxn::getWarehouseId, warehouseId)
                        .in(scope != null, InventoryTxn::getWarehouseId, scope)
                        .eq(materialId != null, InventoryTxn::getMaterialId, materialId)
                        .eq(StringUtils.hasText(sourceType), InventoryTxn::getSourceType, sourceType)
                        .like(StringUtils.hasText(sourceNo), InventoryTxn::getSourceNo, sourceNo)
                        .orderByDesc(InventoryTxn::getId));
        List<InventoryTxn> records = txnPage.getRecords();
        Map<Long, Material> materials = records.isEmpty() ? Map.of()
                : materialMapper.selectBatchIds(records.stream().map(InventoryTxn::getMaterialId).distinct().toList())
                .stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        Map<Long, Warehouse> warehouses = records.isEmpty() ? Map.of()
                : warehouseMapper.selectBatchIds(records.stream().map(InventoryTxn::getWarehouseId).distinct().toList())
                .stream().collect(Collectors.toMap(Warehouse::getId, Function.identity()));
        Map<Long, SysUser> users = records.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(records.stream().map(InventoryTxn::getOperatorId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Page<Map<String, Object>> result = new Page<>(txnPage.getCurrent(), txnPage.getSize(), txnPage.getTotal());
        result.setRecords(records.stream().map(t -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", t.getId());
            row.put("txnNo", t.getTxnNo());
            row.put("createdAt", t.getCreatedAt());
            row.put("sourceType", t.getSourceType());
            row.put("sourceNo", t.getSourceNo());
            row.put("direction", t.getDirection());
            row.put("qty", t.getQty());
            row.put("beforeQty", t.getBeforeQty());
            row.put("afterQty", t.getAfterQty());
            row.put("batchNo", t.getBatchNo());
            Material m = materials.get(t.getMaterialId());
            row.put("materialCode", m == null ? null : m.getCode());
            row.put("materialName", m == null ? null : m.getName());
            Warehouse w = warehouses.get(t.getWarehouseId());
            row.put("warehouseName", w == null ? null : w.getName());
            SysUser u = users.get(t.getOperatorId());
            row.put("operatorName", u == null ? null : u.getName());
            return row;
        }).toList());
        return result;
    }
}
