# Implementation Plan: 客户服务（投诉咨询与在线维修）

**Branch**: `021-customer-service-repair` | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/021-customer-service-repair/spec.md`

## Summary

在**个人中心**新增「客户服务」，客户可提交**投诉咨询**与**在线维修**并跟踪状态；管理端新增
**客户服务**菜单，客服可查看/代建工单、记录电话联系结果、为维修申请**生成维修订单**（维修费 +
寄回运费），客户在个人中心**在线支付或对公转账**，并查看**回收邮寄地址**、标记「已寄出」与回填运单号。

技术路径：全部落在 **`yudao-module-trade-server`**（与 014 印刷定制同一选址），复用**
支付模块（在线支付 + 对公转账）**、会员 API、文件上传、菜单/权限与操作留痕；**不新建交易订单**
（维修单独立于「我的订单」），维修支付单以 `merchantOrderId = repair:<id>` 前缀复用既有支付回调，
在交易订单回调入口加一个前缀分支。

## Technical Context

**Language/Version**: Java 17（`yudao-cloud`，Maven 多模块）+ Vue 3 + TypeScript（门面站与管理端）

**Primary Dependencies**: Spring Boot / MyBatis-Plus（yudao 既有）、支付模块 `yudao-module-pay`、
会员模块 API、Element Plus（管理端）

**Storage**: MySQL（既有库，新增 5 张表 + 菜单/权限数据）；附件沿用现有对象存储与上传接口

**Testing**: 后端 JUnit + H2（`BaseDbUnitTest`）；门面站/管理端 Vitest + Vue Test Utils

**Target Platform**: 管理端 Web（`/admin-api`）+ 门面站个人中心（`/app-api`）；小程序/App 本期不改

**Project Type**: 单体 Web 服务 + 后台管理端 + 门面站

**Performance Goals**: 工单/维修列表分页 ≤ 1 秒；提交与开单操作 ≤ 2 秒（SC-210 / SC-212）

**Constraints**:
- 维修订单**不得进入**「我的订单」，也不复用商品订单的开票/售后入口（FR-583）
- 支付复用既有支付能力，不新建收款通道（FR-571）
- 开单、状态流转、支付状态更新必须原子（FR-579）
- 附件只收图片，≤ 5 张、单张 ≤ 10 MB（FR-580）
- 发版须经所有者确认（宪法原则 VI）

**Scale/Scope**: 数百会员与企业的量级；新增 2 个 C 端页面区块、1 个管理端页面、约 26 个接口
（C 端 10 + 管理端 16）、5 张表

## Constitution Check

*GATE: 必须在 Phase 0 前通过；Phase 1 设计后复查。*

| 原则 | 本特性的落实 | 结论 |
|---|---|---|
| I. 测试先行 | 每个故事先写失败测试：归属隔离、状态流转、一申请一单、支付回调幂等、对公确认 | ✅ 任务里排在最前 |
| II. 先澄清 | 规格已记录 8 条澄清（US70~75 / FR-559~584），无遗留 NEEDS CLARIFICATION | ✅ |
| III. 复用既有能力 | 支付/对公转账复用 `yudao-module-pay`；会员检索复用 `MemberUserApi`；上传、菜单、`@LogRecord` 复用既有；**不新建交易订单** | ✅ |
| IV. 最小改动 | 新增 5 表与独立 service；对既有代码只改**支付回调入口的一个前缀分支**（`AppTradeOrderController#updateOrderPaid`） | ✅ |
| V. 增量可验证 | US70~75 各自可独立验收；SC-210~216 均可度量 | ✅ |
| VI. 发版须所有者确认 | quickstart 写明 SQL → 后端 → 管理端 → 门面站顺序；**执行前必须得到当次确认** | ✅ |

**门禁结论**：通过，无违规需要记录到 Complexity Tracking。

**Phase 1 设计后复查**：研究（R1~R13）、数据模型与两份契约均未引入新的原则冲突；
支付回调前缀分支与「不建交易订单」的选择已在 Complexity Tracking / R3 / R4 说明。

## Project Structure

### Documentation (this feature)

```text
specs/021-customer-service-repair/
├── plan.md              # 本文件
├── spec.md              # 功能规格（已澄清）
├── research.md          # Phase 0 输出
├── data-model.md        # Phase 1 输出
├── quickstart.md        # Phase 1 输出
├── checklists/
│   └── requirements.md
├── contracts/
│   ├── app-api.md
│   └── admin-api.md
└── tasks.md             # /speckit-tasks 生成（本命令不创建）
```

### Source Code (repository root)

```text
yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/
├── controller/app/customerservice/           # C 端接口（工单、维修、维修单支付、地址）
│   └── vo/
├── controller/admin/customerservice/         # 管理端接口（列表、代建、处理、开单、确认收款）
│   └── vo/
├── dal/dataobject/customerservice/           # 5 个 DO
├── dal/mysql/customerservice/                # Mapper
├── service/customerservice/                  # 工单、维修申请、维修单、地址、状态机
│   └── bo/
├── convert/customerservice/                  # VO ↔ DO / BO
└── enums/customerservice/                    # 类型、状态、来源、支付状态
yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/.../trade/
├── service/customerservice/                  # 服务层单测（H2）
└── controller/.../customerservice/           # 权限回归

yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/
├── api/trade/customerService/index.ts
└── views/trade/customer-service/
    ├── index.vue                             # 主页面（投诉咨询 / 在线维修 / 回收地址）
    └── components/                           # 表格、处理、代建、开单、状态、地址表单

storefront/src/
├── api/customerService.ts                    # C 端客户服务接口
├── views/CustomerServiceView.vue             # 个人中心「客户服务」
├── components/AccountSidebar.vue             # 增加「客户服务」入口（唯一改动）
└── router/index.ts                           # 新增 /account/customer-service 路由

yudao-cloud/sql/mysql/043-customer-service.sql  # 5 表 + 菜单/按钮权限
```

**Structure Decision**: 复用既有 `trade` 模块与支付能力，不新增 Maven 模块；
C 端与管理端分控制器，共用一个 service 层；维修单是独立业务对象，不建 `trade_order`。

## Complexity Tracking

> 无宪法违规。唯一对既有代码的改动是支付回调入口的前缀分支，原因见 research R4。

| 改动点 | 为什么必须 | 更简单的替代方案为何不可行 |
|---|---|---|
| `AppTradeOrderController#updateOrderPaid` 增加 `repair:` 前缀分支 | 支付模块全应用共用同一个 `order_notify_url` | 新建 pay_app 需重复配置微信/支付宝商户凭据；为维修单建交易订单会让它出现在「我的订单」，违反 FR-583 |
