-- ============================================================================
-- 043（021 客户服务：投诉咨询与在线维修）
--
-- 内容：5 张新表 + 菜单/按钮权限。
-- 幂等：表 IF NOT EXISTS；菜单/按钮 WHERE NOT EXISTS。
--
-- 回查：
--   SELECT table_name FROM information_schema.tables
--    WHERE table_schema = DATABASE()
--      AND table_name IN ('trade_customer_service_ticket','trade_repair_request',
--                         'trade_repair_order','trade_customer_service_log',
--                         'trade_customer_service_address');
--   SELECT id, name, permission, component FROM system_menu
--    WHERE permission LIKE 'trade:customer-service:%';
--
-- 回滚（按依赖顺序）：
--   DELETE FROM system_menu WHERE permission LIKE 'trade:customer-service:%';
--   DROP TABLE IF EXISTS trade_customer_service_address;
--   DROP TABLE IF EXISTS trade_customer_service_log;
--   DROP TABLE IF EXISTS trade_repair_order;
--   DROP TABLE IF EXISTS trade_repair_request;
--   DROP TABLE IF EXISTS trade_customer_service_ticket;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 投诉咨询
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_customer_service_ticket` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_id` bigint NOT NULL COMMENT '会员编号',
  `type` tinyint NOT NULL COMMENT '诉求类型：1 投诉 2 咨询',
  `content` varchar(1000) NOT NULL COMMENT '内容',
  `contact_mobile` varchar(20) NOT NULL COMMENT '联系手机号',
  `images` varchar(2000) NULL DEFAULT NULL COMMENT '图片 URL JSON 数组（≤5 张）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 待处理 10 已处理',
  `handle_remark` varchar(500) NULL DEFAULT NULL COMMENT '处理说明',
  `handler_id` bigint NULL DEFAULT NULL COMMENT '处理人（system_users.id）',
  `handle_time` datetime NULL DEFAULT NULL COMMENT '处理时间',
  `source` tinyint NOT NULL DEFAULT 1 COMMENT '来源：1 客户自助 2 客服代建',
  `creator_id` bigint NULL DEFAULT NULL COMMENT '创建人（会员或管理员编号）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_ticket_member`(`tenant_id` ASC, `member_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_ticket_status`(`tenant_id` ASC, `status` ASC, `create_time` ASC) USING BTREE
) COMMENT = '客户服务投诉咨询';

-- ---------------------------------------------------------------------------
-- 2. 维修申请（一台设备一行）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_repair_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_id` bigint NOT NULL COMMENT '会员编号',
  `device_model` varchar(100) NOT NULL COMMENT '设备型号',
  `fault_desc` varchar(1000) NOT NULL COMMENT '故障描述',
  `contact_mobile` varchar(20) NOT NULL COMMENT '联系手机号',
  `receiver_name` varchar(50) NOT NULL COMMENT '客户返修收件人',
  `receiver_mobile` varchar(20) NOT NULL COMMENT '客户返修电话',
  `receiver_area` varchar(100) NOT NULL COMMENT '客户返修省市区',
  `receiver_detail` varchar(255) NOT NULL COMMENT '客户返修详细地址',
  `images` varchar(2000) NULL DEFAULT NULL COMMENT '图片 URL JSON 数组（≤5 张）',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 待联系 10 已联系 20 已开单 30 已寄出 40 维修中 50 已完成 60 已取消',
  `contact_time` datetime NULL DEFAULT NULL COMMENT '电话联系时间',
  `contact_result` varchar(500) NULL DEFAULT NULL COMMENT '联系结果说明',
  `ship_company` varchar(50) NULL DEFAULT NULL COMMENT '客户寄出快递公司',
  `ship_no` varchar(64) NULL DEFAULT NULL COMMENT '客户寄出运单号',
  `ship_time` datetime NULL DEFAULT NULL COMMENT '客户标记寄出时间',
  `source` tinyint NOT NULL DEFAULT 1 COMMENT '来源：1 客户自助 2 客服代建',
  `creator_id` bigint NULL DEFAULT NULL COMMENT '创建人（会员或管理员编号）',
  `cancel_reason` varchar(255) NULL DEFAULT NULL COMMENT '取消原因',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_repair_member`(`tenant_id` ASC, `member_id` ASC, `create_time` ASC) USING BTREE,
  INDEX `idx_repair_status`(`tenant_id` ASC, `status` ASC, `create_time` ASC) USING BTREE
) COMMENT = '客户维修申请';

