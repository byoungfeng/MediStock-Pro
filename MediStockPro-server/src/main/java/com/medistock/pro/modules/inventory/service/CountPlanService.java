package com.medistock.pro.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.inventory.dto.CountEntryDTO;
import com.medistock.pro.modules.inventory.dto.CountPlanDTO;
import com.medistock.pro.modules.inventory.entity.CountItem;
import com.medistock.pro.modules.inventory.entity.CountPlan;
import com.medistock.pro.modules.inventory.entity.InventoryBatch;
import com.medistock.pro.modules.inventory.mapper.CountItemMapper;
import com.medistock.pro.modules.inventory.mapper.CountPlanMapper;
import com.medistock.pro.modules.inventory.mapper.InventoryBatchMapper;
import com.medistock.pro.modules.system.service.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 盘点计划: DRAFT→(开始:快照账面/可选冻结)→COUNTING→(录入)→(确认:差异调整)→CONFIRMED / CANCELLED
 */
@Service
@RequiredArgsConstructor
public class CountPlanService extends ServiceImpl<CountPlanMapper, CountPlan> {

    private final CountItemMapper countItemMapper;
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

    public PageResult<CountPlan> pageQuery(int page, int size, Long warehouseId, String status) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return PageResult.of(new Page<CountPlan>(page, size, 0));
        }
        Page<CountPlan> p = page(new Page<>(page, size), new LambdaQueryWrapper<CountPlan>()
                .eq(warehouseId != null, CountPlan::getWarehouseId, warehouseId)
                .in(scope != null, CountPlan::getWarehouseId, scope)
                .eq(status != null && !status.isBlank(), CountPlan::getStatus, status)
                .orderByDesc(CountPlan::getId));
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long id) {
        CountPlan master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "盘点计划不存在: " + id);
        }
        assertScope(master.getWarehouseId());
        List<CountItem> items = countItemMapper.selectList(
                new LambdaQueryWrapper<CountItem>().eq(CountItem::getPlanId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public CountPlan create(CountPlanDTO dto) {
        CountPlan plan = new CountPlan();
        plan.setPlanNo(billNoGenerator.next("PD", "count_plan", "plan_no"));
        plan.setWarehouseId(dto.warehouseId());
        plan.setScope(dto.scope());
        plan.setFreeze(Boolean.TRUE.equals(dto.freeze()) ? 1 : 0);
        plan.setStatus("DRAFT");
        plan.setVersion(0);
        save(plan);
        return plan;
    }

    /** 开始盘点: 快照当前批次账面到明细; freeze=1 时冻结全仓批次 */
    @Transactional(rollbackFor = Exception.class)
    public void start(Long id, Integer version) {
        CountPlan plan = getById(id);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "盘点计划不存在: " + id);
        }
        // 同仓同时只允许一个进行中盘点
        Long running = baseMapper.selectCount(new LambdaQueryWrapper<CountPlan>()
                .eq(CountPlan::getWarehouseId, plan.getWarehouseId())
                .eq(CountPlan::getStatus, "COUNTING"));
        if (running != null && running > 0) {
            throw new BizException(ErrorCode.INV_006, "该仓库已有进行中的盘点, 请先完成或取消");
        }
        List<InventoryBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<InventoryBatch>()
                .eq(InventoryBatch::getWarehouseId, plan.getWarehouseId())
                .gt(InventoryBatch::getOnHand, 0));
        for (InventoryBatch b : batches) {
            CountItem item = new CountItem();
            item.setPlanId(id);
            item.setMaterialId(b.getMaterialId());
            item.setLocationId(b.getLocationId());
            item.setBatchNo(b.getBatchNo());
            item.setBookQty(b.getOnHand());
            countItemMapper.insert(item);
        }
        if (plan.getFreeze() == 1) {
            batchMapper.update(null, new LambdaUpdateWrapper<InventoryBatch>()
                    .eq(InventoryBatch::getWarehouseId, plan.getWarehouseId())
                    .eq(InventoryBatch::getStatus, "NORMAL")
                    .set(InventoryBatch::getStatus, "FROZEN"));
        }
        guardStatus(id, version, "DRAFT", "COUNTING");
        update(null, new LambdaUpdateWrapper<CountPlan>()
                .eq(CountPlan::getId, id)
                .set(CountPlan::getSnapshotAt, LocalDateTime.now()));
    }

    /** 盘点录入: 填实盘数并计算差异 */
    @Transactional(rollbackFor = Exception.class)
    public void entry(Long id, CountEntryDTO dto) {
        CountPlan plan = getById(id);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "盘点计划不存在: " + id);
        }
        if (!"COUNTING".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "非盘点中状态, 不能录入: " + plan.getStatus());
        }
        List<CountItem> items = countItemMapper.selectList(
                new LambdaQueryWrapper<CountItem>().eq(CountItem::getPlanId, id));
        Map<Long, CountItem> itemMap = items.stream().collect(Collectors.toMap(CountItem::getId, i -> i));
        for (CountEntryDTO.Line line : dto.items()) {
            CountItem item = itemMap.get(line.itemId());
            if (item == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "明细不属于本计划: itemId=" + line.itemId());
            }
            int diff = line.countQty() - item.getBookQty();
            if (diff != 0 && (line.diffReason() == null || line.diffReason().isBlank())) {
                throw new BizException(ErrorCode.PARAM_INVALID, "有差异必须填写原因: itemId=" + line.itemId());
            }
            countItemMapper.update(null, new LambdaUpdateWrapper<CountItem>()
                    .eq(CountItem::getId, item.getId())
                    .set(CountItem::getCountQty, line.countQty())
                    .set(CountItem::getDiffQty, diff)
                    .set(CountItem::getDiffReason, line.diffReason()));
        }
    }

    /** 确认盘点: 差异行调账, 解冻批次 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, Integer version) {
        CountPlan plan = getById(id);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "盘点计划不存在: " + id);
        }
        List<CountItem> items = countItemMapper.selectList(
                new LambdaQueryWrapper<CountItem>().eq(CountItem::getPlanId, id));
        List<CountItem> notCounted = items.stream().filter(i -> i.getCountQty() == null).collect(Collectors.toList());
        if (!notCounted.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "存在未录入明细: " + notCounted.size() + " 行");
        }
        List<InventoryEngine.AdjustLine> adjustLines = new ArrayList<>();
        for (CountItem item : items) {
            if (item.getDiffQty() != null && item.getDiffQty() != 0) {
                adjustLines.add(new InventoryEngine.AdjustLine(item.getMaterialId(), item.getBatchNo(), item.getDiffQty()));
            }
        }
        if (!adjustLines.isEmpty()) {
            inventoryEngine.adjust(plan.getWarehouseId(), adjustLines, "COUNT", id, plan.getPlanNo());
        }
        unfreeze(plan.getWarehouseId());
        guardStatus(id, version, "COUNTING", "CONFIRMED");
    }

    /** 取消盘点: 解冻批次, 不调整库存 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Integer version) {
        CountPlan plan = getById(id);
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "盘点计划不存在: " + id);
        }
        if (!List.of("DRAFT", "COUNTING").contains(plan.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "当前状态不允许取消: " + plan.getStatus());
        }
        unfreeze(plan.getWarehouseId());
        guardStatus(id, version, plan.getStatus(), "CANCELLED");
    }

    private void unfreeze(Long warehouseId) {
        batchMapper.update(null, new LambdaUpdateWrapper<InventoryBatch>()
                .eq(InventoryBatch::getWarehouseId, warehouseId)
                .eq(InventoryBatch::getStatus, "FROZEN")
                .set(InventoryBatch::getStatus, "NORMAL"));
    }

    private void guardStatus(Long id, Integer version, String expected, String next) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<CountPlan>()
                .eq(CountPlan::getId, id)
                .eq(CountPlan::getVersion, version)
                .eq(CountPlan::getStatus, expected)
                .set(CountPlan::getStatus, next)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "盘点计划状态已变化, 请刷新后重试");
        }
    }
}
