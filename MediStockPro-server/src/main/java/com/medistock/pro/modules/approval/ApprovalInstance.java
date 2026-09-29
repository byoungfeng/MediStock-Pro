package com.medistock.pro.modules.approval;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批实例 (表无 created_by/updated_by/deleted; uk(business_type, business_id))
 */
@Data
@TableName("approval_instance")
public class ApprovalInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** PURCHASE_REQUEST/PURCHASE_ORDER/ISSUE_REQUEST/SCRAP/ADJUST */
    private String businessType;

    private Long businessId;

    /** PENDING/APPROVED/REJECTED/CANCELLED */
    private String status;

    private String currentNode;

    private Long submitterId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
