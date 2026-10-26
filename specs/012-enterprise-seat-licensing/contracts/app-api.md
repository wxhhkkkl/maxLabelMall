# C 端 / 桌面端接口契约（012 企业客户与席位授权）

> 前缀：**只走 `/app-api`**（宪法）。除特别说明外都要求登录。
> 统一响应：`{ "code": 0, "data": …, "msg": "" }`；分页 `data = { list, total }`。
> 公共请求头沿用 009：`tenant-id`（生产 162）、`terminal`（桌面端 31、门面站 20）、`Authorization: Bearer …`。

---

## 0. 角色与权限

| 角色 | 判定 | 能做什么 |
|---|---|---|
| 企业 OWNER | `maxlabel_org_member.role=1` 且企业启用 | 成员增删、角色调整、OWNER 转交、席位分配/撤销、购买企业授权 |
| 企业 ADMIN | `role=2` 且企业启用 | 成员增删、席位分配/撤销、购买企业授权 |
| 企业 MEMBER | `role=3` 且企业启用 | 查看企业与自己席位、主动退出 |
| 非企业会员 | 无成员关系 | 看不到企业中心与企业商品 |

服务端 MUST 每次校验角色，**不能**只依赖前端隐藏入口。

---

## 1. 当前权益（既有接口，仅加字段）

### 1.1 `GET /member/subscription/get`

```jsonc
// 只有个人订阅（行为与今天一致）
{ "code": 0, "data": {
  "memberId": 1001, "active": true,
  "startDate": "2026-10-26", "endDate": "2027-10-26",
  "startTime": "2026-10-26T00:00:00+08:00",
  "endTime": "2027-10-27T00:00:00+08:00",
  "remainingDays": 365, "periodMonths": 12,
  "sourceOrderNo": "o202610260001",
  "sourceType": 1, "orgId": null, "orgName": null
}, "msg": "" }

// 只有企业席位
{ "code": 0, "data": {
  "active": true,
  "endDate": "2027-06-30",
  "sourceType": 2,
  "orgId": 88, "orgName": "南京路桥机械有限公司",
  "sourceOrderNo": null, "periodMonths": null
}, "msg": "" }

// 两者并存，企业更晚 → sourceType=2；并列/个人更晚 → sourceType=1
```

| 字段 | 说明 |
|---|---|
| `active` | `max(个人 end_time, 企业未过期且未退款批次 end_time) > NOW()` |
| `endDate` | 较晚来源的最后有效自然日；到期日当天全天有效 |
| `sourceType` | `1` 个人订阅 / `2` 企业席位；表示**决定 `endDate` 的来源** |
| `orgId` / `orgName` | 会员有有效企业席位时返回；否则为 `null` |
| 其余字段 | 语义不变；企业来源下 `periodMonths/sourceOrderNo` 可能为 `null` |

> ⚠️ 既有客户端把 `sourceType` 当未知字段忽略即可；**不允许**把新增字段设为必填。

---

## 2. 企业中心（member-server）

### 2.1 `GET /member/org/get` —— 我的企业

```jsonc
{ "code": 0, "data": {
  "orgId": 88, "orgName": "南京路桥机械有限公司",
  "role": 1, "status": 0,
  "seatActive": true, "seatEndDate": "2027-06-30",
  "memberCount": 5,
  "seatTotal": 20, "seatUsed": 3, "seatAvailable": 17,
  "licenseEndDate": "2027-06-30"
}, "msg": "" }
```

- 无企业：`data = { "orgId": null, "role": null, ... }`，不抛错（前端据此显示“未加入企业”）。
- `seatAvailable` 由服务端计算（R64），前端不得自行相减。
- `licenseEndDate` = 企业所有**未过期且未退款**批次的最大到期日（展示用）。

### 2.2 `GET /member/org/member/page` —— 企业成员与席位

