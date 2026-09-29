package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 调拨单: DRAFT→PENDING→APPROVED→SHIPPED→RECEIVED / CANCELLED
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("transfer_order")
public class TransferOrder extends BaseEntity {

    private String transferNo;

    private Long fromWarehouseId;

    private Long toWarehouseId;

    private String reason;

    /** DRAFT/PENDING/APPROVED/SHIPPED/RECEIVED/CANCELLED */
    private String status;

    private LocalDateTime shipTime;

    private LocalDateTime receiveTime;

    private Integer version;

    private String remark;
}
