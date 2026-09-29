package com.medistock.pro.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 入库单创建请求
 */
public record InboundDTO(
        @NotNull(message = "仓库必填") Long warehouseId,
        String bizType,
        String remark,
        @NotEmpty(message = "明细不能为空") @Valid List<Line> items
) {
    public record Line(
            @NotNull(message = "物资必填") Long materialId,
            Long locationId,
            @NotNull(message = "批号必填") String batchNo,
            LocalDate productionDate,
            LocalDate expiryDate,
            @NotNull @Positive(message = "数量必须>0") Integer qty,
            BigDecimal unitCost
    ) {}
}
