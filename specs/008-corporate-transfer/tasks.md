# 任务清单（008 对公转账）

**Input**: `specs/008-corporate-transfer/` ｜ **Prerequisites**: plan.md / spec.md / research.md / data-model.md / contracts/

**Tests**：本仓库宪法**原则 I（测试先行，不可协商）** 要求测试任务**必须**存在、**必须**排在同一故事的实现任务之前、
且**必须先写失败**。凡"无法先写"的（如新增枚举值、建表本身），在该任务上注明理由。

## Format: `[ID] [P?] [Story] Description`

- 编号**续 007**：本清单从 **T461** 起（007 排到 T460）。编号全局唯一、不补空号。
- **[P]**：可并行（不同文件、无依赖）

---

## Phase 1: Setup

- [x] T461 建 `specs/008-corporate-transfer/`：`spec.md`（两轮澄清 Q40~Q46）/ `plan.md` /
      `research.md`（R42~R47）/ `data-model.md` / `contracts/{app-api,admin-api}.md` / `quickstart.md`
- [x] T462 把 `.specify/feature.json` 指向 `specs/008-corporate-transfer`
      ⚠️ **这条不是走过场**：它原先钉的是 `specs/006-order-invoice`，而 speckit 的脚本末尾是
      `Copy-Item ... -Force` —— 不改的话**每条命令都会去覆盖 006 已完成的 plan/tasks**

## Phase 2: Foundational（US26 / US27 **都**依赖它）

- [x] T463 [P] **SQL** `yudao-cloud/sql/mysql/012-corporate-transfer.sql`（幂等，照 002~011 的写法）：
      ① 建 `trade_order_offline_payment`（`uk_order_id` 唯一）② `trade_config` 加 5 个字段
      （`offline_pay_enabled` / 户名 / 账号 / 开户行 / 提示语）
      ③ `pay_channel` 插 `offline` ④ 字典插渠道名「对公转账」
      ⚠️ ③④ 漏了会**静默降级**：渠道不存在 → 确认收款失败；字典没补 → 订单上渠道名空白（R46）
- [x] T464 [P] `trade-api`：`PayChannelEnum` 加 `OFFLINE("offline", "对公转账")`
      ⚠️ **无法先写测试**：纯枚举值新增，写测试只会得到编译错误（不是"失败原因正确"）
- [x] T465 `pay-server`：新增「**造一笔已支付的 offline 支付单**」的能力并暴露到 `PayOrderApi`
      （金额取订单实付、`merchantOrderId` 取订单 id —— `updateOrderPaid` 会**逐项校验**，对不上就抛
      `..._PAY_PRICE_NOT_MATCH`，R43）
      **做法已定**（A1 已收口）：**同时产出 `pay_order_extension`，复用既有的 `updateOrderSuccess` 路径** ——
      不另写一条绕过 extension 的成功路径（那等于在"支付成功"上搞第二套真相，上游改状态机时必被漏掉）
- [x] T465b `pay-server`：新增 **`OfflinePayClient`**（`extends AbstractPayClient<NonePayClientConfig>`，
      `doUnifiedOrder` / `doUnifiedRefund` **本地直接返回成功**，不调用任何三方）
      + 注册进 `PayClientFactoryImpl` 的 `Map<PayChannelEnum, Class<? extends PayClient<?>>>`
      ⚠️ **这是实现时才发现漏的一条**（research R46 初版写错了）：退款会走
      `PayRefundServiceImpl:100` 的 `channelService.getPayClient(...)` —— **没有 client，退款直接崩**，
      而 SC-073 要验的正是"确认收款后能退款"。照 `WalletPayClient` 的形态写
- [x] T466 [P] `trade-server`：子表 DO `TradeOrderOfflinePaymentDO` + Mapper
      （至少 `selectByOrderId` + 一个给列表筛选用的 `inSql` 查询）
- [x] T467 [P] `trade-server`：`TradeConfigDO` + 交易配置的 ReqVO/RespVO 各加 5 个字段
      （后台「交易配置」页要能填收款账户，FR-210）
