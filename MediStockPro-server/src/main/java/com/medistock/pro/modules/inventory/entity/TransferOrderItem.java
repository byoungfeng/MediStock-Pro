package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 调拨单明细 (qty=申请量, shipped_qty=发运量, received_qty=实收量)
 */
@Data
@TableName("transfer_order_item")
public class TransferOrderItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long transferId;

    private Long materialId;

    private String batchNo;

    private Integer qty;

    private Integer shippedQty;

    private Integer receivedQty;

    /** 收发差异原因 (received < shipped 时必填) */
    private String diffReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
