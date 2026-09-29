package com.medistock.pro.modules.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medistock.pro.modules.message.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