- [x] T468 `trade-server`：**补 H2 测试建表脚本** `src/test/resources/sql/create_tables.sql`
      （新表 + `trade_config` 新列）
      ⚠️ 漏了它，本模块**所有**测试类**启动即挂** —— 与 T373（006 的同一坑）一模一样；
      另外 `sql/clean.sql` 也要一并补

## Phase 3: US26 —— 客户用对公转账下单（P1）

**独立可测**：结算页选「对公转账」→ 下单成功 → 订单是「待收款」、有收款账户、**没有任何在线支付入口**。

### 测试（先写、先失败）

- [x] T469 [US26] 服务端：下单带 `payChannelCode=offline` →
      **子表插了一行**、**没有创建在线支付单**（`payOrderId` 为 null）、订单仍是 `UNPAID`（FR-199/200）
      📌 **落点已调整**（实现时）：原写 `TradeOrderUpdateServiceImplTest`，但**那个测试类根本不存在**，
      而建它要 mock 商品/营销/会员/地址一整套 API。照 006 的做法（发票就是独立服务
      `TradeOrderInvoiceServiceImpl` + 轻量测试）拆出了 `TradeOrderOfflinePaymentService`，
      它只依赖 mapper，用 `BaseDbUnitTest` 轻量测（7 条，全过）。
      ⚠️ 因此"**不建在线支付单**"那半边（在 `TradeOrderUpdateServiceImpl` 里）**没有自动化覆盖** ——
      走 quickstart §一.6 的手工核对，已在 quickstart 显式写明。
- [x] T470 [P] [US26] C 端 `CheckoutView.spec.ts`：选对公转账 → 请求体带 `payChannelCode: "offline"`；
      **在线支付时请求体不带它**（回归，FR-211）
      —— ✅ 7 条：没开时不给选项 / 开了时两种并列+带提示语 / 账户明细不在结算响应与页面上 /
      选了带 `offline` / 默认不带 / **改回在线支付后不留残留** / 地址校验不受影响
- [x] T471 [P] [US26] C 端 `OrderDetailView.spec.ts`：待收款单 ① **不出现**任何支付入口
      ② **不显示"无需支付"**（R47 的误判）③ 显示收款账户（FR-201）
      ④ **「取消订单」入口仍在**（spec 的 Edge Case 要求"打款前能取消"）
      —— 📌 分析时发现这条**原先没有任何任务覆盖**（C1）。实际实现多半**已经成立**
      （`canCancel(status)` 只判 `UNPAID`，而对公单正是 `UNPAID`），但**没人验过就不算数**
      —— ✅ 9 条：状态文案「待收款」/ 账户与提示语 / 无支付入口 / **无"无需支付"** /
      取消入口在 / **不显示支付截止**（FR-212）/ 确认后回到既有版式 / 普通单不受影响 /
      **payPrice=0 的普通单照旧"无需支付"**（不能被本特性改掉）

### 实现

- [x] T472 [US26] 服务端：`AppTradeOrderCreateReqVO` 加 `payChannelCode`，
      校验**只接受 `offline`**（传 `wx_lite` 等一律拒 —— 渠道是**支付时**才选的）+ 校验
      `trade_config.offline_pay_enabled`（关了就不让选）
      ⚠️ **"关闭"只挡新下单**：`offline_pay_enabled = false` 时，
      **已产生的待收款订单 MUST 照旧能确认收款、能取消**（FR-210）—— 断言要覆盖这一点
- [x] T473 [US26] 服务端：`buildTradeOrder` 里按 `payChannelCode=offline` **插子表行**，
      并**跳过** `createPayOrder`（`afterCreateTradeOrder` 里那个 `if (payOrderId > 0)` 分支）
- [x] T474 [US26] 服务端：结算响应 `AppTradeOrderSettlementRespVO` 加 `offlinePayEnabled` /
      `offlinePayTip`（前端据此决定"能不能选"；**完整账户信息不放在结算响应里**，见契约 §2）
- [x] T475 [US26] 服务端：订单详情 `AppOrderInvoiceRespVO` 同级加 `offlinePay`
      （`waiting` + 户名/账号/开户行/提示语；**不是对公单时为 null**）
