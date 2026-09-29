package com.medistock.pro.modules.search;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 全局搜索 (P003)
 */
@Tag(name = "全局搜索")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "全局搜索: 物资/供应商/批次/单据 分组命中")
    @GetMapping
    @SaCheckPermission("SEARCH_VIEW")
    public Result<Map<String, Object>> search(@RequestParam String keyword) {
        return Result.success(searchService.search(keyword.trim()));
    }
}
