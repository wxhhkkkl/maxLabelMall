# Research（017 代客下单）

> 编号续 016 的 R112：本文件从 **R113** 开始。
> 结论基于当前仓库代码与 2026-11-06 的所有者拍板（Q123~Q133）。

## R113 —— 复用现有下单链路，只把「登录用户」换成目标客户

**Decision**：管理端代客下单复用
`TradeOrderUpdateServiceImpl#settlementOrder(userId, …)` 与 `#createOrder(userId, …)`，
把 `userId` 传为目标客户；新增管理端入口 `createOrderByAdmin(operatorUserId, customerUserId, reqVO)`，
内部与会员下单共用同一段 `createOrder0`，只在落库时额外写 `agent_user_id` 与来源。

**Rationale**：
- 计价包含会员价、限时折扣、满减、运费模板、企业商品校验、订阅快照、库存校验等一整套规则；
  另写一套必然产生「两个真相」，且运营看到的价格与客户自购不一致（SC-165 会直接失败）。
- `createOrder` 已经处理优惠券、积分、支付单、开票、企业订单、订单日志等后置逻辑；
  代客下单只是**入参不同**，不是另一条业务链路。
- 把 `createOrder` 拆成薄包装 + `createOrder0`，可以让会员与管理端各自挂不同的
  `@TradeOrderLog` 注解，避免日志写成「用户下单」。

**Alternatives considered**：
- *由前端伪造客户登录态调 C 端 `/app-api/trade/order/create`*：拿不到客户会话，且违反「管理端只走 `/admin-api`」。
- *在管理端复制一份计价/落库逻辑*：两套真相，回归面大，违反宪法原则 III。
- *让运营直接填成交价*：绕过服务端价格规则，Q125 已否决。

## R114 —— 券与积分强制关闭，定制/发票/自提不进入代客入口

**Decision**：代客下单构造 C 端请求时固定 `couponId = null`、`pointStatus = false`，
不设置 `customization` 与 `invoice`；管理端入参不提供这些字段。对隐藏定制 SKU/SPU
（`CustomizationConstants.HIDDEN_SKU_ID` / `HIDDEN_SPU_ID`）**显式拒绝**；实物单无地址时抛
`AGENT_ORDER_ADDRESS_REQUIRED`。

**Rationale**：
- Q125 明确「不选券、不抵积分」；`TradePriceServiceImpl` 在 `pointStatus=false` 且
  `couponId=null` 时不会走券/积分计算器，行为可预测。
- 印刷定制需要材质/尺寸/工艺/文件与报价向导（014），管理端没有等价界面；Q124 已排除。
  隐藏定制 SPU 不在后台「出售中」列表，但 API 仍可直接传 `skuId=990002`；不显式拒绝会落到
  通用「价格计算不正确」，不满足 FR-435 / SC-166 的「可读原因」。
- 发票在 006 中与订单同一事务（Q32）；代客下单不传 `invoice` = 不开票，
  客户支付后仍可在 C 端用现有 `/trade/order-invoice/create` 自行申请（FR-465）。

**Alternatives considered**：
- *允许运营代选客户优惠券/积分*：需要查询客户可用券、处理锁定与退券，且与「不选券」口径冲突。
- *复用 014 定制向导*：工作量与风险都远超本特性；所有者已明确排除。

## R115 —— 支付发起在服务端收口，白名单只允许真实扫码渠道

**Decision**：
- 新增 `POST /trade/order/agent-pay`，权限 `trade:order:agent-create`；请求只接受
  `channelCode ∈ {wx_native, alipay_pc}`，拒绝 `mock` / `wallet` / 其他渠道。
- 在 `PayOrderApi` 新增 `submitOrder(reqDTO, userIp)`（`pay-api` 的 DTO + `PayOrderApiImpl` 实现），
  由 trade 模块调用现有 `PayOrderService#submitOrder`。
- 新增 `GET /trade/order/agent-pay-status?orderId=` 供页面轮询；若支付单已成功而订单未更新，
  调用既有 `syncOrderPayStatusQuietly` 兜底。
- 页面离开后不提供「继续收款」（Q130 / FR-472）。

**Rationale**：
- 既有管理端 `POST /pay/order/submit` **没有 `@PreAuthorize`**，且管理端收银台页面直接暴露
  `mock` / `wallet`。如果代客下单页直接调它，任何后台用户都能用 `mock` 把订单「付掉」，
  与 Q123「必须真实支付」冲突。
- 渠道白名单必须由服务端执行；前端只负责展示。
- `PayOrderApi` 是既有的跨模块 RPC 入口，新增一个提交方法比在 trade 模块里复制支付逻辑更符合宪法 III。

**Alternatives considered**：
- *前端直接调 `/pay/order/submit`*：白名单可被绕过，且需要额外授予 `pay:order:query` 才能轮询。
- *新增「后台收款」支付渠道*：Q123 明确不新增渠道。
- *后台直接标记已收款*：Q123 明确禁止。

