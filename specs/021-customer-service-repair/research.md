# Research: 客户服务（投诉咨询与在线维修）

**Feature**: `021-customer-service-repair` | **Date**: 2026-11-17

本文件记录实现前必须定下来的技术决策，逐条给出依据与备选方案。
规格面的澄清见 [spec.md](./spec.md) 的 Clarifications。

---

## R1. 代码选址：落在 `yudao-module-trade-server`

**Decision**：客户服务、维修申请、维修订单、回收地址全部放在 `trade` 模块的
`cn.iocoder.yudao.module.trade.*.customerservice` 包下，**不新建 Maven 模块**。

**Rationale**：
- 本特性与支付、管理端订单菜单、文件上传强相关；`trade` 模块已经依赖 `pay` API 与会员 API，
  依赖方向天然成立（`member` 是下层模块，不能反向依赖 `pay`/`trade`）。
- 014 印刷定制已经采用同一选址（`trade` 模块 + `trade_customization_*` 表），有可直接复用的
  目录、测试、菜单与前端结构。

**Alternatives considered**：
- 新建 `yudao-module-customer-service`：边界更干净，但要新增 api/server 两个 Maven 模块、
  网关注册、单体聚合、依赖与部署脚本，收益不抵成本。
- 放进 `member` 模块：无法依赖 `trade`/`pay` 的支付能力，会逼出一套独立的收款逻辑。

---

## R2. 表设计：按「投诉咨询 / 维修申请 / 维修订单 / 日志 / 回收地址」拆 5 张表

**Decision**：

| 表 | 用途 |
|---|---|
| `trade_customer_service_ticket` | 投诉 / 咨询 |
| `trade_repair_request` | 维修申请（一台设备一行） |
| `trade_repair_order` | 维修订单（一个维修申请最多一笔） |
| `trade_customer_service_log` | 工单 / 维修单的处理与状态流转记录 |
| `trade_customer_service_address` | 回收邮寄地址（每租户一份） |

**Rationale**：
- 投诉咨询与维修申请的状态机、字段差异大（维修有设备、联系记录、寄出运单、维修订单），
  合成一张宽表会塞入大量 NULL 且查询条件分叉。
- 维修订单与维修申请是 1:0..1，独立表便于用唯一索引保证「一申请一单」（FR-568）。

**Alternatives considered**：
- 单表 + 类型判别 + 扩展表：列表查询简单，但状态机与权限判断会长期依赖类型分支，反而不清晰。
- 复用 `trade_after_sale`：售后是订单退款/退货，与设备送修无关（spec E2）。

---

## R3. 维修订单不建交易订单，独立维护

**Decision**：维修订单落在 `trade_repair_order`，**不创建 `trade_order` / 订单项**；因此不会出现在
「我的订单」（FR-583），也不需要商品/库存/收货地址快照。

**Rationale**：
- 规格明确维修单只在「客户服务」展示；建交易订单需要隐藏 SPU/SKU 并额外过滤列表，容易污染
  订单统计、开票与售后链路。
- 维修单只需要「费用 + 支付状态 + 回收地址」，不需要商品订单的库存、优惠、物流与售后能力。

**Alternatives considered**：
- 走 014 的「隐藏 SKU + 交易订单」：实现最快，但违反 FR-583，且订单列表/统计/开票都要额外改造。

---

## R4. 支付复用 pay 模块，用 `merchantOrderId` 前缀路由回调

**Decision**：维修单支付单的 `merchantOrderId` 使用 **`repair:<tradeRepairOrderId>`** 前缀。
在既有支付回调入口 `AppTradeOrderController#updateOrderPaid` 中先判断前缀：
命中则交给 `CustomerServiceRepairOrderService#updateOrderPaid`，否则维持原有交易订单逻辑。

**Rationale**：
- `pay_order.notify_url` 取的是 `pay_app.order_notify_url`，全应用只有**一个**回调地址
  （`PayOrderServiceImpl` 建单时写入）。新建 pay_app 需要重复配置微信/支付宝商户凭据，不可接受。
- 前缀是数字交易订单号不可能出现的形态，判断无歧义；分支只有几行，不动交易订单核心链路。
- 支付单本身仍由 pay 模块管理，退款可在既有「支付订单」后台按支付单号处理（FR-575 的人工通道）。

**Alternatives considered**：
- 新建 pay_app：需要第二套渠道凭据与运营配置，违反最小改动。
- 改造 pay 模块为按业务类型分发：改动面远大于一个前缀分支。

---

## R5. 对公转账：复用 `createPaidOfflineOrder` + 后台确认收款

**Decision**：
1. 客户在维修单选择「对公转账」→ 维修单支付状态置 `10 待确认收款`，并展示 `trade_config`
   里的收款户名/账号/开户行/提示。
2. 客服确认收款时，调用 `PayOrderApi.createPaidOfflineOrder` 造一张 `offline` 渠道的**已支付**支付单
   （`merchantOrderId = repair:<id>`），再同步调用维修单的 `updateOrderPaid`，并回写 `payOrderId`。
3. 读取 `trade_config` 时必须 `ObjUtil.defaultIfNull`：租户 162 没有该行，直接取字段会 NPE
   （2026-10-16 结算页 500 的既有教训）。

**Rationale**：与 008 对公转账的既有链路同形（`TradeOrderUpdateServiceImpl#confirmOfflinePaid`），
保证支付单、退款与审计口径一致。

**Alternatives considered**：只改维修单状态、不造支付单 —— 失去统一支付记录与退款入口，拒绝。

---

## R6. 会员绑定与「客服代建」检索

