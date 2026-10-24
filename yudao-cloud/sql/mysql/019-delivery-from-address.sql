-- ============================================================================
-- 019 —— trade_order 加「发货地址」两列（010 追加）
--
-- 背景：物流地图（快递100 maptrack.do）必须同时有 from（发货地址）和 to（收件地址）。
-- 收件地址能由 receiver_area_id 解析；发货地址此前**没有任何落点**。
--
-- 设计：
--   · 后台发货表单填写「省市区 + 详细地址」，默认值来自
--     `yudao.trade.express.kd100.default-from-area-id / default-from-address`
--   · `delivery_from_area_id` 供地图解析省市区；`delivery_from_address` 留档/展示
--   · 历史订单没有这两列值 → 地图回退到配置的默认发货仓
--
-- 幂等：INFORMATION_SCHEMA 判断列是否存在；重复执行安全。
-- ============================================================================

SET @c1 := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'delivery_from_area_id'
);
SET @d1 := IF(@c1 = 0,
    'ALTER TABLE `trade_order` ADD COLUMN `delivery_from_area_id` int NULL DEFAULT NULL COMMENT ''发货地址地区编号''',
    'SELECT 1');
PREPARE s1 FROM @d1;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @c2 := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'delivery_from_address'
);
SET @d2 := IF(@c2 = 0,
    'ALTER TABLE `trade_order` ADD COLUMN `delivery_from_address` varchar(255) NULL DEFAULT NULL COMMENT ''发货地址详情''',
    'SELECT 1');
PREPARE s2 FROM @d2;
EXECUTE s2;
DEALLOCATE PREPARE s2;

-- 核对：两列都存在
SELECT `COLUMN_NAME`, `COLUMN_TYPE`, `IS_NULLABLE`
FROM `information_schema`.`COLUMNS`
WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = 'trade_order'
  AND `COLUMN_NAME` IN ('delivery_from_area_id', 'delivery_from_address')
ORDER BY `COLUMN_NAME`;
