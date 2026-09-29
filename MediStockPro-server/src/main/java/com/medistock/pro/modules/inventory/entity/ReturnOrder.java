package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 科室退库单: DRAFT→CONFIRMED / CANCELLED (确认即退回入库)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("return_order")
public class ReturnOrder extends BaseEntity {

    private String returnNo;

    /** 来源出库单 */
    private Long issueId;

    private Long warehouseId;

    private String reason;

    /** DRAFT/CONFIRMED/CANCELLED */
    private String status;

    private Integer version;
}
