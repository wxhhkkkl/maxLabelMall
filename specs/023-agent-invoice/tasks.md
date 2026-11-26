# Tasks: 代客开票（管理端代客户申请发票）

**Input**: Design documents from `/specs/023-agent-invoice/`
**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/admin-api.md](./contracts/admin-api.md)

**Tests**: 宪法原则 I（测试先行，不可协商）—— 每个故事先写测试并确认失败，再实现。

**Organization**: 按用户故事分组；US1 为 MVP，US2 复用同一套字段组件与校验。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: US1 / US2
- 所有任务带精确文件路径

## Path Conventions

- 后端：`yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/{main,test}/java/...`
- 管理端：`yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/...`

---

## Phase 1: Setup（共享基础设施）

无项目初始化工作：**不新增依赖、不新增表/列、无 SQL 迁移、不新增权限点**（见 plan.md）。

---

## Phase 2: Foundational（阻塞性前置）

**Purpose**: 两个故事共用的 VO/类型与前端字段组件。

- [x] T001 [P] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/trade/invoice/InvoiceFieldsForm.vue` 抽取发票字段组件（票种/抬头类型/抬头名称/税号/地址/电话/开户行/账户/行号/邮箱；`v-model` 字段对象；`disablePersonal`；`defineExpose({ validate })`），并先写 `InvoiceFieldsForm.spec.ts` 覆盖必填矩阵（企业+普票/专票要名称+税号、个人+普票只要名称且禁专票、收据只要名称、邮箱格式）；再将 `InvoiceEditForm.vue` 改为复用该组件，确认 `InvoiceEditForm.spec.ts` 仍全绿
- [x] T002 [P] 在 `.../trade/controller/admin/order/vo/AdminAgentOrderCreateReqVO.java` 增加 `private AppInvoiceApplyBaseVO invoice;`；新建 `.../controller/admin/order/vo/AdminTradeOrderInvoiceCreateReqVO.java`（`@NotNull Long orderId` + 继承 `AppInvoiceApplyBaseVO`）；在 `.../trade/enums/order/TradeOrderOperateTypeEnum.java` 增加 `ADMIN_APPLY_INVOICE(51, "后台代客户申请发票")`
- [x] T003 [P] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/trade/invoice/index.ts` 增加 `createInvoiceByAdmin(data)`（`POST /trade/order-invoice/create`）；在 `src/api/mall/trade/order/index.ts` 给 `AgentCreateReq` 增加可选 `invoice`，给订单详情 VO 增加 `enterprise?: boolean`

**Checkpoint**: 共享类型与字段组件就绪，两个故事可并行推进

---

## Phase 3: User Story 1 - 代客下单时直接带发票 (Priority: P1) 🎯 MVP

**Goal**: 代客下单表单可勾选开票，填完整 006 字段；订单与发票同一事务创建。

**Independent Test**: 勾选开票并填写企业普票 → 创建成功且订单详情「待开票」；非法发票 → 订单不创建；
实付 0 开关禁用；企业授权单禁用个人抬头。

### Tests for User Story 1（宪法原则 I 要求，先写并确认失败）

- [x] T004 [P] [US1] 在 `.../src/test/java/cn/iocoder/yudao/module/trade/service/order/AgentOrderServiceTest.java` 写失败测试：① 带合法发票下单 → 订单与 `trade_order_invoice` 各一条且金额=实付；② 发票缺税号 / 个人开专票 → 抛 `InvoiceValidator` 错误且**订单未创建**；③ 含企业授权商品的订单 + 个人抬头 → `ENTERPRISE_ORDER_INVOICE_TITLE_REQUIRED`；④ 不传 invoice → 无发票记录（行为不变）
- [x] T005 [P] [US1] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/trade/order/agent/agentOrder.spec.ts` 写失败测试：`buildAgentCreateReq` 在开启开票时带 `invoice`、关闭时不带；在 `agent/` 组件测试中覆盖：开关默认关、实付 0 时禁用、`settlement.enterprise=true` 时禁用「个人」
- [x] T006 [US1] 运行后端 `-Dtest=AgentOrderServiceTest` 与管理端 `npx vitest run src/views/mall/trade/order/agent`，确认 T004/T005 的用例**按预期失败**（不是导入/拼写错误）

### Implementation for User Story 1

- [x] T007 [US1] 在 `.../trade/service/order/AgentOrderServiceImpl.java#create` 中 `createReqVO.setInvoice(reqVO.getInvoice())`，复用 `createOrder0` 既有事务落库；确认企业抬头校验由 `validateEnterpriseOrder` 生效
- [x] T008 [US1] 在 `.../agent/agentOrder.ts` 让 `buildAgentCreateReq` 接收并透传 `invoice`；在 `.../agent/index.vue` 的「价格与支付」卡片增加「开具发票」开关（默认关）与 `InvoiceFieldsForm`（`settlement.price.payPrice <= 0` 禁用开关并提示；`settlement.enterprise` 时禁个人；提交前 `validate()`）

**Checkpoint**: US1 独立可用；测试全绿

---

## Phase 4: User Story 2 - 后台为已有订单补申请发票 (Priority: P2)

**Goal**: 运营在订单详情为满足条件的订单代客户补申请发票（所有订单）。

