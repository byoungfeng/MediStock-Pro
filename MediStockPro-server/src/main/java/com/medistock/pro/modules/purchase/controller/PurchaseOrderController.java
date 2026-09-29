package com.medistock.pro.modules.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.purchase.entity.PurchaseOrder;
import com.medistock.pro.modules.purchase.service.PurchaseOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    @SaCheckPermission("PURCHASE_ORDER_VIEW")
    public Result<PageResult<PurchaseOrder>> page(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) Long supplierId,
                                                  @RequestParam(required = false) String status) {
        return Result.success(purchaseOrderService.pageQuery(page, size, supplierId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("PURCHASE_ORDER_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(purchaseOrderService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("PURCHASE_ORDER_CREATE")
    public Result<PurchaseOrder> create(@Valid @RequestBody CreateReq req) {
        return Result.success(purchaseOrderService.create(req.supplierId(), req.warehouseId(),
                req.expectDate(), req.remark(), req.items(), null, req.agreementId()));
    }

    @PostMapping("/{id}/submit")
    @SaCheckPermission("PURCHASE_ORDER_CREATE")
    public Result<Void> submit(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        purchaseOrderService.submit(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    @SaCheckPermission("PURCHASE_ORDER_APPROVE")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        purchaseOrderService.approve(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission("PURCHASE_ORDER_CREATE")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        purchaseOrderService.cancel(id, dto.version());
        return Result.success();
    }

    public record CreateReq(@NotNull Long supplierId, @NotNull Long warehouseId,
                            LocalDate expectDate, String remark, Long agreementId,
                            @NotEmpty @Valid List<PurchaseOrderService.Line> items) {}
}
