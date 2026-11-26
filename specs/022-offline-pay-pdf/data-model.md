# Data Model：对公转账 PDF 凭证

> ⚠️ **入口只对未取消的对公订单显示**（Q168）。已取消订单不进入本模型；
> 状态字段仍写出「已取消」兜底，防止将来放开入口时凭证缺状态。

本特性**不落库、不产生服务端数据**。以下对象是导出前在浏览器内组装的内容模型，
所有展示值在模型里就已是**格式化后的字符串**（金额 `¥12.90`、时间 `2026-11-25 10:00:00`、
缺失值 `—`），HTML 渲染只负责转义与排版。

## OfflinePayPdfData

| 字段 | 类型 | 来源（`OrderDetail`） | 规则 |
|---|---|---|---|
| `fileName` | `string` | `order.no` | `对公转账单_<订单号>.pdf`；订单号去掉 `\ / : * ? " < > \|` |
| `account.accountName` | `string` | `offlinePay.accountName` | 空 → `—` |
| `account.accountNo` | `string` | `offlinePay.accountNo` | 空 → `—` |
| `account.bankName` | `string` | `offlinePay.bankName` | 空 → `—` |
| `account.tip` | `string \| null` | `offlinePay.tip` | 空 → `null`（不渲染提示块） |
| `order.no` | `string` | `no` | 空 → `—` |
| `order.createTime` | `string` | `createTime`（epoch 毫秒） | `formatDateTime` |
| `order.status` | `string` | `status` + `offlinePay.waiting` | `waiting = true` → 待收款；否则 `orderStatusText(status)` |
| `order.payPrice` | `string` | `payPrice` | `formatYuan` |
| `receiver.name` | `string` | `receiverName` | 空 → `—` |
| `receiver.mobile` | `string` | `receiverMobile` | 空 → `—` |
| `receiver.address` | `string` | `receiverAreaName` + `receiverDetailAddress` | 两段拼接，均空 → `—` |
| `items[].name` | `string` | `items[].spuName` | 空 → `—` |
| `items[].spec` | `string` | `items[].properties`（`propertyName：valueName`） | 多个用「；」连接；无规格 → `—` |
| `items[].unitPrice` | `string` | `items[].price` | `formatYuan` |
| `items[].count` | `number` | `items[].count` | 原样 |
| `items[].subtotal` | `string` | `items[].price × count` | `formatYuan`（商品金额，不含运费/优惠） |
| `amounts[]` | `Array<{label,value,total?}>` | 见下 | 顺序固定；0 也显示 |
| `invoice` | `object \| null` | `invoice` | 见下 |
| `userRemark` | `string \| null` | `userRemark` | `trim()` 为空 → `null` |

### 金额明细（`amounts`，顺序固定）

| 标签 | 值（分） | 说明 |
|---|---|---|
| 商品小计 | `totalPrice` | |
| 促销优惠 | `-discountPrice` | 负数按 `-¥x` 显示 |
| 运费 | `deliveryPrice` | |
| 优惠券抵扣 | `-couponPrice` | |
| 积分抵扣 | `-pointPrice` | 当前恒 0，如实显示 |
| 会员优惠 | `-vipPrice` | 运营配置会员折扣时非 0 |
| **应付总额** | `payPrice` | 加粗，作为凭证金额 |

> 与订单详情页「金额构成」同一口径（`reconcile()` 的六项不变量）。

### 开票信息（`invoice`）

`order.invoice` 为 `null` → `null`（不渲染）。
`invoice.status === 20（已作废）` → `null`（与订单详情页一致：退款/作废后不展示发票内容）。

| 字段 | 来源 | 规则 |
|---|---|---|
| `typeName` | `invoice.typeName` | 空 → `—` |
| `titleKindName` | `invoice.titleKindName` | 空 → `—` |
| `titleName` | `invoice.titleName` | 空 → `—` |
| `taxNo` | `invoice.taxNo` | 空 → `—` |
| `amount` | `invoice.amount` | `formatYuan` |

## 不持久化

- 模型只存在于一次导出的内存中；不写 localStorage / sessionStorage，不请求后端写接口。
- PDF 文件由浏览器直接下载，服务端无记录。
