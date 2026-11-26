# Research：代客开票

**日期**：2026-11-25 | **计划**：[plan.md](./plan.md)

无未决澄清（Q170~Q172 已拍板）。以下记录实现选型。

## R1 代客下单带发票：透传 + 复用既有事务

- **Decision**：`AdminAgentOrderCreateReqVO` 增加 `AppInvoiceApplyBaseVO invoice`；`AgentOrderServiceImpl#create`
  在构建 C 端下单请求时 `setInvoice(reqVO.getInvoice())`。落库完全沿用
  `TradeOrderUpdateServiceImpl#createOrder0` 里既有的 `createInvoice(order, invoice)`（同一事务）。
- **Rationale**：订单、发票要么都成、要么都不成（006 Q32）；服务端已有唯一入口，不另写一套。
- **Alternatives**：下单后再调补申请接口 —— 会产生「订单在、票没了」的中间态，且失败提示割裂。

## R2 后台补申请：新增一个管理端接口

- **Decision**：`POST /trade/order-invoice/create`，请求体 `orderId + AppInvoiceApplyBaseVO` 字段；
  权限复用 `trade:order:update`；服务层新增 `createInvoiceByAdmin`。
- **Rationale**：管理端没有任何「创建申请」入口（E6）；补申请必须有一个显式接口。
  权限复用避免新增菜单/按钮数据（无 SQL）。
- **Alternatives**：复用 C 端 `/app-api` 接口 —— 跨端前缀违规（宪法技术栈约束）。
  直接改库/写脚本 —— 不可审计、不可复用校验。

## R3 校验复用：抽出「补开」公共校验

- **Decision**：把 `createInvoiceByMember` 的「已支付 / 实付>0 / 未取消」抽成私有
  `validateSupplementInvoice(order)`，两个入口共用；一单一条仍由 `doCreateInvoice` 与 `uk_order_id` 保证。
- **Rationale**：避免两套校验漂移（宪法 III）；错误码沿用 `INVOICE_ORDER_*`。
- **Alternatives**：各自复制三行 —— 后续改口径必然漏一处。

## R4 企业抬头规则

- **Decision**：代客下单由既有 `validateEnterpriseOrder` 拦截（`createReqVO.invoice` 会参与）。
  补申请按订单快照 `maxlabelOrgId != null` 判断，要求 `titleKind = 企业`，否则报
  `ENTERPRISE_ORDER_INVOICE_TITLE_REQUIRED`。管理端订单详情 VO 增加 `enterprise` 布尔
  （`maxlabelOrgId != null`），前端据此禁用「个人」并提示。
- **Rationale**：企业授权订单不允许个人抬头（FR-296）；用订单快照判断，不受商品当前配置变化影响。
- **Alternatives**：补申请时重新按 SKU 查企业计划 —— 商品可能已改配置，历史订单会误判。

## R5 审计：订单日志 + creator

- **Decision**：`TradeOrderOperateTypeEnum` 增加 `ADMIN_APPLY_INVOICE(51, "后台代客户申请发票")`；
  新服务方法加 `@TradeOrderLog` 并 `TradeOrderLogUtils.setOrderInfo(...)`。
  发票记录本身的 `creator` 由 MyBatis 自动填充为当前后台用户。
- **Rationale**：满足 FR-607 可追溯；订单日志在管理端订单详情可见，不需要新表。
- **Alternatives**：新建审计表 —— 过度设计，违反宪法 IV。

## R6 前端字段复用：抽取 InvoiceFieldsForm

- **Decision**：把 `InvoiceEditForm.vue` 的字段与 rules 抽成 `InvoiceFieldsForm.vue`
  （`v-model` 收/发字段对象；`disablePersonal` 控制企业单；`defineExpose({ validate })`）。
  `InvoiceEditForm` 改为使用它；代客下单与订单详情补申请复用同一个组件。
- **Rationale**：票种/抬头必填矩阵已在 admin 维护一份，再复制两份必然分叉；后端仍是最终裁决。
- **Alternatives**：三处各写一份 —— 维护成本高且容易漏改。

## R7 代客下单 UI

- **Decision**：在「价格与支付」卡片内增加「开具发票」开关（默认关）；开启后渲染字段组件。
  `settlement.price.payPrice <= 0` 时开关禁用并提示「实付 0 元，不能开票」；
  `settlement.enterprise = true` 时禁用「个人」抬头。提交前 `validate()`，通过后才带上 `invoice`。
- **Rationale**：结算已由服务端给出应付与企业标记，前端不自行推断（宪法 III）。
- **Alternatives**：固定展开表单 —— 大多数代客单不开票，会让界面变长。

## R8 订单详情补申请入口

- **Decision**：在详情页发票区块增加「代申请发票」按钮，显示条件：
  `没有 invoice && payStatus && status !== CANCELED && payPrice > 0`，并 `v-hasPermi="['trade:order:update']"`。
  点击打开 Dialog（复用字段组件），提交成功重新拉取详情。
- **Rationale**：与后端校验一一对应，不给出「点了必被拒」的按钮；详情页是运营处理单笔订单的入口。
- **Alternatives**：列表页加入口 —— 列表没有完整金额/发票状态上下文，暂不加。

## R9 0 元订单

- **Decision**：下单时前端禁用开票；服务端沿用既有「忽略」（不建发票）。
  补申请时服务端**拒绝**（`INVOICE_ORDER_AMOUNT_ZERO`），前端不显示入口。
- **Rationale**：与 006 两条路径的既有差异保持一致（下单忽略 / 补开拒绝）。

## R10 测试策略

- **Decision**：
  - 后端 `AgentOrderServiceTest`：带票下单成功、非法票（缺税号/个人专票）导致**整单不创建**；
  - 后端 `TradeOrderInvoiceServiceImplTest`：补申请四条条件、一单一条、企业抬头、成功路径；
  - 管理端 `InvoiceFieldsForm.spec.ts`：必填矩阵（企业税号、个人禁专票、收据只要名称）；
  - 管理端 `agentOrder.spec.ts` / 组件测试：开关、0 元禁用、请求体带 `invoice`；
  - 管理端订单详情测试：入口显示条件、提交调用接口、成功后刷新。
- **Rationale**：宪法原则 I；纯逻辑与组件分层可测，不依赖真实浏览器。
