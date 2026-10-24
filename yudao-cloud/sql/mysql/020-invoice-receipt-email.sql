-- ============================================================================
-- 020 —— 发票模块：收据票种 + 邮箱（011）
--
-- 1. 收据是新的票种 3（`InvoiceTypeEnum.RECEIPT`）：type 列是 tinyint，**无需 DDL**；
--    收据不是税票，抬头**只要名称**（Q61）。
-- 2. 邮箱是选填项：开票申请与抬头簿各存一份（Q62/Q63），后台展示、人工发送。
--    - `trade_order_invoice.email`：本次申请的邮箱快照
--    - `trade_invoice_title.email`：抬头簿的邮箱，选这条抬头时自动带出
--
-- 幂等：INFORMATION_SCHEMA 判断列是否存在；重复执行安全。
-- ============================================================================

SET @c1 := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order_invoice' AND COLUMN_NAME = 'email'
);
SET @d1 := IF(@c1 = 0,
    'ALTER TABLE `trade_order_invoice` ADD COLUMN `email` varchar(128) NULL DEFAULT NULL COMMENT ''接收发票/收据的邮箱（选填）'' AFTER `bank_name`',
    'SELECT 1');
PREPARE s1 FROM @d1;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @c2 := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_invoice_title' AND COLUMN_NAME = 'email'
);
SET @d2 := IF(@c2 = 0,
    'ALTER TABLE `trade_invoice_title` ADD COLUMN `email` varchar(128) NULL DEFAULT NULL COMMENT ''接收发票/收据的邮箱（选填）'' AFTER `bank_name`',
    'SELECT 1');
PREPARE s2 FROM @d2;
EXECUTE s2;
DEALLOCATE PREPARE s2;

-- 核对
SELECT `TABLE_NAME`, `COLUMN_NAME`, `COLUMN_TYPE`
FROM `information_schema`.`COLUMNS`
WHERE `TABLE_SCHEMA` = DATABASE()
  AND `COLUMN_NAME` = 'email'
  AND `TABLE_NAME` IN ('trade_order_invoice', 'trade_invoice_title')
ORDER BY `TABLE_NAME`;
