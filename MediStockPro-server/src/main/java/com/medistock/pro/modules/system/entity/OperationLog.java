package com.medistock.pro.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志 (只增不改; 表无 created_by/updated_by/deleted, 不继承 BaseEntity)
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String module;

    private String action;

    private String businessId;

    private String method;

    private String uri;

    private String ip;

    private Integer status;

    private String errorMsg;

    private LocalDateTime createdAt;
}
