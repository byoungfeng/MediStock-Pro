package com.medistock.pro.modules.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.purchase.entity.Acceptance;
import com.medistock.pro.modules.purchase.service.AcceptanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/acceptances")
@RequiredArgsConstructor
public class AcceptanceController {

    private final AcceptanceService acceptanceService;

    @GetMapping
    @SaCheckPermission("PURCHASE_ACCEPTANCE_VIEW")
    public Result<PageResult<Acceptance>> page(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) String status) {
        return Result.success(acceptanceService.pageQuery(page, size, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("PURCHASE_ACCEPTANCE_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(acceptanceService.detail(id));
    }

    @PostMapping("/from-receipt/{receiptId}")
    @SaCheckPermission("PURCHASE_ACCEPTANCE_CREATE")
    public Result<Acceptance> createFromReceipt(@PathVariable Long receiptId) {
        return Result.success(acceptanceService.createFromReceipt(receiptId));
    }

    /** 验收: pass=true 通过(自动生成入库单) / false 整单拒收 */
    @PostMapping("/{id}/act")
    @SaCheckPermission("PURCHASE_ACCEPTANCE_EXECUTE")
    public Result<Map<String, Object>> act(@PathVariable Long id, @Valid @RequestBody ActReq req) {
        Long inboundId = acceptanceService.act(id, req.version(), req.pass(), req.items(), req.remark());
        return Result.success(Map.of("inboundId", inboundId == null ? -1 : inboundId));
    }

    public record ActReq(@NotNull Integer version, boolean pass, String remark,
                         @NotEmpty @Valid List<AcceptanceService.Line> items) {}
}
