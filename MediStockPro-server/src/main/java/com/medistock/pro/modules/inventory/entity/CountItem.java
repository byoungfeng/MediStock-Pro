package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 盘点明细: book_qty=快照账面, count_qty=实盘, diff_qty=差异(实盘-账面)
 */
@Data
@TableName("count_item")
public class CountItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long materialId;

    private Long locationId;

    private String batchNo;

    private Integer bookQty;

    private Integer countQty;

    private Integer diffQty;

    private String diffReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
