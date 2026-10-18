-- ============================================================================
-- 012 —— 对公转账（008）：建子表 + 交易配置加字段 + offline 渠道 + 渠道字典
--
-- ⚠️ 本项目自建脚本，**首次上线执行一次**；每一步都写了幂等保护（**可以重跑**）。
--
-- 为什么要它（specs/008-corporate-transfer）
-- ----------------------------------------------------------------------------
--   一部分客户是**对公打款**（企业网银/柜台转账），不走支付宝微信。本特性让他们能下单：
--   结算页选「对公转账」→ 订单进「待收款」→ 财务对着银行流水确认收款 → 进「待发货」。
--
--   四件事各自的理由：
--   ⓐ `trade_order_offline_payment` —— **一行同时表达两件事**：① 本单是不是对公转账
--      ② 财务凭哪笔银行流水确认的。⚠️ 为什么建子表而不是给 `trade_order` 加列 / 加订单状态值：
--      见 research 的 R42（`trade_order` 是全局最热的表、006 已立"建子表"先例；
--      而 `TradeOrderStatusEnum` 全仓 ~76 处引用，加状态值代价与收益不成比例）。
--   ⓑ `trade_config` 的 5 个字段 —— 收款账户信息**必须可配置**（FR-210），
--      MUST NOT 写死在代码里。⚠️ 户名/账号/开户行是**业务事实**，脚本**不填值**（留 NULL），
--      由所有者在后台「交易配置」里填 —— 宪法 FR-054~056 不得编造。
--   ⓒ `offline` 渠道 —— 退款会走 `PayRefundServiceImpl:100` 的 `getPayClient(channelId)`，
--      **没有渠道行，退款直接崩**（SC-073 要验的就是退款）。⚠️ 它挂的必须是
--      **tenant 162 那个启用中的 `mall` 应用** —— 库里有两个 app_key='mall' 的应用
--      （id=1 是上游种子的租户 1，id=10 才是本项目的），所以下面**按 status=0 动态取**，
--      不写死 id。
--   ⓓ 字典 `pay_channel_code` 加一条 —— 订单详情/列表按渠道取显示名，
--      少一条**订单上的渠道名就是空白**。
--
-- 幂等
-- ----------------------------------------------------------------------------
--   建表：`CREATE TABLE IF NOT EXISTS`
--   加列：先查 information_schema（`ADD COLUMN` 本身不幂等，同 009/010 的做法）
--   插数据：`INSERT ... SELECT ... WHERE NOT EXISTS`
--
-- 回滚
-- ----------------------------------------------------------------------------
--   DROP TABLE IF EXISTS `trade_order_offline_payment`;
--   ALTER TABLE `trade_config` DROP COLUMN `offline_pay_enabled`, DROP COLUMN `offline_pay_account_name`,
--         DROP COLUMN `offline_pay_account_no`, DROP COLUMN `offline_pay_bank_name`, DROP COLUMN `offline_pay_tip`;
--   UPDATE `pay_channel`     SET `deleted` = b'1' WHERE `code` = 'offline';
--   UPDATE `system_dict_data` SET `deleted` = b'1' WHERE `dict_type` = 'pay_channel_code' AND `value` = 'offline';
--   ⚠️ 回滚会丢掉**所有对公转账的确认痕迹**（谁按哪个流水号确认的），且对公单会失去"是不是对公"的判据。
--
-- 执行记录
-- ----------------------------------------------------------------------------
--   ⏳ 尚未执行。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- ⓐ 对公转账信息（与订单 1:1）
--
--    ⚠️ `confirm_time` 为空即"还没确认" —— **不另设状态列**，"有没有值"本身就是状态。
--    ⚠️ `bank_serial_no` 可为空：下单那一刻它本来就是空的；"确认收款时必填"写在服务层，
--       不在列约束里（写了的话下单就插不进来）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_order_offline_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_id` bigint UNSIGNED NOT NULL COMMENT '订单编号，关联 trade_order.id',
  `bank_serial_no` varchar(64) NULL DEFAULT NULL COMMENT '银行流水号（确认收款时必填）',
  `payer_name` varchar(128) NULL DEFAULT NULL COMMENT '打款方名称（选填）',
  `paid_time` datetime NULL DEFAULT NULL COMMENT '到账时间（选填）',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注（选填）',
  `confirm_user_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '确认人，关联 system_users.id',
  `confirm_time` datetime NULL DEFAULT NULL COMMENT '确认时间；为空即"还没确认"',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_id` (`order_id` ASC) USING BTREE
) COMMENT = '对公转账信息（订单的线下收款记录）';


-- ---------------------------------------------------------------------------
-- ⓑ `trade_config` 加 5 个字段（收款账户信息，FR-210）
-- ---------------------------------------------------------------------------
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'trade_config'
                  AND COLUMN_NAME = 'offline_pay_enabled');

SET @ddl := IF(@exists = 0,
    'ALTER TABLE `trade_config`
       ADD COLUMN `offline_pay_enabled` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否启用对公转账'',
       ADD COLUMN `offline_pay_account_name` varchar(128) NULL DEFAULT NULL COMMENT ''对公转账：收款户名'',
       ADD COLUMN `offline_pay_account_no` varchar(64) NULL DEFAULT NULL COMMENT ''对公转账：收款账号'',
       ADD COLUMN `offline_pay_bank_name` varchar(128) NULL DEFAULT NULL COMMENT ''对公转账：开户行'',
       ADD COLUMN `offline_pay_tip` varchar(255) NULL DEFAULT NULL COMMENT ''对公转账：给客户的提示''',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ---------------------------------------------------------------------------
-- ⓒ `pay_channel` 插 `offline` 渠道
--
--    ⚠️ **动态取应用**：`app_key='mall' AND status=0` —— 库里有两个 app_key='mall' 的应用
--       （id=1 属租户 1、id=10 属租户 162），只有**启用中的那个**是本项目在用的。
--       写死 id 会在别的环境插错租户。
--    ⚠️ `config` 用 `NonePayClientConfig`（offline 不发起任何三方调用，没有配置项），
--       类名必须是**当前代码里的包名** `cn.iocoder.yudao.module.pay.framework...`。
-- ---------------------------------------------------------------------------
INSERT INTO `pay_channel`
    (`code`, `status`, `fee_rate`, `remark`, `app_id`, `config`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 'offline', 0, 0.0, '对公转账（008）', a.`id`,
       '{"@class":"cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.NonePayClientConfig","name":"offline-conf"}',
       '1', NOW(), '1', NOW(), b'0', a.`tenant_id`
  FROM `pay_app` a
 WHERE a.`app_key` = 'mall'
   AND a.`status` = 0
   AND a.`deleted` = b'0'
   AND NOT EXISTS (SELECT 1 FROM `pay_channel` c
                    WHERE c.`code` = 'offline' AND c.`app_id` = a.`id` AND c.`deleted` = b'0');


-- ---------------------------------------------------------------------------
-- ⓓ 字典 `pay_channel_code` 加「对公转账」
-- ---------------------------------------------------------------------------
INSERT INTO `system_dict_data`
    (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`,
     `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 15, '对公转账', 'offline', 'pay_channel_code', 0, '', '', '', '1', NOW(), '1', NOW(), b'0'
 WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data`
                    WHERE `dict_type` = 'pay_channel_code' AND `value` = 'offline' AND `deleted` = b'0');


