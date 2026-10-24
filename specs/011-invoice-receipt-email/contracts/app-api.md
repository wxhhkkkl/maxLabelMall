# C 端接口契约（011 发票收据 / 邮箱）

> 复用 006 的发票接口；下表只列**新增/变化的字段**。前缀 `/app-api`。

## 1. 开票申请（下单 `invoice` 子对象 / 补开 `POST /trade/order-invoice/create`）

```jsonc
{
  "orderId": 900,                 // 仅补开需要
  "type": 3,                      // 1 普票 / 2 专票 / 3 收据（新增）
  "titleKind": 1,                 // 1 企业 / 2 个人
  "titleName": "北京某某科技有限公司",
  "taxNo": null,                  // 收据不要求；普票/专票的企业必填
  "titleAddress": null,
  "titlePhone": null,
  "bankName": null,
  "email": "finance@example.com"  // 新增：选填；格式合法、≤128
}
```

| 规则 | 说明 |
|---|---|
| 收据 | `type=3`：企业/个人都只要 `titleName`；税号选填（填了要合法） |
| 个人 + 专票 | 仍拒（006 Q38） |
| 邮箱 | 选填；非法格式 → `1_011_009_009 邮箱格式不正确` |

## 2. 抬头簿（`/trade/order-invoice/title/*`）

- 新增/修改：与上面同一套字段，**多 `email`**；
- 列表响应：每条抬头带 `email`；
- 选中一条抬头时前端把 `email` 一并填进开票表单。

## 3. 订单详情 `invoice` 对象

新增 `email`；`typeName` 可能为「收据」。其余字段不变。

## 4. 前端展示顺序（Q64）

```
企业 / 个人  →  电子普通发票 / 电子增值税专用发票 / 收据  →  选择抬头（按两者过滤）
                                                         →  抬头字段 + 邮箱
```

## 5. 订单列表 `GET /trade/order/page`

在 006 的 `invoiceStatus` 旁**多返回 `invoiceType`**（取值 1/2/3，未申请时为 `null`，与 `invoiceStatus` 同源）。
前端在「发票·待开票」下面显示票种名称。
