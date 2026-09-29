package com.medistock.pro.modules.master.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织机构 (集团/医院/院区/科室)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("org_unit")
public class OrgUnit extends BaseEntity {

    private String code;

    private String name;

    /** GROUP/HOSPITAL/CAMPUS/DEPT */
    private String type;

    private Long parentId;

    private Integer status;
}
