-- ============================================================================
-- 038（018 LabelShop 模板导入：后台批量迁移）
--
-- 内容：
--   1. 2 张新表：导入任务、导入明细
--   2. 按钮权限 member:org:labelshop-import（挂在「企业管理」页面下，企业代导）
--   3. 按钮权限 member:user:labelshop-import（挂在「会员用户」页面下，个人会员代导）
--
-- 幂等：CREATE TABLE IF NOT EXISTS；菜单 WHERE NOT EXISTS。
-- 上线顺序：先执行本脚本，再发后端，最后发管理端 / 桌面端。
--
-- 回查：
--   SHOW TABLES LIKE 'maxlabel_labelshop_import_%';
--   SELECT id, name, permission, type, parent_id FROM system_menu
--    WHERE permission IN ('member:org:labelshop-import', 'member:user:labelshop-import');
--
-- 回滚：
--   DELETE FROM system_menu WHERE permission IN
--     ('member:org:labelshop-import', 'member:user:labelshop-import');
--   DROP TABLE IF EXISTS maxlabel_labelshop_import_item;
--   DROP TABLE IF EXISTS maxlabel_labelshop_import_task;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 导入任务
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_labelshop_import_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `subject_type` tinyint NOT NULL DEFAULT 1 COMMENT '导入对象：1 企业 2 个人会员',
  `org_id` bigint NULL DEFAULT NULL COMMENT '企业编号，关联 maxlabel_org.id；个人会员任务为 NULL',
  `owner_member_id` bigint NOT NULL COMMENT '导入目标会员编号：企业任务为目标 OWNER，个人任务为目标会员',
  `folder_id` bigint NULL DEFAULT NULL COMMENT '本次新建的企业文件夹编号（登录/枚举成功后回填）',
  `folder_name` varchar(64) NULL DEFAULT NULL COMMENT '计划新建的文件夹名称（<时间>导入）；个人任务为 NULL',
  `source_account_masked` varchar(128) NOT NULL COMMENT 'LabelShop 来源账号掩码',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 待执行 1 进行中 2 已完成 3 已失败 4 已中断',
  `phase` varchar(32) NOT NULL DEFAULT '' COMMENT '阶段：LOGIN/LIST/IMPORT/FINISH',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '枚举出的模板总数',
  `processed_count` int NOT NULL DEFAULT 0 COMMENT '已处理数（成功+跳过+失败）',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '成功数',
  `skip_count` int NOT NULL DEFAULT 0 COMMENT '跳过数（幂等已存在）',
  `fail_count` int NOT NULL DEFAULT 0 COMMENT '失败数（仅模板级）',
  `personal_bytes` bigint NOT NULL DEFAULT 0 COMMENT '写入个人空间字节数',
  `enterprise_bytes` bigint NOT NULL DEFAULT 0 COMMENT '写入企业空间字节数',
  `error_message` varchar(500) NULL DEFAULT NULL COMMENT '任务级失败原因（含来源级失败摘要；不含密码）',
  `operator_id` bigint NULL DEFAULT NULL COMMENT '后台操作人 system_users.id',
  `operator_name` varchar(64) NULL DEFAULT '' COMMENT '后台操作人昵称快照',
  `started_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `finished_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `heartbeat_time` datetime NULL DEFAULT NULL COMMENT '心跳时间',
  `running_org_id` bigint NULL DEFAULT NULL COMMENT '进行中企业编号；终态置 NULL',
  `running_member_id` bigint NULL DEFAULT NULL COMMENT '进行中个人会员编号；终态置 NULL',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_running_org`(`tenant_id` ASC, `running_org_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_running_member`(`tenant_id` ASC, `running_member_id` ASC) USING BTREE,
  INDEX `idx_org_id`(`tenant_id` ASC, `org_id` ASC, `id` DESC) USING BTREE,
  INDEX `idx_member_id`(`tenant_id` ASC, `subject_type` ASC, `owner_member_id` ASC, `id` DESC) USING BTREE,
  INDEX `idx_status`(`tenant_id` ASC, `status` ASC, `update_time` ASC) USING BTREE
) COMMENT = 'MaxLabel LabelShop 导入任务';

-- ---------------------------------------------------------------------------
-- 2. 导入明细
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_labelshop_import_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `task_id` bigint NOT NULL COMMENT '任务编号',
  `source_library` tinyint NOT NULL COMMENT '来源库：12 我的模板库 13 组模板库 1 企业/组织模板',
  `source_ref` varchar(64) NOT NULL COMMENT 'LabelShop 模板 UUID',
  `source_category` varchar(255) NOT NULL DEFAULT '' COMMENT '来源分类路径',
  `source_name` varchar(255) NOT NULL DEFAULT '' COMMENT '来源模板名称（原始）',
  `target_scope` tinyint NOT NULL COMMENT '目标空间：1 个人 2 企业',
  `target_owner_id` bigint NOT NULL COMMENT '目标归属：个人为 OWNER 会员编号，企业为 org_id',
  `content_id` bigint NULL DEFAULT NULL COMMENT '写入的 maxlabel_share_content.id',
  `format` varchar(16) NOT NULL DEFAULT 'lsdx' COMMENT '格式：lsdx / cloud-json',
  `editable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否可编辑：lsdx=1，企业私有 JSON=0',
  `size_bytes` bigint NOT NULL DEFAULT 0 COMMENT '写入字节数',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 待处理 1 成功 2 跳过 3 失败',
  `error_message` varchar(500) NULL DEFAULT NULL COMMENT '失败原因（不含密码）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_source`(`tenant_id` ASC, `task_id` ASC, `source_ref` ASC) USING BTREE,
  INDEX `idx_target_source`(`tenant_id` ASC, `target_scope` ASC, `target_owner_id` ASC, `source_ref` ASC) USING BTREE,
  INDEX `idx_task_status`(`tenant_id` ASC, `task_id` ASC, `status` ASC, `id` ASC) USING BTREE,
  INDEX `idx_task_library`(`tenant_id` ASC, `task_id` ASC, `source_library` ASC) USING BTREE
) COMMENT = 'MaxLabel LabelShop 导入明细';

