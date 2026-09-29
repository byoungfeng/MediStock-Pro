-- ============================================================================
-- MediStock Pro 医院进销存系统 · 数据库设计 DDL (V4.0)
-- 基线: V2.0《数据库V2表结构》33 表 + V3.0《库存引擎设计》字段模型
-- 数据库: MySQL 8.0+ / InnoDB / utf8mb4
--
-- P0 底线落地:
--   1. 库存不得负数      -> inventory_batch CHECK 约束 + 乐观锁 version + 行锁
--   2. 变化可追溯        -> inventory_txn 只增不改, 一切变动携来源单据
--   3. 关键动作幂等      -> 单号唯一索引 + Idempotency-Key + version 乐观锁
--   4. 并发不重复扣减    -> UPDATE ... WHERE id=? AND version=? 影响行数校验
--   5. 生效单据不物理删除 -> 仅 DRAFT 可删(deleted), 生效后红冲/作废
--
-- V3.0 库存模型: available_qty = on_hand - locked_qty (派生值, 不落库, 禁改)
--                in_transit_qty 为调出仓批次的在途量(发运加/接收减)
-- ============================================================================
-- (Flyway V1: 已剥离 CREATE DATABASE/USE, 连接串自带 medistock_pro 库)
-- ============================================================================
-- 一、组织与权限
-- ============================================================================

-- 1. 组织机构 (集团 -> 医院 -> 院区 -> 科室)
CREATE TABLE `org_unit` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`       VARCHAR(32)  NOT NULL              COMMENT '组织编码',
  `name`       VARCHAR(64)  NOT NULL              COMMENT '名称',
  `type`       VARCHAR(16)  NOT NULL              COMMENT 'GROUP集团/HOSPITAL医院/CAMPUS院区/DEPT科室',
  `parent_id`  BIGINT UNSIGNED DEFAULT NULL       COMMENT '上级组织',
  `status`     TINYINT      NOT NULL DEFAULT 1,
  `created_by` BIGINT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` BIGINT UNSIGNED DEFAULT NULL,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB COMMENT='组织机构';

-- 2. 用户
CREATE TABLE `sys_user` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`     VARCHAR(32)  NOT NULL,
  `employee_no`  VARCHAR(32)  NOT NULL              COMMENT '工号',
  `password`     VARCHAR(128) NOT NULL              COMMENT 'BCrypt',
  `name`         VARCHAR(64)  NOT NULL              COMMENT '姓名',
  `org_id`       BIGINT UNSIGNED DEFAULT NULL       COMMENT '所属科室/部门',
  `phone`        VARCHAR(32)  DEFAULT NULL,
  `email`        VARCHAR(64)  DEFAULT NULL,
  `status`       TINYINT      NOT NULL DEFAULT 1,
  `last_login_at` DATETIME    DEFAULT NULL,
  `created_by`   BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_employee_no` (`employee_no`),
  KEY `idx_org` (`org_id`)
) ENGINE=InnoDB COMMENT='用户';

-- 3. 角色 (数据范围: ALL全院/CAMPUS授权院区/WAREHOUSE授权仓库/DEPT本科室/SELF本人)
CREATE TABLE `sys_role` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`       VARCHAR(32)  NOT NULL              COMMENT 'LEADER院领导/BUYER采购员/KEEPER仓管/ACCEPTOR验收员/APPLICANT科室申请人/DEPT_HEAD科室负责人/FINANCE财务/ADMIN',
  `name`       VARCHAR(64)  NOT NULL,
  `data_scope` VARCHAR(16)  NOT NULL DEFAULT 'SELF',
  `status`     TINYINT      NOT NULL DEFAULT 1,
  `remark`     VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB COMMENT='角色';

-- 4. 权限点 (菜单+动作, 编码见《05_菜单权限编码》剪枝版)
CREATE TABLE `sys_permission` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `perm_code`  VARCHAR(64)  NOT NULL              COMMENT '如 PURCHASE_ORDER_APPROVE',
  `perm_name`  VARCHAR(64)  NOT NULL,
  `perm_type`  VARCHAR(8)   NOT NULL DEFAULT 'ACTION' COMMENT 'MENU/ACTION',
  `parent_id`  BIGINT UNSIGNED DEFAULT NULL,
  `sort`       INT          NOT NULL DEFAULT 0,
  `status`     TINYINT      NOT NULL DEFAULT 1,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB COMMENT='权限点';

-- 5. 用户-角色
CREATE TABLE `sys_user_role` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `role_id`    BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`)
) ENGINE=InnoDB COMMENT='用户角色';

-- 6. 角色-权限
CREATE TABLE `role_permission` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `role_id`       BIGINT UNSIGNED NOT NULL,
  `permission_id` BIGINT UNSIGNED NOT NULL,
  `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`)
) ENGINE=InnoDB COMMENT='角色权限';

-- 7. 角色/用户-仓库数据权限绑定 (data_scope=WAREHOUSE 时生效)
CREATE TABLE `sys_role_warehouse` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `role_id`      BIGINT UNSIGNED NOT NULL,
  `warehouse_id` BIGINT UNSIGNED NOT NULL,
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_wh` (`role_id`,`warehouse_id`)
) ENGINE=InnoDB COMMENT='角色仓库数据权限';

-- 8. 系统字典
CREATE TABLE `sys_dict` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `dict_type`  VARCHAR(40) NOT NULL               COMMENT 'MATERIAL_CATEGORY/UOM/EXCEPTION_TYPE...',
  `dict_code`  VARCHAR(40) NOT NULL,
  `dict_label` VARCHAR(64) NOT NULL,
  `sort`       INT         NOT NULL DEFAULT 0,
  `status`     TINYINT     NOT NULL DEFAULT 1,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code` (`dict_type`,`dict_code`)
) ENGINE=InnoDB COMMENT='系统字典';

