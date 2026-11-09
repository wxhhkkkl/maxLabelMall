---
description: "Task list for 017 代客下单"
---

# Tasks: 代客下单（运营管理端）

**Input**: Design documents from `specs/017-agent-order/`
**Prerequisites**: plan.md · spec.md · research.md · data-model.md · contracts/ · quickstart.md

**Tests**: 本项目宪法原则 I（测试先行，不可协商）要求所有新功能先写失败测试。以下每个用户故事的
测试任务 MUST 先于实现任务完成，并确认失败原因正确（不是拼写/导入错误）。若某处确实无法先写测试，
在该任务上注明原因。

**Organization**: 按用户故事分组，每个故事可独立实现、独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US54~US59）
- 所有路径均相对仓库根 `d:\workspace\maxLabelMall`

## Path Conventions

- 后端：`yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/...`
- 支付 API：`yudao-cloud/yudao-module-pay/yudao-module-pay-api/src/main/java/...`
- 管理端：`yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/`
- 门面站：`storefront/src/`
- SQL：`yudao-cloud/sql/mysql/`

---

## Phase 1: Setup（共享枚举）

**Purpose**: 先落下跨端枚举值，后续所有故事都引用。

- [x] T001 在 `yudao-cloud/yudao-framework/yudao-common/src/main/java/cn/iocoder/yudao/framework/common/enums/TerminalEnum.java` 增加 `ADMIN_AGENT(40, "后台代客")`，确认 `ARRAYS` 自动包含 40
- [x] T002 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/order/TradeOrderOperateTypeEnum.java` 增加 `ADMIN_AGENT_CREATE(3, "运营代客下单")`
- [x] T003 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/aftersale/AfterSaleOperateTypeEnum.java` 增加 `ADMIN_AGENT_CREATE(13, "运营代客发起售后")`

---

## Phase 2: Foundational（阻塞所有故事）

**Purpose**: 数据库列、来源字典、权限点、错误码、共用字段与支付 RPC；完成前不能开始故事实现。

**⚠️ CRITICAL**: 本阶段完成前，任何用户故事都不得开始。

> 本阶段以**结构骨架**为主（列、DO 字段、DTO、枚举、错误码、VO 字段）。
> 凡是带行为判定的改动（例如 `applyAgentCreatedFilter` 的筛选语义），实现前 MUST 先补失败测试——
> T009 已把「先写 `TradeOrderMapperTest` 失败用例」并入同一任务，T030 只做口径复核。

- [x] T004 新建 `yudao-cloud/sql/mysql/036-agent-order.sql`：幂等增加 `trade_order.agent_user_id bigint NULL DEFAULT NULL COMMENT '代客下单的后台用户编号；NULL=客户自助' AFTER user_id`、索引 `idx_agent_user_id`、`terminal` 字典「后台代客/40」、两个按钮权限 `trade:order:agent-create`（父级 = `trade:order:query` 的 `parent_id`）与 `trade:after-sale:agent-create`（父级 = `trade:after-sale:query` 的 `parent_id`），均用子查询、照 `013-corporate-transfer-menus.sql`；文件头写明回滚与「先执行 SQL 再发后端」
- [x] T005 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/order/TradeOrderDO.java` 增加 `private Long agentUserId;`（放在 `userId` 之后，注释写明 NULL=客户自助）
- [x] T006 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java` 增加 `1_011_012_000~010` 段错误码（含隐藏定制 SKU、无收货地址两条；见 `data-model.md` §7；新增前先确认号段未被占用）
- [x] T007 [P] 在 `yudao-cloud/yudao-module-pay/yudao-module-pay-api/src/main/java/cn/iocoder/yudao/module/pay/api/order/dto/PayOrderSubmitReqDTO.java`、`PayOrderSubmitRespDTO.java` 新建 DTO，并在 `.../api/order/PayOrderApi.java` 增加 `submitOrder(@RequestBody PayOrderSubmitReqDTO, @RequestParam("userIp") String)`；在 `yudao-cloud/yudao-module-pay/yudao-module-pay-server/src/main/java/cn/iocoder/yudao/module/pay/api/order/PayOrderApiImpl.java` 转调 `PayOrderService#submitOrder`
- [x] T008 [P] 在 `.../controller/admin/order/vo/TradeOrderBaseVO.java` 增加 `agentUserId`；在 `.../controller/app/order/vo/AppTradeOrderDetailRespVO.java`、`AppTradeOrderPageItemRespVO.java` 增加 `agentCreated`；在 `.../convert/order/TradeOrderConvert.java` 的 `convert02` 两个重载中由 `agentUserId != null` 派生 `agentCreated`
- [x] T009 [P] **先**在 `.../trade-server/src/test/java/cn/iocoder/yudao/module/trade/dal/mysql/order/TradeOrderMapperTest.java` 写 `agentCreated=true/false/null` 的失败用例，**再**在 `.../dal/mysql/order/TradeOrderMapper.java` 增加 `applyAgentCreatedFilter`（`true → IS NOT NULL`、`false → IS NULL`、`null → 不拼`），并让 `selectPage` 与 `selectOrderSummaryGroupByRefundStatus` 共用（照 008 的 `applyPayChannelCodeFilter`）
- [x] T010 [P] 在 `.../controller/admin/order/vo/{TradeOrderPageReqVO,TradeOrderPageItemRespVO,TradeOrderDetailRespVO}.java` 增加 `agentCreated` query 与 `agentUserName` 响应字段（`agentUserId` 已在 BaseVO）

