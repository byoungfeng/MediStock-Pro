package com.medistock.pro.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 验收异常单 (P025): OPEN -> RECTIFYING -> RESOLVED
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acceptance_exception")
public class AcceptanceException extends BaseEntity {

    private String exceptionNo;

    private Long acceptanceId;

    /** QUALITY/QUANTITY/DAMAGE/DOCUMENT/OTHER */
    private String type;

    private String reason;

    /** SUPPLIER/LOGISTICS/HOSPITAL/OTHER */
    private String responsibility;

    private String result;

    /** OPEN/RECTIFYING/RESOLVED */
    private String status;
}
