-- ============================================================================
-- 021（012 企业客户与席位授权）
--
-- 内容：4 张新表 + 3 个既有表加列 + 企业管理的菜单/权限。
-- 幂等：表用 IF NOT EXISTS；加列/加索引用 information_schema 判断 + 动态 SQL；
--       菜单用 WHERE NOT EXISTS。
--
-- 执行：python scripts/apply_003_filter_dimension.py --file yudao-cloud/sql/mysql/021-enterprise-seat-licensing.sql
--
-- 执行记录：
--   2026-10-26 已通过应用服务器 8.146.233.197 的 SSH 内网隧道，在
--   172.20.68.219:3306/ruoyi-vue-pro（MySQL 8.0.44）执行：36/36 条成功；
--   4 张新表创建完成；3 列（maxlabel_subscription_plan.scope、
--   product_spu.enterprise_only、trade_order.maxlabel_org_id）齐全；
--   10 个权限点插入完成。**幂等复跑通过**（第二次执行无报错、无重复行）。
--   回查：
--       SELECT table_name FROM information_schema.tables
--        WHERE table_schema = DATABASE()
--          AND table_name IN ('maxlabel_org','maxlabel_org_member','maxlabel_org_license','maxlabel_org_seat');
--       SELECT table_name, column_name, column_type, is_nullable, column_default
--         FROM information_schema.columns
--        WHERE table_schema = DATABASE()
--          AND (table_name, column_name) IN
--              (('maxlabel_subscription_plan','scope'),('product_spu','enterprise_only'),('trade_order','maxlabel_org_id'));
--       SELECT permission FROM system_menu WHERE permission LIKE 'member:org:%' ORDER BY permission;
--
-- 回滚（按依赖顺序）：
--   DELETE FROM system_menu WHERE permission IN (
--     'member:org:query','member:org:create','member:org:update',
--     'member:org:member:add','member:org:member:remove','member:org:member:update-role',
--     'member:org:owner:transfer','member:org:seat:assign','member:org:seat:revoke',
--     'member:org:license:query');
--   DROP TABLE IF EXISTS maxlabel_org_seat;
--   DROP TABLE IF EXISTS maxlabel_org_license;
--   DROP TABLE IF EXISTS maxlabel_org_member;
--   DROP TABLE IF EXISTS maxlabel_org;
--   -- 3 列/索引可保留（旧代码忽略未知列），有企业数据时不要删。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 企业主体
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_org` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(128) NOT NULL COMMENT '企业名称',
  `owner_member_id` bigint NOT NULL COMMENT '企业 OWNER，关联 member_user.id',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
  `remark` varchar(255) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_name`(`tenant_id` ASC, `name` ASC) USING BTREE,
  INDEX `idx_owner`(`tenant_id` ASC, `owner_member_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业主体';

-- ---------------------------------------------------------------------------
-- 2. 企业成员关系（退出/移出物理删除）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_org_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '企业编号，关联 maxlabel_org.id',
  `member_id` bigint NOT NULL COMMENT '会员编号，关联 member_user.id',
  `role` tinyint NOT NULL DEFAULT 3 COMMENT '角色：1 OWNER 2 ADMIN 3 MEMBER',
  `joined_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（退出时物理删除，本列保留）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_member`(`tenant_id` ASC, `member_id` ASC) USING BTREE,
  INDEX `idx_org`(`tenant_id` ASC, `org_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业成员关系';

-- ---------------------------------------------------------------------------
-- 3. 企业授权批次（一行 = 一次支付的一段周期 × 一批席位）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_org_license` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '企业编号，关联 maxlabel_org.id',
  `plan_id` bigint NOT NULL COMMENT '计划编号，关联 maxlabel_subscription_plan.id',
  `spu_id` bigint NOT NULL COMMENT '商品 SPU 快照',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 快照',
  `period_months` int NOT NULL COMMENT '本段授予月数（plan.period_months × 数量）',
  `seats_total` int NOT NULL COMMENT '本批授予席位数（= 订单项数量）',
  `start_time` datetime NOT NULL COMMENT '本段开始自然日 00:00:00',
  `end_time` datetime NOT NULL COMMENT '本段最后有效自然日的次日 00:00:00（不含当日）',
  `source_order_id` bigint NOT NULL COMMENT '来源订单编号',
  `source_order_item_id` bigint NOT NULL COMMENT '来源订单项编号（幂等键）',
  `source_order_no` varchar(64) NOT NULL COMMENT '来源订单号快照（后台展示）',
  `refunded` bit(1) NOT NULL DEFAULT b'0' COMMENT '来源订单或订单项是否已全额退款（仅标记，不回收）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_source_item`(`tenant_id` ASC, `source_order_item_id` ASC) USING BTREE,
  INDEX `idx_org_end`(`tenant_id` ASC, `org_id` ASC, `end_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业授权批次';

-- ---------------------------------------------------------------------------
-- 4. 席位分配（不绑定具体批次；撤销后复用同一行）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_org_seat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '企业编号，关联 maxlabel_org.id',
  `member_id` bigint NOT NULL COMMENT '会员编号，关联 member_user.id',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 有效 1 已撤销',
  `operator_type` tinyint NOT NULL DEFAULT 1 COMMENT '操作人类型：1 会员 2 系统用户',
  `operator_id` bigint NOT NULL DEFAULT 0 COMMENT '操作人编号（会员或 system_users.id）',
  `assigned_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次分配时间',
  `revoked_time` datetime NULL DEFAULT NULL COMMENT '撤销时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_org_member`(`tenant_id` ASC, `org_id` ASC, `member_id` ASC) USING BTREE,
  INDEX `idx_org_status`(`tenant_id` ASC, `org_id` ASC, `status` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业席位分配';

-- ---------------------------------------------------------------------------
-- 5. 既有表加列（幂等：先查 information_schema，再动态执行）
-- ---------------------------------------------------------------------------

-- 5.1 计划类别：1 个人 2 企业
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'maxlabel_subscription_plan'
     AND column_name = 'scope'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `maxlabel_subscription_plan` ADD COLUMN `scope` tinyint NOT NULL DEFAULT 1 COMMENT ''类别：1 个人 2 企业'' AFTER `period_months`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5.2 商品是否企业专享
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'product_spu'
     AND column_name = 'enterprise_only'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `product_spu` ADD COLUMN `enterprise_only` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否企业专享：0 否 1 是''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5.3 订单快照企业
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'trade_order'
     AND column_name = 'maxlabel_org_id'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_order` ADD COLUMN `maxlabel_org_id` bigint NULL DEFAULT NULL COMMENT ''MaxLabel 企业编号（企业授权订单快照）''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5.4 订单企业索引
SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'trade_order'
     AND index_name = 'idx_maxlabel_org'
);
SET @ddl = IF(@idx_exists = 0,
  'CREATE INDEX `idx_maxlabel_org` ON `trade_order`(`tenant_id` ASC, `maxlabel_org_id` ASC)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 6. 菜单 / 权限（挂在「会员中心」下）
-- ---------------------------------------------------------------------------
SET @member_center_id = (
  SELECT `id` FROM `system_menu`
   WHERE `name` = '会员中心' AND `parent_id` = 0 AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

-- 页面：企业管理
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '企业管理', 'member:org:query', 2, 95, @member_center_id,
       'org', 'ep:office-building', 'member/org/index',
       'MemberOrg', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:query') AS existed
);

SET @org_page_id = (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:query' LIMIT 1);

-- 按钮：新增企业
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '新增企业', 'member:org:create', 3, 1, @org_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:create') AS existed
);

-- 按钮：修改 / 停用企业
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '修改/停用企业', 'member:org:update', 3, 2, @org_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:update') AS existed
);

-- 按钮：新增成员
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '新增成员', 'member:org:member:add', 3, 3, @org_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:member:add') AS existed
);

-- 按钮：移出成员
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '移出成员', 'member:org:member:remove', 3, 4, @org_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:member:remove') AS existed
);

-- 按钮：调整 ADMIN
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '调整管理员', 'member:org:member:update-role', 3, 5, @org_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:member:update-role') AS existed
);

-- 按钮：转交 OWNER
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '转交 OWNER', 'member:org:owner:transfer', 3, 6, @org_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:owner:transfer') AS existed
);

-- 按钮：分配席位
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '分配席位', 'member:org:seat:assign', 3, 7, @org_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:seat:assign') AS existed
);

-- 按钮：撤销席位
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '撤销席位', 'member:org:seat:revoke', 3, 8, @org_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:seat:revoke') AS existed
);

-- 按钮：查看授权批次
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '查看授权批次', 'member:org:license:query', 3, 9, @org_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:org:license:query') AS existed
);

-- 自检：
--   SELECT id, name, permission, type, parent_id FROM system_menu
--    WHERE permission LIKE 'member:org:%'
--    ORDER BY parent_id, sort;
