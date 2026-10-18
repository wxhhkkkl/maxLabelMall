-- ============================================================================
-- 007 —— 订单开票（开票申请 / 发票抬头簿）
--
-- ⚠️ 本文件是**本项目自建**的迁移脚本，不是官方资产（同 002~006）。
--    同目录下其它 `*.sql`（mall-*、member-* 等）是从外部下载的模块 SQL，重建库时整套导入；
--    本文件只在**首次上线时执行一次**（下面写了 `IF NOT EXISTS` 幂等保护）。
--
-- 为什么要它（specs/006-order-invoice）
-- ----------------------------------------------------------------------------
--   这个商城此前**没有任何发票能力**：客户下单无处填抬头、订单不存开票信息、后台没有开票入口、
--   客户付完款也看不到自己有没有发票（全仓搜 `invoice` / `发票` 零命中）。
--   实际流程是客户打电话给张经理，人工开票后寄出或发文件，**系统里不留任何记录**。
--
--   本次要落的是**两张表**：
--     ① `trade_order_invoice` —— 一笔订单的开票申请（**1:1** 挂订单），承载票种、抬头快照、
--        状态与开好的发票文件；
--     ② `trade_invoice_title` —— 会员维护的**发票抬头簿**（多条 + 一条默认）。
--
-- 为什么新建表而不是往 `trade_order` 加列
-- ----------------------------------------------------------------------------
--   `trade_order` 是全局最热的表（详情 / 列表 / 售后 / 统计都查它），已有 40+ 列。
--   把这 6~8 个字段平铺进去，会让每一条订单查询都多背一份与它无关的数据；
--   而"未申请开票"在**子表**上就是"没有行"，语义比一堆 NULL 强得多 ——
--   顺带让 FR-179（上线前的历史订单视为未申请）**一行数据都不用补**。
--
-- 与测试用建表脚本的关系
-- ----------------------------------------------------------------------------
--   服务端单测跑在 H2 上，用的是
--   `yudao-module-trade-server/src/test/resources/sql/create_tables.sql`。
--   那份**必须同步这两张表**（任务 T373），否则本模块的测试根本起不来。
--   清理脚本 `sql/clean.sql` 也要一起补（T373），否则用例之间会互相污染。
--
-- 回滚
-- ----------------------------------------------------------------------------
--   DROP TABLE IF EXISTS `trade_order_invoice`;
--   DROP TABLE IF EXISTS `trade_invoice_title`;
--   ⚠️ 会把客户已提交的开票申请与抬头簿**全部丢掉**，执行前先想清楚（已开好的发票文件还在对象存储里）。
--
-- 配套脚本
-- ----------------------------------------------------------------------------
--   菜单与权限点是**另一份**：`008-order-invoice-menus.sql`（漏跑的症状是"接口能调、按钮不显示，
--   连超管也一样"，见 004 的踩坑记录）。
--
-- 执行记录
-- ----------------------------------------------------------------------------
--   ✅ **2026-10-15 已在生产库（ruoyi-vue-pro）执行**，走 `scripts/apply_003_filter_dimension.py
--      --file 007-order-invoice.sql`（经应用机 SSH 隧道）。执行后回查：
--        trade_order_invoice 22 列 / trade_invoice_title 14 列，两表均 0 行；
--        `uk_order_id` 唯一索引、`idx_user_id`、`idx_status` 都在。
--      ⚠️ 当时**后端与两个前端的代码尚未发版** —— 表建好了但没人写它，是正常的中间态。
-- ============================================================================


