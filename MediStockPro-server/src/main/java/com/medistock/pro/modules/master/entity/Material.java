package com.medistock.pro.modules.master.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 物资主数据 (药品=物资子集)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material")
public class Material extends BaseEntity {

    /** 物资编码 (唯一) */
    private String code;

    private String name;

    /** 规格 */
    private String spec;

    /** 基本单位(最小单位) */
    private String uom;

    private String brand;

    /** 生产厂家 */
    private String manufacturer;

    private Long categoryId;

    /** 高值耗材 */
    private Integer isHighValue;

    /** 批号管理 */
    private Integer batchManaged;

    /** 效期管理 */
    private Integer expiryManaged;

    /** UDI 管理 */
    private Integer udiManaged;

    /** 安全库存下限 */
    private Integer safetyQty;

    /** 库存上限(超储预警) */
    private Integer maxQty;

    /** 效期预警天数(空=取系统参数) */
    private Integer expiryAlertDays;

    private Integer status;

    private String remark;
}
