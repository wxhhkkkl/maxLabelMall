-- ============================================================================
-- 027（014 印刷定制下单）
--
-- 内容：4 张新表 + `trade_order.customization` 列与索引 + 隐藏 SPU/SKU + 菜单/权限。
-- 幂等：表 IF NOT EXISTS；加列/索引查 information_schema；隐藏商品与菜单 WHERE NOT EXISTS。
--
-- 回查：
--   SELECT table_name FROM information_schema.tables
--    WHERE table_schema = DATABASE()
--      AND table_name IN ('trade_customization_material','trade_customization_area_rule',
--                         'trade_customization_config','trade_customization_order');
--   SHOW COLUMNS FROM trade_order LIKE 'customization';
--   SELECT id, name, status FROM product_spu WHERE id = 990001;
--   SELECT id, name, permission, component FROM system_menu WHERE permission LIKE 'trade:customization-%';
--
-- 回滚（按依赖顺序）：
--   DELETE FROM system_menu WHERE permission LIKE 'trade:customization-%';
--   DELETE FROM product_sku WHERE id = 990002;
--   DELETE FROM product_spu WHERE id = 990001;
--   ALTER TABLE `trade_order` DROP INDEX `idx_tenant_customization`;
--   ALTER TABLE `trade_order` DROP COLUMN `customization`;
--   DROP TABLE IF EXISTS trade_customization_order;
--   DROP TABLE IF EXISTS trade_customization_config;
--   DROP TABLE IF EXISTS trade_customization_area_rule;
--   DROP TABLE IF EXISTS trade_customization_material;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 定制材质
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_customization_material` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(64) NOT NULL COMMENT '材质名称',
  `description` varchar(1000) NULL DEFAULT '' COMMENT '材质说明（展示给客户）',
  `base_price` int NOT NULL COMMENT '材质基价，单位：分/㎡',
  `print_coefficient` decimal(10,4) NOT NULL COMMENT '材质打印系数',
  `printing_coefficient` decimal(10,4) NOT NULL COMMENT '印刷系数',
  `min_quantity` int NOT NULL COMMENT '数量下限',
  `max_quantity` int NOT NULL COMMENT '数量上限',
  `min_width` int NOT NULL COMMENT '成品宽度下限，mm',
  `max_width` int NOT NULL COMMENT '成品宽度上限，mm',
  `min_height` int NOT NULL COMMENT '成品高度下限，mm',
  `max_height` int NOT NULL COMMENT '成品高度上限，mm',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_status`(`tenant_id` ASC, `status` ASC, `sort` ASC) USING BTREE
) COMMENT = '定制印刷材质';

-- ---------------------------------------------------------------------------
-- 2. 面积区间规则
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_customization_area_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `material_id` bigint NOT NULL COMMENT '材质编号',
  `min_area` decimal(12,4) NOT NULL COMMENT '区间下界（含），㎡',
  `max_area` decimal(12,4) NOT NULL COMMENT '区间上界（不含），㎡；0 表示无上限',
  `print_coefficient` decimal(10,4) NOT NULL COMMENT '区间打印系数',
  `die_cut_coefficient` decimal(10,4) NOT NULL COMMENT '模切系数；0 表示不收按面积费、仍收初始价',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序（升序）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_material`(`tenant_id` ASC, `material_id` ASC, `min_area` ASC) USING BTREE
) COMMENT = '定制印刷面积区间规则';

-- ---------------------------------------------------------------------------
-- 3. 全局计价参数（每租户一行）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_customization_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `print_base_price` int NOT NULL COMMENT '打印初始价，分',
  `printing_base_price` int NOT NULL COMMENT '印刷初始价，分',
  `die_cut_base_price` int NOT NULL COMMENT '模切初始价，分',
  `urgent_base_price` int NOT NULL COMMENT '加急初始价，分',
  `urgent_coefficient` decimal(10,4) NOT NULL COMMENT '加急系数',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_tenant`(`tenant_id`) USING BTREE
) COMMENT = '定制印刷计价参数';

-- ---------------------------------------------------------------------------
-- 4. 定制订单扩展（与 trade_order 1:1）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_customization_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_id` bigint NOT NULL COMMENT '交易订单编号',
  `material_id` bigint NOT NULL COMMENT '材质编号',
  `material_name` varchar(64) NOT NULL COMMENT '材质名称快照',
  `width` int NOT NULL COMMENT '成品宽度，mm',
  `height` int NOT NULL COMMENT '成品高度，mm',
  `quantity` int NOT NULL COMMENT '定制数量',
  `label_count` int NULL DEFAULT 0 COMMENT '实际数量',
  `template_name` varchar(128) NULL DEFAULT '' COMMENT 'LabelShop 模板名称（选填）',
  `unit_area` decimal(12,4) NOT NULL COMMENT '单件面积，㎡',
  `total_area` decimal(12,4) NOT NULL COMMENT '整单总面积，㎡',
  `mode` tinyint NOT NULL DEFAULT 2 COMMENT '业务模式：1 印刷（多色） 2 打印（黑色） 3 两者 4 空白标签',
  `form` tinyint NOT NULL DEFAULT 2 COMMENT '成品形态：1 卷筒（卷轴） 2 平张（单张）',
  `sheet_spec` varchar(32) NULL DEFAULT '' COMMENT '整张规格：A4 / 其他',
  `die_cut` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否模切',
  `finishing` tinyint NULL DEFAULT NULL COMMENT '边角：1 圆角 2 直角',
  `tear_line` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否撕裂线',
  `row_count` int NULL DEFAULT 1 COMMENT '每张行数',
  `column_count` int NULL DEFAULT 1 COMMENT '每张列数',
  `row_gap` decimal(6,2) NULL DEFAULT 0 COMMENT '行间距，mm',
  `column_gap` decimal(6,2) NULL DEFAULT 0 COMMENT '列间距，mm',
  `axis_diameter` varchar(16) NULL DEFAULT '' COMMENT '轴芯内径（卷筒）',
  `urgent` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否加急',
  `remark` varchar(500) NULL DEFAULT '' COMMENT '客户备注',
  `price_breakdown` json NOT NULL COMMENT '价格明细（分）',
  `param_snapshot` json NOT NULL COMMENT '下单时的参数快照',
  `files` json NOT NULL COMMENT '设计文件数组',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_order`(`tenant_id` ASC, `order_id` ASC) USING BTREE,
  INDEX `idx_tenant_material`(`tenant_id` ASC, `material_id` ASC, `create_time` ASC) USING BTREE
) COMMENT = '定制印刷订单扩展';

