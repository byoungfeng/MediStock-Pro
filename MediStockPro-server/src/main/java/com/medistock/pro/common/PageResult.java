package com.medistock.pro.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果 (对齐 API 约定: total + records)
 */
@Data
public class PageResult<T> implements Serializable {

    private long total;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.setTotal(page.getTotal());
        r.setRecords(page.getRecords());
        return r;
    }

    /** 记录类型与分页查询不同时使用 (如联表/聚合后的 DTO) */
    public static <T> PageResult<T> of(long total, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.setTotal(total);
        r.setRecords(records);
        return r;
    }
}
