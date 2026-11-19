-- ============================================================================
-- 048 商品关联驱动/软件下载（2026-11-18）
--
-- 变更：`product_spu` 增加 `driver_software_id`，指向「软件分发」里的软件/驱动
--       （maxlabel_software.id）。运营在商品编辑「其他设置」里选择，
--       商品详情页显示「下载驱动 / 下载软件」按钮，直接下载该软件的**当前发布版本**。
--
-- 口径：
--   · 关联粒度是**软件**，不是某个安装包：运营上传新版本后，所有关联商品自动跟着更新
--   · 下载目标取该软件 current_flag = 1 且 status = 0（已发布）里**最新发布**的一条；
--     多平台/多架构时不会并列展示
--   · 软件停用 / 无当前发布 / 关联已删除时，商品页不显示下载入口（不报错）
--   · NULL = 不关联（存量商品默认为 NULL，商品页行为不变）
--
-- 先决条件：041-product-browse-level.sql 已执行（本脚本用 browse_level_ids 定位列）。
--
-- 上线顺序：先执行本脚本 → 再发后端 → 最后发管理端 / 门面站。
--
-- 回滚：
--   ALTER TABLE `product_spu` DROP COLUMN `driver_software_id`;
-- ============================================================================

ALTER TABLE `product_spu`
  ADD COLUMN `driver_software_id` bigint NULL DEFAULT NULL
    COMMENT '关联软件/驱动编号（maxlabel_software.id）；NULL = 商品详情页不显示下载入口'
    AFTER `browse_level_ids`;
