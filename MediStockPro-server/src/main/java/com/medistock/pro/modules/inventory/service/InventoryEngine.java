package com.medistock.pro.modules.inventory.service;

import java.util.List;

/**
 * 库存引擎 (V3.0《库存引擎设计》—— 所有库存变动的唯一入口)
 *
 * 铁律:
 *  1. 业务模块禁止直接 UPDATE inventory_batch, 一切变动经本引擎
 *  2. 每次变动与库存批次更新同一事务写 inventory_txn (只增不改)
 *  3. available = on_hand - locked_qty (派生, 不落库, 禁改)
 *  4. 并发: 批次行 SELECT ... FOR UPDATE 串行化 + version 乐观锁兜底; 冲突抛 INV_002
 *  5. 幂等: txn_no = 来源单号:明细行:动作 (uk_txn_no); 已存在的动作跳过, 重复提交不重复扣减
 *  6. FEFO: 出库/拣货按 expiry_date 升序(NULL 最后)分配; 冻结/过期/报废批次不参与 (INV_004)
 */
public interface InventoryEngine {

    /** 入库: 建/增批次 on_hand, 写流水 (来源: 验收/调拨/盘盈/退库/调整) */
    void inbound(Long warehouseId, List<InboundLine> lines, String sourceType, Long sourceId, String sourceNo);

    /** 锁定: 审批通过生成拣货任务; 只减 available, 不动 on_hand; 返回拣货任务ID */
    Long reserve(Long warehouseId, List<OutboundLine> lines, String sourceType, Long sourceId, String sourceNo);

    /** 释放锁定: 取消/驳回, 仅释放本任务当前持有量 (已生成出库单的任务禁止释放) */
    void release(Long reservationId);

    /** 部分释放: 短拣差额释放 (txn 粒度到拣货明细行) */
    void releaseQty(Long taskId, Long taskItemId, int qty);

    /** 出库: 校验锁定量, 扣 on_hand 释放 locked, 写流水 */
    void outbound(Long warehouseId, List<OutboundLine> lines, String sourceType, Long sourceId, String sourceNo);

    /** 调拨发运: 调出仓 on_hand -> in_transit */
    void transferShip(Long fromWarehouseId, List<OutboundLine> lines, Long transferId, String transferNo);

    /** 调拨接收: 调入仓建/增批次, 调出仓 in_transit 核减 */
    void transferReceive(Long fromWarehouseId, Long toWarehouseId, List<InboundLine> lines, Long transferId, String transferNo);

    /** 调拨在途损耗核销: 实收 < 发运时, 差额从在途核销(记 TRANSFER_LOSS 流水) */
    void transferLoss(Long fromWarehouseId, Long materialId, String batchNo, int qty, Long transferId, String transferNo);

    /** 核销出库 (报废/退货出): 直接减 on_hand, 不走锁定; 允许过期批次, 禁止冻结批次; 可用量必须足够 */
    void writeOff(Long warehouseId, List<OutboundLine> lines, String sourceType, Long sourceId, String sourceNo);

    /** 库存调整: 按差异增减 (来源: 盘点/手工) */
    void adjust(Long warehouseId, List<AdjustLine> lines, String sourceType, Long sourceId, String sourceNo);

    /** FEFO 预分配试算: 按效期升序给出批次建议 (不出库) */
    List<FefoSuggestion> fefoAllocate(Long warehouseId, Long materialId, int qty);

    record InboundLine(Long materialId, Long locationId, String batchNo,
                       java.time.LocalDate productionDate, java.time.LocalDate expiryDate,
                       int qty, java.math.BigDecimal unitCost) {}

    record OutboundLine(Long materialId, Long locationId, String batchNo, int qty, String overrideReason) {}

    record AdjustLine(Long materialId, String batchNo, int diffQty) {}

    record FefoSuggestion(Long batchId, String batchNo, java.time.LocalDate expiryDate, int suggestQty) {}
}
