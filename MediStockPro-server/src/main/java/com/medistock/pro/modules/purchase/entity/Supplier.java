package com.medistock.pro.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 供应商
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("supplier")
public class Supplier extends BaseEntity {

    private String code;

    private String name;

    /** 统一社会信用代码 */
    private String creditCode;

    private String contact;

    private String phone;

    private String address;

    /** 风险等级 A/B/C */
    private String riskLevel;

    private Integer status;

    private String remark;
}
