package com.medistock.pro.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.medistock.pro.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    private String employeeNo;

    @JsonIgnore
    private String password;

    private String name;

    private Long orgId;

    private String phone;

    private String email;

    private Integer status;

    private LocalDateTime lastLoginAt;
}
