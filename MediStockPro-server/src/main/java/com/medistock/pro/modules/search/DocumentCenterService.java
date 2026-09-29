package com.medistock.pro.modules.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medistock.pro.modules.inventory.entity.*;
import com.medistock.pro.modules.inventory.mapper.*;
import com.medistock.pro.modules.purchase.entity.*;
import com.medistock.pro.modules.purchase.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单据中心 (P055): 全类型单据统一查询 (类型/状态/编号/日期)
 * 全类型模式: 每类取最近 100 条合并排序后内存分页 (演示规模足够)
 */
@Service
@RequiredArgsConstructor
public class DocumentCenterService {

    private final IssueRequestMapper issueRequestMapper;
    private final IssueOrderMapper issueOrderMapper;
    private final StockInboundMapper stockInboundMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final CountPlanMapper countPlanMapper;
    private final ScrapOrderMapper scrapOrderMapper;
    private final ReturnOrderMapper returnOrderMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final ReceiptMapper receiptMapper;
    private final AcceptanceMapper acceptanceMapper;
    private final PurchaseAgreementMapper purchaseAgreementMapper;

    public record DocEntry(String type, String typeName, Long id, String no, String status,
                           Long createdBy, LocalDateTime createdAt) {
    }

    public Map<String, Object> query(String type, String status, String no,
                                     LocalDate from, LocalDate to, int page, int size) {
        LocalDateTime fromTs = from == null ? null : from.atStartOfDay();
        LocalDateTime toTs = to == null ? null : to.atTime(LocalTime.MAX);
        List<DocEntry> all = new ArrayList<>();
        collect(all, type, "ISSUE_REQUEST", "领用申请", no, status, fromTs, toTs,
                issueRequestMapper, IssueRequest::getRequestNo, IssueRequest::getStatus, IssueRequest::getCreatedAt);
        collect(all, type, "ISSUE_ORDER", "出库单", no, status, fromTs, toTs,
                issueOrderMapper, IssueOrder::getIssueNo, IssueOrder::getStatus, IssueOrder::getCreatedAt);
        collect(all, type, "STOCK_INBOUND", "入库单", no, status, fromTs, toTs,
                stockInboundMapper, StockInbound::getInboundNo, StockInbound::getStatus, StockInbound::getCreatedAt);
        collect(all, type, "TRANSFER", "调拨单", no, status, fromTs, toTs,
                transferOrderMapper, TransferOrder::getTransferNo, TransferOrder::getStatus, TransferOrder::getCreatedAt);
        collect(all, type, "COUNT_PLAN", "盘点计划", no, status, fromTs, toTs,
                countPlanMapper, CountPlan::getPlanNo, CountPlan::getStatus, CountPlan::getCreatedAt);
        collect(all, type, "SCRAP", "报废单", no, status, fromTs, toTs,
                scrapOrderMapper, ScrapOrder::getScrapNo, ScrapOrder::getStatus, ScrapOrder::getCreatedAt);
        collect(all, type, "RETURN", "退库单", no, status, fromTs, toTs,
                returnOrderMapper, ReturnOrder::getReturnNo, ReturnOrder::getStatus, ReturnOrder::getCreatedAt);
        collect(all, type, "PURCHASE_REQUEST", "采购申请", no, status, fromTs, toTs,
                purchaseRequestMapper, PurchaseRequest::getRequestNo, PurchaseRequest::getStatus, PurchaseRequest::getCreatedAt);
        collect(all, type, "PURCHASE_ORDER", "采购订单", no, status, fromTs, toTs,
                purchaseOrderMapper, PurchaseOrder::getOrderNo, PurchaseOrder::getStatus, PurchaseOrder::getCreatedAt);
        // receipt/acceptance 不继承 BaseEntity (表无 deleted 列), 单独收集
        if (!StringUtils.hasText(type) || "RECEIPT".equals(type)) {
            receiptMapper.selectPage(new Page<>(1, StringUtils.hasText(type) ? 500 : 100),
                    new LambdaQueryWrapper<Receipt>()
                            .like(StringUtils.hasText(no), Receipt::getReceiptNo, no)
                            .eq(StringUtils.hasText(status), Receipt::getStatus, status)
                            .ge(fromTs != null, Receipt::getCreatedAt, fromTs)
                            .le(toTs != null, Receipt::getCreatedAt, toTs)
                            .orderByDesc(Receipt::getCreatedAt)).getRecords()
                    .forEach(e -> all.add(new DocEntry("RECEIPT", "收货单", e.getId(), e.getReceiptNo(),
                            e.getStatus(), e.getCreatedBy(), e.getCreatedAt())));
        }
        if (!StringUtils.hasText(type) || "ACCEPTANCE".equals(type)) {
            acceptanceMapper.selectPage(new Page<>(1, StringUtils.hasText(type) ? 500 : 100),
                    new LambdaQueryWrapper<Acceptance>()
                            .like(StringUtils.hasText(no), Acceptance::getAcceptanceNo, no)
                            .eq(StringUtils.hasText(status), Acceptance::getStatus, status)
                            .ge(fromTs != null, Acceptance::getCreatedAt, fromTs)
                            .le(toTs != null, Acceptance::getCreatedAt, toTs)
                            .orderByDesc(Acceptance::getCreatedAt)).getRecords()
                    .forEach(e -> all.add(new DocEntry("ACCEPTANCE", "验收单", e.getId(), e.getAcceptanceNo(),
                            e.getStatus(), e.getCreatedBy(), e.getCreatedAt())));
        }
        collect(all, type, "PURCHASE_AGREEMENT", "采购协议", no, status, fromTs, toTs,
                purchaseAgreementMapper, PurchaseAgreement::getAgreementNo, PurchaseAgreement::getStatus, PurchaseAgreement::getCreatedAt);

        all.sort(Comparator.comparing(DocEntry::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        int fromIdx = Math.min((page - 1) * size, all.size());
        int toIdx = Math.min(fromIdx + size, all.size());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", all.subList(fromIdx, toIdx));
        result.put("total", all.size());
        result.put("current", page);
        result.put("size", size);
        return result;
    }

    /** 单类型收集: 指定 type 时只查目标表; 全类型时每类限量 100。SFunction 必须指向具体实体 (MP lambda 缓存按声明类解析) */
    private <T extends com.medistock.pro.common.BaseEntity> void collect(
            List<DocEntry> out, String requestedType, String type, String typeName,
            String no, String status, LocalDateTime from, LocalDateTime to,
            com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> noGetter,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, String> statusGetter,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, LocalDateTime> createdAtGetter) {
        if (StringUtils.hasText(requestedType) && !requestedType.equals(type)) {
            return;
        }
        LambdaQueryWrapper<T> qw = new LambdaQueryWrapper<T>()
                .like(StringUtils.hasText(no), noGetter, no)
                .eq(StringUtils.hasText(status), statusGetter, status)
                .ge(from != null, createdAtGetter, from)
                .le(to != null, createdAtGetter, to)
                .orderByDesc(createdAtGetter);
        int limit = StringUtils.hasText(requestedType) ? 500 : 100;
        mapper.selectPage(new Page<>(1, limit), qw).getRecords().forEach(e -> out.add(
                new DocEntry(type, typeName, e.getId(), noGetter.apply(e), statusGetter.apply(e),
                        e.getCreatedBy(), e.getCreatedAt())));
    }
}
