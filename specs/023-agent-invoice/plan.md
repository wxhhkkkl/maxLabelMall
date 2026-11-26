# Implementation Plan: 代客开票（管理端代客户申请发票）

**Branch**: `023-agent-invoice` | **Date**: 2026-11-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-agent-invoice/spec.md`

## Summary

放开 017 的「不代客申请发票」限制：① 代客下单请求增加完整 006 发票子对象，复用 `createOrder0`
既有的「与订单同一事务」落库；② 新增管理端代申请接口，复用 `InvoiceValidator` / `doCreateInvoice`
与 `trade_order_invoice` 表，权限复用 `trade:order:update`。**无 SQL、无新表/列/权限点**。
前端把 006 的发票字段表单从 `InvoiceEditForm` 抽成可复用组件，供代客下单与管理端补申请共同使用。

## Technical Context

**Language/Version**: Java（yudao / Spring Boot，JDK 25）+ TypeScript / Vue 3（管理端）

**Primary Dependencies**: 既有 trade 模块（`TradeOrderInvoiceService`、`InvoiceValidator`）、
管理端 Element Plus；**不新增依赖**

**Storage**: MySQL 既有表 `trade_order_invoice`（不改结构，无迁移脚本）

**Testing**: 后端 JUnit 5 + H2（`BaseDbUnitTest`）；管理端 Vitest 5 + @vue/test-utils

**Target Platform**: 管理端浏览器 + 服务端 `/admin-api`

**Project Type**: Web 应用（服务端 + 管理端；门面站不动）

**Performance Goals**: 无新增性能指标；复用既有下单/申请链路

**Constraints**: 不新增权限点、菜单、表、列、SQL；校验唯一来源仍是 `InvoiceValidator`；
补申请入口只放在管理端订单详情

**Scale/Scope**: 管理端单个下单/补申请操作；不影响 C 端与门面站

## Constitution Check

| 原则 | 结论 | 依据 |
|---|---|---|
| I. 测试先行 | ✅ | 先补后端服务测试与管理端组件/逻辑测试，再实现 |
| II. 先澄清 | ✅ | Q170~Q172 已由所有者拍板 |
| III. 复用既有能力 | ✅ | 复用 `createOrder0` 事务、`InvoiceValidator`、`doCreateInvoice`、发票表；抽取既有字段表单 |
| IV. 最小改动 | ✅ | 1 个新接口 + 2 个 VO/字段 + 前端复用组件；无新依赖/SQL/权限 |
| V. 增量可验证 | ✅ | SC-224~SC-229 对应单测、组件测试与 quickstart |
| VI. 发版须所有者确认 | ✅ | 本计划不含发版动作 |

**结论**：无违规项，无需 Complexity Tracking。

## Project Structure

### Documentation (this feature)

```text
specs/023-agent-invoice/
├── plan.md
├── research.md
├── data-model.md
├── contracts/admin-api.md
├── quickstart.md
└── tasks.md            # /speckit-tasks 输出
```

### Source Code (repository root)

```text
yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/
├── controller/admin/order/vo/AdminAgentOrderCreateReqVO.java        # + invoice
├── controller/admin/invoice/vo/AdminTradeOrderInvoiceCreateReqVO.java # 新增
├── controller/admin/order/vo/TradeOrderDetailRespVO.java            # + enterprise
├── controller/admin/order/TradeOrderController.java                 # 详情回填 enterprise
├── controller/admin/invoice/TradeOrderInvoiceController.java        # + POST /create
├── enums/order/TradeOrderOperateTypeEnum.java                       # + 代申请日志类型
├── service/order/AgentOrderServiceImpl.java                         # 透传 invoice
└── service/invoice/TradeOrderInvoiceService{Impl}.java             # + createInvoiceByAdmin

yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/...
├── service/order/AgentOrderServiceTest.java                         # 下单带票 / 非法票不建单
└── service/invoice/TradeOrderInvoiceServiceImplTest.java           # 补申请校验

yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/
├── api/mall/trade/invoice/index.ts          # + createInvoiceByAdmin
├── api/mall/trade/order/index.ts            # AgentCreateReq.invoice / 订单详情 enterprise
├── utils/invoice.ts                         # 复用票种常量（必要时补充）
├── views/mall/trade/invoice/InvoiceFieldsForm.vue  # 新增（从 InvoiceEditForm 抽取）
├── views/mall/trade/invoice/InvoiceEditForm.vue    # 改为复用字段组件
├── views/mall/trade/order/agent/index.vue          # 代客下单发票区块
├── views/mall/trade/order/agent/agentOrder.ts      # 请求体带 invoice
└── views/mall/trade/order/detail/index.vue         # 「代申请发票」入口 + 弹窗
```

**Structure Decision**：服务端沿用 trade 模块既有分层，不新建模块；管理端把发票字段 UI 抽为
可复用组件（`InvoiceFieldsForm`），避免第三份「票种/抬头必填矩阵」。

## Complexity Tracking

无。
