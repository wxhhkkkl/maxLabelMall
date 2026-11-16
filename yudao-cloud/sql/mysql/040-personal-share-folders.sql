-- ============================================================================
-- 040 —— 016 个人空间嵌套文件夹（2026-11-16）
--
-- 背景：018 导入需要把「我的模板库」按来源分类路径落到会员个人空间的多层文件夹；
--       同时个人空间支持会员自行新建 / 改名 / 移动 / 删除文件夹。
--
-- 变更：maxlabel_share_folder
--   · scope            1 个人 2 企业（存量企业文件夹默认 2）
--   · owner_member_id  个人文件夹归属会员；企业文件夹为 NULL
--   · parent_id        父文件夹；NULL = 根目录（企业文件夹保持单层）
--   · org_id           放宽为可空（个人文件夹为 NULL）
--   · idx_personal     个人空间按 owner/parent 查树
--
-- 幂等：INFORMATION_SCHEMA 守卫；重复执行安全。
-- 上线顺序：本脚本 → 后端 → 桌面端 / 管理端。
-- 回滚：先确认没有个人文件夹与子文件夹，再删除新增列/索引（见文件末尾）。
-- ============================================================================

-- 1. scope
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_share_folder' AND COLUMN_NAME = 'scope');
SET @s := IF(@c = 0,
  'ALTER TABLE `maxlabel_share_folder` ADD COLUMN `scope` tinyint NOT NULL DEFAULT 2 COMMENT ''空间类型：1 个人 2 企业'' AFTER `id`',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2. owner_member_id
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_share_folder' AND COLUMN_NAME = 'owner_member_id');
SET @s := IF(@c = 0,
  'ALTER TABLE `maxlabel_share_folder` ADD COLUMN `owner_member_id` bigint NULL DEFAULT NULL COMMENT ''个人文件夹归属会员编号；企业文件夹为 NULL'' AFTER `scope`',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3. parent_id
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_share_folder' AND COLUMN_NAME = 'parent_id');
SET @s := IF(@c = 0,
  'ALTER TABLE `maxlabel_share_folder` ADD COLUMN `parent_id` bigint NULL DEFAULT NULL COMMENT ''父文件夹编号；NULL = 根目录'' AFTER `owner_member_id`',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 4. org_id 放宽为可空
SET @c := (SELECT IS_NULLABLE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_share_folder' AND COLUMN_NAME = 'org_id');
SET @s := IF(@c = 'NO',
  'ALTER TABLE `maxlabel_share_folder` MODIFY COLUMN `org_id` bigint NULL DEFAULT NULL COMMENT ''企业编号，关联 maxlabel_org.id；个人文件夹为 NULL''',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 5. 个人空间树索引
SET @c := (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'maxlabel_share_folder' AND INDEX_NAME = 'idx_personal');
SET @s := IF(@c = 0,
  'ALTER TABLE `maxlabel_share_folder` ADD INDEX `idx_personal`(`tenant_id` ASC, `scope` ASC, `owner_member_id` ASC, `parent_id` ASC, `deleted` ASC) USING BTREE',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 6. 存量行显式回填 scope=2（新列默认值已覆盖，双保险）
UPDATE `maxlabel_share_folder` SET `scope` = 2 WHERE `scope` IS NULL;

-- ---------------------------------------------------------------------------
-- 回滚（仅在确认没有个人文件夹/子文件夹后执行）：
--   ALTER TABLE `maxlabel_share_folder` DROP INDEX `idx_personal`;
--   ALTER TABLE `maxlabel_share_folder` DROP COLUMN `parent_id`;
--   ALTER TABLE `maxlabel_share_folder` DROP COLUMN `owner_member_id`;
--   ALTER TABLE `maxlabel_share_folder` DROP COLUMN `scope`;
--   -- org_id 回 NOT NULL 前先确认没有个人文件夹
-- ---------------------------------------------------------------------------
