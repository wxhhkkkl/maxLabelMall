# App API Contract — 修改订单收货地址

## PUT `/app-api/trade/order/update-address`

**鉴权**：登录会员（`getLoginUserId`）。

**请求体**：

```json
{
  "id": 1,
  "receiverName": "张三",
  "receiverMobile": "13800008888",
  "receiverAreaId": 110101,
  "receiverDetailAddress": "xx 路 1 号"
}
```

字段均必填；`receiverName` / `receiverMobile` / `receiverDetailAddress` 非空。

**响应**：

```json
{ "code": 0, "data": true, "msg": "" }
```

**错误码**：

| code | 场景 |
|---|---|
| `ORDER_NOT_FOUND` | 订单不存在、不属于当前会员 |
| `ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_DELIVERED` | 订单不是待发货 |

**副作用**：仅更新 `trade_order` 的 `receiver_*` 四个字段；写一条订单操作日志
`MEMBER_UPDATE_ADDRESS`。不改地址簿、金额、订单状态。
