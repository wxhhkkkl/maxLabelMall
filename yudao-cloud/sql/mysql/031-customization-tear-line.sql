-- ============================================================================
-- 031（014 印刷定制）—— 边角改为「圆角/直角」，撕裂线拆成独立开关
--
-- 背景（2026-11-01 所有者按参考页面截图拍板）：
--   第三步的成品处理是「边角：圆角 / 直角」+「撕裂线」复选框，
--   原实现把两者做成了一个二选一（1 圆角 / 2 撕裂线）。
--
-- 变更：`trade_customization_order` 增加 `tear_line` bit(1)；
--       `finishing` 语义改为 1 圆角 / 2 直角。
-- 计价：圆角或撕裂线视为模切、收模切费；直角不额外收。
--
-- 幂等：先查 information_schema。
-- ============================================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'tear_line');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `tear_line` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否撕裂线'' AFTER `finishing`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
