package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.CountEntryDTO;
import com.medistock.pro.modules.inventory.dto.CountPlanDTO;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.inventory.entity.CountPlan;
import com.medistock.pro.modules.inventory.service.CountPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/count-plans")
@RequiredArgsConstructor
public class CountPlanController {

    private final CountPlanService countPlanService;

    @GetMapping
    @SaCheckPermission("STOCK_COUNT_VIEW")
    public Result<PageResult<CountPlan>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) Long warehouseId,
                                              @RequestParam(required = false) String status) {
        return Result.success(countPlanService.pageQuery(page, size, warehouseId, status));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("STOCK_COUNT_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(countPlanService.detail(id));
    }

    @PostMapping
    @SaCheckPermission("STOCK_COUNT_CREATE")
    public Result<CountPlan> create(@Valid @RequestBody CountPlanDTO dto) {
        return Result.success(countPlanService.create(dto));
    }

    @PostMapping("/{id}/start")
    @SaCheckPermission("STOCK_COUNT_EXECUTE")
    public Result<Void> start(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        countPlanService.start(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/entry")
    @SaCheckPermission("STOCK_COUNT_EXECUTE")
    public Result<Void> entry(@PathVariable Long id, @Valid @RequestBody CountEntryDTO dto) {
        countPlanService.entry(id, dto);
        return Result.success();
    }

    @PostMapping("/{id}/confirm")
    @SaCheckPermission("STOCK_COUNT_CONFIRM")
    public Result<Void> confirm(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        countPlanService.confirm(id, dto.version());
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    @SaCheckPermission("STOCK_COUNT_CREATE")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        countPlanService.cancel(id, dto.version());
        return Result.success();
    }
}
