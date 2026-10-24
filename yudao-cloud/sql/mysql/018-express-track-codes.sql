-- ============================================================================
-- 018 —— 给 trade_delivery_express 加 `kd100_code`，并回填租户 162 的快递100 编码
--
-- 为什么不能直接改 `code`
-- ----------------------------------------------------------------------------
--   `trade_delivery_express.code` 现在存的是 `STO / SF / ZTO / YD / ...`（短码），
--   它**同时**被 `TradeStatusSyncToWxaOrderHandler` 当作 `expressCompany` 上送
--   微信小程序（微信要的正是这套短码）。
--
--   快递100 的实时查询接口 `com` 要的是全拼（`shentong / shunfeng / ...`）。
--   把 `code` 改成全拼 = 微信物流同步拿到非法公司编码，是一个今天不报、发货时才炸的回归。
--
--   所以：**加一列 `kd100_code` 专供快递100，`code` 一个字节都不动。**
--
-- 回填范围（租户 162 = 生产租户；依据 scripts/backfill_legacy_mall.py 的 13 家）
-- ----------------------------------------------------------------------------
--   · 12 家能确认对应关系 → 填 `kd100_code`
--   · 「安能」在快递100 有 `ane66`（安能快递）与 `annengwuliu`（安能快运）两条产品线，
--     历史数据只有一个「安能」，无法确认 → **留空**（按 FR-261 降级，不猜）
--   · 跨越 / 顺心捷达原本 `code` 为空，这里按 `name` 定位后填 `kuayue` / `sxjdfreight`
--
-- 幂等 / 回滚
-- ----------------------------------------------------------------------------
--   · 加列：先查 information_schema，已存在则跳过；可安全重跑。
--   · 回填：只更新 `kd100_code IS NULL` 的行；**不会覆盖**运维后来手工填过的值。
--   · 回滚：`ALTER TABLE trade_delivery_express DROP COLUMN kd100_code;`
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 加列（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断）
-- ---------------------------------------------------------------------------
SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'trade_delivery_express'
      AND COLUMN_NAME = 'kd100_code'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `trade_delivery_express` ADD COLUMN `kd100_code` varchar(64) NULL DEFAULT NULL COMMENT ''快递100 的公司编码（com 参数），为空 = 暂不支持查询''',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. 回填租户 162（只写 kd100_code；code 列不动）
-- ---------------------------------------------------------------------------
UPDATE `trade_delivery_express` SET `kd100_code` = 'shentong'     WHERE `tenant_id` = 162 AND `name` = '申通'     AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'shunfeng'     WHERE `tenant_id` = 162 AND `name` = '顺丰快递' AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'zhongtong'    WHERE `tenant_id` = 162 AND `name` = '中通快递' AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'yunda'        WHERE `tenant_id` = 162 AND `name` = '韵达'     AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'yuantong'     WHERE `tenant_id` = 162 AND `name` IN ('圆通快递', '圆通速递') AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'jd'           WHERE `tenant_id` = 162 AND `name` = '京东'     AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'jtexpress'    WHERE `tenant_id` = 162 AND `name` = '极兔速递' AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'debangwuliu'  WHERE `tenant_id` = 162 AND `name` = '德邦'     AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'youshuwuliu'  WHERE `tenant_id` = 162 AND `name` = '优速'     AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'kuayue'       WHERE `tenant_id` = 162 AND `name` = '跨越'     AND `deleted` = 0 AND `kd100_code` IS NULL;
UPDATE `trade_delivery_express` SET `kd100_code` = 'sxjdfreight'  WHERE `tenant_id` = 162 AND `name` = '顺心捷达' AND `deleted` = 0 AND `kd100_code` IS NULL;
-- 安能：不填（ane66 / annengwuliu 无法确认）

-- ---------------------------------------------------------------------------
-- 3. 核对：租户 162 应有 13 家，其中 12 家有 kd100_code、安能为空
-- ---------------------------------------------------------------------------
SELECT `id`, `code`, `name`, `kd100_code`
FROM `trade_delivery_express`
WHERE `tenant_id` = 162 AND `deleted` = 0
ORDER BY `id`;
