package com.medistock.pro.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.medistock.pro.modules.master.entity.Material;
import com.medistock.pro.modules.master.entity.MaterialCategory;
import com.medistock.pro.modules.master.entity.Warehouse;
import com.medistock.pro.modules.master.mapper.MaterialCategoryMapper;
import com.medistock.pro.modules.master.service.MaterialService;
import com.medistock.pro.modules.master.service.WarehouseService;
import com.medistock.pro.modules.system.entity.RolePermission;
import com.medistock.pro.modules.system.entity.SysPermission;
import com.medistock.pro.modules.system.entity.SysRole;
import com.medistock.pro.modules.system.entity.SysUser;
import com.medistock.pro.modules.system.entity.SysUserRole;
import com.medistock.pro.modules.system.entity.SystemParam;
import com.medistock.pro.modules.system.mapper.RolePermissionMapper;
import com.medistock.pro.modules.system.mapper.SysPermissionMapper;
import com.medistock.pro.modules.system.mapper.SysRoleMapper;
import com.medistock.pro.modules.system.mapper.SysUserRoleMapper;
import com.medistock.pro.modules.system.mapper.SystemParamMapper;
import com.medistock.pro.modules.system.service.RbacService;
import com.medistock.pro.modules.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首次启动种子数据 (仅当对应表为空时):
 *  - admin / admin123
 *  - 演示仓库 + 演示物资 (M2 冒烟用)
 * TODO: 正式环境删除或改为 Flyway migration
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserService sysUserService;
    private final WarehouseService warehouseService;
    private final MaterialService materialService;
    private final SysPermissionMapper permissionMapper;
    private final SysRoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final SystemParamMapper systemParamMapper;
    private final RbacService rbacService;

    /** 权限点目录: 与控制器 @SaCheckPermission 对齐 */
    private static final Map<String, String> PERMISSIONS = new LinkedHashMap<>() {{
        put("MATERIAL_MANAGE_VIEW", "物资-查看");
        put("MATERIAL_MANAGE_CREATE", "物资-新建");
        put("MATERIAL_MANAGE_EDIT", "物资-编辑");
        put("ORG_MANAGE_VIEW", "组织仓库-查看");
        put("ORG_MANAGE_CREATE", "组织仓库-新建");
        put("ORG_MANAGE_EDIT", "组织仓库-编辑");
        put("INVENTORY_VIEW", "库存总览-查看");
        put("INVENTORY_DETAIL_VIEW", "库存明细-查看");
        put("BATCH_MANAGE_VIEW", "批次管理-查看");
        put("BATCH_MANAGE_EDIT", "批次管理-编辑");
        put("INVENTORY_LEDGER_VIEW", "库存台账-查看");
        put("STOCK_INBOUND_VIEW", "入库单-查看");
        put("STOCK_INBOUND_EXECUTE", "入库单-执行");
        put("ISSUE_REQUEST_VIEW", "科室申领-查看");
        put("ISSUE_REQUEST_EDIT", "科室申领-编辑");
        put("ISSUE_REQUEST_APPROVE", "科室申领-审批");
        put("PICK_EXECUTE_VIEW", "拣货任务-查看");
        put("PICK_EXECUTE", "拣货任务-执行");
        put("ISSUE_CONFIRM", "出库复核签收");
        put("ISSUE_REVERSE", "出库红冲");
        put("STOCK_TRANSFER_VIEW", "调拨单-查看");
        put("STOCK_TRANSFER_CREATE", "调拨单-编辑");
        put("STOCK_TRANSFER_APPROVE", "调拨单-审批");
        put("STOCK_TRANSFER_EXECUTE", "调拨单-收发执行");
        put("STOCK_COUNT_VIEW", "盘点-查看");
        put("STOCK_COUNT_CREATE", "盘点-编辑");
        put("STOCK_COUNT_EXECUTE", "盘点-录入");
        put("STOCK_COUNT_CONFIRM", "盘点-确认调账");
        put("STOCK_RETURN_VIEW", "退库单-查看");
        put("STOCK_RETURN_CREATE", "退库单-编辑");
        put("STOCK_RETURN_CONFIRM", "退库单-确认");
        put("STOCK_SCRAP_VIEW", "报废单-查看");
        put("STOCK_SCRAP_CREATE", "报废单-编辑");
        put("STOCK_SCRAP_APPROVE", "报废单-审批");
        put("REPORT_VIEW", "报表-查看");
        put("SUPPLIER_VIEW", "供应商-查看");
        put("SUPPLIER_EDIT", "供应商-编辑");
        put("PURCHASE_REQUEST_VIEW", "采购申请-查看");
        put("PURCHASE_REQUEST_EDIT", "采购申请-编辑");
        put("PURCHASE_REQUEST_APPROVE", "采购申请-审批");
        put("PURCHASE_AGREEMENT_VIEW", "采购协议-查看");
        put("PURCHASE_AGREEMENT_EDIT", "采购协议-编辑");
        put("PURCHASE_ORDER_VIEW", "采购订单-查看");
        put("PURCHASE_ORDER_CREATE", "采购订单-编辑");
        put("PURCHASE_ORDER_APPROVE", "采购订单-审批");
        put("PURCHASE_RECEIPT_VIEW", "收货单-查看");
        put("PURCHASE_RECEIPT_REGISTER", "收货单-登记");
        put("PURCHASE_ACCEPTANCE_VIEW", "验收单-查看");
        put("PURCHASE_ACCEPTANCE_CREATE", "验收单-生成");
        put("PURCHASE_ACCEPTANCE_EXECUTE", "验收单-执行");
        put("USER_MANAGE_VIEW", "用户管理-查看");
        put("USER_MANAGE_EDIT", "用户管理-编辑");
        put("ROLE_MANAGE_VIEW", "角色管理-查看");
        put("ROLE_MANAGE_EDIT", "角色管理-编辑");
        put("DASHBOARD_VIEW", "仪表盘-查看");
        put("SEARCH_VIEW", "全局搜索");
        put("MESSAGE_VIEW", "消息中心-查看");
        put("MESSAGE_MANAGE", "消息中心-已读操作");
        put("APPROVAL_CENTER", "审批中心");
        put("DOCUMENT_VIEW", "单据中心-查看");
        put("AUDIT_LOG_VIEW", "操作日志-查看");
        put("SYSTEM_PARAM_MANAGE", "系统参数-管理");
        put("DATA_SCOPE_MANAGE", "数据权限-管理");
        put("MATERIAL_CATEGORY_MANAGE_VIEW", "物资分类-查看");
        put("MATERIAL_CATEGORY_MANAGE_EDIT", "物资分类-编辑");
        put("MATERIAL_UOM_MANAGE_VIEW", "包装换算-查看");
        put("MATERIAL_UOM_MANAGE_EDIT", "包装换算-编辑");
        put("SUPPLIER_MATERIAL_MANAGE_VIEW", "供应商物资-查看");
        put("SUPPLIER_MATERIAL_MANAGE_EDIT", "供应商物资-编辑");
        put("ALERT_VIEW", "预警中心-查看");
        put("REPORT_SUPPLIER", "供应商绩效报表");
        put("ACCEPTANCE_EXCEPTION", "验收异常-登记处理");
        put("DATA_IMPORT_EXPORT", "数据导入导出");
        put("API_MONITOR_VIEW", "接口监控-查看");
        put("MATERIAL_ATTRIBUTE_MANAGE_EDIT", "物资属性-批量修改");
    }};

    @Override
    public void run(String... args) {
        if (sysUserService.count() == 0) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setEmployeeNo("0001");
            admin.setPassword(new BCryptPasswordEncoder().encode("admin123"));
            admin.setName("系统管理员");
            admin.setStatus(1);
            sysUserService.save(admin);
            log.info("已初始化管理员账号 admin / admin123, 请尽快修改密码");
        }

        // 权限点目录 + ADMIN 角色 + admin 挂接 (需在 admin 用户之后执行)
        seedRbac();

        // 数据权限测试种子: pharmacist(单仓) + warehouse_admin(多仓) + pharma 用户
        seedTestDataScope();

        if (warehouseService.count() == 0) {
            warehouseService.save(warehouse("WH001", "药库", "DRUG_DEPOT"));
            warehouseService.save(warehouse("WH002", "中心药房", "CENTER_PHARMACY"));
            warehouseService.save(warehouse("WH003", "门诊药房", "OUTPATIENT"));
            warehouseService.save(warehouse("WH004", "住院药房", "INPATIENT"));
            log.info("已初始化演示仓库 WH001~WH004");
        }

        // 系统参数种子 (幂等)
        seedSystemParams();

        // 物资分类种子 + 既有 category_id=0 数据修复
        Long drugCategoryId = seedCategories();

        if (materialService.count() == 0) {
            materialService.save(material("YP001", "阿莫西林胶囊", "0.25g*24粒", "华北制药", 50, drugCategoryId));
            materialService.save(material("YP002", "布洛芬缓释胶囊", "0.3g*20粒", "中美史克", 60, drugCategoryId));
            materialService.save(material("YP003", "注射用头孢曲松钠", "1.0g*10支", "罗氏制药", 30, drugCategoryId));
            log.info("已初始化演示物资 YP001~YP003");
        } else if (drugCategoryId != null) {
            // 历史种子 category_id=0 的修正为药品分类
            materialService.update(null, new LambdaUpdateWrapper<Material>()
                    .eq(Material::getCategoryId, 0L)
                    .set(Material::getCategoryId, drugCategoryId));
        }
    }

    /** 分类种子: 药品/耗材/试剂 (幂等), 返回"药品"分类ID */
    private Long seedCategories() {
        if (categoryMapper.selectCount(null) == 0) {
            categoryMapper.insert(category("DRUG", "药品", 1));
            categoryMapper.insert(category("CONSUMABLE", "耗材", 2));
            categoryMapper.insert(category("REAGENT", "试剂", 3));
            log.info("已初始化物资分类 药品/耗材/试剂");
        }
        MaterialCategory drug = categoryMapper.selectOne(new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getCode, "DRUG"));
        return drug != null ? drug.getId() : null;
    }

    private MaterialCategory category(String code, String name, int sort) {
        MaterialCategory c = new MaterialCategory();
        c.setCode(code);
        c.setName(name);
        c.setParentId(null);
        c.setSort(sort);
        c.setStatus(1);
        return c;
    }

    /** RBAC 种子: 权限点目录 + ADMIN 角色(全量权限) + admin 用户挂接 (幂等, 已有则跳过) */
    private void seedRbac() {
        for (Map.Entry<String, String> e : PERMISSIONS.entrySet()) {
            Long count = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                    .eq(SysPermission::getPermCode, e.getKey()));
            if (count == 0) {
                SysPermission p = new SysPermission();
                p.setPermCode(e.getKey());
                p.setPermName(e.getValue());
                p.setPermType("ACTION");
                p.setStatus(1);
                permissionMapper.insert(p);
            }
        }

        SysRole adminRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, "ADMIN"));
        if (adminRole == null) {
            adminRole = new SysRole();
            adminRole.setCode("ADMIN");
            adminRole.setName("系统管理员");
            adminRole.setDataScope("ALL");
            adminRole.setStatus(1);
            adminRole.setRemark("内置角色: 全部权限");
            roleMapper.insert(adminRole);
        }
        // ADMIN 增量补齐: 目录新增权限点在重启后自动挂接, 不依赖首次初始化
        List<SysPermission> all = permissionMapper.selectList(null);
        List<Long> linkedIds = rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, adminRole.getId()))
                .stream().map(RolePermission::getPermissionId).toList();
        int added = 0;
        for (SysPermission p : all) {
            if (linkedIds.contains(p.getId())) {
                continue;
            }
            RolePermission rp = new RolePermission();
            rp.setRoleId(adminRole.getId());
            rp.setPermissionId(p.getId());
            rolePermissionMapper.insert(rp);
            added++;
        }
        if (added > 0) {
            log.info("已为 ADMIN 角色增量装配 {} 个权限点 (共 {})", added, all.size());
        }

        SysUser admin = sysUserService.getByUsername("admin");
        if (admin != null) {
            Long urCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, admin.getId()));
            if (urCount == 0) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(admin.getId());
                ur.setRoleId(adminRole.getId());
                userRoleMapper.insert(ur);
                log.info("已为 admin 用户挂接 ADMIN 角色");
            }
        }
    }

    /** 系统参数种子 (P058): 仅在 key 不存在时插入 v1 */
    private void seedSystemParams() {
        seedParam("expiry.alert.days", "90", "效期预警天数");
        seedParam("stock.fefo.enabled", "true", "FEFO 先到期先出");
        seedParam("approval.issue.enabled", "true", "领用审批开关");
        seedParam("approval.purchase.enabled", "true", "采购审批开关");
        seedParam("bill.no.date.format", "yyyyMMdd", "单号日期格式");
    }

    private void seedParam(String key, String value, String name) {
        Long count = systemParamMapper.selectCount(new LambdaQueryWrapper<SystemParam>()
                .eq(SystemParam::getParamKey, key));
        if (count > 0) {
            return;
        }
        SystemParam p = new SystemParam();
        p.setParamKey(key);
        p.setParamValue(value);
        p.setParamName(name);
        p.setVersion(1);
        p.setStatus(1);
        systemParamMapper.insert(p);
        log.info("已初始化系统参数 {}={}", key, value);
    }

    /** 数据权限测试种子 (幂等): pharmacist 角色(WAREHOUSE:WH002) + pharma 用户; warehouse_admin 角色(WAREHOUSE:WH003+WH004) */
    private void seedTestDataScope() {
        Warehouse wh2 = warehouseService.lambdaQuery().eq(Warehouse::getCode, "WH002").one();
        Warehouse wh3 = warehouseService.lambdaQuery().eq(Warehouse::getCode, "WH003").one();
        Warehouse wh4 = warehouseService.lambdaQuery().eq(Warehouse::getCode, "WH004").one();

        // pharmacist 角色: 单仓库 (WH002)
        SysRole pharmacist = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, "PHARMACIST"));
        if (pharmacist == null) {
            pharmacist = new SysRole();
            pharmacist.setCode("PHARMACIST");
            pharmacist.setName("药房管理员");
            pharmacist.setDataScope("WAREHOUSE");
            pharmacist.setStatus(1);
            pharmacist.setRemark("测试角色: 单仓库数据权限");
            roleMapper.insert(pharmacist);
            if (wh2 != null) {
                rbacService.assignDataScope(pharmacist.getId(), "WAREHOUSE", java.util.List.of(wh2.getId()));
            }
            log.info("已初始化角色 PHARMACIST (WAREHOUSE: WH002)");
        }

        // warehouse_admin 角色: 多仓库 (WH003+WH004)
        SysRole whAdmin = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, "WAREHOUSE_ADMIN"));
        if (whAdmin == null) {
            whAdmin = new SysRole();
            whAdmin.setCode("WAREHOUSE_ADMIN");
            whAdmin.setName("多仓管理员");
            whAdmin.setDataScope("WAREHOUSE");
            whAdmin.setStatus(1);
            whAdmin.setRemark("测试角色: 多仓库数据权限");
            roleMapper.insert(whAdmin);
            java.util.List<Long> whIds = new java.util.ArrayList<>();
            if (wh3 != null) whIds.add(wh3.getId());
            if (wh4 != null) whIds.add(wh4.getId());
            if (!whIds.isEmpty()) {
                rbacService.assignDataScope(whAdmin.getId(), "WAREHOUSE", whIds);
            }
            log.info("已初始化角色 WAREHOUSE_ADMIN (WAREHOUSE: WH003+WH004)");
        }

        // pharma 用户: 绑定 pharmacist 角色
        SysUser pharma = sysUserService.getByUsername("pharma");
        if (pharma == null) {
            pharma = new SysUser();
            pharma.setUsername("pharma");
            pharma.setEmployeeNo(String.format("%04d", sysUserService.count() + 1));
            pharma.setPassword(new BCryptPasswordEncoder().encode("123456"));
            pharma.setName("药房管理员");
            pharma.setStatus(1);
            sysUserService.save(pharma);
            if (pharmacist != null) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(pharma.getId());
                ur.setRoleId(pharmacist.getId());
                userRoleMapper.insert(ur);
            }
            log.info("已初始化测试账号 pharma / 123456 (WAREHOUSE: WH002)");
        }
    }

    private Warehouse warehouse(String code, String name, String type) {
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName(name);
        w.setType(type);
        w.setStatus(1);
        return w;
    }

    private Material material(String code, String name, String spec, String manufacturer, int safetyQty, Long categoryId) {
        Material m = new Material();
        m.setCode(code);
        m.setName(name);
        m.setSpec(spec);
        m.setUom("盒");
        m.setManufacturer(manufacturer);
        m.setCategoryId(categoryId);
        m.setBatchManaged(1);
        m.setExpiryManaged(1);
        m.setSafetyQty(safetyQty);
        m.setStatus(1);
        return m;
    }
}
