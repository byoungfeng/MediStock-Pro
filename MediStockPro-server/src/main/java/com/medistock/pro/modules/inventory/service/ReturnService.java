package com.medistock.pro.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.inventory.entity.ReturnOrder;
import com.medistock.pro.modules.inventory.entity.ReturnOrderItem;
import com.medistock.pro.modules.inventory.mapper.ReturnOrderItemMapper;
import com.medistock.pro.modules.inventory.mapper.ReturnOrderMapper;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 科室退库单: 创建(DRAFT) → 确认(退回入库, on_hand 增加) → CONFIRMED / CANCELLED
 */
@Service
@RequiredArgsConstructor
public class ReturnService extends ServiceImpl<ReturnOrderMapper, ReturnOrder> {

    private final ReturnOrderItemMapper itemMapper;
    private final InventoryEngine inventoryEngine;
    private final BillNoGenerator billNoGenerator;
    private final RbacService rbacService;

    private List<Long> scope() {
        return rbacService.currentUserWarehouseScope();
    }

    private void assertScope(Long warehouseId) {
        List<Long> s = scope();
        if (s != null && !s.contains(warehouseId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "无权访问该仓库数据");
        }
    }

    public PageResult<ReturnOrder> pageQuery(int page, int size, Long warehouseId, String status) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return PageResult.of(new Page<ReturnOrder>(page, size, 0));
        }
        Page<ReturnOrder> p = page(new Page<>(page, size), new LambdaQueryWrapper<ReturnOrder>()
                .eq(warehouseId != null, ReturnOrder::getWarehouseId, warehouseId)
                .in(scope != null, ReturnOrder::getWarehouseId, scope)
                .eq(status != null && !status.isBlank(), ReturnOrder::getStatus, status)
                .orderByDesc(ReturnOrder::getId));
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long id) {
        ReturnOrder master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "退库单不存在: " + id);
        }
        assertScope(master.getWarehouseId());
        List<ReturnOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReturnOrder create(Long issueId, Long warehouseId, String reason, List<Line> items) {
        ReturnOrder order = new ReturnOrder();
        order.setReturnNo(billNoGenerator.next("TK", "return_order", "return_no"));
        order.setIssueId(issueId);
        order.setWarehouseId(warehouseId);
        order.setReason(reason);
        order.setStatus("DRAFT");
        order.setVersion(0);
        save(order);
        for (Line line : items) {
            ReturnOrderItem item = new ReturnOrderItem();
            item.setReturnId(order.getId());
            item.setMaterialId(line.materialId());
            item.setBatchNo(line.batchNo());
            item.setQty(line.qty());
            itemMapper.insert(item);
        }
        return order;
    }

    /** 确认退库: 退回入库 (批次已存在则加回; 不存在则按无库位重建) */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Integer version) {
        ReturnOrder order = getById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "退库单不存在: " + id);
        }
        List<ReturnOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ReturnOrderItem>().eq(ReturnOrderItem::getReturnId, id));
        List<InventoryEngine.InboundLine> lines = items.stream()
                .map(i -> new InventoryEngine.InboundLine(i.getMaterialId(), null, i.getBatchNo(),
                        null, null, i.getQty(), null))
                .collect(Collectors.toList());
        inventoryEngine.inbound(order.getWarehouseId(), lines, "RETURN", id, order.getReturnNo());
        guardStatus(id, version, "DRAFT", "CONFIRMED");
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Integer version) {
        guardStatus(id, version, "DRAFT", "CANCELLED");
    }

    private void guardStatus(Long id, Integer version, String expected, String next) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<ReturnOrder>()
                .eq(ReturnOrder::getId, id)
                .eq(ReturnOrder::getVersion, version)
                .eq(ReturnOrder::getStatus, expected)
                .set(ReturnOrder::getStatus, next)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "退库单状态已变化, 请刷新后重试");
        }
    }

    public record Line(Long materialId, String batchNo, Integer qty) {}
}
