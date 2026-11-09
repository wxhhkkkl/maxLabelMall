# Implementation Plan: 代客下单（运营管理端）

**Branch**: `017-agent-order` | **Date**: 2026-11-06
**Spec**: [spec.md](./spec.md) | **Base**: 009 会员体系 · 012 企业席位 · 014 印刷定制 · 008 对公转账

## Summary

让运营在管理端**以客户账户名义**创建订单，并把「代下单人」作为订单的正式属性留痕。

- **复用现有下单链路**：管理端选客户 → 选商品 → 选客户地址 → 服务端计价 → 调 `settlementOrder` / `createOrder`（把登录用户换成目标客户），不另写一套计价与订单落库；
- **支付复用现有渠道**：微信扫码（`wx_native`）/ 支付宝扫码（`alipay_pc`）在后台发起真实支付并展示二维码；对公转账沿用 `offline` 的「待收款 → 确认收款」；
- **订单新增 1 列**：`trade_order.agent_user_id`（NULL = 客户自助）；
- **新增 1 个来源**：`TerminalEnum.ADMIN_AGENT(40)` + `terminal` 字典「后台代客」；
- **新增 2 个权限点**：`trade:order:agent-create`、`trade:after-sale:agent-create`；
- **C 端只读标识 + 收紧操作**：订单列表/详情显示「客服代下单」；隐藏取消与自助退款入口，服务端同步拒绝；
- **后台代客售后**：运营在订单详情代客户发起售后，复用现有审核/退款；在线支付原路退，对公/线下由财务打款。

## Technical Context

**语言 / 栈**：Java 17+（项目以 JDK 25 运行）+ Spring Boot（yudao-cloud）；Vue 3 + TypeScript（admin-vue3、storefront）
**数据**：MySQL 8.0；`trade_order` 加 1 列 + 1 个索引；`system_menu` 加 2 个权限点；`system_dict_data` 加 1 条来源字典
**支付**：复用 `wx_native` / `alipay_pc` / `offline`；**不新增渠道**，不暴露 `mock` / `wallet`
**测试**：JUnit 5 + Mockito（trade / pay 模块）；Vitest（admin-vue3、storefront）
**接口**：管理端只走 `/admin-api`，C 端只走 `/app-api`（宪法）
**约束**：管理端代客下单不写购物车、不用券、不抵积分；不支持自提 / 印刷定制 / 代开发票
**规模**：租户内运营人数与代客订单量均较小；列表沿用现有分页，不做跨租户聚合

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 先写失败测试：`createOrderByAdmin`（归属/来源/券积分关闭/校验）、支付渠道白名单、C 端取消与退款拒绝、后台代客售后、列表筛选、admin/storefront Vitest |
| **II. 先澄清** | ✅ Q123~Q133 已拍板；无遗留 `[NEEDS CLARIFICATION]` |
| **III. 复用** | ✅ 复用 `settlementOrder` / `createOrder`、`PayOrderService#submitOrder`、`AfterSaleService` 售后状态机、`AdminUserApi`、`MemberAddressApi`、`ProductSpuApi`、admin 的 `SpuSelect` 与收银台二维码交互 |
| **IV. 最小改动** | ✅ 1 列 + 1 来源 + 2 权限点 + 1 个新管理端页面；C 端只加标识与守卫；不新建订单表、支付表或售后表 |
| **V. 可验证** | ✅ SC-161~SC-173 在 quickstart 逐条落点；真实支付与渠道未配置场景显式标注验证方式 |

**设计后复核**：Phase 0/1 未新增违规——仍是 MySQL 单一存储；管理端 `/admin-api`、C 端 `/app-api`；
未引入新前端框架或 UI 组件库；测试先行顺序在任务分解中保持。

## 关键设计决策

完整论证见 [research.md](./research.md)（R113~R123）：

