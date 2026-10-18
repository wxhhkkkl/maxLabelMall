-- ============================================================================
-- 013（008 对公转账）—— 把「确认收款」的权限点登记进 system_menu
--
-- ⚠️ 本项目自建脚本，**首次上线执行一次**；下面写了幂等保护（重复执行不会插出第二份）。
--
-- 为什么必须有这一步（008 / FR-203）
-- ----------------------------------------------------------------------------
--   管理端的「确认收款」按钮用 `v-hasPermi="['trade:order:confirm-paid']"`，
--   而那个指令只看一件事：**用户权限集合里有没有这个字符串** —— 它来自
--   `/system/auth/get-permission-info`，等于「该用户全部菜单上的 `permission`」。
--
--   ⚠️ **超管并不会拿到 `*:*:*`**：它的权限集合 = 菜单表里出现过的所有 permission 字符串。
--   所以一个权限串**没登记成菜单** → 不在集合里 → **按钮被指令从 DOM 里删掉，超管也一样**；
--   而后端 `@PreAuthorize` 对 super_admin 有旁路 —— 表现为「接口能调、按钮不显示」。
--   排查时很容易往"没发版 / 浏览器缓存"上想。（004 踩过一次，008 的 T484 也标了这条。
--   ⚠️ **这一条只能手工验**，自动化测试看不见它。）
--
--   为什么是**独立权限点**（而不是复用 `trade:order:update`）：确认收款是**资金动作**，
--   不是所有后台角色都该能做（FR-203）。将来收窄权限时，在「角色管理」里不给某个角色
--   勾它即可，不必改代码。
--
-- 挂在哪
-- ----------------------------------------------------------------------------
--   挂在「商城 → 订单中心 → 订单列表」下（与「订单查询」这个按钮同级），
--   因为它作用于**订单列表 / 订单详情**两处：
--       SELECT id, parent_id FROM system_menu WHERE permission = 'trade:order:query';
--       -- 581 = 「订单查询」按钮，它的 parent_id = 270 = 「订单列表」
--   ⚠️ 这里**用子查询取 parent_id，而不是写死 270** —— 008 的菜单脚本（008-order-invoice-menus.sql）
--      写死了 266，那一行就带了一句"换环境前先核对"。子查询没有这个问题。
--
-- 回滚
-- ----------------------------------------------------------------------------
--   DELETE FROM `system_menu` WHERE `permission` = 'trade:order:confirm-paid';
--
--   **临时隐藏**（代码还没发版、但菜单先上去了的时候）：
--   UPDATE `system_menu` SET `status` = 1 WHERE `permission` = 'trade:order:confirm-paid';
--   （`status` 1 = 停用；发版后改回 0）⚠️ 注意：停用同样会让**超管看不到那个按钮**。
--
-- 执行记录
-- ----------------------------------------------------------------------------
--   ⏳ **尚未在生产库执行**（2026-10-16 建文件时）—— 与 012 一起，等所有者放行。
--   执行后请在这里补一行回查结果：
--       SELECT id, name, permission, type, parent_id FROM system_menu
--        WHERE permission = 'trade:order:confirm-paid';
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 按钮（type=3）：确认收款
--
--    ⚠️ 后端必须真的校验它：`TradeOrderController#confirmOfflinePaid` 上挂着
--       `@PreAuthorize("@ss.hasPermission('trade:order:confirm-paid')")` ——
--       少了登记这边，是"按钮不显示"；少了注解那边，是"谁都能确认收款"。
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '确认对公转账收款', 'trade:order:confirm-paid', 3, 10,
       (SELECT `id` FROM (
            SELECT `id` FROM `system_menu` WHERE `permission` = 'trade:order:query'
        ) AS queryButton),
       '', '', '', NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    -- 包一层派生表：MySQL 不允许在子查询里直接引用 INSERT 的目标表
    SELECT 1 FROM (
        SELECT `id` FROM `system_menu` WHERE `permission` = 'trade:order:confirm-paid'
    ) AS existed
);

-- ---------------------------------------------------------------------------
-- 自检：应当恰好 1 行，且 parent_id 等于「订单查询」那条按钮的 parent_id
--
--   SELECT id, name, permission, type, parent_id
--     FROM `system_menu` WHERE `permission` = 'trade:order:confirm-paid';
--
-- 再验一次"超管看得到按钮"（这才是这一步的目的）：
--   用超管登录管理端 → 刷新 → 打开**一笔待收款的对公单**（订单列表里带「待收款」标签的），
--   操作区应当出现「**确认收款**」按钮。
--   📌 顺带核对：普通订单**不该**出现这个按钮（后端也会拒，见 FR-207）。
-- ---------------------------------------------------------------------------
