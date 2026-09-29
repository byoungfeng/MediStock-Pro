package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报废单: PENDING→CONFIRMED / CANCELLED (审批即核销库存)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("scrap_order")
public class ScrapOrder extends BaseEntity {

    private String scrapNo;

    private Long warehouseId;

    private String reason;

    private String attachments;

    /** PENDING/CONFIRMED/CANCELLED */
    private String status;

    private Integer version;
}