**Checkpoint**: 数据库列、字典、权限、错误码、共用 VO/筛选、支付 RPC 就绪。

---

## Phase 3: User Story 1（US54）— 运营以客户名义创建订单（P1 · MVP）

**Goal**: 选客户 → 选商品 → 选地址 → 服务端计价 → 创建订单；订单归属客户并记录代下单人与来源。

**Independent Test**: 用已有地址的客户下一笔实物单，后台列表出现订单（代下单人=当前运营、来源=后台代客），
客户账号能在 C 端看到待支付订单，金额与客户自购一致。

### Tests for US54（先写失败测试）

- [x] T011 [P] [US54] 新建 `.../trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/order/AgentOrderServiceTest.java`：客户不存在/禁用、实物/虚拟/自提商品范围、**隐藏定制 SKU（990002）拒绝**、**实物单无地址拒绝（`AGENT_ORDER_ADDRESS_REQUIRED`）**、券积分强制关闭、`addressId` 归属校验；确认失败原因为「类/方法不存在」
- [x] T012 [P] [US54] 在 `.../trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImplTest.java` 增加 `createOrderByAdmin` 用例：写 `agentUserId`、`terminal=40`、目标客户 `userId`、会员下单行为不变
- [x] T013 [P] [US54] 新建 `.../views/mall/trade/order/agent/agentOrder.spec.ts`：先写失败用例——步骤校验、请求体构造（不含 couponId/points/customization）、价格展示、提交期禁用重复提交

### Implementation for US54

