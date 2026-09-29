package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.TransferDTO;
import com.medistock.pro.modules.inventory.dto.TransferReceiveDTO;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.inventory.entity.TransferOrder;
import com.medistock.pro.modules.inventory.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @GetMapping
    @SaCheckPermission("STOCK_TRANSFER_VIEW")
    public Result<PageResult<TransferOrder>> page(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) Long fromWarehouseId,
                                                  @RequestParam(required = false) Long toWarehouseId,
                                                  @RequestParam(required = false) String status) {
        return Result.success(transferService.pageQuery(page, size, fromWarehouseId, toWarehouseId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("STOCK_TRANSFER_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(transferService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("STOCK_TRANSFER_CREATE")
    public Result<TransferOrder> create(@Valid @RequestBody TransferDTO dto) {
        return Result.success(transferService.create(dto));
    }

    @PostMapping("/{id}/submit")
    @SaCheckPermission("STOCK_TRANSFER_CREATE")
    public Result<Void> submit(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        transferService.submit(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/approve")
    @SaCheckPermission("STOCK_TRANSFER_APPROVE")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        transferService.approve(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/ship")
    @SaCheckPermission("STOCK_TRANSFER_EXECUTE")
    public Result<Void> ship(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        transferService.ship(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/receive")
    @SaCheckPermission("STOCK_TRANSFER_EXECUTE")
    public Result<Void> receive(@PathVariable Long id, @Valid @RequestBody TransferReceiveDTO dto) {
        transferService.receive(id, dto);
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission("STOCK_TRANSFER_CREATE")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        transferService.cancel(id, dto.version());
        return Result.success();
    }
}
