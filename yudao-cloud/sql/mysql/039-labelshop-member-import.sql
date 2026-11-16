-- ============================================================================
-- 039 —— 018 个人会员代导的幂等升级（适用于已执行过旧版 038 的库）
--
-- 背景（2026-11-13）：
--   018 的个人会员代导给 `maxlabel_labelshop_import_task` 增加了
--   `subject_type` / `running_member_id`，并把 `org_id` / `folder_name` 放宽为可空，
--   同时新增按钮权限 `member:user:labelshop-import`。
--   038 使用 CREATE TABLE IF NOT EXISTS，表已存在时不会补列，因此需要本脚本。
--
-- 幂等：INFORMATION_SCHEMA 判断列 / 索引是否存在；菜单 WHERE NOT EXISTS。
-- 全新库：先执行 038（已含全部结构），再执行本脚本也无副作用。
-- 回滚：本脚本只补结构，不删除数据；如需回滚参见 038 的说明。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. subject_type
-- ---------------------------------------------------------------------------
SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_labelshop_import_task'
    AND COLUMN_NAME = 'subject_type');
SET @s1 := IF(@c1 = 0,
  'ALTER TABLE `maxlabel_labelshop_import_task` ADD COLUMN `subject_type` tinyint NOT NULL DEFAULT 1 COMMENT ''导入对象：1 企业 2 个人会员'' AFTER `id`',
  'SELECT 1');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

-- ---------------------------------------------------------------------------
-- 2. org_id / folder_name 放宽为可空
-- ---------------------------------------------------------------------------
SET @c2 := (SELECT IS_NULLABLE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_labelshop_import_task'
    AND COLUMN_NAME = 'org_id');
SET @s2 := IF(@c2 = 'NO',
  'ALTER TABLE `maxlabel_labelshop_import_task` MODIFY COLUMN `org_id` bigint NULL DEFAULT NULL COMMENT ''企业编号，关联 maxlabel_org.id；个人会员任务为 NULL''',
  'SELECT 1');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

SET @c3 := (SELECT IS_NULLABLE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_labelshop_import_task'
    AND COLUMN_NAME = 'folder_name');
SET @s3 := IF(@c3 = 'NO',
  'ALTER TABLE `maxlabel_labelshop_import_task` MODIFY COLUMN `folder_name` varchar(64) NULL DEFAULT NULL COMMENT ''计划新建的文件夹名称（<时间>导入）；个人任务为 NULL''',
  'SELECT 1');
PREPARE st3 FROM @s3; EXECUTE st3; DEALLOCATE PREPARE st3;

-- ---------------------------------------------------------------------------
-- 3. running_member_id
-- ---------------------------------------------------------------------------
SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_labelshop_import_task'
    AND COLUMN_NAME = 'running_member_id');
SET @s4 := IF(@c4 = 0,
  'ALTER TABLE `maxlabel_labelshop_import_task` ADD COLUMN `running_member_id` bigint NULL DEFAULT NULL COMMENT ''进行中个人会员编号；终态置 NULL'' AFTER `running_org_id`',
  'SELECT 1');
PREPARE st4 FROM @s4; EXECUTE st4; DEALLOCATE PREPARE st4;

-- ---------------------------------------------------------------------------
-- 4. 索引 uk_running_member / idx_member_id
-- ---------------------------------------------------------------------------
SET @c5 := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_labelshop_import_task'
    AND INDEX_NAME = 'uk_running_member');
SET @s5 := IF(@c5 = 0,
  'ALTER TABLE `maxlabel_labelshop_import_task` ADD UNIQUE INDEX `uk_running_member`(`tenant_id` ASC, `running_member_id` ASC) USING BTREE',
  'SELECT 1');
PREPARE st5 FROM @s5; EXECUTE st5; DEALLOCATE PREPARE st5;

SET @c6 := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_labelshop_import_task'
    AND INDEX_NAME = 'idx_member_id');
SET @s6 := IF(@c6 = 0,
  'ALTER TABLE `maxlabel_labelshop_import_task` ADD INDEX `idx_member_id`(`tenant_id` ASC, `subject_type` ASC, `owner_member_id` ASC, `id` DESC) USING BTREE',
  'SELECT 1');
PREPARE st6 FROM @s6; EXECUTE st6; DEALLOCATE PREPARE st6;

-- ---------------------------------------------------------------------------
-- 5. 按钮权限 member:user:labelshop-import（挂在「会员用户」页面下）
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
