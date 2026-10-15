-- ============================================================================
-- 008 —— 把「开票申请」菜单与它的两个权限点登记进 system_menu
--
-- ⚠️ 本项目自建脚本，**首次上线执行一次**；下面写了幂等保护（重复执行不会插出第二份）。
--    同目录其它 `*.sql` 是从外部下载的模块 SQL，重建库时整套导入；本文件是**本项目自建**的。
--
-- 为什么必须有这一步（006 / FR-167 / FR-174 / Q29）
-- ----------------------------------------------------------------------------
--   管理端按钮用的是 `v-hasPermi="['trade:order-invoice:issue']"`，而前端那个指令只看一件事：
--   用户权限集合里有没有这个**字符串** —— 它来自 `/system/auth/get-permission-info`，
--   等于「该用户全部菜单上的 `permission`」。
--
--   ⚠️ **超管并不会拿到 `*:*:*`**：它的权限集合 = 菜单表里出现过的所有 permission 字符串。
--   所以一个权限串**没登记成菜单** → 不在集合里 → **按钮被指令从 DOM 里删掉，超管也一样**。
--   而后端 `@PreAuthorize` 对 super_admin 有旁路 —— 表现为「接口能调、按钮不显示」，
--   排查时很容易往"没发版 / 浏览器缓存"上想。（004 踩过一次，见 004-dimension-menus 的开头。）
--
--   为什么还要**独立的菜单**（而不是塞进订单详情页）：运营是"拿着一批申请去开票"，
--   不是"翻到某笔订单"—— FR-167 明确要求独立菜单。
--
-- 挂在哪
-- ----------------------------------------------------------------------------
--   与「售后退款」同级，即「商城 → 订单中心」下：
--       SELECT id, name, parent_id FROM system_menu WHERE permission = 'trade:after-sale:query';
--       -- 267 = 售后退款，它的 parent_id = 266 = 订单中心
--   **换环境前先核对这一条**（下面第 1 段的 parent_id 写的是 266）。
--
-- 回滚
-- ----------------------------------------------------------------------------
--   DELETE FROM `system_menu`
--    WHERE `permission` IN ('trade:order-invoice:query', 'trade:order-invoice:issue');
--   ⚠️ 菜单行与按钮行一起删（只删按钮会留下一个点进去 404 的菜单）。
--
--   **临时隐藏**（代码还没发版、但菜单先上去了的时候）：把它停用，别删 ——
--   UPDATE `system_menu` SET `status` = 1
--    WHERE `permission` IN ('trade:order-invoice:query', 'trade:order-invoice:issue');
--   （`status` 1 = 停用；发版后改回 0 即可）
--
-- 执行记录
-- ----------------------------------------------------------------------------
--   ✅ **2026-10-15 已在生产库执行**。回查结果（与管理端"订单中心"同级的树）：
--        12740 「开票申请」 type=2 parent=266（订单中心） component=mall/trade/invoice/index
--        12741 「发票开具」 type=3 parent=12740
--      重复执行不会插出第二份（`NOT EXISTS` 幂等）。
--   ⚠️ 当时**管理端前端尚未发版** —— 此时点这个菜单会打不开（页面文件还没上）。
--      要么尽快发版，要么先按上面的 UPDATE 把它停用。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 菜单本体（type=2）：点进去就是开票申请列表
--
--    列顺序与既有菜单行一致（去掉自增 id），component 指向管理端视图文件：
--      `yudao-ui-admin-vue3/src/views/mall/trade/invoice/index.vue`
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '开票申请', 'trade:order-invoice:query', 2, 3, 266, 'invoice', 'ep:tickets',
       'mall/trade/invoice/index', 'TradeOrderInvoice', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    -- 包一层派生表：MySQL 不允许在子查询里直接引用 INSERT 的目标表
    SELECT 1 FROM (
        SELECT `id` FROM `system_menu` WHERE `permission` = 'trade:order-invoice:query'
    ) AS existed
);

-- ---------------------------------------------------------------------------
-- 2. 按钮（type=3）：「上传发票 / 标记已开票」
--
--    ⚠️ 这是**有财务效力**的动作（Q29），所以它必须是**独立权限点** ——
--    将来要收窄权限，在「角色管理」里不给某个角色勾它即可，不必改代码。
--    parent_id 用子查询取第 1 段刚插进去的菜单 id（而不是猜一个数字）。
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu`
    (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
     `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '发票开具', 'trade:order-invoice:issue', 3, 1,
       (SELECT `id` FROM (
            SELECT `id` FROM `system_menu` WHERE `permission` = 'trade:order-invoice:query'
        ) AS menu),
       '', '', '', NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM (
        SELECT `id` FROM `system_menu` WHERE `permission` = 'trade:order-invoice:issue'
    ) AS existed
);

-- ---------------------------------------------------------------------------
-- 自检：应当恰好 2 行（1 个菜单 + 1 个按钮），且按钮的 parent_id 等于菜单的 id
--
--   SELECT id, name, permission, type, parent_id, component
--     FROM `system_menu`
--    WHERE `permission` LIKE 'trade:order-invoice:%' ORDER BY type;
--
-- 再验一次"超管看得到按钮"（这才是这一步的目的）：
--   用超管登录管理端 → 刷新 → 「商城 → 订单中心 → 开票申请」应当出现，
--   列表页操作列里的「开具发票」按钮也应当在（不出现说明权限串没进菜单表）。
-- ---------------------------------------------------------------------------