-- 9. 系统参数 (编号规则/效期阈值/库存策略/审批策略, 支持发布与回滚)
CREATE TABLE `system_param` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `param_key`   VARCHAR(64)  NOT NULL             COMMENT '如 expiry.alert.days / bill.no.prefix',
  `param_value` VARCHAR(500) NOT NULL,
  `param_name`  VARCHAR(64)  NOT NULL,
  `version`     INT          NOT NULL DEFAULT 1   COMMENT '发布版本(回滚用)',
  `status`      TINYINT      NOT NULL DEFAULT 1   COMMENT '1已发布 0草稿',
  `updated_by`  BIGINT UNSIGNED DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_param_key_ver` (`param_key`,`version`)
) ENGINE=InnoDB COMMENT='系统参数';

-- ============================================================================
-- 二、物资主数据
-- ============================================================================

-- 10. 物资分类 (树形)
CREATE TABLE `material_category` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`       VARCHAR(32) NOT NULL,
  `name`       VARCHAR(64) NOT NULL,
  `parent_id`  BIGINT UNSIGNED DEFAULT NULL,
  `sort`       INT         NOT NULL DEFAULT 0,
  `status`     TINYINT     NOT NULL DEFAULT 1,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB COMMENT='物资分类(药品/耗材/试剂...)';

-- 11. 物资主数据 (药品=物资一类, 批号/效期/UDI 属性内聚)
CREATE TABLE `material` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`           VARCHAR(32)  NOT NULL          COMMENT '物资编码',
  `name`           VARCHAR(128) NOT NULL,
  `spec`           VARCHAR(64)  DEFAULT NULL      COMMENT '规格',
  `uom`            VARCHAR(16)  NOT NULL          COMMENT '基本单位(最小单位)',
  `brand`          VARCHAR(64)  DEFAULT NULL,
  `manufacturer`   VARCHAR(128) DEFAULT NULL      COMMENT '生产厂家',
  `category_id`    BIGINT UNSIGNED NOT NULL,
  `is_high_value`  TINYINT      NOT NULL DEFAULT 0 COMMENT '高值耗材',
  `batch_managed`  TINYINT      NOT NULL DEFAULT 1 COMMENT '批号管理',
  `expiry_managed` TINYINT      NOT NULL DEFAULT 1 COMMENT '效期管理',
  `udi_managed`    TINYINT      NOT NULL DEFAULT 0 COMMENT 'UDI管理',
  `safety_qty`     INT          DEFAULT NULL      COMMENT '安全库存下限',
  `max_qty`        INT          DEFAULT NULL      COMMENT '库存上限(超储预警)',
  `expiry_alert_days` INT       DEFAULT NULL      COMMENT '效期预警天数(空=取系统参数)',
  `status`         TINYINT      NOT NULL DEFAULT 1,
  `remark`         VARCHAR(255) DEFAULT NULL,
  `created_by`     BIGINT UNSIGNED DEFAULT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`     BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_category` (`category_id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB COMMENT='物资主数据';

-- 12. 单位换算 (采购单位/库存单位/领用单位)
CREATE TABLE `material_uom` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `material_id` BIGINT UNSIGNED NOT NULL,
  `from_uom`    VARCHAR(16) NOT NULL              COMMENT '源单位(如 箱)',
  `to_uom`      VARCHAR(16) NOT NULL              COMMENT '目标单位(如 盒)',
  `rate`        DECIMAL(14,4) NOT NULL            COMMENT '换算率(1 from = rate to)',
  `status`      TINYINT       NOT NULL DEFAULT 1,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversion` (`material_id`,`from_uom`,`to_uom`),
  CONSTRAINT `chk_uom_rate_positive` CHECK (`rate` > 0)
) ENGINE=InnoDB COMMENT='单位换算';

-- ============================================================================
-- 三、供应商
-- ============================================================================

-- 13. 供应商档案
CREATE TABLE `supplier` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(32)  NOT NULL,
  `name`        VARCHAR(128) NOT NULL,
  `credit_code` VARCHAR(32)  NOT NULL             COMMENT '统一社会信用代码',
  `contact`     VARCHAR(64)  DEFAULT NULL,
  `phone`       VARCHAR(32)  DEFAULT NULL,
  `address`     VARCHAR(255) DEFAULT NULL,
  `risk_level`  VARCHAR(8)   NOT NULL DEFAULT 'C' COMMENT '风险等级 A/B/C',
  `status`      TINYINT      NOT NULL DEFAULT 1,
  `remark`      VARCHAR(255) DEFAULT NULL,
  `created_by`  BIGINT UNSIGNED DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`  BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  UNIQUE KEY `uk_credit_code` (`credit_code`)
) ENGINE=InnoDB COMMENT='供应商档案';

-- 14. 供应商资质 (证照/授权, 过期禁止新增采购订单)
CREATE TABLE `supplier_qualification` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `supplier_id` BIGINT UNSIGNED NOT NULL,
  `type`        VARCHAR(32)  NOT NULL             COMMENT '营业执照/经营许可证/GSP/厂家授权',
  `cert_no`     VARCHAR(64)  DEFAULT NULL,
  `file_id`     VARCHAR(64)  DEFAULT NULL         COMMENT '附件文件ID',
  `valid_from`  DATE         DEFAULT NULL,
  `valid_to`    DATE         NOT NULL             COMMENT '有效期至',
  `status`      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待审/VALID有效/EXPIRED过期/REJECTED',
  `created_by`  BIGINT UNSIGNED DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`  BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_supplier_valid_to` (`supplier_id`,`valid_to`)
) ENGINE=InnoDB COMMENT='供应商资质';

