package com.medistock.pro.modules.inventory.entity;

import com.medistock.pro.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 入库单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("stock_inbound")
public class StockInbound extends BaseEntity {

    private String inboundNo;

    /** PURCHASE采购/TRANSFER_IN调拨/SURPLUS盘盈/RETURN退库/ADJUST调整 */
    private String bizType;

    private Long acceptanceId;

    private Long sourceId;

    private String sourceNo;

    private Long warehouseId;

    /** PENDING待入库/CONFIRMED已入库/REVERSED */
    private String status;

    private Integer totalQty;

    private BigDecimal totalAmount;

    private Integer version;

    private String remark;
}
