package com.medistock.pro.modules.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medistock.pro.modules.inventory.entity.IssueRequest;
import com.medistock.pro.modules.inventory.entity.StockInbound;
import com.medistock.pro.modules.inventory.entity.TransferOrder;
import com.medistock.pro.modules.inventory.mapper.InventoryBatchMapper;
import com.medistock.pro.modules.inventory.mapper.InventoryTxnMapper;
import com.medistock.pro.modules.inventory.mapper.IssueRequestMapper;
import com.medistock.pro.modules.inventory.mapper.StockInboundMapper;
import com.medistock.pro.modules.inventory.mapper.TransferOrderMapper;
import com.medistock.pro.modules.purchase.entity.Acceptance;
import com.medistock.pro.modules.purchase.entity.PurchaseOrder;
import com.medistock.pro.modules.purchase.entity.PurchaseRequest;
import com.medistock.pro.modules.purchase.mapper.AcceptanceMapper;
import com.medistock.pro.modules.purchase.mapper.PurchaseOrderMapper;
import com.medistock.pro.modules.purchase.mapper.PurchaseRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页仪表盘聚合 (P002): 库存指标 + 待办 + 近7日出入库趋势
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InventoryBatchMapper batchMapper;
    private final InventoryTxnMapper txnMapper;
    private final IssueRequestMapper issueRequestMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final StockInboundMapper stockInboundMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final AcceptanceMapper acceptanceMapper;
    private final com.medistock.pro.modules.system.service.RbacService rbacService;

    @Value("${medistock.expiry-alert-days:90}")
    private int expiryAlertDays;

    public Map<String, Object> overview(Long warehouseId) {
        Map<String, Object> result = new HashMap<>();
        // 数据权限 (P008): 仓库范围
        List<Long> scope = rbacService.currentUserWarehouseScope();
        // 库存指标 (含 totalValue 库存总值)
        result.put("stock", batchMapper.summary(warehouseId, expiryAlertDays, scope));
        // 待办
        Map<String, Object> todo = new LinkedHashMap<>();
        todo.put("issueRequestPending", issueRequestMapper.selectCount(
                new LambdaQueryWrapper<IssueRequest>().eq(IssueRequest::getStatus, "PENDING")
                        .in(scope != null, IssueRequest::getWarehouseId, scope)));
        todo.put("purchaseRequestPending", purchaseRequestMapper.selectCount(
                new LambdaQueryWrapper<PurchaseRequest>().eq(PurchaseRequest::getStatus, "PENDING")));
        todo.put("purchaseOrderPending", purchaseOrderMapper.selectCount(
                new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getStatus, "PENDING")));
        todo.put("inboundPending", stockInboundMapper.selectCount(
                new LambdaQueryWrapper<StockInbound>().eq(StockInbound::getStatus, "PENDING")
                        .in(scope != null, StockInbound::getWarehouseId, scope)));
        todo.put("transferPending", transferOrderMapper.selectCount(
                new LambdaQueryWrapper<TransferOrder>().eq(TransferOrder::getStatus, "PENDING")
                        .and(scope != null, w -> w.in(TransferOrder::getFromWarehouseId, scope).or().in(TransferOrder::getToWarehouseId, scope))));
        todo.put("acceptancePending", acceptanceMapper.selectCount(
                new LambdaQueryWrapper<Acceptance>().eq(Acceptance::getStatus, "PENDING")));
        result.put("todo", todo);
        // 近7日出入库趋势
        List<Map<String, Object>> trend = txnMapper.dailyTrend(warehouseId, 7, scope);
        result.put("trend", trend);
        return result;
    }
}
