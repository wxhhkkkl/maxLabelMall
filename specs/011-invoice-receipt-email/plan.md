# Implementation Plan: 发票收据、邮箱与抬头选择顺序（011）

**Branch**: `011-invoice-receipt-email` | **Date**: 2026-10-24
**Spec**: [spec.md](./spec.md) | **Base**: [006 发票](../../006-order-invoice/spec.md)

## Summary

在 006 发票模块上加三样东西，**不新建表、不改状态机**：

1. **收据** —— `InvoiceTypeEnum.RECEIPT=3`；收据**只要抬头名称**（企业/个人都一样）。
2. **邮箱** —— 申请与抬头簿各加一个选填列；后台展示，人工发送。
3. **抬头顺序** —— 把选择器移进 `InvoiceFieldsForm`，放在「企业/个人 → 票种」之后，
   按「企业/个人 + 票种」双重过滤。

## Technical Context

**语言 / 栈**：Java 17+（yudao-cloud）+ Vue 3 / TypeScript（storefront、admin-vue3）
**数据**：MySQL。**只加两列**：`trade_order_invoice.email`、`trade_invoice_title.email`；
票种列是 tinyint，收据=3 无需 DDL。
**测试**：JUnit（`InvoiceValidatorTest`、`TradeOrderInvoiceServiceImplTest`、
`TradeInvoiceTitleServiceImplTest`、`TradeOrderInvoiceMapperTest`）；storefront/admin vitest。
**约束**：复用 006 的申请/抬头/后台链路；C 端只走 `/app-api`、管理端只走 `/admin-api`。

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 收据/邮箱/过滤规则都先在 `InvoiceValidatorTest` 与 storefront/admin specs 里写断言；DDL 两列属于已声明的结构性例外 |
| **II. 先澄清** | ✅ Q61~Q64（收据字段、邮箱用途/归属、收据抬头簿）已拍板 |
| **III. 复用** | ✅ 收据复用发票的申请、状态、文件、退款撤回；邮箱复用现有 VO/转换；不新增服务 |
| **IV. 最小改动** | ✅ 两列 + 一个枚举值 + 表单顺序；不改状态机、不建表、不新增邮件服务 |
| **V. 可验证** | ✅ SC-101~104 在 quickstart 有落点；既有 006 用例继续跑 |

## 关键设计决策

- **R57 收据只要名称**：`InvoiceValidator` 对 `RECEIPT` 跳过企业税号必填；填了仍校验格式。
- **R58 邮箱两处存**：申请快照 + 抬头簿；选抬头时带出（Q64）。
- **R59 选择器进表单**：`InvoiceFieldsForm` 加 `showPicker`；申请页显示、抬头编辑弹层隐藏；
  选择器按 `type + titleKind` 过滤。
- **R60 后台只加展示**：列表加邮箱列；开具弹层加「票据类型 / 邮箱」两行；不做邮件发送。
- **R61 上线顺序**：先跑 `020-invoice-receipt-email.sql` → 再发后端 → 最后发两个前端。

## 代码落点

```text
yudao-cloud/
├── sql/mysql/020-invoice-receipt-email.sql
├── yudao-module-mall/yudao-module-trade-api/.../enums/invoice/InvoiceTypeEnum.java
├── yudao-module-mall/yudao-module-trade-server/src/main/java/.../
│   ├── controller/{app,admin}/invoice/vo/*.java          # email
│   ├── dal/dataobject/invoice/*.java                      # email
│   ├── service/invoice/InvoiceValidator.java             # 收据 + 邮箱
│   └── test/resources/sql/create_tables.sql              # H2 email
├── storefront/src/
│   ├── types/invoice.ts · utils/invoice.ts               # RECEIPT + email + 校验
│   ├── components/InvoiceFieldsForm.vue                  # picker 顺序 + email
│   ├── components/{CheckoutInvoiceCard,InvoiceApplyDialog}.vue
│   ├── components/{InvoiceTitlePicker,InvoiceTitleDialog}.vue
│   └── views/{InvoiceTitleView,CheckoutView}.vue
└── yudao-ui/yudao-ui-admin-vue3/src/
    ├── api/mall/trade/invoice/index.ts                   # email
    └── views/mall/trade/invoice/{index,InvoiceIssueForm}.vue
```

## 实施分期

| 阶段 | 内容 | 交付后 |
|---|---|---|
| ① 后端 | 枚举 + 校验 + DO/VO + 两列 | 收据/邮箱能落库，后台能返回 |
| ② C 端 | 收据/邮箱/选择器顺序 | 客户能按新顺序开票 |
| ③ 后台 | 列表/开具弹层展示 | 运营能看到票种与邮箱 |
| ④ 上线 | SQL → 后端 → 两个前端 | 功能可用 |

## 主要风险

| # | 风险 | 对策 |
|---|---|---|
| R57 | 照搬「企业必须税号」把收据也拦住 | `InvoiceValidatorTest` 明确钉住「收据企业只填名称通过」 |
| R59 | 选择器仍在票种之前 / 仍只按票种过滤 | 选进 `InvoiceFieldsForm` 的票种之后；spec 断言双重过滤 |
| R58 | 漏了抬头簿的邮箱 | `TradeInvoiceTitleDO` + `AppInvoiceTitleRespVO` + 选抬头 `fillFrom` 都带上 |
| R61 | 先发后端、SQL 未跑 → 查询带 email 列 500 | quickstart 写明顺序：SQL → 后端 → 前端 |
