package com.medistock.pro.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 领用申请明细
 */
@Data
@TableName("issue_request_item")
public class IssueRequestItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long requestId;

    private Long materialId;

    private Integer qty;

    private Integer approvedQty;

    /** 已发数量 */
    private Integer issuedQty;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
