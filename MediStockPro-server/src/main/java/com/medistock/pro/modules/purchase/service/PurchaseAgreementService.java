package com.medistock.pro.modules.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.inventory.service.BillNoGenerator;
import com.medistock.pro.modules.purchase.entity.PurchaseAgreement;
import com.medistock.pro.modules.purchase.entity.PurchaseAgreementItem;
import com.medistock.pro.modules.purchase.mapper.PurchaseAgreementItemMapper;
import com.medistock.pro.modules.purchase.mapper.PurchaseAgreementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购协议: DRAFT→EFFECTIVE→TERMINATED; EFFECTIVE 且当前日期在 [start,end] 内才可被订单引用
 */
@Service
@RequiredArgsConstructor
public class PurchaseAgreementService extends ServiceImpl<PurchaseAgreementMapper, PurchaseAgreement> {

    private final PurchaseAgreementItemMapper itemMapper;
    private final BillNoGenerator billNoGenerator;

    public PageResult<PurchaseAgreement> pageQuery(int page, int size, Long supplierId, String status) {
        Page<PurchaseAgreement> p = page(new Page<>(page, size), new LambdaQueryWrapper<PurchaseAgreement>()
                .eq(supplierId != null, PurchaseAgreement::getSupplierId, supplierId)
                .eq(status != null && !status.isBlank(), PurchaseAgreement::getStatus, status)
                .orderByDesc(PurchaseAgreement::getId));
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long id) {
        PurchaseAgreement master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购协议不存在: " + id);
        }
        List<PurchaseAgreementItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseAgreementItem>().eq(PurchaseAgreementItem::getAgreementId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseAgreement create(Long supplierId, LocalDate startDate, LocalDate endDate,
                                    String payTerms, String remark, List<Line> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "协议明细不能为空");
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "协议起止日期非法");
        }
        PurchaseAgreement agreement = new PurchaseAgreement();
        agreement.setAgreementNo(billNoGenerator.next("XY", "purchase_agreement", "agreement_no"));
        agreement.setSupplierId(supplierId);
        agreement.setStartDate(startDate);
        agreement.setEndDate(endDate);
        agreement.setPayTerms(payTerms);
        agreement.setRemark(remark);
        agreement.setStatus("DRAFT");
        save(agreement);

        for (Line line : items) {
            if (line.price() == null || line.price().signum() < 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "协议价必须>=0");
            }
            PurchaseAgreementItem item = new PurchaseAgreementItem();
            item.setAgreementId(agreement.getId());
            item.setMaterialId(line.materialId());
            item.setPrice(line.price());
            item.setTaxRate(line.taxRate());
            itemMapper.insert(item);
        }
        return agreement;
    }

    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        guardStatus(id, "DRAFT", "EFFECTIVE");
    }

    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long id) {
        guardStatus(id, "EFFECTIVE", "TERMINATED");
    }

    /**
     * 订单引用校验 + 协议价目表。
     * 校验: 协议存在/EFFECTIVE/供应商一致/今日在协议期内; 返回 materialId→协议价。
     */
    public Map<Long, BigDecimal> resolvePrices(Long agreementId, Long supplierId) {
        PurchaseAgreement agreement = getById(agreementId);
        if (agreement == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购协议不存在: " + agreementId);
        }
        if (!"EFFECTIVE".equals(agreement.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "协议未生效: " + agreement.getStatus());
        }
        if (!agreement.getSupplierId().equals(supplierId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "协议供应商与订单供应商不一致");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(agreement.getStartDate()) || today.isAfter(agreement.getEndDate())) {
            throw new BizException(ErrorCode.INV_006, "当前日期不在协议有效期内");
        }
        Map<Long, BigDecimal> prices = new HashMap<>();
        for (PurchaseAgreementItem item : itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseAgreementItem>().eq(PurchaseAgreementItem::getAgreementId, agreementId))) {
            prices.put(item.getMaterialId(), item.getPrice());
        }
        return prices;
    }

    void guardStatus(Long id, String expected, String next) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<PurchaseAgreement>()
                .eq(PurchaseAgreement::getId, id)
                .eq(PurchaseAgreement::getStatus, expected)
                .set(PurchaseAgreement::getStatus, next));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "采购协议状态已变化, 请刷新后重试");
        }
    }

    public record Line(Long materialId, BigDecimal price, BigDecimal taxRate) {}
}
