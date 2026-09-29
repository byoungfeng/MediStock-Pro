package com.medistock.pro.modules.master.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.master.entity.Location;
import com.medistock.pro.modules.master.service.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping
    @SaCheckPermission("ORG_MANAGE_VIEW")
    public Result<List<Location>> list(@RequestParam(required = false) Long warehouseId) {
        return Result.success(locationService.listByWarehouse(warehouseId));
    }

    @PostMapping
    @SaCheckPermission("ORG_MANAGE_EDIT")
    public Result<Location> create(@Valid @RequestBody Location location) {
        return Result.success(locationService.create(location));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("ORG_MANAGE_EDIT")
    public Result<Location> update(@PathVariable Long id, @Valid @RequestBody Location location) {
        return Result.success(locationService.update(id, location));
    }

    @PostMapping("/{id}/status")
    @SaCheckPermission("ORG_MANAGE_EDIT")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusReq req) {
        locationService.changeStatus(id, req.status());
        return Result.success();
    }

    public record StatusReq(@NotNull Integer status) {}
}
