package com.medistock.pro.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medistock.pro.modules.system.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
