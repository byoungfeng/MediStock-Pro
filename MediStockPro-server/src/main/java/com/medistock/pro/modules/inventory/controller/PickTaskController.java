package com.medistock.pro.modules.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.inventory.dto.PickCompleteDTO;
import com.medistock.pro.modules.inventory.dto.VersionDTO;
import com.medistock.pro.modules.inventory.entity.PickTask;
import com.medistock.pro.modules.inventory.service.PickTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 拣货任务
 */
@Tag(name = "拣货任务")
@RestController
@RequestMapping("/api/v1/pick-tasks")
@RequiredArgsConstructor
public class PickTaskController {

    private final PickTaskService pickTaskService;

    @Operation(summary = "拣货任务列表(FEFO 顺序)")
    @GetMapping
    @SaCheckPermission("PICK_EXECUTE_VIEW")
    public Result<PageResult<PickTask>> page(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Long warehouseId) {
        return Result.success(PageResult.of(pickTaskService.pageQuery(page, size, status, warehouseId)));
    }

    @Operation(summary = "拣货任务详情(含明细)")
    @GetMapping("/{id}")
    @SaCheckPermission("PICK_EXECUTE_VIEW")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(pickTaskService.detail(id));
    }

    @Operation(summary = "完成拣货(实拣<=锁定; 短拣必填原因; 生成出库单)")
    @PostMapping("/{id}/complete")
    @SaCheckPermission("PICK_EXECUTE")
    public Result<Void> complete(@PathVariable Long id, @Valid @RequestBody PickCompleteDTO dto) {
        pickTaskService.complete(id, dto);
        return Result.success();
    }

    @Operation(summary = "取消拣货(完整释放本任务锁定)")
    @PostMapping("/{id}/cancel")
    @SaCheckPermission("PICK_EXECUTE")
    public Result<Void> cancel(@PathVariable Long id, @Valid @RequestBody VersionDTO dto) {
        pickTaskService.cancel(id, dto.version());
        return Result.success();
    }
}