-- ---------------------------------------------------------------------------
-- 3. 维修订单（一个维修申请最多一笔）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_repair_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `repair_request_id` bigint NOT NULL COMMENT '维修申请编号',
  `member_id` bigint NOT NULL COMMENT '会员编号',
  `repair_fee` int NOT NULL DEFAULT 0 COMMENT '维修费，分',
  `return_freight` int NOT NULL DEFAULT 0 COMMENT '寄回运费，分',
  `payable_amount` int NOT NULL COMMENT '应付合计，分',
  `fee_remark` varchar(500) NOT NULL COMMENT '费用说明',
  `pay_status` tinyint NOT NULL DEFAULT 0 COMMENT '支付状态：0 待支付 10 待确认收款 20 已支付 30 无需支付 40 已取消',
  `pay_channel_code` varchar(32) NULL DEFAULT NULL COMMENT '支付渠道',
  `pay_order_id` bigint NULL DEFAULT NULL COMMENT '支付单号',
  `pay_time` datetime NULL DEFAULT NULL COMMENT '支付成功/确认收款时间',
  `offline_confirm_user_id` bigint NULL DEFAULT NULL COMMENT '对公转账确认人',
  `offline_confirm_time` datetime NULL DEFAULT NULL COMMENT '对公转账确认时间',
  `creator_id` bigint NOT NULL COMMENT '开单管理员',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_repair_order_request`(`tenant_id` ASC, `repair_request_id` ASC) USING BTREE
) COMMENT = '客户维修订单';

-- ---------------------------------------------------------------------------
-- 4. 流转记录（只追加）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_customer_service_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `target_type` tinyint NOT NULL COMMENT '目标类型：1 投诉咨询 2 维修申请 3 维修订单',
  `target_id` bigint NOT NULL COMMENT '目标编号',
  `action` varchar(32) NOT NULL COMMENT '动作',
  `from_status` tinyint NULL DEFAULT NULL COMMENT '变更前状态',
  `to_status` tinyint NULL DEFAULT NULL COMMENT '变更后状态',
  `remark` varchar(500) NULL DEFAULT NULL COMMENT '说明（客户可见）',
  `operator_type` tinyint NOT NULL DEFAULT 3 COMMENT '操作人类型：1 会员 2 管理员 3 系统',
  `operator_id` bigint NULL DEFAULT NULL COMMENT '操作人编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_log_target`(`tenant_id` ASC, `target_type` ASC, `target_id` ASC, `id` ASC) USING BTREE
) COMMENT = '客户服务流转记录';

-- ---------------------------------------------------------------------------
-- 5. 回收邮寄地址（每租户一条，由服务层保证）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_customer_service_address` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `receiver_name` varchar(50) NOT NULL COMMENT '收件人',
  `receiver_mobile` varchar(20) NOT NULL COMMENT '联系电话',
  `area_name` varchar(100) NOT NULL COMMENT '省市区',
  `detail_address` varchar(255) NOT NULL COMMENT '详细地址',
  `ship_remark` varchar(500) NULL DEFAULT NULL COMMENT '寄件说明',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_address_tenant`(`tenant_id` ASC, `deleted` ASC) USING BTREE
) COMMENT = '客户服务回收邮寄地址';

-- ---------------------------------------------------------------------------
-- 6. 菜单：客户服务（顶层目录 + 服务工单页面 + 处理按钮）
-- ---------------------------------------------------------------------------
INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '客户服务', '', 1, 70, 0, '/customer-service', 'ep:service', NULL,
       NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `path` = '/customer-service' AND `parent_id` = 0) AS existed
);

SET @customer_service_group_id = (
  SELECT `id` FROM `system_menu`
   WHERE `path` = '/customer-service' AND `parent_id` = 0 AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '服务工单', 'trade:customer-service:query', 2, 10, @customer_service_group_id,
       'ticket', 'ep:headset', 'trade/customer-service/index',
       'TradeCustomerService', 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'trade:customer-service:query') AS existed
);

SET @customer_service_page_id = (
  SELECT `id` FROM `system_menu`
   WHERE `permission` = 'trade:customer-service:query' AND `deleted` = b'0'
   ORDER BY `id` LIMIT 1
);

INSERT INTO `system_menu`
  (`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`,
   `component_name`, `status`, `visible`, `keep_alive`, `always_show`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '处理工单', 'trade:customer-service:handle', 3, 1, @customer_service_page_id,
       '', '', '', NULL, 0, b'1', b'1', b'1',
       '', NOW(), '', NOW(), b'0'
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM (SELECT `id` FROM `system_menu` WHERE `permission` = 'trade:customer-service:handle') AS existed
);
