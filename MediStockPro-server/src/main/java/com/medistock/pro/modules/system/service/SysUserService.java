package com.medistock.pro.modules.system.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.modules.system.entity.SysUser;
import com.medistock.pro.modules.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

@Service
public class SysUserService extends ServiceImpl<SysUserMapper, SysUser> {

    public SysUser getByUsername(String username) {
        return lambdaQuery().eq(SysUser::getUsername, username).one();
    }
}
