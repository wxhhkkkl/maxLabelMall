# C 端接口契约（008 对公转账）

> 前缀：**只走 `/app-api`**（宪法约束）。

---

## 1. `POST /trade/order/create` —— 入参加 `payChannelCode`

```jsonc
// 请求（选「对公转账」时）
{ "items": [{ "skuId": 271, "count": 1 }],
  "pointStatus": false,
  "deliveryType": 1,
  "payChannelCode": "offline",   // ← 新增：**下单时就选定的支付渠道**
  "remark": "…", "invoice": { … } }

// 请求（在线支付，即现状）—— **不传** `payChannelCode`，行为与改动前逐字一致
{ "items": [...], "pointStatus": false, "deliveryType": 1 }

// 响应：**与现状同形**，但对公单的 `payOrderId` 恒为 null（没建在线支付单）
{ "code": 0, "data": { "id": 900, "payOrderId": null }, "msg": "" }
```

| 规则 | 说明 |
|---|---|
| 取值 | **只接受 `offline`**（今天唯一的"下单时选定"的渠道）。传其它值（如 `wx_lite`）MUST 被拒 —— 渠道是**支付时**才选的，下单时只有"要不要走线下"这一个决定 |
| 不传 / 传空 | = 在线支付，**完全走现状**（FR-211） |
| 搭配校验 | 与 `deliveryType`、`invoice` 等**互不影响**；对公单同样可以选券、选配送方式、勾开票（Q46） |
| 被禁用时 | `trade_config.offline_pay_enabled = false` 时 MUST 被拒（FR-210 的"可关掉"） |

## 2. `GET /trade/order/settlement` —— 响应加"能不能选对公转账"

```jsonc
{ "code": 0, "data": {
  "deliveryType": 1,
  "offlinePayEnabled": true,        // ← 新增：是否对客户开放「对公转账」
  "offlinePayTip": "[[收款账户信息占位]]",  // ← 新增：给客户的提示（可空）
  "price": { … }, "items": [ … ] } }
```

⚠️ **账户明细（户名/账号/开户行）放在订单详情**，不放在结算响应里 —— 客户在**还没下单**时
不需要看到完整账号，而且那是逐个订单要对照的信息。结算页只需要知道"**能不能选**"。

## 3. `GET /trade/order/get-detail` —— 待收款订单

```jsonc
{ "id": 900, "no": "o…", "status": 0, "payStatus": false,
  "payOrderId": null,               // 对公单**没有在线支付单**
  "payChannelCode": null,           // 确认收款前为空（列语义不变，见 data-model §5）
  "offlinePay": {                   // ← 新增；**不是对公单时为 null**
    "waiting": true,                // 待收款 = 对公单 且 未支付
    "accountName": "[[收款户名占位]]",
    "accountNo":   "[[收款账号占位]]",
    "bankName":    "[[开户行占位]]",
    "tip":         "[[提示语占位]]"   // 例如"请在附言里写订单号"
  } }
```

**前端落点（两处，都要改）**：

1. ⚠️ **`needsNoPay` 会误判**：现有实现是 `未支付 && !payOrderId`，对公单**正好命中** →
   页面会显示"无需支付"。**「待收款」分支必须排在它前面**（R47）。
2. 订单详情展示收款账户与"确认到账后发货"的说明；**MUST NOT 出现任何在线支付入口**（FR-201）。

## 4. 下单后的跳转

现状是 `router.push('/order/' + id)` → **直接进订单详情** ✓ 收款账户正好展示在那里，**不用改**。

## 5. `GET /trade/order/page` —— 列表项加"是不是待收款单"

```jsonc
{ "list": [ { "id": 900, "status": 0, "payOrderId": null,
              "offlineWaiting": true,   // ← 新增；不是对公单 / 已确认收款时为 false
              "items": [ … ] } ] }
```

**为什么要加这个字段**（FR-200 明确要求"**订单列表**与详情都显示「待收款」"）：

| 现状 | 问题 |
|---|---|
| 列表的标签由 `status` 映射 —— 对公单是 `0 未支付` → 显示「**待支付**」 | 与 FR-201「客户侧没有在线支付入口」自相矛盾，客户会去找支付宝 |
| 列表上 `status === 0` 就给「**立即支付**」按钮 | 点进去是一个**没有支付按钮**的详情页 —— 白跑一趟 |

⚠️ **底层状态值不变**（仍是 `0`），只是文案与入口不同 —— 所以列表的**筛选**照旧按
「待支付」筛（对公单本来就在那一格里），`orderStatusFilters()` 一个字都不改。

判定与订单详情**同一口径**：是对公单 **且** 未确认收款 **且** 订单还没付。
「是不是对公单且没确认」由 `TradeOrderOfflinePaymentService#getWaitingReceiptOrderIds`
一次批量给（**不是逐笔查**），「订单是否还付着」由 controller 结合订单状态判。

## 6. `payChannelCode` 的取值纪律（重申）

| 值 | 时机 | 说明 |
|---|---|---|
| **不传** | 下单 | = 在线支付，**完全走现状**（FR-211） |
| `offline` | 下单 | 对公转账（Q41） |
| `wx_lite` / `alipay_pc` … | **支付时** | 下单传它们会被拒（FR-199） |
