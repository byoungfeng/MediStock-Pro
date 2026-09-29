package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拣货明细 (FEFO 推荐批次; 手工改派需 override_reason)
 */
@Data
@TableName("pick_task_item")
public class PickTaskItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long materialId;

    private Long locationId;

    /** FEFO 推荐批号 */
    private String batchNo;

    /** 应拣数量 */
    private Integer suggestedQty;

    /** 实拣数量 */
    private Integer pickedQty;

    /** 短拣原因 */
    private String shortReason;

    /** 非 FEFO 改派原因 */
    private String overrideReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
