-- ============================================================================
-- 014（009 MaxLabel 会员体系）—— 5 张表 + 管理端菜单/权限
--
-- 幂等：表用 IF NOT EXISTS；菜单用 WHERE NOT EXISTS（重复执行不插第二份）。
-- 执行：mysql -u<user> -p <db> < 014-maxlabel-member.sql
--      或 python scripts/apply_003_filter_dimension.py --file yudao-cloud/sql/mysql/014-maxlabel-member.sql
--
-- 执行记录：
--   2026-10-20 已通过应用服务器 8.146.233.197 的 SSH 内网隧道，在
--   172.20.68.219:3306/ruoyi-vue-pro（MySQL 8.0.44）执行：18/18 条成功；
--   5 张表创建完成，9 个权限点插入完成；**幂等复跑通过**（无重复行）。
--   ⚠️ 菜单行已就绪，但角色仍需在「角色管理」里勾选这些权限。
--
-- 回滚（按依赖顺序）：
--   DELETE FROM system_menu WHERE permission IN (
--     'member:shared-template:query','member:shared-template:audit','member:shared-template:offline',
--     'product:subscription-plan:query','product:subscription-plan:create','product:subscription-plan:update',
--     'member:subscription:query','member:cloud-template:query','member:print-record:query');
--   DROP TABLE IF EXISTS maxlabel_print_record;
--   DROP TABLE IF EXISTS maxlabel_shared_template;
--   DROP TABLE IF EXISTS maxlabel_label_template;
--   DROP TABLE IF EXISTS member_subscription;
--   DROP TABLE IF EXISTS maxlabel_subscription_plan;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 会员授权商品映射（SKU → 周期）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_subscription_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `spu_id` bigint NOT NULL COMMENT '商品 SPU 编号',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 编号（购买单元）',
  `period_months` int NOT NULL COMMENT '授予周期（自然月，如 1 / 12）',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（保留列，业务不使用）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_sku`(`tenant_id` ASC, `sku_id` ASC) USING BTREE,
  INDEX `idx_spu`(`spu_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 会员授权商品映射（SKU → 周期）';

