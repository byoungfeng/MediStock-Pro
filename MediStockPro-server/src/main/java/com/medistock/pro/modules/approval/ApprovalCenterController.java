package com.medistock.pro.modules.approval;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批中心 (P056)
 */
@Tag(name = "审批中心")
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalCenterController {

    private final ApprovalCenterService approvalCenterService;
    private final ApprovalTrailService approvalTrailService;

    public record ActionReq(@NotBlank(message = "bizType 必填") String bizType,
                            @NotNull(message = "bizId 必填") Long bizId,
                            @NotNull(message = "pass 必填") Boolean pass,
                            String reason) {
    }

    @Operation(summary = "统一待办列表")
    @GetMapping("/todo")
    @SaCheckPermission("APPROVAL_CENTER")
    public Result<List<ApprovalCenterService.TodoItem>> todo() {
        return Result.success(approvalCenterService.todo());
    }

    @Operation(summary = "统一审批动作 (同意/驳回)")
    @PostMapping("/action")
    @SaCheckPermission("APPROVAL_CENTER")
    public Result<Void> action(@Valid @RequestBody ActionReq req) {
        approvalCenterService.action(req.bizType(), req.bizId(), req.pass(), req.reason());
        return Result.success();
    }

    @Operation(summary = "审批留痕 (实例+记录)")
    @GetMapping("/trail")
    @SaCheckPermission("APPROVAL_CENTER")
    public Result<java.util.Map<String, Object>> trail(@RequestParam String businessType,
                                                       @RequestParam Long businessId) {
        return Result.success(approvalTrailService.trail(businessType, businessId));
    }
}
