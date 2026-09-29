package com.medistock.pro.modules.master.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 包装换算 (表无 created_by/updated_by/deleted, 不继承 BaseEntity)
 */
@Data
@TableName("material_uom")
public class MaterialUom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long materialId;

    /** 源单位(如 箱) */
    private String fromUom;

    /** 目标单位(如 盒) */
    private String toUom;

    /** 换算率(1 from = rate to), DDL CHECK rate>0 */
    private BigDecimal rate;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
