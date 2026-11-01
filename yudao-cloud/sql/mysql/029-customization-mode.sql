-- ============================================================================
-- 029（014 印刷定制）—— 业务模式由「是否印刷」布尔改为三选一
--
-- 背景（2026-11-01 所有者按参考页面拍板 Q108）：
--   参考站点的模式是 标签印刷（多色）/ 标签打印（黑色）/ 多色印刷+黑色打印 三选一；
--   原实现是「打印必选 + 印刷可选」，多收了一笔打印费。
--
-- 变更：
--   1. `trade_customization_order` 增加 `mode` tinyint（1 印刷 / 2 打印 / 3 两者）
--   2. 旧列 `printing` 回填后删除（该表上线时无订单，回填只是兜底）
--
-- 幂等：加列 / 删列 / 回填都先查 information_schema。
--
-- 回查：
--   SHOW COLUMNS FROM trade_customization_order LIKE 'mode';
--   SHOW COLUMNS FROM trade_customization_order LIKE 'printing';  -- 期望不存在
--
-- 回滚：无法自动恢复旧列语义；如需回退，先加回 printing bit 再按 mode 反填。
-- ============================================================================

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'mode'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `mode` tinyint NOT NULL DEFAULT 2 COMMENT ''业务模式：1 印刷（多色） 2 打印（黑色） 3 两者'' AFTER `quantity`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @old_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'printing'
);
SET @ddl = IF(@old_exists = 1,
  'UPDATE `trade_customization_order` SET `mode` = IF(`printing` = b''1'', 3, 2)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@old_exists = 1,
  'ALTER TABLE `trade_customization_order` DROP COLUMN `printing`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
