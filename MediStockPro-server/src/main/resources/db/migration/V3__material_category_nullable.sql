-- 物资分类允许为空 (P060 CSV 导入可不填分类; 分类为弱约束)
ALTER TABLE `material` MODIFY COLUMN `category_id` BIGINT UNSIGNED NULL;
