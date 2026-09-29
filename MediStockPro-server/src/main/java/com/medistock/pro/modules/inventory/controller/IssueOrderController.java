package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.inventory.entity.IssueOrder;
import com.medistock.pro.modules.inventory.service.IssueOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 出库单
 */
@Tag(name = "出库单")
@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class IssueOrderController {

    private final IssueOrderService issueOrderService;

    @Operation(summary = "出库单列表")
    @GetMapping
    @SaCheckPermission("ISSUE_CONFIRM")
    public Result<PageResult<IssueOrder>> page(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) Long warehouseId,
                                               @RequestParam(required = false) Long departmentId) {
        return Result.success(PageResult.of(issueOrderService.pageQuery(page, size, status, warehouseId, departmentId)));
    }

    @Operation(summary = "出库单详情(含明细)")
    @GetMapping("/{id}")
    @SaCheckPermission("ISSUE_CONFIRM")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(issueOrderService.detail(id));
    }

    @Operation(summary = "复核出库(批次/数量与锁定一致; 扣减并释放锁定)")
    @PostMapping("/{id}/confirm")
    @SaCheckPermission("ISSUE_CONFIRM")
    public Result<Void> confirm(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        issueOrderService.confirm(id, dto.version());
        return Result.success();
    }

    @Operation(summary = "科室签收(闭环)")
    @PostMapping("/{id}/sign")
    @SaCheckPermission("ISSUE_CONFIRM")
    public Result<Void> sign(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        issueOrderService.sign(id, dto.version(), dto.signBy());
        return Result.success();
    }

    @Operation(summary = "红冲(仅已签收; 库存按原批次入回, 申请已发量回退)")
    @PostMapping("/{id}/reverse")
    @SaCheckPermission("ISSUE_REVERSE")
    public Result<Void> reverse(@PathVariable Long id, @Valid @RequestBody ReverseReq req) {
        issueOrderService.reverse(id, req.version(), req.reason());
        return Result.success();
    }

    public record ReverseReq(@NotNull Integer version, String reason) {}
}