-- 15. 供应商-物资关系 (报价/税/交期/MOQ)
CREATE TABLE `supplier_material` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `supplier_id` BIGINT UNSIGNED NOT NULL,
  `material_id` BIGINT UNSIGNED NOT NULL,
  `price`       DECIMAL(14,2) DEFAULT NULL        COMMENT '最近报价',
  `tax_rate`    DECIMAL(5,2)  DEFAULT NULL        COMMENT '税率%',
  `lead_days`   INT           DEFAULT NULL        COMMENT '交期(天)',
  `moq`         INT           DEFAULT NULL        COMMENT '最小起订量',
  `status`      TINYINT       NOT NULL DEFAULT 1,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_material` (`supplier_id`,`material_id`)
) ENGINE=InnoDB COMMENT='供应商物资关系';

-- 16. 采购协议
CREATE TABLE `purchase_agreement` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `agreement_no` VARCHAR(40)  NOT NULL,
  `supplier_id`  BIGINT UNSIGNED NOT NULL,
  `start_date`   DATE         NOT NULL,
  `end_date`     DATE         NOT NULL,
  `pay_terms`    VARCHAR(128) DEFAULT NULL        COMMENT '付款条件',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/ACTIVE/TERMINATED',
  `remark`       VARCHAR(500) DEFAULT NULL,
  `created_by`   BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agreement_no` (`agreement_no`)
) ENGINE=InnoDB COMMENT='采购协议';

-- 17. 采购协议明细 (协议价)
CREATE TABLE `purchase_agreement_item` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `agreement_id` BIGINT UNSIGNED NOT NULL,
  `material_id`  BIGINT UNSIGNED NOT NULL,
  `price`        DECIMAL(14,2) NOT NULL           COMMENT '协议价',
  `tax_rate`     DECIMAL(5,2)  DEFAULT NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agreement_material` (`agreement_id`,`material_id`)
) ENGINE=InnoDB COMMENT='采购协议明细';

-- ============================================================================
-- 四、采购链路 (申请 -> 审批 -> 订单 -> 审批 -> 到货 -> 验收 -> 入库)
-- ============================================================================

-- 18. 采购申请
CREATE TABLE `purchase_request` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_no`    VARCHAR(40) NOT NULL            COMMENT '申请单号(幂等键)',
  `department_id` BIGINT UNSIGNED NOT NULL        COMMENT '申请科室',
  `warehouse_id`  BIGINT UNSIGNED NOT NULL        COMMENT '收货仓库',
  `status`        VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED/CLOSED',
  `purpose`       VARCHAR(255) DEFAULT NULL       COMMENT '用途',
  `version`       INT         NOT NULL DEFAULT 0,
  `created_by`    BIGINT UNSIGNED DEFAULT NULL,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`    BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_status` (`status`),
  KEY `idx_dept` (`department_id`)
) ENGINE=InnoDB COMMENT='采购申请';

-- 19. 采购申请明细
CREATE TABLE `purchase_request_item` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_id`   BIGINT UNSIGNED NOT NULL,
  `material_id`  BIGINT UNSIGNED NOT NULL,
  `qty`          INT NOT NULL                     COMMENT '申请数量',
  `approved_qty` INT DEFAULT NULL                 COMMENT '批准数量(审批可改量)',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_request` (`request_id`),
  CONSTRAINT `chk_pri_qty_positive` CHECK (`qty` > 0)
) ENGINE=InnoDB COMMENT='采购申请明细';

