# 管理端接口契约（009 MaxLabel 会员体系）

> 前缀：**只走 `/admin-api`**（宪法）。统一响应 `{ code: 200, data, msg }`（管理端成功码为 200）。
> 分页 `data = { list, total }`；`pageSize` 上限 200。
> 所有接口都要求 `Authorization: Bearer <adminAccessToken>` 与 `tenant-id`。

---

## 1. 会员订阅（会员列表 / 会员详情页签）

### 1.1 `GET /admin-api/member/user/page`（既有接口扩展）

在既有会员分页响应中**批量**补充两个只读字段（不改变原有字段）：

```jsonc
{ "code": 200, "data": { "total": 100, "list": [
  { "id": 1001, "mobile": "138…", "nickname": "张三",
    "subscriptionActive": true,
    "subscriptionEndDate": "2027-10-20",
    "subscriptionEndTime": "2027-10-21T00:00:00+08:00" }
] }, "msg": "" }
```

| 规则 | 说明 |
|---|---|
| 无订阅 | `subscriptionActive=false`，`subscriptionEndDate/EndTime=null` |
| 已过期 | `subscriptionActive=false`，`subscriptionEndDate=最后一段到期日`，`subscriptionEndTime=次日 00:00` |
| `active` | `MAX(end_time) > NOW()`；到期日**当天全天有效**，次日 00:00 起过期 |
| 查询方式 | 按本页 `userIds` **一次批量**查 `member_subscription` 的 `MAX(end_time)`，禁止 N+1 |
| 权限 | 沿用 `member:user:query`；**列表中的订阅字段也随该权限返回**，不再单独用 `member:subscription:query` 隐藏（避免"接口给了、前端藏了"的不一致） |

### 1.2 `GET /admin-api/member/subscription/page`

Query：`userId`（必填）、`pageNo`、`pageSize`。

```jsonc
{ "code": 200, "data": { "total": 3, "list": [
  { "id": 501, "memberId": 1001, "periodMonths": 12,
    "startDate": "2026-10-20", "endDate": "2027-10-20",
    "startTime": "2026-10-20T00:00:00+08:00",
    "endTime":   "2027-10-21T00:00:00+08:00",
    "active": true, "remainingDays": 365,
    "sourceOrderId": 8801, "sourceOrderNo": "o202610200001",
    "refunded": false, "createTime": "2026-10-20T10:00:00+08:00" }
] }, "msg": "" }
```

| 规则 | 说明 |
|---|---|
| 排序 | `end_time DESC`（最新一段在前） |
| `active` | `end_time > NOW()`（Asia/Shanghai）；到期日当天全天有效，次日 00:00 起过期 |
| `endDate` / `remainingDays` | `endDate` 为最后有效自然日（= `endTime.minusDays(1)`）；`remainingDays = max(0, DAYS.between(today, endDate))`，不含今天；到期日当天为 0，前端显示「今天到期」 |
| `refunded` | **整单或会员订单项**全额退款完成时由 trade 处理器标记；后台只展示，不自动回收 |
| 权限 | `member:subscription:query` |

---

## 2. 会员授权商品（独立运营页）

### 2.1 `GET /admin-api/product/subscription-plan/page`

Query：`pageNo`、`pageSize`、`spuId?`、`enabled?`。

```jsonc
{ "code": 200, "data": { "total": 2, "list": [
  { "id": 11, "spuId": 300, "skuId": 301,
    "spuName": "MaxLabel 会员授权", "skuName": "月卡",
    "price": 2900, "periodMonths": 1, "enabled": true, "sort": 1,
    "createTime": "2026-10-20T09:00:00+08:00" }
] }, "msg": "" }
```

### 2.2 `POST /admin-api/product/subscription-plan/create`

```jsonc
{ "spuId": 300, "skuId": 301, "periodMonths": 1, "enabled": true, "sort": 1 }
```

