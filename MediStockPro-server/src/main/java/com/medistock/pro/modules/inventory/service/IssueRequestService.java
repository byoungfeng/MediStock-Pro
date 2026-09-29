package com.medistock.pro.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.inventory.dto.ApproveDTO;
import com.medistock.pro.modules.inventory.dto.IssueRequestDTO;
import com.medistock.pro.modules.inventory.entity.IssueRequest;
import com.medistock.pro.modules.inventory.entity.IssueRequestItem;
import com.medistock.pro.modules.inventory.mapper.IssueRequestItemMapper;
import com.medistock.pro.modules.inventory.mapper.IssueRequestMapper;
import com.medistock.pro.modules.system.service.RbacService;
import com.medistock.pro.modules.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 领用申请: DRAFT -> PENDING -> (审批通过: 生成拣货任务并锁定库存) PICKING -> COMPLETED
 *                          -> (驳回) REJECTED
 */
@Service
@RequiredArgsConstructor
public class IssueRequestService extends ServiceImpl<IssueRequestMapper, IssueRequest> {

    private final IssueRequestItemMapper itemMapper;
    private final InventoryEngine inventoryEngine;
    private final BillNoGenerator billNoGenerator;
    private final MessageService messageService;
    private final com.medistock.pro.modules.approval.ApprovalTrailService approvalTrailService;
    private final RbacService rbacService;

    private List<Long> scope() {
        return rbacService.currentUserWarehouseScope();
    }

    private void assertScope(Long warehouseId) {
        List<Long> s = scope();
        if (s != null && !s.contains(warehouseId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "无权访问该仓库数据");
        }
    }

    public Page<IssueRequest> pageQuery(int page, int size, String status, Long departmentId, Long warehouseId) {
        List<Long> scope = scope();
        if (scope != null && scope.isEmpty()) {
            return new Page<>(page, size, 0);
        }
        return lambdaQuery()
                .eq(StringUtils.hasText(status), IssueRequest::getStatus, status)
                .eq(departmentId != null, IssueRequest::getDepartmentId, departmentId)
                .eq(warehouseId != null, IssueRequest::getWarehouseId, warehouseId)
                .in(scope != null, IssueRequest::getWarehouseId, scope)
                .orderByDesc(IssueRequest::getCreatedAt)
                .page(new Page<>(page, size));
    }

    public Map<String, Object> detail(Long id) {
        IssueRequest master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "领用申请不存在: " + id);
        }
        assertScope(master.getWarehouseId());
        List<IssueRequestItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<IssueRequestItem>().eq(IssueRequestItem::getRequestId, id));
        Map<String, Object> result = new HashMap<>();
        result.put("master", master);
        result.put("items", items);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public IssueRequest create(IssueRequestDTO dto) {
        IssueRequest master = new IssueRequest();
        master.setRequestNo(billNoGenerator.next("LY", "issue_request", "request_no"));
        master.setDepartmentId(dto.departmentId());
        master.setWarehouseId(dto.warehouseId());
        master.setPriority(StringUtils.hasText(dto.priority()) ? dto.priority() : "NORMAL");
        master.setPurpose(dto.purpose());
        master.setStatus("DRAFT");
        master.setVersion(0);
        save(master);

        for (IssueRequestDTO.Line line : dto.items()) {
            IssueRequestItem item = new IssueRequestItem();
            item.setRequestId(master.getId());
            item.setMaterialId(line.materialId());
            item.setQty(line.qty());
            item.setIssuedQty(0);
            itemMapper.insert(item);
        }
        return master;
    }

    /** 提交: DRAFT -> PENDING */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id, Integer version) {
        guardStatus(id, version, "DRAFT", "PENDING");
        IssueRequest master = getById(id);
        approvalTrailService.onSubmit("ISSUE_REQUEST", id, master.getCreatedBy());
    }

    /** 驳回: PENDING -> REJECTED */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Integer version) {
        guardStatus(id, version, "PENDING", "REJECTED");
        IssueRequest master = getById(id);
        approvalTrailService.onAction("ISSUE_REQUEST", id, false, null);
        messageService.notify(master.getCreatedBy(), "APPROVAL",
                "领用申请 " + master.getRequestNo() + " 已被驳回", "请登录系统查看详情", "ISSUE_REQUEST", id);
    }

    /**
     * 审批通过: PENDING(或拣货取消后的 APPROVED) -> PICKING
     * 同事务: 改审批数量 -> 引擎 FEFO 锁定 -> 生成拣货任务
     * 库存不足则整体回滚 (P0: 不允许部分锁定)
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, ApproveDTO dto) {
        IssueRequest master = getById(id);
        if (master == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "领用申请不存在: " + id);
        }
        if (!"PENDING".equals(master.getStatus()) && !"APPROVED".equals(master.getStatus())) {
            throw new BizException(ErrorCode.INV_006, "当前状态不可审批: " + master.getStatus());
        }

        List<IssueRequestItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<IssueRequestItem>().eq(IssueRequestItem::getRequestId, id));
        Map<Long, Integer> approvedMap = dto.items() == null ? Map.of()
                : dto.items().stream().collect(Collectors.toMap(ApproveDTO.Line::itemId, ApproveDTO.Line::approvedQty));

        List<InventoryEngine.OutboundLine> lines = new java.util.ArrayList<>();
        for (IssueRequestItem item : items) {
            int approved = approvedMap.getOrDefault(item.getId(), item.getQty());
            if (approved < 0 || approved > item.getQty()) {
                throw new BizException(ErrorCode.PARAM_INVALID, "审批数量非法: itemId=" + item.getId());
            }
            IssueRequestItem update = new IssueRequestItem();
            update.setId(item.getId());
            update.setApprovedQty(approved);
            itemMapper.updateById(update);
            if (approved > 0) {
                lines.add(new InventoryEngine.OutboundLine(item.getMaterialId(), null, null, approved, null));
            }
        }
        if (lines.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "审批通过数量不能全为 0");
        }

        // 引擎锁定 + 生成拣货任务 (同事务, 失败整体回滚)
        inventoryEngine.reserve(master.getWarehouseId(), lines, "ISSUE_REQUEST", master.getId(), master.getRequestNo());

        guardStatus(id, dto.version(), master.getStatus(), "PICKING");
        approvalTrailService.onAction("ISSUE_REQUEST", id, true, dto.reason());
        messageService.notify(master.getCreatedBy(), "APPROVAL",
                "领用申请 " + master.getRequestNo() + " 已审批通过", "已锁定库存并生成拣货任务", "ISSUE_REQUEST", id);
    }

    private void guardStatus(Long id, Integer version, String from, String to) {
        int rows = baseMapper.update(null, new LambdaUpdateWrapper<IssueRequest>()
                .eq(IssueRequest::getId, id)
                .eq(IssueRequest::getVersion, version)
                .eq(IssueRequest::getStatus, from)
                .set(IssueRequest::getStatus, to)
                .setSql("version = version + 1"));
        if (rows == 0) {
            throw new BizException(ErrorCode.INV_006, "单据状态已变化, 请刷新后重试");
        }
    }
}
