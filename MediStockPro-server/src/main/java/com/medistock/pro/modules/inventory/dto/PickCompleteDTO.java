package com.medistock.pro.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 拣货完成请求 (实拣≤应拣; 短拣必填原因; 非FEFO改派必填 overrideReason)
 */
public record PickCompleteDTO(
        @NotNull(message = "version 必填") Integer version,
        @NotEmpty @Valid List<Line> items
) {
    public record Line(
            @NotNull Long itemId,
            @NotNull Integer pickedQty,
            String shortReason,
            String overrideReason
    ) {}
}
