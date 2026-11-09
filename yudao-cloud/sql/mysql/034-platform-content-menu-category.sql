-- ============================================================================
-- 034（016 增补）—— 平台共享内容菜单归入「软件管理」+ 分类管理
--
-- 内容：
--   1. 把「平台共享内容」页面从会员中心移动到「软件管理」目录（/software）
--   2. 新增 `maxlabel_platform_content_category` 分类表
--   3. 用平台内容已有的 distinct category 初始化分类
--
-- 幂等：UPDATE 只改 parent_id/sort；CREATE TABLE IF NOT EXISTS；
--       初始化分类按 (tenant_id, name) 判重。
--
-- 回查：
--   SELECT id, name, parent_id, path FROM system_menu
--    WHERE permission = 'member:platform-content:query';
--   SELECT * FROM maxlabel_platform_content_category ORDER BY sort, id;
--
-- 回滚：
--   DROP TABLE IF EXISTS maxlabel_platform_content_category;
--   UPDATE system_menu SET parent_id = 373, sort = 92
--    WHERE permission = 'member:platform-content:query';
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 移动菜单到「软件管理」
-- ---------------------------------------------------------------------------
SET @software_menu_id = (
  SELECT `id` FROM `system_menu`
   WHERE `path` = '/software' AND `parent_id` = 0 AND `deleted` + 0 = 0
   ORDER BY `id` LIMIT 1
);

UPDATE `system_menu`
   SET `parent_id` = @software_menu_id,
       `sort` = 95,
       `update_time` = NOW()
 WHERE `permission` = 'member:platform-content:query'
   AND `deleted` + 0 = 0
   AND @software_menu_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. 平台共享内容分类
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `maxlabel_platform_content_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(64) NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_sort`(`tenant_id` ASC, `sort` ASC, `id` ASC) USING BTREE
) COMMENT = 'MaxLabel 平台共享内容分类';

-- ---------------------------------------------------------------------------
-- 3. 用已有平台内容分类初始化
-- ---------------------------------------------------------------------------
INSERT INTO `maxlabel_platform_content_category`
  (`name`, `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT p.`category`, 0, 'system', NOW(), 'system', NOW(), b'0', p.`tenant_id`
FROM `maxlabel_platform_content` p
WHERE p.`category` IS NOT NULL AND p.`category` <> '' AND p.`deleted` + 0 = 0
GROUP BY p.`tenant_id`, p.`category`
HAVING NOT EXISTS (
  SELECT 1 FROM `maxlabel_platform_content_category` c
   WHERE c.`tenant_id` = p.`tenant_id` AND c.`name` = p.`category` AND c.`deleted` + 0 = 0
);
