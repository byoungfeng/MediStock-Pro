package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 科室领用申请
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("issue_request")
public class IssueRequest extends BaseEntity {

    private String requestNo;

    private Long departmentId;

    /** 发放仓库 */
    private Long warehouseId;

    /** NORMAL/URGENT */
    private String priority;

    private String purpose;

    /** DRAFT/PENDING/APPROVED/REJECTED/PICKING/COMPLETED */
    private String status;

    private Integer version;
}
