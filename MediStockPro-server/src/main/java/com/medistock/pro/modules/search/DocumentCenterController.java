package com.medistock.pro.modules.search;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.medistock.pro.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 单据中心 (P055)
 */
@Tag(name = "单据中心")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentCenterController {

    private final DocumentCenterService documentCenterService;

    @Operation(summary = "全类型单据统一查询")
    @GetMapping
    @SaCheckPermission("DOCUMENT_VIEW")
    public Result<Map<String, Object>> query(@RequestParam(required = false) String type,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String no,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.success(documentCenterService.query(type, status, no, from, to, page, size));
    }
}
