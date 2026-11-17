# admin-api 契约（021 客户服务）

所有路径以 `/admin-api` 前缀，**只走管理端**。

权限拆分（FR-578）：

| 权限 | 覆盖 |
|---|---|
| `trade:customer-service:query` | 列表、详情（只读） |
| `trade:customer-service:handle` | 代建、处理、联系记录、开单、改状态、取消、确认收款、会员检索、回收地址维护 |

---

## 1. 投诉咨询

### `GET /trade/customer-service/ticket/page`

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNo` / `pageSize` | 是 | 分页 |
| `type` | 否 | `1` 投诉 / `2` 咨询 |
| `status` | 否 | `0` 待处理 / `10` 已处理 |
| `source` | 否 | `1` 客户自助 / `2` 客服代建 |
| `keyword` | 否 | 会员昵称 / 手机号 / 内容 |
| `createTime` | 否 | `[起, 止]` |

```jsonc
{ "code": 0, "data": { "total": 1, "list": [
  { "id": 501, "memberId": 88, "memberNickname": "张三", "memberMobile": "138…",
    "type": 1, "typeName": "投诉", "content": "…", "contactMobile": "…",
    "status": 0, "statusName": "待处理",
    "source": 1, "sourceName": "客户自助", "images": ["…"],
    "handleRemark": null, "handlerName": null, "handleTime": null,
    "createTime": 1794900000000 }
] } }
```

### `GET /trade/customer-service/ticket/get?id=501`

返回详情 + `logs`；`logs` 含创建与处理记录（`operatorType`、`operatorId`、`operatorName`、时间、说明）。

### `PUT /trade/customer-service/ticket/handle`

```jsonc
{ "id": 501, "handleRemark": "已电话回访，指导重新安装驱动，客户确认恢复" }
```

**规则**：只有 `0 待处理` 可处理；`handleRemark` 必填且 ≤ 500 字；处理人取当前登录管理员；
写 `trade_customer_service_log` 与 `@LogRecord`。

### `POST /trade/customer-service/ticket/create`

客服代建，请求体在 C 端创建字段基础上增加：

```jsonc
{ "memberId": 88 }   // 必填，已注册会员
```

**规则**：会员必须存在；其余校验与 C 端一致；`source=2`，`creator_id` 取当前管理员；
管理端列表可按来源筛选（FR-582）。

---

## 2. 在线维修

### `GET /trade/customer-service/repair/page`

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNo` / `pageSize` | 是 | 分页 |
| `status` | 否 | `0/10/20/30/40/50/60` |
| `source` | 否 | `1` 客户自助 / `2` 客服代建 |
| `keyword` | 否 | 会员昵称 / 手机号 / 设备型号 |
| `createTime` | 否 | `[起, 止]` |

列表行包含：申请编号、会员、设备型号、状态、维修单支付状态、是否已寄出/运单号、开单人、创建时间。

### `GET /trade/customer-service/repair/get?id=601`

返回申请详情、联系记录、寄出信息、维修订单（含支付状态与费用）、回收地址快照、流转日志。

### `POST /trade/customer-service/repair/create`

客服代建，请求体与 C 端创建一致 + `memberId`；`source=2`。校验规则完全一致（FR-582）。

### `PUT /trade/customer-service/repair/contact`

```jsonc
{ "id": 601, "contactResult": "已电话联系，确认可寄修，报价 100 元 + 寄回运费 20 元" }
```

**规则**：允许从 `0 待联系 / 10 已联系` 调用；记录 `contact_time = now`、联系人与结果；
把状态推进到 `10 已联系`；写日志。

### `POST /trade/customer-service/repair/order/create`

```jsonc
{ "repairRequestId": 601,
  "repairFee": 10000,      // 分，≥ 0
  "returnFreight": 2000,   // 分，≥ 0
  "feeRemark": "更换打印头；含寄回运费" }
```

```jsonc
{ "code": 0, "data": { "repairOrderId": 701, "payableAmount": 12000, "payStatus": 0 } }
```

**规则（逐条可验收）**

| 规则 | 依据 |
|---|---|
| 申请必须存在且状态为 `0 待联系 / 10 已联系` | FR-568 |
| 一个申请最多一笔维修订单；并发开单只有一笔成功 | FR-568 / 唯一键 |
| 两项费用均 ≥ 0；费用说明必填 ≤ 500 字 | FR-569 |
| `payableAmount = repairFee + returnFreight` | FR-569 |
| 创建成功与申请状态 → `20 已开单` 同一事务 | FR-579 |
| `payableAmount = 0` → `payStatus = 30 无需支付`，否则 `0 待支付` | FR-573 |
| 记录开单管理员、时间；写日志与 `@LogRecord` | FR-569 / FR-581 |

### `PUT /trade/customer-service/repair/status`

```jsonc
{ "id": 601, "status": 40, "remark": "已收到设备，开始维修" }
```