-- ---------------------------------------------------------------------------
-- 2. 会员订阅授予流水
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `member_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_id` bigint NOT NULL COMMENT '会员编号，关联 member_user.id',
  `plan_id` bigint NOT NULL COMMENT '计划编号，关联 maxlabel_subscription_plan.id',
  `spu_id` bigint NOT NULL COMMENT '商品 SPU 快照',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 快照',
  `period_months` int NOT NULL COMMENT '本段授予月数（plan.period_months × 数量）',
  `start_time` datetime NOT NULL COMMENT '本段开始自然日 00:00:00（=max(今天, 上段 end_time 的日期)）',
  `end_time` datetime NOT NULL COMMENT '本段最后有效自然日的次日 00:00:00（不含当日）',
  `source_order_id` bigint NOT NULL COMMENT '来源订单编号',
  `source_order_item_id` bigint NOT NULL COMMENT '来源订单项编号（幂等键）',
  `source_order_no` varchar(64) NOT NULL COMMENT '来源订单号快照（后台展示）',
  `refunded` bit(1) NOT NULL DEFAULT b'0' COMMENT '来源订单或会员订单项是否已全额退款（仅标记，不回收）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_source_item`(`tenant_id` ASC, `source_order_item_id` ASC) USING BTREE,
  INDEX `idx_member_end`(`tenant_id` ASC, `member_id` ASC, `end_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 会员订阅授予流水';

-- ---------------------------------------------------------------------------
-- 3. 会员私有云端模板
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_label_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_id` bigint NOT NULL COMMENT '所属会员，关联 member_user.id',
  `name` varchar(255) NOT NULL COMMENT '模板名称',
  `content` longtext NOT NULL COMMENT '模板正文（.msdx JSON 原文）',
  `size_bytes` int unsigned NOT NULL COMMENT '正文字节数',
  `content_hash` char(64) NOT NULL COMMENT '正文 SHA-256（重复上传/排障用）',
  `width_mm` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标签宽度（毫米，检索用）',
  `height_mm` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标签高度（毫米，检索用）',
  `category` varchar(64) NULL DEFAULT '' COMMENT '分类（自定义，检索用）',
  `keywords` varchar(255) NULL DEFAULT '' COMMENT '关键词（逗号分隔）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_member_time`(`tenant_id` ASC, `member_id` ASC, `create_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 会员私有云端模板';

-- ---------------------------------------------------------------------------
-- 4. 平台共享模板（清洗后的副本）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_shared_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `source_template_id` bigint NOT NULL COMMENT '来源私有模板编号',
  `publisher_member_id` bigint NOT NULL COMMENT '发布会员编号',
  `name` varchar(255) NOT NULL COMMENT '模板名称',
  `content` longtext NOT NULL COMMENT '清洗后的模板正文',
  `size_bytes` int unsigned NOT NULL COMMENT '正文字节数',
  `width_mm` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标签宽度（毫米）',
  `height_mm` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标签高度（毫米）',
  `category` varchar(64) NULL DEFAULT '' COMMENT '分类',
  `keywords` varchar(255) NULL DEFAULT '' COMMENT '关键词',
  `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态：0 待审核 1 已通过 2 已驳回 3 已下架',
  `reject_reason` varchar(255) NULL DEFAULT NULL COMMENT '驳回原因（驳回时必填）',
  `audit_user_id` bigint NULL DEFAULT NULL COMMENT '审核人，关联 system_users.id',
  `audit_time` datetime NULL DEFAULT NULL COMMENT '审核时间',
  `published_time` datetime NULL DEFAULT NULL COMMENT '通过并公开的时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_source`(`tenant_id` ASC, `source_template_id` ASC) USING BTREE,
  INDEX `idx_audit`(`tenant_id` ASC, `audit_status` ASC, `published_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 平台共享模板（清洗后的副本）';

-- ---------------------------------------------------------------------------
-- 5. 会员打印记录（不含业务数据快照）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_print_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_id` bigint NOT NULL COMMENT '会员编号',
  `device_id` char(36) NOT NULL COMMENT '桌面端随机设备标识（UUID，非机器指纹）',
  `record_uid` char(36) NOT NULL COMMENT '桌面端生成的记录 UUID（幂等）',
  `print_time` datetime NOT NULL COMMENT '打印时间（桌面端时间）',
  `template_title` varchar(255) NOT NULL COMMENT '模板名称',
  `mode` varchar(16) NOT NULL COMMENT '打印方式：driver / command',
  `count` int NOT NULL COMMENT '计划标签张数',
  `copies` int NOT NULL COMMENT '单签拷贝数',
  `physical_count` bigint NULL DEFAULT NULL COMMENT '实际打印张数（可空）',
  `sent_count` bigint NULL DEFAULT NULL COMMENT '已发送张数（可空）',
  `status` varchar(16) NOT NULL COMMENT '状态：completed/submitted/partial/failed/canceled/unknown',
  `printer` varchar(255) NOT NULL DEFAULT '' COMMENT '打印机名称',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_record`(`tenant_id` ASC, `member_id` ASC, `record_uid` ASC) USING BTREE,
  INDEX `idx_member_time`(`tenant_id` ASC, `member_id` ASC, `print_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 会员打印记录（不含业务数据快照）';

-- ============================================================================
-- 管理端菜单 / 权限
--
-- ⚠️ 权限串必须登记成 system_menu 行，否则 v-hasPermi 会把按钮从 DOM 删掉，
--    连超管也看不到（后端 @PreAuthorize 对 super_admin 有旁路，会表现为"接口能调、按钮不显示"）。
-- ============================================================================

SET @member_center_id = (
  SELECT `id` FROM `system_menu`
   WHERE `name` = '会员中心' AND `parent_id` = 0 AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

-- 页面：共享模板审核
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '共享模板审核', 'member:shared-template:query', 2, 90, @member_center_id,
       'template-review', 'ep:document-checked', 'member/template/review/index',
       'MemberSharedTemplateReview', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:shared-template:query') AS existed
);

-- 页面：会员授权商品
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '会员授权商品', 'product:subscription-plan:query', 2, 91, @member_center_id,
       'subscription-plan', 'ep:goods', 'member/subscription/plan/index',
       'MemberSubscriptionPlan', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'product:subscription-plan:query') AS existed
);

SET @review_page_id = (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:shared-template:query' LIMIT 1);
SET @plan_page_id = (SELECT `id` FROM `system_menu` WHERE `permission` = 'product:subscription-plan:query' LIMIT 1);
SET @member_user_page_id = (SELECT `parent_id` FROM `system_menu` WHERE `permission` = 'member:user:query' LIMIT 1);

-- 按钮：共享模板审核通过/驳回
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '审核通过/驳回', 'member:shared-template:audit', 3, 1, @review_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:shared-template:audit') AS existed
);

-- 按钮：共享模板下架
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '下架共享模板', 'member:shared-template:offline', 3, 2, @review_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:shared-template:offline') AS existed
);

-- 按钮：新增会员授权计划
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '新增计划', 'product:subscription-plan:create', 3, 1, @plan_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'product:subscription-plan:create') AS existed
);

-- 按钮：修改/停用会员授权计划
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '修改/停用计划', 'product:subscription-plan:update', 3, 2, @plan_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'product:subscription-plan:update') AS existed
);

-- 页签权限：订阅 / 模板 / 打印记录（挂在会员管理页下）
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '会员订阅查询', 'member:subscription:query', 3, 20, @member_user_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:subscription:query') AS existed
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '会员模板查询', 'member:cloud-template:query', 3, 21, @member_user_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:cloud-template:query') AS existed
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '会员打印记录查询', 'member:print-record:query', 3, 22, @member_user_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:print-record:query') AS existed
);

-- 自检：
--   SELECT id, name, permission, type, parent_id FROM system_menu
--    WHERE permission LIKE 'member:%' OR permission LIKE 'product:subscription-plan:%'
--    ORDER BY parent_id, sort;
