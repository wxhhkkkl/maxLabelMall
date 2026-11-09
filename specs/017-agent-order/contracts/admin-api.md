# 管理端接口契约（017 代客下单）

> 前缀 `/admin-api`；沿用 `@PreAuthorize("@ss.hasPermission('...')")`。
> 所有金额单位：分；时间由 yudao 全局序列化为 epoch 毫秒（管理端 `formatDate` 处理）。

---

## 1. 代客结算预览

### `POST /trade/order/agent-settlement`

权限：`trade:order:agent-create`

**用途**：运营选好客户、商品、地址后，由服务端按目标客户身份算价；提交前的价格以此为准。

```jsonc
// Request
{
  "userId": 2048,
  "items": [{ "skuId": 11, "count": 2 }],
  "addressId": 1024
}
```

```jsonc
// Response（复用 AppTradeOrderSettlementRespVO；下面只列本页面用到的字段）
{
  "code": 0,
  "data": {
    "items": [
      { "skuId": 11, "spuId": 5, "spuName": "标签 A", "picUrl": "…",
        "price": 1000, "count": 2, "properties": [] }
    ],
    "price": {
      "totalPrice": 2000, "discountPrice": 0, "deliveryPrice": 600,
      "couponPrice": 0, "pointPrice": 0, "vipPrice": 0, "payPrice": 2600
    },
    "address": { "id": 1024, "name": "张三", "mobile": "…", "areaId": 110101,
                 "detailAddress": "…" },
    "deliveryType": 1,
    "coupons": [],
    "usePoint": 0,
    "totalPoint": 0,
    "promotions": [],
    "enterprise": false,
    "offlinePayEnabled": true,
    "offlinePayTip": "…"
  }
}
```

> `Item.price` 是**单价**（分）；订单级金额在 `price` 对象里。完整字段以
> `AppTradeOrderSettlementRespVO` 为准，不要按本示例臆造 `originalPrice` / `pointStatus` 等字段。

**规则**：

- `userId` 必须存在且 `status=0`；否则 `1_011_012_000` / `1_011_012_001`。
- `items` 每条必须 `skuId + count≥1`；商品库存、上架状态、企业权限、企业续费席位数等沿用现有结算校验。
- 地址必须是 `userId` 名下地址（`MemberAddressApi#getAddress(id, userId)` 校验）；
  实物单未传 `addressId` 时按现有逻辑取客户默认地址；客户没有地址时由提交环节拒绝
  （`AGENT_ORDER_ADDRESS_REQUIRED`）。
- **不读券、不抵积分**：管理端请求不提供券/积分字段；服务端构造 C 端请求时强制
  `couponId=null`、`pointStatus=false`。
- 只支持快递 / 虚拟商品：商品 `deliveryTypes` 仅含「自提」时返回 `1_011_012_002`；
  隐藏定制 SKU/SPU（`CustomizationConstants.HIDDEN_SKU_ID/HIDDEN_SPU_ID`）返回
  `1_011_012_009`；印刷定制不进入本接口（无 `customization` 字段）。
- 预览与创建共用同一套价格规则；提交时若价格变化，以创建响应为准。

---

## 2. 创建代客订单

### `POST /trade/order/agent-create`

权限：`trade:order:agent-create`

```jsonc
// Request
{
  "userId": 2048,
  "items": [{ "skuId": 11, "count": 2 }],
  "addressId": 1024,
  "userRemark": "请在工作日送",       // 可选，≤200
  "payChannelCode": null              // null = 在线支付；"offline" = 对公转账
}
```

```jsonc
// Response
{
  "code": 0,
  "data": {
    "orderId": 123456,
    "payOrderId": 98765,              // 对公转账时为 null
    "payPrice": 2600,
    "orderStatus": 0                  // 0 待支付；对公转账同样是 0（另有 offlineWaiting 标识）
  }
}
```

**规则**：

- 订单归属 `userId`；`agent_user_id` = 当前登录后台用户；`terminal` = `40`（后台代客）。
- 订单日志写 `ADMIN_AGENT_CREATE(3)`：内容「运营代客下单」，`user_id`/`user_type` 为当前后台用户。
- `payChannelCode` **只接受 `offline`**；其他值报现有 `1_011_000_117`；
  对公转账开关未开时报现有 `1_011_000_118`。
- 不创建/不使用购物车；不写客户地址簿；不使用优惠券、不抵扣积分；不代申请发票。
- 库存、价格、企业权限、订阅快照等由服务端重新计算，**客户端金额不参与**。
- 目标客户必须是已注册且启用的会员；不做代注册。

**错误**：

| code | 场景 |
|---|---|
| `1_011_012_000` | 客户不存在 |
| `1_011_012_001` | 客户被禁用 |
| `1_011_012_002` | 商品仅支持门店自提 |
| `1_011_012_003` | 支付方式不在允许范围 |
| `1_011_012_009` | 印刷定制商品不支持代客下单 |
| `1_011_012_010` | 实物单未选择客户地址 / 客户没有地址 |
| 现有商品/库存/企业/地址错误码 | 沿用，不重复造 |

---

## 3. 发起在线支付

### `POST /trade/order/agent-pay`

权限：`trade:order:agent-create`

