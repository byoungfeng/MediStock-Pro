package com.medistock.pro.modules.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数 (表无 created_by/deleted; uk(param_key, version) 版本化)
 */
@Data
@TableName("system_param")
public class SystemParam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paramKey;

    private String paramValue;

    private String paramName;

    /** 发布版本 (回滚用) */
    private Integer version;

    /** 1已发布 0草稿 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
