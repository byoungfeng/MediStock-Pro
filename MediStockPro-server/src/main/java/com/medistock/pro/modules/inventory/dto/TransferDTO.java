package com.medistock.pro.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** 新建调拨单 */
public record TransferDTO(
        @NotNull Long fromWarehouseId,
        @NotNull Long toWarehouseId,
        String reason,
        String remark,
        @NotEmpty @Valid List<Line> items) {

    public record Line(
            @NotNull Long materialId,
            @NotNull String batchNo,
            @NotNull @Positive Integer qty) {}
}
