package com.medistock.pro.modules.master.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 仓库 (药库/中心药房/门诊药房/住院药房)
 * 注意: 本表无 created_by/updated_by 列, 不继承 BaseEntity
 */
@Data
@TableName("warehouse")
public class Warehouse implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    /** DRUG_DEPOT药库/CENTER_PHARMACY中心药房/OUTPATIENT门诊/INPATIENT住院/GENERAL */
    private String type;

    /** 所属院区/组织 */
    private Long orgId;

    private Long parentId;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