-- 20. 采购订单
CREATE TABLE `purchase_order` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`     VARCHAR(40) NOT NULL             COMMENT '订单号(幂等键)',
  `supplier_id`  BIGINT UNSIGNED NOT NULL,
  `agreement_id` BIGINT UNSIGNED DEFAULT NULL     COMMENT '关联协议',
  `request_id`   BIGINT UNSIGNED DEFAULT NULL     COMMENT '来源申请',
  `warehouse_id` BIGINT UNSIGNED NOT NULL         COMMENT '收货仓库',
  `status`       VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/PART_RECEIVED/COMPLETED/CLOSED/REVERSED',
  `total_qty`    INT           NOT NULL DEFAULT 0,
  `total_amount` DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `expect_date`  DATE          DEFAULT NULL       COMMENT '承诺到货日',
  `version`      INT           NOT NULL DEFAULT 0,
  `remark`       VARCHAR(500)  DEFAULT NULL,
  `created_by`   BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_supplier` (`supplier_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='采购订单';

-- 21. 采购订单明细
CREATE TABLE `purchase_order_item` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT UNSIGNED NOT NULL,
  `material_id`  BIGINT UNSIGNED NOT NULL,
  `ordered_qty`  INT           NOT NULL,
  `received_qty` INT           NOT NULL DEFAULT 0 COMMENT '已验收合格入库量',
  `unit_price`   DECIMAL(14,2) NOT NULL,
  `tax_rate`     DECIMAL(5,2)  DEFAULT NULL,
  `amount`       DECIMAL(14,2) NOT NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  CONSTRAINT `chk_poi_qty_positive` CHECK (`ordered_qty` > 0),
  CONSTRAINT `chk_poi_received`     CHECK (`received_qty` >= 0 AND `received_qty` <= `ordered_qty`)
) ENGINE=InnoDB COMMENT='采购订单明细';

-- 22. 到货单
CREATE TABLE `receipt` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `receipt_no`   VARCHAR(40) NOT NULL,
  `order_id`     BIGINT UNSIGNED NOT NULL,
  `arrival_date` DATE         NOT NULL            COMMENT '到货日期',
  `box_count`    INT          DEFAULT NULL,
  `transport_no` VARCHAR(64)  DEFAULT NULL        COMMENT '运输单号',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED已登记/SUBMITTED待验收/ACCEPTED已验收',
  `version`      INT          NOT NULL DEFAULT 0,
  `remark`       VARCHAR(500) DEFAULT NULL,
  `created_by`   BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_receipt_no` (`receipt_no`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB COMMENT='到货单';

-- 23. 到货明细
CREATE TABLE `receipt_item` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `receipt_id`  BIGINT UNSIGNED NOT NULL,
  `material_id` BIGINT UNSIGNED NOT NULL,
  `batch_no`    VARCHAR(64) DEFAULT NULL,
  `expiry_date` DATE        DEFAULT NULL,
  `qty`         INT         NOT NULL              COMMENT '到货数量',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_receipt` (`receipt_id`),
  CONSTRAINT `chk_ri_qty_positive` CHECK (`qty` > 0)
) ENGINE=InnoDB COMMENT='到货明细';

-- 24. 验收单
CREATE TABLE `acceptance` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `acceptance_no` VARCHAR(40) NOT NULL,
  `receipt_id`    BIGINT UNSIGNED NOT NULL,
  `status`        VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待验收/COMPLETED已完成',
  `result`        VARCHAR(16) DEFAULT NULL        COMMENT 'QUALIFIED合格/PARTIAL部分合格/REJECTED拒收',
  `version`       INT         NOT NULL DEFAULT 0,
  `remark`        VARCHAR(500) DEFAULT NULL,
  `created_by`    BIGINT UNSIGNED DEFAULT NULL,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`    BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_acceptance_no` (`acceptance_no`),
  KEY `idx_receipt` (`receipt_id`)
) ENGINE=InnoDB COMMENT='验收单';

-- 25. 验收明细 (合格+不合格=实收; 仅合格量可入库)
CREATE TABLE `acceptance_item` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `acceptance_id` BIGINT UNSIGNED NOT NULL,
  `material_id`   BIGINT UNSIGNED NOT NULL,
  `batch_no`      VARCHAR(64) NOT NULL,
  `expiry_date`   DATE        DEFAULT NULL,
  `received_qty`  INT         NOT NULL            COMMENT '实收数量',
  `accepted_qty`  INT         NOT NULL DEFAULT 0  COMMENT '合格数量',
  `rejected_qty`  INT         NOT NULL DEFAULT 0  COMMENT '不合格数量(隔离/拒收)',
  `reject_reason` VARCHAR(255) DEFAULT NULL,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_acceptance` (`acceptance_id`),
  KEY `idx_batch_expiry` (`batch_no`,`expiry_date`),
  CONSTRAINT `chk_ai_qty_balance` CHECK (`accepted_qty` + `rejected_qty` = `received_qty`),
  CONSTRAINT `chk_ai_qty_positive` CHECK (`received_qty` > 0)
) ENGINE=InnoDB COMMENT='验收明细';

-- ============================================================================
-- 五、仓储与库存引擎
-- ============================================================================

-- 26. 仓库
CREATE TABLE `warehouse` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `code`       VARCHAR(32) NOT NULL,
  `name`       VARCHAR(64) NOT NULL,
  `type`       VARCHAR(20) NOT NULL DEFAULT 'GENERAL' COMMENT 'DRUG_DEPOT药库/CENTER_PHARMACY中心药房/OUTPATIENT门诊/INPATIENT住院/GENERAL',
  `org_id`     BIGINT UNSIGNED DEFAULT NULL         COMMENT '所属院区/组织',
  `parent_id`  BIGINT UNSIGNED DEFAULT NULL,
  `status`     TINYINT     NOT NULL DEFAULT 1,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`    TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB COMMENT='仓库';

-- 27. 库位 (库区-货架-库位)
CREATE TABLE `location` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `warehouse_id` BIGINT UNSIGNED NOT NULL,
  `zone`         VARCHAR(32) DEFAULT NULL         COMMENT '库区',
  `shelf`        VARCHAR(32) DEFAULT NULL         COMMENT '货架',
  `code`         VARCHAR(32) NOT NULL             COMMENT '库位编码',
  `capacity`     INT         DEFAULT NULL,
  `status`       TINYINT     NOT NULL DEFAULT 1,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wh_loc_code` (`warehouse_id`,`code`)
) ENGINE=InnoDB COMMENT='库位';

-- 28. 入库单
CREATE TABLE `stock_inbound` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `inbound_no`    VARCHAR(40) NOT NULL,
  `biz_type`      VARCHAR(20) NOT NULL DEFAULT 'PURCHASE' COMMENT 'PURCHASE采购/TRANSFER_IN调拨/SURPLUS盘盈/RETURN退库/ADJUST调整',
  `acceptance_id` BIGINT UNSIGNED DEFAULT NULL    COMMENT '来源验收单',
  `source_id`     BIGINT UNSIGNED DEFAULT NULL    COMMENT '通用来源单据ID',
  `source_no`     VARCHAR(40) DEFAULT NULL,
  `warehouse_id`  BIGINT UNSIGNED NOT NULL,
  `status`        VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待入库/CONFIRMED已入库/REVERSED',
  `total_qty`     INT         NOT NULL DEFAULT 0,
  `total_amount`  DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `version`       INT         NOT NULL DEFAULT 0,
  `remark`        VARCHAR(500) DEFAULT NULL,
  `created_by`    BIGINT UNSIGNED DEFAULT NULL,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`    BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inbound_no` (`inbound_no`),
  KEY `idx_wh_status` (`warehouse_id`,`status`),
  KEY `idx_source` (`biz_type`,`source_id`)
) ENGINE=InnoDB COMMENT='入库单';

