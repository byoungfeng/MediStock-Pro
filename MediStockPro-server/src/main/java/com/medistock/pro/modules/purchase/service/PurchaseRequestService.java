package com.medistock.pro.modules.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.common.PageResult;
import com.medistock.pro.modules.inventory.service.BillNoGenerator;
import com.medistock.pro.modules.purchase.entity.PurchaseOrder;
import com.medistock.pro.modules.purchase.entity.PurchaseRequest;
import com.medistock.pro.modules.purchase.entity.PurchaseRequestItem;
import com.medistock.pro.modules.purchase.mapper.PurchaseRequestItemMapper;
import com.medistock.pro.modules.purchase.mapper.PurchaseRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购申请: DRAFT→PENDING→APPROVED/REJECTED→ORDERED(已转订单) / CANCELLED
 */
@Service
@RequiredArgsConstructor
public class PurchaseRequestService extends ServiceImpl<PurchaseRequestMapper, PurchaseRequest> {

    private final PurchaseRequestItemMapper itemMapper;
    private final PurchaseOrderService orderService;
    private final BillNoGenerator billNoGenerator;
    private final com.medistock.pro.modules.message.MessageService messageService;
    private final com.medistock.pro.modules.approval.ApprovalTrailService approvalTrailService;

    public PageResult<PurchaseRequest> pageQuery(int page, int size, Long departmentId, String status) {
        Page<PurchaseRequest> p = page(new Page<>(page, size), new LambdaQueryWrapper<PurchaseRequest>()
                .eq(departmentId != null, PurchaseRequest::getDepartmentId, departmentId)
                .eq(status != null && !status.isBlank(), PurchaseRequest::getStatus, status)
                .orderByDesc(PurchaseRequest::getId));
        return PageResult.of(p);
    }

    public Map<String, Object> detail(Long id) {
        PurchaseRequest master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购申请不存在: " + id);
        }
        List<PurchaseRequestItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequestItem>().eq(PurchaseRequestItem::getRequestId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequest create(Long departmentId, Long warehouseId, String purpose, List<Line> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "申请明细不能为空");
        }
        PurchaseRequest req = new PurchaseRequest();
        req.setRequestNo(billNoGenerator.next("SQ", "purchase_request", "request_no"));
        req.setDepartmentId(departmentId);
        req.setWarehouseId(warehouseId);
        req.setPurpose(purpose);
        req.setStatus("DRAFT");
        req.setVersion(0);
        save(req);

        for (Line line : items) {
            if (line.qty() == null || line.qty() <= 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "申请数量必须>0");
            }
            PurchaseRequestItem item = new PurchaseRequestItem();
            item.setRequestId(req.getId());
            item.setMaterialId(line.materialId());
            item.setQty(line.qty());
            itemMapper.insert(item);
        }
        return req;
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Integer version) {
        guardStatus(id, version, "DRAFT", "PENDING");
        PurchaseRequest req = getById(id);
        approvalTrailService.onSubmit("PURCHASE_REQUEST", id, req.getCreatedBy());
    }

    /**
     * 审批: pass=true→APPROVED(写入核定量), false→REJECTED
     * approvedQtys: itemId→核定量, 缺省=申请量
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Integer version, boolean pass, Map<Long, Integer> approvedQtys) {
        guardStatus(id, version, "PENDING", pass ? "APPROVED" : "REJECTED");
        PurchaseRequest req = getById(id);
        approvalTrailService.onAction("PURCHASE_REQUEST", id, pass, null);
        messageService.notify(req.getCreatedBy(), "APPROVAL",
                "采购申请 " + req.getRequestNo() + (pass ? " 已审批通过" : " 已被驳回"),
                pass ? "可转采购订单" : "请登录系统查看详情", "PURCHASE_REQUEST", id);
        if (!pass) {
            return;
        }
        List<PurchaseRequestItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequestItem>().eq(PurchaseRequestItem::getRequestId, id));
        for (PurchaseRequestItem item : items) {
            Integer approved = approvedQtys != null ? approvedQtys.get(item.getId()) : null;
            if (approved == null) {
                approved = item.getQty();
            }
            if (approved < 0 || approved > item.getQty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "核定量须在 0~申请量 之间: itemId=" + item.getId());
            }
            item.setApprovedQty(approved);
            itemMapper.updateById(item);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Integer version) {
        PurchaseRequest req = getById(id);
        if (req == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购申请不存在: " + id);
        }
        if (!List.of("DRAFT", "PENDING").contains(req.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "已审批申请不可取消: " + req.getStatus());
        }
        guardStatus(id, version, req.getStatus(), "CANCELLED");
    }

    /**
     * 转采购订单: APPROVED→ORDERED, 按核定量生成 DRAFT 采购订单并回写 request_id。
     * prices: materialId→单价, 缺省 0 (可在订单环节补价)。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrder toOrder(Long id, Integer version, Long supplierId, LocalDate expectDate,
                                 Map<Long, BigDecimal> prices) {
        PurchaseRequest req = getById(id);
        if (req == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采购申请不存在: " + id);
        }
        List<PurchaseRequestItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequestItem>().eq(PurchaseRequestItem::getRequestId, id));
        List<PurchaseOrderService.Line> lines = new ArrayList<>();
        for (PurchaseRequestItem item : items) {
            int qty = item.getApprovedQty() != null ? item.getApprovedQty() : item.getQty();
            if (qty <= 0) {
                continue;
            }
            BigDecimal price = prices != null ? prices.get(item.getMaterialId()) : null;
            lines.add(new PurchaseOrderService.Line(item.getMaterialId(), qty,
                    price != null ? price : BigDecimal.ZERO));
        }
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "核定量为零, 无可转订单明细");
        }
        PurchaseOrder order = orderService.create(supplierId, req.getWarehouseId(), expectDate,
                "由采购申请 " + req.getRequestNo() + " 转入", lines, req.getId());
        guardStatus(id, version, "APPROVED", "ORDERED");
        return order;
    }

    void guardStatus(Long id, Integer version, String expected, String next) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<PurchaseRequest>()
                .eq(PurchaseRequest::getId, id)
                .eq(PurchaseRequest::getVersion, version)
                .eq(PurchaseRequest::getStatus, expected)
                .set(PurchaseRequest::getStatus, next)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "采购申请状态已变化, 请刷新后重试");
        }
    }

    public record Line(Long materialId, Integer qty) {}
}