- [x] T014 [US54] 新建 `.../controller/admin/order/vo/AdminAgentOrderSettlementReqVO.java`、`AdminAgentOrderCreateReqVO.java`、`TradeOrderCreateRespVO.java`（字段见 `contracts/admin-api.md` §1~§2）
- [x] T015 [US54] 新建 `.../service/order/AgentOrderService.java` 与 `AgentOrderServiceImpl.java`：校验客户存在且 `status=0`；解析 SKU→SPU，拒绝仅自提商品；用 `CustomizationConstants.HIDDEN_SKU_ID/HIDDEN_SPU_ID` 拒绝隐藏定制商品（错误码 `AGENT_ORDER_CUSTOMIZATION_NOT_SUPPORTED`）；实物单无地址时抛 `AGENT_ORDER_ADDRESS_REQUIRED`；构造 C 端 `AppTradeOrderSettlementReqVO`/`AppTradeOrderCreateReqVO` 时强制 `couponId=null`、`pointStatus=false`、不传 `customization`/`invoice`；调用 `settlementOrder` / `createOrderByAdmin`
- [x] T016 [US54] 在 `.../service/order/TradeOrderUpdateService.java` 增加 `createOrderByAdmin(Long operatorUserId, Long customerUserId, AppTradeOrderCreateReqVO)`；在 `TradeOrderUpdateServiceImpl.java` 把 `createOrder` 改为薄包装 + `createOrder0`，管理端入口写 `agentUserId`、`terminal=ADMIN_AGENT`，并挂 `@TradeOrderLog(ADMIN_AGENT_CREATE)`
- [x] T017 [US54] 在 `.../controller/admin/order/TradeOrderController.java` 增加 `POST /trade/order/agent-settlement` 与 `POST /trade/order/agent-create`，均 `@PreAuthorize("@ss.hasPermission('trade:order:agent-create')")`
- [x] T018 [US54] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/trade/order/index.ts` 增加 `AgentSettlementReq` / `AgentCreateReq` / `AgentCreateResp` 类型与 `agentSettlement()` / `agentCreate()` 方法
- [x] T019 [US54] 新建 `.../views/mall/trade/order/agent/index.vue` 及 `AgentCustomerSelect.vue`、`AgentProductSelect.vue`、`AgentAddressSelect.vue`、`agentOrder.ts`：客户搜索（复用 `getUserPage`）、商品选择（复用 `SpuSelect`，逐条加入 SKU/数量）、地址选择（复用 `getAddressList`）、服务端计价预览、提交；虚拟商品隐藏地址，实物单必选地址；通过 T013 的 spec
- [x] T020 [US54] 在 `.../router/modules/remaining.ts` 增加隐藏路由 `TradeOrderAgentCreate`（`order/agent`，`activeMenu: '/mall/trade/order'`）；在 `.../views/mall/trade/order/index.vue` 增加「代客下单」按钮（`v-hasPermi="['trade:order:agent-create']"`）

**Checkpoint**: US54 可独立验收——订单创建成功、归属与来源正确、C 端可见。

---

## Phase 4: User Story 2（US55）— 在线支付（P1）

**Goal**: 后台发起微信/支付宝真实支付，展示二维码/链接；回调成功后订单进入待发货。

**Independent Test**: 创建代客订单 → 选择已配置渠道 → 后台出现二维码 → 测试账号扫码支付 →
后台与 C 端在 10 秒内显示待发货；未支付时保持待支付。

### Tests for US55（先写失败测试）

- [x] T021 [P] [US55] 在 `AgentOrderServiceTest.java` 增加：`channelCode` 白名单（`wx_native`/`alipay_pc` 通过，`mock`/`wallet`/`wx_lite` 拒绝）、非代客订单拒绝、未支付状态校验、支付状态轮询返回 `orderStatus/payStatus`；另在 `yudao-cloud/yudao-module-pay/yudao-module-pay-server/src/test/java/cn/iocoder/yudao/module/pay/api/order/PayOrderApiImplTest.java` 覆盖 `submitOrder` 的 DTO 转换（mock `PayOrderService`）
- [x] T022 [P] [US55] 在 `agentOrder.spec.ts` 增加支付方式/渠道映射、轮询停止条件、渠道不可用时的错误展示（先写失败用例）

### Implementation for US55

- [x] T023 [US55] 新建 `.../controller/admin/order/vo/AdminAgentOrderPayReqVO.java`、`AdminAgentOrderPayRespVO.java`、`AdminAgentOrderPayStatusRespVO.java`
- [x] T024 [US55] 在 `AgentOrderService` 增加 `pay(orderId, channelCode)` 与 `getPayStatus(orderId)`：校验代客订单/未支付/`payOrderId`，调用 `PayOrderApi#submitOrder`；状态查询时若支付单已成功而订单未更新，调用 `syncOrderPayStatusQuietly` 兜底
- [x] T025 [US55] 在 `TradeOrderController` 增加 `POST /trade/order/agent-pay` 与 `GET /trade/order/agent-pay-status`，权限 `trade:order:agent-create`
- [x] T026 [US55] 在 `agent/index.vue` 增加渠道选择（微信扫码/支付宝扫码）、二维码/链接弹层、2 秒轮询、成功/关闭提示与跳转订单详情；复用 `Qrcode` 组件与现有收银台交互，不暴露 `mock`；通过 T022 的 spec

**Checkpoint**: US55 可独立验收——真实扫码支付成功后订单进入待发货。

---

## Phase 5: User Story 3（US56）— 对公转账（P2）

> 优先级 P2；若严格按 P1 → P2 交付，可先做 US57 / US58（均为 P1）。本阶段**不构成**对它们的前置依赖，
> 放在这里只是让两条支付分支（在线 / 对公）相邻。

**Goal**: 代客下单可选对公转账，订单进入待收款，财务走现有确认收款。

**Independent Test**: 选择对公转账 → 订单为待收款、无在线支付单 → 财务确认收款后进入待发货。

### Tests for US56（先写失败测试）