- [x] T476 [US26] C 端 `types/order.ts` + `api/order.ts`：加上述字段与 `payChannelCode` 入参
- [x] T477 [US26] C 端 `CheckoutView.vue`：结算页加「对公转账」支付方式（`offlinePayEnabled` 为 false 时不显示）；
      选了它就把 `payChannelCode` 带进下单请求（FR-199 / Q41）
      📌 位置（所有者 2026-10-16 连着调了两轮）：卡片放在「**商品清单**」**正下方**；
      且「优惠券」挪到「发票」之后。**最终左列顺序**（已用用例按 `.ml-card-title` 的顺序钉住）：
      收货地址 → 商品清单 → **支付方式** → 发票 → 优惠券 → 买家留言。
      理由说得通：**先看买什么、再看怎么付**；券/票这类"附加动作"往后排。
      📌 同时（同一次发版）把**右侧那一列**（金额明细 + 提交订单）做成 **sticky 跟着滚动**
      （`.co-aside`）—— 清单长时不用滚到底才能提交。
      ⚠️ sticky 的三个前提写在样式注释里：① `.ml-2col` 是 `align-items: start`，
      栅格项没被拉伸，sticky 才有移动空间；② `top: 100px` = 站内惯例
      （顶栏 `.header` 76px + 24px，见 `.account-sidebar`）；③ **1100px 以下关掉** ——
      栅格塌成单列后右列在清单下面，再粘住会盖住内容。
- [x] T478 [US26] C 端 `OrderDetailView.vue`：「待收款」区块（收款账户 + "确认到账后发货"的说明）
      ⚠️ **该分支必须排在 `needsNoPay` 之前**（R47），否则对公单会显示"无需支付"
      📌 版式（所有者 2026-10-16 指定）：待收款单的「**取消订单**」放在**状态行内**
      （状态文案 + 标签 + 取消订单同一行，按钮右对齐），**整条 `.od-actions` 不再渲染** ——
      否则会剩一个只装一颗按钮的空白操作条（截图里就是这样）。**普通待支付订单的版式不变**。
      已用两条用例钉住"按钮在哪"与"普通单不受影响"，防止以后挪回去。
      —— ✅ 实现方式：`needsNoPay` 与新增的 `canPayOnline` **都排除** `offlineWaiting`；
      状态文案/说明/支付截止三处也各自让位（「待收款」/「按下方账户转账」/不显示截止）
- [x] T478b [US26] **（任务清单外的补充）列表项加 `offlineWaiting` + C 端列表让位** —— FR-200 原文
      写的是"**订单列表**与详情也 MUST 显示「待收款」"，而 T478 只覆盖了详情。
      不补的话列表上会出现 **「待支付」+「立即支付」**：点进去是个没有支付按钮的详情页
      （违反 FR-200 / FR-201 的"客户侧没有在线支付入口"）。
      落点：`AppTradeOrderPageItemRespVO.offlineWaiting` + `AppTradeOrderController.getOrderPage`
      批量赋值（**一次 IN，不逐笔查**）；`TradeOrderOfflinePaymentService#getWaitingReceiptOrderIds`；
      C 端 `OrderListView.vue` 的标签文案与「立即支付」入口。
      **底层状态值不变**，所以列表筛选仍叫「待支付」（对公单本来就在那一格）
      —— ✅ 服务端 1 条（`getWaitingReceiptOrderIds`）+ C 端 5 条

## Phase 4: US27 —— 财务确认收款 + 清理弃单（P1）

**独立可测**：后台点「确认收款」并填流水号 → 订单变「待发货」、退款能走通；弃单能被取消；
对公单不被 2 小时超时砍掉。

### 测试（先写、先失败）

