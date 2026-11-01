# 数据模型：印刷定制下单

**Feature**: `014-print-customization` | **Date**: 2026-11-01

四张新表，全部继承 `BaseDO`（`creator/create_time/updater/update_time/deleted`）并带
`tenant_id`。表名前缀 `trade_customization_`，与既有的 `trade_order_*` 一致。

金额字段一律 **`int`、单位分**；面积字段 **`decimal(12,4)`、单位 ㎡**；系数
**`decimal(10,4)`**。

---

## 1. `trade_customization_material` —— 定制材质

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK, auto | |
| `name` | varchar(64) | NOT NULL | 材质名称，如「透明PET」 |
| `description` | varchar(1000) | NULL | 材质说明（展示给客户） |
| `base_price` | int | NOT NULL | 材质基价，单位 **分/㎡**（截图 11.38 元/㎡ → 1138） |
| `print_coefficient` | decimal(10,4) | NOT NULL | 材质打印系数（截图 2 / 4） |
| `printing_coefficient` | decimal(10,4) | NOT NULL | 印刷系数（截图 1.0 ~ 1.5） |
| `min_quantity` | int | NOT NULL | 数量下限 |
| `max_quantity` | int | NOT NULL | 数量上限 |
| `min_width` / `max_width` | int | NOT NULL | 成品宽度上下限，mm |
| `min_height` / `max_height` | int | NOT NULL | 成品高度上下限，mm |
| `status` | tinyint | NOT NULL DEFAULT 0 | 0 启用 / 1 停用 |
| `sort` | int | NOT NULL DEFAULT 0 | 升序 |
| `tenant_id` | bigint | NOT NULL | |

**索引**：`idx_tenant_status (tenant_id, status, sort)`

**校验**：`base_price > 0`；系数 `> 0`；`min_* <= max_*`；
至少存在一条覆盖到「无上限」的面积区间（见下）。

---

## 2. `trade_customization_area_rule` —— 面积区间规则

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK, auto | |
| `material_id` | bigint | NOT NULL | 所属材质 |
| `min_area` | decimal(12,4) | NOT NULL | 区间下界（含），㎡，对应**整单总面积** |
| `max_area` | decimal(12,4) | NOT NULL | 区间上界（不含）；**0 = 无上限** |
| `print_coefficient` | decimal(10,4) | NOT NULL | 区间打印系数（与材质打印系数**相乘**，Q104） |
| `die_cut_coefficient` | decimal(10,4) | NOT NULL | 模切系数；`0` 表示不收按面积的模切费、但仍收模切初始价（Q105=B），**不代表不能模切** |
| `sort` | int | NOT NULL DEFAULT 0 | 升序 |
| `tenant_id` | bigint | NOT NULL | |

**索引**：`idx_tenant_material (tenant_id, material_id, min_area)`

**连续性校验（Q100=A，保存时强制）**：

1. 按 `min_area` 升序排序后，第一条必须从该材质的**最小起始面积**（默认 0）开始；
2. 相邻两条必须满足 `前一条.max_area = 后一条.min_area`（`max_area = 0` 只能出现在最后一条）；
3. 最后一条 `max_area` 必须为 `0`（无上限）；
4. 重叠、倒挂、断档 → 拒绝保存，错误信息指出冲突的两条区间。

---

## 3. `trade_customization_config` —— 全局计价参数（每租户一行）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK, auto | |
| `print_base_price` | int | NOT NULL | 打印初始价，分（截图 200 元 → 20000） |
| `printing_base_price` | int | NOT NULL | 印刷初始价，分（600 元 → 60000） |
| `die_cut_base_price` | int | NOT NULL | 模切初始价，分（400 元 → 40000） |
| `urgent_base_price` | int | NOT NULL | 加急初始价，分（50 元 → 5000） |
| `urgent_coefficient` | decimal(10,4) | NOT NULL | 加急系数（1.1） |
| `tenant_id` | bigint | NOT NULL | |

**唯一**：`uk_tenant (tenant_id)`

