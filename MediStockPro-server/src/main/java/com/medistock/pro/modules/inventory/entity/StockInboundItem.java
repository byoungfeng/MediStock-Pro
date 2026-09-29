package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入库明细
 */
@Data
@TableName("stock_inbound_item")
public class StockInboundItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inboundId;

    private Long materialId;

    /** 上架库位 */
    private Long locationId;

    private String batchNo;

    private LocalDate productionDate;

    private LocalDate expiryDate;

    /** 入库数量(=验收合格量) */
    private Integer qty;

    private BigDecimal unitCost;

    private BigDecimal amount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
