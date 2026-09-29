package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 盘点计划: DRAFT→COUNTING→CONFIRMED / CANCELLED
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("count_plan")
public class CountPlan extends BaseEntity {

    private String planNo;

    private Long warehouseId;

    /** 盘点范围描述 (如 全仓/某分类) */
    private String scope;

    /** 盘点期间冻结仓库批次 (1=冻结, 禁止出库/调拨发运) */
    private Integer freeze;

    /** DRAFT/COUNTING/CONFIRMED/CANCELLED */
    private String status;

    private LocalDateTime snapshotAt;

    private Integer version;
}
