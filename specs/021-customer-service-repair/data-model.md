# Data Model: 客户服务（投诉咨询与在线维修）

**Feature**: `021-customer-service-repair` | **Date**: 2026-11-17

所有表沿用 yudao `BaseDO` 的公共列：`tenant_id`、`creator`、`create_time`、`updater`、
`update_time`、`deleted`（逻辑删除）。金额一律为**分**（`int`）。

---

## 1. `trade_customer_service_ticket`（投诉咨询）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK auto | |
| `member_id` | bigint | NOT NULL | 提交/被代建的会员 |
| `type` | tinyint | NOT NULL | `1` 投诉 / `2` 咨询 |
| `content` | varchar(1000) | NOT NULL | 内容（1～1000 字） |
| `contact_mobile` | varchar(20) | NOT NULL | 联系手机号（11 位，默认账户手机号、可改） |
| `images` | varchar(2000) | NULL | 图片 URL JSON 数组，≤ 5 张 |
| `status` | tinyint | NOT NULL DEFAULT 0 | `0` 待处理 / `10` 已处理 |
| `handle_remark` | varchar(500) | NULL | 处理说明（标记已处理时必填） |
| `handler_id` | bigint | NULL | 处理人（`system_users.id`） |
| `handle_time` | datetime | NULL | 处理时间 |
| `source` | tinyint | NOT NULL DEFAULT 1 | `1` 客户自助 / `2` 客服代建 |
| `creator_id` | bigint | NULL | 创建人（会员或管理员编号） |

**索引**：`idx_member(tenant_id, member_id, create_time)`、`idx_status(tenant_id, status, create_time)`。

**状态**：`0 待处理 → 10 已处理`；只有待处理可处理（重复处理返回明确错误）。

---

## 2. `trade_repair_request`（维修申请）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK auto | |
| `member_id` | bigint | NOT NULL | 报修会员 |
| `device_model` | varchar(100) | NOT NULL | 设备型号 |
| `fault_desc` | varchar(1000) | NOT NULL | 故障描述（1～1000 字） |
| `contact_mobile` | varchar(20) | NOT NULL | 联系手机号（11 位） |
| `receiver_name` | varchar(50) | NOT NULL | 客户返修收件人 |
| `receiver_mobile` | varchar(20) | NOT NULL | 客户返修电话 |
| `receiver_area` | varchar(100) | NOT NULL | 省市区快照 |
| `receiver_detail` | varchar(255) | NOT NULL | 详细地址 |
| `images` | varchar(2000) | NULL | 图片 URL JSON 数组 |
| `status` | tinyint | NOT NULL DEFAULT 0 | 见 §4 状态机 |
| `contact_time` | datetime | NULL | 电话联系时间 |
| `contact_result` | varchar(500) | NULL | 联系结果说明 |
| `ship_company` | varchar(50) | NULL | 客户寄出快递公司 |
| `ship_no` | varchar(64) | NULL | 客户寄出运单号（标记已寄出时必填） |
| `ship_time` | datetime | NULL | 客户标记寄出的时间 |
| `source` | tinyint | NOT NULL DEFAULT 1 | `1` 客户自助 / `2` 客服代建 |
| `creator_id` | bigint | NULL | 创建人（会员或管理员编号） |
| `cancel_reason` | varchar(255) | NULL | 取消原因 |

**索引**：`idx_member(tenant_id, member_id, create_time)`、`idx_status(tenant_id, status, create_time)`。

**不变式**：一台设备一行（Q2）；`ship_no` 非空时 `status ≥ 30`；`status = 60` 时 `cancel_reason` 非空。

---

## 3. `trade_repair_order`（维修订单）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK auto | |
| `repair_request_id` | bigint | NOT NULL | 关联维修申请 |
| `member_id` | bigint | NOT NULL | 客户 |
| `repair_fee` | int | NOT NULL DEFAULT 0 | 维修费（分） |
| `return_freight` | int | NOT NULL DEFAULT 0 | 寄回运费（分） |
| `payable_amount` | int | NOT NULL | 应付合计 = 维修费 + 寄回运费 |
| `fee_remark` | varchar(500) | NOT NULL | 费用说明 |
| `pay_status` | tinyint | NOT NULL DEFAULT 0 | `0` 待支付 / `10` 待确认收款 / `20` 已支付 / `30` 无需支付 / `40` 已取消 |
| `pay_channel_code` | varchar(32) | NULL | `wx_lite` / `alipay_pc` / `offline` 等 |
| `pay_order_id` | bigint | NULL | pay 模块支付单号 |
| `pay_time` | datetime | NULL | 支付成功/确认收款时间 |
| `offline_confirm_user_id` | bigint | NULL | 对公转账确认人（`system_users.id`） |
| `offline_confirm_time` | datetime | NULL | 对公转账确认时间 |
| `refund_status` | tinyint | NOT NULL DEFAULT 0 | `0` 未退款 / `10` 退款中 / `20` 已退款 / `30` 退款失败（044） |
| `pay_refund_id` | bigint | NULL | pay 模块退款单号（在线支付） |
| `refund_price` | int | NULL | 退款金额（分，全额 = `payable_amount`） |
| `refund_reason` | varchar(200) | NULL | 退款原因 |
| `refund_time` | datetime | NULL | 退款完成时间 |
| `refund_operator_id` | bigint | NULL | 退款操作人（`system_users.id`） |
| `complete_time` | datetime | NULL | 完成维修时间（045；非空即已完成） |
| `complete_operator_id` | bigint | NULL | 完成维修操作人（`system_users.id`） |
| `creator_id` | bigint | NOT NULL | 开单管理员（`system_users.id`） |

