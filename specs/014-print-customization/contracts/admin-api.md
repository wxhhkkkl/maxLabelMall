# 管理端接口契约（014 印刷定制）

> 前缀：只走 `/admin-api`（宪法）。统一响应 `{ "code": 0, "data": …, "msg": "" }`；
> 请求头沿用既有 `tenant-id` / `Authorization`。
> 金额为 **分**，面积为 **㎡**（4 位小数）。

权限点：

| 权限 | 用途 |
|---|---|
| `trade:customization-material:query` | 材质 / 区间查询 |
| `trade:customization-material:create` / `:update` / `:delete` | 材质维护 |
| `trade:customization-config:query` / `:update` | 全局计价参数 |
| `trade:order:query`（既有） | 订单列表与详情 |

---

## 1. 材质管理

### `GET /admin-api/trade/customization/material/page`

参数：`pageNo`、`pageSize`、`name`（模糊）、`status`。

```jsonc
{ "code": 0, "data": { "total": 12, "list": [
  {
    "id": 3, "name": "可移除铜版",
    "basePrice": 1138,
    "printCoefficient": 4.0000, "printingCoefficient": 1.5000,
    "minQuantity": 1, "maxQuantity": 100000,
    "minWidth": 10, "maxWidth": 2000,
    "minHeight": 10, "maxHeight": 2000,
    "status": 0, "sort": 0,
    "areaRules": [
      { "id": 11, "minArea": 0, "maxArea": 10, "printCoefficient": 2.0000, "dieCutCoefficient": 1.0000 },
      { "id": 12, "minArea": 10, "maxArea": 100, "printCoefficient": 1.8000, "dieCutCoefficient": 1.0000 }
    ]
  }
] }, "msg": "" }
```

### `POST /admin-api/trade/customization/material/create`

### `PUT /admin-api/trade/customization/material/update`

**请求体（新增 / 修改共用；区间随材质一起保存）**

```jsonc
{
  "id": 3,                         // update 时必填
  "name": "可移除铜版",
  "basePrice": 1138,
  "printCoefficient": 4.0,
  "printingCoefficient": 1.5,
  "minQuantity": 1, "maxQuantity": 100000,
  "minWidth": 10, "maxWidth": 2000,
  "minHeight": 10, "maxHeight": 2000,
  "status": 0, "sort": 0,
  "areaRules": [
    { "minArea": 0, "maxArea": 10, "printCoefficient": 2.0, "dieCutCoefficient": 1.0 },
    { "minArea": 10, "maxArea": 0, "printCoefficient": 1.4, "dieCutCoefficient": 0 }
  ]
}
```

**校验（拒绝保存并返回具体位置）**：

- `basePrice > 0`；系数 `> 0`；`minQuantity <= maxQuantity`；`minWidth <= maxWidth`；`minHeight <= maxHeight`；
- `areaRules` 非空、从最小面积起**连续覆盖**、最后一条 `maxArea = 0`；重叠 / 倒挂 / 断档
  → `CUSTOMIZATION_AREA_RULE_INVALID`，`msg` 指出冲突的两条区间。

### `DELETE /admin-api/trade/customization/material/delete?id=3`

软删除。停用与删除都不影响历史订单（订单用快照展示）。

### `PUT /admin-api/trade/customization/material/update-status`

```jsonc
{ "id": 3, "status": 1 }
```

---

## 2. 全局计价参数

### `GET /admin-api/trade/customization/config/get`

```jsonc
{ "code": 0, "data": {
  "printBasePrice": 20000,       // 200 元
  "printingBasePrice": 60000,    // 600 元
  "dieCutBasePrice": 40000,      // 400 元
  "urgentBasePrice": 5000,       // 50 元
  "urgentCoefficient": 1.1000
}, "msg": "" }
```

### `PUT /admin-api/trade/customization/config/save`

请求体同上。校验：初始价 `>= 0`、`urgentCoefficient >= 1`。保存后**下一次报价立即生效**；
同时写一条操作日志（操作人、时间、变更前后值，FR-380）。

---

## 3. 订单列表与详情

### 3.1 列表筛选（既有接口扩展）

`GET /admin-api/trade/order/page` 新增查询参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `customization` | boolean | `true` = 只看定制订单；不传 = 全部（与改动前一致） |

实现按 `trade_order.customization` 列筛选（R93），统计接口复用同一条件。

### 3.2 详情（既有接口扩展）

`GET /admin-api/trade/order/get?id=123` 在定制订单上增加：

```jsonc
{
  "id": 123, "customization": true,
  "customizationDetail": {
    "id": 789,
    "materialId": 3, "materialName": "可移除铜版",
    "width": 50, "height": 100, "quantity": 1000,
    "unitArea": 0.0050, "totalArea": 5.0000,
    "mode": 2, "form": 2, "sheetSpec": "A4", "finishing": 1, "tearLine": false, "tearLine": false,
    "rowCount": 1, "columnCount": 1, "rowGap": 3, "columnGap": 3,
    "axisDiameter": "25.4mm",
    "dieCut": false, "urgent": false,
    "remark": "请按附件颜色印刷",
    "breakdown": { "print": 34100, "printing": 0, "dieCut": 0, "urgent": 0,
                   "subtotal": 34100, "total": 34100 },
    "files": [ { "name": "artwork.pdf", "url": "https://…/artwork.pdf", "size": 1048576 } ],
    "paramSnapshot": { /* R92 的快照结构 */ }
  }
}
```

> 后台看到的是**下单时的参数快照**，不是当前参数；这是客诉时能解释金额的依据（SC-142 / FR-376）。

---

## 4. 错误码

| 错误码 | 含义 |
|---|---|
| `CUSTOMIZATION_MATERIAL_NOT_AVAILABLE` | 材质不存在 / 已停用 / 已删除 |
| `CUSTOMIZATION_SPEC_OUT_OF_RANGE` | 数量或宽高超出材质上下限 |
| `CUSTOMIZATION_AREA_NOT_MATCHED` | 整单总面积匹配不到区间（配置异常或越界） |
| `CUSTOMIZATION_AREA_RULE_INVALID` | 区间重叠 / 倒挂 / 断档 |
| `CUSTOMIZATION_FILE_INVALID` | 文件数量、格式或域名不合规 |
| `CUSTOMIZATION_PRICE_CHANGED` | 提交价与最新报价不一致（返回新报价，不建单） |
