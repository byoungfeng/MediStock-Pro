package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库存流水 (只增不改; txn_no = 来源单号:明细行:动作 为幂等键)
 */
@Data
@TableName("inventory_txn")
public class InventoryTxn {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String txnNo;

    private Long warehouseId;

    private Long materialId;

    private String batchNo;

    /** 变动数量(正入负出) */
    private Integer qty;

    /** IN/OUT */
    private String direction;

    /** 变动前数量 (IN/OUT=on_hand; LOCK/UNLOCK=locked_qty) */
    private Integer beforeQty;

    private Integer afterQty;

    /** INBOUND/ISSUE/TRANSFER_SHIP/TRANSFER_RECEIVE/ADJUST/SCRAP/RETURN/LOCK/UNLOCK */
    private String sourceType;

    private Long sourceId;

    private String sourceNo;

    private Long operatorId;

    private LocalDateTime createdAt;
}
