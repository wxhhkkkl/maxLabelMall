# 契约：代客开票（管理端 /admin-api）

**版本**：2026-11-25 · 对应 FR-599~FR-612
**权限**：全部复用 `trade:order:update`（不新增权限点）

## 1. 代客下单（既有接口，新增可选字段）

`PUT /trade/order/agent-create`

```json
{
  "userId": 1024,
  "items": [{ "skuId": 1001, "count": 2 }],
  "addressId": 2048,
  "payChannelCode": "offline",
  "userRemark": "请尽快发货",
  "invoice": {
    "type": 1,
    "titleKind": 1,
    "titleName": "某某科技有限公司",
    "taxNo": "91440300MA5XXXXXXX",
    "titleAddress": "深圳市南山区…",
    "titlePhone": "0755-12345678",
    "bankName": "招商银行深圳分行",
    "bankAccount": "755900000000000",
    "bankCode": "308584000013",
    "email": "finance@example.com"
  }
}
```

| 规则 | 说明 |
|---|---|
| `invoice` 缺省 / `null` | 不开票（与改动前一致） |
| 提供 `invoice` | 与订单**同一事务**创建发票申请；任一校验失败则订单不创建 |
| `amount` | 不接受传值；服务端取订单实付 |
| 实付 0 | 服务端沿用既有「忽略」，不创建发票（前端同时禁用开票开关） |
| 含企业授权商品 | `titleKind` 必须为 `1`（企业），否则 `ENTERPRISE_ORDER_INVOICE_TITLE_REQUIRED` |

校验失败错误码沿用 006：缺税号、个人开专票、税号格式、邮箱格式等（`InvoiceValidator`）。

## 2. 后台代客户补申请（新增）

`POST /trade/order-invoice/create`

**权限**：`trade:order:update`

**请求体**

```json
{
  "orderId": 123456789,
  "type": 1,
  "titleKind": 1,
  "titleName": "某某科技有限公司",
  "taxNo": "91440300MA5XXXXXXX",
  "titleAddress": null,
  "titlePhone": null,
  "bankName": null,
  "bankAccount": null,
  "bankCode": null,
  "email": "finance@example.com"
}
```

**成功响应**：`CommonResult<Long>` — 新发票申请编号。

**拒绝条件与错误码（与 C 端补开一致）**

| 条件 | 错误码（006 既有） |
|---|---|
| 订单不存在 / 已删除 | `ORDER_NOT_FOUND` |
| 订单未支付 | `INVOICE_ORDER_NOT_PAID` |
| 实付为 0 | `INVOICE_ORDER_AMOUNT_ZERO` |
| 订单已取消 | `INVOICE_ORDER_CANCELED` |
| 已有发票申请 | `INVOICE_ORDER_EXISTS` |
| 抬头字段不合法 | `InvoiceValidator` 对应错误码 |
| 企业授权单用了个人抬头 | `ENTERPRISE_ORDER_INVOICE_TITLE_REQUIRED` |

**副作用**：写一条订单日志（`ADMIN_APPLY_INVOICE`「后台代客户申请发票」）；
不改变订单金额、支付状态、订单状态。

## 3. 订单详情（既有接口，新增展示字段）

`GET /trade/order/get-detail?id=...`

```json
{
  "code": 0,
  "data": {
    "...": "...",
    "enterprise": true,
    "invoice": null
  }
}
```

- `enterprise`：订单是否为企业授权订单（`maxlabel_org_id` 非空）。
- 前端据此在补申请弹窗禁用「个人」抬头；服务端仍会独立校验。

## 4. 不变量

- 本特性不新增任何权限点、菜单、按钮数据。
- 不新增数据库表/列，无 SQL 迁移。
- C 端接口与门面站行为不变。
