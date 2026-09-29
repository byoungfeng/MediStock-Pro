package com.medistock.pro.modules.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.inventory.dto.ApproveDTO;
import com.medistock.pro.modules.inventory.entity.IssueRequest;
import com.medistock.pro.modules.inventory.entity.ScrapOrder;
import com.medistock.pro.modules.inventory.entity.TransferOrder;
import com.medistock.pro.modules.inventory.mapper.IssueRequestMapper;
import com.medistock.pro.modules.inventory.mapper.ScrapOrderMapper;
import com.medistock.pro.modules.inventory.mapper.TransferOrderMapper;
import com.medistock.pro.modules.inventory.service.IssueRequestService;
import com.medistock.pro.modules.inventory.service.ScrapService;
import com.medistock.pro.modules.inventory.service.TransferService;
import com.medistock.pro.modules.purchase.entity.PurchaseOrder;
import com.medistock.pro.modules.purchase.entity.PurchaseRequest;
import com.medistock.pro.modules.purchase.mapper.PurchaseOrderMapper;
import com.medistock.pro.modules.purchase.mapper.PurchaseRequestMapper;
import com.medistock.pro.modules.purchase.service.PurchaseOrderService;
import com.medistock.pro.modules.purchase.service.PurchaseRequestService;
import com.medistock.pro.modules.system.entity.SysUser;
import com.medistock.pro.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 审批中心 (P056): 聚合各业务待审单据为统一待办, 动作分发到既有业务服务 (状态机不重写)
 */
@Service
@RequiredArgsConstructor
public class ApprovalCenterService {

    private final IssueRequestMapper issueRequestMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final ScrapOrderMapper scrapOrderMapper;
    private final SysUserMapper sysUserMapper;
    private final IssueRequestService issueRequestService;
    private final PurchaseRequestService purchaseRequestService;
    private final PurchaseOrderService purchaseOrderService;
    private final TransferService transferService;
    private final ScrapService scrapService;

    public record TodoItem(String bizType, Long bizId, String bizNo, String title,
                           Long applicantId, String applicantName, LocalDateTime createdAt) {
    }

    /** 统一待办: 申领/采购申请/采购订单/调拨/报废 的 PENDING 单 */
    public List<TodoItem> todo() {
        List<TodoItem> items = new ArrayList<>();
        issueRequestMapper.selectList(new LambdaQueryWrapper<IssueRequest>().eq(IssueRequest::getStatus, "PENDING"))
                .forEach(r -> items.add(new TodoItem("ISSUE_REQUEST", r.getId(), r.getRequestNo(),
                        "科室领用申请 " + r.getRequestNo(), r.getCreatedBy(), null, r.getCreatedAt())));
        purchaseRequestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>().eq(PurchaseRequest::getStatus, "PENDING"))
                .forEach(r -> items.add(new TodoItem("PURCHASE_REQUEST", r.getId(), r.getRequestNo(),
                        "采购申请 " + r.getRequestNo(), r.getCreatedBy(), null, r.getCreatedAt())));
        purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>().eq(PurchaseOrder::getStatus, "PENDING"))
                .forEach(r -> items.add(new TodoItem("PURCHASE_ORDER", r.getId(), r.getOrderNo(),
                        "采购订单 " + r.getOrderNo(), r.getCreatedBy(), null, r.getCreatedAt())));
        transferOrderMapper.selectList(new LambdaQueryWrapper<TransferOrder>().eq(TransferOrder::getStatus, "PENDING"))
                .forEach(r -> items.add(new TodoItem("TRANSFER", r.getId(), r.getTransferNo(),
                        "调拨申请 " + r.getTransferNo(), r.getCreatedBy(), null, r.getCreatedAt())));
        scrapOrderMapper.selectList(new LambdaQueryWrapper<ScrapOrder>().eq(ScrapOrder::getStatus, "PENDING"))
                .forEach(r -> items.add(new TodoItem("SCRAP", r.getId(), r.getScrapNo(),
                        "报废申请 " + r.getScrapNo(), r.getCreatedBy(), null, r.getCreatedAt())));

        Map<Long, SysUser> users = items.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(items.stream().map(TodoItem::applicantId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return items.stream()
                .map(i -> new TodoItem(i.bizType(), i.bizId(), i.bizNo(), i.title(), i.applicantId(),
                        users.get(i.applicantId()) == null ? null : users.get(i.applicantId()).getName(), i.createdAt()))
                .sorted(Comparator.comparing(TodoItem::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }

    /**
     * 统一审批动作: 版本服务端取当前值, 各服务内部状态守卫兜底并发 (INV_006)
     *
     * @param pass true=同意 false=驳回 (仅申领/采购申请支持驳回)
     */
    public void action(String bizType, Long bizId, boolean pass, String reason) {
        switch (bizType) {
            case "ISSUE_REQUEST" -> {
                IssueRequest doc = mustExist(issueRequestMapper.selectById(bizId), bizType, bizId);
                if (pass) {
                    issueRequestService.approve(bizId, new ApproveDTO(doc.getVersion(), true, reason, null));
                } else {
                    issueRequestService.reject(bizId, doc.getVersion());
                }
            }
            case "PURCHASE_REQUEST" -> {
                PurchaseRequest doc = mustExist(purchaseRequestMapper.selectById(bizId), bizType, bizId);
                purchaseRequestService.approve(bizId, doc.getVersion(), pass, null);
            }
            case "PURCHASE_ORDER" -> {
                if (!pass) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "采购订单暂不支持驳回, 请到采购页处理");
                }
                PurchaseOrder doc = mustExist(purchaseOrderMapper.selectById(bizId), bizType, bizId);
                purchaseOrderService.approve(bizId, doc.getVersion());
            }
            case "TRANSFER" -> {
                if (!pass) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "调拨单暂不支持驳回, 请到调拨页处理");
                }
                TransferOrder doc = mustExist(transferOrderMapper.selectById(bizId), bizType, bizId);
                transferService.approve(bizId, doc.getVersion());
            }
            case "SCRAP" -> {
                if (!pass) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "报废单暂不支持驳回, 请到报废页处理");
                }
                ScrapOrder doc = mustExist(scrapOrderMapper.selectById(bizId), bizType, bizId);
                scrapService.approve(bizId, doc.getVersion());
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "不支持的审批类型: " + bizType);
        }
    }

    private <T> T mustExist(T doc, String bizType, Long bizId) {
        if (doc == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "单据不存在: " + bizType + "#" + bizId);
        }
        return doc;
    }
}
