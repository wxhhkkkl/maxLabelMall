# C 端接口契约（007 虚拟商品）

> 前缀：**只走 `/app-api`**（宪法约束）。本特性**不新增接口**，只改两个既有接口的字段。

---

## 1. `GET /trade/order/settlement` —— **响应**加 `deliveryType`

> **变化点**：响应多一个字段；与此同时**前端不再传** `deliveryType` 这个 query 参数。

```jsonc
// 请求（query）
//   ⚠️ 007 起**不再传 `deliveryType`** —— 由服务端按商品判定。
//   传了也不会被采纳：落库与服务端分支都以"服务端算出来的那个"为准（FR-189）。
?items[0].skuId=271&items[0].count=1&pointStatus=false

// 响应
{ "code": 0, "data": {
  "type": 0,
  "deliveryType": 3,          // ← 新增：1 快递 / 2 自提 / **3 虚拟商品**
  "items": [ … ],
  "coupons": [ … ],
  "price": { "totalPrice": 600, "deliveryPrice": 0, "payPrice": 600, … },
  "address": null,            // 虚拟单没有地址
  "usePoint": 0, "totalPoint": 0, "promotions": []
}, "msg": "" }
```

**`deliveryType` 是权威值**：前端据它决定"要不要收货地址"（`3` → 不要），
MUST NOT 自己按商品或购物车内容推断（购物车接口根本不返回商品的配送方式）。

**判定规则**（服务端，`TradePriceServiceImpl#resolveDeliveryType`）：
所有商品都**只支持虚拟商品** → `3`；否则 → `1`。
"配送方式为空"与"同时含快递"都按**快递**（见 spec 的 Edge Cases）。

## 2. `POST /trade/order/create` —— **入参**的 `deliveryType` 行为明确化

```jsonc
// 请求
{ "items": [{ "skuId": 271, "count": 1 }],
  "pointStatus": false,
  "deliveryType": 3,          // ← 必须**回传结算响应里的那个**
  "remark": "…",              // 买家留言（006）
  "invoice": { … } }          // 开票（006），不传 = 不开票
```

| 规则 | 说明 |
|---|---|
| `deliveryType` 仍必填 | `AppTradeOrderCreateReqVO` 上的 `@AssertTrue` 没动；前端回传结算给的值 |
| **服务端以自己算的为准** | 订单落库与服务端分支用的是 `calculateRespBO.deliveryType`（FR-189）。客户端传的值不参与 |
| 传了错的会怎样 | `TradeDeliveryPriceCalculator` 仍会校验"商品是否支持该配送方式"，不匹配报「配送方式不正确」—— 这是既有的门，没有被放宽 |
| 虚拟单的地址 | 整个请求**不带 `addressId`**（带也不会被采纳，因为不走快递分支） |
| `addressId` 本来就选填 | 后端 `getAddress()` 是 `if (addressId != null)` —— **不是 007 改的**，自提一直如此 |

## 3. `GET /trade/order/get-detail` —— 虚拟单的 `receiver*` 为空

**MUST NOT 改字段**，但前端要按"有没有收货信息"决定渲不渲染：

```jsonc
{ "id": 900, "no": "o…", "status": 10,
  "deliveryType": 3,
  "receiverName": null, "receiverMobile": null,
  "receiverAreaName": null, "receiverDetailAddress": null,
  "userRemark": "…", "invoice": null, "items": [ … ] }
```

前端落点：`OrderDetailView.vue` 的「收货信息」卡片改成 `v-if="order.receiverName"` ——
否则虚拟单会渲染出一片空白（照 006「买家留言」那张卡片的做法）。

## 4. 前端落点汇总

| 文件 | 改动 |
|---|---|
| `api/order.ts` | 结算**不传** `deliveryType`；新增 `DELIVERY_TYPE_VIRTUAL = 3` |
| `types/order.ts` | `SettlementResp.deliveryType`（必填） |
| `CheckoutView.vue` | `isVirtual` computed；虚拟单不渲染地址卡片、不挑默认地址、提交不卡地址；下单时回传 `deliveryType` |
| `OrderDetailView.vue` | 「收货信息」卡片按 `order.receiverName` 决定渲不渲染 |
