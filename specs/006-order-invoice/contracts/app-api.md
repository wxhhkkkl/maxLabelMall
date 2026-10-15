# app-api 契约（006）

C 端（`storefront`）本期新增与改动的接口。所有路径以 `/app-api` 为前缀，**只走 `/app-api`**。

> ⚠️ **时间字段一律是 epoch 毫秒数**，不是字符串 —— yudao 全局给 `LocalDateTime` 注册了
> `TimestampLocalDateTimeSerializer`（001 的 e2e 与 005 的单测都在这里踩过）。前端渲染必须过 `utils/time.ts`。

---

## 1. 发票抬头簿（**新增**，照 `/member/address/**` 的形状）

### 1.1 `GET /trade/invoice-title/list`

当前登录会员的全部抬头，默认项排在最前。

```jsonc
{ "code": 0, "data": [
  { "id": 3, "titleName": "南京路桥机械有限公司", "taxNo": "91320104660690975E",
    "titleAddress": "南京市…", "titlePhone": "18913984596", "bankName": "工商银行南京分行 6222…",
    "defaultStatus": true }
], "msg": "" }
```

### 1.2 `POST /trade/invoice-title/create`

```jsonc
// 请求（`titleName` 必填，其余选填 —— 抬头可以不完整，缺项在选专票时才被拒，见 data-model §4）
{ "titleName": "…", "taxNo": "…", "titleAddress": "…", "titlePhone": "…", "bankName": "…", "defaultStatus": false }
// 响应：新抬头的 id
{ "code": 0, "data": 4, "msg": "" }
```

### 1.3 `PUT /trade/invoice-title/update`

body 为 `{ id, ...同 create }`，响应 `data: true`。

### 1.4 `DELETE /trade/invoice-title/delete?id=<抬头编号>`

响应 `data: true`。

**四条共用的规则**：

- 一律**按当前登录会员**（`getLoginUserId()`）过滤与写入 —— 传别人的 `id` 必须查不到（SC-060 在抬头簿上的那一半）。
- `defaultStatus = true` 时，**服务端负责把该会员的其他抬头清掉**（唯一性在服务层，不在数据库，见 data-model §2）。
- 删除一条抬头**不影响任何历史申请** —— 那些申请存的是快照（FR-166 / SC-061）。
- 保存时按 §4 的规则做长度、税号格式与 trim 校验。

---

## 2. 订单详情补开（**新增**，US20）

### 2.1 `POST /trade/order-invoice/create`

```jsonc
// 请求（没有 titleId —— R27：提交的就是快照本身）
{ "orderId": 1001, "type": 2,
  "titleName": "南京路桥机械有限公司", "taxNo": "91320104660690975E",
  "titleAddress": "南京市…", "titlePhone": "18913984596", "bankName": "工商银行南京分行 6222…" }
// 响应：新申请的 id
{ "code": 0, "data": 77, "msg": "" }
```

**服务端 MUST 按顺序校验（缺一不可，FR-163）**：

| # | 校验 | 失败时 |
|---|---|---|
| 1 | 订单存在 | 业务异常「订单不存在」 |
| 2 | **订单属于当前登录会员** | 业务异常「订单不存在」（**不用"无权访问"，避免泄露订单是否存在**） |
| 3 | 订单**已支付**（`payStatus = true`） | 业务异常「订单还未支付，付款后可申请开票」 |
| 4 | 订单**实付金额 > 0** | 业务异常「本单实付金额为 0，无需开票」（spec Edge Case：全额抵扣的订单没有可开票金额） |
| 5 | 订单**未取消**（`status ≠ CANCELED`） | 业务异常「订单已取消，无法申请开票」 |
| 6 | 该订单**尚无申请**（`uk_order_id`） | 业务异常「该订单已提交过开票申请」 |
| 7 | 按 `type` 校验抬头字段（data-model §4） | 业务异常，**指明缺哪一项** |

**金额不由请求决定**：`amount` 取服务端读到的订单实付金额（FR-161）。

---

## 3. 改动的既有接口（**只做加法**）

### 3.1 `POST /trade/order/create` —— 入参加一个可选 `invoice`

```jsonc
{
  "items": [{ "skuId": 1, "count": 2 }], "addressId": 9, "couponId": null,
  "pointStatus": false, "deliveryType": 1, "remark": "",
  "invoice": { "type": 1, "titleName": "张三", "taxNo": "", "titleAddress": "",
               "titlePhone": "", "bankName": "" }
}
```

