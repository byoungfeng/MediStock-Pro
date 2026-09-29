package com.medistock.pro.modules.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.inventory.service.BillNoGenerator;
import com.medistock.pro.modules.purchase.entity.PurchaseOrder;
import com.medistock.pro.modules.purchase.entity.PurchaseOrderItem;
import com.medistock.pro.modules.purchase.mapper.PurchaseOrderItemMapper;
import com.medistock.pro.modules.purchase.mapper.PurchaseOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购订单: DRAFT→PENDING→APPROVED→RECEIVING→COMPLETED / CANCELLED
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> {

    private final PurchaseOrderItemMapper itemMapper;
    private final BillNoGenerator billNoGenerator;
    private final SupplierService supplierService;
    private final PurchaseAgreementService agreementService;
    private final com.medistock.pro.modules.message.MessageService messageService;
    private final com.medistock.pro.modules.approval.ApprovalTrailService approvalTrailService;

    public PageResult<PurchaseOrder> pageQuery(int page, int size, Long supplierId, String status) {
        Page<PurchaseOrder> p = page(new Page<>(page, size), new LambdaQueryWrapper<PurchaseOrder>()
                .eq(supplierId != null, PurchaseOrder::getSupplierId, supplierId)
                .eq(status != null && !status.isBlank(), PurchaseOrder::getStatus, status)
                .orderByDesc(PurchaseOrder::getId));
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long id) {
        PurchaseOrder master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购订单不存在: " + id);
        }
        List<PurchaseOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder create(Long supplierId, Long warehouseId, LocalDate expectDate, String remark, List<Line> items) {
        return create(supplierId, warehouseId, expectDate, remark, items, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder create(Long supplierId, Long warehouseId, LocalDate expectDate, String remark,
                                List<Line> items, Long requestId) {
        return create(supplierId, warehouseId, expectDate, remark, items, requestId, null);
    }

    /**
     * 创建采购订单。agreementId 非空时校验协议(生效/供应商一致/在有效期),
     * 明细单价为 0 或空时取协议价; 物资不在协议内则报错。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder create(Long supplierId, Long warehouseId, LocalDate expectDate, String remark,
                                List<Line> items, Long requestId, Long agreementId) {
        supplierService.assertSupplierQualified(supplierId); // P0: 资质过期/缺失禁采
        Map<Long, BigDecimal> agreementPrices = null;
        if (agreementId != null) {
            agreementPrices = agreementService.resolvePrices(agreementId, supplierId);
        }
        List<Line> resolvedItems = new ArrayList<>(items.size());
        for (Line line : items) {
            BigDecimal price = line.unitPrice();
            if (agreementPrices != null && (price == null || price.signum() == 0)) {
                price = agreementPrices.get(line.materialId());
                if (price == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "物资不在协议价目内: materialId=" + line.materialId());
                }
            }
            if (price == null) {
                price = BigDecimal.ZERO;
            }
            resolvedItems.add(new Line(line.materialId(), line.orderedQty(), price));
        }

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(billNoGenerator.next("CG", "purchase_order", "order_no"));
        order.setRequestId(requestId);
        order.setAgreementId(agreementId);
        order.setSupplierId(supplierId);
        order.setWarehouseId(warehouseId);
        order.setExpectDate(expectDate);
        order.setRemark(remark);
        order.setStatus("DRAFT");
        order.setVersion(0);

        int totalQty = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Line line : resolvedItems) {
            BigDecimal amount = line.unitPrice().multiply(BigDecimal.valueOf(line.orderedQty()));
            totalQty += line.orderedQty();
            totalAmount = totalAmount.add(amount);
        }
        order.setTotalQty(totalQty);
        order.setTotalAmount(totalAmount);
        save(order);

        for (Line line : resolvedItems) {
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setOrderId(order.getId());
            item.setMaterialId(line.materialId());
            item.setOrderedQty(line.orderedQty());
            item.setReceivedQty(0);
            item.setUnitPrice(line.unitPrice());
            item.setAmount(line.unitPrice().multiply(BigDecimal.valueOf(line.orderedQty())));
            itemMapper.insert(item);
        }
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Integer version) {
        guardStatus(id, version, "DRAFT", "PENDING");
        PurchaseOrder order = getById(id);
        approvalTrailService.onSubmit("PURCHASE_ORDER", id, order.getCreatedBy());
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Integer version) {
        guardStatus(id, version, "PENDING", "APPROVED");
        PurchaseOrder order = getById(id);
        approvalTrailService.onAction("PURCHASE_ORDER", id, true, null);
        messageService.notify(order.getCreatedBy(), "APPROVAL",
                "采购订单 " + order.getOrderNo() + " 已审批通过", "供应商可发货, 到货后请登记收货", "PURCHASE_ORDER", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Integer version) {
        PurchaseOrder order = getById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购订单不存在: " + id);
        }
        if (!List.of("DRAFT", "PENDING").contains(order.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "已审批订单不可取消(已有收货在途): " + order.getStatus());
        }
        guardStatus(id, version, order.getStatus(), "CANCELLED");
    }

    /** 收货登记回调: 累加实收并按进度推进状态 (RECEIVING/COMPLETED) */
    @Transactional(rollbackFor = Exception.class)
    public void onReceiptRegistered(Long orderId) {
        List<PurchaseOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, orderId));
        boolean allReceived = items.stream().allMatch(i -> i.getReceivedQty() >= i.getOrderedQty());
        baseMapper.update(null, new LambdaUpdateWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, orderId)
                .in(PurchaseOrder::getStatus, "APPROVED", "RECEIVING")
                .set(PurchaseOrder::getStatus, allReceived ? "COMPLETED" : "RECEIVING")
                .setSql("version = version + 1"));
    }

    void guardStatus(Long id, Integer version, String expected, String next) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, id)
                .eq(PurchaseOrder::getVersion, version)
                .eq(PurchaseOrder::getStatus, expected)
                .set(PurchaseOrder::getStatus, next)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "采购订单状态已变化, 请刷新后重试");
        }
    }

    public record Line(Long materialId, Integer orderedQty, BigDecimal unitPrice) {}
}
