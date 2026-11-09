-- ============================================================================
-- 032 —— 买家删除订单（假删除）：C 端隐藏，运营后台仍可见
--
-- 背景（2026-11-04 所有者拍板）：
--   用户端允许删除「已取消 / 已完成」的订单；删除后只对买家自己隐藏，
--   运营人员仍要能在后台看到这笔订单。
--
-- 变更：`trade_order` 增加 `user_deleted` bit(1)，默认 0。
--   · C 端查询（列表 / 计数 / 详情 / 用户态操作）一律带 `user_deleted = 0`
--   · 管理端查询**不带**这个条件，因此照常可见
--   · 与全局逻辑删除 `deleted` 是两回事：后者会让运营后台也查不到
--
-- 幂等：先查 information_schema。
-- ============================================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name = 'user_deleted');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_order` ADD COLUMN `user_deleted` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''用户端是否已删除（假删除，仅对 C 端隐藏）'' AFTER `deleted`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
