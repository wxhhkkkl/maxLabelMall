-- ============================================================================
-- 024 —— 个人会员订阅订单：标记 + 用户端禁止退款 + 支付后直接完成
-- ============================================================================
-- 背景（2026-10-27 所有者拍板）：
--   · 个人会员订阅订单与个人订阅一样，**用户端不再提供退款入口**；
--     运营后台的人工退款通道保留。
--   · 纯虚拟的订阅订单支付成功后没有发货动作，**直接置为「已完成」**。
--
-- 判定来源：订单创建时按订单项 SKU 命中 `maxlabel_subscription_plan.scope = 1`（个人），
-- 在 `trade_order.maxlabel_subscription` 打快照；企业授权继续用 `maxlabel_org_id`。
--
-- 幂等：加列用 information_schema 判断；回填是 `WHERE maxlabel_subscription = 0`。
--
-- 回查：
--   SELECT column_name, column_type, is_nullable, column_default
--     FROM information_schema.columns
--    WHERE table_schema = DATABASE() AND table_name = 'trade_order'
--      AND column_name = 'maxlabel_subscription';
--   SELECT COUNT(*) FROM trade_order
--    WHERE deleted = 0 AND maxlabel_subscription = b'1';
--
-- 回滚：
--   ALTER TABLE `trade_order` DROP COLUMN `maxlabel_subscription`;
-- ============================================================================

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'trade_order'
     AND column_name = 'maxlabel_subscription'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_order` ADD COLUMN `maxlabel_subscription` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否 MaxLabel 会员订阅订单（个人；用户端不允许退款）''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回填历史订单：订单项 SKU 命中个人计划（scope=1）即视为订阅订单。
-- 企业订单（scope=2）已有 maxlabel_org_id，不在这里标记。
UPDATE `trade_order` o
JOIN `trade_order_item` i ON i.order_id = o.id AND i.deleted = 0
JOIN `maxlabel_subscription_plan` p ON p.sku_id = i.sku_id AND p.tenant_id = o.tenant_id
     AND p.deleted = 0 AND p.scope = 1
SET o.maxlabel_subscription = b'1'
WHERE o.deleted = 0 AND o.maxlabel_subscription = b'0';

-- 历史已支付的个人订阅订单：纯虚拟、没有发货动作，直接补成「已完成」。
-- 只改 status=10（待发货）；待支付/已取消订单不碰。
UPDATE `trade_order`
SET `status` = 30, `finish_time` = NOW()
WHERE `deleted` = 0
  AND `maxlabel_subscription` = b'1'
  AND `status` = 10
  AND `delivery_type` = 3;
