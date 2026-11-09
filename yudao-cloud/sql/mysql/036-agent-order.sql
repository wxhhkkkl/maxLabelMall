-- ============================================================================
-- 036 —— 代客下单：订单归属、来源、权限点
--
-- 背景（2026-11-06 所有者拍板 Q123~Q133）：
--   运营可在管理端以客户账户名义创建订单；后台记录「谁帮忙下的单」。
--   · trade_order.agent_user_id：代下单的后台用户编号，NULL = 客户自助
--   · terminal 字典「后台代客 / 40」：订单来源可直接筛选/报表
--   · 两个独立权限点：trade:order:agent-create / trade:after-sale:agent-create
--
-- ⚠️ 上线顺序：本脚本 → 后端 → 管理端 → 门面站。
--    漏跑本脚本而后端先上，会报 Unknown column 'agent_user_id'（与 032 事故同类）。
--
-- 幂等：先查 information_schema / 菜单表；重复执行安全。
-- 回滚：建议保留 agent_user_id（已产生的归属记录不能丢）；临时关闭入口用
--       UPDATE system_menu SET status = 1 WHERE permission IN (...);
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. trade_order.agent_user_id
-- ---------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'agent_user_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_order` ADD COLUMN `agent_user_id` bigint NULL DEFAULT NULL COMMENT ''代客下单的后台用户编号；NULL=客户自助'' AFTER `user_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND INDEX_NAME = 'idx_agent_user_id');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE `trade_order` ADD INDEX `idx_agent_user_id` (`agent_user_id`)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. terminal 字典：后台代客 / 40
-- ---------------------------------------------------------------------------
INSERT INTO `system_dict_data`
  (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 40, '后台代客', '40', 'terminal', 0, 'default', '', '终端 - 后台代客',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (
    SELECT `id` FROM `system_dict_data` WHERE `dict_type` = 'terminal' AND `value` = '40'
  ) AS existed
);

-- ---------------------------------------------------------------------------
-- 3. 权限点：代客下单（挂在订单列表下）
--    ⚠️ 父级取 trade:order:query 的 parent_id（270 = 订单列表），不是 query 按钮自己的 id。
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '代客下单', 'trade:order:agent-create', 3, 20,
       (SELECT `parent_id` FROM (
          SELECT `parent_id` FROM `system_menu` WHERE `permission` = 'trade:order:query'
        ) AS queryButton),
       '', '', '', NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (
    SELECT `id` FROM `system_menu` WHERE `permission` = 'trade:order:agent-create'
  ) AS existed
);

-- ---------------------------------------------------------------------------
-- 4. 权限点：代客发起售后（挂在售后订单下）
--    父级取 trade:after-sale:query 的 parent_id（267 = 售后订单）。
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '代客发起售后', 'trade:after-sale:agent-create', 3, 20,
       (SELECT `parent_id` FROM (
          SELECT `parent_id` FROM `system_menu` WHERE `permission` = 'trade:after-sale:query'
        ) AS afterSaleButton),
       '', '', '', NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (
    SELECT `id` FROM `system_menu` WHERE `permission` = 'trade:after-sale:agent-create'
  ) AS existed
);

-- ---------------------------------------------------------------------------
-- 5. 核对
-- ---------------------------------------------------------------------------
SELECT `COLUMN_NAME`, `COLUMN_TYPE`, `IS_NULLABLE`
FROM `information_schema`.`COLUMNS`
WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = 'trade_order'
  AND `COLUMN_NAME` = 'agent_user_id';

SELECT `id`, `name`, `permission`, `type`, `parent_id`
FROM `system_menu`
WHERE `permission` IN ('trade:order:agent-create', 'trade:after-sale:agent-create');

SELECT `label`, `value`
FROM `system_dict_data`
WHERE `dict_type` = 'terminal' AND `value` = '40';
