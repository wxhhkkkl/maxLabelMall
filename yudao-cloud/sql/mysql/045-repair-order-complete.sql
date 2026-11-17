-- ============================================================================
-- 021 客户服务 —— 维修订单手动完成维修（045）
--
-- 先决条件：044-customer-service-refund.sql 已执行。
-- 回滚：
--   ALTER TABLE trade_repair_order
--     DROP COLUMN complete_operator_id, DROP COLUMN complete_time;
-- ============================================================================

ALTER TABLE `trade_repair_order`
  ADD COLUMN `complete_time` datetime NULL DEFAULT NULL COMMENT '完成维修时间' AFTER `refund_operator_id`,
  ADD COLUMN `complete_operator_id` bigint NULL DEFAULT NULL COMMENT '完成维修操作人（system_users.id）' AFTER `complete_time`;