OWNER/ADMIN 可访问。

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNo` / `pageSize` | 是 | 分页 |
| `keyword` | 否 | 会员昵称 / 手机号后四位 |

```jsonc
{ "code": 0, "data": { "list": [{
  "memberId": 1002, "nickname": "张三", "mobileMasked": "138****8000",
  "role": 3, "seatActive": true, "seatEndDate": "2027-06-30",
  "joinedTime": "2026-10-26T10:00:00+08:00"
}], "total": 5 }, "msg": "" }
```

> ⚠️ 只返回会员标识、角色、席位状态；**不得**返回模板、打印记录或其内容（FR-319）。

### 2.3 `POST /member/org/member/add` —— 按手机号加入成员

OWNER/ADMIN 可操作；**加入立即生效，无待确认状态**（Q70）。

```jsonc
{ "mobile": "13800138000" }
```

| 错误 | 说明 |
|---|---|
| `MEMBER_ORG_MOBILE_NOT_REGISTERED` | 手机号未注册 → “请对方先注册” |
| `MEMBER_ORG_MEMBER_IN_OTHER` | 已在另一个企业 → “请先退出原企业” |
| `MEMBER_ORG_MEMBER_ALREADY` | 已在本企业 → 幂等成功或明确提示 |

### 2.4 `POST /member/org/member/remove` —— 移出成员

```jsonc
{ "memberId": 1002 }
```

- OWNER/ADMIN 可操作；**不能移出 OWNER**（`MEMBER_ORG_OWNER_REQUIRED`）。
- 同事务释放其席位；模板/打印记录保留（FR-308）。

### 2.5 `PUT /member/org/member/update-role` —— 调整 ADMIN

仅 OWNER；只能设/取消 `ADMIN(2)` 与 `MEMBER(3)`，**不能**通过此接口设 OWNER（用 2.6）。

```jsonc
{ "memberId": 1002, "role": 2 }
```

### 2.6 `POST /member/org/owner/transfer` —— 转交 OWNER

仅 OWNER；目标必须是本企业成员。

```jsonc
{ "memberId": 1002 }
```

- 原子完成：目标 `role=OWNER`、原 OWNER `role=ADMIN`、`maxlabel_org.owner_member_id=目标`。
- 并发转交最多一次成功（SC-120）。

### 2.7 `POST /member/org/seat/assign` / `revoke` —— 分配 / 撤销席位

OWNER/ADMIN 可操作。

```jsonc
{ "memberId": 1002 }
```

| 错误 | 说明 |
|---|---|
| `MEMBER_ORG_SEAT_FULL` | 可用容量为 0 → 提示剩余席位 |
| `MEMBER_ORG_SEAT_ALREADY` | 已有有效席位 → 幂等或明确提示 |
| `MEMBER_ORG_NOT_MEMBER` | 目标不是本企业成员 |
| `MEMBER_ORG_SEAT_NOT_EXISTS` | 撤销一个不存在的席位 |

- 撤销后立即失去企业来源权益；个人订阅不受影响（FR-304）。
- 分配成功返回最新的 `seatUsed/seatAvailable`（可选）。

### 2.8 `GET /member/org/license/page` —— 企业授权批次

OWNER/ADMIN 可访问。

```jsonc
{ "code": 0, "data": { "list": [{
  "id": 1, "periodMonths": 12, "seatsTotal": 20,
  "startDate": "2026-10-26", "endDate": "2027-10-26",
  "remainingDays": 365, "sourceOrderNo": "o202610260001",
  "refunded": false, "folded": false, "active": true
}], "total": 2 }, "msg": "" }
```

| 字段 | 规则 |
|---|---|
| `active` | `end_time > NOW()` **且** `refunded=false` **且** `folded=false`；退款批次显示「已退款」，折算批次显示「已折算」 |
| `refunded` | 退款批次只留痕：不计入 `licenseEndDate`、席位容量与续费起点 |
| `folded` | **折算批次**（Q78）：剩余时长已并入后续批次，只留痕，不计入 `licenseEndDate`、席位容量与续费起点；前端 MUST NOT 再给「续费」入口 |

> 已分配席位不因批次退款或折算而自动撤销，由运营人工处理（Q76）。
> 复购后的新批次 `startDate` 恒为购买日，`endDate` 体现折算后的时长（Q78）。

### 2.9 `POST /member/org/leave` —— 主动退出企业

- MEMBER/ADMIN 可退出；**OWNER 必须先转交**（`MEMBER_ORG_OWNER_CANNOT_LEAVE`）。
- 同事务释放席位；个人订阅与历史数据不受影响。

---

## 3. 企业商品（product-server）

### 3.1 `GET /product/subscription-plan/enterprise/list`

需登录，且调用者是启用企业的 OWNER/ADMIN；否则 `ENTERPRISE_PLAN_FORBIDDEN`。

```jsonc
{ "code": 0, "data": [{
  "spuId": 501, "skuId": 601, "name": "MaxLabel 企业授权 · 每席位",
  "periodMonths": 12, "price": 99000, "picUrl": "https://…",
  "stock": 9999, "sort": 0
}], "msg": "" }
```

### 3.2 `GET /product/subscription-plan/enterprise/get?skuId=601`

同上权限；返回企业商品详情（含 `description`、`deliveryType=3`），供企业中心详情页使用。

### 3.3 公开商城接口的行为

| 接口 | 行为 |
|---|---|
| `GET /product/spu/page`（列表 / 搜索） | **排除** `enterprise_only=1` 的 SPU |
| `GET /product/spu/get-detail?id=` | 命中企业商品 → `SPU_ENTERPRISE_ONLY`（“该商品仅对企业客户开放”） |
| `GET /product/spu/list-by-ids` | **按调用者过滤**：未登录 / 非企业 OWNER、ADMIN → 排除 `enterprise_only=1`；企业 OWNER、ADMIN → 保留（购物车/订单渲染需要） |
| `GET /product/filter/*`（筛选维度计数） | 排除企业商品 |
| `POST /product/favorite/create` | 非企业 OWNER、ADMIN 收藏 `enterprise_only=1` → `SPU_ENTERPRISE_ONLY` |
| `GET /product/favorite/page` | 非企业 OWNER、ADMIN → 过滤企业商品（处理历史收藏残留） |
| `GET /product/browse-history/page` | 非企业 OWNER、ADMIN → 过滤企业商品（处理历史浏览残留） |
| `GET /product/subscription-plan/list` | **只返回个人计划**（`scope=1`），保持桌面端/门面站现有行为 |

> `ProductSpuApi#getSpuList`（RPC）**不**加过滤：trade 校验订单项、促销活动内部读取都走它，
> 过滤会连带影响企业订单。过滤只做在 C 端公开控制器（`AppProductSpuController#getSpuList`、
> 收藏与浏览记录控制器）。

---

## 4. 下单与发票

### 4.1 购物车 / 结算 / 收藏 / 浏览记录

企业商品（`enterprise_only=1`）只允许企业 OWNER/ADMIN 走购买链路；其他入口不得泄露名称或价格。

| 接口 | 行为 |
|---|---|
| `GET /trade/order/settlement-product` | 公开接口，**一律排除**企业商品（企业管理员从企业计划接口取价） |
| `POST /trade/cart/add` / `PUT /trade/cart/update-count` | 非企业 OWNER/ADMIN → `ENTERPRISE_PRODUCT_NOT_AVAILABLE` |
| `GET /trade/cart/list` | 非企业 OWNER/ADMIN → 过滤企业商品（处理历史购物车残留） |
| `GET /trade/order/settlement` | 非企业 OWNER/ADMIN 且结算项含企业商品 → `ENTERPRISE_PRODUCT_NOT_AVAILABLE` |
| `POST /product/favorite/create` | 非企业 OWNER/ADMIN 收藏企业商品 → `SPU_ENTERPRISE_ONLY` |
| `GET /product/favorite/page` | 非企业 OWNER/ADMIN → 过滤企业商品 |
| `GET /product/browse-history/page` | 非企业 OWNER/ADMIN → 过滤企业商品 |

> `ProductSpuApi#getSpuList`（RPC）不在此列：管理端与订单内部仍可读取企业商品。
> 企业管理员在这些入口中保留企业商品（购物车/结算需要），但公开价格展示接口
> `settlement-product` 对所有人都不返回企业商品。

### 4.2 `POST /trade/order/create` —— 行为变化，无新字段

订单项中**任一**命中 `scope=2` 的计划时：

1. 买家 MUST 是启用企业的 OWNER/ADMIN；否则
   `ENTERPRISE_ORDER_BUYER_NOT_ADMIN`。
2. 请求 **MAY 不带 `invoice`**（不开发票）；若带，`invoice.titleKind` MUST 为 `1`（企业）；
   传个人抬头时返回 `ENTERPRISE_ORDER_INVOICE_TITLE_REQUIRED`。
3. 抬头字段仍由 `InvoiceValidator` 校验（企业抬头必须有税号）。
4. 订单落库时写入 `trade_order.maxlabel_org_id`（企业快照）。
5. 结算页对企业订单 MUST **直接隐藏个人抬头选项**；企业商品页进入结算时带 `enterprise=1`。

> 混合订单（个人商品 + 企业商品）若开票，同样整单企业抬头；买家要给个人商品开个人票时应拆单。
> 纯个人订单的行为**逐字不变**。

### 4.3 支付成功

- 无需客户端额外调用；服务端按 `plan.scope` 自动发放个人订阅或企业授权批次。
- **纯虚拟的企业授权订单支付成功后直接为「已完成」**，不进入发货流程；
  含实物商品的订单仍为「待发货」（Q77）。
- 权益在下一次 `GET /member/subscription/get` 刷新时可见（SC-106）。

### 4.4 企业授权订单不支持用户端退款（Q79）

| 接口 | 行为 |
|---|---|
| `GET /trade/order/get-detail` | 响应新增 `enterprise: boolean`（`trade_order.maxlabel_org_id` 非空），前端据此隐藏「申请退款」入口 |
| `POST /trade/after-sale/create` | 命中企业授权订单 → `ENTERPRISE_ORDER_REFUND_NOT_SUPPORTED`（“企业授权订单不支持退款，如有特殊情况请联系平台处理”） |

> **运营后台的人工退款通道保留**：后台售后退款不走 `createAfterSale`，仍可处理特殊情况，
> 退款后按 Q76 给批次打「已退款」标记。
> 未支付订单的取消不受影响（没有资金往来）。

---

## 5. 桌面端（`../maxlabel`）

| 变化 | 位置 |
|---|---|
| `MallMember` 增加 `subscriptionSource?: 'personal' \| 'enterprise'`、`orgName?: string` | `app/src/shared/ipcContract.ts` |
| 解析 `sourceType/orgName` | `app/src/main/mallRepository.ts` |
| 账户弹层显示“企业授权（XX 公司）” | `app/src/renderer/src/dialogs/MallAccountDialog.tsx` |
| **不改**权益判定 | `entitlementService` 仍只看 `active/endTime`（Q69） |

---

## 6. 错误码（按模块既有分段分配）

| 模块 | 名称 | 语义 |
|---|---|---|
| member | `MEMBER_ORG_NOT_EXISTS` | 企业不存在 |
| member | `MEMBER_ORG_DISABLED` | 企业已停用 |
| member | `MEMBER_ORG_NOT_ADMIN` | 不是企业管理员 |
| member | `MEMBER_ORG_MOBILE_NOT_REGISTERED` | 手机号未注册 |
| member | `MEMBER_ORG_MEMBER_IN_OTHER` | 已在其他企业 |
| member | `MEMBER_ORG_MEMBER_ALREADY` | 已在本企业 |
| member | `MEMBER_ORG_MEMBER_NOT_FOUND` | 不是本企业成员 |
| member | `MEMBER_ORG_OWNER_REQUIRED` | 企业必须保留 OWNER |
| member | `MEMBER_ORG_OWNER_CANNOT_LEAVE` | OWNER 不能直接退出 |
| member | `MEMBER_ORG_SEAT_FULL` | 席位已用完 |
| member | `MEMBER_ORG_SEAT_ALREADY` | 已有有效席位 |
| member | `MEMBER_ORG_SEAT_NOT_EXISTS` | 席位不存在 |
| member | `MEMBER_ORG_RENEWAL_SEATS_TOO_FEW` | 购买席位数少于当前有效席位数 |
| product | `ENTERPRISE_PLAN_FORBIDDEN` | 非企业管理员不能购买 |
| product | `SPU_ENTERPRISE_ONLY` | 企业商品不在公开商城 |
| trade | `ENTERPRISE_ORDER_BUYER_NOT_ADMIN` | 企业订单买家不是管理员 |
| trade | `ENTERPRISE_ORDER_INVOICE_TITLE_REQUIRED` | 企业订单必须用企业抬头 |
| trade | `ENTERPRISE_PRODUCT_NOT_AVAILABLE` | 企业商品只能由企业管理员在企业中心购买（购物车/结算拒绝） |
| trade | `ENTERPRISE_ORDER_REFUND_NOT_SUPPORTED` | 企业授权订单用户端不支持退款（Q79） |