- [x] T027 [P] [US56] 在 `AgentOrderServiceTest.java` 增加：`payChannelCode=offline` 创建订单时不产生在线支付单；对公开关关闭时拒绝
- [x] T028 [P] [US56] 在 `agentOrder.spec.ts` 增加对公转账分支（先写失败用例）：请求体带 `offline`、不发起支付、不显示二维码、`offlinePayEnabled=false` 时不提供选项

### Implementation for US56

- [x] T029 [US56] 在 `agent/index.vue` 增加「对公转账」选项（依据结算响应的 `offlinePayEnabled`），提交时 `payChannelCode='offline'` 且不调用 `agent-pay`；成功提示「已生成待收款订单」并跳转详情；通过 T028 的 spec

**Checkpoint**: US56 可独立验收——对公代客订单可被财务确认收款。

---

## Phase 6: User Story 4（US57）— 留痕与查询（P1）

**Goal**: 后台列表/详情展示代下单人，支持代客/自助筛选，订单日志有「运营代客下单」。

**Independent Test**: 代客订单在列表显示代下单人；筛选「代客」只出代客单；详情显示代下单人与时间；
日志出现「运营代客下单」；统计与列表口径一致。

### Tests for US57（先写失败测试）

- [x] T030 [P] [US57] 复核 T009 写入的 `TradeOrderMapperTest` 用例，补充「列表与统计共用同一口径」的断言（同一文件；先确认 T009 的失败用例已存在）
- [x] T031 [P] [US57] 新建 `.../trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/admin/order/TradeOrderControllerTest.java`：`AdminUserApi` 批量解析、账号不存在时 `agentUserName=null`（可对 controller 的私有解析逻辑做抽出或使用 MockMvc）
- [x] T032 [P] [US57] 在 `.../views/mall/trade/order/index.spec.ts` 与 `components/OrderTableColumn.spec.ts` 增加筛选参数、列展示、`后台用户#{id}` fallback（先写失败用例）

### Implementation for US57

- [x] T033 [US57] 在 `TradeOrderController#getOrderPage` 与 `#getOrderDetail` 用 `AdminUserApi.getUserMap` 批量解析 `agentUserName`；`TradeOrderPageReqVO.agentCreated` 已由 T009/T010 接入
- [x] T034 [US57] 在 `.../views/mall/trade/order/index.vue` 的查询区增加「下单方式」筛选（全部/仅代客/仅客户自助，reset 时清空）；在 `components/OrderTableColumn.vue` 会员列下方显示 `代下单：{agentUserName}`（自助单显示空或「客户自助」）；在 `detail/index.vue` 订单信息增加「代下单人」与下单时间，并确认**不出现**「继续收款 / 重新发起支付」入口（FR-472）；通过 T032 的 spec
- [x] T035 [US57] 确认订单详情操作日志能展示 `ADMIN_AGENT_CREATE` 的「运营代客下单」内容（若前端日志无用户类型映射，补映射；不新增日志接口）

**Checkpoint**: US57 可独立验收——归属可查、可筛、可审计。

---

## Phase 7: User Story 5（US58）— 客户侧标识与禁自助退款（P1）

**Goal**: C 端列表/详情显示「客服代下单」；隐藏取消与退款入口；服务端同步拒绝。

**Independent Test**: 客户账号看到标识、看不到运营姓名；代客订单无取消/退款入口；
直接调接口被明确拒绝；自助订单行为不变。

### Tests for US58（先写失败测试）

- [x] T036 [P] [US58] 在 `.../trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderUpdateServiceImplTest.java` 增加会员取消代客订单被拒；在 `.../service/aftersale/AfterSaleServiceImplTest.java` 增加会员售后拒绝代客订单
- [x] T037 [P] [US58] 在 `storefront/src/utils/orderStatus.spec.ts` 与 `storefront/src/utils/afterSale.spec.ts` 增加 `agentCreated=true` 时 `canCancel` / `canApplyRefund` 返回 false

### Implementation for US58