**前置条件**：`wx_native` / `alipay_pc` 必须在 `pay_channel` 中为商城 `pay_app` 启用。
未启用时 `PayOrderServiceImpl#validateChannelCanSubmit` 会报错，接口按 FR-448 透出可读原因；
这**不影响**订单创建与对公转账。quickstart 必须显式验证「已配置的渠道」，
未配置的渠道标为「未验证 + 需先配置」。

## R116 —— 对公转账原样复用 008

**Decision**：代客下单选择对公转账时，`createOrder` 传 `payChannelCode = offline`；
沿用 `afterCreateTradeOrder` 创建 `trade_order_offline_payment` 行，不建在线支付单；
财务走现有 `confirmOfflinePaid`。

**Rationale**：008 已实现开关校验、待收款判定、幂等确认、支付单合成与退款链路；
重写会破坏「退款挂在支付单上」的既有不变量（008 R43）。

**Alternatives considered**：
- *新建「后台收款」渠道*：Q123 已否决。
- *代客下单时直接确认收款*：资金动作越权，且 Q123 已否决。

## R117 —— 归属用 `trade_order.agent_user_id`，不冗余昵称

**Decision**：`trade_order` 增加 `agent_user_id bigint NULL`（NULL = 客户自助），并加索引；
列表/详情通过 `AdminUserApi.getUserMap` 解析昵称；账号不存在时前端显示 `后台用户#{id}`。
`agent_user_id` 只在代客下单时写入，后台不提供修改入口。

**Rationale**：
- 「谁帮他下的单」是订单的一等属性，列表筛选、报表、导出都要用；只写订单日志无法作为查询条件。
- 不冗余昵称：后台用户改名后应显示当前昵称；历史身份以编号为准，避免两份可能不一致的姓名。
- `BaseDO.creator` 也会是操作人，但它的语义是「创建者」，且并非所有订单都有意义；
  显式列让语义清晰，也便于索引。

**Alternatives considered**：
- *单独建 `trade_agent_order` 关联表*：每笔订单最多一个代下单人，1:1 关系；独立表只增加 JOIN。
- *只依赖订单日志*：不可筛选、不可索引，且日志可被后续清理策略影响。
- *快照姓名*：改名/删除后反而误导；编号 + 实时解析更可靠。

## R118 —— 来源新增 `ADMIN_AGENT(40)`，并插字典

**Decision**：`TerminalEnum` 增加 `ADMIN_AGENT(40, "后台代客")`；`system_dict_data` 的
`terminal` 字典增加 value=40 的行；代客订单落库时 `terminal = 40`。

**Rationale**：
- Q132 要求来源筛选与报表可直接区分；落入 `UNKNOWN(0)` 会与历史来源缺失订单混在一起。
- 管理端订单列表的「订单来源」筛选使用 `@InEnum(TerminalEnum)`，枚举必须能通过校验；
  下拉展示使用 `terminal` 字典，两处必须同时补。

**Alternatives considered**：
- *沿用 `UNKNOWN(0)`*：报表无法区分。
- *复用 `H5(20)`*：伪装成客户终端，违反 Q132。
- *不加来源，只靠代下单人列*：报表按来源统计时仍然混在一起。

## R119 —— C 端标识与双端守卫

**Decision**：
- `AppTradeOrderPageItemRespVO` / `AppTradeOrderDetailRespVO` 增加 `agentCreated`；
  `TradeOrderConvert` 由 `agentUserId != null` 派生。
- storefront 列表/详情显示「客服代下单」中性标识；不展示运营姓名。
- `utils/orderStatus.ts#canCancel` 与 `utils/afterSale.ts#canApplyRefund` 都把 `agentCreated` 作为拒绝条件；
  `OrderDetailView` 同时隐藏取消与退款入口。
- 服务端 `cancelOrderByMember` 与 `AfterSaleServiceImpl#validateOrderItemApplicable` 增加守卫，
  返回明确错误码与文案。

**Rationale**：
- Q127/Q131 要求客户可见标识、不可取消、不可自助退款；前端隐藏只是减少「注定被拒」的点击，
  权威判定必须在服务端（宪法 III）。
- 032（买家删除）与 015（改地址）都出现过「只改一端」的问题；本特性两处入口（取消、退款）
  各自都有前端 + 服务端两层。

**Alternatives considered**：
- *只在 C 端隐藏*：直接调接口仍可取消/退款。
- *让代客订单在 C 端不可见*：Q127 明确要求客户能看到标识。
- *禁止客户支付代客订单*：Q123/Q130 未禁止；客户仍可正常支付。

## R120 —— 后台代客售后：独立入口，复用状态机

**Decision**：
- `AfterSaleService` 新增 `createAfterSaleByAdmin(operatorUserId, reqVO)`；
  `AfterSaleController` 新增 `POST /trade/after-sale/agent-create`，
  权限 `trade:after-sale:agent-create`（Q133）。
- 复用 `validateOrderItemApplicable` 的其余校验（订单项存在、未重复申请、金额上限、已支付、
  退货退款需已发货等），仅对**代客订单**跳过「用户端不允许退款」的三类守卫
  （代客 / 企业授权 / 个人订阅）；后台入口只允许代客订单。
- 售后单 `user_id` = 客户，`creator` = 后台操作人（BaseDO 自动填充）；
  新增 `AfterSaleOperateTypeEnum.ADMIN_AGENT_CREATE(13, "运营代客发起售后")`。

