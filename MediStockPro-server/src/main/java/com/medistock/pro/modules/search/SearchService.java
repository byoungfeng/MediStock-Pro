package com.medistock.pro.modules.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medistock.pro.modules.inventory.entity.*;
import com.medistock.pro.modules.inventory.mapper.*;
import com.medistock.pro.modules.master.entity.Material;
import com.medistock.pro.modules.master.mapper.MaterialMapper;
import com.medistock.pro.modules.purchase.entity.*;
import com.medistock.pro.modules.purchase.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局搜索 (P003): 物资/供应商/批次/单据 分组命中, 每组最多 5 条
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final InventoryBatchMapper batchMapper;
    private final IssueRequestMapper issueRequestMapper;
    private final IssueOrderMapper issueOrderMapper;
    private final StockInboundMapper stockInboundMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final ScrapOrderMapper scrapOrderMapper;
    private final ReturnOrderMapper returnOrderMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseAgreementMapper purchaseAgreementMapper;

    private static final int LIMIT = 5;

    public Map<String, Object> search(String keyword) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 注意: like OR like 必须用 and(...) 分组, 否则与后续 eq 的 AND 绑定顺序错误
        result.put("materials", materialMapper.selectPage(new Page<>(1, LIMIT),
                new LambdaQueryWrapper<Material>()
                        .and(w -> w.like(Material::getCode, keyword).or().like(Material::getName, keyword))
                        .eq(Material::getDeleted, 0)).getRecords());

        result.put("suppliers", supplierMapper.selectPage(new Page<>(1, LIMIT),
                new LambdaQueryWrapper<Supplier>()
                        .and(w -> w.like(Supplier::getCode, keyword).or().like(Supplier::getName, keyword))
                        .eq(Supplier::getDeleted, 0)).getRecords());

        result.put("batches", batchMapper.selectPage(new Page<>(1, LIMIT),
                new LambdaQueryWrapper<InventoryBatch>().like(InventoryBatch::getBatchNo, keyword)).getRecords());

        // 单据按单号命中, 统一为 {type, id, no, status}
        List<Map<String, Object>> docs = new java.util.ArrayList<>();
        issueRequestMapper.selectPage(new Page<>(1, LIMIT), new LambdaQueryWrapper<IssueRequest>()
                .like(IssueRequest::getRequestNo, keyword)).getRecords()
                .forEach(d -> docs.add(doc("ISSUE_REQUEST", "领用申请", d.getId(), d.getRequestNo(), d.getStatus())));
        issueOrderMapper.selectPage(new Page<>(1, LIMIT), new LambdaQueryWrapper<IssueOrder>()
                .like(IssueOrder::getIssueNo, keyword)).getRecords()
                .forEach(d -> docs.add(doc("ISSUE_ORDER", "出库单", d.getId(), d.getIssueNo(), d.getStatus())));
        stockInboundMapper.selectPage(new Page<>(1, LIMIT), new LambdaQueryWrapper<StockInbound>()
                .like(StockInbound::getInboundNo, keyword)).getRecords()
                .forEach(d -> docs.add(doc("STOCK_INBOUND", "入库单", d.getId(), d.getInboundNo(), d.getStatus())));
        transferOrderMapper.selectPage(new Page<>(1, LIMIT), new LambdaQueryWrapper<TransferOrder>()
                .like(TransferOrder::getTransferNo, keyword)).getRecords()
                .forEach(d -> docs.add(doc("TRANSFER", "调拨单", d.getId(), d.getTransferNo(), d.getStatus())));
        scrapOrderMapper.selectPage(new Page<>(1, LIMIT), new LambdaQueryWrapper<ScrapOrder>()
                .like(ScrapOrder::getScrapNo, keyword)).getRecords()
                .forEach(d -> docs.add(doc("SCRAP", "报废单", d.getId(), d.getScrapNo(), d.getStatus())));
        returnOrderMapper.selectPage(new Page<>(1, LIMIT), new LambdaQueryWrapper<ReturnOrder>()
                .like(ReturnOrder::getReturnNo, keyword)).getRecords()
                .forEach(d -> docs.add(doc("RETURN", "退库单", d.getId(), d.getReturnNo(), d.getStatus())));
        purchaseRequestMapper.selectPage(new Page<>(1, LIMIT), new LambdaQueryWrapper<PurchaseRequest>()
                .like(PurchaseRequest::getRequestNo, keyword)).getRecords()
                .forEach(d -> docs.add(doc("PURCHASE_REQUEST", "采购申请", d.getId(), d.getRequestNo(), d.getStatus())));
        purchaseOrderMapper.selectPage(new Page<>(1, LIMIT), new LambdaQueryWrapper<PurchaseOrder>()
                .like(PurchaseOrder::getOrderNo, keyword)).getRecords()
                .forEach(d -> docs.add(doc("PURCHASE_ORDER", "采购订单", d.getId(), d.getOrderNo(), d.getStatus())));
        purchaseAgreementMapper.selectPage(new Page<>(1, LIMIT), new LambdaQueryWrapper<PurchaseAgreement>()
                .like(PurchaseAgreement::getAgreementNo, keyword)).getRecords()
                .forEach(d -> docs.add(doc("PURCHASE_AGREEMENT", "采购协议", d.getId(), d.getAgreementNo(), d.getStatus())));
        result.put("documents", docs.stream().limit(LIMIT * 2L).toList());
        return result;
    }

    private Map<String, Object> doc(String type, String typeName, Long id, String no, String status) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("type", type);
        d.put("typeName", typeName);
        d.put("id", id);
        d.put("no", no);
        d.put("status", status);
        return d;
    }
}
