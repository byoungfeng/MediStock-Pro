package com.medistock.pro.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/** 盘点录入: 按明细行填实盘数 */
public record CountEntryDTO(
        @NotNull Integer version,
        @NotEmpty @Valid List<Line> items) {

    public record Line(
            @NotNull Long itemId,
            @NotNull @PositiveOrZero Integer countQty,
            String diffReason) {}
}