-- ---------------------------------------------------------------------------
-- 3. 菜单 / 权限
-- ---------------------------------------------------------------------------
SET @org_page_id = (
  SELECT `id` FROM `system_menu`
   WHERE `permission` = 'member:org:query' AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '导入历史模板', 'member:org:labelshop-import', 3, 10, @org_page_id,
       '', '', NULL, NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE @org_page_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT `id` FROM `system_menu`
                    WHERE `permission` = 'member:org:labelshop-import') AS existed
  );

SET @labelshop_menu_id = (
  SELECT `id` FROM `system_menu`
   WHERE `permission` = 'member:org:labelshop-import' AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

-- 跟随「企业管理」页面已授权的角色；后续新角色由管理员在后台自行分配
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT parent_acl.`role_id`, @labelshop_menu_id, 'deploy', NOW(), 'deploy', NOW(), b'0',
       parent_acl.`tenant_id`
FROM (
  SELECT `role_id`, `tenant_id`
    FROM `system_role_menu`
   WHERE `menu_id` = @org_page_id AND `deleted` = b'0'
) AS parent_acl
LEFT JOIN `system_role_menu` AS existed
  ON existed.`role_id` = parent_acl.`role_id`
 AND existed.`menu_id` = @labelshop_menu_id
 AND existed.`deleted` = b'0'
WHERE @labelshop_menu_id IS NOT NULL
  AND existed.`id` IS NULL;

-- ---------------------------------------------------------------------------
-- 3.2 个人会员代导按钮（挂在「会员管理 → 会员用户」页面下）
-- ---------------------------------------------------------------------------
SET @member_page_id = (
  SELECT `parent_id` FROM `system_menu`
   WHERE `permission` = 'member:user:query' AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '导入历史模板', 'member:user:labelshop-import', 3, 10, @member_page_id,
       '', '', NULL, NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE @member_page_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM (SELECT `id` FROM `system_menu`
                    WHERE `permission` = 'member:user:labelshop-import') AS existed
  );

SET @member_labelshop_menu_id = (
  SELECT `id` FROM `system_menu`
   WHERE `permission` = 'member:user:labelshop-import' AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

-- 跟随「会员用户」页面已授权的角色；后续新角色由管理员在后台自行分配
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT parent_acl.`role_id`, @member_labelshop_menu_id, 'deploy', NOW(), 'deploy', NOW(), b'0',
       parent_acl.`tenant_id`
FROM (
  SELECT `role_id`, `tenant_id`
    FROM `system_role_menu`
   WHERE `menu_id` = @member_page_id AND `deleted` = b'0'
) AS parent_acl
LEFT JOIN `system_role_menu` AS existed
  ON existed.`role_id` = parent_acl.`role_id`
 AND existed.`menu_id` = @member_labelshop_menu_id
 AND existed.`deleted` = b'0'
WHERE @member_labelshop_menu_id IS NOT NULL
  AND existed.`id` IS NULL;
