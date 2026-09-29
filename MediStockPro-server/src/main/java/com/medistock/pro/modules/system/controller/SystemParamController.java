package com.medistock.pro.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.system.entity.SystemParam;
import com.medistock.pro.modules.system.service.SystemParamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统参数 (P058): 版本化编辑/发布/回滚
 */
@Tag(name = "系统参数")
@RestController
@RequestMapping("/api/v1/system-params")
@RequiredArgsConstructor
public class SystemParamController {

    private final SystemParamService paramService;

    public record PublishReq(@NotBlank(message = "paramKey 必填") String paramKey,
                             @NotNull(message = "paramValue 必填") String paramValue,
                             String paramName) {
    }

    public record RollbackReq(@NotNull(message = "version 必填") Integer version) {
    }

    @Operation(summary = "生效参数列表")
    @GetMapping
    @SaCheckPermission("SYSTEM_PARAM_MANAGE")
    public Result<List<SystemParam>> list() {
        return Result.success(paramService.listEffective());
    }

    @Operation(summary = "参数版本历史")
    @GetMapping("/{key}/versions")
    @SaCheckPermission("SYSTEM_PARAM_MANAGE")
    public Result<List<SystemParam>> versions(@PathVariable String key) {
        return Result.success(paramService.versions(key));
    }

    @Operation(summary = "发布(新建版本)")
    @PutMapping
    @SaCheckPermission("SYSTEM_PARAM_MANAGE")
    public Result<SystemParam> publish(@Valid @RequestBody PublishReq req) {
        return Result.success(paramService.publish(req.paramKey(), req.paramValue(), req.paramName()));
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/{key}/rollback")
    @SaCheckPermission("SYSTEM_PARAM_MANAGE")
    public Result<SystemParam> rollback(@PathVariable String key, @Valid @RequestBody RollbackReq req) {
        return Result.success(paramService.rollback(key, req.version()));
    }
}
