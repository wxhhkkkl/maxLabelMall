# 管理端接口契约（008 对公转账）

> 前缀：**只走 `/admin-api`**（宪法约束）。

---

## 1. `PUT /trade/order/confirm-offline-paid` —— **确认收款**（新增）

> 权限点：**`trade:order:confirm-paid`**（独立权限 —— 资金动作，不是所有后台角色都该有）
> 幂等：**是**（FR-206）

```jsonc
// 请求
{ "id": 900,
  "bankSerialNo": "2026101600012345",   // **必填** —— 财务动作必须留痕（Q40④）
  "payerName": "南京路桥机械有限公司",     // 选填
  "paidTime": 1790486340000,             // 选填，epoch ms（财务照银行流水填）
  "remark": "分两笔打的，这是第一笔" }      // 选填

// 响应
{ "code": 0, "data": true, "msg": "" }
```

**服务端 MUST 按顺序校验**：

| # | 校验 | 失败时 |
|---|---|---|
| 1 | 订单存在 | 「订单不存在」 |
| 2 | **订单是对公单**（子表有行） | 「该订单不是对公转账订单」 |
| 3 | **订单尚未支付**（`status = 待支付`） | 「该订单已收款」（=幂等的边界） |
| 4 | **订单未取消** | 「订单已取消」（FR-214） |
| 5 | `bankSerialNo` 非空 | 「请填写银行流水号」（FR-204） |

**通过后做的事（同一事务）**：

1. 在 pay 模块**造一笔 `offline` 渠道的已支付支付单** —— 金额取订单实付、`merchantOrderId` 取订单 id
   （⚠️ `updateOrderPaid` 会校验这两个，对不上会抛 `..._PAY_PRICE_NOT_MATCH`，R43）
2. 调**既有的** `updateOrderPaid(orderId, payOrderId)` → 订单进「待发货」
3. 写回子表：流水号 / 打款方 / 到账时间 / 备注 / **确认人 = 当前登录用户** / **确认时间 = now**

⚠️ **不要自己改订单状态**（R43：退款挂在支付单上，绕开它退款就断了）。

## 2. `PUT /trade/order/cancel` —— **后台取消订单**（新增，FR-213）

> 权限点：沿用既有的 `trade:order:update`
> 存在的理由：对公单**不参与超时自动取消**（FR-212），客户弃单后**必须有人能清掉它** ——
> 而后台此前**根本没有取消订单的功能**

```jsonc
{ "id": 900, "reason": "客户已放弃打款" }   // reason 选填
// 响应：{ "code": 0, "data": true }
```

| 规则 | 说明 |
|---|---|
| 只能取消**未支付**订单 | 已支付的（含确认收款后的）MUST 走**退款**，MUST NOT 用取消（金额对不上） |
| 复用既有链路 | 走 `cancelOrder0(order, ...)`，与系统超时取消同一套（释放库存、作废开票申请等）✓ |
| 取消后 | MUST NOT 还能确认收款（FR-214） |
| 留痕 | **谁、什么时候、为什么**（`reason`） |

## 3. `GET /trade/order/page` —— 筛选待收款（FR-209）

**不加新参数**，复用既有的 `payChannelCode`：

```jsonc
?payChannelCode=offline&status=0     // 「待收款」= 对公渠道 + 待支付状态
```

⚠️ 但**查询实现要动**：`payChannelCode` 这一列在**确认收款前是空的**（列语义是"支付成功的渠道"，
见 data-model §5），所以**不能靠它筛** —— 筛选条件走**子表**：
`inSql(order_id IN (SELECT order_id FROM trade_order_offline_payment WHERE ...))`。
（前端传的仍是 `payChannelCode=offline`，因为那是运营看得懂的说法；**转换在服务端做**。）

## 4. 交易配置页（FR-210）

`seller-config`/交易配置页加一块「对公转账」：是否启用 + 户名 + 账号 + 开户行 + 提示语。
走既有的 `trade_config` 读写接口（**不新增接口**，只是多几个字段）。

## 5. 前端落点

| 文件 | 改动 |
|---|---|
| `views/mall/trade/order/detail/index.vue` | 「待收款」时显示「确认收款」按钮（`v-hasPermi`）；弹层收集流水号等；显示已确认的流水号与确认人 |
| `views/mall/trade/order/index.vue` | 操作列加「取消订单」；筛选区复用「支付方式」下拉（补了字典后自动出现「对公转账」） |
| `views/mall/trade/order/form/` | 新增「确认收款」弹层（照 `OrderDeliveryForm` 的形态） |
| `src/utils/constants.ts` | 若需要状态文案映射则同步（**本次不加状态值**，所以大概率不用动）✓ |

## 6. `get-detail` / `page` —— 加「是不是待收款单」+ 确认痕迹

```jsonc
// GET /admin-api/trade/order/get-detail?id=900
{ "id": 900, "status": 0, "payChannelCode": null,   // ⚠️ 确认收款前**是空的**
  "offlineWaiting": true,                            // ← 新增：按钮显隐就靠它
  "offlinePayment": {                                // ← 新增：不是对公单为 null
    "bankSerialNo": "2026101600012345",
    "payerName": "南京路桥机械有限公司",
    "paidTime": 1790486340000, "remark": "分两笔打的",
    "confirmUserId": 249, "confirmTime": 1790487000000
  } }

// GET /admin-api/trade/order/page —— 列表项同样加 `offlineWaiting`
```

**为什么必须加这两个字段**（而不是让前端自己算）：

| 想当然的写法 | 为什么不成立 |
|---|---|
| `payChannelCode === 'offline'` | 那一列记的是"**支付成功**的渠道" → 对公单**确认收款前是空的**，判不出来 |
| 用 `payOrderId === null` | 与"全额券抵扣那类无需支付"**完全重合**，分不出来（C 端踩的是同一个坑，R47） |
| 前端拼 `status === 0 && deliveryType …` | 拼不出"是不是对公单"这件事 —— 只有子表知道（原则 III：判定在服务端） |

`offlineWaiting` 的口径与 C 端 `offlinePay.waiting` **完全一致**（是对公单 **且** 未支付 **且** 未确认），
两端口径由**同一批测试**（`TradeOrderUpdateServiceImplTest` 的确认/取消用例）钉住。
