# app-api 契约（021 客户服务）

所有路径以 `/app-api` 前缀，**只走门面站个人中心**（小程序/App 本期不新增页面）。
全部接口要求会员登录；数据按 `member_id` 隔离。

---

## 1. 投诉咨询

### `POST /trade/customer-service/ticket/create`

```jsonc
{ "type": 1,                       // 1 投诉 / 2 咨询
  "content": "打印时标签总是偏移，已换纸仍出现",
  "contactMobile": "13800008888",  // 必填，11 位手机号
  "images": ["https://img.yuwangchenfa.com/20261117/a.jpg"] // 可选，≤ 5 张
}
```

```jsonc
{ "code": 0, "data": { "id": 501 } }
```

**规则**

| 规则 | 依据 |
|---|---|
| 内容 1～1000 字；联系手机号必填且为 11 位手机号 | FR-561 |
| 图片 ≤ 5 张、JPG/PNG/WEBP、单张 ≤ 10 MB、域名合法 | FR-580 |
| 同一会员 60 秒内相同类型 + 相同内容只生成一条 | FR-562 |
| 初始状态 `0 待处理`；记录来源=客户自助 | FR-562 |

### `GET /trade/customer-service/ticket/page`

参数：`pageNo` / `pageSize`。只返回当前会员的记录，按 `create_time` 倒序。

```jsonc
{ "code": 0, "data": { "total": 2, "list": [
  { "id": 501, "type": 1, "typeName": "投诉", "content": "…",
    "status": 0, "statusName": "待处理",
    "handleRemark": null, "handleTime": null,
    "images": ["…"], "createTime": 1794900000000 }
] } }
```

### `GET /trade/customer-service/ticket/get?id=501`

返回单条详情；`status=10` 时 `handleRemark` 必不为空（客户可见，Q1）。
非本人记录返回「工单不存在」。

---

## 2. 在线维修

### `POST /trade/customer-service/repair/create`

```jsonc
{ "deviceModel": "Zebra ZT411",
  "faultDesc": "开机后走纸不停，伴随异响",
  "contactMobile": "13800008888",
  "receiverName": "张三",
  "receiverMobile": "13800008888",
  "receiverArea": "江苏省 南京市 鼓楼区",
  "receiverDetail": "xx 路 1 号",
  "images": ["…"] }
```

```jsonc
{ "code": 0, "data": { "id": 601 } }
```

**规则**

| 规则 | 依据 |
|---|---|
| 一份申请只对应一台设备（多台分别提交） | FR-565 / Q2 |
| 设备型号 ≤ 100 字；故障描述 1～1000 字 | FR-565 |
| 收件信息为**客户返修收件地址**，可从地址簿带入后快照 | FR-565 |
| 初始状态 `0 待联系`；记录来源=客户自助 | FR-566 |

### `GET /trade/customer-service/repair/page`

参数：`pageNo` / `pageSize`；只返回本人记录，按 `create_time` 倒序。

```jsonc
{ "code": 0, "data": { "total": 1, "list": [
  { "id": 601, "deviceModel": "Zebra ZT411", "faultDesc": "…",
    "status": 20, "statusName": "已开单",
    "repairOrderId": 701, "repairOrderPayStatus": 0,
    "shipCompany": null, "shipNo": null,
    "createTime": 1794900000000 }
] } }
```

### `GET /trade/customer-service/repair/get?id=601`

返回维修申请 + 维修订单 + 回收地址 + 客户可见的流转记录。

```jsonc
{ "code": 0, "data": {
  "id": 601, "deviceModel": "…", "faultDesc": "…",
  "contactMobile": "138…", "receiverName": "张三",
  "receiverMobile": "138…", "receiverArea": "…", "receiverDetail": "…",
  "images": ["…"], "status": 30, "statusName": "已寄出",
  "contactTime": 1794900000000, "contactResult": "已电话确认故障",
  "shipCompany": "顺丰", "shipNo": "SF1234567890", "shipTime": 1794900000000,
  "repairOrder": {
    "id": 701, "repairFee": 10000, "returnFreight": 2000,
    "payableAmount": 12000, "feeRemark": "更换打印头，含寄回运费",
    "payStatus": 20, "payStatusName": "已支付",
    "payChannelCode": "wx_lite", "payTime": 1794900000000
  },
  "returnAddress": { "receiverName": "…", "receiverMobile": "…",
    "areaName": "…", "detailAddress": "…", "shipRemark": "…" },
  "logs": [{ "action": "ORDER_CREATE", "remark": "已开单", "createTime": 1794900000000 }]
} }
```

**规则**

- 只能访问本人的维修申请（FR-560）。
- `repairOrder` 未开单时为 `null`。
- `returnAddress` 未配置时为 `null`，前端显示「请联系客服获取邮寄地址」（FR-574）。
- 未支付且应付 > 0 时，前端提示「支付后再寄件」。

### `POST /trade/customer-service/repair/ship`

```jsonc
{ "id": 601, "shipCompany": "顺丰", "shipNo": "SF1234567890" }
```

**规则**：申请状态必须是 `20 已开单`；维修单 `pay_status ∈ {20 已支付, 30 无需支付}`；
`shipNo` 必填；标记后状态 `30 已寄出` 并记录时间（FR-584）。客服推进到「维修中」前可重复调用修改。

---

## 3. 维修订单支付

### `POST /trade/customer-service/repair-order/pay-online`

```jsonc
{ "id": 701 }
```

```jsonc
{ "code": 0, "data": { "payOrderId": 99001 } }
```

**规则**

- 只有 `pay_status = 0 待支付` 可发起；`30 无需支付` / `20 已支付` 拒绝。
- 首次调用用 pay 模块建支付单，`merchantOrderId = "repair:701"`，`subject = "维修订单 #701"`；
  重复调用复用已建的 `payOrderId`。
- 前端拿到 `payOrderId` 后调用既有 `POST /pay/order/submit` 选择渠道（微信 / 支付宝）。

### `POST /trade/customer-service/repair-order/pay-offline`

```jsonc
{ "id": 701 }
```

```jsonc
{ "code": 0, "data": {
  "payStatus": 10,
  "offline": { "accountName": "…", "accountNo": "…", "bankName": "…", "tip": "…" }
} }
```

**规则**：仅当 `trade_config.offline_pay_enabled = true`；把支付状态置为 `10 待确认收款`，
返回收款户名/账号/开户行/提示（读取 `trade_config` 时按 null 兜底）。
`pay_status` 已经是 `10 待确认收款` 时**幂等返回同一份收款信息**（客户关闭弹层后可再次查看）；
已支付 / 已取消 / 无需支付仍拒绝。

### 支付回调（内部，不经前端）

pay 模块回调既有 `/app-api/trade/order/update-paid`，`merchantOrderId` 为 `repair:<id>` 时
由客户服务 service 处理：把维修单置为 `20 已支付`，记录支付单号、渠道与时间；
重复回调幂等（FR-572）。

---

## 4. 回收邮寄地址

### `GET /trade/customer-service/return-address`

返回当前租户的回收地址；未配置时 `data = null`，前端展示求助提示（FR-574）。
也可以从维修详情里的 `returnAddress` 读取，本接口用于独立的地址展示场景。

---

## 5. 附件上传

复用既有上传接口（`POST /infra/file/upload`），不新增文件存储。
服务端在创建工单时再次校验扩展名、大小与 URL 域名（FR-580）。

---

## 6. 错误码

见 [data-model.md](../data-model.md) §7。客户端按 `code` 展示 `msg`，不得自行猜测状态。
