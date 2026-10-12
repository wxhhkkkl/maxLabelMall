-- ============================================================================
-- 005 —— 修三处「按 IPv4 长度建的 IP 列」，IPv6 客户会写不进去
--
-- ⚠️ 与 002/003/004 不同：那几份是**建新东西**，只在首次上线跑一次；
--    本文件是**修官方建表脚本里的坑**，所以重建库时也要补跑一遍。
--    （错的宽度写在 ruoyi-vue-pro.sql:6465 与 mall-2026-04-18.sql:1479，
--      两份都是外部下载的官方资产，**没有直接去改它们**。）
--
-- 现象（2026-10-12 客户报「验证码发送失败：系统异常」）
-- ----------------------------------------------------------------------------
--   app.log 里两条硬证据：
--     13:49:33  send-sms-code  Parameters: 13911806394, 5177, 1,
--                              2408:8206:4823:fa10:7c4d:e225:717d:606f, ...
--     13:49:34  ERROR GlobalExceptionHandler.defaultExceptionHandler
--               Data truncation: Data too long for column 'create_ip' at row 1
--               at SmsCodeServiceImpl.createSmsCode(SmsCodeServiceImpl.java:75)
--
--   那个 `2408:...` 是 **39 字符的 IPv6**（联通段）。`create_ip` 是 `varchar(15)`
--   —— 15 正好是 `255.255.255.255` 的长度，上游写这行时只考虑了 IPv4。
--   INSERT 被 MySQL 拒 → DataIntegrityViolationException 没人接 → 兜底 handler
--   返回 500「系统异常」。**不是**限流、不是模板/渠道没绑（那些都有各自文案）。
--
--   为什么只有部分客户中招：IPv4 出口的客户 ≤15 字符能插进去，走运营商 IPv6 的
--   客户必炸。所以它是「按客户网络类型随机中招」，不是全站故障。
--
-- 同一个坑的第二处（更严重：直接掉单）
-- ----------------------------------------------------------------------------
--   `trade_order.user_ip` 是 `varchar(30)`，同样装不下 39 字符。日志里已命中 8 次，
--   语句是 `INSERT INTO trade_order (...)` —— **下单也会 500**。
--
-- 为什么是 50
-- ----------------------------------------------------------------------------
--   本仓库其它 IP 列（`system_login_log.user_ip`、`member_user.login_ip`、
--   `pay_order.user_ip` 等）统一是 `varchar(50)`，够放 IPv6 及其 IPv4-mapped 写法。
--   这里跟齐，不另立标准。
--
-- 同一个坑的第三处（修完前两处之后，才轮得到它报错）
-- ----------------------------------------------------------------------------
--   `member_user.register_ip` 是 `varchar(32)`，39 > 32。它落在**同一个页面**的
--   下一个动作上：`MemberAuthServiceImpl.smsLogin`（登录弹层的"未注册的手机号验证后
--   将自动创建账号"）会走 `createUserIfAbsent` → `MemberUserServiceImpl.createUser`
--   里的 `setRegisterIp(...)`。
--   所以对 IPv6 客户，只修前两处的话报错点会从"获取验证码"平移到"登录"——
--   **新号仍然进不来**，只是换了个按钮报同一个 500。号码已注册的老客户不走建号分支。
--
--   生产库 2026-10-12 做过全库扫描：`COLUMN_NAME REGEXP '(^|_)ip(_|$)'` 共 15 个列，
--   改完下面三处后**没有第四处**（其余 12 列都是 varchar(50) 或 255）。
--
-- 幂等 / 回滚
-- ----------------------------------------------------------------------------
--   幂等：MODIFY 到同一个定义重复执行不改语义，可安全重跑。
--   回滚：把 varchar(50) 改回 varchar(15) / varchar(30) / varchar(32) 即可 ——
--         但**只有在表里还没有超过原长度的值时才可行**，一旦落进一条 IPv6 就会截断报错。
--         本次故障是写在 INSERT 阶段失败的，**没有脏数据落库**，所以现在还能回滚。
-- ============================================================================

-- 1. 短信验证码：发短信时记录请求来源 IP（客户报错的直接原因）
--    线上现为 varchar(15) NOT NULL，无默认值，utf8mb4_unicode_ci
ALTER TABLE `system_sms_code`
    MODIFY COLUMN `create_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        NOT NULL COMMENT '创建 IP';

-- 2. 交易订单：下单时记录用户 IP（同一根因，表现是下单 500）
--    线上现为 varchar(30) NOT NULL DEFAULT ''，注意该表默认排序规则是 utf8mb4_bin，
--    这里显式照抄列上的 collation，避免 MODIFY 时被表默认值改掉
ALTER TABLE `trade_order`
    MODIFY COLUMN `user_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        NOT NULL DEFAULT '' COMMENT '用户 IP';

-- 3. 会员注册 IP：新号自动建号时记录来源（修完 1、2 之后才会暴露出来的那一处）
--    线上现为 varchar(32) NOT NULL，无默认值。注意该列上的排序规则是
--    utf8mb4_general_ci —— 与库/表默认未必一致，显式照抄，别让 MODIFY 顺手改掉
ALTER TABLE `member_user`
    MODIFY COLUMN `register_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
        NOT NULL COMMENT '注册 IP';
