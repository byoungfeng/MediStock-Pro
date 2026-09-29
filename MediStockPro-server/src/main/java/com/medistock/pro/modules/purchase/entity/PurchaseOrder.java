package com.medistock.pro.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单: DRAFT→PENDING→APPROVED→RECEIVING→COMPLETED / CANCELLED
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_order")
public class PurchaseOrder extends BaseEntity {

    private String orderNo;

    private Long supplierId;

    private Long agreementId;

    private Long requestId;

    /** 收货仓库 */
    private Long warehouseId;

    /** DRAFT/PENDING/APPROVED/RECEIVING/COMPLETED/CANCELLED */
    private String status;

    private Integer totalQty;

    private BigDecimal totalAmount;

    private LocalDate expectDate;

    private Integer version;

    private String remark;
}
