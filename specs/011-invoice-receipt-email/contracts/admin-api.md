# 管理端接口契约（011 发票收据 / 邮箱）

> 复用 006 的 `/admin-api/trade/order-invoice/*`；只列变化。

## 1. `GET /trade/order-invoice/page`

响应项新增：

```jsonc
{ "type": 3, "typeName": "收据", "email": "finance@example.com", "titleName": "…" }
```

- 列表新增「邮箱」列；「发票类型」列直接显示 `typeName`（收据自动出现）。
- 权限、状态、金额、订单实付/已退等不变。

## 2. `PUT /trade/order-invoice/issue`

不变：上传 PDF/图片挂到申请上。**收据与发票共用**。

## 3. 「开具发票」弹层

新增两行只读信息：

| 行 | 内容 |
|---|---|
| 票据类型 | `typeName`（电子普通发票 / 电子增值税专用发票 / **收据**） |
| 邮箱 | `email`（空显示「—」） |

不做自动邮件发送（Q62）；运营按邮箱人工发送。
