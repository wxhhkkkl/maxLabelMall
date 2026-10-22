-- ============================================================================
-- 015 —— 运费模板 12「258包邮」补上长治市（140400）市级节点
--
-- 背景（2026-10-22 线上工单）：
--   会员 18613816876（id 719）的收货地址 area_id = 140400（长治市，市级节点），
--   而模板 12 只勾选了长治市下辖的 12 个区县（140403~140431），未勾市级节点。
--   结算时 TradeDeliveryPriceCalculator 按 areaId 精确匹配模板，找不到即抛
--   1011003001「计算快递运费异常，找不到对应的运费模板」。
--
-- 说明：
--   这是一个**数据兜底**，让存量市级地址也能结算；地址表单已在同批修改中
--   强制“城市有下级区县时必须选到区县”，避免新地址再写进市级节点。
--
-- 幂等：FIND_IN_SET 已存在时不重复追加。
-- ============================================================================

UPDATE `trade_delivery_express_template_charge`
SET `area_ids` = CONCAT(`area_ids`, ',140400')
WHERE `template_id` = 12
  AND `deleted` = b'0'
  AND FIND_IN_SET('140400', `area_ids`) = 0;

-- 自检：应返回 1
-- SELECT FIND_IN_SET('140400', `area_ids`) FROM `trade_delivery_express_template_charge`
--  WHERE `template_id` = 12 AND `deleted` = b'0';
