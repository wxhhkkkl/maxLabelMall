# C 端接口契约（017 代客下单）

> 前缀 `/app-api`；本特性**不新增 C 端接口**，只增加一个只读字段并在两个写入口加拒绝。
> 所有金额单位：分；时间由 yudao 全局序列化为 epoch 毫秒（storefront `utils/time.ts` 处理）。

---

## 1. 订单列表新增字段

### `GET /trade/order/page`

响应项（`AppTradeOrderPageItemRespVO`）新增：

```jsonc
{
  "id": 123456,
  "agentCreated": true,     // true = 客服代下单；false / 缺省 = 客户自助
  "...": "其余字段不变"
}
```

- `agentCreated` 由服务端按 `trade_order.agent_user_id != null` 派生。
- **不返回**代下单人的姓名、账号或编号（FR-459）。
- storefront 在列表项显示中性标签「客服代下单」；不改状态、不改支付入口。

---

## 2. 订单详情新增字段

### `GET /trade/order/get-detail`

响应新增：

```jsonc
{
  "id": 123456,
  "agentCreated": true,
  "...": "其余字段不变"
}
```

- storefront 详情显示「客服代下单」标识，不展示运营身份（FR-460）。
- 前端据 `agentCreated` 隐藏「取消订单」与「申请退款」入口；
  服务端对应接口同样拒绝（见下）。

---

## 3. 客户取消订单：代客订单拒绝

### `DELETE /trade/order/cancel?id=123456`

代客订单（`agent_user_id != null`）：

```jsonc
{ "code": 1_011_012_006, "data": null, "msg": "代客订单不支持客户自行取消，请联系客服" }
```

- 仅未支付订单才会走到「能否取消」的判断；已支付订单沿用现有状态错误。
- 客户自助订单行为不变。
- 运营仍可在后台用现有 `PUT /trade/order/cancel` 取消未支付的代客订单。
- 服务端守卫放在 `TradeOrderUpdateServiceImpl#cancelOrderByMember`，不依赖前端隐藏。

---

## 4. 客户申请售后：代客订单拒绝

### `POST /trade/after-sale/create`

代客订单（`agent_user_id != null`）：

```jsonc
{ "code": 1_011_012_007, "data": null, "msg": "代客订单不支持客户自助退款，请联系客服处理" }
```

- guard 放在 `AfterSaleServiceImpl#validateOrderItemApplicable`；
  企业授权 / 个人订阅的现有守卫顺序不变，代客守卫紧随其后。
- 客户自助订单行为不变。
- 运营在后台用 `POST /admin-api/trade/after-sale/agent-create` 代客户发起售后（见 `admin-api.md` §6）。

---

## 5. 客户仍可执行的操作

| 操作 | 代客订单 | 说明 |
|---|---|---|
| 查看列表 / 详情 | ✅ | 显示「客服代下单」标识 |
| 支付未支付订单 | ✅ | 与普通订单同一支付单；Q123/Q130 未禁止 |
| 取消未支付订单 | ❌ | 返回 `1_011_012_006` |
| 申请售后 / 退款 | ❌ | 返回 `1_011_012_007`；由运营后台代发起 |
| 查看「我的售后」 | ✅ | 后台代发的售后单 `user_id` 为客户，正常展示 |
| 撤销售后 | ✅ | 沿用现有售后撤销规则（Q126 Assumption） |
| 再次购买 | ✅ | 沿用现有 `canReorder` 规则（普通订单为 true） |
| 删除已取消 / 已完成订单 | ✅ | 沿用 032 的既有规则（仅对 C 端隐藏） |
| 申请开票（支付后） | ✅ | 沿用 006 的 C 端补开能力 |

---

## 6. 兼容性

- 只增字段，不删不改既有字段；旧版 storefront 忽略 `agentCreated` 也能正常工作。
- 两个写入口的拒绝只对 `agent_user_id != null` 的订单生效；历史订单与客户自助订单不受影响。
- 不新增 C 端路由或页面；标识与守卫都在既有订单列表/详情内完成。
