package com.medistock.pro.modules.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导入导出任务 (表无 updated_by/deleted)
 */
@Data
@TableName("import_job")
public class ImportJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobNo;

    /** IMPORT/EXPORT */
    private String type;

    /** MATERIAL/SUPPLIER... */
    private String bizType;

    private String fileId;

    private Integer totalCount;

    private Integer successCount;

    private Integer failCount;

    /** 失败明细 JSON */
    private String failDetail;

    /** RUNNING/SUCCESS/PARTIAL/FAILED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
