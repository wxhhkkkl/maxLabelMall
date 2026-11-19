-- ============================================================================
-- 047 订单列表拆成「商品订单 / 授权订单」两个菜单（2026-11-18）
--
-- 变更（只动菜单树，不改表结构）：
--   1. 原「订单列表」改名「商品订单」（path/component 不变，component_name 仍是 TradeOrder）
--   2. 新增「授权订单」菜单：同一个页面组件，component_name = TradeOrderLicense，
--      前端按路由名给后端传 `license=true`（详见 mall/trade/order/index.vue）
--   3. 授权订单菜单紧跟商品订单，后续兄弟菜单的 sort 顺延
--   4. 新菜单跟随「商品订单」已授权的角色（后续新角色由管理员在后台自行分配）
--
-- 口径：
--   授权订单 = 个人会员订阅单（trade_order.maxlabel_subscription = 1，024）
--            或 企业授权单（trade_order.maxlabel_org_id 非空，012）
--   商品订单 = 其余全部订单（含 BarTender 这类虚拟软件商品，它们没有授权标记）
--   授权订单都是「支付即完成」的虚拟单，因此该菜单默认标签是「全部订单」。
--
-- 上线顺序：
--   先执行本脚本 → 再发后端（TradeOrderPageReqVO.license）→ 最后发管理端。
--   菜单随登录缓存下发，执行后管理员需**重新登录**。
--
-- 回滚：
--   DELETE FROM system_role_menu WHERE menu_id = (SELECT id FROM (SELECT id FROM system_menu
--     WHERE path='license-order' AND deleted=b'0') t);
--   DELETE FROM system_menu WHERE path='license-order' AND deleted=b'0';
--   UPDATE system_menu SET name='订单列表', sort=1 WHERE path='order' AND deleted=b'0';
--   UPDATE system_menu SET sort=2 WHERE path='after-sale' AND deleted=b'0';
--   UPDATE system_menu SET sort=3 WHERE path IN ('delivery','invoice') AND deleted=b'0';
--   UPDATE system_menu SET sort=4 WHERE path='brokerage' AND deleted=b'0';
-- ============================================================================

SET @order_center_id = (
  SELECT `id` FROM `system_menu`
   WHERE `name` = '订单中心' AND `path` = '/trade' AND `parent_id` = 0 AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

-- ---------------------------------------------------------------------------
-- 1. 原「订单列表」→「商品订单」
-- ---------------------------------------------------------------------------
UPDATE `system_menu`
   SET `name` = '商品订单', `updater` = '', `update_time` = NOW()
 WHERE `path` = 'order' AND `parent_id` = @order_center_id AND `deleted` = b'0';

SET @order_menu_id = (
  SELECT `id` FROM `system_menu`
   WHERE `path` = 'order' AND `parent_id` = @order_center_id AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

-- ---------------------------------------------------------------------------
-- 2. 新增「授权订单」（同一个页面组件，靠 component_name 区分）
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '授权订单', 'trade:order:query', 2, 2, @order_center_id,
       'license-order', 'ep:key', 'mall/trade/order/index',
       'TradeOrderLicense', 0, b'1', b'0', b'1',
       '', NOW(), '', NOW(), b'0'
  FROM DUAL
 WHERE NOT EXISTS (
   SELECT 1 FROM (SELECT `id` FROM `system_menu`
                   WHERE `path` = 'license-order' AND `parent_id` = @order_center_id
                     AND `deleted` = b'0') AS existed
 );

SET @license_menu_id = (
  SELECT `id` FROM `system_menu`
   WHERE `path` = 'license-order' AND `parent_id` = @order_center_id AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

-- ---------------------------------------------------------------------------
-- 3. 兄弟菜单排序顺延：0 交易配置 / 1 商品订单 / 2 授权订单 / 3 售后退款
--    / 4 配送管理 / 5 开票申请 / 6 佣金管理
-- ---------------------------------------------------------------------------
UPDATE `system_menu` SET `sort` = 3, `updater` = '', `update_time` = NOW()
 WHERE `path` = 'after-sale' AND `parent_id` = @order_center_id AND `deleted` = b'0';
UPDATE `system_menu` SET `sort` = 4, `updater` = '', `update_time` = NOW()
 WHERE `path` = 'delivery' AND `parent_id` = @order_center_id AND `deleted` = b'0';
UPDATE `system_menu` SET `sort` = 5, `updater` = '', `update_time` = NOW()
 WHERE `path` = 'invoice' AND `parent_id` = @order_center_id AND `deleted` = b'0';
UPDATE `system_menu` SET `sort` = 6, `updater` = '', `update_time` = NOW()
 WHERE `path` = 'brokerage' AND `parent_id` = @order_center_id AND `deleted` = b'0';

-- ---------------------------------------------------------------------------
-- 4. 跟随「商品订单」已授权的角色
-- ---------------------------------------------------------------------------
INSERT INTO `system_role_menu`
  (`role_id`, `menu_id`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT parent_acl.`role_id`, @license_menu_id, 'deploy', NOW(), 'deploy', NOW(), b'0',
       parent_acl.`tenant_id`
FROM (
  SELECT `role_id`, `tenant_id`
    FROM `system_role_menu`
   WHERE `menu_id` = @order_menu_id AND `deleted` = b'0'
) AS parent_acl
LEFT JOIN `system_role_menu` AS existed
  ON existed.`role_id` = parent_acl.`role_id`
 AND existed.`menu_id` = @license_menu_id
 AND existed.`deleted` = b'0'
WHERE @license_menu_id IS NOT NULL
  AND existed.`id` IS NULL;
