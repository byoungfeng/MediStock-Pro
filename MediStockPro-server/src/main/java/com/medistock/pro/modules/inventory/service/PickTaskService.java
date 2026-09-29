package com.medistock.pro.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.inventory.dto.PickCompleteDTO;
import com.medistock.pro.modules.inventory.entity.*;
import com.medistock.pro.modules.inventory.mapper.*;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 拣货任务: PICKING -> (完成: 生成出库单) PICKED
 *                   -> (取消: 完整释放锁定) CANCELLED
 */
@Service
@RequiredArgsConstructor
public class PickTaskService extends ServiceImpl<PickTaskMapper, PickTask> {

    private final PickTaskItemMapper itemMapper;
    private final IssueOrderMapper issueOrderMapper;
    private final IssueOrderItemMapper issueOrderItemMapper;
    private final IssueRequestMapper issueRequestMapper;
    private final InventoryBatchMapper batchMapper;
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

    public Page<PickTask> pageQuery(int page, int size, String status, Long warehouseId) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return new Page<>(page, size, 0);
        }
        return lambdaQuery()
                .eq(StringUtils.hasText(status), PickTask::getStatus, status)
                .eq(warehouseId != null, PickTask::getWarehouseId, warehouseId)
                .in(scope != null, PickTask::getWarehouseId, scope)
                .orderByDesc(PickTask::getCreatedAt)
                .page(new Page<>(page, size));
    }

    public Map<String, Object> detail(Long id) {
        PickTask task = getById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "拣货任务不存在: " + id);
        }
        assertScope(task.getWarehouseId());
        List<PickTaskItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PickTaskItem>().eq(PickTaskItem::getTaskId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", task);
        result.put("items", items);
        return result;
    }

    /**
     * 完成拣货: PICKING -> PICKED
     * 短拣差额立即释放锁定; 同事务生成待复核出库单
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, PickCompleteDTO dto) {
        PickTask task = getById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "拣货任务不存在: " + id);
        }
        assertScope(task.getWarehouseId());
        List<PickTaskItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PickTaskItem>().eq(PickTaskItem::getTaskId, id));
        Map<Long, PickTaskItem> itemMap = items.stream()
                .collect(Collectors.toMap(PickTaskItem::getId, i -> i));

        int totalQty = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PickCompleteDTO.Line line : dto.items()) {
            PickTaskItem item = itemMap.get(line.itemId());
            if (item == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "明细不属于本任务: " + line.itemId());
            }
            if (line.pickedQty() < 0 || line.pickedQty() > item.getSuggestedQty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "实拣数量非法: itemId=" + line.itemId());
            }
            if (line.pickedQty() < item.getSuggestedQty() && !StringUtils.hasText(line.shortReason())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "短拣必须填写原因: itemId=" + line.itemId());
            }
            PickTaskItem update = new PickTaskItem();
            update.setId(item.getId());
            update.setPickedQty(line.pickedQty());
            update.setShortReason(line.shortReason());
            update.setOverrideReason(line.overrideReason());
            itemMapper.updateById(update);

            // 短拣差额释放锁定 (P0: 拣货取消/短拣必须完整释放)
            int shortQty = item.getSuggestedQty() - line.pickedQty();
            if (shortQty > 0) {
                inventoryEngine.releaseQty(task.getId(), item.getId(), shortQty);
            }

            if (line.pickedQty() > 0) {
                totalQty += line.pickedQty();
                InventoryBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<InventoryBatch>()
                        .eq(InventoryBatch::getWarehouseId, task.getWarehouseId())
                        .eq(InventoryBatch::getMaterialId, item.getMaterialId())
                        .eq(InventoryBatch::getBatchNo, item.getBatchNo())
                        .last("LIMIT 1"));
                if (batch != null && batch.getUnitCost() != null) {
                    totalAmount = totalAmount.add(batch.getUnitCost().multiply(BigDecimal.valueOf(line.pickedQty())));
                }
            }
        }

        guardStatus(id, dto.version(), "PICKING", "PICKED");

        // 生成待复核出库单
        IssueOrder order = new IssueOrder();
        order.setIssueNo(billNoGenerator.next("CK", "issue_order", "issue_no"));
        IssueRequest request = issueRequestMapper.selectById(task.getSourceId());
        order.setRequestId(task.getSourceId());
        order.setPickTaskId(task.getId());
        order.setWarehouseId(task.getWarehouseId());
        order.setDepartmentId(request == null ? null : request.getDepartmentId());
        order.setStatus("PENDING");
        order.setTotalQty(totalQty);
        order.setTotalAmount(totalAmount);
        order.setVersion(0);
        issueOrderMapper.insert(order);

        for (PickCompleteDTO.Line line : dto.items()) {
            if (line.pickedQty() <= 0) continue;
            PickTaskItem item = itemMap.get(line.itemId());
            IssueOrderItem orderItem = new IssueOrderItem();
            orderItem.setIssueId(order.getId());
            orderItem.setMaterialId(item.getMaterialId());
            orderItem.setBatchNo(item.getBatchNo());
            orderItem.setQty(line.pickedQty());
            issueOrderItemMapper.insert(orderItem);
        }
    }

    /** 取消拣货: 完整释放本任务锁定, 申请退回 APPROVED 待重新审批拣货 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Integer version) {
        PickTask task = getById(id);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "拣货任务不存在: " + id);
        }
        guardStatus(id, version, "PICKING", "CANCELLED");
        inventoryEngine.release(id); // 内含: 已生成出库单则拒绝; 异常则整体回滚
        issueRequestMapper.update(null, new LambdaUpdateWrapper<IssueRequest>()
                .eq(IssueRequest::getId, task.getSourceId())
                .eq(IssueRequest::getStatus, "PICKING")
                .set(IssueRequest::getStatus, "APPROVED")
                .setSql("version = version + 1"));
    }

    private void guardStatus(Long id, Integer version, String from, String to) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<PickTask>()
                .eq(PickTask::getId, id)
                .eq(PickTask::getVersion, version)
                .eq(PickTask::getStatus, from)
                .set(PickTask::getStatus, to)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "单据状态已变化, 请刷新后重试");
        }
    }
}