-- 29. 入库明细
CREATE TABLE `stock_inbound_item` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `inbound_id`   BIGINT UNSIGNED NOT NULL,
  `material_id`  BIGINT UNSIGNED NOT NULL,
  `location_id`  BIGINT UNSIGNED DEFAULT NULL     COMMENT '上架库位',
  `batch_no`     VARCHAR(64) NOT NULL,
  `production_date` DATE     DEFAULT NULL,
  `expiry_date`  DATE        DEFAULT NULL,
  `qty`          INT         NOT NULL             COMMENT '入库数量(=验收合格量)',
  `unit_cost`    DECIMAL(14,4) DEFAULT NULL,
  `amount`       DECIMAL(14,2) DEFAULT NULL,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_inbound` (`inbound_id`),
  CONSTRAINT `chk_sii_qty_positive` CHECK (`qty` > 0)
) ENGINE=InnoDB COMMENT='入库明细';

-- 30. 批次库存 (V3.0 核心: 现存量唯一权威; available = on_hand - locked 派生禁改)
CREATE TABLE `inventory_batch` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `warehouse_id`    BIGINT UNSIGNED NOT NULL,
  `location_id`     BIGINT UNSIGNED DEFAULT NULL,
  `material_id`     BIGINT UNSIGNED NOT NULL,
  `batch_no`        VARCHAR(64)     NOT NULL,
  `production_date` DATE            DEFAULT NULL,
  `expiry_date`     DATE            DEFAULT NULL,
  `on_hand`         INT             NOT NULL DEFAULT 0 COMMENT '实际在库',
  `locked_qty`      INT             NOT NULL DEFAULT 0 COMMENT '锁定(拣货/审批占用)',
  `in_transit_qty`  INT             NOT NULL DEFAULT 0 COMMENT '调拨在途(本仓调出未达)',
  `unit_cost`       DECIMAL(14,4)   DEFAULT NULL,
  `status`          VARCHAR(16)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/FROZEN冻结/EXPIRED过期/SCRAPPED报废',
  `version`         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁(V3.0)',
  `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wh_loc_material_batch` (`warehouse_id`,`location_id`,`material_id`,`batch_no`),
  KEY `idx_expiry` (`expiry_date`),
  KEY `idx_wh_material` (`warehouse_id`,`material_id`),
  CONSTRAINT `chk_on_hand_non_negative`    CHECK (`on_hand` >= 0),
  CONSTRAINT `chk_locked_non_negative`     CHECK (`locked_qty` >= 0),
  CONSTRAINT `chk_locked_le_on_hand`       CHECK (`locked_qty` <= `on_hand`),
  CONSTRAINT `chk_in_transit_non_negative` CHECK (`in_transit_qty` >= 0)
) ENGINE=InnoDB COMMENT='批次库存(现存量权威表)';

-- 31. 库存流水 (只增不改; 同一来源单据+明细+动作仅一条有效流水)
CREATE TABLE `inventory_txn` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `txn_no`       VARCHAR(48)  NOT NULL            COMMENT '流水号=来源单号:明细行:动作(幂等键)',
  `warehouse_id` BIGINT UNSIGNED NOT NULL,
  `material_id`  BIGINT UNSIGNED NOT NULL,
  `batch_no`     VARCHAR(64)  NOT NULL,
  `qty`          INT          NOT NULL            COMMENT '变动数量(正入负出)',
  `direction`    VARCHAR(4)   NOT NULL            COMMENT 'IN/OUT',
  `before_qty`   INT          NOT NULL,
  `after_qty`    INT          NOT NULL,
  `source_type`  VARCHAR(20)  NOT NULL            COMMENT 'INBOUND/ISSUE/TRANSFER_SHIP/TRANSFER_RECEIVE/ADJUST/SCRAP/RETURN/LOCK/UNLOCK',
  `source_id`    BIGINT UNSIGNED NOT NULL,
  `source_no`    VARCHAR(40)  NOT NULL,
  `operator_id`  BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_txn_no` (`txn_no`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_batch_time` (`material_id`,`warehouse_id`,`created_at`)
) ENGINE=InnoDB COMMENT='库存流水(只增不改)';

-- ============================================================================
-- 六、科室领用链路 (申领 -> 审批 -> 拣货(锁定) -> 复核 -> 出库 -> 签收/退回)
-- ============================================================================

-- 32. 领用申请
CREATE TABLE `issue_request` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_no`    VARCHAR(40) NOT NULL,
  `department_id` BIGINT UNSIGNED NOT NULL,
  `warehouse_id`  BIGINT UNSIGNED NOT NULL        COMMENT '发放仓库',
  `priority`      VARCHAR(8)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/URGENT紧急',
  `purpose`       VARCHAR(255) DEFAULT NULL,
  `status`        VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/REJECTED/PICKING/COMPLETED',
  `version`       INT         NOT NULL DEFAULT 0,
  `created_by`    BIGINT UNSIGNED DEFAULT NULL,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`    BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_no` (`request_no`),
  KEY `idx_dept_status` (`department_id`,`status`)
) ENGINE=InnoDB COMMENT='科室领用申请';