- [x] T479 [US27] 服务端：确认收款 —— 订单进「待发货」+ 子表回填（流水号/确认人/确认时间）；
      **不填流水号被拒**；**重复确认幂等**；**非对公单被拒**；**已支付的被拒**（FR-203~208 / SC-071/072）
      —— ✅ 落点：**本模块第一个** mock 一大串外部 API 的服务测试
      `TradeOrderUpdateServiceImplTest`（`BaseDbUnitTest` + `@Import` 两个真实 Service +
      `@MockitoBean` 其余 + H2 真库）。**9 个用例**：确认成功 / 重复幂等（含"痕迹没被第二次盖掉"）/
      非对公单被拒 / 已取消被拒 / 自动取消跳过对公单 / 直接调单个订单也不砍 / 后台取消成功（带原因留痕）/
      已支付不能取消 / 取消后不能再确认收款
      ⚠️ 两个**造数要求**（漏了会以假乱真地红）：① 订单必须带**一条订单项**——
      `TradeOrderConvert` 取 `orderItems.get(0).getSpuName()` 当支付单 subject，没有项直接下标越界；
      ② `payOrderApi.getOrder(...)` 必须返回一张**形态正确**的支付单（SUCCESS + 金额/商户单号对齐），
      否则 `updateOrderPaid` 的复核会拒 —— 这正好印证了 R43 的"必须造一张对得上的单"
- [x] T480 [US27] 服务端：确认收款后**退款能走通**（证明确实复用了支付单链路，SC-073）
      —— ✅ 分两半钉住（**都在 mock 之外的真实代码上**）：
      ① **pay 侧**（`PayOrderServiceTest#testCreatePaidOfflineOrder_success`）：造出来的单
      状态 SUCCESS、金额/`merchantOrderId` 与入参一致、**产出 `pay_order_extension`**、
      建了支付通知任务；② **trade 侧**（T479 的用例）：捕获传给 `createPaidOfflineOrder` 的 DTO，
      断言 `merchantOrderId == 订单 id`、`price == 订单实付`。
      ⚠️ 另加 `OfflinePayClientTest` **4 条**：工厂注册（R46）/ **下单一律抛异常**（钱没到账绝不能标已支付）/
      **退款返回成功**（记账，SC-073）/ 查询抛异常
      📌 退款按 `merchantOrderId` 找支付单（`PayRefundServiceImpl`），**不看** `trade_order.pay_order_id`——
      所以链路通不通取决于"有没有那张对得上的支付单"，上面两半正是它
- [x] T481 [US27] 服务端：**自动取消的候选集里没有对公单**（R45）——
      ⚠️ 这条要**同时覆盖两处**：主查询的筛选、以及 `cancelOrderBySystem(order)` 里那句
      无条件读 `order.getPayOrderId()` 的兜底；同时断言**普通未支付单照旧被取消**（FR-212 / SC-075）
      —— ✅ 已**实测"拆掉筛选就变红"**（临时把主查询的排除去掉 → `expected: <1> but was: <2>`）。
      📌 实测还发现一处**只有计数能抓住**的细节：只拆主筛选时，对公单的**状态其实没被改**
      （第二道保护挡住了），红的是**计数**——所以 `assertEquals(1, count)` 那句是**载荷**，别当装饰删掉。
      📌 **第三处同类坑（清单外，实现时发现）**：`cancelOrderByMember` 里也有一句无条件的
      `payOrderApi.getOrder(order.getPayOrderId())` —— 客户取消一笔**对公待收款单**（quickstart §三.5
      的正常路径）就会拿 null 当 id 去查支付单。已一并加 `payOrderId != null` 守卫。

### 实现

- [x] T482 [US27] 服务端：新增 `confirmOfflinePaid(...)` 服务方法 ——
      ✅ 落在 `TradeOrderUpdateServiceImpl`（订单域，与 `updateOrderPaid` 同一处）。顺序：
      校验 → **先落子表的确认痕迹**（它是幂等闸门）→ 造 offline 已支付单（复用
      `TradeOrderConvert.convert` 保证与在线支付同形）→ `getSelf().updateOrderPaid` →
      **把 `pay_order_id` 写回订单**（`updateOrderPaid` 不写它，线上支付是在 `createPayOrder` 里写的）。
      调 T465 的能力造支付单 + 调**既有的** `updateOrderPaid` + 回填子表；
      ⚠️ **不要自己改订单状态**（R43：退款挂在支付单上）
      📌 落点放在**订单域**（`TradeOrderUpdateService`，或另建一个对公转账服务）——
      分析时纠正（I1）：原先写成"`TradeOrderInvoiceService` 同级"，那是**开票**域的类名，
      对公转账与开票无关，读的人会找错地方
