package com.medistock.pro.modules.alert;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务预警 (表无 created_by/updated_by/deleted; handled_by/at 处置时手动赋值)
 */
@Data
@TableName("alert")
public class Alert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** EXPIRY效期/LOW_STOCK下限/OVERSTOCK超储/ARRIVAL到货逾期 */
    private String type;

    /** 关联对象 (批次/订单) */
    private Long businessId;

    /** INFO/WARN/URGENT */
    private String level;

    private String title;

    private String content;

    private LocalDateTime dueAt;

    /** OPEN待处置/HANDLED已处置/IGNORED已忽略/CLOSED(条件消除自动关闭) */
    private String status;

    private Long handledBy;

    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