-- 33. 领用申请明细
CREATE TABLE `issue_request_item` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `request_id`   BIGINT UNSIGNED NOT NULL,
  `material_id`  BIGINT UNSIGNED NOT NULL,
  `qty`          INT NOT NULL,
  `approved_qty` INT DEFAULT NULL,
  `issued_qty`   INT NOT NULL DEFAULT 0           COMMENT '已发数量',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_request` (`request_id`),
  CONSTRAINT `chk_iri_qty_positive` CHECK (`qty` > 0),
  CONSTRAINT `chk_iri_issued`       CHECK (`issued_qty` >= 0 AND `issued_qty` <= `qty`)
) ENGINE=InnoDB COMMENT='领用申请明细';

-- 34. 拣货任务 (审批通过后生成, 同时锁定库存)
CREATE TABLE `pick_task` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_no`      VARCHAR(40) NOT NULL,
  `source_id`    BIGINT UNSIGNED NOT NULL         COMMENT '来源领用申请ID',
  `source_no`    VARCHAR(40) NOT NULL,
  `warehouse_id` BIGINT UNSIGNED NOT NULL,
  `status`       VARCHAR(16) NOT NULL DEFAULT 'PICKING' COMMENT 'PICKING拣货中/PICKED待复核/CANCELLED已取消',
  `version`      INT         NOT NULL DEFAULT 0,
  `created_by`   BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_source` (`source_id`)
) ENGINE=InnoDB COMMENT='拣货任务';

-- 35. 拣货明细 (FEFO 推荐批次; 手工改派需 override_reason)
CREATE TABLE `pick_task_item` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `task_id`         BIGINT UNSIGNED NOT NULL,
  `material_id`     BIGINT UNSIGNED NOT NULL,
  `location_id`     BIGINT UNSIGNED DEFAULT NULL,
  `batch_no`        VARCHAR(64) NOT NULL          COMMENT 'FEFO推荐批号',
  `suggested_qty`   INT         NOT NULL          COMMENT '应拣数量',
  `picked_qty`      INT         DEFAULT NULL      COMMENT '实拣数量',
  `short_reason`    VARCHAR(255) DEFAULT NULL     COMMENT '短拣原因',
  `override_reason` VARCHAR(255) DEFAULT NULL     COMMENT '非FEFO改派原因',
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  CONSTRAINT `chk_pti_picked` CHECK (`picked_qty` IS NULL OR (`picked_qty` >= 0 AND `picked_qty` <= `suggested_qty`))
) ENGINE=InnoDB COMMENT='拣货明细';

-- 36. 出库单 (复核通过后确认出库: 扣 on_hand 并释放锁定)
CREATE TABLE `issue_order` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `issue_no`      VARCHAR(40) NOT NULL,
  `request_id`    BIGINT UNSIGNED DEFAULT NULL    COMMENT '来源领用申请',
  `pick_task_id`  BIGINT UNSIGNED DEFAULT NULL,
  `warehouse_id`  BIGINT UNSIGNED NOT NULL,
  `department_id` BIGINT UNSIGNED DEFAULT NULL    COMMENT '领用科室',
  `status`        VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待复核/CONFIRMED已出库/SIGNED已签收/REVERSED',
  `total_qty`     INT         NOT NULL DEFAULT 0,
  `total_amount`  DECIMAL(14,2) NOT NULL DEFAULT 0.00,
  `sign_by`       VARCHAR(64) DEFAULT NULL        COMMENT '签收人',
  `sign_time`     DATETIME    DEFAULT NULL,
  `version`       INT         NOT NULL DEFAULT 0,
  `remark`        VARCHAR(500) DEFAULT NULL,
  `created_by`    BIGINT UNSIGNED DEFAULT NULL,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`    BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`       TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_no` (`issue_no`),
  KEY `idx_wh_status` (`warehouse_id`,`status`),
  KEY `idx_dept` (`department_id`)
) ENGINE=InnoDB COMMENT='出库单';

-- 37. 出库明细
CREATE TABLE `issue_order_item` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `issue_id`    BIGINT UNSIGNED NOT NULL,
  `material_id` BIGINT UNSIGNED NOT NULL,
  `batch_no`    VARCHAR(64) NOT NULL,
  `qty`         INT         NOT NULL,
  `unit_cost`   DECIMAL(14,4) DEFAULT NULL,
  `amount`      DECIMAL(14,2) DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_issue` (`issue_id`),
  CONSTRAINT `chk_ioi_qty_positive` CHECK (`qty` > 0)
) ENGINE=InnoDB COMMENT='出库明细';

-- 38. 退库单 (不超过原单可退余额)
CREATE TABLE `return_order` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `return_no`    VARCHAR(40) NOT NULL,
  `issue_id`     BIGINT UNSIGNED NOT NULL         COMMENT '原出库单',
  `warehouse_id` BIGINT UNSIGNED NOT NULL,
  `reason`       VARCHAR(255) DEFAULT NULL,
  `status`       VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/CONFIRMED已完成/REVERSED',
  `version`      INT         NOT NULL DEFAULT 0,
  `created_by`   BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_return_no` (`return_no`),
  KEY `idx_issue` (`issue_id`)
) ENGINE=InnoDB COMMENT='退库单';

