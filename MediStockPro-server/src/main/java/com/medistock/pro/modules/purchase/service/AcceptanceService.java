package com.medistock.pro.modules.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.inventory.dto.InboundDTO;
import com.medistock.pro.modules.inventory.entity.StockInbound;
import com.medistock.pro.modules.inventory.service.BillNoGenerator;
import com.medistock.pro.modules.inventory.service.StockInboundService;
import com.medistock.pro.modules.purchase.entity.*;
import com.medistock.pro.modules.purchase.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 验收单: 由收货单生成 → 录入验收结果 → 通过时自动生成待确认入库单
 */
@Service
@RequiredArgsConstructor
public class AcceptanceService extends ServiceImpl<AcceptanceMapper, Acceptance> {

    private final AcceptanceItemMapper itemMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptItemMapper receiptItemMapper;
    private final ReceiptService receiptService;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final StockInboundService stockInboundService;
    private final BillNoGenerator billNoGenerator;

    public PageResult<Acceptance> pageQuery(int page, int size, String status) {
        Page<Acceptance> p = page(new Page<>(page, size), new LambdaQueryWrapper<Acceptance>()
                .eq(status != null && !status.isBlank(), Acceptance::getStatus, status)
                .orderByDesc(Acceptance::getId));
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long id) {
        Acceptance master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "验收单不存在: " + id);
        }
        List<AcceptanceItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<AcceptanceItem>().eq(AcceptanceItem::getAcceptanceId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    /** 由收货单生成验收单 (一单一验) */
    @Transactional(rollbackFor = Exception.class)
    public Acceptance createFromReceipt(Long receiptId) {
        Receipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "收货单不存在: " + receiptId);
        }
        if (!"REGISTERED".equals(receipt.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "收货单已验收或状态异常: " + receipt.getStatus());
        }
        List<ReceiptItem> receiptItems = receiptItemMapper.selectList(
                new LambdaQueryWrapper<ReceiptItem>().eq(ReceiptItem::getReceiptId, receiptId));

        Acceptance acceptance = new Acceptance();
        acceptance.setAcceptanceNo(billNoGenerator.next("YS", "acceptance", "acceptance_no"));
        acceptance.setReceiptId(receiptId);
        acceptance.setStatus("PENDING");
        acceptance.setVersion(0);
        save(acceptance);

        for (ReceiptItem ri : receiptItems) {
            AcceptanceItem item = new AcceptanceItem();
            item.setAcceptanceId(acceptance.getId());
            item.setMaterialId(ri.getMaterialId());
            item.setBatchNo(ri.getBatchNo());
            item.setExpiryDate(ri.getExpiryDate());
            item.setReceivedQty(ri.getQty());
            // DDL 约束 chk_ai_qty_balance: accepted+rejected=received 恒成立, 默认全部合格, 验收时按实改
            item.setAcceptedQty(ri.getQty());
            item.setRejectedQty(0);
            itemMapper.insert(item);
        }
        return acceptance;
    }

    /**
     * 验收: 每行 accepted+rejected 必须等于 received; 通过时自动生成待确认入库单 (含拒收则只入合格量)
     */
    @Transactional(rollbackFor = Exception.class)
    public Long act(Long id, Integer version, boolean pass, List<Line> lines, String remark) {
        Acceptance acceptance = getById(id);
        if (acceptance == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "验收单不存在: " + id);
        }
        List<AcceptanceItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<AcceptanceItem>().eq(AcceptanceItem::getAcceptanceId, id));
        Map<Long, AcceptanceItem> byId = items.stream()
                .collect(Collectors.toMap(AcceptanceItem::getId, i -> i));

        for (Line line : lines) {
            AcceptanceItem item = byId.get(line.itemId());
            if (item == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "验收明细不属于本单: itemId=" + line.itemId());
            }
            if (line.acceptedQty() + line.rejectedQty() != item.getReceivedQty()) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "合格+拒收必须等于实收: itemId=" + line.itemId() + ", 实收=" + item.getReceivedQty());
            }
            if (line.rejectedQty() > 0 && (line.rejectReason() == null || line.rejectReason().isBlank())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "拒收必须填写原因: itemId=" + line.itemId());
            }
            itemMapper.update(null, new LambdaUpdateWrapper<AcceptanceItem>()
                    .eq(AcceptanceItem::getId, item.getId())
                    .set(AcceptanceItem::getAcceptedQty, line.acceptedQty())
                    .set(AcceptanceItem::getRejectedQty, line.rejectedQty())
                    .set(AcceptanceItem::getRejectReason, line.rejectReason()));
        }

        Long inboundId = null;
        if (pass) {
            inboundId = createInboundFromAcceptance(acceptance, items, lines);
            receiptService.markStatus(acceptance.getReceiptId(), "REGISTERED", "ACCEPTED");
        } else {
            receiptService.markStatus(acceptance.getReceiptId(), "REGISTERED", "REJECTED");
        }

        int rows = baseMapper.update(null, new LambdaUpdateWrapper<Acceptance>()
                .eq(Acceptance::getId, id)
                .eq(Acceptance::getVersion, version)
                .eq(Acceptance::getStatus, "PENDING")
                .set(Acceptance::getStatus, pass ? "PASSED" : "REJECTED")
                .set(Acceptance::getResult, remark)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "验收单状态已变化, 请刷新后重试");
        }
        return inboundId;
    }

    /** 验收通过 → 生成待确认入库单 (bizType=PURCHASE, 单价取订单价) */
    private Long createInboundFromAcceptance(Acceptance acceptance, List<AcceptanceItem> items, List<Line> lines) {
        Receipt receipt = receiptMapper.selectById(acceptance.getReceiptId());
        PurchaseOrder order = orderMapper.selectById(receipt.getOrderId());
        Map<Long, PurchaseOrderItem> orderItemByMaterial = orderItemMapper.selectList(
                        new LambdaQueryWrapper<PurchaseOrderItem>().eq(PurchaseOrderItem::getOrderId, order.getId()))
                .stream().collect(Collectors.toMap(PurchaseOrderItem::getMaterialId, i -> i, (a, b) -> a));
        Map<Long, Line> lineByItemId = lines.stream().collect(Collectors.toMap(Line::itemId, l -> l));

        List<InboundDTO.Line> inboundLines = new ArrayList<>();
        for (AcceptanceItem item : items) {
            Line line = lineByItemId.get(item.getId());
            if (line == null || line.acceptedQty() == 0) {
                continue;
            }
            PurchaseOrderItem orderItem = orderItemByMaterial.get(item.getMaterialId());
            inboundLines.add(new InboundDTO.Line(item.getMaterialId(), null, item.getBatchNo(),
                    null, item.getExpiryDate(), line.acceptedQty(),
                    orderItem != null ? orderItem.getUnitPrice() : null));
        }
        if (inboundLines.isEmpty()) {
            return null; // 全部拒收: 不生成入库单
        }
        StockInbound inbound = stockInboundService.create(new InboundDTO(
                order.getWarehouseId(), "PURCHASE",
                "验收单 " + acceptance.getAcceptanceNo() + " 自动生成 (采购订单 " + order.getOrderNo() + ")",
                inboundLines));
        return inbound.getId();
    }

    public record Line(Long itemId, Integer acceptedQty, Integer rejectedQty, String rejectReason) {}
}
