-- ============================================================================
-- 030（014 印刷定制）—— 对齐参考站点的四步流程字段
--
-- 背景（2026-11-01 所有者拍板）：按 360code 的「四步向导」补齐成品形态与排版字段。
--   · 加工形式增加「定制空白标签」（mode = 4）
--   · 成品形态：卷筒 / 平张；整张规格；圆角 / 撕裂线
--   · 排版：行数、列数、行距、列距、轴芯内径、实际数量
--   · 模板：LabelShop 模板名称（选填）
--   · 材质增加说明文案
--
-- 价格口径不变：仍按「模式 / 材质 / 面积 / 数量 / 模切 / 加急」的已上线模型；
-- 上述排版字段只作为**生产要求**随订单保存，不参与计价。
--
-- 幂等：每列先查 information_schema。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 材质说明
-- ---------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_material' AND column_name = 'description');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_material` ADD COLUMN `description` varchar(1000) NULL DEFAULT '''' COMMENT ''材质说明（展示给客户）'' AFTER `name`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. 订单扩展：成品形态与排版
-- ---------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'form');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `form` tinyint NOT NULL DEFAULT 2 COMMENT ''成品形态：1 卷筒（卷轴） 2 平张（单张）'' AFTER `mode`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'sheet_spec');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `sheet_spec` varchar(32) NULL DEFAULT '''' COMMENT ''整张规格：A4 / 其他'' AFTER `form`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'finishing');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `finishing` tinyint NULL DEFAULT NULL COMMENT ''成品处理：1 圆角 2 撕裂线'' AFTER `die_cut`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'row_count');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `row_count` int NULL DEFAULT 1 COMMENT ''每张行数'' AFTER `finishing`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'column_count');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `column_count` int NULL DEFAULT 1 COMMENT ''每张列数'' AFTER `row_count`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'row_gap');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `row_gap` decimal(6,2) NULL DEFAULT 0 COMMENT ''行间距，mm'' AFTER `column_count`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'column_gap');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `column_gap` decimal(6,2) NULL DEFAULT 0 COMMENT ''列间距，mm'' AFTER `row_gap`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'axis_diameter');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `axis_diameter` varchar(16) NULL DEFAULT '''' COMMENT ''轴芯内径（卷筒）：25.4mm / 76.2mm / 40mm'' AFTER `column_gap`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'label_count');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `label_count` int NULL DEFAULT 0 COMMENT ''实际数量'' AFTER `quantity`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'trade_customization_order' AND column_name = 'template_name');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_customization_order` ADD COLUMN `template_name` varchar(128) NULL DEFAULT '''' COMMENT ''LabelShop 模板名称（选填）'' AFTER `label_count`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
