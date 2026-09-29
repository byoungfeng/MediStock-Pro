package com.medistock.pro.modules.inventory.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 单号生成查询 (table/column 仅传内部常量)
 */
@Mapper
public interface BillNoMapper {

    @Select("SELECT ${column} FROM ${table} WHERE ${column} LIKE CONCAT(#{prefix}, '%') ORDER BY ${column} DESC LIMIT 1")
    String findMaxNo(@Param("table") String table, @Param("column") String column, @Param("prefix") String prefix);
}
