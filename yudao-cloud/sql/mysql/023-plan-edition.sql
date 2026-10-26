-- ---------------------------------------------------------------------------
-- 023 桌面端版本档位（edition）
--
-- 背景：桌面端 017 的授权判定按「标准版 / 专业版 / 企业版」分档，但商城侧没有
-- 任何档位字段，导致 /member/subscription/get 永远不返回 edition —— 订阅有效
-- 也被桌面端判成「未订阅」。本迁移把档位落到三张表：
--
--   maxlabel_subscription_plan.edition  计划配置（管理端可改）
--   member_subscription.edition         个人订阅发放快照（发时不随计划改）
--   maxlabel_org_license.edition        企业批次发放快照
--
-- 口径（2026-10-26 所有者拍板）：
--   个人登录（未付费）→ 标准版（不落库，桌面端按登录态处理）
--   个人会员授权      → 专业版
--   企业授权          → 企业版
--
-- 所有 DDL 都按 information_schema 幂等执行，可重复跑。
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 1. 计划档位
-- ---------------------------------------------------------------------------
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'maxlabel_subscription_plan'
     AND column_name = 'edition'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `maxlabel_subscription_plan` ADD COLUMN `edition` varchar(16) NOT NULL DEFAULT ''standard'' COMMENT ''桌面端版本：standard 标准版 / professional 专业版 / enterprise 企业版'' AFTER `scope`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量计划回填：个人 → 专业版，企业 → 企业版。
-- 只在列刚创建时执行，避免覆盖管理端之后的修改。
SET @ddl = IF(@col_exists = 0,
  'UPDATE `maxlabel_subscription_plan` SET `edition` = IF(`scope` = 2, ''enterprise'', ''professional'')',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 2. 个人订阅发放快照
-- ---------------------------------------------------------------------------
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'member_subscription'
     AND column_name = 'edition'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `member_subscription` ADD COLUMN `edition` varchar(16) NULL DEFAULT NULL COMMENT ''发放时的桌面端版本快照：standard / professional / enterprise'' AFTER `plan_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@col_exists = 0,
  'UPDATE `member_subscription` s JOIN `maxlabel_subscription_plan` p ON p.id = s.`plan_id` SET s.`edition` = p.`edition` WHERE s.`edition` IS NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 3. 企业批次发放快照
-- ---------------------------------------------------------------------------
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'maxlabel_org_license'
     AND column_name = 'edition'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `maxlabel_org_license` ADD COLUMN `edition` varchar(16) NULL DEFAULT NULL COMMENT ''发放时的桌面端版本快照：standard / professional / enterprise'' AFTER `plan_id`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(@col_exists = 0,
  'UPDATE `maxlabel_org_license` l JOIN `maxlabel_subscription_plan` p ON p.id = l.`plan_id` SET l.`edition` = p.`edition` WHERE l.`edition` IS NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
