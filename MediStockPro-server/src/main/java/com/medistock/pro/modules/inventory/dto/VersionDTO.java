package com.medistock.pro.modules.inventory.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 乐观锁动作请求 (确认/提交/签收等)
 */
public record VersionDTO(
        @NotNull(message = "version 必填") Integer version,
        String signBy,
        String remark
) {}
