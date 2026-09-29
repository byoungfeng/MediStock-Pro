package com.medistock.pro.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.system.entity.SystemParam;
import com.medistock.pro.modules.system.mapper.SystemParamMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 系统参数 (P058): 版本化发布/回滚。每个 param_key 的最新已发布版本生效。
 */
@Service
public class SystemParamService extends ServiceImpl<SystemParamMapper, SystemParam> {

    /** 生效参数列表: 每个 key 的最高版本已发布记录 */
    public List<SystemParam> listEffective() {
        return list(new LambdaQueryWrapper<SystemParam>()
                .eq(SystemParam::getStatus, 1)
                .orderByAsc(SystemParam::getParamKey)
                .orderByDesc(SystemParam::getVersion))
                .stream()
                .collect(java.util.stream.Collectors.toMap(SystemParam::getParamKey, p -> p, (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .values().stream().toList();
    }

    /** 某参数的全部版本 (回滚选择用) */
    public List<SystemParam> versions(String key) {
        return list(new LambdaQueryWrapper<SystemParam>()
                .eq(SystemParam::getParamKey, key)
                .orderByDesc(SystemParam::getVersion));
    }

    /** 发布新版本 (值变更即版本+1, 立即生效) */
    @Transactional(rollbackFor = Exception.class)
    public SystemParam publish(String key, String value, String name) {
        if (!StringUtils.hasText(key) || value == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "参数键/值必填");
        }
        SystemParam latest = latestPublished(key);
        int nextVersion = latest == null ? 1 : latest.getVersion() + 1;
        SystemParam p = new SystemParam();
        p.setParamKey(key);
        p.setParamValue(value);
        p.setParamName(StringUtils.hasText(name) ? name : (latest == null ? key : latest.getParamName()));
        p.setVersion(nextVersion);
        p.setStatus(1);
        save(p);
        return p;
    }

    /** 回滚到指定版本: 复制其值为新版本 (不动历史, 只增不改) */
    @Transactional(rollbackFor = Exception.class)
    public SystemParam rollback(String key, int targetVersion) {
        SystemParam target = getOne(new LambdaQueryWrapper<SystemParam>()
                .eq(SystemParam::getParamKey, key)
                .eq(SystemParam::getVersion, targetVersion));
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "参数版本不存在: " + key + "@v" + targetVersion);
        }
        return publish(key, target.getParamValue(), target.getParamName());
    }

    /** 读取生效值 (业务侧用, 带默认值) */
    public String getValue(String key, String defaultValue) {
        SystemParam latest = latestPublished(key);
        return latest == null ? defaultValue : latest.getParamValue();
    }

    private SystemParam latestPublished(String key) {
        return getOne(new LambdaQueryWrapper<SystemParam>()
                .eq(SystemParam::getParamKey, key)
                .eq(SystemParam::getStatus, 1)
                .orderByDesc(SystemParam::getVersion)
                .last("LIMIT 1"));
    }
}