-- ---------------------------------------------------------------------------
-- 5. trade_order 增加 customization 列与索引（幂等）
-- ---------------------------------------------------------------------------
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND column_name = 'customization'
);
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE `trade_order` ADD COLUMN `customization` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否定制订单'' AFTER `maxlabel_subscription`',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE() AND table_name = 'trade_order' AND index_name = 'idx_tenant_customization'
);
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE `trade_order` ADD INDEX `idx_tenant_customization`(`tenant_id` ASC, `customization` ASC, `status` ASC)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 6. 隐藏 SPU / SKU（订单项必须指向真实 SKU；见 data-model §5）
-- ---------------------------------------------------------------------------
INSERT INTO `product_spu`
  (`id`, `name`, `keyword`, `introduction`, `description`, `category_id`, `brand_id`, `pic_url`,
   `slider_pic_urls`, `sort`, `status`, `spec_type`, `price`, `market_price`, `cost_price`, `stock`,
   `delivery_types`, `delivery_template_id`, `give_integral`, `enterprise_only`, `sub_commission_type`,
   `sales_count`, `virtual_sales_count`, `browse_count`,
   `create_time`, `update_time`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 990001, '印刷定制服务（系统）', NULL, '系统预置，不可直接购买', NULL, 0, NULL, '', '', 0, 1, b'0',
       0, NULL, 0, 1000000000, '1',
       (SELECT id FROM `trade_delivery_express_template`
         WHERE tenant_id = 162 AND deleted = b'0' ORDER BY id LIMIT 1),
       0, b'1', NULL, 0, 0, 0, NOW(), NOW(), 'system', 'system', b'0', 162
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM `product_spu` WHERE id = 990001) AS existed);

INSERT INTO `product_sku`
  (`id`, `spu_id`, `properties`, `price`, `market_price`, `cost_price`, `bar_code`, `pic_url`,
   `stock`, `weight`, `volume`, `first_brokerage_price`, `second_brokerage_price`, `sales_count`,
   `create_time`, `update_time`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 990002, 990001, '[]', 0, NULL, 0, NULL, '', 1000000000, 0, 0, 0, 0, 0,
       NOW(), NOW(), 'system', 'system', b'0', 162
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM (SELECT id FROM `product_sku` WHERE id = 990002) AS existed);

-- ---------------------------------------------------------------------------
-- 7. 菜单与权限（顶层目录 + 材质管理 + 计价参数 + 按钮）
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '印刷定制', '', 1, 31, 0, '/customization', 'ep:printer', NULL,
       NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT id FROM `system_menu` WHERE path = '/customization' AND parent_id = 0 AND deleted = b'0') AS existed
);

SET @group_id = (
  SELECT id FROM `system_menu`
   WHERE path = '/customization' AND parent_id = 0 AND deleted = b'0'
   ORDER BY id LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '材质管理', 'trade:customization-material:query', 2, 1, @group_id, 'material', 'ep:files',
       'mall/customization/material/index', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT id FROM `system_menu` WHERE permission = 'trade:customization-material:query' AND deleted = b'0') AS existed
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '计价参数', 'trade:customization-config:query', 2, 2, @group_id, 'config', 'ep:set-up',
       'mall/customization/config/index', NULL, 0, b'1', b'1', b'1', '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT id FROM `system_menu` WHERE permission = 'trade:customization-config:query' AND deleted = b'0') AS existed
);

-- 按钮权限（type=3）
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT t.name, t.permission, 3, 1, @group_id, '', '', NULL, NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM (
  SELECT '新增材质' AS name, 'trade:customization-material:create' AS permission UNION ALL
  SELECT '编辑材质', 'trade:customization-material:update' UNION ALL
  SELECT '删除材质', 'trade:customization-material:delete' UNION ALL
  SELECT '保存参数', 'trade:customization-config:update'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT id FROM `system_menu` WHERE permission = t.permission AND deleted = b'0') AS existed
);

-- ---------------------------------------------------------------------------
-- 8. 可选：按截图初始化材质与区间（示例数据，按需执行 / 修改）
--    这里不插入业务数据，避免把示例价格带进生产；由运营在后台录入。
-- ---------------------------------------------------------------------------
