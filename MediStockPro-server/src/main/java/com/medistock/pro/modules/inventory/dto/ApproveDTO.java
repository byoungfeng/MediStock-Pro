package com.medistock.pro.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 审批请求 (可改量; items 为空 = 按申请量全批)
 */
public record ApproveDTO(
        @NotNull(message = "version 必填") Integer version,
        Boolean pass,
        String reason,
        @Valid List<Line> items
) {
    public record Line(
            @NotNull Long itemId,
            @NotNull Integer approvedQty
    ) {}
}