```jsonc
// Request
{ "orderId": 123456, "channelCode": "wx_native" }   // 或 "alipay_pc"
```

```jsonc
// Response
{
  "code": 0,
  "data": {
    "status": 0,                 // 0 待支付 / 10 已支付（PayOrderStatusEnum）
    "displayMode": "qr_code",    // qr_code / url
    "displayContent": "weixin://wxpay/bizpayurl?…"
  }
}
```

**规则**：

- 只允许 `wx_native` / `alipay_pc`；`mock`、`wallet`、`wx_lite` 等一律 `1_011_012_003`。
- 订单必须是代客订单（`agent_user_id != null`）、未支付、存在 `payOrderId`；
  否则 `1_011_012_004` / `1_011_012_005`。
- 服务端调用 `PayOrderApi#submitOrder`，**不经过**管理端收银台的 `/pay/order/submit`。
- 渠道未配置 / 被停用 / 三方调用失败 → 透出支付模块的可读错误；订单保持待支付。
- 页面内可重复调用（重新生成二维码、切换微信/支付宝）；离开代客下单页后不提供该入口。

---

## 4. 支付状态轮询

### `GET /trade/order/agent-pay-status?orderId=123456`

权限：`trade:order:agent-create`

```jsonc
{
  "code": 0,
  "data": {
    "orderId": 123456,
    "orderStatus": 0,        // TradeOrderStatusEnum
    "payStatus": false,
    "payOrderId": 98765
  }
}
```

**规则**：

- 若支付单已成功而贸易订单仍未更新，调用既有 `syncOrderPayStatusQuietly` 兜底后再返回。
- 不返回客户敏感信息；页面只据此判断「继续展示二维码 / 支付成功 / 已关闭」。

---

## 5. 订单列表 / 详情新增字段

### `GET /trade/order/page`

新增 query：`agentCreated`（`true` 仅代客 / `false` 仅自助 / 不传全部）。

新增响应字段（`TradeOrderPageItemRespVO`）：

```jsonc
{
  "id": 123456,
  "agentUserId": 1,             // null = 客户自助
  "agentUserName": "运营小王",   // 账号不存在时为 null，前端显示 后台用户#1
  "...": "其余字段不变"
}
```

- 列表与统计（`GET /trade/order/summary`）必须使用同一 `agentCreated` 过滤口径。
- `agentUserName` 由 `AdminUserApi.getUserMap` 批量解析，**不逐笔查**。

### `GET /trade/order/get-detail`

新增响应字段：

```jsonc
{
  "agentUserId": 1,
  "agentUserName": "运营小王",
  "createTime": 1762300000000,
  "...": "其余字段不变"
}
```

- 详情页展示「代下单人」与下单时间（使用订单 `createTime`）。
- 不提供修改/清除代下单人的接口。

---

## 6. 后台代客发起售后

### `POST /trade/after-sale/agent-create`

权限：`trade:after-sale:agent-create`（**独立于** `trade:after-sale:agree` / `refund`）

```jsonc
// Request（复用 AppAfterSaleCreateReqVO）
{
  "orderItemId": 555,
  "way": 1,                    // 1 退款 / 2 退货退款
  "refundPrice": 2600,
  "applyReason": "客户申请退款",
  "applyDescription": "…",
  "applyPicUrls": ["…"]
}
```

```jsonc
// Response
{ "code": 0, "data": 9001 }    // 售后编号
```

**规则**：

- 订单必须是代客订单（`agent_user_id != null`），否则 `1_011_012_008`。
- 复用现有校验：订单项存在、未重复申请、退款金额 ≤ 订单项实付、订单已支付且未取消、
  退货退款需已发货、拼团进行中不可售后等。
- 售后单 `user_id` = 订单客户；`creator` = 当前后台用户；日志类型 `ADMIN_AGENT_CREATE(13)`。
- 在线支付按现有 `payRefundApi` 原路退；对公/线下支付由财务线下打款，系统记录退款状态。
- 客户侧仍不能自助发起售后；本接口不经会员鉴权。

---

## 7. 权限与菜单

| 权限 | 用途 | 前端落点 |
|---|---|---|
| `trade:order:agent-create` | 代客下单页、结算、创建、支付、轮询 | 订单列表「代客下单」按钮、代客下单页 |
| `trade:after-sale:agent-create` | 代客发起售后 | 订单详情「代客发起售后」按钮 |

- 两个权限点都必须在 `system_menu` 登记，否则 `v-hasPermi` 会把按钮从 DOM 删掉（超管也一样）。
- 菜单 SQL 用子查询取父级，不写死菜单 id（照 013）。
- 「代客发起售后」入口只在 `agentUserId != null` 且订单可售后时显示；接口再校验一次。
- 代客售后与现有售后审核/退款权限分离：有 `agent-create` 不代表能同意/退款。

---

## 8. 兼容性

- 既有订单列表/详情字段只增不改；前端旧版本忽略新字段即可。
- 既有 `POST /pay/order/submit` 行为不变；代客支付走新接口。
- 既有会员下单、对公转账确认收款、后台售后动作均不受影响。
- 新增来源值 40 只影响管理端来源筛选/展示；C 端不展示来源。
