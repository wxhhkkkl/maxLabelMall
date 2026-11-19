-- ============================================================================
-- 046 订单中心提升为一级目录 + 订单列表筛选调整（2026-11-18）
--
-- 变更（只动菜单树，不改表结构）：
--   1. 把「订单中心」从「MALL 商城系统」下移到根，成为一级目录（path 改为 /trade）。
--      子菜单（订单列表 / 售后退款 / 交易配置 / 配送管理 / 开票申请 / 佣金管理）
--      保持不动，仍挂在它下面，角色授权（system_role_menu）也不受影响。
--   2. 订单列表的「订单类型」筛选已由前端移除，换成「商品类型（实物 / 虚拟）」，
--      该筛选复用 trade_order.delivery_type（3 = 虚拟商品，1/2 = 实物），**无需加列**。
--
-- 上线顺序：
--   先执行本脚本 → 再发后端（新增 virtual 查询参数）→ 最后发管理端（筛选 UI + 菜单路径）。
--
-- 生效注意：
--   菜单树在登录时随 token 下发并缓存，执行后管理员需**重新登录**才能在侧边栏看到新位置。
--
-- 回滚：
--   UPDATE system_menu
--      SET parent_id = 449, path = 'trade', sort = 65, update_time = NOW()
--    WHERE name = '订单中心' AND path = '/trade' AND parent_id = 0 AND deleted = b'0';
-- ============================================================================

UPDATE `system_menu`
   SET `parent_id` = 0,
       `path` = '/trade',
       `sort` = 60,
       `updater` = '',
       `update_time` = NOW()
 WHERE `name` = '订单中心'
   AND `path` = 'trade'
   AND `parent_id` = 449
   AND `deleted` = b'0';
