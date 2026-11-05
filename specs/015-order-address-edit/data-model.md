# Data Model: 发货前修改收货地址

**本特性无数据库变更。** 复用既有 `trade_order` 收货快照字段：

| 字段 | 说明 | 本特性 |
|---|---|---|
| `id` | 订单编号 | 请求必填 |
| `user_id` | 会员编号 | 归属校验 |
| `status` | 订单状态 | 必须为 10（待发货） |
| `receiver_name` | 收件人 | 可更新 |
| `receiver_mobile` | 收件人手机 | 可更新 |
| `receiver_area_id` | 收件人地区编号 | 可更新 |
| `receiver_detail_address` | 详细地址 | 可更新 |
| `delivery_price` / `pay_price` | 运费 / 应付 | **不变** |

`member_address` 只作为来源读取，本特性**不写**。
