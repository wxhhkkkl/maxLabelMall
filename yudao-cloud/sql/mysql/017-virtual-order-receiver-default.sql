-- ============================================================================
-- 017 —— 给 trade_order 的收件人两列补默认值：虚拟商品单本来就没有收件人
--
-- 现象（2026-10-22 客户在 /checkout 提交订单报「系统异常」）
-- ----------------------------------------------------------------------------
--   `POST /app-api/trade/order/create` 连续 4 次失败，infra_api_error_log 里的
--   根因是：
--     DataIntegrityViolationException:
--       Field 'receiver_name' doesn't have a default value
--
--   客户买的是**虚拟商品**（`product_spu.delivery_types = '3'`，软件授权 1 年）。
--   按 007 的设计，整单虚拟的订单**不要收货地址**，
--   `TradeOrderUpdateServiceImpl#buildTradeOrder` 只给「快递」「自提」两个分支
--   填 receiver*，虚拟单两个分支都不进 —— receiver* 保持 null。
--
--   而这两列是 `NOT NULL` 且**没有默认值**。MyBatis-Plus 的 insert 默认策略是
--   NOT_NULL，即 null 字段**整个不写进 INSERT**，于是 MySQL 报"该列没有默认值"，
--   而不是"该列不能为 NULL"。异常没人接，兜底 handler 返回 500「系统异常」。
--
--   旁证：线上 trade_order 有 731 笔快递单、14 笔自提单、**0 笔虚拟单** ——
--   虚拟商品从上线起就没有一单能下成功。
--
-- 为什么是 DEFAULT '' 而不是改成 NULL
-- ----------------------------------------------------------------------------
--   · 移动的是"空"的表示法，不是可空性：列继续 NOT NULL，既有查询/导出逻辑
--     不用跟着改成判 null（`receiverName.trim()` 这类调用不会 NPE）。
--   · 007 的 data-model 写的是「不改 trade_order 的结构」，这里也不是加列/改语义，
--     只是承认"虚拟单没有收件人"这个既成事实。
--   · 前台/后台对空收件人的判定都是**按配送方式**走的（虚拟单显示「买家」），
--     不依赖 receiver_name 是 '' 还是 null。
--
-- 配套 / 重建库
-- ----------------------------------------------------------------------------
--   · 本文件只改默认值，**没有代码改动**：执行完即可下单，无需重启应用。
--   · `mall-2026-04-18.sql`（外部下载的官方资产）里这两列仍是
--     `NOT NULL` 无默认值 —— 与 005 同例，**不去改它**；重建库后要补跑本文件。
--   · 测试库的 H2 建表脚本（`trade-server/src/test/resources/sql/create_tables.sql`）
--     已同步成 `DEFAULT ''`，否则将来写"虚拟单建单"的集成测试会撞同一个错。
--
-- 幂等 / 回滚
-- ----------------------------------------------------------------------------
--   幂等：MODIFY 到同一个定义重复执行不改语义，可安全重跑。
--   回滚：去掉 DEFAULT '' 即可 —— 但**回滚后虚拟商品仍然下不了单**。
-- ============================================================================

ALTER TABLE `trade_order`
    MODIFY COLUMN `receiver_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        NOT NULL DEFAULT '' COMMENT '收件人名称',
    MODIFY COLUMN `receiver_mobile` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        NOT NULL DEFAULT '' COMMENT '收件人手机';
