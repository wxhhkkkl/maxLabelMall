# C 端接口契约（014 印刷定制）

> 前缀：只走 `/app-api`（宪法）。统一响应 `{ "code": 0, "data": …, "msg": "" }`；
> 请求头沿用既有 `tenant-id` / `terminal` / `Authorization`。
> 金额一律为 **分**；面积一律为 **㎡**（4 位小数）。

---

## 1. 可选材质

### `GET /app-api/trade/customization/material/list`

**公开**（不需要登录）。只返回启用的材质，按 `sort` 升序。

```jsonc
{ "code": 0, "data": [
  {
    "id": 3,
    "name": "可移除铜版",
    "minQuantity": 1, "maxQuantity": 100000,
    "minWidth": 10, "maxWidth": 2000,
    "minHeight": 10, "maxHeight": 2000
  }
], "msg": "" }
```

> 只给**允许范围**，不下发系数与基价 —— 价格永远由服务端算。

---

## 2. 报价

### `POST /app-api/trade/customization/quote`

**公开**。纯计算，不落库、不创建订单。传 `addressId` 时一并返回运费。

**请求**

```jsonc
{
  "materialId": 3,
  "width": 50, "height": 100, "quantity": 1000,
  "mode": 2,                 // 1 印刷 / 2 打印 / 3 两者 / 4 空白标签
  "form": 2,                 // 1 卷筒 / 2 平张（报价可不传）
  "sheetSpec": "A4", "finishing": 1, "tearLine": false,
  "rowCount": 1, "columnCount": 1, "rowGap": 3, "columnGap": 3,
  "axisDiameter": "25.4mm",
  "dieCut": false, "urgent": false,
  "addressId": 7,            // 可选；不传则 freight = 0
  "deliveryType": 1          // 可选，默认 1 快递
}
```

**响应**

```jsonc
{ "code": 0, "data": {
  "materialId": 3, "materialName": "可移除铜版",
  "unitArea": 0.0050, "totalArea": 5.0000,
  "breakdown": {
    "print": 34100, "printing": 0, "dieCut": 0, "urgent": 0,
    "subtotal": 34100, "total": 34100
  },
  "freight": 800,
  "payable": 34900,
  "quoteTime": "2026-11-01 10:00:00"
}, "msg": "" }
```

**错误**：材质不存在 / 已停用（`CUSTOMIZATION_MATERIAL_NOT_AVAILABLE`）；
宽高或数量越界（`CUSTOMIZATION_SPEC_OUT_OF_RANGE`，`msg` 里带该材质的允许范围）；
整单总面积匹配不到区间（`CUSTOMIZATION_AREA_NOT_MATCHED`）。

---

## 3. 创建定制订单

### `POST /app-api/trade/customization/order/create`

**需要登录**（未登录返回 `code=401`）。

**请求**

```jsonc
{
  "materialId": 3,
  "width": 50, "height": 100, "quantity": 1000,
  "mode": 2,
  "form": 2, "sheetSpec": "A4", "finishing": 1, "tearLine": false, "tearLine": false,
  "rowCount": 1, "columnCount": 1, "rowGap": 3, "columnGap": 3,
  "axisDiameter": "25.4mm",
  "dieCut": false, "urgent": false,
  "files": [
    { "name": "artwork.pdf", "url": "https://img.yuwangchenfa.com/20261101/artwork.pdf",
      "size": 1048576, "type": "application/pdf" }
  ],
  "remark": "请按附件颜色印刷",
  "expectedAmount": 34900,      // 客户在页面上看到的应付金额（含运费）
  "addressId": 7,
  "deliveryType": 1,
  "invoice": { /* 与既有 createOrder 的 invoice 结构一致，可选；与订单同一事务落库（Q107=A） */ }
}
```

**响应**

```jsonc
{ "code": 0, "data": {
  "id": 123,                    // trade_order.id
  "payOrderId": 456,            // 支付单号；可能为 null（应付 0 元时）
  "customizationOrderId": 789
}, "msg": "" }
```

**价格已变（Q101）**

```jsonc
{ "code": 0, "data": {
  "changed": true,
  "quote": { /* 同 §2 的响应体 */ }
}, "msg": "" }
```

> ⚠️ 用 `changed` 标记而不是错误码：前端请求层在非 0 code 时会抛错并**丢掉 data**，
> 用错误码就拿不到新报价。该分支**不创建订单**；客户确认后把 `expectedAmount`
> 换成 `quote.payable` 再提交。

**其他错误**：文件数量 / 格式 / 域名不合规（`CUSTOMIZATION_FILE_INVALID`）；
60 秒内重复提交（直接返回已建订单，不报错，见 R94）；地址不属于当前用户（沿用既有校验）。

---

## 4. 订单详情里的定制区块

既有的 `GET /app-api/trade/order/get?id=123` 在 `customization = true` 的订单上增加字段
（普通订单不返回该字段，行为逐字不变）：

```jsonc
{
  "id": 123, "customization": true,
  "customizationDetail": {
    "materialName": "可移除铜版",
    "width": 50, "height": 100, "quantity": 1000,
    "unitArea": 0.0050, "totalArea": 5.0000,
    "mode": 2, "form": 2, "sheetSpec": "A4", "finishing": 1, "tearLine": false, "tearLine": false,
    "rowCount": 1, "columnCount": 1, "rowGap": 3, "columnGap": 3,
    "axisDiameter": "25.4mm",
    "dieCut": false, "urgent": false,
    "remark": "请按附件颜色印刷",
    "breakdown": { "print": 34100, "printing": 0, "dieCut": 0, "urgent": 0,
                   "subtotal": 34100, "total": 34100 },
    "files": [ { "name": "artwork.pdf", "url": "https://…/artwork.pdf", "size": 1048576 } ]
  }
}
```

> 客户可以重新下载自己上传的文件（与后台同一个 URL）。

---

## 5. 文件上传

沿用既有 `POST /app-api/infra/file/upload`（`multipart/form-data`，字段名 `file`），
返回字符串 URL。定制页不新增上传接口。

**服务端在提交订单时会再校验**：数量 1~5、扩展名 ∈ {pdf, ai, cdr, jpg, jpeg, png}、
URL 以 `yudao.customization.file-domain` 开头（默认 `https://img.yuwangchenfa.com`）。
单文件上限 **16MB**（Q106=A），与上传接口的 multipart 上限一致。
