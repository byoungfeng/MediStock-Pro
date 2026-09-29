package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出库明细
 */
@Data
@TableName("issue_order_item")
public class IssueOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long issueId;

    private Long materialId;

    private String batchNo;

    private Integer qty;

    private BigDecimal unitCost;

    private BigDecimal amount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
