-- ============================================================================
-- 004 —— 把「筛选维度」的 4 个权限点登记成按钮型菜单
--
-- ⚠️ 本文件是**本项目自建**的迁移脚本，不是官方资产。只在**首次上线时执行一次**；
--    下面写了幂等保护，重复执行不会插出第二份。
--
-- 为什么必须有这一步（2026-10-11 上线后实测发现，**超管也看不到按钮**）
-- ---------------------------------------------------------------------------
-- 管理端按钮用的是 `v-hasPermi="['product:filter-dimension:query']"`。
-- 前端那个指令只看一件事：
--     userStore.permissions.has('*:*:*') || permission.some(p => userStore.permissions.has(p))
-- 而这个 `permissions` 集合，是 `/system/auth/get-permission-info` 拼出来的 ——
-- 它 = **该用户全部菜单上的 `permission` 字符串**（`AuthConvert` 里就是
-- `convertSet(menuList, MenuDO::getPermission)`）。
--
-- **超管并不会拿到 `*:*:*`**：`PermissionServiceImpl.getRoleMenuListByRoleId` 对超管返回的是
-- "全部菜单"，于是它的权限集合 = 菜单表里出现过的所有 permission 字符串。
-- `product:filter-dimension:*` 不在任何菜单里 → 不在集合里 → **按钮被指令删掉，超管也一样**。
--
-- 更要命的是**后端是放行的**：`@PreAuthorize("@ss.hasPermission(...)")` 对 super_admin 有旁路。
-- 于是表现为「接口能调、按钮不显示」，排查时很容易往"没发版 / 浏览器缓存"上想。
--
-- 为什么用 SQL 而不是让运营去「系统管理 → 菜单管理」点四下
-- ---------------------------------------------------------------------------
-- 两种都行。SQL 的好处是**可重复、可审查、换环境照跑**；菜单管理页适合一次性微调。
-- 这里选 SQL，并顺手把 4 条挂在「商品分类」(id=210) 下面 —— 因为维度配置本来就住在那一页。
--
-- 顺带的效果：受限角色以后可以在这 4 个权限点上单独授权（角色管理里勾选），
-- 不必为了用一个筛选配置而开超管。
--
-- 回滚：DELETE FROM `system_menu`
--       WHERE `permission` IN ('product:filter-dimension:query','product:filter-dimension:create',
--                              'product:filter-dimension:update','product:filter-dimension:delete');
-- ============================================================================

INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT t.name, t.permission, 3, t.sort, 210, '', '', '', NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM (
    SELECT '筛选维度查询' AS name, 'product:filter-dimension:query'  AS permission, 5 AS sort
    UNION ALL SELECT '筛选维度创建', 'product:filter-dimension:create', 6
    UNION ALL SELECT '筛选维度更新', 'product:filter-dimension:update', 7
    UNION ALL SELECT '筛选维度删除', 'product:filter-dimension:delete', 8
) AS t
WHERE NOT EXISTS (
    -- 包一层派生表：MySQL 不允许在子查询里直接引用 INSERT 的目标表
    SELECT 1 FROM (
        SELECT `id` FROM `system_menu` WHERE `permission` = t.permission
    ) AS existed
);

-- 自检：应当恰好 4 行
-- SELECT id, name, permission, parent_id, type FROM `system_menu`
--  WHERE `permission` LIKE 'product:filter-dimension:%' ORDER BY sort;