**规则**：只允许 `30 → 40`（维修中）与 `40 → 50`（已完成）；其他迁移拒绝；写日志。
`50 已完成` 之后不允许再改状态。

### `PUT /trade/customer-service/repair/cancel`

```jsonc
{ "id": 601, "reason": "客户放弃维修" }
```

**规则**：`0/10/20/30/40` 可取消，`50/60` 拒绝；`reason` 必填 ≤ 255 字；
维修单未支付时置 `pay_status = 40 已取消`；已支付时提示先完成人工退款（R13）；写日志。

---

## 3. 维修订单

### `POST /trade/customer-service/repair-order/confirm-offline`

```jsonc
{ "id": 701, "bankSerialNo": "202611170001", "payerName": "某某公司",
  "paidTime": 1794900000000, "remark": "对公到账" }
```

**规则**

- 只有 `pay_status = 10 待确认收款` 可确认；已支付 / 已取消 / 无需支付拒绝。
- 先条件更新 `10 → 20`（幂等闸门），再调用 `PayOrderApi.createPaidOfflineOrder`
  造 `offline` 已支付支付单（`merchantOrderId = repair:<id>`），同步更新维修单
  `pay_order_id / pay_channel_code=offline / pay_time / offline_confirm_*`。
- 写日志与 `@LogRecord`。

### `GET /trade/customer-service/repair-order/page`

查询参数：`pageNo / pageSize / id / memberId / payStatus / refundStatus / createTime[]`。

列表行：订单编号、会员昵称/手机号、设备型号、维修费、寄回运费、应付合计、支付状态、
退款状态、创建时间。详情额外返回支付渠道、支付时间、对公确认痕迹、退款单号/金额/原因/时间。

### `PUT /trade/customer-service/repair-order/refund`

```jsonc
{ "id": 701, "reason": "客户取消维修，全额退款" }
```

**规则（2026-11-17 所有者拍板：全额退，线下人工标记）**

- 仅 `pay_status = 20 已支付` 且 `refund_status = 0 未退款` 可退；金额固定为 `payable_amount`。
- **在线支付**：调用 `PayRefundApi.createRefund`，`merchantOrderId = repair:<id>`、
  `merchantRefundId = repair-refund:<id>`，退款状态置 `10 退款中`；退款回调在
  `AfterSaleController#updateAfterSaleRefunded` 按 `repair:` 前缀分流，成功置 `20 已退款`，
  失败置 `30 退款失败`。
- **对公/线下确认**（`pay_channel_code = offline` 或存在 `offline_confirm_time`）：不调用支付退款，
  运营填写原因后直接置 `20 已退款`。
- 权限 `trade:customer-service:refund`；全流程写流转日志。

### `PUT /trade/customer-service/repair-order/complete?id=701`

手动完成维修：仅维修申请处于 `30 已寄出` 或 `40 维修中`、订单已支付/无需支付且未退款时可用；
完成后写入 `complete_time` / `complete_operator_id`，维修申请置 `50 已完成`，并写双方流转日志。
权限 `trade:customer-service:handle`。

### `GET /trade/customer-service/repair-order/get?id=701`

管理端维修订单详情：费用、支付状态、支付渠道、支付时间、对公确认痕迹、退款状态与退款痕迹、
完成维修人/时间、关联申请与会员。

### 管理端菜单（044）

`客户服务` 下拆为四个独立菜单：
`投诉咨询`（原 043 的「服务工单」改名）/ `在线维修` / `回收地址设置` / `维修订单`；
维修订单页下增加按钮权限 `trade:customer-service:refund`。

---

## 4. 会员检索（代建用）

### `GET /trade/customer-service/member/search?keyword=13800008888`

- 关键字为 11 位手机号 → 精准匹配；否则按昵称模糊匹配（最多 20 条）。
- 返回 `[{ "id": 88, "nickname": "张三", "mobile": "138…" }]`。
- 权限 `trade:customer-service:handle`；不要求 `member:user:query`（R6）。

---

## 5. 回收邮寄地址

### `GET /trade/customer-service/address`

返回当前租户的回收地址；未配置时 `data = null`。

### `PUT /trade/customer-service/address`

```jsonc
{ "receiverName": "李四", "receiverMobile": "010-88613986",
  "areaName": "北京市 北京市 海淀区", "detailAddress": "xx 路 8 号",
  "shipRemark": "请在外箱注明维修单号，寄出后回填运单号" }
```

**规则**：字段必填；每租户只保留一条有效记录；保存后客户侧立即读取最新值（FR-577）；
写日志与 `@LogRecord`。

---

## 6. 权限与服务端校验

- 所有接口 MUST 挂 `@PreAuthorize`；只读角色调用处理类接口返回 403（FR-578）。
- 所有查询 MUST 按租户隔离（`tenant_id` 由框架自动处理）。
- 对公确认、开单、状态流转 MUST 在服务端校验，不依赖前端按钮显隐（宪法 III）。
