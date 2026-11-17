-- ============================================================================
-- 042 —— 授权看板（020）
--
-- 变更：
--   ① 新表 member_license_grant —— 运营赠送授权的留痕（一次赠送一行，不可修改/删除）
--   ② member_subscription / maxlabel_org_license 各加 license_grant_id（赠送来源）
--   ③ 两表的 plan_id / spu_id / sku_id / source_order_* 放宽为可空
--      —— 手工赠送没有订单与套餐快照；MySQL 唯一索引允许多个 NULL，
--         既有订单行完全不受影响（uk_source_item 保留）
--   ④ 菜单「授权看板」（/software 下）+ 按钮权限 member:license:gift
--
-- 幂等：DDL 前一律查 information_schema；菜单走 INSERT ... SELECT ... WHERE NOT EXISTS。
-- 上线顺序：本脚本 → 后端 → 管理端 → 给角色勾权限（执行前须得到所有者确认，宪法原则 VI）。
-- 回滚：新表可保留（只增不改）；license_grant_id 可留；
--       ⚠️ 不要把放宽的列改回 NOT NULL —— 一旦已有赠送行，改回去会失败。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 赠送记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `member_license_grant` (
  `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '赠送编号',
  `target_type`     tinyint      NOT NULL COMMENT '对象类型：1 会员 2 企业',
  `target_id`       bigint       NOT NULL COMMENT '会员编号或企业编号',
  `content_type`    tinyint      NOT NULL COMMENT '内容：1 时长 2 席位',
  `edition`         varchar(32)  NULL DEFAULT NULL COMMENT '赠送版本快照：professional / enterprise',
  `months`          int          NULL DEFAULT NULL COMMENT '赠送月数（content_type=1 必填）',
  `seats`           int          NULL DEFAULT NULL COMMENT '赠送席位数（content_type=2 必填）',
  `reason`          varchar(255) NOT NULL COMMENT '赠送理由（必填）',
  `before_end_time` datetime     NULL DEFAULT NULL COMMENT '赠送前的到期时间',
  `after_end_time`  datetime     NOT NULL COMMENT '赠送后的到期时间',
  `before_seats`    int          NULL DEFAULT NULL COMMENT '赠送前的席位数',
  `after_seats`     int          NULL DEFAULT NULL COMMENT '赠送后的席位数',
  `operator_id`     bigint       NOT NULL COMMENT '操作人（system_users.id）',
  `creator`         varchar(64)  NULL DEFAULT '' COMMENT '创建者',
  `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater`         varchar(64)  NULL DEFAULT '' COMMENT '更新者',
  `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除（不使用）',
  `tenant_id`       bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_target`(`tenant_id` ASC, `target_type` ASC, `target_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_operator`(`tenant_id` ASC, `operator_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_create_time`(`tenant_id` ASC, `create_time` ASC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = 'MaxLabel 授权赠送记录';

-- ---------------------------------------------------------------------------
-- 2. 两张授权表：加 `license_grant_id`
-- ---------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_subscription' AND COLUMN_NAME = 'license_grant_id');
SET @s := IF(@c = 0,
  'ALTER TABLE `member_subscription` ADD COLUMN `license_grant_id` bigint NULL DEFAULT NULL COMMENT ''赠送来源：member_license_grant.id；订单来源为 NULL'' AFTER `source_order_no`',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_org_license' AND COLUMN_NAME = 'license_grant_id');
SET @s := IF(@c = 0,
  'ALTER TABLE `maxlabel_org_license` ADD COLUMN `license_grant_id` bigint NULL DEFAULT NULL COMMENT ''赠送来源：member_license_grant.id；订单来源为 NULL'' AFTER `source_order_no`',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------------------------------------------------------------------------
-- 3. 放宽来源列与套餐列为可空（赠送行没有这些快照）
--    逐列判 is_nullable，已经是 YES 就跳过；MODIFY 时保留原类型与注释。
-- ---------------------------------------------------------------------------
-- 3.1 member_subscription
SET @s := (
  SELECT CONCAT('ALTER TABLE `member_subscription` MODIFY COLUMN `plan_id` bigint NULL DEFAULT NULL COMMENT ''计划编号，关联 maxlabel_subscription_plan.id（赠送行为 NULL）''')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_subscription' AND COLUMN_NAME = 'plan_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `member_subscription` MODIFY COLUMN `spu_id` bigint NULL DEFAULT NULL COMMENT ''商品 SPU 快照（赠送行为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_subscription' AND COLUMN_NAME = 'spu_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `member_subscription` MODIFY COLUMN `sku_id` bigint NULL DEFAULT NULL COMMENT ''商品 SKU 快照（赠送行为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_subscription' AND COLUMN_NAME = 'sku_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `member_subscription` MODIFY COLUMN `source_order_id` bigint NULL DEFAULT NULL COMMENT ''来源订单编号（赠送行为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_subscription' AND COLUMN_NAME = 'source_order_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `member_subscription` MODIFY COLUMN `source_order_item_id` bigint NULL DEFAULT NULL COMMENT ''来源订单项编号（幂等键；赠送行为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_subscription' AND COLUMN_NAME = 'source_order_item_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `member_subscription` MODIFY COLUMN `source_order_no` varchar(64) NULL DEFAULT NULL COMMENT ''来源订单号快照（赠送行为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'member_subscription' AND COLUMN_NAME = 'source_order_no' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3.2 maxlabel_org_license（同一组列）
SET @s := (SELECT 'ALTER TABLE `maxlabel_org_license` MODIFY COLUMN `plan_id` bigint NULL DEFAULT NULL COMMENT ''计划编号（赠送批次为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_org_license' AND COLUMN_NAME = 'plan_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `maxlabel_org_license` MODIFY COLUMN `spu_id` bigint NULL DEFAULT NULL COMMENT ''商品 SPU 快照（赠送批次为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_org_license' AND COLUMN_NAME = 'spu_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `maxlabel_org_license` MODIFY COLUMN `sku_id` bigint NULL DEFAULT NULL COMMENT ''商品 SKU 快照（赠送批次为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_org_license' AND COLUMN_NAME = 'sku_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `maxlabel_org_license` MODIFY COLUMN `source_order_id` bigint NULL DEFAULT NULL COMMENT ''来源订单编号（赠送批次为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_org_license' AND COLUMN_NAME = 'source_order_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `maxlabel_org_license` MODIFY COLUMN `source_order_item_id` bigint NULL DEFAULT NULL COMMENT ''来源订单项编号（幂等键；赠送批次为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_org_license' AND COLUMN_NAME = 'source_order_item_id' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := (SELECT 'ALTER TABLE `maxlabel_org_license` MODIFY COLUMN `source_order_no` varchar(64) NULL DEFAULT NULL COMMENT ''来源订单号快照（赠送批次为 NULL）'''
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_org_license' AND COLUMN_NAME = 'source_order_no' AND IS_NULLABLE = 'NO');
SET @s := IFNULL(@s, 'SELECT 1'); PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------------------------------------------------------------------------
-- 4. 菜单：授权看板（挂 /software 下，排在软件分类之后）
-- ---------------------------------------------------------------------------
SET @software_group_id = (
  SELECT `id` FROM `system_menu`
   WHERE `path` = '/software' AND `parent_id` = 0 AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '授权看板', 'member:license:query', 2, 60, @software_group_id,
       'license', 'ep:data-analysis', 'member/license/index',
       'MemberLicense', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:license:query') AS existed
);

SET @license_page_id = (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:license:query' AND `deleted` = b'0' LIMIT 1);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '授权赠送', 'member:license:gift', 3, 1, @license_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:license:gift') AS existed
);
