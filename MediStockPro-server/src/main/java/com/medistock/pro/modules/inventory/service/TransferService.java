package com.medistock.pro.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.inventory.dto.TransferDTO;
import com.medistock.pro.modules.inventory.dto.TransferReceiveDTO;
import com.medistock.pro.modules.inventory.entity.TransferOrder;
import com.medistock.pro.modules.inventory.entity.TransferOrderItem;
import com.medistock.pro.modules.inventory.mapper.TransferOrderItemMapper;
import com.medistock.pro.modules.inventory.mapper.TransferOrderMapper;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 调拨单: DRAFT→PENDING→APPROVED→SHIPPED→RECEIVED / CANCELLED
 * 发运: on_hand→in_transit; 收货: 在途核减 + 调入仓建批; 短收差额按在途损耗核销
 */
@Service
@RequiredArgsConstructor
public class TransferService extends ServiceImpl<TransferOrderMapper, TransferOrder> {

    private final TransferOrderItemMapper itemMapper;
    private final InventoryEngine inventoryEngine;
    private final BillNoGenerator billNoGenerator;
    private final RbacService rbacService;

    private List<Long> scope() {
        return rbacService.currentUserWarehouseScope();
    }

    private void assertTransferScope(Long fromWh, Long toWh) {
        List<Long> s = scope();
        if (s != null && !s.contains(fromWh) && !s.contains(toWh)) {
            throw new BizException(ErrorCode.NOT_FOUND, "无权访问该调拨单");
        }
    }

