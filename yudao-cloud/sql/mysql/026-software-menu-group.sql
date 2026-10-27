-- ============================================================================
-- 026 —— 把软件相关的菜单收拢到「软件管理」主菜单
--
-- 背景（2026-10-27 所有者要求）：
--   公共性模板审核、会员授权商品、企业管理 原本挂在「会员中心」下；
--   软件分发、软件分类 原本挂在「MALL 商城系统」下。
--   现在统一归到一个顶层目录「软件管理」。
--
-- 执行：python scripts/apply_003_filter_dimension.py --file 026-software-menu-group.sql
--
-- 回查：
--   SELECT m.id, m.name, m.path, p.name AS parent
--     FROM system_menu m LEFT JOIN system_menu p ON p.id = m.parent_id
--    WHERE m.deleted = 0 AND m.component IN
--      ('member/template/review/index','member/subscription/plan/index','member/org/index',
--       'mall/software/info/index','mall/software/category/index');
--   期望 5 行的 parent 都是「软件管理」。
--
-- 回滚：把这 5 行的 parent_id 改回 373（会员中心）/ 449（MALL 商城系统），path 还原。
--       顶层目录「软件管理」可直接删（先清空其子项）。
-- ============================================================================

-- 1. 顶层目录：软件管理
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '软件管理', '', 1, 30, 0, '/software', 'ep:box', NULL,
       NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `path` = '/software' AND `parent_id` = 0 AND `deleted` = b'0') AS existed
);

SET @software_group_id = (
  SELECT `id` FROM `system_menu`
   WHERE `path` = '/software' AND `parent_id` = 0 AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

-- 2. 把 5 个页面挂到新目录下，并改成相对路径
UPDATE `system_menu` SET `parent_id` = @software_group_id, `path` = 'template-review', `sort` = 10
 WHERE `deleted` = b'0' AND `component` = 'member/template/review/index';

UPDATE `system_menu` SET `parent_id` = @software_group_id, `path` = 'subscription-plan', `sort` = 20
 WHERE `deleted` = b'0' AND `component` = 'member/subscription/plan/index';

UPDATE `system_menu` SET `parent_id` = @software_group_id, `path` = 'org', `sort` = 30
 WHERE `deleted` = b'0' AND `component` = 'member/org/index';

UPDATE `system_menu` SET `parent_id` = @software_group_id, `path` = 'dispatch', `sort` = 40
 WHERE `deleted` = b'0' AND `component` = 'mall/software/info/index';

UPDATE `system_menu` SET `parent_id` = @software_group_id, `path` = 'category', `sort` = 50
 WHERE `deleted` = b'0' AND `component` = 'mall/software/category/index';
