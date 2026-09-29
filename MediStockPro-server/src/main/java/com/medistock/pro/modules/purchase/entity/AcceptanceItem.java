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

@Data
@TableName("acceptance_item")
public class AcceptanceItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long acceptanceId;

    private Long materialId;

    private String batchNo;

    private LocalDate expiryDate;

    private Integer receivedQty;

    private Integer acceptedQty;

    private Integer rejectedQty;

    private String rejectReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