**Rationale**：
- 客户侧售后入口必须继续拒绝（FR-462）；如果给会员入口加一个「后台调用」开关，
  这个开关一旦从请求体可传就会被伪造。独立服务方法 + 独立权限点是更安全的边界。
- 复用现有售后状态机意味着审核、退款、回滚积分/经验、支付退款都无需重写。
- 在线支付退款走现有 `payRefundApi`；对公/线下退款仍由财务线下打款
  （`OfflinePayClient` 的退款是模拟成功，系统只记录状态，真实资金线下处理）。

**Alternatives considered**：
- *在会员接口上加 `admin=true` 参数*：可被伪造，且会削弱会员侧守卫。
- *不新增售后入口，只允许线下退款*：Q126 选择 C，业务上需要系统内售后记录。
- *单独建「代客退款单」*：与现有售后重复，违反宪法 III。

**注意**：代客订单如果是企业授权单/个人订阅单，后台代客售后会跳过对应的「用户端不允许退款」守卫。
这是 Q126「所有代客订单都由后台处理退款」的直接结果；企业席位回收与订阅权益回滚仍按现有支付/售后副作用执行，
quickstart 将其列为人工核对项。

## R121 —— 列表与统计共用筛选实现

**Decision**：在 `TradeOrderMapper` 新增 `applyAgentCreatedFilter(query, Boolean agentCreated)`：
`true → agent_user_id IS NOT NULL`，`false → agent_user_id IS NULL`，`null → 不拼条件`；
`selectPage` 与 `selectOrderSummaryGroupByRefundStatus` 都调用它。

**Rationale**：008 的 `applyPayChannelCodeFilter` 已经证明「只改列表不改统计」会造成
「列表 3 条、统计 10 条」的对账问题（R45）。代客筛选同样必须两处共用。

**Alternatives considered**：
- *前端本地过滤*：分页与统计都会错。
- *只在列表加条件*：统计口径不一致。

**注意**：统计的 `MPJLambdaWrapperX` 版本也要有对应方法；两个重载放在同一个 Mapper 里，
避免复制条件。

## R122 —— 支付只在代客下单页面内完成

**Decision**：不在订单详情/列表提供「继续收款」；代客下单页在提交成功后保留支付步骤，
页面内可重新生成二维码并切换微信/支付宝；离开页面后未支付订单按现有超时规则取消，
或由运营在后台取消后重建。

**Rationale**：Q130 已拍板；这样不需要为「未支付代客订单」设计额外的支付入口与权限，
也不会出现「详情页能付款、但页面不知道客户当初选了什么」的半截状态。

**Alternatives considered**：
- *订单详情提供继续收款*：Q130 已否决。
- *渠道锁定为创建时选择*：Q130 的选项 C 未采用；页面内允许重试更贴近现场。

## R123 —— 不新增通知；营销赠送沿用现有规则

**Decision**：不发送短信/站内信；客户通过订单列表/详情的「客服代下单」标识感知。
代客订单支付后仍按现有规则赠送积分/经验（除非另行确认关闭）。

**Rationale**：
- 门面站目前没有消息中心；站内信写了也看不到。
- 短信模板当前只绑定了登录/改密/忘记密码三个场景（`sms-templates-unbound` 记录），
  新增订单通知模板属于支付/通知域的独立工作，不在本特性范围。
- 代客订单是真实购买，赠送积分/经验与客户自购一致；不使用券/积分已经限制了套利面。

**Alternatives considered**：
- *短信通知*：需要新模板、渠道绑定与频控，成本与合规面都超出本特性。
- *站内信*：需要先做 C 端消息中心入口。
- *关闭赠送*：会改变现有营销规则，所有者未要求。

---

## 风险与验证要点

| # | 风险 | 处理 |
|---|---|---|
| K1 | `trade_order.agent_user_id` 未先执行 SQL → 后端 `Unknown column`（032 事故同类） | 上线顺序固定为 `036` → 后端 → 管理端 → 门面站；quickstart §1 先查列 |
| K2 | `wx_native` / `alipay_pc` 未在 `pay_channel` 启用 → 在线支付不可用 | 接口透出可读原因；quickstart 分别验证已配置渠道，未配置渠道标为「未验证 + 需先配置」 |
| K3 | 仅前端隐藏取消/退款，接口仍可调 | 服务端 `cancelOrderByMember` 与会员售后守卫同步拒绝；单测覆盖 |
| K4 | 列表与统计筛选口径不一致 | 共用 `applyAgentCreatedFilter`；mapper 测试覆盖 true/false/null |
| K5 | 代客售后误放行客户自助订单 | 后台入口只接受 `agentUserId != null` 的订单；单测覆盖 |
| K6 | `TerminalEnum` 加了值但字典没加 → 下拉空白/筛选异常 | SQL 同时插 `system_dict_data`；quickstart 核对 |
| K7 | 后台代客售后对企业/订阅订单跳过用户端守卫，权益回滚需人工核对 | 作为「显式不自动化验证项」写入 quickstart，由财务/运营核对 |
