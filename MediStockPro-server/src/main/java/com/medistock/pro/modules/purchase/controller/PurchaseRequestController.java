package com.medistock.pro.modules.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.purchase.entity.PurchaseOrder;
import com.medistock.pro.modules.purchase.entity.PurchaseRequest;
import com.medistock.pro.modules.purchase.service.PurchaseRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final PurchaseRequestService requestService;

    @GetMapping
    @SaCheckPermission("PURCHASE_REQUEST_VIEW")
    public Result<PageResult<PurchaseRequest>> page(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size,
                                                    @RequestParam(required = false) Long departmentId,
                                                    @RequestParam(required = false) String status) {
        return Result.success(requestService.pageQuery(page, size, departmentId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("PURCHASE_REQUEST_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(requestService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("PURCHASE_REQUEST_EDIT")
    public Result<PurchaseRequest> create(@Valid @RequestBody CreateReq req) {
        return Result.success(requestService.create(req.departmentId(), req.warehouseId(), req.purpose(), req.items()));
    }

    @PostMapping("/{id}/submit")
    @SaCheckPermission("PURCHASE_REQUEST_EDIT")
    public Result<Void> submit(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        requestService.submit(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    @SaCheckPermission("PURCHASE_REQUEST_APPROVE")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody ApproveReq req) {
        requestService.approve(id, req.version(), req.pass(), req.approvedQtys());
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission("PURCHASE_REQUEST_EDIT")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        requestService.cancel(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/to-order")
    @SaCheckPermission("PURCHASE_REQUEST_EDIT")
    public Result<PurchaseOrder> toOrder(@PathVariable Long id, @Valid @RequestBody ToOrderReq req) {
        return Result.success(requestService.toOrder(id, req.version(), req.supplierId(), req.expectDate(), req.prices()));
    }

    public record CreateReq(@NotNull Long departmentId, @NotNull Long warehouseId, String purpose,
                            @NotEmpty @Valid java.util.List<PurchaseRequestService.Line> items) {}

    public record ApproveReq(@NotNull Integer version, @NotNull Boolean pass,
                             Map<Long, Integer> approvedQtys) {}

    public record ToOrderReq(@NotNull Integer version, @NotNull Long supplierId,
                             LocalDate expectDate, Map<Long, BigDecimal> prices) {}
}