-- 39. 退库明细
CREATE TABLE `return_order_item` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `return_id`   BIGINT UNSIGNED NOT NULL,
  `material_id` BIGINT UNSIGNED NOT NULL,
  `batch_no`    VARCHAR(64) NOT NULL,
  `qty`         INT         NOT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_return` (`return_id`),
  CONSTRAINT `chk_roi_qty_positive` CHECK (`qty` > 0)
) ENGINE=InnoDB COMMENT='退库明细';

-- ============================================================================
-- 七、调拨 (发运=调出仓 on_hand→in_transit; 接收=调入仓入账, 差异进异常)
-- ============================================================================

-- 40. 调拨单
CREATE TABLE `transfer_order` (
  `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `transfer_no`       VARCHAR(40) NOT NULL,
  `from_warehouse_id` BIGINT UNSIGNED NOT NULL,
  `to_warehouse_id`   BIGINT UNSIGNED NOT NULL,
  `reason`            VARCHAR(255) DEFAULT NULL,
  `status`            VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING/APPROVED/IN_TRANSIT/COMPLETED/REVERSED',
  `ship_time`         DATETIME    DEFAULT NULL,
  `receive_time`      DATETIME    DEFAULT NULL,
  `version`           INT         NOT NULL DEFAULT 0,
  `remark`            VARCHAR(500) DEFAULT NULL,
  `created_by`        BIGINT UNSIGNED DEFAULT NULL,
  `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`        BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transfer_no` (`transfer_no`),
  KEY `idx_from` (`from_warehouse_id`,`status`),
  KEY `idx_to` (`to_warehouse_id`,`status`),
  CONSTRAINT `chk_transfer_not_self` CHECK (`from_warehouse_id` <> `to_warehouse_id`)
) ENGINE=InnoDB COMMENT='调拨单';

-- 41. 调拨明细
CREATE TABLE `transfer_order_item` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `transfer_id`  BIGINT UNSIGNED NOT NULL,
  `material_id`  BIGINT UNSIGNED NOT NULL,
  `batch_no`     VARCHAR(64) NOT NULL,
  `qty`          INT NOT NULL                     COMMENT '申请调拨量',
  `shipped_qty`  INT NOT NULL DEFAULT 0           COMMENT '实发量',
  `received_qty` INT NOT NULL DEFAULT 0           COMMENT '实收量(在途=实发-实收)',
  `diff_reason`  VARCHAR(255) DEFAULT NULL        COMMENT '差异原因',
  `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_transfer` (`transfer_id`),
  CONSTRAINT `chk_toi_qty_positive` CHECK (`qty` > 0),
  CONSTRAINT `chk_toi_ship_recv`    CHECK (`received_qty` >= 0 AND `received_qty` <= `shipped_qty` AND `shipped_qty` <= `qty`)
) ENGINE=InnoDB COMMENT='调拨明细';

-- ============================================================================
-- 八、盘点 / 调整 / 报废
-- ============================================================================

-- 42. 盘点计划
CREATE TABLE `count_plan` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `plan_no`      VARCHAR(40) NOT NULL,
  `warehouse_id` BIGINT UNSIGNED NOT NULL,
  `scope`        VARCHAR(64) DEFAULT NULL         COMMENT '范围: 库区/分类JSON',
  `freeze`       TINYINT     NOT NULL DEFAULT 0   COMMENT '冻结策略: 1盘点期间冻结出入库',
  `status`       VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED进行中/PENDING_REVIEW待复核/COMPLETED/REVERSED',
  `snapshot_at`  DATETIME    DEFAULT NULL         COMMENT '快照时间',
  `version`      INT         NOT NULL DEFAULT 0,
  `created_by`   BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_no` (`plan_no`),
  KEY `idx_wh_status` (`warehouse_id`,`status`)
) ENGINE=InnoDB COMMENT='盘点计划';

-- 43. 盘点明细 (快照账面 + 实盘 + 差异; 提交实盘不直接改库存)
CREATE TABLE `count_item` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `plan_id`    BIGINT UNSIGNED NOT NULL,
  `material_id` BIGINT UNSIGNED NOT NULL,
  `location_id` BIGINT UNSIGNED DEFAULT NULL,
  `batch_no`   VARCHAR(64) NOT NULL,
  `book_qty`   INT NOT NULL                       COMMENT '快照账面量',
  `count_qty`  INT DEFAULT NULL                   COMMENT '实盘量',
  `diff_qty`   INT DEFAULT NULL                   COMMENT '差异=实盘-账面',
  `diff_reason` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_plan_batch` (`plan_id`,`material_id`,`batch_no`)
) ENGINE=InnoDB COMMENT='盘点明细';

-- 44. 库存调整单 (盘点差异审批通过后生成; 确认时校验当前库存 version)
CREATE TABLE `stock_adjustment` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `adjustment_no` VARCHAR(40) NOT NULL,
  `warehouse_id` BIGINT UNSIGNED NOT NULL,
  `source_type`  VARCHAR(20) DEFAULT NULL         COMMENT '来源: COUNT盘点/MANUAL',
  `source_id`    BIGINT UNSIGNED DEFAULT NULL,
  `reason`       VARCHAR(255) NOT NULL,
  `status`       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/CONFIRMED/REVERSED',
  `version`      INT         NOT NULL DEFAULT 0,
  `created_by`   BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_adjustment_no` (`adjustment_no`)
) ENGINE=InnoDB COMMENT='库存调整单';

-- 45. 调整明细
CREATE TABLE `stock_adjustment_item` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `adjustment_id` BIGINT UNSIGNED NOT NULL,
  `material_id`   BIGINT UNSIGNED NOT NULL,
  `batch_no`      VARCHAR(64) NOT NULL,
  `diff_qty`      INT         NOT NULL            COMMENT '调整量(正增负减)',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_adjustment` (`adjustment_id`),
  CONSTRAINT `chk_sai_diff_nonzero` CHECK (`diff_qty` <> 0)
) ENGINE=InnoDB COMMENT='库存调整明细';

-- 46. 报损报废单
CREATE TABLE `scrap_order` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `scrap_no`     VARCHAR(40) NOT NULL,
  `warehouse_id` BIGINT UNSIGNED NOT NULL,
  `reason`       VARCHAR(255) NOT NULL            COMMENT '过期/破损/召回',
  `attachments`  VARCHAR(500) DEFAULT NULL        COMMENT '照片附件ID串',
  `status`       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/EXECUTED/REVERSED',
  `version`      INT         NOT NULL DEFAULT 0,
  `created_by`   BIGINT UNSIGNED DEFAULT NULL,
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scrap_no` (`scrap_no`)
) ENGINE=InnoDB COMMENT='报损报废单';

