package com.medistock.pro.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商资质 (supplier_qualification 表无 deleted 列, 不继承 BaseEntity)
 * status: EFFECTIVE 有效 / EXPIRED 已过期 / INVALID 作废
 */
@Data
@TableName("supplier_qualification")
public class SupplierQualification implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    /** 资质类型: BUSINESS_LICENSE 营业执照 / PRODUCTION_LICENSE 生产许可 / OPERATION_LICENSE 经营许可 / GSP 认证等 */
    private String type;

    private String certNo;

    private String fileId;

    private LocalDate validFrom;

    /** 有效期止 (NOT NULL, P0 禁采判定依据) */
    private LocalDate validTo;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
