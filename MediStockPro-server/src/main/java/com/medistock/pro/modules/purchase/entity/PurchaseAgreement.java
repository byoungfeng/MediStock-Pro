package com.medistock.pro.modules.purchase.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 采购协议(合同价): DRAFT→EFFECTIVE→TERMINATED/EXPIRED
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("purchase_agreement")
public class PurchaseAgreement extends BaseEntity {

    private String agreementNo;

    private Long supplierId;

    private LocalDate startDate;

    private LocalDate endDate;

    private String payTerms;

    /** DRAFT/EFFECTIVE/TERMINATED */
    private String status;

    private String remark;
}
