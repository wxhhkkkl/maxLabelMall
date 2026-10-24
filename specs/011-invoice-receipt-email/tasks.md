# 任务清单（011 发票收据、邮箱与抬头选择顺序）

**Input**: [spec.md](./spec.md) / [plan.md](./plan.md) / 006 发票模块
**测试策略**：宪法原则 I；行为先写失败测试；加列 DDL 属于结构性例外。

## Phase 1: 后端

- [X] T001 `InvoiceTypeEnum` 加 `RECEIPT(3, "收据")`；`InvoiceValidator` 收据只要求名称、邮箱选填校验；`INVOICE_EMAIL_INVALID`
- [X] T002 `AppInvoiceApplyBaseVO` / `TradeOrderInvoiceDO` / `TradeInvoiceTitleDO` / 三个响应 VO 加 `email`；转换复用 `BeanUtils`
- [X] T003 `020-invoice-receipt-email.sql`（两列 email + 核对）；H2 `create_tables.sql` 同步
- [X] T004 测试：`InvoiceValidatorTest` 加收据（企业/个人只名称）、邮箱 trim/格式；更新「非法票种」列表（3 不再是非法）

## Phase 2: C 端

- [X] T005 `types/invoice.ts` / `utils/invoice.ts`：`RECEIPT`、`INVOICE_TYPE_NAME`、`email`、收据校验、邮箱格式
- [X] T006 `InvoiceFieldsForm`：抬头选择器移到「企业/个人 → 票种」之后、按 `type + titleKind` 过滤、邮箱字段
- [X] T007 `CheckoutInvoiceCard` / `InvoiceApplyDialog` 去掉外层选择器；`emptyForm` 加 email
- [X] T008 `InvoiceTitlePicker` 加 `titleKind` 过滤；`InvoiceTitleDialog` 加 email/titleKind、隐藏选择器
- [X] T009 `InvoiceTitleView` 加收据分段、邮箱展示、设默认带 email；`CheckoutView` 表单加 email
- [X] T010 测试：发票 6 个 spec + `utils/invoice.spec` 全绿（1350 用例）

## Phase 3: 后台

- [X] T011 `TradeOrderInvoiceVO` 加 email；列表加邮箱列；开具弹层加「票据类型 / 邮箱」
- [X] T012 测试：`InvoiceIssueForm.spec` 7 用例通过

## Phase 4: 文档与上线

- [X] T013 spec / plan / tasks / contracts / data-model / quickstart
- [X] T014 执行 `020-invoice-receipt-email.sql`（生产库；幂等）—— 2026-10-24 已执行，两列 `email` 已核对
- [X] T015 发后端 → storefront → admin；按 quickstart 验证收据/邮箱/顺序/回归 —— 2026-10-24 已上线，公网三入口 200

**已完成 15 / 15**（2026-10-24 上线）。

## 上线记录

- SQL `020-invoice-receipt-email.sql` 已执行：`trade_order_invoice.email` / `trade_invoice_title.email`
- 后端回滚备份：`/root/yudao-server.jar.bak-20261024-145617`；48080 HTTP 200，近期 0 ERROR
- storefront：`index.html.bak-20261024-150125`；admin：`/var/www/maxlabel/admin/index.html.bak-20261024-150301`
- 公网：`https://new.yuwangchenfa.com/`、`/admin/`、`http://maxlabel.cn/` 均 200
