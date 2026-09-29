package com.medistock.pro.modules.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-仓库数据权限绑定 (表无 created_by/updated_by/deleted)
 */
@Data
@TableName("sys_role_warehouse")
public class SysRoleWarehouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long warehouseId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
