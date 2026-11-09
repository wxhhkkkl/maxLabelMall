-- ============================================================================
-- 033（016 共享内容空间：个人 / 企业 / 平台）
--
-- 内容：
--   1. 5 张新表：空间配额锁、企业文件夹、文件夹可见成员、个人/企业内容、平台内容
--   2. 两张旧表的数据迁移（保留原 id）：
--        maxlabel_label_template      -> maxlabel_share_content（scope=1, contentType=1）
--        maxlabel_shared_template     -> maxlabel_platform_content（仅 audit_status=1）
--   3. 个人空间用量回填 maxlabel_share_space
--   4. 平台内容菜单 / 权限；软删旧「共享模板审核」菜单
--
-- 幂等：CREATE TABLE IF NOT EXISTS；迁移 INSERT ... SELECT ... WHERE NOT EXISTS；
--       菜单 WHERE NOT EXISTS；旧菜单 UPDATE 只动 deleted=0 的行。
-- 旧表不删、不修改，作为回滚与对账依据。
--
-- 回查：
--   SHOW TABLES LIKE 'maxlabel_share%';
--   SELECT COUNT(*) FROM maxlabel_share_content;
--   SELECT COUNT(*) FROM maxlabel_platform_content;
--   SELECT scope, owner_id, used_bytes FROM maxlabel_share_space ORDER BY scope, owner_id;
--   SELECT id, name, permission FROM system_menu WHERE permission LIKE 'member:platform-content-%';
--
-- 回滚（按依赖顺序）：
--   DELETE FROM system_menu WHERE permission LIKE 'member:platform-content-%';
--   UPDATE system_menu SET deleted = b'0'
--    WHERE permission IN ('member:shared-template:query','member:shared-template:audit','member:shared-template:offline');
--   DROP TABLE IF EXISTS maxlabel_share_folder_member;
--   DROP TABLE IF EXISTS maxlabel_share_folder;
--   DROP TABLE IF EXISTS maxlabel_share_content;
--   DROP TABLE IF EXISTS maxlabel_share_space;
--   DROP TABLE IF EXISTS maxlabel_platform_content;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 空间配额锁（个人 / 企业各一行，惰性创建）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_share_space` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `scope` tinyint NOT NULL COMMENT '空间类型：1 个人 2 企业',
  `owner_id` bigint NOT NULL COMMENT '归属编号：个人为 member_user.id，企业为 maxlabel_org.id',
  `used_bytes` bigint NOT NULL DEFAULT 0 COMMENT '已用字节数（锁内按内容表重算后回写）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_scope_owner`(`tenant_id` ASC, `scope` ASC, `owner_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 共享空间配额锁';

-- ---------------------------------------------------------------------------
-- 2. 企业文件夹（单层，无根目录内容）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_share_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '企业编号，关联 maxlabel_org.id',
  `name` varchar(64) NOT NULL COMMENT '文件夹名称',
  `creator_member_id` bigint NOT NULL COMMENT '创建的管理员会员编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_org`(`tenant_id` ASC, `org_id` ASC, `deleted` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业共享文件夹';

-- ---------------------------------------------------------------------------
-- 3. 文件夹可见成员（名单；物理删除，避免重新加入继承旧权限）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_share_folder_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `folder_id` bigint NOT NULL COMMENT '文件夹编号，关联 maxlabel_share_folder.id',
  `member_id` bigint NOT NULL COMMENT '可见会员编号，关联 member_user.id',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（名单变更时物理删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_folder_member`(`tenant_id` ASC, `folder_id` ASC, `member_id` ASC) USING BTREE,
  INDEX `idx_member`(`tenant_id` ASC, `member_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业文件夹可见成员';

-- ---------------------------------------------------------------------------
-- 4. 个人 / 企业内容项
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_share_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `scope` tinyint NOT NULL COMMENT '空间类型：1 个人 2 企业',
  `owner_member_id` bigint NOT NULL COMMENT '上传者 / 个人空间归属会员编号',
  `org_id` bigint NULL DEFAULT NULL COMMENT '企业编号；个人空间为 NULL',
  `folder_id` bigint NULL DEFAULT NULL COMMENT '文件夹编号；企业空间必填，个人空间为 NULL',
  `content_type` tinyint NOT NULL COMMENT '内容类型：1 模板 2 txt 3 excel 4 csv 5 图片',
  `name` varchar(255) NOT NULL COMMENT '名称（含扩展名）',
  `storage_type` tinyint NOT NULL DEFAULT 1 COMMENT '存储方式：1 文本正文 2 文件地址',
  `content` longtext NULL COMMENT '文本正文（模板 / txt / csv）',
  `file_url` varchar(512) NULL DEFAULT NULL COMMENT '对象存储访问地址（excel / 图片）',
  `mime_type` varchar(128) NULL DEFAULT '' COMMENT 'MIME 类型',
  `size_bytes` bigint NOT NULL COMMENT '字节大小（计入配额）',
  `content_hash` char(64) NULL DEFAULT NULL COMMENT '文本正文 SHA-256',
  `width_mm` decimal(10,2) NULL DEFAULT 0 COMMENT '模板宽度（毫米）',
  `height_mm` decimal(10,2) NULL DEFAULT 0 COMMENT '模板高度（毫米）',
  `category` varchar(64) NULL DEFAULT '' COMMENT '分类',
  `keywords` varchar(255) NULL DEFAULT '' COMMENT '关键词',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_personal`(`tenant_id` ASC, `scope` ASC, `owner_member_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_enterprise`(`tenant_id` ASC, `org_id` ASC, `folder_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_type`(`tenant_id` ASC, `scope` ASC, `content_type` ASC, `deleted` ASC) USING BTREE
) COMMENT = 'MaxLabel 个人/企业共享内容';

-- ---------------------------------------------------------------------------
-- 5. 平台共享内容
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_platform_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `content_type` tinyint NOT NULL COMMENT '内容类型：1 模板 2 txt 3 excel 4 csv 5 图片',
  `name` varchar(255) NOT NULL COMMENT '名称（含扩展名）',
  `storage_type` tinyint NOT NULL DEFAULT 1 COMMENT '存储方式：1 文本正文 2 文件地址',
  `content` longtext NULL COMMENT '文本正文（模板 / txt / csv，模板已清洗）',
  `file_url` varchar(512) NULL DEFAULT NULL COMMENT '对象存储访问地址（excel / 图片）',
  `mime_type` varchar(128) NULL DEFAULT '' COMMENT 'MIME 类型',
  `size_bytes` bigint NOT NULL COMMENT '字节大小',
  `content_hash` char(64) NULL DEFAULT NULL COMMENT '文本正文 SHA-256',
  `width_mm` decimal(10,2) NULL DEFAULT 0 COMMENT '模板宽度（毫米）',
  `height_mm` decimal(10,2) NULL DEFAULT 0 COMMENT '模板高度（毫米）',
  `category` varchar(64) NULL DEFAULT '' COMMENT '分类',
  `keywords` varchar(255) NULL DEFAULT '' COMMENT '关键词',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1 上架 0 下架',
  `publisher_user_id` bigint NULL DEFAULT NULL COMMENT '发布管理员编号（system_users.id）',
  `published_time` datetime NULL DEFAULT NULL COMMENT '上架时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status_type`(`tenant_id` ASC, `status` ASC, `content_type` ASC, `id` DESC) USING BTREE,
  INDEX `idx_category`(`tenant_id` ASC, `status` ASC, `category` ASC) USING BTREE
) COMMENT = 'MaxLabel 平台共享内容';

-- ---------------------------------------------------------------------------
-- 6. 存量迁移：私有云端模板 -> 个人内容（保留原 id）
-- ---------------------------------------------------------------------------
INSERT INTO `maxlabel_share_content`
  (`id`, `scope`, `owner_member_id`, `org_id`, `folder_id`, `content_type`, `name`,
   `storage_type`, `content`, `file_url`, `mime_type`, `size_bytes`, `content_hash`,
   `width_mm`, `height_mm`, `category`, `keywords`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT t.`id`, 1, t.`member_id`, NULL, NULL, 1, t.`name`,
       1, t.`content`, NULL, '', t.`size_bytes`, t.`content_hash`,
       t.`width_mm`, t.`height_mm`, t.`category`, t.`keywords`,
       t.`creator`, t.`create_time`, t.`updater`, t.`update_time`, t.`deleted`, t.`tenant_id`
FROM `maxlabel_label_template` t
WHERE NOT EXISTS (SELECT 1 FROM `maxlabel_share_content` c WHERE c.`id` = t.`id`);

-- ---------------------------------------------------------------------------
-- 7. 存量迁移：已审通过的平台共享模板 -> 平台内容（保留原 id）
-- ---------------------------------------------------------------------------
INSERT INTO `maxlabel_platform_content`
  (`id`, `content_type`, `name`, `storage_type`, `content`, `file_url`, `mime_type`,
   `size_bytes`, `content_hash`, `width_mm`, `height_mm`, `category`, `keywords`,
   `status`, `publisher_user_id`, `published_time`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, 1, s.`name`, 1, s.`content`, NULL, '',
       s.`size_bytes`, NULL, s.`width_mm`, s.`height_mm`, s.`category`, s.`keywords`,
       1, s.`audit_user_id`, s.`published_time`,
       s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM `maxlabel_shared_template` s
WHERE s.`audit_status` = 1 AND s.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `maxlabel_platform_content` p WHERE p.`id` = s.`id`);

-- ---------------------------------------------------------------------------
-- 8. 个人空间用量回填（迁移后执行一次；重复执行按最新合计覆盖）
-- ---------------------------------------------------------------------------
INSERT INTO `maxlabel_share_space`
  (`scope`, `owner_id`, `used_bytes`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 1, c.`owner_member_id`, SUM(c.`size_bytes`), 'system', NOW(), 'system', NOW(), b'0', c.`tenant_id`
FROM `maxlabel_share_content` c
WHERE c.`scope` = 1 AND c.`deleted` = b'0'
GROUP BY c.`tenant_id`, c.`owner_member_id`
ON DUPLICATE KEY UPDATE `used_bytes` = VALUES(`used_bytes`), `update_time` = NOW();

-- ---------------------------------------------------------------------------
-- 9. 菜单 / 权限
-- ---------------------------------------------------------------------------
SET @member_center_id = (
  SELECT `id` FROM `system_menu`
   WHERE `name` = '会员中心' AND `parent_id` = 0 AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '平台共享内容', 'member:platform-content:query', 2, 92, @member_center_id,
       'platform-content', 'ep:collection', 'member/platformContent/index',
       'MemberPlatformContent', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'member:platform-content:query') AS existed
);

SET @platform_page_id = (
  SELECT `id` FROM `system_menu` WHERE `permission` = 'member:platform-content:query' LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT t.name, t.permission, 3, t.sort, @platform_page_id,
       '', '', NULL, NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM (
  SELECT '上传平台内容' AS name, 'member:platform-content:create' AS permission, 1 AS sort UNION ALL
  SELECT '编辑/上下架平台内容', 'member:platform-content:update', 2 UNION ALL
  SELECT '删除平台内容', 'member:platform-content:delete', 3
) t
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = t.permission) AS existed
);

-- 旧「共享模板审核」页与按钮退役（历史数据已迁移到平台内容表）
UPDATE `system_menu`
   SET `deleted` = b'1', `update_time` = NOW()
 WHERE `deleted` = b'0'
   AND `permission` IN ('member:shared-template:query', 'member:shared-template:audit', 'member:shared-template:offline');
