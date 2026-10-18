# 数据模型（007 虚拟商品）

**本特性不新建表、不新增列、不新增索引。** 全部落在两个**既有字段的新取值**上。

---

## 1. `product_spu.delivery_types` —— 新增取值 `3`

```sql
`delivery_types` varchar(32) NOT NULL DEFAULT '' COMMENT '配送方式数组',
```

- 逗号分隔的多选（`IntegerListTypeHandler`），现存值只有 `1` / `2`，空串也合法存在。
- **新增 `3` = 虚拟商品**。`varchar(32)` 放得下，与既有格式兼容。
- ⚠️ **"是不是虚拟商品"的判据是「只含 3」**，不是「含 3」：
  一个 `[1,3]` 的商品**支持快递**，仍按实物处理（要地址）。
- ⚠️ **空串不算虚拟**：建表默认值就是 `''`，历史数据里有若干条。当成虚拟会让那些
  **实物**商品收不到收货地址。

| 值 | 含义 | 结算时本单要不要地址 |
|---|---|---|
| `1` / `2` / `1,2` | 实物 | **要** |
| `''` | 历史数据（多为实物） | **要**（保守） |
| `1,3` | 支持快递的虚拟商品（配置不当） | **要**（保守） |
| **`3`** | **纯虚拟商品** | **不要** |

## 2. `trade_order.delivery_type` —— 新增取值 `3`

- 既有列、既有枚举（`DeliveryTypeEnum` 加一档）。
- 值为 `3` 时，`receiver_name` / `receiver_mobile` / `receiver_area_id` /
  `receiver_detail_address` / `pick_up_store_id` / `pick_up_verify_code` **全部为空**
  —— **这是刻意的**，软件没有收货信息。
- 值为 `3` 时 `delivery_price` 必然为 `0`（虚拟商品不产生运费）。

> ⚠️ **不改 `trade_order` 的结构**：`delivery_type` 本来就是 `tinyint`，
> 3 放得下。"是否虚拟"是**推导出来的**，不再存第二份真相。

## 3. `system_dict_data` —— 新增一行

| 列 | 值 |
|---|---|
| `dict_type` | `trade_delivery_type` |
| `label` | 虚拟商品 |
| `value` | `3` |
| `sort` | 3 |
| `status` | 0（启用） |

脚本：`011-virtual-goods-dict.sql`（幂等）。

## 4. 状态机（**不变**）

虚拟商品单走**既有**状态机，没有新状态、没有新流转：

```
待支付(0) --支付--> 待发货(10) --后台「发货→无需发货」--> 已发货(20) --客户确认收货--> 已完成(30)
```

- ⚠️ **「发货」这一步本次才允许虚拟单走**（原来要求 `deliveryType == 快递`）。
  复用的是既有的「无需发货」分支（`logisticsId = 0`），**没加新接口**。
- ⚠️ 客户仍需点「确认收货」才到「已完成」—— 复用既有链路的必然结果，本次不改。

## 5. 判定规则（服务端唯一一处）

`TradePriceServiceImpl#resolveDeliveryType`：

```
所有商品都 (含 3 且 不含 1 且 不含 2)  →  VIRTUAL(3)
否则                                  →  EXPRESS(1)
```

- 结果写进 `TradePriceCalculateRespBO.deliveryType` → 映射到结算响应 `deliveryType`。
- **下单落库也用它**（`TradeOrderConvert` 显式映射），**不用**请求里传的值 ——
  请求里的 `deliveryType` 只用于"商品支不支持"的既有校验。