**唯一键**：`uk_request(tenant_id, repair_request_id)` —— 一个维修申请最多一笔维修订单（FR-568）。

**支付单约定**：`pay_order.merchant_order_id = "repair:" + trade_repair_order.id`；
`subject` = `维修订单 #{id}`（≤ 32 字符）。

---

## 4. 状态机

### 4.1 维修申请（`trade_repair_request.status`）

```
0 待联系 ──联系──▶ 10 已联系 ──开单──▶ 20 已开单 ──客户标记寄出──▶ 30 已寄出
                                                                    │
                                                         客服收到设备 ▼
                                                              40 维修中 ──▶ 50 已完成

0/10/20/30/40 ──客服取消（填原因）──▶ 60 已取消
```

- `20 → 30` 前置条件：维修单 `pay_status ∈ {20 已支付, 30 无需支付}`。
- `40 / 50` 只能由客服推进；`60` 只能由客服触发。
- 已支付订单取消前必须先人工退款（R13）。

### 4.2 维修订单（`trade_repair_order.pay_status`）

```
创建时：
  payable_amount = 0            → 30 无需支付
  payable_amount > 0            → 0 待支付
客户选择对公转账              → 10 待确认收款
客服确认收款 / 在线支付回调     → 20 已支付
维修申请取消（未支付）         → 40 已取消
```

**幂等**：相同支付单号重复回调直接返回；`待确认收款 → 已支付` 使用条件更新，
抢不到即视为已确认。

---

## 5. `trade_customer_service_log`（流转记录）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK | |
| `target_type` | tinyint | `1` 投诉咨询 / `2` 维修申请 / `3` 维修订单 |
| `target_id` | bigint | 目标编号 |
| `action` | varchar(32) | `CREATE` / `HANDLE` / `CONTACT` / `ORDER_CREATE` / `PAY_ONLINE` / `PAY_OFFLINE` / `PAY_CONFIRM` / `SHIP` / `REPAIRING` / `COMPLETE` / `CANCEL` / `ADDRESS_UPDATE` |
| `from_status` | tinyint NULL | 变更前状态 |
| `to_status` | tinyint NULL | 变更后状态 |
| `remark` | varchar(500) NULL | 说明（客户可见） |
| `operator_type` | tinyint | `1` 会员 / `2` 管理员 / `3` 系统 |
| `operator_id` | bigint NULL | 操作人编号 |

**索引**：`idx_target(tenant_id, target_type, target_id, id)`。

**不变式**：只追加、不修改、不删除（物理删除不允许，逻辑删除也不使用）。

---

## 6. `trade_customer_service_address`（回收邮寄地址）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK | |
| `receiver_name` | varchar(50) | 收件人 |
| `receiver_mobile` | varchar(20) | 联系电话 |
| `area_name` | varchar(100) | 省市区 |
| `detail_address` | varchar(255) | 详细地址 |
| `ship_remark` | varchar(500) | 寄件说明（客户可见） |

**不变式**：每个租户只保留一条有效记录（`deleted = 0`）；无记录时客户侧显示求助提示（FR-574）。

---

## 7. 错误码（新增到 `yudao-module-trade-api` 的 `ErrorCodeConstants`，使用 `1_011_013_xxx` 段）

| 常量 | 场景 |
|---|---|
| `CUSTOMER_SERVICE_TICKET_NOT_EXISTS` | 工单不存在 / 不属于当前会员 |
| `CUSTOMER_SERVICE_TICKET_HANDLED` | 重复处理已处理的工单 |
| `CUSTOMER_SERVICE_REPAIR_NOT_EXISTS` | 维修申请不存在 / 不属于当前会员 |
| `CUSTOMER_SERVICE_REPAIR_STATUS_INVALID` | 状态迁移不允许 |
| `CUSTOMER_SERVICE_REPAIR_ORDER_EXISTS` | 该申请已开单（含并发唯一键冲突） |
| `CUSTOMER_SERVICE_REPAIR_ORDER_NOT_EXISTS` | 维修订单不存在 |
| `CUSTOMER_SERVICE_REPAIR_ORDER_NOT_PAYABLE` | 无需支付 / 已支付时重复发起支付 |
| `CUSTOMER_SERVICE_REPAIR_NOT_PAID` | 未支付却标记已寄出 |
| `CUSTOMER_SERVICE_REPAIR_NOT_SHIPPABLE` | 状态不是已开单却标记已寄出 |
| `CUSTOMER_SERVICE_REPAIR_SHIP_NO_REQUIRED` | 标记已寄出未填运单号 |
| `CUSTOMER_SERVICE_OFFLINE_NOT_ALLOWED` | 后台未开启对公转账 |
| `CUSTOMER_SERVICE_OFFLINE_CONFIRM_FAIL` | 对公确认时状态不允许 |
| `CUSTOMER_SERVICE_ADDRESS_NOT_CONFIGURED` | 回收地址未配置（客户侧提示） |
| `CUSTOMER_SERVICE_FILE_INVALID` | 附件格式 / 数量 / 大小 / 域名不合规 |
| `CUSTOMER_SERVICE_MEMBER_NOT_EXISTS` | 代建时会员不存在 |

---

## 8. 关系图

```
member_user 1 ── * trade_customer_service_ticket
member_user 1 ── * trade_repair_request 1 ── 0..1 trade_repair_order
                                  │                    │
                                  └──── * trade_customer_service_log（target_type=2）
                                                       │
                              trade_customer_service_log（target_type=3）
trade_customer_service_address（每租户 1 条）
```
