-- V2: 验收异常单 (P025)
CREATE TABLE `acceptance_exception` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `exception_no`   VARCHAR(40)  NOT NULL,
  `acceptance_id`  BIGINT UNSIGNED NOT NULL,
  `type`           VARCHAR(32)  NOT NULL              COMMENT 'QUALITY质量/QUANTITY数量/DAMAGE破损/DOCUMENT单据/OTHER',
  `reason`         VARCHAR(500) NOT NULL              COMMENT '异常原因',
  `responsibility` VARCHAR(32)  DEFAULT NULL          COMMENT 'SUPPLIER供应商/LOGISTICS物流/HOSPITAL医院/OTHER',
  `result`         VARCHAR(500) DEFAULT NULL          COMMENT '处理结果',
  `status`         VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN待处理/RECTIFYING整改中/RESOLVED已关闭',
  `created_by`     BIGINT UNSIGNED DEFAULT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by`     BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exception_no` (`exception_no`),
  KEY `idx_acceptance` (`acceptance_id`)
) ENGINE=InnoDB COMMENT='验收异常单';
