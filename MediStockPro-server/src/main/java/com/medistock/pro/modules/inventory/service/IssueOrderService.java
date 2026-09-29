package com.medistock.pro.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.inventory.entity.*;
import com.medistock.pro.modules.inventory.mapper.*;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 出库单: PENDING -> (复核确认: 扣库存释放锁定) CONFIRMED -> (科室签收) SIGNED
 */
@Service
@RequiredArgsConstructor
public class IssueOrderService extends ServiceImpl<IssueOrderMapper, IssueOrder> {

    private final IssueOrderItemMapper itemMapper;
    private final IssueRequestMapper issueRequestMapper;
    private final IssueRequestItemMapper issueRequestItemMapper;
    private final PickTaskItemMapper pickTaskItemMapper;
    private final InventoryEngine inventoryEngine;
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

    public Page<IssueOrder> pageQuery(int page, int size, String status, Long warehouseId, Long departmentId) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return new Page<>(page, size, 0);
        }
        return lambdaQuery()
                .eq(StringUtils.hasText(status), IssueOrder::getStatus, status)
                .eq(warehouseId != null, IssueOrder::getWarehouseId, warehouseId)
                .in(scope != null, IssueOrder::getWarehouseId, scope)
                .eq(departmentId != null, IssueOrder::getDepartmentId, departmentId)
                .orderByDesc(IssueOrder::getCreatedAt)
                .page(new Page<>(page, size));
    }

    public Map<String, Object> detail(Long id) {
        IssueOrder master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "出库单不存在: " + id);
        }
        assertScope(master.getWarehouseId());
        List<IssueOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<IssueOrderItem>().eq(IssueOrderItem::getIssueId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    /**
     * 复核出库: PENDING -> CONFIRMED
     * 同事务: 引擎扣减(校验锁定) -> 回写申请已发数量 -> 全部发完则申请 COMPLETED
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Integer version) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<IssueOrder>()
                .eq(IssueOrder::getId, id)
                .eq(IssueOrder::getVersion, version)
                .eq(IssueOrder::getStatus, "PENDING")
                .set(IssueOrder::getStatus, "CONFIRMED")
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "出库单状态已变化, 请刷新后重试");
        }
        IssueOrder master = getById(id);
        List<IssueOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<IssueOrderItem>().eq(IssueOrderItem::getIssueId, id));

        // 出库明细无库位列, 从拣货明细取 location_id 供引擎精确定位批次行
        // 注意: location_id 可空, Collectors.toMap 不接受 null 值, 手工组装
        Map<String, Long> locationMap = new HashMap<>();
        if (master.getPickTaskId() != null) {
            List<PickTaskItem> pickItems = pickTaskItemMapper.selectList(
                    new LambdaQueryWrapper<PickTaskItem>().eq(PickTaskItem::getTaskId, master.getPickTaskId()));
            for (PickTaskItem p : pickItems) {
                locationMap.putIfAbsent(p.getMaterialId() + ":" + p.getBatchNo(), p.getLocationId());
            }
        }

        final Map<String, Long> locations = locationMap;
        List<InventoryEngine.OutboundLine> lines = items.stream()
                .map(i -> new InventoryEngine.OutboundLine(i.getMaterialId(),
                        locations.get(i.getMaterialId() + ":" + i.getBatchNo()),
                        i.getBatchNo(), i.getQty(), null))
                .toList();
        inventoryEngine.outbound(master.getWarehouseId(), lines, "ISSUE", master.getId(), master.getIssueNo());

        // 回写申请已发数量
        if (master.getRequestId() != null) {
            Map<Long, Integer> byMaterial = items.stream().collect(Collectors.groupingBy(
                    IssueOrderItem::getMaterialId, Collectors.summingInt(IssueOrderItem::getQty)));
            List<IssueRequestItem> reqItems = issueRequestItemMapper.selectList(
                    new LambdaQueryWrapper<IssueRequestItem>().eq(IssueRequestItem::getRequestId, master.getRequestId()));
            boolean allIssued = true;
            for (IssueRequestItem reqItem : reqItems) {
                int add = byMaterial.getOrDefault(reqItem.getMaterialId(), 0);
                if (add > 0) {
                    issueRequestItemMapper.update(null, new LambdaUpdateWrapper<IssueRequestItem>()
                            .eq(IssueRequestItem::getId, reqItem.getId())
                            .setSql("issued_qty = issued_qty + " + add));
                }
                int issued = (reqItem.getIssuedQty() == null ? 0 : reqItem.getIssuedQty()) + add;
                int approved = reqItem.getApprovedQty() == null ? reqItem.getQty() : reqItem.getApprovedQty();
                if (issued < approved) {
                    allIssued = false;
                }
            }
            if (allIssued) {
                issueRequestMapper.update(null, new LambdaUpdateWrapper<IssueRequest>()
                        .eq(IssueRequest::getId, master.getRequestId())
                        .eq(IssueRequest::getStatus, "PICKING")
                        .set(IssueRequest::getStatus, "COMPLETED")
                        .setSql("version = version + 1"));
            }
        }
    }

    /**
     * 红冲: SIGNED -> REVERSED
     * 同事务: 按出库明细原批次把库存入回(sourceType=REVERSAL), 回退申请已发数量, 已完成申请退回 PICKING。
     * 幂等: 状态守卫(仅 SIGNED 可冲) + 流水 uk_txn_no(动作 IN 与原 OUT 不冲突, 重复冲红被状态守卫拦截)。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id, Integer version, String reason) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<IssueOrder>()
                .eq(IssueOrder::getId, id)
                .eq(IssueOrder::getVersion, version)
                .eq(IssueOrder::getStatus, "SIGNED")
                .set(IssueOrder::getStatus, "REVERSED")
                .set(IssueOrder::getRemark, reason)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "仅已签收出库单可红冲, 请刷新后重试");
        }
        IssueOrder master = getById(id);
        List<IssueOrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<IssueOrderItem>().eq(IssueOrderItem::getIssueId, id));

        List<InventoryEngine.InboundLine> lines = items.stream()
                .map(i -> new InventoryEngine.InboundLine(i.getMaterialId(), null, i.getBatchNo(),
                        null, null, i.getQty(), i.getUnitCost()))
                .toList();
        inventoryEngine.inbound(master.getWarehouseId(), lines, "REVERSAL", master.getId(), master.getIssueNo());

        // 回退申请已发数量; 若申请已完成则退回 PICKING 允许重新拣货
        if (master.getRequestId() != null) {
            Map<Long, Integer> byMaterial = items.stream().collect(Collectors.groupingBy(
                    IssueOrderItem::getMaterialId, Collectors.summingInt(IssueOrderItem::getQty)));
            List<IssueRequestItem> reqItems = issueRequestItemMapper.selectList(
                    new LambdaQueryWrapper<IssueRequestItem>().eq(IssueRequestItem::getRequestId, master.getRequestId()));
            for (IssueRequestItem reqItem : reqItems) {
                int sub = byMaterial.getOrDefault(reqItem.getMaterialId(), 0);
                if (sub > 0) {
                    issueRequestItemMapper.update(null, new LambdaUpdateWrapper<IssueRequestItem>()
                            .eq(IssueRequestItem::getId, reqItem.getId())
                            .setSql("issued_qty = GREATEST(issued_qty - " + sub + ", 0)"));
                }
            }
            issueRequestMapper.update(null, new LambdaUpdateWrapper<IssueRequest>()
                    .eq(IssueRequest::getId, master.getRequestId())
                    .eq(IssueRequest::getStatus, "COMPLETED")
                    .set(IssueRequest::getStatus, "PICKING")
                    .setSql("version = version + 1"));
        }
    }

    /** 科室签收: CONFIRMED -> SIGNED */
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long id, Integer version, String signBy) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<IssueOrder>()
                .eq(IssueOrder::getId, id)
                .eq(IssueOrder::getVersion, version)
                .eq(IssueOrder::getStatus, "CONFIRMED")
                .set(IssueOrder::getStatus, "SIGNED")
                .set(IssueOrder::getSignBy, signBy)
                .set(IssueOrder::getSignTime, LocalDateTime.now())
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "出库单状态已变化, 请刷新后重试");
        }
    }
}