| # | 决策 |
|---|---|
| R113 | 管理端代客下单复用 `settlementOrder` + `createOrder(userId=目标客户)`；新增 `createOrderByAdmin(operatorUserId, customerUserId, reqVO)`，订单同时写 `agent_user_id` 与来源 `ADMIN_AGENT` |
| R114 | 代客下单强制 `couponId=null`、`pointStatus=false`，不传 `customization` / `invoice`；商品范围、库存、企业权限全部沿用现有服务端校验；**隐藏定制 SKU/SPU 显式拒绝**（`CustomizationConstants.HIDDEN_SKU_ID` / `HIDDEN_SPU_ID`），避免落到通用价格错误；实物单无地址抛专用错误 |
| R115 | 支付发起新增 `POST /trade/order/agent-pay`，服务端白名单只允许 `wx_native` / `alipay_pc`；新增 `PayOrderApi#submitOrder`，避免前端直连 `/pay/order/submit` 时误用 `mock` |
| R116 | 对公转账复用 `payChannelCode=offline`：不建在线支付单，订单进「待收款」，财务走现有确认收款 |
| R117 | 归属只存 `agent_user_id`（后台用户编号），不冗余昵称；列表/详情用 `AdminUserApi.getUserMap` 解析，账号删除时前端显示 `后台用户#{id}` |
| R118 | 来源新增 `TerminalEnum.ADMIN_AGENT(40, "后台代客")`，并插 `terminal` 字典；订单来源筛选与报表直接可区分 |
| R119 | C 端订单列表/详情增加 `agentCreated`；前端据此显示「客服代下单」、隐藏取消与退款入口；服务端在会员取消与会员售后两个入口同步拒绝 |
| R120 | 后台代客售后新增 `POST /trade/after-sale/agent-create`，权限 `trade:after-sale:agent-create`；复用现有售后状态机，跳过「用户端不允许退款」的三类守卫，`trade_after_sale.creator` 记录操作人 |
| R121 | 列表与统计共用 `applyAgentCreatedFilter`（照 008 的 `applyPayChannelCodeFilter`），避免「列表筛得出、统计数不对」 |
| R122 | 支付只在代客下单页面内完成：页面内可重试/换渠道；离开页面后后台不提供「继续收款」，未支付订单按现有超时取消或由运营取消后重建 |
| R123 | 不新增短信/站内信通知；代客订单沿用现有积分/经验赠送规则，但不使用券、不抵积分 |

## Project Structure

### Documentation（本特性）

```text
specs/017-agent-order/
├── plan.md · research.md · data-model.md · quickstart.md
├── contracts/admin-api.md · contracts/app-api.md
└── checklists/requirements.md
```

### Source Code

```text
yudao-cloud/
├── sql/mysql/036-agent-order.sql                          # agent_user_id + 来源字典 + 2 个权限点（幂等）
├── yudao-module-mall/yudao-module-trade-api/.../enums/
│   ├── ErrorCodeConstants.java                            # 代客取消/退款/渠道/客户状态等错误码
│   ├── order/TradeOrderOperateTypeEnum.java               # ADMIN_AGENT_CREATE
│   └── aftersale/AfterSaleOperateTypeEnum.java            # ADMIN_AGENT_CREATE
├── yudao-module-mall/yudao-module-trade-server/
│   ├── dal/dataobject/order/TradeOrderDO.java             # + agentUserId
│   ├── dal/mysql/order/TradeOrderMapper.java              # applyAgentCreatedFilter（列表 + 统计）
│   ├── controller/admin/order/
│   │   ├── TradeOrderController.java                       # agent-settlement / agent-create / agent-pay / agent-pay-status
│   │   └── vo/{AdminAgentOrderSettlementReqVO,AdminAgentOrderCreateReqVO,
│   │           AdminAgentOrderPayReqVO,AdminAgentOrderPayRespVO,
│   │           AdminAgentOrderPayStatusRespVO}.java
│   ├── controller/admin/order/vo/{TradeOrderBaseVO,TradeOrderPageItemRespVO,
│   │                              TradeOrderDetailRespVO,TradeOrderPageReqVO}.java  # 归属/来源字段
│   ├── controller/admin/aftersale/AfterSaleController.java # agent-create
│   ├── controller/app/order/vo/{AppTradeOrderDetailRespVO,AppTradeOrderPageItemRespVO}.java # agentCreated
│   ├── convert/order/TradeOrderConvert.java               # agentCreated / 归属映射
│   ├── service/order/TradeOrderUpdateService(+Impl).java  # createOrderByAdmin / 取消守卫 / 支付状态
│   ├── service/order/AgentOrderService(+Impl).java        # 客户校验、商品范围、结算/创建编排、支付白名单
│   ├── service/aftersale/AfterSaleService(+Impl).java     # createAfterSaleByAdmin + 守卫拆分
│   └── src/test/java/...                                  # 先写测试
├── yudao-module-mall/yudao-module-product-api/...          # 复用 ProductSpuApi（不新增方法）
├── yudao-module-pay/
│   ├── yudao-module-pay-api/.../api/order/{PayOrderApi, dto/PayOrderSubmitReqDTO,
│   │                                     dto/PayOrderSubmitRespDTO}.java
│   └── yudao-module-pay-server/.../api/order/PayOrderApiImpl.java
└── yudao-framework/yudao-common/.../enums/TerminalEnum.java # ADMIN_AGENT(40)

yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/
├── api/mall/trade/order/index.ts                          # agent 系列接口 + 字段
├── api/mall/trade/afterSale/index.ts                      # agentCreate
├── api/member/user/index.ts                               # 复用 getUserPage
├── api/member/address/index.ts                            # 复用 getAddressList
├── views/mall/trade/order/
│   ├── index.vue / components/OrderTableColumn.vue        # 「代客下单」入口、筛选、代下单人
│   ├── detail/index.vue                                   # 代下单人、代客发起售后
│   ├── form/OrderAgentAfterSaleForm.vue                   # 代客售后弹层
│   └── agent/{index.vue,AgentCustomerSelect.vue,
│               AgentProductSelect.vue,AgentAddressSelect.vue,
│               agentOrder.ts,agentOrder.spec.ts}          # 新页面 + 纯函数测试
└── router/modules/remaining.ts                            # TradeOrderAgentCreate 隐式路由

storefront/src/
├── types/order.ts                                         # agentCreated
├── utils/afterSale.ts / afterSale.spec.ts                 # canApplyRefund 增加 agentCreated
├── utils/orderStatus.ts / orderStatus.spec.ts             # canCancel 增加 agentCreated
├── views/OrderDetailView.vue / OrderDetailView.spec.ts    # 标识 + 隐藏取消/退款
└── views/OrderListView.vue / OrderListView.spec.ts        # 标识 + 隐藏取消
```