- [x] T483 [US27] 服务端：`TradeOrderController`（admin）加 `PUT /trade/order/confirm-offline-paid`
      + `@PreAuthorize("@ss.hasPermission('trade:order:confirm-paid')")`
      —— ✅ 同文件另加 `PUT /trade/order/cancel`（T486）；两个入参 VO 都带 `@Valid` 校验
- [x] T484 [P] [US27] **SQL** 菜单权限点脚本：登记 `trade:order:confirm-paid`
      —— ✅ `sql/mysql/013-corporate-transfer-menus.sql`（幂等；**用子查询取 parent_id**
      而不是写死 270 —— 008 的菜单脚本写死了 266 并因此留了一句"换环境前先核对"）
      ⚠️ **脚本本身尚未在生产执行**，也**没有自动化测试**（"超管看不到按钮"只能手工验）
      ⚠️ 漏了 = **超管也看不到按钮**，而接口是通的（004 踩过，只能手工验）
- [x] T485 [US27] 服务端：改 `cancelOrderBySystem()` **两处**（见 T481）
      —— ✅ 主查询按 `getWaitingReceiptOrderIds` 过滤 + 单个订单方法的 `isWaitingReceipt` 前置保护，
      并把那句兜底改成 `payOrderId != null` 才查支付单
- [x] T486 [US27] 服务端：后台「取消订单」服务方法 + `PUT /trade/order/cancel`
      （复用既有的 `cancelOrder0`；只能取消**未支付**订单 —— 已支付的走退款）(FR-213/214)
      —— ✅ `cancelOrderByAdmin`；权限沿用 `trade:order:update`；取消原因写进**商家备注**留痕；
      新增 `TradeOrderCancelTypeEnum.ADMIN_CANCEL(50)` 与 `TradeOrderOperateTypeEnum.ADMIN_CANCEL(42)`
      （后者的编号正是原代码里 `// 42 预留：管理员取消订单` 那一行）
- [x] T487 [US27] 服务端：订单列表按 `payChannelCode=offline` 筛选 → 服务端**转成子表 `inSql`**
      —— ✅ `TradeOrderMapper` 里抽出 `applyPayChannelCodeFilter`，**列表与统计两个 wrapper 各一个重载**：
      只改列表会出"筛得出、统计数不对"（与自动取消那两处同一类坑）。
      测试落在新的 `TradeOrderMapperTest`（H2 真跑 SQL）：对公待收款单**能被筛出来**（按列筛会一条都没有）、
      确认后仍在结果里（筛的是"是不是对公单"）、其余渠道行为不变、不传时不筛
      （不能直接按 `pay_channel_code` 筛：那列在确认收款前是空的，见 data-model §5）(FR-209)
- [x] T488 [P] [US27] 管理端：`views/mall/trade/order/form/` 新增「确认收款」弹层（照 `OrderDeliveryForm` 形态）
      + 详情页按钮用 `v-hasPermi="['trade:order:confirm-paid']"`
      —— ✅ `OrderOfflineConfirmForm.vue`（流水号必填的 `el-form` 校验 + 弹层里先亮出订单号与应付金额）。
      详情页把「确认收款」放在**独立的一行** descriptions-item 上（**不能**塞进
      `trade:order:update` 那一行 —— 那会让"只有 confirm-paid 权限"的人看不到）。
      另加「取消订单」按钮 + 「对公转账」痕迹块（流水号/打款方/到账时间/确认人/确认时间，FR-205 可追溯）
- [x] T488b [US27] **（任务清单外的补充）管理端两个订单 VO 加"是不是待收款单"** —— 清单里只写了
      "详情页按钮用 v-hasPermi"，但**按钮没有依据可判**：对公单在确认收款前 `payChannelCode`
      是**空的**（那一列记的是"支付成功的渠道"），别的字段也分不出来。
      落点：`TradeOrderDetailRespVO` + `offlineWaiting` / `offlinePayment`（含确认痕迹），
      `TradeOrderPageItemRespVO` + `offlineWaiting`；判定在服务端拼（`buildOfflinePayment`），
      列表侧一次 IN 批量查。**同 T478b 的性质：FR 原文要求、原清单漏了**
      —— ✅ C 端已有同类字段，管理端补上后两端口径一致
