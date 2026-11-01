-- ============================================================================
-- 028（014 印刷定制）—— 初始化材质 / 区间 / 计价参数
--
-- 数据来源（2026-11-01 用户提供的旧系统后台截图）：
--   · 材质与基价、打印系数、印刷系数：截图「材质管理」
--   · 四个初始价与加急系数：截图「系统参数」
--   · 面积区间：截图「材质价格配置」完整给出了「可移除铜版」的六档；
--     其余材质只露出零散行，这里先沿用同一套六档，运营按实际在后台调整。
--
-- ⚠️ 区间必须连续覆盖（Q100）：0 起、首尾相接、最后一条上界为 0（无上限）。
-- ⚠️ 幂等：材质按「租户 + 名称」判重；区间按「材质 + 下界」判重；参数按租户判重。
--
-- 回滚：
--   DELETE FROM trade_customization_area_rule WHERE tenant_id = 162 AND material_id IN
--     (SELECT id FROM (SELECT id FROM trade_customization_material WHERE tenant_id = 162) t);
--   DELETE FROM trade_customization_material WHERE tenant_id = 162;
--   DELETE FROM trade_customization_config WHERE tenant_id = 162;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 计价参数（截图：打印 200 / 印刷 600 / 模切 400 / 加急 50，系数 1.1）
-- ---------------------------------------------------------------------------
INSERT INTO `trade_customization_config`
  (`print_base_price`, `printing_base_price`, `die_cut_base_price`, `urgent_base_price`,
   `urgent_coefficient`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 20000, 60000, 40000, 5000, 1.1000,
       'system', NOW(), 'system', NOW(), b'0', 162
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `trade_customization_config` c
   WHERE c.tenant_id = 162 AND c.deleted = b'0'
);

