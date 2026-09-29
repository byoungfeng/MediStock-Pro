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
 * 库位 (仓内 区/架/位)。location 表无 created_by/updated_by 列, 不继承 BaseEntity
 */
@Data
@TableName("location")
public class Location implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    /** 区 */
    private String zone;

    /** 架 */
    private String shelf;

    /** 库位编码 (仓内唯一) */
    private String code;

    private Integer capacity;

    /** 1 启用 / 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
