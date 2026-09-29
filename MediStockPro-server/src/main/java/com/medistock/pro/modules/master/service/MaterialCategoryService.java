package com.medistock.pro.modules.master.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.medistock.pro.common.BizException;
import com.medistock.pro.common.ErrorCode;
import com.medistock.pro.modules.master.entity.Material;
import com.medistock.pro.modules.master.entity.MaterialCategory;
import com.medistock.pro.modules.master.mapper.MaterialCategoryMapper;
import com.medistock.pro.modules.master.mapper.MaterialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物资分类 (P009): 树形 CRUD, 编码唯一, 有物资/有下级禁停用
 */
@Service
@RequiredArgsConstructor
public class MaterialCategoryService extends ServiceImpl<MaterialCategoryMapper, MaterialCategory> {

    private final MaterialMapper materialMapper;

    public List<MaterialCategory> listAll() {
        return list(new LambdaQueryWrapper<MaterialCategory>()
                .orderByAsc(MaterialCategory::getSort).orderByAsc(MaterialCategory::getCode));
    }

    /** 启用分类选项 (库存筛选等业务场景用, 登录即可) */
    public List<MaterialCategory> listEnabled() {
        return list(new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getStatus, 1)
                .orderByAsc(MaterialCategory::getSort).orderByAsc(MaterialCategory::getCode));
    }

    /** 含自身的后代分类 id 集合 (平铺表内存收集, 分类量级小) */
    public List<Long> selfAndDescendantIds(Long rootId) {
        List<MaterialCategory> all = listAll();
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (MaterialCategory c : all) {
            if (c.getParentId() != null) {
                childrenMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        List<Long> ids = new ArrayList<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            ids.add(cur);
            for (Long child : childrenMap.getOrDefault(cur, List.of())) {
                queue.add(child);
            }
        }
        return ids;
    }

    public MaterialCategory create(MaterialCategory category) {
        Long dup = baseMapper.selectCount(new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getCode, category.getCode()));
        if (dup > 0) {
            throw new BizException(ErrorCode.DUPLICATE_KEY, "分类编码已存在: " + category.getCode());
        }
        category.setStatus(1);
        save(category);
        return category;
    }

    public void update(Long id, MaterialCategory patch) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在: " + id);
        }
        patch.setId(id);
        patch.setCode(null); // 编码创建后不可改
        updateById(patch);
    }

    /** 停用: 有下级分类或挂有物资时禁止 */
    public void changeStatus(Long id, Integer status) {
        if (getById(id) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在: " + id);
        }
        if (status != null && status == 0) {
            Long children = baseMapper.selectCount(new LambdaQueryWrapper<MaterialCategory>()
                    .eq(MaterialCategory::getParentId, id).eq(MaterialCategory::getStatus, 1));
            if (children > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "存在有效下级分类, 禁止停用");
            }
            Long materials = materialMapper.selectCount(new LambdaQueryWrapper<Material>()
                    .eq(Material::getCategoryId, id).eq(Material::getStatus, 1));
            if (materials > 0) {
                throw new BizException(ErrorCode.PARAM_INVALID, "分类下存在有效物资, 禁止停用");
            }
        }
        MaterialCategory update = new MaterialCategory();
        update.setId(id);
        update.setStatus(status);
        updateById(update);
    }
}