- [x] T038 [US58] 在 `TradeOrderUpdateServiceImpl#cancelOrderByMember` 增加 `agentUserId != null` 守卫（错误码 `AGENT_ORDER_CANCEL_NOT_SUPPORTED`）；在 `AfterSaleServiceImpl#validateOrderItemApplicable` 增加会员侧守卫（错误码 `AGENT_ORDER_AFTER_SALE_NOT_SUPPORTED`）
- [x] T039 [US58] 在 `storefront/src/types/order.ts` 的 `OrderPageItem` / `OrderDetail` 增加 `agentCreated?: boolean`；`utils/orderStatus.ts#canCancel(status, noUserCancel?)`、`utils/afterSale.ts#canApplyRefund(..., noUserRefund)` 接入
- [x] T040 [US58] 在 `storefront/src/views/OrderListView.vue` 与 `OrderDetailView.vue` 显示「客服代下单」中性标识，隐藏代客订单的取消/退款入口；先写失败 spec（`OrderListView.spec.ts` / `OrderDetailView.spec.ts`）再实现

**Checkpoint**: US58 可独立验收——客户侧标识正确、双端守卫生效。

---

## Phase 8: User Story 6（US59）— 后台代客户发起售后（P2）

**Goal**: 运营在后台为代客订单代客户发起售后，复用现有审核/退款；记录发起操作人。

**Independent Test**: 已支付代客订单在后台发起售后 → 客户在「我的售后」可见 → 财务按现有流程退款；
无「代客售后」权限的账号看不到入口/调用被拒；自助订单不能用该入口。

### Tests for US59（先写失败测试）

- [x] T041 [P] [US59] 在 `.../service/aftersale/AfterSaleServiceImplTest.java` 增加：会员路径拒绝代客订单；后台路径允许代客订单并写入 `user_id=客户`、`creator=操作人`；后台路径拒绝自助订单；`ADMIN_AGENT_CREATE` 日志
- [x] T042 [P] [US59] 为 `OrderAgentAfterSaleForm.vue` / `agentOrder.ts` 先写失败 Vitest：必填、金额上限、方式切换、提交 payload

### Implementation for US59

- [x] T043 [US59] 在 `AfterSaleService` / `AfterSaleServiceImpl` 增加 `createAfterSaleByAdmin(Long operatorUserId, AppAfterSaleCreateReqVO)`：解析订单项/订单、校验 `agentUserId != null`、复用其余校验（跳过用户端三类退款守卫）、挂 `@AfterSaleLog(ADMIN_AGENT_CREATE)`
- [x] T044 [US59] 在 `.../controller/admin/aftersale/AfterSaleController.java` 增加 `POST /trade/after-sale/agent-create`，权限 `trade:after-sale:agent-create`
- [x] T045 [US59] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/trade/afterSale/index.ts` 增加 `agentCreate()`；新建 `.../views/mall/trade/order/form/OrderAgentAfterSaleForm.vue`（订单项、方式、原因、金额、描述、凭证）；在 `detail/index.vue` 增加「代客发起售后」按钮（`v-hasPermi="['trade:after-sale:agent-create']"`，仅代客订单且可售后时显示）；通过 T042 的 spec

**Checkpoint**: US59 可独立验收——后台代发售后、客户可见、权限分离。

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 全链路回归、文档与上线核对。

- [x] T046 运行后端测试：`mvn -pl yudao-module-mall/yudao-module-trade-server -am test`（⚠️ 必须带 `-am`；`pay` 模块 5 个既有失败不修）
- [x] T047 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3` 与 `storefront` 分别运行 `npm run test`，并跑 typecheck；修复本特性引入的失败
- [ ] T048 执行 `quickstart.md` §1~§4 的 V1~V10 手工验收，记录每项结果（通过/未通过/未验证）
- [x] T049 在 `CLAUDE.md` 增加 017 特性条目与上线顺序（`036` → 后端 → 管理端 → 门面站），并注明「漏跑 SQL 会 Unknown column」
- [x] T050 [P] 复核 `git diff`（仓库根）：不触碰无关代码、不删除既有日志/菜单、不修改客户自助订单的既有行为
- [x] T051 [P] 复核 `036-agent-order.sql` 幂等性、字典与两个权限点各 1 行；记录 quickstart §6 的显式不验证项（真实双渠道支付、通知、企业权益回滚、跨租户、服务端幂等）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞所有用户故事**
- **US54 (Phase 3)**: 依赖 Foundational；**MVP**
- **US55 (Phase 4)**: 依赖 US54（需要 `payOrderId` 与创建入口）
- **US56 (Phase 5)**: 依赖 US54（离线创建是同一入口的分支）
- **US57 (Phase 6)**: 依赖 Foundational 的字段与 mapper 筛选；可与 US55/US56 并行
- **US58 (Phase 7)**: 依赖 Foundational 的 `agentCreated` 字段；可与 US55/US56/US57 并行
- **US59 (Phase 8)**: 依赖 US54 + US58（售后守卫已拆分）
- **Polish (Phase 9)**: 依赖所有目标故事完成

