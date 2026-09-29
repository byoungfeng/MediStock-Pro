package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拣货任务 (审批通过后生成, 同时锁定库存)
 */
@Data
@TableName("pick_task")
public class PickTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;

    /** 来源领用申请ID */
    private Long sourceId;

    private String sourceNo;

    private Long warehouseId;

    /** PICKING拣货中/PICKED待复核/CANCELLED已取消 */
    private String status;

    private Integer version;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
