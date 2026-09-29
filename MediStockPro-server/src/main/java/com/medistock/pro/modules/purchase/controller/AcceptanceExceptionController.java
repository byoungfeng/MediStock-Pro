package com.medistock.pro.modules.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.purchase.entity.AcceptanceException;
import com.medistock.pro.modules.purchase.service.AcceptanceExceptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 验收异常 (P025)
 */
@Tag(name = "验收异常")
@RestController
@RequestMapping("/api/v1/acceptance-exceptions")
@RequiredArgsConstructor
public class AcceptanceExceptionController {

    private final AcceptanceExceptionService exceptionService;

    public record CreateReq(@NotNull(message = "acceptanceId 必填") Long acceptanceId,
                            String type, @NotNull(message = "reason 必填") String reason,
                            String responsibility) {
    }

    public record HandleReq(@NotNull(message = "action 必填") String action, String result) {
    }

    @Operation(summary = "异常单分页")
    @GetMapping
    @SaCheckPermission("ACCEPTANCE_EXCEPTION")
    public Result<PageResult<AcceptanceException>> page(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) Long acceptanceId,
                                                        @RequestParam(required = false) String status) {
        return Result.success(PageResult.of(exceptionService.pageQuery(page, size, acceptanceId, status)));
    }

    @Operation(summary = "登记异常")
    @PostMapping
    @SaCheckPermission("ACCEPTANCE_EXCEPTION")
    public Result<AcceptanceException> create(@Valid @RequestBody CreateReq req) {
        return Result.success(exceptionService.create(req.acceptanceId(), req.type(), req.reason(), req.responsibility()));
    }

    @Operation(summary = "处理异常 (RECTIFY整改 / RESOLVE关闭)")
    @PostMapping("/{id}/handle")
    @SaCheckPermission("ACCEPTANCE_EXCEPTION")
    public Result<Void> handle(@PathVariable Long id, @Valid @RequestBody HandleReq req) {
        exceptionService.handle(id, req.action(), req.result());
        return Result.success();
    }
}
