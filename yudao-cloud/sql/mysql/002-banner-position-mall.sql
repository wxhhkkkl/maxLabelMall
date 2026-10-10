-- ============================================================================
-- 002 —— 给 banner 位置字典加一个「商城页」取值
--
-- ⚠️ 本文件是**本项目自建**的迁移脚本，不是官方资产。
--    同目录下其它 `*.sql`（mall-*、pay-* 等）是从外部下载的模块 SQL，重建库时整套导入；
--    本文件只在**首次上线时执行一次**。重建库时不要把它当成官方资产一并导入
--    （会插重复行；不过下面写了幂等保护，重复执行也不会出第二行）。
--
-- 为什么要它：
--   后端 `BannerPositionEnum` 新增了 `MALL_POSITION(6, "商城页")`（本项目自加），
--   而管理端「商城系统 → 营销中心 → 内容管理 → Banner」的「位置」是**字典驱动的单选**
--   （`getIntDictOptions(DICT_TYPE.PROMOTION_BANNER_POSITION)`）。
--   字典里没有这一行，运营在后台就**选不到**「商城页」。
--
--   两边必须**成对存在**：
--     · 只有枚举、没有字典 → 运营选不到；
--     · 只有字典、没有枚举 → 提交被 `@InEnum(BannerPositionEnum.class)` **400 拒绝**。
--
-- 回滚：DELETE FROM `system_dict_data`
--       WHERE `dict_type` = 'promotion_banner_position' AND `value` = '6';
-- ============================================================================

INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`,
     `remark`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 0, '商城页', '6', 'promotion_banner_position', 0, 'warning', '',
       '本项目新增：商城页（商品列表页）顶部横幅', '1', NOW(), '1', NOW(), b'0'
FROM (SELECT 1) AS one_row
WHERE NOT EXISTS (
    -- 包一层派生表：MySQL 不允许在子查询里直接引用 INSERT 的目标表
    SELECT 1 FROM (
        SELECT `id` FROM `system_dict_data`
        WHERE `dict_type` = 'promotion_banner_position' AND `value` = '6'
    ) AS existed
);
