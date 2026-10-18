# 技术决策与被否决方案（008 对公转账）

> 编号续 007：本文件从 **R42** 起（007 排到 R41）。
> 每条都记「选了什么 / 为什么 / 否决了什么」——**否决理由比选择本身更值钱**，
> 因为下次有人想改回去时，能先看到当初为什么不那么做。

---

## R42 —— 「待收款」怎么表达：**1:1 子表**（不新增状态值、不给 `trade_order` 加列）

**Decision**：新增一张与订单 1:1 的子表 `trade_order_offline_payment`。
**下单时插一行**（选「对公转账」才插），确认收款时**填上**流水号等字段。

- 「本单是不是对公转账」= **子表有没有这一行**
- 「待收款」= **有行 且 订单未支付**

**Rationale**（三条，都来自代码事实）：

1. **不能新增状态值**：`TradeOrderStatusEnum` 全仓 **~76 处引用、23 个文件**
   （后端 64 处 / 15 文件，其中 `TradeOrderUpdateServiceImpl` 一个文件就 24 处；
   加前端 `orderStatus.ts` 映射与字典）。为一个标记动状态机，代价与收益完全不成比例。
2. **不该往 `trade_order` 加列**：本项目**至今没有**给它加过任何自有的列；006 的
   `007-order-invoice.sql` 头部把理由写死了 ——「`trade_order` 是全局最热的表
   （详情/列表/售后/统计都查它），已有 40+ 列」。006 因此建了子表。
3. **确认痕迹本来就要一个落点**（流水号 / 打款方 / 到账时间 / 确认人 / 确认时间）——
   于是"怎么表达待收款"与"痕迹落哪"**合并成同一张表**，一次解决两个问题（Q40④）。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 给 `trade_order` 加一个 `payment_type` 列 | 违反本项目对 `trade_order` 的回避惯例（见上）；而且痕迹仍无处安放，等于还要再想办法 |
| 新增状态值 `WAIT_RECEIVE(5)` | 牵动 64 处后端引用 + 前端映射 + 字典，且状态机是所有端判断的公共依赖，风险最高 |
| **复用 `pay_channel_code` 列**（下单时写 `'offline'`） | **本来很诱人**：能白拿筛选、显示名、以及自动跳过微信物流同步。**否决原因**：该列的注释明写「**支付成功**的支付渠道」，在**下单时**（还没付）写它与语义相悖；且它仍解决不了"痕迹落哪"。⚠️ 但**它的两个好处我们没丢**：确认收款后 `updateOrderPaid` 会把 `pay_channel_code` 置为支付单的渠道（= `offline`），于是**筛选与微信跳过照样自动生效** |

---

## R43 —— 确认收款怎么让订单"已支付"：**造一笔真实支付单**，复用 `updateOrderPaid`

**Decision**：在 pay 模块新增一个「创建**已支付**的 offline 支付单」的能力，
由确认收款调用；随后走**既有的** `TradeOrderUpdateService.updateOrderPaid(id, payOrderId)`。

**Rationale**：`updateOrderPaid` 是"订单转待发货"的**唯一入口**（由支付回调 controller
调用），而它对支付单**校验很严**：支付单必须存在、状态 SUCCESS、**金额等于订单实付**、
`merchantOrderId` 等于订单 id —— 不满足就抛 `ORDER_UPDATE_PAID_FAIL_PAY_PRICE_NOT_MATCH`。
也就是说：**要复用这条链路，就必须造一张真的、对得上的支付单**。
✅ 顺带**解决退款**：退款是挂在支付单上的，有支付单退款才走得通（SC-073）。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 直接改订单状态（`updateByIdAndStatus` 置已支付） | **退款链路会断** —— 退款要按支付单发起；绕开支付单等于给自己埋一个"这单退不了款"的坑 |
| 复用 `PayOrderApi.createOrder` 再手动置成功 | 造出来的单是 `WAITING`；而把它变 SUCCESS 的**唯一既有路径** `PayOrderService.updateOrderSuccess` **要求一条 `PayOrderExtensionDO`**（它从 `orderExtensionMapper.selectByNo(...)` 取）——结果还是要新增能力，且更绕 |

**⚠️ 已定（2026-10-16）：同时产出 `pay_order_extension`，复用既有的成功路径。**

原本这里留了"两者选一"，现已定死 —— 因为调研已经给出了答案：
既有把支付单变 SUCCESS 的**唯一**路径是 `PayOrderService.updateOrderSuccess`，
而它**要求一条 `PayOrderExtensionDO`**（从 `orderExtensionMapper.selectByNo(...)` 取）。
于是只有两条路：**补上那张 extension 复用既有路径**，或**另写一条绕过 extension 的成功路径**。
后者等于**在"支付成功"这件事上搞出第二套真相** —— 而将来上游改支付单状态机时，
那条旁路必然被漏掉。**选前者**：多写一行数据，换来"与线上支付走完全同一条链路"（原则 III）。

---

## R44 —— 收款账户信息配在哪：**`trade_config` 加字段**

**Decision**：`TradeConfigDO`（单行配置表）加 4 个字段：户名 / 账号 / 开户行 / 给客户的提示语。

**Rationale**：它已经是"交易相关的可运营配置"的既有载体（售后原因、配送、分销一堆开关），
后台有现成的「交易配置」页可直接扩展；满足 FR-210「MUST 可配置，不得写死在代码里」。

