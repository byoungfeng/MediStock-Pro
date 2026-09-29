package com.medistock.pro.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.system.entity.OperationLog;
import com.medistock.pro.modules.system.mapper.OperationLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 操作日志查询 (P057)
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogMapper operationLogMapper;

    @Operation(summary = "操作日志分页查询")
    @GetMapping
    @SaCheckPermission("AUDIT_LOG_VIEW")
    public Result<PageResult<OperationLog>> page(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String username,
                                                 @RequestParam(required = false) String module,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Page<OperationLog> result = operationLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<OperationLog>()
                        .like(StringUtils.hasText(username), OperationLog::getUsername, username)
                        .eq(StringUtils.hasText(module), OperationLog::getModule, module)
                        .eq(status != null, OperationLog::getStatus, status)
                        .ge(from != null, OperationLog::getCreatedAt, from == null ? null : from.atStartOfDay())
                        .le(to != null, OperationLog::getCreatedAt, to == null ? null : to.atTime(LocalTime.MAX))
                        .orderByDesc(OperationLog::getId));
        return Result.success(PageResult.of(result));
    }
}
