# 数据模型（010 订单物流跟踪）

**一句话**：**没有新表**，物流轨迹**不落库**。唯一的数据变更是给
`trade_delivery_express` 加一列 `kd100_code`，并把租户 162 的快递公司编码补齐。
理由见 [research.md](./research.md) R48。

---

## 1. `trade_delivery_express` —— 加一列 `kd100_code`（**新增**）

```sql
ALTER TABLE `trade_delivery_express`
  ADD COLUMN `kd100_code` varchar(64) NULL DEFAULT NULL COMMENT '快递100 的公司编码（com 参数）';
```

| 列 | 含义 | 约束 |
|---|---|---|
| `code`（**保持原样**） | 微信小程序发货信息同步用的公司编码（`SF/STO/ZTO/YD`…） | 现有 NOT NULL + 唯一校验不变 |
| `kd100_code`（新增） | 快递100 实时查询的 `com` 编码（`shentong/shunfeng/...`） | **可空**。为空 = 该快递公司暂不支持查询（FR-261） |

**为什么可空**：历史数据里就有说不清对应关系的快递公司（如「安能」的两条产品线）。
硬填一个会变成"看起来能查、实际查错公司"——**留空是诚实的**（FR-269）。

**为什么不在 `code` 上改**：`code` 同时供微信小程序物流同步使用（spec E11）。
改它会让微信拿到非法公司编码。这是本特性**最容易踩的坑**。

**后台维护**：`DeliveryExpressBaseVO` 加 `kd100Code`（选填），
`ExpressForm.vue` 加一个「快递100 编码」输入框；列表可显示为辅助列。

---

## 2. 租户 162 的 13 家快递公司 —— 回填 `kd100_code`

来源：`scripts/backfill_legacy_mall.py` 的 `EXPRESS_CODES` + 快递100 官方编码表。

| 历史名称 | 现有 `code`（不动） | `kd100_code`（回填） | 状态 |
|---|---|---|---|
| 申通 | `STO` | `shentong` | ✅ 确认 |
| 顺丰快递 | `SF` | `shunfeng` | ✅ 确认 |
| 中通快递 | `ZTO` | `zhongtong` | ✅ 确认 |
| 韵达 | `YD` | `yunda` | ✅ 确认 |
| 圆通快递 | `YTO` | `yuantong` | ✅ 确认 |
| 圆通速递 | `YTO` | `yuantong` | ✅ 确认 |
| 京东 | `JD` | `jd` | ✅ 确认 |
| 极兔速递 | `JTSD` | `jtexpress` | ✅ 确认 |
| 德邦 | `DBL` | `debangwuliu` | ✅ 确认 |
| 优速 | `UC` | `youshuwuliu` | ✅ 确认 |
| 跨越 | *（空）* | `kuayue` | ✅ 确认（跨越速运） |
| 顺心捷达 | *（空）* | `sxjdfreight` | ✅ 确认 |
| **安能** | `ANE` | **留空** | ⚠️ 快递100 有 `ane66`（安能快递）与 `annengwuliu`（安能快运），无法确认 → 不猜 |

> ⚠️ 回填 SQL **只写 `kd100_code`**，`code` 列一个字节都不动。
> 跨越 / 顺心捷达原本 `code` 为空，回填时按 `name` 定位。

---

## 3. 物流轨迹 —— **不落库**

| 项 | 值 |
|---|---|
| 存储 | Redis 缓存，不建表、不写订单字段 |
| 缓存键 | `kd100_code + '-' + 运单号 + '-' + 收件人手机号`（沿用现有 `EXPRESS_TRACK`） |
| TTL | 1 小时（`spring.cache.redis.time-to-live`） |
| 空结果 | **也会缓存**（避免反复触发计费/限频，见 R50） |
| 顺序 | 服务端按 `time` **倒序**后返回 |
| 数据形态 | `[{ time: epoch 毫秒, content: "..." }]`（与 `AppOrderExpressTrackRespDTO` 一致） |

**不落库的理由**：轨迹是第三方的实时数据，没有业务价值需要持久化；落库会引入
同步/清理/一致性三套成本，而缓存已经解决"1 小时内重复查看"的成本问题。

---

## 4. 展示资格（C 端与后台**同一口径**）

```
有运单号        := trade_order.logistics_no 非空
可查编码        := 该单 logistics_id 对应的 trade_delivery_express.kd100_code 非空
展示物流区块    := 有运单号           ← 未发货 / 虚拟商品 / 自提单都不满足
拉起轨迹查询    := 有运单号 AND 可查编码
```

| 订单情形 | 物流区块 | 轨迹区 |
|---|---|---|
| 未发货 | 不展示 | — |
| 虚拟商品 / 到店自提 | 不展示 | — |
| 已发货 + `kd100_code` 有值 | 展示（公司/运单号/发货时间） | 轨迹时间轴；空数组 → 「暂无轨迹信息」 |
| 已发货 + `kd100_code` 为空 | 展示（公司/运单号/发货时间） | 「该快递公司暂不支持查询」 |
| 已发货 + 第三方超时/报错 | 展示 | 「暂时无法获取物流信息，请稍后重试」 |

**订单状态**不参与判定：只要运单号在，`已发货(20)` / `已完成(30)` / 已退款 都照常可查
（轨迹是历史事实，不随订单状态消失）。

---

## 5. 与微信小程序物流同步的边界（**回归护栏**）

| 动作 | 用的列 |
|---|---|
| C 端 / 后台**查轨迹** | `kd100_code` |
| 发货后**同步微信** | **`code`**（保持原值） |

两条链路互不读写对方的列。任何"把 `code` 改成全拼"的实现都视为**错误**。
`TradeStatusSyncToWxaOrderHandler` 本特性**不修改**。

---

## 7. 物流地图（010 追加）

- **没有新表**。地图由快递100 `maptrack.do` 返回 `trailUrl`，前端 iframe 展示。
- 配置（`yudao.trade.express.kd100`）：
  - `map-config-key`：地图模板 id（`.env` 的 `KUAIDI100_TEMPLATE`）
  - `map-from`：**单一发货地址**（省市区，业务事实，由所有者填写）
  - 任一为空 → `1_011_004_104`（物流地图未配置）
- `to` 由 `trade_order.receiver_area_id` + `AreaUtils.format()` 解析；
  为空（历史导入单）→ `1_011_004_105`（无法生成地图），**不解析详细地址**。
- 缓存：`express_map`，1 小时；键 `code-num-phone-to`，避免重复计费。

---

## 8. 发货地址（010 追加，2026-10-24）

```sql
ALTER TABLE `trade_order`
  ADD COLUMN `delivery_from_area_id` int NULL COMMENT '发货地址地区编号',
  ADD COLUMN `delivery_from_address` varchar(255) NULL COMMENT '发货地址详情';
```

- 发货时后台填写：省市区（`delivery_from_area_id`，树选择）+ 详细地址（`delivery_from_address`），
  默认取配置的 `default-from-area-id` / `default-from-address`，可改。
- 物流地图 `from` = 订单的发货地址地区（缺省 → 配置默认）；`to` = `receiver_area_id`。
- 配置（`yudao.trade.express.kd100`）：`map-config-key`、`default-from-area-id`、`default-from-address`。
- 迁移：`sql/mysql/019-delivery-from-address.sql`（幂等）。