| 校验 | 规则 |
|---|---|
| SKU 归属 | `sku.spuId == spuId` |
| 配送方式 | SPU 的 `deliveryTypes` **只含 `VIRTUAL(3)`**（不含 EXPRESS/PICK_UP），否则 `MEMBER_SUBSCRIPTION_PLAN_NOT_VIRTUAL` |
| 单一会员 SPU | 若已存在其他 `spuId` 的启用计划 → `MEMBER_SUBSCRIPTION_PLAN_MULTI_SPU`（Q51） |
| SKU 价格 | **> 0**（0 元订单不会自动开通，R49） |
| 周期 | `1 ≤ periodMonths ≤ 120`；启用计划缺少 `1` 或 `12` 时管理页**提示**，不阻止保存（FR-223） |
| 唯一 | 同租户 `skuId` 只能有一条；重复返回明确错误 |
| 权限 | `product:subscription-plan:create` |

> 计划不做删除；**停用** = `PUT /update` 把 `enabled` 置 false。已发放订阅不受影响。

### 2.3 `PUT /admin-api/product/subscription-plan/update`

```jsonc
{ "id": 11, "periodMonths": 12, "enabled": true, "sort": 2 }
```

- **不允许改 `spuId/skuId`**；要换商品请停用旧计划再新建（避免历史订阅指向漂移）；
- 修改只影响**之后**的发放；已发放的 `member_subscription` 保留自己的周期快照；
- 权限：`product:subscription-plan:update`。

---

## 3. 会员上传的标签模板（会员详情页签 / 共享审核预览）

### 3.1 `GET /admin-api/member/cloud-template/page`

Query：`userId`（必填）、`pageNo`、`pageSize`、`keyword?`。

```jsonc
{ "code": 200, "data": { "total": 2, "list": [
  { "id": 9001, "memberId": 1001, "name": "顺丰面单 100×150",
    "sizeBytes": 48211, "widthMm": 100.0, "heightMm": 150.0,
    "category": "物流", "keywords": "顺丰,面单",
    "shareStatus": 1, "createTime": "…", "updateTime": "…" }
] }, "msg": "" }
```

- 只返回元数据；不含正文；
- `shareStatus`：`null/0/1/2/3`，与 C 端一致；
- 权限：`member:cloud-template:query`。

### 3.2 `GET /admin-api/member/cloud-template/get?id=`

```jsonc
{ "code": 200, "data": {
  "id": 9001, "memberId": 1001, "name": "顺丰面单 100×150",
  "content": "{…私有 msdx 原文…}", "sizeBytes": 48211,
  "widthMm": 100.0, "heightMm": 150.0, "category": "物流", "keywords": "顺丰,面单",
  "createTime": "…", "updateTime": "…"
}, "msg": "" }
```

- 用于后台**预览/下载**；返回的是**本人私有正文**（已剥离口令），不是共享清洗版；
- 权限：`member:cloud-template:query`；
- ⚠️ 该接口只在后台使用，**不得**在 C 端暴露。

---

## 4. 共享模板审核（独立运营页）

### 4.1 `GET /admin-api/member/shared-template/page`

Query：`auditStatus?`（0/1/2/3）、`keyword?`、`pageNo`、`pageSize`。

```jsonc
{ "code": 200, "data": { "total": 5, "list": [
  { "id": 7001, "sourceTemplateId": 9001,
    "publisherMemberId": 1001, "publisherNickname": "张三", "publisherMobile": "138****0000",
    "name": "标准物流面单", "sizeBytes": 40120,
    "widthMm": 100.0, "heightMm": 150.0,
    "category": "物流", "keywords": "面单,快递",
    "auditStatus": 0, "rejectReason": null,
    "auditUserId": null, "auditTime": null, "publishedTime": null,
    "createTime": "2026-10-20T12:00:00+08:00" }
] }, "msg": "" }
```

- 默认按 `create_time DESC`；可选按状态筛选；
- 权限：`member:shared-template:query`。

### 4.2 `GET /admin-api/member/shared-template/get?id=`

返回**清洗后的副本正文**（用于审核预览），字段同列表 + `content`。
权限：`member:shared-template:query`。

### 4.3 `PUT /admin-api/member/shared-template/approve`

```jsonc
{ "id": 7001 }
```