-- ---------------------------------------------------------------------------
-- 2. 材质（截图「材质管理」：基价单位元/㎡，这里换算成分）
--    数量 / 宽高上下限截图未给，按标签印刷常见范围初始化：1~100000 张、10~2000 mm
-- ---------------------------------------------------------------------------
INSERT INTO `trade_customization_material`
  (`name`, `base_price`, `print_coefficient`, `printing_coefficient`,
   `min_quantity`, `max_quantity`, `min_width`, `max_width`, `min_height`, `max_height`,
   `status`, `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT t.name, t.base_price, t.print_coefficient, t.printing_coefficient,
       1, 100000, 10, 2000, 10, 2000,
       0, t.sort, 'system', NOW(), 'system', NOW(), b'0', 162
FROM (
  SELECT '可移除铜版' AS name, 1138 AS base_price, 4.0000 AS print_coefficient, 1.5000 AS printing_coefficient, 10 AS sort UNION ALL
  SELECT '可移除热敏', 1387, 2.0000, 1.1000, 20 UNION ALL
  SELECT '可移除合成', 1924, 4.0000, 1.5000, 30 UNION ALL
  SELECT '激光打印纸', 1284, 4.0000, 1.5000, 40 UNION ALL
  SELECT '优质热敏纸', 540, 4.0000, 1.0000, 50 UNION ALL
  -- 2026-11-01 补充：参考页面有、截图没有的两种（价格为占位，运营按实际调整）
  SELECT '高级热敏纸', 700, 4.0000, 1.0000, 25 UNION ALL
  SELECT '白色PET', 1320, 4.0000, 1.4000, 65 UNION ALL
  SELECT '透明PET', 1320, 4.0000, 1.4000, 60 UNION ALL
  SELECT '高级铜版纸', 800, 4.0000, 1.2000, 70 UNION ALL
  SELECT '合成纸', 1020, 4.0000, 1.2000, 80 UNION ALL
  SELECT '优质铜版纸', 500, 4.0000, 1.2000, 90 UNION ALL
  SELECT '哑银PET', 1560, 4.0000, 1.2000, 100
) t
WHERE NOT EXISTS (
  SELECT 1 FROM `trade_customization_material` m2
   WHERE m2.tenant_id = 162 AND m2.name = t.name AND m2.deleted = b'0'
);

-- ---------------------------------------------------------------------------
-- 3. 面积区间（截图只完整给出「可移除铜版」；其余材质先沿用同一套六档）
--    单位：㎡；max = 0 表示无上限
-- ---------------------------------------------------------------------------
INSERT INTO `trade_customization_area_rule`
  (`material_id`, `min_area`, `max_area`, `print_coefficient`, `die_cut_coefficient`,
   `sort`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT m.id, r.min_area, r.max_area, r.print_coefficient, r.die_cut_coefficient,
       r.sort, 'system', NOW(), 'system', NOW(), b'0', m.tenant_id
FROM `trade_customization_material` m
JOIN (
  SELECT '可移除铜版' AS name, 0 AS min_area, 10 AS max_area, 2.0000 AS print_coefficient, 1.0000 AS die_cut_coefficient, 0 AS sort UNION ALL
  SELECT '可移除铜版', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '可移除铜版', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '可移除铜版', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '可移除铜版', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '可移除铜版', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '可移除热敏', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '可移除热敏', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '可移除热敏', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '可移除热敏', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '可移除热敏', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '可移除热敏', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '可移除合成', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '可移除合成', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '可移除合成', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '可移除合成', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '可移除合成', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '可移除合成', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '激光打印纸', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '激光打印纸', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '激光打印纸', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '激光打印纸', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '激光打印纸', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '激光打印纸', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '优质热敏纸', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '优质热敏纸', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '优质热敏纸', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '优质热敏纸', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '优质热敏纸', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '优质热敏纸', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '透明PET', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '透明PET', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '透明PET', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '透明PET', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '透明PET', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '透明PET', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '高级铜版纸', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '高级铜版纸', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '高级铜版纸', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '高级铜版纸', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '高级铜版纸', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '高级铜版纸', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '合成纸', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '合成纸', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '合成纸', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '合成纸', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '合成纸', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '合成纸', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '优质铜版纸', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '优质铜版纸', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '优质铜版纸', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '优质铜版纸', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '优质铜版纸', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '优质铜版纸', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '哑银PET', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '哑银PET', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '哑银PET', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '哑银PET', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '哑银PET', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '哑银PET', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '高级热敏纸', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '高级热敏纸', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '高级热敏纸', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '高级热敏纸', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '高级热敏纸', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '高级热敏纸', 2000, 0, 1.4000, 0.0000, 5 UNION ALL

  SELECT '白色PET', 0, 10, 2.0000, 1.0000, 0 UNION ALL
  SELECT '白色PET', 10, 100, 1.8000, 1.0000, 1 UNION ALL
  SELECT '白色PET', 100, 200, 1.6000, 0.5000, 2 UNION ALL
  SELECT '白色PET', 200, 500, 1.6000, 0.0000, 3 UNION ALL
  SELECT '白色PET', 500, 2000, 1.5000, 0.0000, 4 UNION ALL
  SELECT '白色PET', 2000, 0, 1.4000, 0.0000, 5
) r ON r.name = m.name
WHERE m.tenant_id = 162 AND m.deleted = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `trade_customization_area_rule` r2
     WHERE r2.tenant_id = 162 AND r2.material_id = m.id
       AND r2.min_area = r.min_area AND r2.deleted = b'0'
  );

-- ---------------------------------------------------------------------------
-- 4. 材质说明（参考站点的文案；已存在的材质只补空值，幂等）
-- ---------------------------------------------------------------------------
UPDATE `trade_customization_material` SET `description` = '有热敏涂层的平滑亮白纸，适合物流标签、连续批量热敏打印与高覆盖率标签。'
 WHERE `tenant_id` = 162 AND `name` = '优质热敏纸' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '美国艾利有热敏涂层的平滑亮白纸，有 FASSON 背印，适合物流标签与批量热敏打印。'
 WHERE `tenant_id` = 162 AND `name` = '高级热敏纸' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '美国艾利法森牌铜版纸，有 FASSON 背印，适合工业半高光彩色标签印刷。'
 WHERE `tenant_id` = 162 AND `name` = '高级铜版纸' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '国产铜版纸，适用于空白模切与条码打印，适合小瓶体贴标和精细印刷。'
 WHERE `tenant_id` = 162 AND `name` = '优质铜版纸' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '适合标签打印及印刷的白色 PET 薄膜，耐撕裂、耐高温、尺寸稳定、不透光。'
 WHERE `tenant_id` = 162 AND `name` = '白色PET' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '适合标签打印及印刷的哑银色 PET 薄膜，印刷性能优秀，适合耐久性标签。'
 WHERE `tenant_id` = 162 AND `name` = '哑银PET' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '适用于热转印打印；印刷或条码打印后的标签应避免接触酒精等溶剂。'
 WHERE `tenant_id` = 162 AND `name` = '合成纸' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '适合标签打印及印刷的透明 PET 薄膜，印刷性能优秀，适合耐久性标签。'
 WHERE `tenant_id` = 162 AND `name` = '透明PET' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '专为激光打印设计，吸墨性能好，适用于 A4 单张打印与标价标签。'
 WHERE `tenant_id` = 162 AND `name` = '激光打印纸' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '可反复重贴的胶黏剂，模切与排废性能好，抗 UV。'
 WHERE `tenant_id` = 162 AND `name` = '可移除合成' AND (`description` IS NULL OR `description` = '');
UPDATE `trade_customization_material` SET `description` = '可移除胶黏剂的铜版纸，适合需要反复揭贴的标签。'
 WHERE `tenant_id` = 162 AND `name` = '可移除铜版' AND (`description` IS NULL OR `description` = '');
