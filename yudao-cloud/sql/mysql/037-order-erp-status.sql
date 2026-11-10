-- ============================================================================
-- 037 —— 订单「ERP系统开单」状态（2026-11-10）
--
-- 背景：ERP 开单是**独立于订单主状态**（status）的业务标记。
--   · 现阶段由运营在管理端手工标记 / 撤销；
--   · 未来由 ERP 对接回写；
--   · 标记已开单时记录 ERP 单号，撤销时清空。
--
-- 字段：
--   trade_order.erp_created  bit(1) NOT NULL DEFAULT b'0'  是否已在ERP系统开单
--   trade_order.erp_no       varchar(64) NULL              ERP系统单号；未开单为 NULL
--
-- ⚠️ 上线顺序：本脚本 → 后端 → 管理端。
--    漏跑本脚本而后端先上，会报 Unknown column 'erp_created'（与 032 / 036 同类）。
--    权限点复用既有的 `trade:order:update`，**不需要**新增 system_menu 记录。
--
-- 幂等：先查 information_schema；重复执行安全。
-- 回滚：保留两列（历史标记不能丢）即可；临时关闭入口用前端隐藏 / 后端撤接口。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. trade_order.erp_created
-- ---------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'erp_created');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_order` ADD COLUMN `erp_created` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否已在ERP系统开单'' AFTER `maxlabel_subscription`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. trade_order.erp_no
-- ---------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'erp_no');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_order` ADD COLUMN `erp_no` varchar(64) NULL DEFAULT NULL COMMENT ''ERP系统单号；未开单为NULL'' AFTER `erp_created`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3. 核对
-- ---------------------------------------------------------------------------
SELECT `COLUMN_NAME`, `COLUMN_TYPE`, `IS_NULLABLE`, `COLUMN_DEFAULT`, `COLUMN_COMMENT`
FROM `information_schema`.`COLUMNS`
WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = 'trade_order'
  AND `COLUMN_NAME` IN ('erp_created', 'erp_no')
ORDER BY `ORDINAL_POSITION`;