**Independent Test**: 已支付/未取消/实付>0/无发票的订单可提交补申请；不满足条件时入口不可用、接口拒绝；
重复申请被拒；企业授权单个人抬头被拒；成功后 C 端显示「待开票」。

### Tests for User Story 2（宪法原则 I 要求，先写并确认失败）

- [x] T009 [P] [US2] 在 `.../src/test/java/cn/iocoder/yudao/module/trade/service/invoice/TradeOrderInvoiceServiceImplTest.java` 写失败测试：`createInvoiceByAdmin` 成功路径；未支付 / 实付 0 / 已取消 / 已有申请 / 企业单个人抬头 → 对应既有错误码；成员补开 `createInvoiceByMember` 行为不变；**补申请后 `trade_order_log` 存在 `ADMIN_APPLY_INVOICE` 记录且操作人为当前后台账号（FR-607 / SC-229）**；**申请前后订单的 `pay_price / pay_status / status` 不变（FR-609）**
- [x] T010 [P] [US2] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/trade/order/detail/index.spec.ts` 写失败测试：入口仅在 `无 invoice && payStatus && status≠40 && payPrice>0` 时显示；点击提交调用 `createInvoiceByAdmin` 并重新拉取详情；`enterprise=true` 时禁用个人抬头
- [x] T011 [US2] 运行后端 `-Dtest=TradeOrderInvoiceServiceImplTest` 与管理端 `npx vitest run src/views/mall/trade/order/detail/index.spec.ts`，确认 T009/T010 **按预期失败**

### Implementation for User Story 2

- [x] T012 [US2] 在 `.../trade/service/invoice/TradeOrderInvoiceServiceImpl.java` 抽出 `validateSupplementInvoice(order)`（订单存在/已支付/实付>0/未取消），`createInvoiceByMember` 改为复用；新增 `createInvoiceByAdmin(Long operatorUserId, AdminTradeOrderInvoiceCreateReqVO reqVO)`：调用公共校验 + `maxlabelOrgId != null` 时要求企业抬头，再走 `doCreateInvoice`；加 `@TradeOrderLog(operateType = ADMIN_APPLY_INVOICE)` 并 `TradeOrderLogUtils.setOrderInfo(...)`；接口 `TradeOrderInvoiceService` 同步
- [x] T013 [US2] 在 `.../trade/controller/admin/invoice/TradeOrderInvoiceController.java` 增加 `POST /trade/order-invoice/create`（`@PreAuthorize("@ss.hasPermission('trade:order:update')")`，`@Valid`）；在 `TradeOrderDetailRespVO.java` 增加 `enterprise` 字段，并在 `TradeOrderController#getOrderDetail` 中 `respVO.setEnterprise(order.getMaxlabelOrgId() != null)`
- [x] T014 [US2] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/trade/order/detail/index.vue` 增加「代申请发票」入口（`v-hasPermi="['trade:order:update']"`）与 Dialog（复用 `InvoiceFieldsForm`；`enterprise` 时禁个人；提交后 `getDetail` 刷新）

**Checkpoint**: US1 + US2 均可独立验证

---

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T015 运行后端 `AgentOrderServiceTest`、`TradeOrderInvoiceServiceImplTest` 及相邻测试；运行管理端 `src/views/mall/trade/order` 与 `src/views/mall/trade/invoice` 下的测试，修复回归（记录既有失败，不修）
- [x] T016 运行管理端 `pnpm ts:check`，确认**本次新增文件**不在错误列表（全项目既有错误不处理）
- [ ] T017 按 [quickstart.md](./quickstart.md) §3 在本地手工验收 SC-224~SC-229（§4 的显式不验证项不执行）
- [x] T018 更新 `specs/017-agent-order/spec.md`（FR-465 注明由 023 修订）、`plan.md`（R114/约束）、`contracts/admin-api.md`（不代申请发票那句）；在 `CLAUDE.md` 增加 023 条目（无 SQL、先发后端再发管理端）
- [x] T019 核对最终 diff：**无 SQL 文件、无 `system_menu`/权限数据变更**；确认 C 端与门面站零改动

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational**: T001/T002/T003 无依赖，可并行
- **US1**: 依赖 T002、T003；T004/T005 先写并失败（T006）后实现（T007/T008）
- **US2**: 依赖 T001、T002、T003；T009/T010 先写并失败（T011）后实现（T012/T013/T014）
- **Polish**: 依赖 US1 + US2

### Within Each User Story

- 测试先写 → 运行确认失败 → 实现 → 测试全绿

### Parallel Opportunities

- T001 / T002 / T003 不同文件，可并行
- T004（后端测试）与 T005（管理端测试）不同模块，可并行
- T009（后端）与 T010（管理端）可并行
- US1 与 US2 在 Foundational 完成后可由不同人并行

## Implementation Strategy

### MVP First（仅 US1）

1. T001~T003 共享前置
2. T004~T006 测试先行
3. T007~T008 实现到全绿
4. 手工验证代客下单带票 → MVP 可交付

### Incremental Delivery

1. US1：下单时带票
2. US2：所有订单后台补申请
3. Polish：全量测试、类型检查、手工验收、017/CLAUDE.md 文档

## Notes

- 无 SQL、无迁移；无新权限点/菜单数据
- 不发版（宪法原则 VI）：完成后停在「已就绪、待发版」
- 上线顺序：**先发后端，再发管理端**
