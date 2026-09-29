package com.medistock.pro.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/** 调拨收货: 实收 < 发运时 diffReason 必填, 差额按在途损耗核销 */
public record TransferReceiveDTO(
        @NotNull Integer version,
        @NotEmpty @Valid List<Line> items) {

    public record Line(
            @NotNull Long itemId,
            @NotNull @PositiveOrZero Integer receivedQty,
            String diffReason) {}
}
