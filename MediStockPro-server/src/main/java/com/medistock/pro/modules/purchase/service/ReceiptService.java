package com.medistock.pro.modules.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.inventory.service.BillNoGenerator;
import com.medistock.pro.modules.purchase.entity.PurchaseOrder;
import com.medistock.pro.modules.purchase.entity.PurchaseOrderItem;
import com.medistock.pro.modules.purchase.entity.Receipt;
import com.medistock.pro.modules.purchase.entity.ReceiptItem;
import com.medistock.pro.modules.purchase.mapper.PurchaseOrderItemMapper;
import com.medistock.pro.modules.purchase.mapper.PurchaseOrderMapper;
import com.medistock.pro.modules.purchase.mapper.ReceiptItemMapper;
import com.medistock.pro.modules.purchase.mapper.ReceiptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收货单 (到货登记): 针对已审批采购订单登记到货批次/数量, 累计订单实收
 */
@Service
@RequiredArgsConstructor
public class ReceiptService extends ServiceImpl<ReceiptMapper, Receipt> {

    private final ReceiptItemMapper itemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final PurchaseOrderService purchaseOrderService;
    private final BillNoGenerator billNoGenerator;

    public PageResult<Receipt> pageQuery(int page, int size, Long orderId, String status) {
        Page<Receipt> p = page(new Page<>(page, size), new LambdaQueryWrapper<Receipt>()
                .eq(orderId != null, Receipt::getOrderId, orderId)
                .eq(status != null && !status.isBlank(), Receipt::getStatus, status)
                .orderByDesc(Receipt::getId));
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long id) {
        Receipt master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "收货单不存在: " + id);
        }
        List<ReceiptItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ReceiptItem>().eq(ReceiptItem::getReceiptId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    /** 到货登记: 仅 APPROVED/RECEIVING 订单可收货; 实收不得超订 */
    @Transactional(rollbackFor = Exception.class)
    public Receipt register(Long orderId, LocalDate arrivalDate, String transportNo, Integer boxCount,
                            String remark, List<Line> lines) {
        PurchaseOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购订单不存在: " + orderId);
        }
        if (!List.of("APPROVED", "RECEIVING").contains(order.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "订单状态不可收货: " + order.getStatus());
        }
        List<PurchaseOrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, orderId));
        Map<Long, PurchaseOrderItem> byMaterial = orderItems.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getMaterialId, i -> i, (a, b) -> a));

        Receipt receipt = new Receipt();
        receipt.setReceiptNo(billNoGenerator.next("SH", "receipt", "receipt_no"));
        receipt.setOrderId(orderId);
        receipt.setArrivalDate(arrivalDate);
        receipt.setTransportNo(transportNo);
        receipt.setBoxCount(boxCount);
        receipt.setRemark(remark);
        receipt.setStatus("REGISTERED");
        receipt.setVersion(0);
        save(receipt);

        for (Line line : lines) {
            PurchaseOrderItem orderItem = byMaterial.get(line.materialId());
            if (orderItem == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "物资不在订单中: materialId=" + line.materialId());
            }
            if (orderItem.getReceivedQty() + line.qty() > orderItem.getOrderedQty()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "实收超订: materialId=" + line.materialId() + ", 剩余可收=" + (orderItem.getOrderedQty() - orderItem.getReceivedQty()));
            }
            ReceiptItem item = new ReceiptItem();
            item.setReceiptId(receipt.getId());
            item.setMaterialId(line.materialId());
            item.setBatchNo(line.batchNo());
            item.setExpiryDate(line.expiryDate());
            item.setQty(line.qty());
            itemMapper.insert(item);

            orderItemMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PurchaseOrderItem>()
                    .eq(PurchaseOrderItem::getId, orderItem.getId())
                    .setSql("received_qty = received_qty + " + line.qty()));
        }
        purchaseOrderService.onReceiptRegistered(orderId);
        return receipt;
    }

    void markStatus(Long id, String expected, String next) {
        baseMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Receipt>()
                .eq(Receipt::getId, id)
                .eq(Receipt::getStatus, expected)
                .set(Receipt::getStatus, next)
                .setSql("version = version + 1"));
    }

    public record Line(Long materialId, String batchNo, LocalDate expiryDate, Integer qty) {}
}
