package com.medistock.pro.modules.inventory.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.inventory.entity.*;
import com.medistock.pro.modules.inventory.mapper.*;
import com.medistock.pro.modules.inventory.service.BillNoGenerator;
import com.medistock.pro.modules.inventory.service.InventoryEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存引擎实现
 *
 * 并发模型: 批次行 FOR UPDATE 串行化 + version 乐观锁兜底 + uk_txn_no 幂等
 * 流水语义: IN/OUT 记 on_hand 前后余额; LOCK/UNLOCK 记 locked_qty 前后余额
 */
@Service
@RequiredArgsConstructor
public class InventoryEngineImpl implements InventoryEngine {

    private final InventoryBatchMapper batchMapper;
    private final InventoryTxnMapper txnMapper;
    private final PickTaskMapper pickTaskMapper;
    private final PickTaskItemMapper pickTaskItemMapper;
    private final IssueOrderMapper issueOrderMapper;
    private final BillNoGenerator billNoGenerator;

    // ==================== 入库 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inbound(Long warehouseId, List<InboundLine> lines, String sourceType, Long sourceId, String sourceNo) {
        for (InboundLine line : lines) {
            String txnNo = txnNo(sourceNo, line.materialId(), line.batchNo(), "IN");
            if (txnExists(txnNo)) {
                continue; // 幂等: 该行已入账
            }
            InventoryBatch batch = lockOrCreateBatch(warehouseId, line);
            int before = batch.getOnHand();
            int after = before + line.qty();
            guardedUpdate(batch, b -> b.setOnHand(after));
            writeTxn(txnNo, warehouseId, line.materialId(), line.batchNo(), line.qty(), "IN",
                    before, after, sourceType, sourceId, sourceNo);
        }
    }

    // ==================== 锁定 / 释放 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reserve(Long warehouseId, List<OutboundLine> lines, String sourceType, Long sourceId, String sourceNo) {
        // 同物资多行合并, 避免重复 FEFO 扫描
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OutboundLine line : lines) {
            merged.merge(line.materialId(), line.qty(), Integer::sum);
        }

        PickTask task = new PickTask();
        task.setTaskNo(billNoGenerator.next("JH", "pick_task", "task_no"));
        task.setSourceId(sourceId);
        task.setSourceNo(sourceNo);
        task.setWarehouseId(warehouseId);
        task.setStatus("PICKING");
        task.setVersion(0);
        pickTaskMapper.insert(task);

        for (Map.Entry<Long, Integer> e : merged.entrySet()) {
            Long materialId = e.getKey();
            int need = e.getValue();
            List<InventoryBatch> candidates = fefoCandidates(warehouseId, materialId, true);
            for (InventoryBatch batch : candidates) {
                if (need <= 0) break;
                int available = batch.getOnHand() - batch.getLockedQty();
                if (available <= 0) continue;
                int alloc = Math.min(available, need);
                int beforeLocked = batch.getLockedQty();
                guardedUpdate(batch, b -> b.setLockedQty(beforeLocked + alloc));

                PickTaskItem item = new PickTaskItem();
                item.setTaskId(task.getId());
                item.setMaterialId(materialId);
                item.setLocationId(batch.getLocationId());
                item.setBatchNo(batch.getBatchNo());
                item.setSuggestedQty(alloc);
                pickTaskItemMapper.insert(item);

                writeTxn(task.getTaskNo() + ":" + item.getId() + ":LOCK", warehouseId, materialId,
                        batch.getBatchNo(), alloc, "OUT", beforeLocked, beforeLocked + alloc,
                        "LOCK", task.getId(), task.getTaskNo());
                need -= alloc;
            }
            if (need > 0) {
                throw new BizException(ErrorCode.INV_001, "库存不足: materialId=" + materialId + ", 缺口=" + need);
            }
        }
        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long reservationId) {
        PickTask task = pickTaskMapper.selectById(reservationId);
        if (task == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "拣货任务不存在: " + reservationId);
        }
        // 已生成出库单的任务禁止释放 (P0: 防止出库后锁定被误释放)
        Long orderCount = issueOrderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<IssueOrder>()
                        .eq(IssueOrder::getPickTaskId, reservationId)
                        .in(IssueOrder::getStatus, "PENDING", "CONFIRMED", "SIGNED"));
        if (orderCount != null && orderCount > 0) {
            throw new BizException(ErrorCode.INV_006, "已生成出库单, 禁止释放锁定");
        }
        List<PickTaskItem> items = pickTaskItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PickTaskItem>()
                        .eq(PickTaskItem::getTaskId, reservationId));
        for (PickTaskItem item : items) {
            // 当前持有量 = 实拣(若已拣) 否则 应拣
            int held = item.getPickedQty() != null ? item.getPickedQty() : item.getSuggestedQty();
            if (held > 0) {
                doRelease(task, item, held);
            }
        }
        // 任务状态由调用方(单据服务)管理, 引擎只负责库存与流水
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseQty(Long taskId, Long taskItemId, int qty) {
        if (qty <= 0) return;
        PickTask task = pickTaskMapper.selectById(taskId);
        PickTaskItem item = pickTaskItemMapper.selectById(taskItemId);
        if (task == null || item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "拣货任务/明细不存在");
        }
        doRelease(task, item, qty);
    }

    /** 释放一行锁定 (UNLOCK 流水粒度到拣货明细, 天然幂等) */
    private void doRelease(PickTask task, PickTaskItem item, int qty) {
        String txnNo = task.getTaskNo() + ":" + item.getId() + ":UNLOCK";
        if (txnExists(txnNo)) return;
        InventoryBatch batch = lockBatch(task.getWarehouseId(), item.getMaterialId(), item.getBatchNo(), item.getLocationId());
        int beforeLocked = batch.getLockedQty();
        if (beforeLocked < qty) {
            throw new BizException(ErrorCode.INV_003, "锁定量不足: batchId=" + batch.getId());
        }
        guardedUpdate(batch, b -> b.setLockedQty(beforeLocked - qty));
        writeTxn(txnNo, task.getWarehouseId(), item.getMaterialId(), item.getBatchNo(), qty, "IN",
                beforeLocked, beforeLocked - qty, "UNLOCK", task.getId(), task.getTaskNo());
    }

    // ==================== 出库 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void outbound(Long warehouseId, List<OutboundLine> lines, String sourceType, Long sourceId, String sourceNo) {
        for (OutboundLine line : lines) {
            String txnNo = txnNo(sourceNo, line.materialId(), line.batchNo(), "OUT");
            if (txnExists(txnNo)) {
                continue; // P0: 重复提交出库只扣减一次
            }
            InventoryBatch batch = lockBatch(warehouseId, line.materialId(), line.batchNo(), line.locationId());
            assertBatchUsable(batch);
            int before = batch.getOnHand();
            int locked = batch.getLockedQty();
            if (before < line.qty()) {
                throw new BizException(ErrorCode.INV_001, "库存不足: batchId=" + batch.getId());
            }
            if (locked < line.qty()) {
                throw new BizException(ErrorCode.INV_003, "锁定量不足: batchId=" + batch.getId());
            }
            guardedUpdate(batch, b -> {
                b.setOnHand(before - line.qty());
                b.setLockedQty(locked - line.qty());
            });
            writeTxn(txnNo, warehouseId, line.materialId(), line.batchNo(), line.qty(), "OUT",
                    before, before - line.qty(), sourceType, sourceId, sourceNo);
        }
    }

    // ==================== 调拨 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferShip(Long fromWarehouseId, List<OutboundLine> lines, Long transferId, String transferNo) {
        for (OutboundLine line : lines) {
            String txnNo = txnNo(transferNo, line.materialId(), line.batchNo(), "SHIP");
            if (txnExists(txnNo)) {
                continue; // P0: 发运超时重试不得重复扣减
            }
            InventoryBatch batch = lockBatch(fromWarehouseId, line.materialId(), line.batchNo(), line.locationId());
            assertBatchUsable(batch);
            int before = batch.getOnHand();
            int available = before - batch.getLockedQty();
            if (available < line.qty()) {
                throw new BizException(ErrorCode.INV_001, "可用库存不足: batchId=" + batch.getId());
            }
            guardedUpdate(batch, b -> {
                b.setOnHand(before - line.qty());
                b.setInTransitQty(batch.getInTransitQty() + line.qty());
            });
            writeTxn(txnNo, fromWarehouseId, line.materialId(), line.batchNo(), line.qty(), "OUT",
                    before, before - line.qty(), "TRANSFER_SHIP", transferId, transferNo);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferReceive(Long fromWarehouseId, Long toWarehouseId, List<InboundLine> lines, Long transferId, String transferNo) {
        for (InboundLine line : lines) {
            String txnNo = txnNo(transferNo, line.materialId(), line.batchNo(), "RECV");
            if (txnExists(txnNo)) {
                continue;
            }
            // 调出仓: in_transit 核减 (不重复记流水, 收发差异由调拨异常单处理)
            InventoryBatch fromBatch = lockBatch(fromWarehouseId, line.materialId(), line.batchNo(), null);
            if (fromBatch.getInTransitQty() < line.qty()) {
                throw new BizException(ErrorCode.INV_006, "在途数量不足: batchId=" + fromBatch.getId());
            }
            guardedUpdate(fromBatch, b -> b.setInTransitQty(b.getInTransitQty() - line.qty()));

            // 调入仓: 建/增批次 on_hand
            InventoryBatch toBatch = lockOrCreateBatch(toWarehouseId, line);
            int before = toBatch.getOnHand();
            guardedUpdate(toBatch, b -> b.setOnHand(before + line.qty()));
            writeTxn(txnNo, toWarehouseId, line.materialId(), line.batchNo(), line.qty(), "IN",
                    before, before + line.qty(), "TRANSFER_RECEIVE", transferId, transferNo);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferLoss(Long fromWarehouseId, Long materialId, String batchNo, int qty, Long transferId, String transferNo) {
        if (qty <= 0) return;
        String txnNo = txnNo(transferNo, materialId, batchNo, "LOSS");
        if (txnExists(txnNo)) {
            return;
        }
        InventoryBatch batch = lockBatch(fromWarehouseId, materialId, batchNo, null);
        if (batch.getInTransitQty() < qty) {
            throw new BizException(ErrorCode.INV_006, "在途数量不足, 无法核销: batchId=" + batch.getId());
        }
        guardedUpdate(batch, b -> b.setInTransitQty(b.getInTransitQty() - qty));
        // on_hand 不变, 前后余额记当前在库
        writeTxn(txnNo, fromWarehouseId, materialId, batchNo, qty, "OUT",
                batch.getOnHand(), batch.getOnHand(), "TRANSFER_LOSS", transferId, transferNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void writeOff(Long warehouseId, List<OutboundLine> lines, String sourceType, Long sourceId, String sourceNo) {
        for (OutboundLine line : lines) {
            String txnNo = txnNo(sourceNo, line.materialId(), line.batchNo(), "WO");
            if (txnExists(txnNo)) {
                continue;
            }
            InventoryBatch batch = lockBatch(warehouseId, line.materialId(), line.batchNo(), line.locationId());
            // 报废允许过期批次; 仅冻结/报废状态禁止操作
            if (!"NORMAL".equals(batch.getStatus())) {
                throw new BizException(ErrorCode.INV_004, "批次状态不可核销(" + batch.getStatus() + "): " + batch.getBatchNo());
            }
            int before = batch.getOnHand();
            int available = before - batch.getLockedQty();
            if (available < line.qty()) {
                throw new BizException(ErrorCode.INV_001, "可用库存不足(有锁定): batchId=" + batch.getId());
            }
            guardedUpdate(batch, b -> b.setOnHand(before - line.qty()));
            writeTxn(txnNo, warehouseId, line.materialId(), line.batchNo(), line.qty(), "OUT",
                    before, before - line.qty(), sourceType, sourceId, sourceNo);
        }
    }

    // ==================== 调整 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long warehouseId, List<AdjustLine> lines, String sourceType, Long sourceId, String sourceNo) {
        for (AdjustLine line : lines) {
            String txnNo = txnNo(sourceNo, line.materialId(), line.batchNo(), "ADJ");
            if (txnExists(txnNo)) {
                continue; // P0: 盘点调整不覆盖后续真实库存 —— 同事务按当前行锁余额计算
            }
            InventoryBatch batch = lockBatch(warehouseId, line.materialId(), line.batchNo(), null);
            int before = batch.getOnHand();
            int after = before + line.diffQty();
            if (after < 0 || after < batch.getLockedQty()) {
                throw new BizException(ErrorCode.INV_001, "调整后库存不能为负或低于锁定量: batchId=" + batch.getId());
            }
            guardedUpdate(batch, b -> b.setOnHand(after));
            writeTxn(txnNo, warehouseId, line.materialId(), line.batchNo(), line.diffQty(),
                    line.diffQty() >= 0 ? "IN" : "OUT", before, after, sourceType, sourceId, sourceNo);
        }
    }

    // ==================== FEFO ====================

    @Override
    public List<FefoSuggestion> fefoAllocate(Long warehouseId, Long materialId, int qty) {
        List<InventoryBatch> candidates = fefoCandidates(warehouseId, materialId, false);
        List<FefoSuggestion> result = new ArrayList<>();
        int need = qty;
        for (InventoryBatch batch : candidates) {
            if (need <= 0) break;
            int available = batch.getOnHand() - batch.getLockedQty();
            if (available <= 0) continue;
            int alloc = Math.min(available, need);
            result.add(new FefoSuggestion(batch.getId(), batch.getBatchNo(), batch.getExpiryDate(), alloc));
            need -= alloc;
        }
        return result;
    }

    // ==================== 内部 ====================

    /** FEFO 候选批次: 正常状态 + 未过期 + 有可用, 效期升序(NULL 最后) */
    private List<InventoryBatch> fefoCandidates(Long warehouseId, Long materialId, boolean forUpdate) {
        return batchMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventoryBatch>()
                        .eq(InventoryBatch::getWarehouseId, warehouseId)
                        .eq(InventoryBatch::getMaterialId, materialId)
                        .eq(InventoryBatch::getStatus, "NORMAL")
                        .and(w -> w.isNull(InventoryBatch::getExpiryDate)
                                .or().ge(InventoryBatch::getExpiryDate, LocalDate.now()))
                        .apply("on_hand - locked_qty > 0")
                        .orderByAsc(InventoryBatch::getExpiryDate)
                        .orderByAsc(InventoryBatch::getId)
                        .last(forUpdate ? "FOR UPDATE" : ""));
    }

    /**
     * 按 (仓, 物资, 批号[, 库位]) 行锁读取
     * 注意: MySQL 唯一索引允许多个 NULL, location_id 为空时必须显式 IS NULL 匹配, 否则 uk 兜底失效
     */
    private InventoryBatch lockBatch(Long warehouseId, Long materialId, String batchNo, Long locationId) {
        InventoryBatch batch = batchMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventoryBatch>()
                        .eq(InventoryBatch::getWarehouseId, warehouseId)
                        .eq(InventoryBatch::getMaterialId, materialId)
                        .eq(InventoryBatch::getBatchNo, batchNo)
                        .and(w -> {
                            if (locationId != null) w.eq(InventoryBatch::getLocationId, locationId);
                            else w.isNull(InventoryBatch::getLocationId);
                        })
                        .last("LIMIT 1 FOR UPDATE"));
        if (batch == null) {
            throw new BizException(ErrorCode.INV_004, "批次不存在: " + batchNo);
        }
        return batch;
    }

    /** 入库批次: 存在则行锁, 不存在则创建 (uk 冲突时重入行锁, 防并发建批) */
    private InventoryBatch lockOrCreateBatch(Long warehouseId, InboundLine line) {
        try {
            return lockBatch(warehouseId, line.materialId(), line.batchNo(), line.locationId());
        } catch (BizException notFound) {
            InventoryBatch batch = new InventoryBatch();
            batch.setWarehouseId(warehouseId);
            batch.setLocationId(line.locationId());
            batch.setMaterialId(line.materialId());
            batch.setBatchNo(line.batchNo());
            batch.setProductionDate(line.productionDate());
            batch.setExpiryDate(line.expiryDate());
            batch.setOnHand(0);
            batch.setLockedQty(0);
            batch.setInTransitQty(0);
            batch.setUnitCost(line.unitCost());
            batch.setStatus("NORMAL");
            batch.setVersion(0);
            try {
                batchMapper.insert(batch);
                return batch;
            } catch (DuplicateKeyException concurrent) {
                return lockBatch(warehouseId, line.materialId(), line.batchNo(), line.locationId());
            }
        }
    }

    private void assertBatchUsable(InventoryBatch batch) {
        if (!"NORMAL".equals(batch.getStatus())) {
            throw new BizException(ErrorCode.INV_004, "批次不可用(" + batch.getStatus() + "): " + batch.getBatchNo());
        }
        if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(LocalDate.now())) {
            throw new BizException(ErrorCode.INV_007, "批次已过期: " + batch.getBatchNo());
        }
    }

    /** 乐观锁更新: WHERE id AND version; 0 行 = 并发冲突 */
    private void guardedUpdate(InventoryBatch batch, java.util.function.Consumer<InventoryBatch> mutator) {
        int oldVersion = batch.getVersion();
        mutator.accept(batch);
        batch.setVersion(oldVersion + 1);
        int rows = batchMapper.update(null, new LambdaUpdateWrapper<InventoryBatch>()
                .eq(InventoryBatch::getId, batch.getId())
                .eq(InventoryBatch::getVersion, oldVersion)
                .set(InventoryBatch::getOnHand, batch.getOnHand())
                .set(InventoryBatch::getLockedQty, batch.getLockedQty())
                .set(InventoryBatch::getInTransitQty, batch.getInTransitQty())
                .set(InventoryBatch::getVersion, batch.getVersion()));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_002, "库存版本冲突: batchId=" + batch.getId());
        }
    }

    private Long currentUserId() {
        try {
            return StpUtil.isLogin() ? Long.valueOf(StpUtil.getLoginIdAsString()) : null;
        } catch (Throwable noContext) {
            return null;
        }
    }

    private boolean txnExists(String txnNo) {
        Long count = txnMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventoryTxn>()
                        .eq(InventoryTxn::getTxnNo, txnNo));
        return count != null && count > 0;
    }

    private String txnNo(String sourceNo, Long materialId, String batchNo, String action) {
        return sourceNo + ":" + materialId + ":" + batchNo + ":" + action;
    }

    private void writeTxn(String txnNo, Long warehouseId, Long materialId, String batchNo, int qty,
                          String direction, int before, int after,
                          String sourceType, Long sourceId, String sourceNo) {
        InventoryTxn txn = new InventoryTxn();
        txn.setTxnNo(txnNo);
        txn.setWarehouseId(warehouseId);
        txn.setMaterialId(materialId);
        txn.setBatchNo(batchNo);
        txn.setQty(qty);
        txn.setDirection(direction);
        txn.setBeforeQty(before);
        txn.setAfterQty(after);
        txn.setSourceType(sourceType);
        txn.setSourceId(sourceId);
        txn.setSourceNo(sourceNo);
        txn.setOperatorId(currentUserId());
        txnMapper.insert(txn);
    }
}
