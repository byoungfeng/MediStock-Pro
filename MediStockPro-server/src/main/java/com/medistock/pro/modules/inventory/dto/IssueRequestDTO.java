package com.medistock.pro.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 领用申请创建请求
 */
public record IssueRequestDTO(
        @NotNull(message = "科室必填") Long departmentId,
        @NotNull(message = "发放仓库必填") Long warehouseId,
        String priority,
        String purpose,
        @NotEmpty(message = "明细不能为空") @Valid List<Line> items
) {
    public record Line(
            @NotNull(message = "物资必填") Long materialId,
            @NotNull @Positive(message = "数量必须>0") Integer qty
    ) {}
}
