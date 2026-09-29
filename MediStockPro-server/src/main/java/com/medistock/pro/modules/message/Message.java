package com.medistock.pro.modules.message;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息 (表无 created_by/updated_by/deleted; read 为 MySQL 保留字需反引号)
 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人 */
    private Long userId;

    /** APPROVAL审批/ALERT预警/SYSTEM系统 */
    private String type;

    private String title;

    private String content;

    private String bizType;

    private Long bizId;

    @TableField("`read`")
    private Integer read;

    private LocalDateTime createdAt;
}
