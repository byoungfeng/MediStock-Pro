package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 批次库存 (现存量唯一权威; available = on_hand - locked_qty 派生不落库)
 * 注意: 本表无 deleted/created_by 列, 不继承 BaseEntity
 */
@Data
@TableName("inventory_batch")
public class InventoryBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    private Long locationId;

    private Long materialId;

    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    /** 实际在库 */
    private Integer onHand;

    /** 锁定(拣货/审批占用) */
    private Integer lockedQty;

    /** 调拨在途(本仓调出未达) */
    private Integer inTransitQty;

    private BigDecimal unitCost;

    /** NORMAL/FROZEN冻结/EXPIRED过期/SCRAPPED报废 */
    private String status;

    /** 乐观锁 */
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