-- 47. 报废明细
CREATE TABLE `scrap_order_item` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `scrap_id`    BIGINT UNSIGNED NOT NULL,
  `material_id` BIGINT UNSIGNED NOT NULL,
  `batch_no`    VARCHAR(64) NOT NULL,
  `qty`         INT         NOT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_scrap` (`scrap_id`),
  CONSTRAINT `chk_soi_qty_positive` CHECK (`qty` > 0)
) ENGINE=InnoDB COMMENT='报废明细';

-- ============================================================================
-- 九、预警 / 审批引擎 / 消息 / 导入导出 / 审计
-- ============================================================================

-- 48. 业务预警 (效期/安全库存/超储/到货逾期; 定时任务生成, 处置闭环)
CREATE TABLE `alert` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `type`        VARCHAR(20) NOT NULL              COMMENT 'EXPIRY效期/LOW_STOCK下限/OVERSTOCK超储/ARRIVAL到货逾期',
  `business_id` BIGINT UNSIGNED NOT NULL          COMMENT '关联对象(批次/订单)',
  `level`       VARCHAR(8)  NOT NULL DEFAULT 'WARN' COMMENT 'INFO/WARN/URGENT',
  `title`       VARCHAR(128) NOT NULL,
  `content`     VARCHAR(500) DEFAULT NULL,
  `due_at`      DATETIME    DEFAULT NULL          COMMENT '到期时间(如效期)',
  `status`      VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN待处置/HANDLED已处置/IGNORED已忽略/CLOSED',
  `handled_by`  BIGINT UNSIGNED DEFAULT NULL,
  `handled_at`  DATETIME    DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type_status_due` (`type`,`status`,`due_at`)
) ENGINE=InnoDB COMMENT='业务预警';

-- 49. 审批实例 (通用审批引擎: 采购申请/订单/领用/报废/调整共用)
CREATE TABLE `approval_instance` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `business_type` VARCHAR(32) NOT NULL            COMMENT 'PURCHASE_REQUEST/PURCHASE_ORDER/ISSUE_REQUEST/SCRAP/ADJUST',
  `business_id`   BIGINT UNSIGNED NOT NULL,
  `status`        VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
  `current_node`  VARCHAR(64) DEFAULT NULL        COMMENT '当前节点',
  `submitter_id`  BIGINT UNSIGNED NOT NULL,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_business` (`business_type`,`business_id`),
  KEY `idx_status_node` (`status`,`current_node`)
) ENGINE=InnoDB COMMENT='审批实例';

-- 50. 审批记录 (只增不改)
CREATE TABLE `approval_record` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT UNSIGNED NOT NULL,
  `node_id`     VARCHAR(64) DEFAULT NULL,
  `approver_id` BIGINT UNSIGNED NOT NULL,
  `action`      VARCHAR(16) NOT NULL              COMMENT 'APPROVE同意/REJECT驳回/TRANSFER转审',
  `comment`     VARCHAR(500) DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_instance` (`instance_id`)
) ENGINE=InnoDB COMMENT='审批记录(只增不改)';

-- 51. 消息中心
CREATE TABLE `message` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL           COMMENT '接收人',
  `type`       VARCHAR(20) NOT NULL               COMMENT 'APPROVAL审批/ALERT预警/SYSTEM系统',
  `title`      VARCHAR(128) NOT NULL,
  `content`    VARCHAR(500) DEFAULT NULL,
  `biz_type`   VARCHAR(32) DEFAULT NULL,
  `biz_id`     BIGINT UNSIGNED DEFAULT NULL,
  `read`       TINYINT     NOT NULL DEFAULT 0,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`,`read`,`created_at`)
) ENGINE=InnoDB COMMENT='消息';

-- 52. 导入导出任务
CREATE TABLE `import_job` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `job_no`      VARCHAR(40) NOT NULL,
  `type`        VARCHAR(20) NOT NULL              COMMENT 'IMPORT/EXPORT',
  `biz_type`    VARCHAR(32) NOT NULL              COMMENT 'MATERIAL/SUPPLIER...',
  `file_id`     VARCHAR(64) DEFAULT NULL,
  `total_count` INT         NOT NULL DEFAULT 0,
  `success_count` INT       NOT NULL DEFAULT 0,
  `fail_count`  INT         NOT NULL DEFAULT 0,
  `fail_detail` TEXT        DEFAULT NULL          COMMENT '失败明细JSON',
  `status`      VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/PARTIAL/FAILED',
  `created_by`  BIGINT UNSIGNED DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_no` (`job_no`)
) ENGINE=InnoDB COMMENT='导入导出任务';

-- 53. 操作日志 (只增不改, 敏感字段脱敏)
CREATE TABLE `operation_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT UNSIGNED DEFAULT NULL,
  `username`    VARCHAR(32)  DEFAULT NULL,
  `module`      VARCHAR(40)  DEFAULT NULL,
  `action`      VARCHAR(64)  DEFAULT NULL,
  `business_id` VARCHAR(64)  DEFAULT NULL         COMMENT '业务对象',
  `method`      VARCHAR(10)  DEFAULT NULL,
  `uri`         VARCHAR(255) DEFAULT NULL,
  `before_json` TEXT         DEFAULT NULL         COMMENT '变更前(脱敏)',
  `after_json`  TEXT         DEFAULT NULL         COMMENT '变更后(脱敏)',
  `ip`          VARCHAR(64)  DEFAULT NULL,
  `status`      TINYINT      DEFAULT 1,
  `error_msg`   VARCHAR(1000) DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_business_time` (`module`,`created_at`),
  KEY `idx_user_time` (`user_id`,`created_at`)
) ENGINE=InnoDB COMMENT='操作日志(只增不改)';

-- ============================================================================
-- 设计说明:
--  1. inventory_txn / approval_record / operation_log 无 updated_at: 应用层禁改禁删
--  2. inventory_batch.version 参与每次库存 UPDATE 的 WHERE 条件 (V3.0 乐观锁)
--  3. 所有单据 version + 单号唯一索引 = 幂等基石; 重复提交返回原单
--  4. acceptance_item 合格/不合格/实收三量平衡由 CHECK 强制
--  5. 可用库存 available = on_hand - locked_qty 为派生值, 不落库, 业务层禁改
-- ============================================================================
