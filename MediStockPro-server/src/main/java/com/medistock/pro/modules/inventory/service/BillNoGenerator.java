package com.medistock.pro.modules.inventory.service;

import com.medistock.pro.modules.inventory.mapper.BillNoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 单据编号生成: 前缀 + yyMMdd + '-' + 4位序号 (如 RK260922-0001)
 * 并发兜底: 表上 uk 唯一索引, 冲突时由全局异常返回 DUPLICATE_KEY, 客户端重试
 */
@Component
@RequiredArgsConstructor
public class BillNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private final BillNoMapper billNoMapper;

    public String next(String prefix, String table, String column) {
        String dayPrefix = prefix + LocalDate.now().format(DAY);
        String maxNo = billNoMapper.findMaxNo(table, column, dayPrefix);
        int seq = 1;
        if (maxNo != null && maxNo.length() >= 4) {
            seq = Integer.parseInt(maxNo.substring(maxNo.length() - 4)) + 1;
        }
        return dayPrefix + "-" + String.format("%04d", seq);
    }
}