**Alternatives considered**：放进 `pay_channel.config`（JSON）——`offline` 渠道的配置会解析成
`NonePayClientConfig`（**没有任何字段**，`validate` 是空实现），放不进去；
另建一张配置表 —— 为 4 个字段建表违反最小改动。

---

## R45 —— 怎么让对公单跳过「超时自动取消」：改**筛选条件**

**Decision**：改 `TradeOrderUpdateServiceImpl.cancelOrderBySystem()` 的选取条件，
**排除有 offline 子表行的订单**。

**Rationale**：现在的实现是
`selectListByStatusAndCreateTimeLt(UNPAID, now - payExpireTime)`（线上 `payExpireTime = 2h`，
xxl-job `tradeOrderAutoCancelJob` **每分钟**跑一次）——而公司走对公打款要**几天**，
照这样钱还没打出去单子就被砍了（这就是 spec Q45 补上的那条致命边界）。

⚠️ **两处必须一起改，只改一处会出事**：
- 主筛选（`cancelOrderBySystem()`）—— 否则待收款单被选中
- 兜底逻辑 `cancelOrderBySystem(order)` 里会**无条件**读 `order.getPayOrderId()` 去查支付单；
  而对公单在下单时**不建在线支付单**（FR-199）→ `payOrderId` 为 null →
  `payOrderApi.getOrder(null)`。**不能让它走到这里。**

**Alternatives considered**：给对公单设一个更长的期限（如 30 天）—— 所有者否掉了
（Q45 的选择 A）：要的是"这类订单**只由人管**"，而不是"到期自动砍"。代价是必须**配套新增
后台取消入口**（否则弃单永久积压）—— 见 FR-213。

---

## R46 —— `offline` 渠道的最小改动面

**Decision**：`PayChannelEnum` 加 `OFFLINE("offline","对公转账")`；`pay_channel` 插一行；
**必须提供一个 `OfflinePayClient`**（下单/退款都本地返回成功，不调用任何三方）并注册进
`PayClientFactoryImpl`；另外补一条字典（订单/列表按渠道显示名称）。

**Rationale**：`PayClientFactoryImpl` 是一张手写 `Map<PayChannelEnum, Class<? extends PayClient<?>>>`，
`createPayClient` 取不到类就 `Assert.notNull(...)` 抛错。

**⚠️ 本条的初版是错的，2026-10-16 实现时纠正**：初版写的是"不写 client，因为 offline 不发起三方调用、
也不走 `getPayClient` 那条路"。**漏看了退款**：
`PayRefundServiceImpl:100` 是 `channelService.getPayClient(channel.getId())`，
同步退款状态（`:304`）也走它 —— **没有 client，退款直接崩**，而 SC-073 要验的正是"确认收款后能退款"。
✅ 现成的对照物：`WalletPayClient`（同样是"无三方交互"的渠道）**实现了 `doUnifiedRefund`**，本地返回成功。

⚠️ **渠道显示名要补字典**：`TradeOrderConvert` 按 `DictTypeConstants.CHANNEL_CODE`
取 label，管理端用 `DICT_TYPE.PAY_CHANNEL_CODE` —— 少一条，订单上的渠道名就是空白。

⚠️ **由此带出的业务口径（Q47，已由所有者拍板）**：对公收款是打进**银行账户**的，所以
`OfflinePayClient` 返回的"退款成功"**只是记账** —— 钱靠财务**线下转回**。
也就是说**客户会先看到「已退款」**。这在线支付里不存在，在対公场景是必然；所有者选择接受
（与 Q42 不加复核、Q43 不做流水导入同一个方向：**先把链路跑通，人工环节留在人身上**）。

---

## R47 —— C 端两处**现成的陷阱**（不处理就会"看着像好了其实没好"）

1. **`OrderDetailView` 的 `needsNoPay` 会把对公单误判成"无需支付"**：
   ```js
   // 全额抵扣的订单后端不建支付单 → payOrderId 为 null
   const needsNoPay = computed(() => isUnpaid.value && !order.value?.payOrderId)
   ```
   对公单**正好命中**（未支付 + 没有支付单）→ 页面会显示"无需支付"，
   而它**明明需要打款**。必须让「待收款」分支**先于** `needsNoPay` 判断。
2. **下单后前端是 `router.push('/order/${res.id}')`**（`CheckoutView`）—— 直接进订单详情，
   而收款账户正好展示在那里 ✓ **路由不用改**。
3. **📌 实现期间补上的第三处：订单列表**（`OrderListView`，2026-10-16）。
   列表上的标签由 `status` 映射 —— 对公单是 `0 未支付` → 显示「**待支付**」，
   而且 `status === 0` 就会渲染「**立即支付**」按钮。点进去是一个**没有支付按钮**的详情页。
   这与 FR-200（"**订单列表与详情**也 MUST 显示「待收款」"）和 FR-201（客户侧没有在线支付入口）
   都冲突。列表接口原本**没有任何字段**能区分对公单，所以补了 `offlineWaiting`（批量查，不逐笔）。
   ⚠️ **底层状态值不变**，所以列表的筛选胶囊**仍叫「待支付」** —— 改名会让对公单筛不出来。

---

## 待计划/实现阶段确定的小项

- 确认收款时造支付单：**同时产出 `pay_order_extension`** 还是**新写一条不经过 extension 的成功路径**（R43 的 ⚠️）
- 子表的表名与字段名（§data-model 已给建议）
- 后台「取消订单」入口的形态（复用既有 `cancelOrderByType` 链路，还是只做待收款专用的最小入口）