- [x] T489 [P] [US27] 管理端：订单列表操作列加「取消订单」+ 筛选区的「支付方式」下拉
      （补了字典后自动出现「对公转账」）
      —— ✅ 筛选区的「支付方式」下拉**本来就有**（绑 `PAY_CHANNEL_CODE` 字典），
      012 补了字典行就自动多出「对公转账」；补的是操作列：更多菜单里的「确认收款」
      （**再加一层 `checkPermi`** —— 它挂在 `trade:order:update` 的下拉里，不能只有那一层）
      与「取消订单」；状态列在 `offlineWaiting` 时显示「**待收款**」标签而不是「待支付」
- [x] T490 [P] [US27] 管理端：交易配置页加「对公转账」一块（启用 + 户名 + 账号 + 开户行 + 提示语）
      —— ✅ `views/mall/trade/config/index.vue` 新增一个「对公转账」页签 + `ConfigVO` 补 5 个字段。
      **户名/账号/开户行留空由使用者填**（业务事实不编造）；页签里写明"关掉只挡新下单、
      已产生的待收款订单不受影响"（FR-210 的下半句）

## Phase 5: Polish

- [x] T491 全量回归：后端 trade 模块全部单测、`storefront` 全量 vitest + `vue-tsc`、
      —— ✅ 2026-10-16 实测：
      · **trade-server 90/90**（本特性从 74 → 90：+4 mapper、+3 offline 确认、+9 order 服务）
      · **pay-server 187 跑完，只有 5 个 `PayOrderServiceTest#testExpireOrder_*` 失败** ——
        这 5 个是**既有的**（用 `git stash` 把那一个测试文件还原到 HEAD 后**照样红**，
        原因是 `LocalDateTime` 的**纳秒精度**在 H2 存取时被截断，`assertPojoEquals` 因此不等）。
        **与本特性无关，未修**（不属于本次改动面）。
      · **storefront 1285/1285**、`vue-tsc` 干净
      · **管理端 `npm test` 35/35**；`npm run ts:check` 有 **8 处既有报错**
        （fms/Login/FilterTagForm/006 的发票 API/UserBasicInfo —— 都不是本特性碰过的文件），
        本特性新增/改动的 5 个文件**零报错**
      · `check:placeholders` 仍是既有的失败（种子文件 233 处 `p('…')`）
      管理端 vitest + eslint、两端构建（**与改动前的告警数逐条对比**）
      · 管理端这 7 个改动/新增文件 `eslint` **零问题**；
      · **两个前端都能构建**：管理端 `vite build --mode prod` ✓（56s）、storefront `npm run build`
        （含 `vue-tsc --noEmit`）✓（8s）
- [x] T495 **（发版当天发现的事故与修复 —— 清单外）** 结算页对**每个客户** 500
      📌 **根因**：`trade_config` 表里**只有 `tenant_id = 1` 的一行**（线上实测），
      本项目租户 162 **没有行** → `TradeConfigService.getTradeConfig()` 返回 **null** →
      而 T474 在 `settlementOrder` 里**无条件**解引用它（`tradeConfig.getOfflinePayEnabled()`）。
      **"没有配置"被写成了 NPE，而不是"对公转账不开放"。**
      📌 **教训（比这次修复重要）**：`@MockitoBean` 的默认返回值就是 `null`，而线上"查不到配置"
      也是 `null` —— **mock 出来的 null 不会自己变红**，只有把这条真实状态**写进用例**才会被看见。
      T474 当时只验了"字段有没有下发"，没验"查不到配置时会怎样"。
      📌 **修复**：三处都改成 `ObjUtil.defaultIfNull(..., new TradeConfigDO())`（与
      `AppTradeConfigController` 既有写法一致）—— `settlementOrder`、`validatePayChannelCode`、
      `AppTradeConfigController` 同级的 `buildOfflinePay`；并补**回归用例**
      `testSettlementOrder_tradeConfigMissing_shouldNotThrow`（**先红后绿**：红的时候
      抛的正是 `TradeConfigDO.getOfflinePayEnabled()` on null，与线上一致）。
      ✅ **影响面**：发版后线上 `NullPointerException` 计数 = **0**（那条路要登录才到得了，
      期间没人走到结算），**无实际客户影响**；修复版 17:32 已发。
      ✅ 已核实线上 jar 的 md5 与本地构建**完全一致**。
      ⚠️ **同类隐患（既有、未改，仅记录）**：`BrokerageUserServiceImpl#getOrCreateBrokerageUser`
      里也直接解引用 `getTradeConfig()` —— 租户没有配置行时会 NPE。它只从 C 端「我的分销」页进入，
      本站未用分销所以没暴露。**不在本次改动面**，但建出配置行后自然消失。

