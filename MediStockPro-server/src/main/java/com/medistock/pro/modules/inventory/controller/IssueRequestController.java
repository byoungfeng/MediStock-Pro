package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.ApproveDTO;
import com.medistock.pro.modules.inventory.dto.IssueRequestDTO;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.inventory.entity.IssueRequest;
import com.medistock.pro.modules.inventory.service.IssueRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 科室领用申请
 */
@Tag(name = "科室领用申请")
@RestController
@RequestMapping("/api/v1/issue-requests")
@RequiredArgsConstructor
public class IssueRequestController {

    private final IssueRequestService issueRequestService;

    @Operation(summary = "申领列表")
    @GetMapping
    @SaCheckPermission("ISSUE_REQUEST_VIEW")
    public Result<PageResult<IssueRequest>> page(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) Long departmentId,
                                                 @RequestParam(required = false) Long warehouseId) {
        return Result.success(PageResult.of(issueRequestService.pageQuery(page, size, status, departmentId, warehouseId)));
    }

    @Operation(summary = "申领详情(含明细)")
    @GetMapping("/{id}")
    @SaCheckPermission("ISSUE_REQUEST_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(issueRequestService.detail(id));
    }

    @Operation(summary = "创建申领(草稿)")
    @PostMapping
    @SaCheckPermission("ISSUE_REQUEST_EDIT")
    public Result<IssueRequest> create(@Valid @RequestBody IssueRequestDTO dto) {
        return Result.success(issueRequestService.create(dto));
    }

    @Operation(summary = "提交")
    @PostMapping("/{id}/submit")
    @SaCheckPermission("ISSUE_REQUEST_EDIT")
    public Result<Void> submit(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        issueRequestService.submit(id, dto.version());
        return Result.success();
    }

    @Operation(summary = "审批(可改量; 通过后生成拣货任务并锁定库存)")
    @PostMapping("/{id}/approve")
    @SaCheckPermission("ISSUE_REQUEST_APPROVE")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody ApproveDTO dto) {
        if (Boolean.FALSE.equals(dto.pass())) {
            issueRequestService.reject(id, dto.version());
        } else {
            issueRequestService.approve(id, dto);
        }
        return Result.success();
    }
}