**Structure Decision**：沿用现有单体聚合与三端结构；代客下单的编排放 trade 模块的
`AgentOrderService`，避免把管理端入参、客户校验与支付白名单塞进 C 端 `TradeOrderUpdateServiceImpl`。
支付提交能力通过 `pay-api` 暴露，符合既有跨模块 RPC 方式。

## 分阶段实施

1. **数据层 + 枚举 + 权限**：`036-agent-order.sql`、`agentUserId`、`TerminalEnum.ADMIN_AGENT`、两个操作类型
   （验证：执行 SQL 后 `trade_order` 列/索引存在、字典与菜单各 1 行；后端编译）
2. **代客下单主链路**：`AgentOrderService`（客户端校验、商品范围、隐藏定制 SKU 拒绝、无地址拒绝、结算、创建）+ `createOrderByAdmin`
   （验证：先写 `AgentOrderServiceTest` / `TradeOrderUpdateServiceImplTest`，覆盖归属/来源/券积分关闭/自提与定制拒绝）
3. **支付**：`PayOrderApi#submitOrder` + `agent-pay` + `agent-pay-status`
   （验证：渠道白名单、未配置渠道报错、支付成功后状态刷新；真实扫码走 quickstart 手工）
4. **C 端守卫**：`agentCreated` 字段、取消/退款服务端拒绝、列表/详情标识
   （验证：`AfterSaleServiceImplTest`、`TradeOrderUpdateServiceImplTest`、`orderStatus.spec.ts`、`afterSale.spec.ts`）
5. **后台代客售后**：`createAfterSaleByAdmin` + `AFTER_SALE_AGENT_CREATE` 日志 + 管理端入口
   （验证：售后单归属客户、creator 为操作人、自助订单不可用）
6. **管理端页面**：代客下单页（客户/商品/地址/支付/轮询）+ 订单列表与详情
   （验证：typecheck + Vitest；页面步骤与请求体纯函数测试）
7. **上线**：SQL → 后端 → 管理端 → 门面站（验证：quickstart §7；真实支付与渠道配置显式标注）

## Complexity Tracking

无宪法违规。跨模块新增 `PayOrderApi#submitOrder` 是为了**在服务端收口支付渠道白名单**——
若让前端直接调既有 `/pay/order/submit`，`mock` 渠道会绕过「必须真实支付」的约束（Q123），
风险高于多一个 RPC 方法。其余能力均复用现有订单、支付、售后与企业校验。
