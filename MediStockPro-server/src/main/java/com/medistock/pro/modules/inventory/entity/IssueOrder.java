package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出库单 (复核通过后确认出库: 扣 on_hand 并释放锁定)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("issue_order")
public class IssueOrder extends BaseEntity {

    private String issueNo;

    /** 来源领用申请 */
    private Long requestId;

    private Long pickTaskId;

    private Long warehouseId;

    /** 领用科室 */
    private Long departmentId;

    /** PENDING待复核/CONFIRMED已出库/SIGNED已签收/REVERSED */
    private String status;

    private Integer totalQty;

    private BigDecimal totalAmount;

    private String signBy;

    private LocalDateTime signTime;

    private Integer version;

    private String remark;
}
