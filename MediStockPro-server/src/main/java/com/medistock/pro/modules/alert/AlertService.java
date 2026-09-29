package com.medistock.pro.modules.alert;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.alert.mapper.AlertMapper;
import com.medistock.pro.modules.inventory.mapper.InventoryBatchMapper;
import com.medistock.pro.modules.purchase.mapper.AcceptanceItemMapper;
import com.medistock.pro.modules.purchase.mapper.PurchaseOrderMapper;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预警中心 (P047/P048) + 供应商绩效 (P048-报表)
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private final InventoryBatchMapper batchMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final AcceptanceItemMapper acceptanceItemMapper;
    private final AlertMapper alertMapper;
    private final RbacService rbacService;

    private List<Long> scope() {
        return rbacService.currentUserWarehouseScope();
    }

    /** 库存预警: 低库存/断货 (可用 < 安全库存); 同步落 alert 表 */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> stockLevel(Long warehouseId) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return List.of();
        }
        if (scope != null && warehouseId != null && !scope.contains(warehouseId)) {
            return List.of();
        }
        List<Map<String, Object>> rows = batchMapper.stockLevelAlerts(warehouseId);
        if (scope != null && warehouseId == null) {
            rows = rows.stream().filter(r -> scope.contains(((Number) r.get("warehouseId")).longValue())).toList();
        }
        syncAlerts("LOW_STOCK", rows.stream().map(r -> new SyncKey(
                ((Number) r.get("materialId")).longValue() * 100000L + ((Number) r.get("warehouseId")).longValue(),
                "URGENT".equals(r.get("alertType")) || "STOCKOUT".equals(String.valueOf(r.get("alertType"))) ? "URGENT" : "WARN",
                r.get("materialName") + " @ " + r.get("warehouseName"),
                "可用 " + r.get("availableQty") + " < 安全库存 " + r.get("safetyQty"), null)).toList());
        return rows;
    }

    /** 到货预警: 逾期待收货订单; 同步落 alert 表 */
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> arrival() {
        List<Map<String, Object>> rows = purchaseOrderMapper.arrivalAlerts();
        syncAlerts("ARRIVAL", rows.stream().map(r -> new SyncKey(
                ((Number) r.get("id")).longValue(), "WARN",
                "采购订单 " + r.get("orderNo") + " 逾期未齐",
                "供应商 " + r.get("supplierName") + ", 预计到货 " + r.get("expectDate"), null)).toList());
        return rows;
    }

    // ==================== 预警持久化 (M11e) ====================

    private record SyncKey(Long businessId, String level, String title, String content, LocalDateTime dueAt) {
    }

    /** 同步: 新出现的预警补插 OPEN; 条件已消除的 OPEN 预警自动 CLOSED */
    private void syncAlerts(String type, List<SyncKey> current) {
        List<Alert> open = alertMapper.selectList(new LambdaQueryWrapper<Alert>()
                .eq(Alert::getType, type).eq(Alert::getStatus, "OPEN"));
        Set<Long> currentIds = current.stream().map(SyncKey::businessId).collect(Collectors.toCollection(HashSet::new));
        Set<Long> openIds = open.stream().map(Alert::getBusinessId).collect(Collectors.toSet());
        for (SyncKey k : current) {
            if (!openIds.contains(k.businessId())) {
                Alert a = new Alert();
                a.setType(type);
                a.setBusinessId(k.businessId());
                a.setLevel(k.level());
                a.setTitle(k.title().length() > 128 ? k.title().substring(0, 128) : k.title());
                a.setContent(k.content() == null ? null
                        : k.content().length() > 500 ? k.content().substring(0, 500) : k.content());
                a.setDueAt(k.dueAt());
                a.setStatus("OPEN");
                alertMapper.insert(a);
            }
        }
        for (Alert a : open) {
            if (!currentIds.contains(a.getBusinessId())) {
                a.setStatus("CLOSED");
                alertMapper.updateById(a);
            }
        }
    }

    /** 预警记录分页 (含已处置/已忽略/已关闭) */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Alert> pageAlerts(int page, int size,
                                                                                        String type, String status) {
        return alertMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new LambdaQueryWrapper<Alert>()
                        .eq(org.springframework.util.StringUtils.hasText(type), Alert::getType, type)
                        .eq(org.springframework.util.StringUtils.hasText(status), Alert::getStatus, status)
                        .orderByDesc(Alert::getId));
    }

    /** 处置: OPEN -> HANDLED/IGNORED */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String action) {
        Alert a = alertMapper.selectById(id);
        if (a == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "预警不存在: " + id);
        }
        if (!"OPEN".equals(a.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "预警已处置: " + a.getStatus());
        }
        if (!"HANDLED".equals(action) && !"IGNORED".equals(action)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的处置动作: " + action);
        }
        a.setStatus(action);
        Object loginId = StpUtil.getLoginIdDefaultNull();
        a.setHandledBy(loginId == null ? null : Long.parseLong(loginId.toString()));
        a.setHandledAt(LocalDateTime.now());
        alertMapper.updateById(a);
    }

    /** 供应商绩效: 订单规模 + 准时率 + 合格率 */
    public List<Map<String, Object>> supplierPerformance() {
        Map<Long, Map<String, Object>> rows = purchaseOrderMapper.supplierOrderStats().stream()
                .collect(Collectors.toMap(r -> ((Number) r.get("supplierId")).longValue(), Function.identity()));

        for (Map<String, Object> p : purchaseOrderMapper.supplierPunctuality()) {
            Map<String, Object> row = rows.get(((Number) p.get("supplierId")).longValue());
            if (row != null) {
                row.put("receiptCount", p.get("receiptCount"));
                row.put("onTimeCount", p.get("onTimeCount"));
            }
        }
        for (Map<String, Object> q : acceptanceItemMapper.supplierQuality()) {
            Map<String, Object> row = rows.get(((Number) q.get("supplierId")).longValue());
            if (row != null) {
                row.put("receivedQty", q.get("receivedQty"));
                row.put("acceptedQty", q.get("acceptedQty"));
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows.values()) {
            long receipts = num(row.get("receiptCount"));
            long onTime = num(row.get("onTimeCount"));
            long received = num(row.get("receivedQty"));
            long accepted = num(row.get("acceptedQty"));
            // 样本不足时不给比率, 前端展示 "-"
            row.put("onTimeRate", receipts > 0 ? pct(onTime, receipts) : null);
            row.put("qualifiedRate", received > 0 ? pct(accepted, received) : null);
            result.add(row);
        }
        result.sort((a, b) -> new BigDecimal(String.valueOf(b.get("totalAmount")))
                .compareTo(new BigDecimal(String.valueOf(a.get("totalAmount")))));
        return result;
    }

    private long num(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }

    private BigDecimal pct(long part, long total) {
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }
}
