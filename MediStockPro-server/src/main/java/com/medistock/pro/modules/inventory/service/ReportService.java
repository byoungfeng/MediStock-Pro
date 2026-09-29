package com.medistock.pro.modules.inventory.service;

import com.medistock.pro.modules.inventory.mapper.InventoryBatchMapper;
import com.medistock.pro.modules.inventory.mapper.InventoryTxnMapper;
import com.medistock.pro.modules.master.entity.Material;
import com.medistock.pro.modules.master.mapper.MaterialMapper;
import com.medistock.pro.modules.purchase.mapper.PurchaseOrderMapper;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 报表 (只读): 收发存汇总 + 采购/库存/领用/近效期/变动趋势分析
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final InventoryTxnMapper txnMapper;
    private final InventoryBatchMapper batchMapper;
    private final MaterialMapper materialMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final RbacService rbacService;

    private List<Long> scope() {
        return rbacService.currentUserWarehouseScope();
    }

    private void assertScope(Long warehouseId) {
        List<Long> s = scope();
        if (s != null && warehouseId != null && !s.contains(warehouseId)) {
            throw new com.medistock.pro.common.BizException(com.medistock.pro.common.ErrorCode.NOT_FOUND, "无权访问该仓库数据");
        }
    }

    public List<Map<String, Object>> inoutSummary(Long warehouseId, LocalDate from, LocalDate to) {
        assertScope(warehouseId);
        List<Map<String, Object>> rows = txnMapper.inoutSummary(warehouseId, from, to);
        Map<Long, Material> materials = rows.isEmpty() ? Map.of()
                : materialMapper.selectBatchIds(rows.stream()
                        .map(r -> ((Number) r.get("materialId")).longValue()).distinct().toList())
                .stream().collect(Collectors.toMap(Material::getId, Function.identity()));
        for (Map<String, Object> row : rows) {
            Long materialId = ((Number) row.get("materialId")).longValue();
            Material m = materials.get(materialId);
            row.put("materialCode", m == null ? null : m.getCode());
            row.put("materialName", m == null ? null : m.getName());
            row.put("spec", m == null ? null : m.getSpec());
            long opening = ((Number) row.get("openingQty")).longValue();
            long in = ((Number) row.get("inQty")).longValue();
            long out = ((Number) row.get("outQty")).longValue();
            row.put("closingQty", opening + in - out);
        }
        return rows;
    }

    /** P049 采购分析: 金额/数量趋势 + 供应商 Top10 */
    public Map<String, Object> purchaseAnalysis(LocalDate from, LocalDate to) {
        Map<String, Object> result = new HashMap<>();
        result.put("trend", purchaseOrderMapper.purchaseTrend(from, to));
        result.put("topSuppliers", purchaseOrderMapper.purchaseTopSuppliers(from, to));
        return result;
    }

    /** P050 库存分析: 分类分布 + 物资金额 Top10 */
    public Map<String, Object> inventoryAnalysis(Long warehouseId) {
        assertScope(warehouseId);
        Map<String, Object> result = new HashMap<>();
        result.put("byCategory", batchMapper.inventoryByCategory(warehouseId));
        result.put("topMaterials", batchMapper.inventoryTopMaterials(warehouseId));
        return result;
    }

    /** P051 领用分析: 趋势 + 科室排行 + 物资 Top10 */
    public Map<String, Object> issueAnalysis(Long warehouseId, LocalDate from, LocalDate to) {
        assertScope(warehouseId);
        Map<String, Object> result = new HashMap<>();
        result.put("trend", txnMapper.issueTrend(warehouseId, from, to));
        result.put("byDepartment", txnMapper.issueByDepartment(warehouseId, from, to));
        result.put("topMaterials", txnMapper.issueTopMaterials(warehouseId, from, to));
        return result;
    }

    /** P052 近效期分析: 分档数量/金额分布 */
    public List<Map<String, Object>> expiryAnalysis(Long warehouseId) {
        assertScope(warehouseId);
        return batchMapper.expiryDistribution(warehouseId);
    }

    /** P054 库存变动趋势: 近 N 日按业务类型净变动 */
    public List<Map<String, Object>> movementTrend(Long warehouseId, int days) {
        assertScope(warehouseId);
        return txnMapper.movementTrend(warehouseId, days);
    }
}