**校验**：初始价 `>= 0`；`urgent_coefficient >= 1`。

---

## 4. `trade_customization_order` —— 定制订单扩展（1:1 订单）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK, auto | |
| `order_id` | bigint | NOT NULL | 关联 `trade_order.id` |
| `material_id` | bigint | NOT NULL | 材质编号（快照另存，允许材质后来被删） |
| `material_name` | varchar(64) | NOT NULL | 材质名称快照 |
| `width` / `height` | int | NOT NULL | 成品宽高，mm |
| `quantity` | int | NOT NULL | 定制数量 N |
| `label_count` | int | DEFAULT 0 | 实际数量（Q111：不再采集，列保留） |
| `template_name` | varchar(128) | NULL | LabelShop 模板名称（Q111：不再采集，列保留） |
| `unit_area` | decimal(12,4) | NOT NULL | 单件面积，㎡ |
| `total_area` | decimal(12,4) | NOT NULL | 整单总面积 = `unit_area × quantity` |
| `printing` | bit(1) | NOT NULL DEFAULT 0 | 是否选择印刷（预印/多色） |
| `die_cut` | bit(1) | NOT NULL DEFAULT 0 | 是否模切 |
| `urgent` | bit(1) | NOT NULL DEFAULT 0 | 是否加急 |
| `remark` | varchar(500) | NULL | 客户备注 |
| `price_breakdown` | json | NOT NULL | `{print, printing, dieCut, urgent, subtotal, total}`，分 |
| `param_snapshot` | json | NOT NULL | 材质 + 区间 + 全局参数 + 计算结果（见 R92） |
| `files` | json | NOT NULL | `[{name, url, size, type}]`，1~5 个 |
| `tenant_id` | bigint | NOT NULL | |

**索引**：
- `uk_order (tenant_id, order_id)` —— 一笔订单至多一条定制记录
- `idx_tenant_material (tenant_id, material_id, create_time)`

**校验（建单时）**：`files` 1~5 个；扩展名与 URL 域名在白名单内；`quantity` 落在材质上下限；
宽高落在材质上下限；整单总面积能匹配到一条区间。

---

## 5. 对既有表的改动

### `trade_order`

| 变更 | 说明 |
|---|---|
| `ADD COLUMN customization bit(1) NOT NULL DEFAULT 0` | 是否定制订单；列表筛选、C 端详情联查的判据 |
| `ADD INDEX idx_tenant_customization (tenant_id, customization, status)` | 后台「只看定制单」 |

不改 `trade_order_item` 结构：订单项用 R90 的「count = N、单价向上取整、差额进商品级优惠」写法。

### 预置隐藏 SPU / SKU

迁移脚本插入：

- `product_spu`：名称「印刷定制服务（系统）」，`status = 1`（上架，否则 `validateSpuList` 会拒绝），
  `spec_type = 0`，`delivery_types = [1]`（快递），`delivery_template_id` 指向租户现有模板，
  `category_id = 0`（不进入任何分类页），`give_integral = 0`；
- `product_sku`：`price = 1`（占位，实际由 `customPrice` 覆盖），`stock = 1000000000`，
  `properties = []`。

ID 用一段保留区间（脚本里 `990001 / 990002`），并在 `trade_customization_config` 不额外存
关联——由常量引用；迁移脚本用 `INSERT ... SELECT ... WHERE NOT EXISTS` 保证幂等。

---

## 6. 状态与生命周期

- 定制订单沿用 `trade_order.status`（待支付 → 待发货 → 已发货 → 已完成；售后走既有流程），
  **不新增生产状态**（Q95=A）。
- 材质停用后：前台不再可选；历史订单通过快照与 `material_id` 仍可完整展示。
- 参数改价后：历史订单的 `price_breakdown` 与 `param_snapshot` 不变（SC-142）。
- 定制订单的退款：用户端无入口，后台走既有「订单退款」；退款金额取 `trade_order.pay_price`
  （含运费分摊，遵循既有口径）。
