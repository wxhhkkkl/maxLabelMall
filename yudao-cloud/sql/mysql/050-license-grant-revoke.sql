-- ============================================================================
-- 050 赠送撤回（020 追加，2026-11-19 所有者拍板）
--
-- 变更：`member_license_grant` 增加 4 列
--   ① `license_id`               —— 企业赠送作用的授权批次（maxlabel_org_license.id）
--                                   以前只能靠"再算一遍 MAX(seats_total)"猜，撤回必须精确
--   ② `revoke_time`              —— 撤回时间；非空 = 已撤回
--   ③ `revoke_operator_id`       —— 撤回人（system_users.id）
--   ④ `revoke_reason`            —— 撤回理由（必填，留痕用）
--
-- 口径（推翻原 Q154「赠送不可撤销」）：
--   · 只能撤回该客户**最近一笔**、且之后没有任何权益变动的赠送；更早的只能找原操作人核对
--   · 个人：删除这笔赠送产生的订阅行（权益 = 未删除订阅的 MAX(end_time)，删掉即精确还原）
--   · 企业送席位：回减批次的 seats_total；**有席位已分配时拦下**，先去企业管理撤席位
--   · 企业送时长：批次的 end_time / period_months 回退到赠送前
--   · 由这笔赠送新建的批次：整批删除（同样要求没有已分配席位）
--   · 赠送记录本身不删不改（reason / 金额类字段原样保留），只写 revoke_* 三列
--
-- 权限：复用 `member:license:gift`，不新增菜单与按钮权限。
--
-- 上线顺序：先执行本脚本 → 再发后端 → 最后发管理端。
-- 回滚：
--   ALTER TABLE `member_license_grant`
--     DROP COLUMN `license_id`, DROP COLUMN `revoke_time`,
--     DROP COLUMN `revoke_operator_id`, DROP COLUMN `revoke_reason`;
-- ============================================================================

ALTER TABLE `member_license_grant`
  ADD COLUMN `license_id` bigint NULL DEFAULT NULL
    COMMENT '企业赠送作用的授权批次（maxlabel_org_license.id）；个人赠送为 NULL'
    AFTER `after_seats`,
  ADD COLUMN `revoke_time` datetime NULL DEFAULT NULL
    COMMENT '撤回时间；非空 = 已撤回' AFTER `operator_id`,
  ADD COLUMN `revoke_operator_id` bigint NULL DEFAULT NULL
    COMMENT '撤回人（system_users.id）' AFTER `revoke_time`,
  ADD COLUMN `revoke_reason` varchar(255) NULL DEFAULT NULL
    COMMENT '撤回理由（必填）' AFTER `revoke_operator_id`;
