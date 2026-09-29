package com.medistock.pro.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商物资报价 (表无 created_by/updated_by/deleted, 不继承 BaseEntity)
 */
@Data
@TableName("supplier_material")
public class SupplierMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    private Long materialId;

    /** 最近报价 */
    private BigDecimal price;

    /** 税率% */
    private BigDecimal taxRate;

    /** 交期(天) */
    private Integer leadDays;

    /** 最小起订量 */
    private Integer moq;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
