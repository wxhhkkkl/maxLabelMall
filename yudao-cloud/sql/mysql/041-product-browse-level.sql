-- ============================================================================
-- 041 —— 019 商品浏览等级（2026-11-16）
--
-- 背景：运营需要把部分商品只开放给特定会员等级（高阶会员专属资料 / 耗材包等），
--       现有 enterprise_only 只能区分「公开商城 / 企业场景」，无法按会员等级控制。
--
-- 变更：product_spu
--   · browse_level_type  tinyint NOT NULL DEFAULT 0
--       0 公开 / 1 全部登录会员 / 2 指定等级会员
--   · browse_level_ids   varchar(512) NULL DEFAULT ''
--       指定等级会员的 member_level.id 数组，逗号分隔；仅 type=2 有意义
--
-- 口径：存量商品全部落为 0（公开），不得因升级而消失（019 SC-201）。
-- 幂等：INFORMATION_SCHEMA 守卫；重复执行安全。
-- 上线顺序：本脚本 → 后端 → 管理端 → C 端回归（C 端无需发版）。
-- 回滚：确认无商品使用浏览等级后，删除两列即可（见文件末尾）。
-- ============================================================================

-- 1. browse_level_type
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND COLUMN_NAME = 'browse_level_type');
SET @s := IF(@c = 0,
  'ALTER TABLE `product_spu` ADD COLUMN `browse_level_type` tinyint NOT NULL DEFAULT 0 COMMENT ''浏览等级：0 公开 1 全部登录会员 2 指定等级会员'' AFTER `delivery_template_id`',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2. browse_level_ids
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product_spu' AND COLUMN_NAME = 'browse_level_ids');
SET @s := IF(@c = 0,
  'ALTER TABLE `product_spu` ADD COLUMN `browse_level_ids` varchar(512) NULL DEFAULT '''' COMMENT ''指定等级会员的会员等级编号数组，逗号分隔；仅 type=2 有意义'' AFTER `browse_level_type`',
  'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 验证（升级后应立即为 0）：
-- SELECT COUNT(*) FROM product_spu WHERE browse_level_type <> 0;
--
-- 回滚（仅在没有商品使用浏览等级时执行）：
-- ALTER TABLE `product_spu` DROP COLUMN `browse_level_ids`;
-- ALTER TABLE `product_spu` DROP COLUMN `browse_level_type`;
