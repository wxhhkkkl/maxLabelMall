-- ============================================================================
-- 010 —— 给发票抬头加「抬头类型」：企业 / 个人（Q38）
--
-- ⚠️ 本项目自建脚本，**首次上线执行一次**；下面写了幂等保护（**可以重跑**）。
--
-- 为什么要它（specs/006-order-invoice 的 Q38，2026-10-15 所有者要求"加一个个人抬头"）
-- ----------------------------------------------------------------------------
--   之前抬头只有"公司"一种形态（Q37 定的必填是「名称 + 税号」两项）。
--   但**个人客户开普票没有纳税人识别号** —— 于是这类客户连抬头都存不下来。
--
--   加一列区分（两张表都要，各自的理由不同）：
--     · 企业（1）→ 名称 + 税号（都必填）
--     · 个人（2）→ **只要名称**；税号是**选填**（自然人可以用身份证号当纳税人识别号，
--       所以是"不要求"而不是"禁止"——写成禁止会把合法场景挡在门外）
--
--   ① `trade_invoice_title`（抬头簿）—— 客户在个人中心维护的就是它，类型跟着抬头走；
--   ② `trade_order_invoice`（开票申请）—— 抬头信息在申请上是**快照**（FR-158），
--      类型也是快照的一部分：不存的话，后台工作台只能靠"税号空着"去猜这是不是个人票。
--
--   ⚠️ **个人抬头只能开普票**：增值税专用发票按税法只能开给一般纳税人企业，
--      所以"个人 + 专票"会被服务端直接拒（`InvoiceValidator`）。
--      抬头簿里因此也不会出现"个人抬头的专票" —— 与 Q36 的分段展示天然吻合：
--      个人抬头只会落在普票那一段里。
--
-- 回填
-- ----------------------------------------------------------------------------
--   `DEFAULT 1`（企业）只是兜底。**真正决定存量行归哪一档的是下面那条 UPDATE**：
--       `tax_no IS NULL OR tax_no = ''` → 个人（2）
--   依据：**企业抬头没有税号根本开不出票**，所以"没填税号"的存量行只可能是个人抬头。
--
--   ⚠️ 这条回填不是"让后台那一列好看一点"，它**有功能后果**：
--      结算页从抬头簿选一条抬头时，抬头类型会跟着那条抬头走；如果一条**没有税号**的
--      个人抬头被错标成**企业**，表单就会开始要求税号 —— 而客户根本没有这个号，
--      于是**他选不了自己存过的那条抬头**。所以这里必须按证据回填，不能一律标企业。
--   ⚠️ 残留风险：Q27 时代普票的税号是**选填**，理论上那时可能存在"企业抬头但没填税号"的行，
--      会被回填成个人。这类行的后果同上（只是标错一个标签），可手工订正。
--
--   实测（2026-10-16，生产库 ruoyi-vue-pro）：两张表**各 1 行**，且**两行都是个人抬头**
--   （抬头簿：名称「李彦亭」、无税号、默认；申请：订单 772、已开票、名称「李先生」、无税号）——
--   正是这条 UPDATE 要处理的那一类。
--
-- 幂等
-- ----------------------------------------------------------------------------
--   `ADD COLUMN` 本身不幂等（重跑报 Duplicate column），所以先查 information_schema，
--   只有列不存在时才执行 ALTER —— 可安全重跑（同 009 的做法）。
--
-- 回滚
-- ----------------------------------------------------------------------------
--   ALTER TABLE `trade_order_invoice` DROP COLUMN `title_kind`;
--   ALTER TABLE `trade_invoice_title` DROP COLUMN `title_kind`;
--   ⚠️ 会丢掉"这条抬头/这笔申请是开给企业还是个人"的区分（回滚后一律按企业要求税号）。
--
-- 执行记录
-- ----------------------------------------------------------------------------
--   ✅ **2026-10-16 已在生产库（ruoyi-vue-pro）执行**，走
--      `scripts/apply_003_filter_dimension.py --file 010-invoice-title-kind.sql`
--      （经应用机 SSH 隧道）。执行后回查：
--        · 两列均为 tinyint NOT NULL DEFAULT 1，注释与上面一致；
--        · 回填 0 遗漏（"没有税号却不是个人"的行数 = 0）；
--        · 两张表各 1 行，**都归到了 2（个人）**。
--      ⚠️ 执行后**必须接着发版后端**：新代码的查询与写入都带着 `title_kind`，
--         列在而代码旧（或反过来）都会让后台开票列表 / C 端下单 500。
--      发版顺序：先跑本脚本 → 再发后端 jar → 最后发两个前端。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 抬头簿：会员维护的那条抬头本身带类型
-- ---------------------------------------------------------------------------
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'trade_invoice_title'
                  AND COLUMN_NAME = 'title_kind');

SET @ddl := IF(@exists = 0,
    'ALTER TABLE `trade_invoice_title` ADD COLUMN `title_kind` tinyint NOT NULL DEFAULT 1 COMMENT ''抬头类型：1 企业，2 个人'' AFTER `type`',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回填（只在**刚加上这一列**时跑一次；重跑时 @exists 已是 1，整条跳过）
SET @backfill := IF(@exists = 0,
    'UPDATE `trade_invoice_title` SET `title_kind` = 2 WHERE `tax_no` IS NULL OR `tax_no` = ''''',
    'SELECT 1');
PREPARE stmt FROM @backfill;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. 开票申请：抬头类型是**申请时快照**的一部分（FR-158），后台靠它区分个人票
-- ---------------------------------------------------------------------------
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'trade_order_invoice'
                  AND COLUMN_NAME = 'title_kind');

SET @ddl := IF(@exists = 0,
    'ALTER TABLE `trade_order_invoice` ADD COLUMN `title_kind` tinyint NOT NULL DEFAULT 1 COMMENT ''抬头类型：1 企业，2 个人'' AFTER `type`',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回填：同第 1 节的理由（`tax_no` 为空的申请不可能是企业抬头）
SET @backfill := IF(@exists = 0,
    'UPDATE `trade_order_invoice` SET `title_kind` = 2 WHERE `tax_no` IS NULL OR `tax_no` = ''''',
    'SELECT 1');
PREPARE stmt FROM @backfill;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 自检（照 009 的做法：**不拿"命令没报错"当成功**）
--
--   ① 两列都在、默认值都是 1：
--      SELECT table_name, column_name, column_type, column_default, column_comment
--        FROM information_schema.columns
--       WHERE table_schema = DATABASE() AND column_name = 'title_kind';
--
--   ② 回填结果：**没有税号的行应当都成了 2**，下面两句都该返回 0 行
--      SELECT id, title_name, tax_no, title_kind FROM trade_invoice_title
--       WHERE (tax_no IS NULL OR tax_no = '') AND title_kind <> 2;
--      SELECT id, order_no,  tax_no, title_kind FROM trade_order_invoice
--       WHERE (tax_no IS NULL OR tax_no = '') AND title_kind <> 2;
-- ---------------------------------------------------------------------------
