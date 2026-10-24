# 管理端接口契约（010 订单物流跟踪）

> 前缀：**只走 `/admin-api`**（宪法约束）。

---

## 1. `GET /trade/order/get-express-track-list` —— 物流轨迹（**沿用既有接口**）

```
GET /admin-api/trade/order/get-express-track-list?id=900
```

| 项 | 值 |
|---|---|
| 权限 | `trade:order:query`（沿用） |
| 响应 | 与 C 端同形：`data: [{ time: epoch 毫秒, content }]`，**倒序** |
| 未发货 | `[]` |
| 编码缺失 | `1_011_004_103` → 管理端显示「该快递公司暂不支持查询」 |
| 第三方失败 | `1_011_004_101/102` → 显示「暂时无法获取物流信息」 |

**本特性只改前端**：`views/mall/trade/order/detail/index.vue` 现在没有 try/catch，
失败时时间轴静默不渲染。改为捕获并显示降级提示；空数组显示「暂无轨迹信息」。
**不加**列表内联展开（Q56）。

⚠️ **后台的轨迹请求带 `silent: true`**：不弹全局错误通知，并把业务 `code` 一起 reject，
所以详情页能内联区分三种情况：

| 情况 | 表现 |
|---|---|
| 精简列表里 `kd100Code` 为空 | 不发起轨迹请求，直接「该快递公司暂不支持查询」（见 §2.3） |
| 返回 `1_011_004_103` | 内联「该快递公司暂不支持查询」 |
| 其它失败（101/102/网络/超时） | 内联「暂时无法获取物流信息，请稍后重试」 |

---

## 2. 快递公司维护 —— 新增 `kd100Code`

### 2.1 数据结构

```jsonc
// 创建 / 更新请求（DeliveryExpressCreateReqVO / UpdateReqVO）
{
  "code": "SF",             // ← 不动：微信物流同步用的编码
  "kd100Code": "shunfeng",  // ← 新增：快递100 的 com 编码，选填
  "name": "顺丰速运",
  "logo": "…",
  "sort": 2,
  "status": 0
}

// 详情 / 分页响应（DeliveryExpressRespVO）—— 原样回带 `kd100Code`
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `code` | 是 | **保持现状语义**：微信小程序发货信息同步用的公司编码（`SF/STO/ZTO/YD`…）。MUST NOT 改成快递100 全拼 |
| `kd100Code` | **否** | 快递100 的 `com` 编码（`shunfeng/shentong/...`）。为空 = 暂不支持查询 |

- 校验：选填；若填了，按普通字符串长度校验（≤ 64）。
- `code` 的唯一性校验**不变**。
- 后台表单 `ExpressForm.vue` 加一个「快递100 编码」输入框（选填，带说明文案）。
- 列表页可加一列展示，便于运营核对（可选，不算需求）。

### 2.2 数据回填

见 [../data-model.md](../data-model.md) §2。关键边界：

- **只写 `kd100_code`**，`code` 列一个字节不动。
- 无法确认的（安能）留空，不猜。

### 2.3 精简列表（`GET /trade/delivery/express/list-all-simple`）

响应项新增 `kd100Code`：

```jsonc
{ "code": 0, "data": [
  { "id": 2, "name": "顺丰速运", "kd100Code": "shunfeng" },
  { "id": 13, "name": "安能",     "kd100Code": null }
] }
```

后台订单详情**用它先判断能不能查**：为空 → 「该快递公司暂不支持查询」且不调轨迹接口；
非空 → 调用轨迹接口，失败 → 通用降级提示（避免一次注定失败的调用与错误通知）。

---

## 3. 明确不改的管理端能力

| 项 | 结论 |
|---|---|
| 订单列表的「快递编号 + 查看物流」 | 维持现状（Q56） |
| 发货表单（选快递公司 + 填运单号） | 不改 |
| 微信小程序物流同步 | 不改（继续读 `code`） |

---

## 4. `GET /trade/order/get-express-map-url` —— 物流地图（010 追加）

```
GET /admin-api/trade/order/get-express-map-url?id=900
```

| 项 | 值 |
|---|---|
| 权限 | `trade:order:query` |
| 响应 | `{ code: 0, data: "https://api.kuaidi100.com/tools/map/V2/..." }` |
| 前端 | 进入订单详情**自动加载** → iframe 内嵌；请求带 `silent: true`，错误内联显示 |

错误码同 C 端（104 未配置 / 105 无法生成 / 103 不支持 / 101·102 三方失败）。
后台在物流区下方直接展示地图与提示，不渲染空 iframe。

---

## 5. `PUT /trade/order/delivery` —— 请求加发货地址（010 追加）

```jsonc
{ "id": 900, "logisticsId": 10, "logisticsNo": "SF1234567890",
  "deliveryFromAreaId": 110108,          // 省市区编号，必填（快递发货）
  "deliveryFromAddress": "西北旺亮甲店永丰中路100号北京人才教育基地红楼107" }
```

- 快递发货时 `deliveryFromAreaId + deliveryFromAddress` **必填**（表单标签「发货详细地址」），后端保存到订单；
- 「无需发货」（`logisticsId=0`）不校验、不保存。

## 6. `GET /trade/order/get-delivery-default-address` —— 发货默认地址（010 追加）

```jsonc
{ "code": 0, "data": { "areaId": 110108, "address": "西北旺亮甲店永丰中路100号北京人才教育基地红楼107" } }
```

- 权限：`trade:order:update`
- 发货表单打开时拉取，预填省市区与详细地址；运营可改。