- [ ] T496 **（用户实操发现的第二处）** 普通订单的支付页多出一个**名叫「offline」的渠道**
      📌 **根因**：`012` 把 `offline` 插进 `pay_channel` 且**启用**（退款要按渠道取 `PayClient`），
      于是它跟着 `/app-api/pay/channel/get-enable-code-list` 一起返回；而 C 端对
      **没登记过的编码**是"回落用编码当名字"（`utils/payChannel.ts` 的 `CHANNEL_LABELS[code] ?? code`）
      → 界面上就凭空多出一个「offline」。客户真去点它必然失败（`OfflinePayClient.doUnifiedOrder` 抛异常）。
      📌 **为什么必须后端改**：「哪个渠道能在**支付页**选」是业务规则，不是展示问题
      （原则 III：前端只做减法、不做加法）。改在 `AppPayChannelController`，
      该接口的语义本就该是"客户在支付页**能选**的渠道"。
      📌 **修法**：从返回值里滤掉 `PayChannelEnum.OFFLINE`（**只改 app 侧那个接口**，
      管理端的 `get-enable-code-list` 不动 —— 运营要能看到这个渠道）。
      ✅ 用例 `AppPayChannelControllerTest`（2 条，`BaseMockitoUnitTest`）。
      **已实测"拆掉过滤就红"**（`assertFalse(codes.contains("offline"))` 失败）。
      ✅ pay 模块 189 条跑完，除既有的 5 个 `testExpireOrder_*` 外全绿。
      ✅ **已发版（18:33）并核实**：线上 jar 的 md5 与本地构建一致（`a84100b7…`），
      且 `AppPayChannelController.class` 的常量池里已引用 `OFFLINE` / `Collectors`
      （修复前这个类只做 `convertSet`，不会有这些）。
      ⚠️ **端到端只差一步人工确认**：那个接口要登录（本机 curl 得 401），
      所以"线上真的不返回 offline 了"要靠**打开一个普通待支付订单的支付页**看 ——
      那个名叫「offline」的渠道应当消失。

- [ ] T492 手工验收 `quickstart.md` §一~§四（**对公下单免在线支付** / **确认收款进待发货能退款** /
      **弃单能取消且不被超时砍** —— 这三条是本特性的判据）
- [x] T493a **`012` / `013` 已在生产库执行并回查**（2026-10-16 17:0x，所有者明确点名后执行）
      · `012`：子表 ✓、`trade_config` 5 列 ✓、`offline` 渠道 **id=55 / app_id=10（启用中的 mall）**
        且**恰好一行** ✓、字典 `pay_channel_code/offline` ✓（id=1411）
      · `013`：菜单 **id=12742「确认对公转账收款」type=3 parent=581** ✓
      · 发版顺序照 spec：**先脚本 → 再后端 → 最后两个前端** ✓
      · 前端入口：storefront `assets/index-iijcsndW.js`、admin `assets/index-CwGL-Iyy.js`
      · 回滚包：`/root/yudao-server.jar.bak-20261016-173222`（修复版前）、
        `/root/maxlabel-www/index.html.bak-20261016-171441`（storefront）、
        `.../admin/index.html.bak-20261016-171646`（管理端）

- [ ] T493 交易配置里**填真实收款账户**（所有者）—— ⚠️ 见下面这条**必须知道**的事
      （业务事实，留占位符的必须由所有者替换）+ 发版（顺序：**脚本 → 后端 → 两个前端**）
- [ ] T494 提交

---

## 需求 → 任务 对照