| 规则 | 说明 |
|---|---|
| **不传 `invoice`** | 就是"不开票"，**不产生任何开票记录**（FR-159）。前端 MUST 用 `undefined` 而不是 `null` / 空对象 —— 后者语义含混（"申请了但字段全空"） |
| `invoice` 必填项不全 | **整单被拒**，错误信息指明缺哪一项（FR-156）。**不允许"先下单、事后再补"** |
| 拒绝 vs 开票失败 | 校验类错误（缺项、税号非法）**拒绝下单**；这是**客户可改**的输入错误，不是系统故障。系统故障（DB 写失败）在**同一事务**里回滚（见 plan 的风险 R1） |
| **订单实付金额为 0**（全额券抵扣） | **不产生申请，但订单照下** —— 不因为一个"本来就开不了票"的选择让客户下不了单（FR-181 的精神）。前端在 0 元时**根本不渲染发票卡片**，这条只对绕过界面的调用方生效 |
| 落库 | 订单与开票申请**同一事务**（`TradeOrderUpdateServiceImpl.createOrder` 内一行） |
| 响应 | **不变**：`{ id, payOrderId }` |

### 3.2 `GET /trade/order/get-detail` —— 响应加 `invoice`

```jsonc
{ "code": 0, "data": {
  "id": 1001, "no": "NO-…", "status": 10, "payPrice": 7800,
  "invoice": {
    "status": 0,                 // 0 待开票 / 10 已开票 / 20 已作废
    "type": 2, "typeName": "电子增值税专用发票",
    "titleName": "…", "taxNo": "…", "titleAddress": "…", "titlePhone": "…", "bankName": "…",
    "amount": 7800,              // 分
    "fileUrl": null,             // 已开票才有值（公网可读，见 spec 的 Assumptions）
    "issueTime": null,           // epoch ms
    "createTime": 1790486340000  // 申请时间，epoch ms
  }, "msg": "" } }
```

**没有申请时**：`invoice` 为 **`null`**（不是空对象）—— 前端据此决定"整块不渲染"还是"给「申请开票」入口"（FR-176 的四态）。

### 3.3 `GET /trade/order/page` —— 每笔订单加 `invoiceStatus`

```jsonc
{ "code": 0, "data": { "total": 3, "list": [
  { "id": 1001, "no": "NO-…", "status": 10, "payPrice": 7800, "invoiceStatus": 0, "items": [ /* … */ ] }
] } }
```

- `invoiceStatus`：`null` = **未申请**；`0/10/20` 同 §3.2。**用 `null` 而不是哨兵值**，与前端的"没申请"判断对齐。
- 实现 MUST **一次 `IN (orderIds)` 批量查询**，不许逐笔查（plan 的风险 R3）。

---

## 4. 前端落点（C 端）

| 位置 | 改动 |
|---|---|
| 结算页 `CheckoutView.vue` | 加一张「发票」卡片（默认不开票）；选开票后出现票种 + 抬头（可从抬头簿选）；必填不全都**提交按钮前置校验**；**实付金额为 0 时整张卡片不渲染**（按结算结果 `payPrice > 0` 判断，前端不自己算金额） |
| 订单详情 `OrderDetailView.vue` | 发票区块**四态**（FR-176）：未支付/已取消且未申请 → 整块不显示；已支付未申请 → 只出「申请开票」；待开票 → 状态文案、**无下载入口**；已开票 → 信息 + 下载链接 + **常驻求助文案**（见下） |
| 订单列表 `OrderListView.vue` | 卡片上显示开票状态（`null` 时什么都不显示） |
| 个人中心 `/account/invoice` | **新页面**：抬头簿列表 + 新增/编辑/删除/设为默认（照 `AddressView` + `AddressFormDialog`） |
| `AccountSidebar.vue` / `router/index.ts` | 各加一条（账户资料组） |

**本期不做**（明确）：结算页"当场新填的抬头**不自动存进抬头簿**"（客户要去个人中心存）；
要自动保存得加一个勾选项，那是所有者没提的能力（Q27~Q31 之外的，不自行增加）。

### 4.1 关于发票文件的链接（两条已核实的性质）

1. **链接是公网可读的**（既有对象存储的既有性质，spec 已作为**显式接受的风险**记录）：
   拿到 URL 的人不登录也能打开。本期**不加**签名 URL / 权限校验。
2. ⚠️ **该文件域不返回任何 CORS 头**（2026-10-15 实测：`img.yuwangchenfa.com` 响应里没有
   `access-control-allow-origin`；缺失文件返回 404）。**后果**：前端**无法**判断这次下载成不成功 ——
   `fetch` 会被跨域策略挡下，`mode: 'no-cors'` 拿到的是无法区分 200/404 的 opaque 响应。
   所以下载入口**必须**配一句**常驻**的兜底文案（"文件打不开？请[联系我们](</support/contact>)"），
   并走**新标签**打开。这条只做到"不静默、有求助路径"，**做不到**"当场告知文件已失效"（plan 风险 R10）。
