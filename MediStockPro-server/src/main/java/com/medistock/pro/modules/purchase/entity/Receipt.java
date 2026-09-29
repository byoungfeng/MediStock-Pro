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
 * 收货单 (到货登记): REGISTERED→ACCEPTED / REJECTED
 * 注: receipt 表无 deleted 列 (生效单据走红冲), 不继承 BaseEntity
 */
@Data
@TableName("receipt")
public class Receipt implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String receiptNo;

    private Long orderId;

    private LocalDate arrivalDate;

    private Integer boxCount;

    private String transportNo;

    /** REGISTERED/ACCEPTED/REJECTED */
    private String status;

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