### User Story Dependencies

- **US54**: 无其他故事依赖；是 US55/US56/US58/US59 的前置
- **US55**: 依赖 US54
- **US56**: 依赖 US54
- **US57**: 与 US54 的字段同源，但展示/筛选可独立验收
- **US58**: 与 US54 的字段同源，客户侧守卫可独立验收
- **US59**: 依赖 US54 + US58 的服务守卫拆分

### Within Each User Story

- 测试 MUST 先写并确认失败
- VO/模型 → Service → Controller → 前端
- 同一文件的任务不可并行（如 `TradeOrderUpdateServiceImpl` 的多个改动）

### Parallel Opportunities

- T002 / T003 可并行（不同枚举文件）
- T006 / T007 / T008 / T009 / T010 可并行（不同文件；T005 的 DO 字段需先于 T008 的 VO 映射）
- US55、US56、US57、US58 在 US54 完成后可由不同开发者并行
- 每个故事内的测试任务可并行
- 前端纯函数测试（agentOrder.spec.ts / orderStatus.spec.ts / afterSale.spec.ts）可并行

---

## Parallel Example: US54

```bash
# 测试先行（两个不同测试文件）
Task: "AgentOrderServiceTest：客户/商品范围/券积分关闭"
Task: "TradeOrderUpdateServiceImplTest：createOrderByAdmin 归属与来源"

# 前端与后端可并行（不同目录）
Task: "AgentOrderService + Controller 后端链路"
Task: "agent/index.vue + 三个选择组件"
```

---

## Implementation Strategy

### MVP First（US54 + US57 + US58）

1. 完成 Setup + Foundational
2. 完成 US54（创建代客订单）
3. 完成 US57（归属展示/筛选）——「后台记录是谁帮忙下单」是本需求的原话，必须与创建一起验收
4. 完成 US58（客户侧标识 + 取消/退款守卫）——**不能把客户可自行取消/退款的版本当作 MVP 发布**
5. **STOP and VALIDATE**：按 quickstart V1 / V7 / V9 独立验证
6. 再按优先级增量：US55 → US56 → US59

> 为什么 MVP 是 US54+US57+US58：只建单不展示归属，无法证明「记录是谁」；
> 不含 US58 的守卫，代客订单可被客户绕过，不能对外发布。

### Incremental Delivery

1. Setup + Foundational → 基础就绪
2. US54 + US57 + US58 → 创建 + 留痕 + 客户侧守卫（MVP）
3. US55 → 在线支付
4. US56 → 对公转账
5. US59 → 后台代客售后

> 严格按 P1/P2 优先级时，US57/US58 可以在 US56 之前交付；阶段顺序把 US56 放在 US55 之后
> 只是为了把两条支付分支放在一起，**不构成依赖**。

### Parallel Team Strategy

1. 团队共同完成 Setup + Foundational
2. Foundational 完成后：
   - 开发者 A：US54 → US55
   - 开发者 B：US57（列表/详情/日志）
   - 开发者 C：US58（C 端守卫）
   - 开发者 D：US59（后台售后，待 US58 守卫拆分后）
3. US56 可由 A 在 US55 之后补齐

---

## Notes

- `[P]` 表示不同文件、无未完成依赖；同一文件的多个改动不要并行
- 每个故事完成后按 Checkpoint 独立验收，再进入下一个优先级
- 测试任务必须先红后绿；不要先实现再补测试
- 交付前必须执行 `quickstart.md` 的 SQL 前置检查与 V1~V10
- 不新增订单表/售后表/支付渠道；所有资金动作沿用现有支付与售后链路

---

## Phase 10: Convergence

- [x] T052 为 `OrderAgentAfterSaleForm.vue` 增加售后凭证图片（`applyPicUrls`），复用现有上传组件并按 `AppAfterSaleCreateReqVO` 提交 per T045 / US59 (partial)
- [x] T053 为 `AgentOrderService#getPayStatus` 增加成功路径单测：支付单已成功但订单未更新时触发 `syncOrderPayStatusQuietly` per T022 / SC-163 (partial)
- [x] T054 在 `AgentOrderService#getPayStatus` 增加「必须是代客订单」校验，并补对应单测 per contracts/admin-api.md §4 (partial)
