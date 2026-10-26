-- ============================================================================
-- 022（012 企业客户与席位授权 · 复购折算）
--
-- 内容：`maxlabel_org_license` 加 `folded_time` —— 复购时把旧批次的剩余时长
--       折算进新批次，旧批次只留痕，不再参与席位容量、企业到期日与下次续费起点。
--
-- 背景（2026-10-26 所有者拍板，Q78）：
--   购买数量 = 购买后的**总席位数**。再次购买时把当前未过期批次的剩余时长折算成
--   「新席位数 × 时长」：例如原有 1 席位还剩 10 个月，再买 2 席位 × 1 年，
--   新批次 = 2 席位，到期日 = 今天 + (1×10 + 2×12) / 2 = 17 个月。
--   被折算的旧批次 MUST NOT 再提供容量，否则同一段时间会被算两次。
--
-- 幂等：先查 information_schema，再动态执行。
--
-- 执行：python scripts/apply_003_filter_dimension.py --file 022-enterprise-license-folded.sql
--
-- 执行记录：
--   2026-10-26 已通过应用服务器 8.146.233.197 的 SSH 内网隧道，在
--   172.20.68.219:3306/ruoyi-vue-pro（MySQL 8.0.44）执行：5 条语句提交成功；
--   回查 `information_schema.columns`：`folded_time datetime NULL` 存在；
--   当时批次表 4 行，`folded_time` 全为 NULL（还没有复购触发折算）。
--
-- 回查：
--   SELECT column_name, column_type, is_nullable, column_default
--     FROM information_schema.columns
--    WHERE table_schema = DATABASE()
--      AND table_name = 'maxlabel_org_license'
--      AND column_name = 'folded_time';
--   SELECT COUNT(*) FROM maxlabel_org_license WHERE folded_time IS NOT NULL;
--
-- 回滚：旧代码忽略未知列，直接保留即可；确需回滚：
--   ALTER TABLE `maxlabel_org_license` DROP COLUMN `folded_time`;
-- ============================================================================

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'maxlabel_org_license'
     AND column_name = 'folded_time'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `maxlabel_org_license` ADD COLUMN `folded_time` datetime NULL DEFAULT NULL COMMENT ''折算时间（已并入后续批次；非空 = 不再参与容量/到期/续费起点）'' AFTER `refunded`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
