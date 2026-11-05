-- ============================================================================
-- 035 —— 发票专票字段拆分：开户行 / 银行账户 / 行号
--
-- 背景（2026-11-05 所有者要求）：
--   电子增值税专用发票原来只有一列 `bank_name`（「开户行及账号」合在一起），
--   现在拆成三列可分别编辑：
--     · bank_name    开户行（银行名称/支行）
--     · bank_account 银行账户（账号）
--     · bank_code    行号（开户行联行号 / CNAPS）
--
-- ⚠️ 历史 `bank_name` 里可能是「工商银行南京分行 6222…」这种合并文本，
--    **无法可靠拆分**；保留原值不动，新列留空，由客户/运营编辑时补全。
--
-- 幂等：INFORMATION_SCHEMA 判断列是否存在；重复执行安全。
-- ============================================================================

SET @c1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_invoice_title' AND COLUMN_NAME = 'bank_account');
SET @d1 := IF(@c1 = 0,
  'ALTER TABLE `trade_invoice_title` ADD COLUMN `bank_account` varchar(64) NULL DEFAULT NULL COMMENT ''银行账户（专票选填）'' AFTER `bank_name`',
  'SELECT 1');
PREPARE s1 FROM @d1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @c2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_invoice_title' AND COLUMN_NAME = 'bank_code');
SET @d2 := IF(@c2 = 0,
  'ALTER TABLE `trade_invoice_title` ADD COLUMN `bank_code` varchar(32) NULL DEFAULT NULL COMMENT ''开户行行号（专票选填）'' AFTER `bank_account`',
  'SELECT 1');
PREPARE s2 FROM @d2; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @c3 := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order_invoice' AND COLUMN_NAME = 'bank_account');
SET @d3 := IF(@c3 = 0,
  'ALTER TABLE `trade_order_invoice` ADD COLUMN `bank_account` varchar(64) NULL DEFAULT NULL COMMENT ''银行账户（申请快照）'' AFTER `bank_name`',
  'SELECT 1');
PREPARE s3 FROM @d3; EXECUTE s3; DEALLOCATE PREPARE s3;

SET @c4 := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order_invoice' AND COLUMN_NAME = 'bank_code');
SET @d4 := IF(@c4 = 0,
  'ALTER TABLE `trade_order_invoice` ADD COLUMN `bank_code` varchar(32) NULL DEFAULT NULL COMMENT ''开户行行号（申请快照）'' AFTER `bank_account`',
  'SELECT 1');
PREPARE s4 FROM @d4; EXECUTE s4; DEALLOCATE PREPARE s4;

-- 核对
SELECT `TABLE_NAME`, `COLUMN_NAME`, `COLUMN_TYPE`
FROM `information_schema`.`COLUMNS`
WHERE `TABLE_SCHEMA` = DATABASE()
  AND `COLUMN_NAME` IN ('bank_account', 'bank_code')
  AND `TABLE_NAME` IN ('trade_invoice_title', 'trade_order_invoice')
ORDER BY `TABLE_NAME`, `COLUMN_NAME`;
