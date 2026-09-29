package com.medistock.pro.modules.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.purchase.entity.Supplier;
import com.medistock.pro.modules.purchase.entity.SupplierQualification;
import com.medistock.pro.modules.purchase.mapper.SupplierMapper;
import com.medistock.pro.modules.purchase.mapper.SupplierQualificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService extends ServiceImpl<SupplierMapper, Supplier> {

    private final SupplierQualificationMapper qualificationMapper;

    public PageResult<Supplier> pageQuery(int page, int size, String keyword, Integer status) {
        Page<Supplier> p = page(new Page<>(page, size), new LambdaQueryWrapper<Supplier>()
                .and(StringUtils.hasText(keyword), w -> w.like(Supplier::getCode, keyword)
                        .or().like(Supplier::getName, keyword))
                .eq(status != null, Supplier::getStatus, status)
                .orderByDesc(Supplier::getId));
        return PageResult.of(p);
    }

    public Supplier create(Supplier supplier) {
        assertUnique(supplier, null);
        supplier.setId(null);
        if (supplier.getStatus() == null) supplier.setStatus(1);
        if (!StringUtils.hasText(supplier.getRiskLevel())) supplier.setRiskLevel("C");
        save(supplier);
        return supplier;
    }

    public Supplier update(Long id, Supplier supplier) {
        Supplier existing = getById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: " + id);
        }
        assertUnique(supplier, id);
        supplier.setId(id);
        updateById(supplier);
        return getById(id);
    }

    // ==================== 资质 (P0: 过期禁采) ====================

    public List<SupplierQualification> qualifications(Long supplierId) {
        expireOutdated(supplierId);
        return qualificationMapper.selectList(new LambdaQueryWrapper<SupplierQualification>()
                .eq(SupplierQualification::getSupplierId, supplierId)
                .orderByDesc(SupplierQualification::getId));
    }

    public SupplierQualification addQualification(Long supplierId, SupplierQualification qual) {
        if (getById(supplierId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: " + supplierId);
        }
        if (qual.getValidTo() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "资质有效期止必填");
        }
        qual.setId(null);
        qual.setSupplierId(supplierId);
        // 状态按有效期自动推导: 过期即 EXPIRED, 否则 EFFECTIVE
        qual.setStatus(qual.getValidTo().isBefore(LocalDate.now()) ? "EXPIRED" : "EFFECTIVE");
        qualificationMapper.insert(qual);
        return qual;
    }

    /** P0 底线: 供应商无有效资质(过期/未登记)时禁止新增采购订单 */
    public void assertSupplierQualified(Long supplierId) {
        expireOutdated(supplierId);
        Long valid = qualificationMapper.selectCount(new LambdaQueryWrapper<SupplierQualification>()
                .eq(SupplierQualification::getSupplierId, supplierId)
                .eq(SupplierQualification::getStatus, "EFFECTIVE")
                .ge(SupplierQualification::getValidTo, LocalDate.now()));
        if (valid == 0) {
            throw new BizException(ErrorCode.SUP_001, "供应商 " + supplierId + " 无有效资质(过期或未登记), 禁止新增采购订单");
        }
    }

    /** 惰性过期: 把已过期但仍标 EFFECTIVE 的资质刷为 EXPIRED */
    private void expireOutdated(Long supplierId) {
        qualificationMapper.update(null, new LambdaUpdateWrapper<SupplierQualification>()
                .eq(SupplierQualification::getSupplierId, supplierId)
                .eq(SupplierQualification::getStatus, "EFFECTIVE")
                .lt(SupplierQualification::getValidTo, LocalDate.now())
                .set(SupplierQualification::getStatus, "EXPIRED"));
    }

    private void assertUnique(Supplier supplier, Long excludeId) {
        Long codeCount = count(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getCode, supplier.getCode())
                .ne(excludeId != null, Supplier::getId, excludeId));
        if (codeCount > 0) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "供应商编码已存在: " + supplier.getCode());
        }
        Long creditCount = count(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getCreditCode, supplier.getCreditCode())
                .ne(excludeId != null, Supplier::getId, excludeId));
        if (creditCount > 0) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "信用代码已存在: " + supplier.getCreditCode());
        }
    }
}
