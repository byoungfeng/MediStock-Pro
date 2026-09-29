package com.medistock.pro.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 验收单: PENDING→PASSED / REJECTED (通过时自动生成待确认入库单)
 * 注: acceptance 表无 deleted 列, 不继承 BaseEntity
 */
@Data
@TableName("acceptance")
public class Acceptance implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String acceptanceNo;

    private Long receiptId;

    /** PENDING/PASSED/REJECTED */
    private String status;

    private String result;

    private Integer version;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
