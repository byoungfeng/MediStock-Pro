package com.medistock.pro.modules.approval;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.medistock.pro.modules.approval.mapper.ApprovalInstanceMapper;
import com.medistock.pro.modules.approval.mapper.ApprovalRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批留痕: 提交建实例(幂等 uk_business), 审批/驳回写记录(只增不改)。
 * 所有方法失败仅记日志, 不阻断业务主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalTrailService {

    private final ApprovalInstanceMapper instanceMapper;
    private final ApprovalRecordMapper recordMapper;

    /** 单据提交审批时调用: 建实例或重开 (驳回/取消后再次提交) */
    public void onSubmit(String businessType, Long businessId, Long submitterId) {
        try {
            ApprovalInstance inst = find(businessType, businessId);
            if (inst == null) {
                inst = new ApprovalInstance();
                inst.setBusinessType(businessType);
                inst.setBusinessId(businessId);
                inst.setStatus("PENDING");
                inst.setCurrentNode("APPROVE");
                inst.setSubmitterId(submitterId != null ? submitterId : currentUserId());
                instanceMapper.insert(inst);
            } else {
                inst.setStatus("PENDING");
                inst.setCurrentNode("APPROVE");
                instanceMapper.updateById(inst);
            }
        } catch (Exception e) {
            log.warn("审批实例写入失败 {}#{}: {}", businessType, businessId, e.getMessage());
        }
    }

    /** 审批通过/驳回时调用: 更新实例 + 追加记录 */
    public void onAction(String businessType, Long businessId, boolean pass, String comment) {
        try {
            ApprovalInstance inst = find(businessType, businessId);
            if (inst == null) {
                // 历史数据无实例: 补建 (提交人未知置 0)
                inst = new ApprovalInstance();
                inst.setBusinessType(businessType);
                inst.setBusinessId(businessId);
                inst.setSubmitterId(0L);
                inst.setCurrentNode("APPROVE");
                instanceMapper.insert(inst);
            }
            inst.setStatus(pass ? "APPROVED" : "REJECTED");
            inst.setCurrentNode(null);
            instanceMapper.updateById(inst);

            ApprovalRecord record = new ApprovalRecord();
            record.setInstanceId(inst.getId());
            record.setNodeId("APPROVE");
            record.setApproverId(currentUserId());
            record.setAction(pass ? "APPROVE" : "REJECT");
            record.setComment(comment);
            recordMapper.insert(record);
        } catch (Exception e) {
            log.warn("审批记录写入失败 {}#{}: {}", businessType, businessId, e.getMessage());
        }
    }

    /** 留痕查询: 实例 + 记录列表 */
    public Map<String, Object> trail(String businessType, Long businessId) {
        Map<String, Object> result = new LinkedHashMap<>();
        ApprovalInstance inst = find(businessType, businessId);
        result.put("instance", inst);
        result.put("records", inst == null ? List.of()
                : recordMapper.selectList(new LambdaQueryWrapper<ApprovalRecord>()
                .eq(ApprovalRecord::getInstanceId, inst.getId())
                .orderByAsc(ApprovalRecord::getId)));
        return result;
    }

    private ApprovalInstance find(String businessType, Long businessId) {
        return instanceMapper.selectOne(new LambdaQueryWrapper<ApprovalInstance>()
                .eq(ApprovalInstance::getBusinessType, businessType)
                .eq(ApprovalInstance::getBusinessId, businessId));
    }

    private Long currentUserId() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId == null ? 0L : Long.parseLong(loginId.toString());
    }
}
