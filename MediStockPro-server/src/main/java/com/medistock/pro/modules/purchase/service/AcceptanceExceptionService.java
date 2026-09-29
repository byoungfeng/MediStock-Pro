package com.medistock.pro.modules.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.inventory.service.BillNoGenerator;
import com.medistock.pro.modules.purchase.entity.Acceptance;
import com.medistock.pro.modules.purchase.entity.AcceptanceException;
import com.medistock.pro.modules.purchase.mapper.AcceptanceExceptionMapper;
import com.medistock.pro.modules.purchase.mapper.AcceptanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 验收异常 (P025): OPEN -> RECTIFYING(整改) -> RESOLVED(复验关闭)
 */
@Service
@RequiredArgsConstructor
public class AcceptanceExceptionService extends ServiceImpl<AcceptanceExceptionMapper, AcceptanceException> {

    private final AcceptanceMapper acceptanceMapper;
    private final BillNoGenerator billNoGenerator;

    public Page<AcceptanceException> pageQuery(int page, int size, Long acceptanceId, String status) {
        return page(new Page<>(page, size), new LambdaQueryWrapper<AcceptanceException>()
                .eq(acceptanceId != null, AcceptanceException::getAcceptanceId, acceptanceId)
                .eq(StringUtils.hasText(status), AcceptanceException::getStatus, status)
                .orderByDesc(AcceptanceException::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AcceptanceException create(Long acceptanceId, String type, String reason, String responsibility) {
        Acceptance acceptance = acceptanceMapper.selectById(acceptanceId);
        if (acceptance == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "验收单不存在: " + acceptanceId);
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "异常原因必填");
        }
        AcceptanceException ex = new AcceptanceException();
        ex.setExceptionNo(billNoGenerator.next("EX", "acceptance_exception", "exception_no"));
        ex.setAcceptanceId(acceptanceId);
        ex.setType(StringUtils.hasText(type) ? type : "OTHER");
        ex.setReason(reason);
        ex.setResponsibility(responsibility);
        ex.setStatus("OPEN");
        save(ex);
        return ex;
    }

    /** 处理: OPEN->RECTIFYING(开始整改) / *->RESOLVED(关闭, 必填处理结果) */
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, String action, String result) {
        AcceptanceException ex = getById(id);
        if (ex == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "异常单不存在: " + id);
        }
        switch (action) {
            case "RECTIFY" -> {
                if (!"OPEN".equals(ex.getStatus())) {
                    throw new BizException(ErrorCode.INV_006, "仅待处理异常可开始整改: " + ex.getStatus());
                }
                ex.setStatus("RECTIFYING");
            }
            case "RESOLVE" -> {
                if ("RESOLVED".equals(ex.getStatus())) {
                    throw new BizException(ErrorCode.INV_006, "异常单已关闭");
                }
                if (!StringUtils.hasText(result)) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "关闭异常必须填写处理结果");
                }
                ex.setStatus("RESOLVED");
                ex.setResult(result);
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "不支持的处理动作: " + action);
        }
        updateById(ex);
    }

    public List<AcceptanceException> listByAcceptance(Long acceptanceId) {
        return list(new LambdaQueryWrapper<AcceptanceException>()
                .eq(AcceptanceException::getAcceptanceId, acceptanceId)
                .orderByDesc(AcceptanceException::getId));
    }
}