-- ---------------------------------------------------------------------------
-- 自检（**不拿"命令没报错"当成功**）
--
--   ① 子表在、列齐：
--      SHOW COLUMNS FROM `trade_order_offline_payment`;
--   ② trade_config 多了 5 列：
--      SHOW COLUMNS FROM `trade_config` LIKE 'offline_pay%';   -- 期望 5 行
--   ③ **恰好一行** offline 渠道，且挂在启用中的 mall 应用上：
--      SELECT c.id, c.code, c.app_id, c.tenant_id, a.app_key, a.status
--        FROM `pay_channel` c JOIN `pay_app` a ON a.id = c.app_id
--       WHERE c.code = 'offline' AND c.deleted = b'0';
--      ⚠️ 若这里返回 **2 行**，说明库里有**两个启用中的** `mall` 应用 —— 请人工删掉多余那条
--   ④ 字典那条在：
--      SELECT `value`, `label` FROM `system_dict_data`
--       WHERE `dict_type` = 'pay_channel_code' AND `value` = 'offline' AND `deleted` = b'0';
--
-- 上线顺序（**不能反**）
-- ----------------------------------------------------------------------------
--   1) 先跑本脚本
--   2) 再发后端（`PayChannelEnum.OFFLINE` / `OfflinePayClient` 都在里面）
--   3) 最后发两个前端
-- ---------------------------------------------------------------------------
