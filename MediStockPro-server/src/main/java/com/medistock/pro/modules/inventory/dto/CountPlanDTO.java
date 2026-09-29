package com.medistock.pro.modules.inventory.dto;

import jakarta.validation.constraints.NotNull;

/** 新建盘点计划 */
public record CountPlanDTO(
        @NotNull Long warehouseId,
        String scope,
        /** 盘点期间是否冻结仓库批次 (默认 false) */
        Boolean freeze) {}
