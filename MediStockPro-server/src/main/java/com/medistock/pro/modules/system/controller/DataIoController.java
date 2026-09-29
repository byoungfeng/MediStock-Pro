package com.medistock.pro.modules.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.common.Result;
import com.medistock.pro.modules.master.entity.Material;
import com.medistock.pro.modules.master.service.MaterialService;
import com.medistock.pro.modules.purchase.entity.Supplier;
import com.medistock.pro.modules.purchase.service.SupplierService;
import com.medistock.pro.modules.system.entity.ImportJob;
import com.medistock.pro.modules.system.mapper.ImportJobMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据导入导出 (P060): CSV 格式 (UTF-8 BOM, Excel 兼容)
 */
@Tag(name = "数据导入导出")
@RestController
@RequestMapping("/api/v1/io")
@RequiredArgsConstructor
public class DataIoController {

    private final MaterialService materialService;
    private final SupplierService supplierService;
    private final ImportJobMapper importJobMapper;

    // ==================== 导出 ====================

    @Operation(summary = "导出物资 CSV")
    @GetMapping("/export/materials")
    @SaCheckPermission("DATA_IMPORT_EXPORT")
    public void exportMaterials(HttpServletResponse resp) throws Exception {
        List<Material> list = materialService.lambdaQuery().orderByAsc(Material::getId).list();
        writeCsv(resp, "materials.csv", "编码,名称,规格,单位,品牌,生产厂家,安全库存,库存上限,状态",
                list.stream().map(m -> row(m.getCode(), m.getName(), m.getSpec(), m.getUom(), m.getBrand(),
                        m.getManufacturer(), str(m.getSafetyQty()), str(m.getMaxQty()),
                        m.getStatus() != null && m.getStatus() == 1 ? "启用" : "停用")).toList());
        recordJob("EXPORT", "MATERIAL", list.size(), list.size(), 0, null);
    }

    @Operation(summary = "导出供应商 CSV")
    @GetMapping("/export/suppliers")
    @SaCheckPermission("DATA_IMPORT_EXPORT")
    public void exportSuppliers(HttpServletResponse resp) throws Exception {
        List<Supplier> list = supplierService.lambdaQuery().orderByAsc(Supplier::getId).list();
        writeCsv(resp, "suppliers.csv", "编码,名称,信用代码,联系人,电话,地址,风险等级,状态",
                list.stream().map(s -> row(s.getCode(), s.getName(), s.getCreditCode(), s.getContact(),
                        s.getPhone(), s.getAddress(), s.getRiskLevel(),
                        s.getStatus() != null && s.getStatus() == 1 ? "启用" : "停用")).toList());
        recordJob("EXPORT", "SUPPLIER", list.size(), list.size(), 0, null);
    }

    // ==================== 导入 ====================

    @Operation(summary = "导入物资 CSV (按编码 upsert)")
    @PostMapping(value = "/import/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SaCheckPermission("DATA_IMPORT_EXPORT")
    public Result<ImportJob> importMaterials(@RequestParam("file") MultipartFile file) throws Exception {
        List<String> failures = new ArrayList<>();
        int total = 0, success = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (lineNo == 1) {
                    line = stripBom(line);
                }
                if (line.isBlank()) {
                    continue;
                }
                if (!headerSkipped && line.startsWith("编码")) {
                    headerSkipped = true;
                    continue;
                }
                total++;
                try {
                    String[] cols = line.split(",", -1);
                    if (cols.length < 2 || cols[0].isBlank() || cols[1].isBlank()) {
                        throw new IllegalArgumentException("编码/名称必填");
                    }
                    Material m = new Material();
                    m.setCode(cols[0].trim());
                    m.setName(cols[1].trim());
                    m.setSpec(val(cols, 2));
                    m.setUom(val(cols, 3));
                    m.setBrand(val(cols, 4));
                    m.setManufacturer(val(cols, 5));
                    m.setSafetyQty(intVal(cols, 6));
                    m.setMaxQty(intVal(cols, 7));
                    m.setStatus("停用".equals(val(cols, 8)) ? 0 : 1);
                    Material existing = materialService.lambdaQuery()
                            .eq(Material::getCode, m.getCode()).one();
                    if (existing == null) {
                        materialService.create(m);
                    } else {
                        m.setId(existing.getId());
                        materialService.update(m);
                    }
                    success++;
                } catch (Exception e) {
                    failures.add("第" + lineNo + "行: " + e.getMessage());
                }
            }
        }
        ImportJob job = recordJob("IMPORT", "MATERIAL", total, success, total - success,
                failures.isEmpty() ? null : String.join("; ", failures.stream().limit(50).toList()));
        return Result.success(job);
    }

    @Operation(summary = "导入导出任务分页")
    @GetMapping("/jobs")
    @SaCheckPermission("DATA_IMPORT_EXPORT")
    public Result<PageResult<ImportJob>> jobs(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        Page<ImportJob> p = importJobMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ImportJob>().orderByDesc(ImportJob::getId));
        return Result.success(PageResult.of(p));
    }

    // ==================== 内部 ====================

    private ImportJob recordJob(String type, String bizType, int total, int success, int fail, String failDetail) {
        ImportJob job = new ImportJob();
        job.setJobNo(type.charAt(0) + bizType.substring(0, 2) + System.currentTimeMillis());
        job.setType(type);
        job.setBizType(bizType);
        job.setTotalCount(total);
        job.setSuccessCount(success);
        job.setFailCount(fail);
        job.setFailDetail(failDetail);
        job.setStatus(fail == 0 ? "SUCCESS" : (success == 0 ? "FAILED" : "PARTIAL"));
        importJobMapper.insert(job);
        return job;
    }

    private void writeCsv(HttpServletResponse resp, String filename, String header, List<String> rows) throws Exception {
        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=" + filename);
        PrintWriter writer = resp.getWriter();
        writer.write('﻿'); // BOM: Excel 识别 UTF-8
        writer.println(header);
        rows.forEach(writer::println);
        writer.flush();
    }

    private static String row(String... cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String c = cols[i] == null ? "" : cols[i];
            sb.append(c.contains(",") || c.contains("\"") || c.contains("\n")
                    ? '"' + c.replace("\"", "\"\"") + '"' : c);
        }
        return sb.toString();
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    private static String val(String[] cols, int idx) {
        return idx < cols.length && !cols[idx].isBlank() ? cols[idx].trim() : null;
    }

    private static Integer intVal(String[] cols, int idx) {
        String v = val(cols, idx);
        return v == null ? null : Integer.valueOf(v);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