| 需求 | 任务 |
|---|---|
| FR-199 结算页可选对公转账、不建在线支付单 | T469, T472, T477 |
| FR-200 订单处于「待收款」且**客户列表与详情**都可见 | T469, T475, T478, **T478b** |
| FR-201/207 待收款单**没有在线支付入口**、普通单不能被确认 | T471, T479, **T488b** |
| FR-205 确认人与确认时间可追溯 | T479, **T488b** |
| FR-201 详情展示收款账户、无在线支付入口 | T471, T475, T478 |
| FR-202 不能自行改回在线支付 | T477（下单前定，无改的入口）, T478 |
| FR-203 确认收款 + 独立权限点 | T482, T483, T484 |
| FR-204 流水号必填 | T479, T482 |
| FR-205 记录确认人与时间 | T479, T482 |
| FR-206 幂等 | T479 |
| FR-207 只作用于待收款 | T479 |
| FR-208 造 offline 已支付单、复用既有链路 | T465, T480, T482 |
| FR-209 能从列表筛出待收款 | T487 |
| FR-210 收款账户可配置 | T463, T467, T490 |
| FR-211 不影响既有在线支付订单 | T470, T481, T491 |
| FR-212 不参与超时自动取消 | T481, T485 |
| FR-213 后台能取消订单 | T486, T489 |
| FR-214 取消后不可再确认收款 | T479, T486 |
| FR-215 不改开票口径 | T491（回归） |
| Q41~Q46 的六条口径 | 分散在 T469~T490 的注释里逐条引用 |

## Dependencies

```
Phase 1 (T461~T462, 已完成)
   └─► Phase 2 Foundational (T463~T468)   ← US26/US27 都卡在这里
          ├─► Phase 3 US26 (T469~T478, T478b)  →  客户能下单
          └─► Phase 4 US27 (T479~T490)  →  财务能核销、能清弃单
                     └─► Phase 5 Polish (T491~T494)
```

- **US26 与 US27 之间**：US27 依赖 US26 产生的数据（一笔待收款订单），但**代码上互不依赖** ——
  可以先各自实现，最后联调。**MVP = US26 + Foundational**（客户能下单了，但财务还得手工处理）。
- ⚠️ **T465（pay 侧造已支付单）是 US27 的硬前置**，但它属于 Foundational —— 因为
  "确认收款"这条路必须先有它才能成立。

## Parallel Execution Examples

- **Phase 2 里**：T463（SQL）/ T464（枚举）/ T466（DO+Mapper）/ T467（交易配置字段）**四件互不相干**，可并行
- **US26 的测试**：T469（服务端）/ T470（C 端结算）/ T471（C 端详情）**三个文件**，可并行
- **US27 的管理端**：T488（确认收款弹层）/ T489（取消+筛选）/ T490（交易配置页）**三个文件**，可并行

## 本清单里要特别当心的四条

1. **T468（补 H2 建表）** —— 漏了它，本模块**所有**测试类**启动就挂**，很容易被误读成"业务代码有问题"。
2. **T481/T485（自动取消两处）** —— 只改主查询、漏了那句无条件读 `payOrderId` 的兜底，
   对公单被选中时会把 **null 传进 pay RPC**。
3. **T478（C 端分支顺序）** —— `needsNoPay` 的条件与对公单**完全重合**，
   分支顺序放错就会把"要打款"显示成"**无需支付**"。
4. **T484（权限点脚本）** —— 漏登记 = **超管也看不到按钮**，而接口是通的；**这条只能手工验**。
5. **T478b（列表那一半）** —— FR-200 要求的是"列表**与**详情"，原清单只写了详情。
   漏掉列表的后果是客户在列表上看到「**待支付**」+「立即支付」，点进去却没有支付按钮。
   📌 这是**实现期间比对 FR 原文时补的**（不是分析阶段发现的），清单与覆盖表已同步。
6. **T488b（管理端同样需要"是不是待收款单"）** —— 同一条病根的另一半：按钮的**依据**
   不在任何现成字段里（对公单确认前 `payChannelCode` 是空的）。
   📌 一并记下：**"某条 FR 说的是两处，清单只写了一处"** 这个模式在本特性里出现了**三次**
   （客户列表 / 管理端标记 / 自动取消的第三处 `cancelOrderByMember`）—— 下次做跨端特性时，
   **先按 FR 原文逐字核对"哪几处"**，别只按"哪几个页面"列任务。
