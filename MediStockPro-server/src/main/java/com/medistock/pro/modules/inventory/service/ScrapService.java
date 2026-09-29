package com.medistock.pro.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.inventory.entity.ScrapOrder;
import com.medistock.pro.modules.inventory.entity.ScrapOrderItem;
import com.medistock.pro.modules.inventory.mapper.ScrapOrderItemMapper;
import com.medistock.pro.modules.inventory.mapper.ScrapOrderMapper;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报废单: 创建(PENDING) → 审批(核销库存) → CONFIRMED / CANCELLED
 */
@Service
@RequiredArgsConstructor
public class ScrapService extends ServiceImpl<ScrapOrderMapper, ScrapOrder> {

    private final ScrapOrderItemMapper itemMapper;
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

    public PageResult<ScrapOrder> pageQuery(int page, int size, Long warehouseId, String status) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return PageResult.of(new Page<ScrapOrder>(page, size, 0));
        }
        Page<ScrapOrder> p = page(new Page<>(page, size), new LambdaQueryWrapper<ScrapOrder>()
                .eq(warehouseId != null, ScrapOrder::getWarehouseId, warehouseId)
                .in(scope != null, ScrapOrder::getWarehouseId, scope)
                .eq(status != null && !status.isBlank(), ScrapOrder::getStatus, status)
                .orderByDesc(ScrapOrder::getId));
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long id) {
        ScrapOrder master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报废单不存在: " + id);
        }
        assertScope(master.getWarehouseId());
        List<ScrapOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ScrapOrderItem>().eq(ScrapOrderItem::getScrapId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public ScrapOrder create(Long warehouseId, String reason, List<Line> items) {
        ScrapOrder order = new ScrapOrder();
        order.setScrapNo(billNoGenerator.next("BF", "scrap_order", "scrap_no"));
        order.setWarehouseId(warehouseId);
        order.setReason(reason);
        order.setStatus("PENDING");
        order.setVersion(0);
        save(order);
        for (Line line : items) {
            ScrapOrderItem item = new ScrapOrderItem();
            item.setScrapId(order.getId());
            item.setMaterialId(line.materialId());
            item.setBatchNo(line.batchNo());
            item.setQty(line.qty());
            itemMapper.insert(item);
        }
        return order;
    }

    /** 审批通过 = 核销库存 (允许过期批次; 冻结批次/锁定量保护由引擎保证) */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Integer version) {
        ScrapOrder order = getById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报废单不存在: " + id);
        }
        List<ScrapOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<ScrapOrderItem>().eq(ScrapOrderItem::getScrapId, id));
        List<InventoryEngine.OutboundLine> lines = items.stream()
                .map(i -> new InventoryEngine.OutboundLine(i.getMaterialId(), null, i.getBatchNo(), i.getQty(), null))
                .collect(Collectors.toList());
        inventoryEngine.writeOff(order.getWarehouseId(), lines, "SCRAP", id, order.getScrapNo());
        guardStatus(id, version, "PENDING", "CONFIRMED");
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Integer version) {
        guardStatus(id, version, "PENDING", "CANCELLED");
    }

    private void guardStatus(Long id, Integer version, String expected, String next) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<ScrapOrder>()
                .eq(ScrapOrder::getId, id)
                .eq(ScrapOrder::getVersion, version)
                .eq(ScrapOrder::getStatus, expected)
                .set(ScrapOrder::getStatus, next)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "报废单状态已变化, 请刷新后重试");
        }
    }

    public record Line(Long materialId, String batchNo, Integer qty) {}
}
