package com.medistock.pro.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.inventory.dto.InboundDTO;
import com.medistock.pro.modules.inventory.entity.StockInbound;
import com.medistock.pro.modules.inventory.entity.StockInboundItem;
import com.medistock.pro.modules.inventory.mapper.StockInboundItemMapper;
import com.medistock.pro.modules.inventory.mapper.StockInboundMapper;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 入库单: 创建(PENDING) -> 确认(CONFIRMED, 经库存引擎入账)
 */
@Service
@RequiredArgsConstructor
public class StockInboundService extends ServiceImpl<StockInboundMapper, StockInbound> {

    private final StockInboundItemMapper itemMapper;
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

    public Page<StockInbound> pageQuery(int page, int size, String status, Long warehouseId, String keyword) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return new Page<>(page, size, 0);
        }
        return lambdaQuery()
                .eq(StringUtils.hasText(status), StockInbound::getStatus, status)
                .eq(warehouseId != null, StockInbound::getWarehouseId, warehouseId)
                .in(scope != null, StockInbound::getWarehouseId, scope)
                .like(StringUtils.hasText(keyword), StockInbound::getInboundNo, keyword)
                .orderByDesc(StockInbound::getCreatedAt)
                .page(new Page<>(page, size));
    }

    public Map<String, Object> detail(Long id) {
        StockInbound master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "入库单不存在: " + id);
        }
        assertScope(master.getWarehouseId());
        List<StockInboundItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<StockInboundItem>().eq(StockInboundItem::getInboundId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public StockInbound create(InboundDTO dto) {
        StockInbound master = new StockInbound();
        master.setInboundNo(billNoGenerator.next("RK", "stock_inbound", "inbound_no"));
        master.setBizType(StringUtils.hasText(dto.bizType()) ? dto.bizType() : "PURCHASE");
        master.setWarehouseId(dto.warehouseId());
        master.setStatus("PENDING");
        master.setVersion(0);
        master.setRemark(dto.remark());

        int totalQty = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InboundDTO.Line line : dto.items()) {
            totalQty += line.qty();
            if (line.unitCost() != null) {
                totalAmount = totalAmount.add(line.unitCost().multiply(BigDecimal.valueOf(line.qty())));
            }
        }
        master.setTotalQty(totalQty);
        master.setTotalAmount(totalAmount);
        save(master);

        for (InboundDTO.Line line : dto.items()) {
            StockInboundItem item = new StockInboundItem();
            item.setInboundId(master.getId());
            item.setMaterialId(line.materialId());
            item.setLocationId(line.locationId());
            item.setBatchNo(line.batchNo());
            item.setProductionDate(line.productionDate());
            item.setExpiryDate(line.expiryDate());
            item.setQty(line.qty());
            item.setUnitCost(line.unitCost());
            item.setAmount(line.unitCost() == null ? null
                    : line.unitCost().multiply(BigDecimal.valueOf(line.qty())));
            itemMapper.insert(item);
        }
        return master;
    }

    /**
     * 确认入库: 状态机 PENDING->CONFIRMED (乐观锁), 同事务经引擎入账
     * 幂等: 重复确认被状态守卫拦截; 引擎侧 uk_txn_no 兜底
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Integer version) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<StockInbound>()
                .eq(StockInbound::getId, id)
                .eq(StockInbound::getVersion, version)
                .eq(StockInbound::getStatus, "PENDING")
                .set(StockInbound::getStatus, "CONFIRMED")
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "入库单状态已变化, 请刷新后重试");
        }
        StockInbound master = getById(id);
        List<StockInboundItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<StockInboundItem>().eq(StockInboundItem::getInboundId, id));
        List<InventoryEngine.InboundLine> lines = items.stream()
                .map(i -> new InventoryEngine.InboundLine(i.getMaterialId(), i.getLocationId(),
                        i.getBatchNo(), i.getProductionDate(), i.getExpiryDate(), i.getQty(), i.getUnitCost()))
                .toList();
        inventoryEngine.inbound(master.getWarehouseId(), lines, "INBOUND", master.getId(), master.getInboundNo());
    }
}
