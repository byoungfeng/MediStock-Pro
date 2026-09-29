package com.medistock.pro.modules.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.purchase.entity.PurchaseAgreement;
import com.medistock.pro.modules.purchase.service.PurchaseAgreementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchase-agreements")
@RequiredArgsConstructor
public class PurchaseAgreementController {

    private final PurchaseAgreementService agreementService;

    @GetMapping
    @SaCheckPermission("PURCHASE_AGREEMENT_VIEW")
    public Result<PageResult<PurchaseAgreement>> page(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int size,
                                                      @RequestParam(required = false) Long supplierId,
                                                      @RequestParam(required = false) String status) {
        return Result.success(agreementService.pageQuery(page, size, supplierId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("PURCHASE_AGREEMENT_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(agreementService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("PURCHASE_AGREEMENT_EDIT")
    public Result<PurchaseAgreement> create(@Valid @RequestBody CreateReq req) {
        return Result.success(agreementService.create(req.supplierId(), req.startDate(), req.endDate(),
                req.payTerms(), req.remark(), req.items()));
    }

    @PostMapping("/{id}/activate")
    @SaCheckPermission("PURCHASE_AGREEMENT_EDIT")
    public Result<Void> activate(@PathVariable Long id) {
        agreementService.activate(id);
        return Result.success();
    }

    @PostMapping("/{id}/terminate")
    @SaCheckPermission("PURCHASE_AGREEMENT_EDIT")
    public Result<Void> terminate(@PathVariable Long id) {
        agreementService.terminate(id);
        return Result.success();
    }

    public record CreateReq(@NotNull Long supplierId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                            String payTerms, String remark,
                            @NotEmpty @Valid List<PurchaseAgreementService.Line> items) {}
}