-- ---------------------------------------------------------------------------
-- 1. 开票申请：一笔订单一条（`uk_order_id`），下单时或订单详情补开时产生。
--
--    状态机（只三态，**没有"待支付"**）：
--        待开票(0) --后台开具并上传--> 已开票(10)
--        待开票(0) --订单取消 / 全额退款取消--> 已作废(20)   [终态]
--        已开票(10) --重新上传--> 已开票(10)（覆盖文件）
--    "订单是否已支付"**不在这张表里存第二份**，由订单裁决：后台「待开票」列表的分页
--    join `trade_order` 并带 `pay_status = 1` 条件（否则会出现"两份真相"，且支付路径一旦绕过
--    埋点就会静默停在错误状态）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_order_invoice` (
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '申请编号',
    `order_id`       bigint       NOT NULL COMMENT '订单编号（关联 trade_order.id，一单一申请）',
    `order_no`       varchar(32)  NOT NULL COMMENT '订单号（下单时抄一份，仅用于展示与排查）',
    `user_id`        bigint       NOT NULL COMMENT '会员编号（关联 member_user.id）',
    `type`           tinyint      NOT NULL COMMENT '发票类型：1 电子普通发票，2 电子增值税专用发票',
    `status`         int          NOT NULL DEFAULT 0 COMMENT '状态：0 待开票，10 已开票，20 已作废',
    `amount`         int          NOT NULL COMMENT '开票金额，单位：分（= 申请时的订单实付金额快照）',
    `title_name`     varchar(255) NOT NULL COMMENT '抬头名称（公司名或个人名）',
    `tax_no`         varchar(64)  DEFAULT NULL COMMENT '纳税人识别号（专票必填，普票选填）',
    `title_address`  varchar(255) DEFAULT NULL COMMENT '注册地址（专票必填）',
    `title_phone`    varchar(64)  DEFAULT NULL COMMENT '注册电话（专票必填）',
    `bank_name`      varchar(255) DEFAULT NULL COMMENT '开户行及账号（专票必填）',
    `invoice_file_url` varchar(255) DEFAULT NULL COMMENT '开好的发票文件地址（只留最新一份）',
    `issue_time`     datetime     DEFAULT NULL COMMENT '开票时间',
    `issue_user_id`  bigint       DEFAULT NULL COMMENT '开具人（后台操作者，关联 system_users.id）',
    `cancel_time`    datetime     DEFAULT NULL COMMENT '作废时间',
    `creator`        varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除，BaseDO 约定）',
    `tenant_id`      bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    -- ⚠️ 本表**刻意**保留唯一索引（与 003 的三张表相反，那几张因逻辑删除而不敢加）：
    --    这里**没有任何逻辑删除开票申请的路径** —— 作废走的是 `status = 20`，不是 `deleted`。
    --    所以 deleted=1 与同一 order_id 再插入**不会**同时出现，唯一索引不会误伤。
    --    它挡的是**并发**：客户连点两次"申请开票"时，服务层的"尚无申请"检查会双双通过，
    --    只有数据库这条唯一约束能保证 FR-160（一个订单最多一条申请）。
    --    将来若真要"删除"申请，请改成作废（status），别用 deleted —— 否则这条索引会开始报错。
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_user_id` (`user_id`),
    -- 后台「待开票」列表按它过滤（join trade_order 再补 pay_status 条件）
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '交易订单开票申请';


-- ---------------------------------------------------------------------------
-- 2. 发票抬头簿：会员自己维护，可存多条，其中一条可设默认。
--
--    ⚠️ **没有票种列** —— 一条抬头**两种票都能用**：开普票只用到「名称 + 税号」，
--       开专票要求五列全有。所以抬头本身不绑票种，**缺项在选专票时才被拒**
--       （校验在服务端 `InvoiceValidator`，按票种判必填）。
--       反过来说：只开普票的客户**不该被迫**填开户行，所以五列除名称外都可空。
--
--    默认抬头的唯一性**在服务层保证**（设新的默认时清掉旧的），不加数据库唯一约束：
--    "每个会员至多一条默认、且可以一条都没有"这种约束 MySQL 的部分索引表达不了
--    ——与 `member_address` 的既有做法一致。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `trade_invoice_title` (
    `id`             bigint       NOT NULL AUTO_INCREMENT COMMENT '抬头编号',
    `user_id`        bigint       NOT NULL COMMENT '会员编号（关联 member_user.id）',
    `title_name`     varchar(255) NOT NULL COMMENT '抬头名称（公司名或个人名）',
    `tax_no`         varchar(64)  DEFAULT NULL COMMENT '纳税人识别号',
    `title_address`  varchar(255) DEFAULT NULL COMMENT '注册地址',
    `title_phone`    varchar(64)  DEFAULT NULL COMMENT '注册电话',
    `bank_name`      varchar(255) DEFAULT NULL COMMENT '开户行及账号',
    `default_status` bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否默认',
    `creator`        varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`        varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`        bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除，BaseDO 约定）',
    `tenant_id`      bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '会员发票抬头';
