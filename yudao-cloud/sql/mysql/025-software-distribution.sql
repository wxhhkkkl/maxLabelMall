-- ============================================================================
-- 025（013 软件与驱动分发）
--
-- 内容：3 张新表 + 12 个软件分发权限点与「软件分发」菜单。
-- 幂等：表用 IF NOT EXISTS；菜单用 WHERE NOT EXISTS。
--
-- 执行：python scripts/apply_003_filter_dimension.py --file yudao-cloud/sql/mysql/025-software-distribution.sql
--
-- 回查：
--   SELECT table_name FROM information_schema.tables
--    WHERE table_schema = DATABASE()
--      AND table_name IN ('maxlabel_software_category','maxlabel_software','maxlabel_software_version');
--   SELECT permission FROM system_menu WHERE permission LIKE 'software:%' ORDER BY permission;
--   期望 12 条。
--   SELECT software_id, platform, arch, COUNT(*) FROM maxlabel_software_version
--    WHERE current_flag = 1 AND deleted = b'0'
--    GROUP BY software_id, platform, arch HAVING COUNT(*) > 1;   -- 期望 0 行
--
-- 回滚（按依赖顺序）：
--   DELETE FROM system_menu WHERE permission LIKE 'software:%';
--   DROP TABLE IF EXISTS maxlabel_software_version;
--   DROP TABLE IF EXISTS maxlabel_software;
--   DROP TABLE IF EXISTS maxlabel_software_category;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 软件分类
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_software_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(64) NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id` ASC, `status` ASC, `sort` ASC) USING BTREE
) COMMENT = 'MaxLabel 软件分类';

-- ---------------------------------------------------------------------------
-- 2. 软件 / 驱动
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_software` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `category_id` bigint NOT NULL COMMENT '所属分类，关联 maxlabel_software_category.id',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `introduction` varchar(1024) NULL DEFAULT '' COMMENT '介绍',
  `type` tinyint NOT NULL COMMENT '类型：1 桌面端软件 2 打印驱动',
  `show_in_support` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否在服务支持页展示',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（软删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_category`(`tenant_id` ASC, `category_id` ASC, `status` ASC) USING BTREE
) COMMENT = 'MaxLabel 软件 / 驱动';

-- ---------------------------------------------------------------------------
-- 3. 软件版本
--    uk_current：current_flag 为 NULL 表示非当前发布（MySQL 唯一索引对 NULL 不生效），
--    因此同一 (software, platform, arch) 至多一行 current_flag = 1 —— 由数据库保证唯一。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_software_version` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `software_id` bigint NOT NULL COMMENT '所属软件，关联 maxlabel_software.id',
  `version` varchar(32) NOT NULL COMMENT '版本号（语义化三段式，如 1.0.23）',
  `platform` varchar(16) NOT NULL COMMENT '平台：windows / macos / linux',
  `arch` varchar(16) NOT NULL COMMENT '架构：x64 / arm64 / x86',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  `sha512` varchar(128) NOT NULL COMMENT 'Base64 编码的 sha512',
  `file_url` varchar(512) NOT NULL COMMENT '对象存储公开永久直链',
  `object_key` varchar(512) NOT NULL COMMENT '对象键（便于运维核对/清理）',
  `release_notes` varchar(2000) NULL DEFAULT '' COMMENT '发布说明',
  `release_time` datetime NULL COMMENT '发布日期（展示用）',
  `current_flag` tinyint NULL DEFAULT NULL COMMENT '1 = 当前发布；NULL = 非当前',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0 已发布 1 草稿 2 已下架',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（软删除，文件与链接仍可用）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_version`(`tenant_id` ASC, `software_id` ASC, `platform` ASC, `arch` ASC, `version` ASC) USING BTREE,
  UNIQUE INDEX `uk_current`(`tenant_id` ASC, `software_id` ASC, `platform` ASC, `arch` ASC, `current_flag` ASC) USING BTREE,
  INDEX `idx_software`(`tenant_id` ASC, `software_id` ASC, `status` ASC) USING BTREE
) COMMENT = 'MaxLabel 软件版本';

-- ---------------------------------------------------------------------------
-- 4. 菜单与权限点（页面 1 + 按钮 11 = 12）
-- ---------------------------------------------------------------------------
SET @mall_id = (
  SELECT `id` FROM `system_menu`
   WHERE `path` = '/mall' AND `parent_id` = 0 AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

-- 页面：软件分发
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '软件分发', 'software:category:query', 2, 90, @mall_id,
       'software', 'ep:download', 'mall/software/info/index',
       'MallSoftware', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'software:category:query') AS existed
);

SET @software_page_id = (SELECT `id` FROM `system_menu` WHERE `permission` = 'software:category:query' LIMIT 1);

-- 页面：软件分类（与「软件分发」同级，组件路径必须与前端文件一致）
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '软件分类', 'software:category:query', 2, 91, @mall_id,
       'software-category', 'ep:folder', 'mall/software/category/index',
       'MallSoftwareCategory', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `component` = 'mall/software/category/index') AS existed
);

-- 按钮：软件分发下的 11 个权限点
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT t.`name`, t.`permission`, 3, t.`sort`, @software_page_id, '', '', '',
       '', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM (
  SELECT '分类新增' AS `name`, 'software:category:create' AS `permission`, 1 AS `sort`
  UNION ALL SELECT '分类修改', 'software:category:update', 2
  UNION ALL SELECT '分类删除', 'software:category:delete', 3
  UNION ALL SELECT '软件查询', 'software:info:query', 4
  UNION ALL SELECT '软件新增', 'software:info:create', 5
  UNION ALL SELECT '软件修改', 'software:info:update', 6
  UNION ALL SELECT '软件删除', 'software:info:delete', 7
  UNION ALL SELECT '版本查询', 'software:version:query', 8
  UNION ALL SELECT '版本新增', 'software:version:create', 9
  UNION ALL SELECT '版本删除', 'software:version:delete', 10
  UNION ALL SELECT '版本发布/下架', 'software:version:publish', 11
) AS t
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = t.`permission`) AS existed
);