    public PageResult<TransferOrder> pageQuery(int page, int size, Long fromWarehouseId, Long toWarehouseId, String status) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return PageResult.of(new Page<TransferOrder>(page, size, 0));
        }
        Page<TransferOrder> p = page(new Page<>(page, size), new LambdaQueryWrapper<TransferOrder>()
                .eq(fromWarehouseId != null, TransferOrder::getFromWarehouseId, fromWarehouseId)
                .eq(toWarehouseId != null, TransferOrder::getToWarehouseId, toWarehouseId)
                .eq(status != null && !status.isBlank(), TransferOrder::getStatus, status)
                .and(scope != null, w -> w.in(TransferOrder::getFromWarehouseId, scope).or().in(TransferOrder::getToWarehouseId, scope))
                .orderByDesc(TransferOrder::getId));
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long id) {
        TransferOrder master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "调拨单不存在: " + id);
        }
        assertTransferScope(master.getFromWarehouseId(), master.getToWarehouseId());
        List<TransferOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>().eq(TransferOrderItem::getTransferId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public TransferOrder create(TransferDTO dto) {
        if (dto.fromWarehouseId().equals(dto.toWarehouseId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "调出仓与调入仓不能相同");
        }
        TransferOrder order = new TransferOrder();
        order.setTransferNo(billNoGenerator.next("DB", "transfer_order", "transfer_no"));
        order.setFromWarehouseId(dto.fromWarehouseId());
        order.setToWarehouseId(dto.toWarehouseId());
        order.setReason(dto.reason());
        order.setRemark(dto.remark());
        order.setStatus("DRAFT");
        order.setVersion(0);
        save(order);

        for (TransferDTO.Line line : dto.items()) {
            TransferOrderItem item = new TransferOrderItem();
            item.setTransferId(order.getId());
            item.setMaterialId(line.materialId());
            item.setBatchNo(line.batchNo());
            item.setQty(line.qty());
            item.setShippedQty(0);
            item.setReceivedQty(0);
            itemMapper.insert(item);
        }
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Integer version) {
        guardStatus(id, version, "DRAFT", "PENDING");
    }

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Integer version) {
        guardStatus(id, version, "PENDING", "APPROVED");
    }

    /** 发运: 调出仓 on_hand → in_transit (整单发运) */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long id, Integer version) {
        TransferOrder order = getById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "调拨单不存在: " + id);
        }
        List<TransferOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>().eq(TransferOrderItem::getTransferId, id));
        List<InventoryEngine.OutboundLine> lines = items.stream()
                .map(i -> new InventoryEngine.OutboundLine(i.getMaterialId(), null, i.getBatchNo(), i.getQty(), null))
                .collect(Collectors.toList());
        inventoryEngine.transferShip(order.getFromWarehouseId(), lines, id, order.getTransferNo());

        guardStatus(id, version, "APPROVED", "SHIPPED");
        itemMapper.update(null, new LambdaUpdateWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getTransferId, id)
                .setSql("shipped_qty = qty"));
        update(null, new LambdaUpdateWrapper<TransferOrder>()
                .eq(TransferOrder::getId, id)
                .set(TransferOrder::getShipTime, LocalDateTime.now()));
    }

    /** 收货: 在途核减 + 调入仓建批入账; 短收差额核销为在途损耗 */
    @Transactional(rollbackFor = Exception.class)
    public void receive(Long id, TransferReceiveDTO dto) {
        TransferOrder order = getById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "调拨单不存在: " + id);
        }
        List<TransferOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<TransferOrderItem>().eq(TransferOrderItem::getTransferId, id));
        Map<Long, TransferOrderItem> itemMap = items.stream()
                .collect(Collectors.toMap(TransferOrderItem::getId, i -> i));

        List<InventoryEngine.InboundLine> receiveLines = new java.util.ArrayList<>();
        for (TransferReceiveDTO.Line line : dto.items()) {
            TransferOrderItem item = itemMap.get(line.itemId());
            if (item == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "明细不属于本单: itemId=" + line.itemId());
            }
            if (line.receivedQty() > item.getShippedQty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "实收大于发运: itemId=" + line.itemId());
            }
            int loss = item.getShippedQty() - line.receivedQty();
            if (loss > 0 && (line.diffReason() == null || line.diffReason().isBlank())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "短收必须填写差异原因: itemId=" + line.itemId());
            }
            if (line.receivedQty() > 0) {
                receiveLines.add(new InventoryEngine.InboundLine(item.getMaterialId(), null, item.getBatchNo(),
                        null, null, line.receivedQty(), null));
            }
            if (loss > 0) {
                inventoryEngine.transferLoss(order.getFromWarehouseId(), item.getMaterialId(), item.getBatchNo(),
                        loss, id, order.getTransferNo());
            }
            itemMapper.update(null, new LambdaUpdateWrapper<TransferOrderItem>()
                    .eq(TransferOrderItem::getId, item.getId())
                    .set(TransferOrderItem::getReceivedQty, line.receivedQty())
                    .set(TransferOrderItem::getDiffReason, line.diffReason()));
        }
        if (!receiveLines.isEmpty()) {
            inventoryEngine.transferReceive(order.getFromWarehouseId(), order.getToWarehouseId(),
                    receiveLines, id, order.getTransferNo());
        }
        guardStatus(id, dto.version(), "SHIPPED", "RECEIVED");
        update(null, new LambdaUpdateWrapper<TransferOrder>()
                .eq(TransferOrder::getId, id)
                .set(TransferOrder::getReceiveTime, LocalDateTime.now()));
    }

    /** 取消: 仅发运前允许 (SHIPPED 后货在途中, 必须走收货/异常流程) */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Integer version) {
        TransferOrder order = getById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "调拨单不存在: " + id);
        }
        if (!List.of("DRAFT", "PENDING", "APPROVED").contains(order.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "当前状态不允许取消: " + order.getStatus());
        }
        guardStatus(id, version, order.getStatus(), "CANCELLED");
    }

    private void guardStatus(Long id, Integer version, String expected, String next) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<TransferOrder>()
                .eq(TransferOrder::getId, id)
                .eq(TransferOrder::getVersion, version)
                .eq(TransferOrder::getStatus, expected)
                .set(TransferOrder::getStatus, next)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "调拨单状态已变化, 请刷新后重试");
        }
    }
}
