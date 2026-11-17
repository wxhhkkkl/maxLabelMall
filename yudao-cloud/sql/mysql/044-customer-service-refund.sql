-- ============================================================================
-- 021 客户服务 —— 维修订单退款 + 管理端四个菜单（044）
--
-- 先决条件：043-customer-service.sql 已执行（5 张表 + 客户服务菜单）。
-- 执行顺序：先跑本脚本，再发后端，最后发管理端。
--
-- 本脚本做两件事：
--   1. trade_repair_order 增加退款字段（全额退款；在线原路退、线下人工标记）
--   2. 管理端「客户服务」拆成四个菜单：
--      投诉咨询 / 在线维修 / 回收地址设置 / 维修订单
--      维修订单下增加「维修退款」按钮（trade:customer-service:refund）
--
-- 回滚（按依赖顺序）：
--   ALTER TABLE trade_repair_order
--     DROP COLUMN refund_operator_id, DROP COLUMN refund_time,
--     DROP COLUMN refund_reason, DROP COLUMN refund_price,
--     DROP COLUMN pay_refund_id, DROP COLUMN refund_status;
--   DELETE FROM system_menu WHERE permission = 'trade:customer-service:refund';
--   DELETE FROM system_menu WHERE path IN ('repair','return-address','repair-order')
--      AND parent_id = (SELECT id FROM (SELECT id FROM system_menu WHERE path='/customer-service' AND parent_id=0 LIMIT 1) t);
--   UPDATE system_menu SET name='服务工单' WHERE path='ticket' AND parent_id = (...);
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 维修订单退款字段
-- ---------------------------------------------------------------------------
ALTER TABLE `trade_repair_order`
  ADD COLUMN `refund_status` tinyint NOT NULL DEFAULT 0 COMMENT '退款状态：0 未退款 10 退款中 20 已退款 30 退款失败' AFTER `offline_confirm_time`,
  ADD COLUMN `pay_refund_id` bigint NULL DEFAULT NULL COMMENT '支付退款单号（在线支付）' AFTER `refund_status`,
  ADD COLUMN `refund_price` int NULL DEFAULT NULL COMMENT '退款金额，分' AFTER `pay_refund_id`,
  ADD COLUMN `refund_reason` varchar(200) NULL DEFAULT NULL COMMENT '退款原因' AFTER `refund_price`,
  ADD COLUMN `refund_time` datetime NULL DEFAULT NULL COMMENT '退款完成时间' AFTER `refund_reason`,
  ADD COLUMN `refund_operator_id` bigint NULL DEFAULT NULL COMMENT '退款操作人（system_users.id）' AFTER `refund_time`,
  ADD INDEX `idx_repair_order_refund` (`tenant_id` ASC, `refund_status` ASC, `create_time` ASC) USING BTREE;

-- ---------------------------------------------------------------------------
-- 2. 管理端菜单：投诉咨询 / 在线维修 / 回收地址设置 / 维修订单
-- ---------------------------------------------------------------------------
UPDATE `system_menu`
   SET `name` = '投诉咨询', `component_name` = 'TradeCustomerServiceTicket',
       `updater` = '', `update_time` = NOW()
 WHERE `path` = 'ticket' AND `parent_id` = (
   SELECT `id` FROM (SELECT `id` FROM `system_menu`
                      WHERE `path` = '/customer-service' AND `parent_id` = 0
                        AND `deleted` = b'0' ORDER BY `id` LIMIT 1) AS group_row
 ) AND `deleted` = b'0';

SET @customer_service_group_id = (
  SELECT `id` FROM `system_menu`
   WHERE `path` = '/customer-service' AND `parent_id` = 0 AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '在线维修', 'trade:customer-service:query', 2, 20, @customer_service_group_id,
       'repair', 'ep:tools', 'trade/customer-service/repair/index',
       'TradeCustomerServiceRepair', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu`
                  WHERE `path` = 'repair' AND `parent_id` = @customer_service_group_id
                    AND `deleted` = b'0') AS existed
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '回收地址设置', 'trade:customer-service:query', 2, 30, @customer_service_group_id,
       'return-address', 'ep:location', 'trade/customer-service/address/index',
       'TradeCustomerServiceAddress', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu`
                  WHERE `path` = 'return-address' AND `parent_id` = @customer_service_group_id
                    AND `deleted` = b'0') AS existed
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '维修订单', 'trade:customer-service:query', 2, 40, @customer_service_group_id,
       'repair-order', 'ep:list', 'trade/customer-service/repair-order/index',
       'TradeCustomerServiceRepairOrder', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu`
                  WHERE `path` = 'repair-order' AND `parent_id` = @customer_service_group_id
                    AND `deleted` = b'0') AS existed
);

SET @repair_order_page_id = (
  SELECT `id` FROM `system_menu`
   WHERE `path` = 'repair-order' AND `parent_id` = @customer_service_group_id
     AND `deleted` = b'0' ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '维修退款', 'trade:customer-service:refund', 3, 1, @repair_order_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu`
                  WHERE `permission` = 'trade:customer-service:refund'
                    AND `deleted` = b'0') AS existed
);
