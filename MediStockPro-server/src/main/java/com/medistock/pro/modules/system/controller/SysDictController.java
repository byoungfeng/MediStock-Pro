package com.medistock.pro.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.system.entity.SysDict;
import com.medistock.pro.modules.system.mapper.SysDictMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统字典管理 (sys_dict)
 */
@Tag(name = "系统字典")
@RestController
@RequestMapping("/api/v1/dicts")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictMapper dictMapper;

    public record DictReq(@NotBlank(message = "dictType 必填") String dictType,
                          @NotBlank(message = "dictCode 必填") String dictCode,
                          @NotBlank(message = "dictLabel 必填") String dictLabel,
                          Integer sort) {
    }

    @Operation(summary = "字典列表 (可按类型过滤)")
    @GetMapping
    @SaCheckPermission("SYSTEM_PARAM_MANAGE")
    public Result<List<SysDict>> list(@RequestParam(required = false) String dictType) {
        return Result.success(dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(StringUtils.hasText(dictType), SysDict::getDictType, dictType)
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSort)));
    }

    @Operation(summary = "新增字典项")
    @PostMapping
    @SaCheckPermission("SYSTEM_PARAM_MANAGE")
    @Transactional(rollbackFor = Exception.class)
    public Result<SysDict> create(@Valid @RequestBody DictReq req) {
        assertUnique(req.dictType(), req.dictCode(), null);
        SysDict d = new SysDict();
        d.setDictType(req.dictType());
        d.setDictCode(req.dictCode());
        d.setDictLabel(req.dictLabel());
        d.setSort(req.sort() == null ? 0 : req.sort());
        d.setStatus(1);
        dictMapper.insert(d);
        return Result.success(d);
    }

    @Operation(summary = "编辑字典项 (类型+编码不可变)")
    @PutMapping("/{id}")
    @SaCheckPermission("SYSTEM_PARAM_MANAGE")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@PathVariable Long id, @RequestBody DictReq req) {
        SysDict d = dictMapper.selectById(id);
        if (d == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典项不存在: " + id);
        }
        d.setDictLabel(req.dictLabel());
        if (req.sort() != null) {
            d.setSort(req.sort());
        }
        dictMapper.updateById(d);
        return Result.success();
    }

    @Operation(summary = "启用/停用")
    @PostMapping("/{id}/status")
    @SaCheckPermission("SYSTEM_PARAM_MANAGE")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        SysDict d = dictMapper.selectById(id);
        if (d == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典项不存在: " + id);
        }
        d.setStatus(status);
        dictMapper.updateById(d);
        return Result.success();
    }

    private void assertUnique(String dictType, String dictCode, Long excludeId) {
        Long count = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, dictType)
                .eq(SysDict::getDictCode, dictCode)
                .ne(excludeId != null, SysDict::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "字典项已存在: " + dictType + "/" + dictCode);
        }
    }
}
