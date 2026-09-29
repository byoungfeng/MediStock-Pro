package com.medistock.pro.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采购申请: DRAFT→PENDING→APPROVED/REJECTED→ORDERED(已转订单) / CANCELLED
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_request")
public class PurchaseRequest extends BaseEntity {

    private String requestNo;

    private Long departmentId;

    /** 目标收货仓库 */
    private Long warehouseId;

    /** DRAFT/PENDING/APPROVED/REJECTED/ORDERED/CANCELLED */
    private String status;

    private String purpose;

    private Integer version;
}