**Decision**：所有工单必须绑定 `member_user.id`。客服代建时，通过 `MemberUserApi` 检索会员：
- 关键字像手机号（11 位数字）→ `getUserByMobile` 精准匹配；
- 否则 → `getUserListByNickname` 模糊匹配（最多返回若干条）。
新增 `GET /trade/customer-service/member/search` 挂在 `trade:customer-service:handle` 权限下，
避免把 `member:user:query` 捆绑给客服角色。

**Rationale**：代建是电话场景的刚需（Q2）；用 API 而非直连会员表，保持模块边界。
未注册会员不能建单（FR-582），客服引导先注册。

---

## R7. 幂等与并发

**Decision**：
- 投诉咨询：同一会员 60 秒内提交相同类型 + 相同内容，只生成一条（Redis 短锁，键含会员与内容哈希，
  与 014 的提交锁同一手法）。
- 维修订单：`trade_repair_order` 对 `(tenant_id, repair_request_id)` 建唯一索引；服务层先查后插，
  唯一键冲突转成明确错误，保证并发开单只有一笔。
- 支付回调：`updateOrderPaid` 校验「已支付且支付单号相同」直接返回；不同支付单号则拒绝。
- 对公确认：先条件更新支付状态（`待确认收款 → 已支付`），抢不到即返回「已收款」。

**Rationale**：规格的边缘场景（连点、并发开单、重复回调）都要求 100% 幂等（SC-215）。

---

## R8. 状态机在服务层集中实现

**Decision**：维修申请状态：
`0 待联系 → 10 已联系 → 20 已开单 → 30 已寄出 → 40 维修中 → 50 已完成`，另有 `60 已取消`。
只允许以下迁移，其他一律拒绝：

| 迁移 | 触发者 | 前置条件 |
|---|---|---|
| 0 → 10 | 客服 | 记录联系结果 |
| 0/10 → 20 | 客服 | 生成维修订单成功（同一事务） |
| 20 → 30 | 客户 | 维修单已支付或无需支付；填写快递公司与运单号 |
| 30 → 40 | 客服 | 已收到设备 |
| 40 → 50 | 客服 | 维修完成 |
| 0/10/20/30/40 → 60 | 客服 | 填取消原因；已支付需先人工退款 |

**Rationale**：前端只做按钮显隐，权威判断在服务端（宪法 III）；状态与订单生成必须在同一事务（FR-579）。

---

## R9. 附件：复用上传接口，只存 URL

**Decision**：C 端与管理端都复用现有上传接口；工单只保存 URL 数组（JSON），
服务端校验：`jpg/jpeg/png/webp`、≤ 5 张、单张 ≤ 10 MB，且 URL 必须来自既有文件域名
（与 014 `CustomizationSpecValidator` 的域名校验同口径）。

**Rationale**：不新建文件存储；防止外链与超限（FR-580）。

---

## R10. 管理端菜单与权限

**Decision**：
- 顶层目录「客户服务」（`path=/customer-service`，type=1），下挂页面「服务工单」
  （`component=trade/customer-service/index`，权限 `trade:customer-service:query`）。
- 页面按钮：「处理 / 代建 / 开单 / 改状态 / 确认收款 / 维护回收地址」统一由
  `trade:customer-service:handle` 控制。
- 菜单 SQL 沿用 `026` / `042` 的幂等写法（`INSERT ... SELECT WHERE NOT EXISTS`）。

**Rationale**：FR-578 要求「看」与「处理」分开授权；单页 + 页签与 020 授权看板一致，
减少管理端页面数量。

---

## R11. 前端：个人中心新增一页，支付复用既有 API

**Decision**：
- 门面站新增 `/account/customer-service`，`AccountSidebar` 增加「客户服务」；页面用页签承载
  「投诉咨询」「在线维修」「我的记录」。
- 维修单支付复用 `@/api/pay`（`listEnabledChannelCodes` / `submitPay` / `getPayOrder`），
  按 `OrderDetailView` 的微信 JSAPI / 支付宝交互实现一个**新的** `MemberPayDialog` 组件，
  **不改** `OrderDetailView` 现有支付逻辑。
- 对公转账展示 `trade_config` 的收款信息；提交后状态为「待确认收款」。
- 地址簿复用 `@/api/address` 的 `listAddress`。

**Rationale**：支付渠道与回调都在服务端；前端只负责渠道选择与跳转。抽取新组件避免复制整段
订单详情逻辑，同时不触碰既有页面（宪法 IV）。

**Alternatives considered**：直接复用订单详情页的支付区块 —— 维修单没有 `/order/:id` 路由，
强行复用会把交易订单概念带进客户服务页。

---

## R12. 留痕：`@LogRecord` + 业务日志表

**Decision**：管理端关键动作（处理、开单、确认收款、改状态、取消、地址修改）继续使用
`@LogRecord` 写操作日志；同时新增 `trade_customer_service_log`，记录**客户可见**的流转时间线
（操作人、时间、前后状态、备注）。

**Rationale**：操作日志是后台审计，客户看不到；规格要求客户看到处理说明与维修进度（US70/US73），
两者用途不同，不能合并。

---

## R13. 退款口径

**Decision**：客户侧不提供退款入口（FR-575）；已支付维修单需要退款时，运营在既有「支付订单」
后台按 `payOrderId` 原路退款（对公转账由财务线下退回），随后在客户服务后台把维修申请取消并填写原因。

**Rationale**：不新建退款链路；复用 pay 模块的支付单退款能力，与 008/014 的人工通道一致。

**明确不做**：维修单自动退款、退款金额按比例拆分、退款状态同步到客户侧（本期以取消原因展示）。
