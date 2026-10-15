-- ============================================================================
-- 009 —— 给发票抬头加「票种」：普票与专票分开管理（Q36）
--
-- ⚠️ 本项目自建脚本，**首次上线执行一次**；下面写了幂等保护（**可以重跑**）。
--
-- 为什么改（specs/006-order-invoice 的 Q36，2026-10-15 所有者实测后提的）
-- ----------------------------------------------------------------------------
--   原来抬头**刻意不绑票种**（Q27 的设计）：一条抬头两种票都能用，理由是不想让
--   只开普票的客户被迫填开户行。所有者用下来要的是**分开管理**：
--   「普通发票和增值税发票分开管理」。
--
--   加一列之后，同一件事换个做法解决：
--     · 普票抬头 → 只要求抬头名称（税号选填）
--     · 专票抬头 → 名称、税号、地址、电话、开户行及账号**五项全填**
--   校验按**抬头自己的票种**判（`InvoiceValidator`），比原来"填的时候宽松、选专票时才拒"
--   更早暴露问题。
--
--   「默认抬头」的唯一性也随之从 **按会员** 改成 **按会员 + 票种** ——
--   否则设了专票的默认，会把普票那条的默认也清掉。
--
-- 回填
-- ----------------------------------------------------------------------------
--   执行时生产库 `trade_invoice_title` 是 **0 行**，所以没有历史数据要猜。
--   `DEFAULT 1`（普票）只是兜底：万一别的环境有存量，它们会落在"普票"这一档，
--   而普票的校验最宽松（只要求名称），不会把已有数据变成非法。
--
-- 幂等
-- ----------------------------------------------------------------------------
--   `ALTER TABLE ... ADD COLUMN` 本身**不幂等**（重跑报 Duplicate column），
--   所以这里先查 information_schema，只有列不存在时才执行 ALTER —— 可安全重跑。
--
-- 回滚
-- ----------------------------------------------------------------------------
--   ALTER TABLE `trade_invoice_title` DROP COLUMN `type`;
--   ⚠️ 会丢掉"这条抬头是普票还是专票"的区分（回滚后所有抬头又变成两票通用）。
-- ============================================================================

SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'trade_invoice_title'
                  AND COLUMN_NAME = 'type');

SET @ddl := IF(@exists = 0,
    'ALTER TABLE `trade_invoice_title` ADD COLUMN `type` tinyint NOT NULL DEFAULT 1 COMMENT ''发票类型：1 电子普通发票，2 电子增值税专用发票'' AFTER `user_id`',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 自检：应当有 type 一列，且默认值为 1
--   SELECT column_name, column_type, is_nullable, column_default, column_comment
--     FROM information_schema.columns
--    WHERE table_schema = DATABASE() AND table_name = 'trade_invoice_title' AND column_name = 'type';
-- ---------------------------------------------------------------------------