- 仅 `auditStatus=0` 可操作；置 `1`，写 `auditUserId/auditTime/publishedTime`；
- 重复通过：幂等返回成功，不覆盖首次审核人与时间；
- 权限：`member:shared-template:audit`。

### 4.4 `PUT /admin-api/member/shared-template/reject`

```jsonc
{ "id": 7001, "reason": "包含客户 logo，请脱敏后重新提交" }
```

- `reason` **必填**（1–255 字）；仅 `auditStatus=0` 可操作；置 `2`；
- 会员重新提交后状态回到 `0` 并清空原因；
- 权限：`member:shared-template:audit`。

### 4.5 `PUT /admin-api/member/shared-template/offline`

```jsonc
{ "id": 7001 }
```

- 仅 `auditStatus=1` 可操作；置 `3`；下架后 C 端列表**立即不再返回**；
- 已下载的本地副本不受影响；
- 权限：`member:shared-template:offline`。

---

## 5. 打印记录（会员详情页签）

### `GET /admin-api/member/print-record/page`

Query：`userId`（必填）、`startTime?`、`endTime?`（`yyyy-MM-dd HH:mm:ss`）、`pageNo`、`pageSize`。

```jsonc
{ "code": 200, "data": { "total": 128, "list": [
  { "id": 5001, "memberId": 1001,
    "deviceId": "2b7e…", "recordUid": "8f1c…",
    "printTime": "2026-10-20T10:00:00+08:00",
    "templateTitle": "顺丰面单 100×150",
    "mode": "driver", "count": 10, "copies": 1,
    "physicalCount": 10, "sentCount": 10,
    "status": "completed", "printer": "Zebra ZT410",
    "createTime": "2026-10-20T10:00:01+08:00" }
] }, "msg": "" }
```

| 规则 | 说明 |
|---|---|
| 排序 | `print_time DESC` |
| 索引 | `(tenant_id, member_id, print_time)` |
| 保留 | **永久**，不提供删除接口（Q53） |
| 权限 | `member:print-record:query` |

---

## 6. 权限点与菜单

| 权限 | 用途 | 菜单 |
|---|---|---|
| `member:subscription:query` | 订阅页签 | 无（随会员详情） |
| `member:cloud-template:query` | 模板页签 / 预览 | 无（随会员详情） |
| `member:print-record:query` | 打印记录页签 | 无（随会员详情） |
| `member:shared-template:query` | 共享审核列表/预览 | 页面「共享模板审核」 |
| `member:shared-template:audit` | 通过/驳回 | 按钮 |
| `member:shared-template:offline` | 下架 | 按钮 |
| `product:subscription-plan:query` | 计划列表 | 页面「会员授权商品」 |
| `product:subscription-plan:create` | 新增计划 | 按钮 |
| `product:subscription-plan:update` | 修改/停用 | 按钮 |

> ⚠️ 每个权限字符串都必须有一条 `system_menu.permission` 记录，否则按钮不会显示（超管同样）。
> 迁移 SQL 用 `INSERT … SELECT … WHERE NOT EXISTS` 幂等插入，并挂到「会员中心」父菜单下。

## 7. 管理端错误约定

| 场景 | 返回 |
|---|---|
| 权限不足 | 403（既有 `@PreAuthorize` 行为） |
| 状态不允许（如驳回已通过条目） | `MEMBER_SHARED_TEMPLATE_INVALID_STATUS`，`msg` 说明当前状态 |
| 计划冲突（同 SKU 重复） | `MEMBER_SUBSCRIPTION_PLAN_DUPLICATE` |
| 来源 SKU 价格 ≤ 0 | `MEMBER_SUBSCRIPTION_PLAN_PRICE_ZERO` |
| SPU 不是纯虚拟商品 | `MEMBER_SUBSCRIPTION_PLAN_NOT_VIRTUAL` |
| 已有其他启用会员 SPU | `MEMBER_SUBSCRIPTION_PLAN_MULTI_SPU` |
| 共享条目不存在 | `MEMBER_SHARED_TEMPLATE_NOT_FOUND` |

> 数字码在实现时按 member 模块 `ErrorCodeConstants` 既有分段分配；契约固定名称与含义。
