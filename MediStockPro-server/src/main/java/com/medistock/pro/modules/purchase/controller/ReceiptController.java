package com.medistock.pro.modules.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.purchase.entity.Receipt;
import com.medistock.pro.modules.purchase.service.ReceiptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping
    @SaCheckPermission("PURCHASE_RECEIPT_VIEW")
    public Result<PageResult<Receipt>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) Long orderId,
                                            @RequestParam(required = false) String status) {
        return Result.success(receiptService.pageQuery(page, size, orderId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("PURCHASE_RECEIPT_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(receiptService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("PURCHASE_RECEIPT_REGISTER")
    public Result<Receipt> register(@Valid @RequestBody RegisterReq req) {
        return Result.success(receiptService.register(req.orderId(), req.arrivalDate(),
                req.transportNo(), req.boxCount(), req.remark(), req.items()));
    }

    public record RegisterReq(@NotNull Long orderId, @NotNull LocalDate arrivalDate,
                              String transportNo, Integer boxCount, String remark,
                              @NotEmpty @Valid List<ReceiptService.Line> items) {}
}
