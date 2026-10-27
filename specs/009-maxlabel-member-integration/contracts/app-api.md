# C 端 / 桌面端接口契约（009 MaxLabel 会员体系）

> 前缀：**只走 `/app-api`**（宪法）。除特别说明外都要求登录。
> 统一响应：`{ "code": 0, "data": …, "msg": "" }`；分页 `data = { list, total }`。

## 0. 公共请求头

| Header | 值 | 说明 |
|---|---|---|
| `tenant-id` | 生产 `162` | **必带**；缺失返回 HTTP 200 + `code=400` |
| `terminal` | `31`（App） | 桌面端固定 31；门面站既有值为 20 |
| `Authorization` | `Bearer <accessToken>` | 登录后所有接口 |
| `Content-Type` | `application/json` | 模板接口使用 JSON，不走 multipart |

**登录本身复用既有接口**（不新增）：

- `POST /member/auth/sms-login` `{mobile, code}`
- `POST /member/auth/login` `{mobile, password}`
- `POST /member/auth/send-sms-code` `{mobile, scene:1, captchaVerification?}`
- `POST /member/auth/wechat/scan/create` → `GET /member/auth/wechat/scan/query` →
  `POST /member/auth/wechat/callback` → `POST /member/auth/wechat/complete`（`abandon` 可选）
- `POST /member/auth/refresh-token?refreshToken=…`
- `GET /member/user/get`

响应统一为 `{ userId, accessToken, refreshToken, expiresTime }`（微信补全流程除外）。

---

## 1. 订阅

### 1.1 `GET /member/subscription/get` —— 当前订阅

```jsonc
// 有有效订阅
{ "code": 0, "data": {
  "active": true,
  "memberId": 1001,
  "startDate": "2026-10-20",                  // 当前段开始自然日
  "endDate":   "2027-10-20",                  // 当前段最后有效自然日（含当日）
  "startTime": "2026-10-20T00:00:00+08:00",
  "endTime":   "2027-10-21T00:00:00+08:00",   // 兜底：所有段的最大 end_time（次日 00:00）
  "remainingDays": 365,
  "periodMonths": 12,
  "sourceOrderNo": "o202610200001"
}, "msg": "" }

// 从未订阅 / 已过期
{ "code": 0, "data": {
  "active": false, "memberId": 1001,
  "startDate": null, "endDate": "2026-09-01",
  "startTime": null, "endTime": "2026-09-02T00:00:00+08:00",
  "remainingDays": 0, "periodMonths": null, "sourceOrderNo": null
}, "msg": "" }
```

| 规则 | 说明 |
|---|---|
| `active` | `MAX(end_time) > NOW()`（Asia/Shanghai）；`end_time` 为最后有效自然日的**次日 00:00（不含当日）** |
| `endDate` | 展示用，`yyyy-MM-dd`，= `endTime.minusDays(1)`；到期日**当天全天有效**，次日 00:00 起过期 |
| `endTime` | 无订阅时为 `null`；已过期时为**最后一段**的次日 00:00 |
| `remainingDays` | `max(0, DAYS.between(today, endDate))`，**不含今天**；年卡示例 365；到期日当天为 0，前端显示「今天到期」 |
| 只读 | 不提供 C 端修改订阅的接口 |

### 1.2 `GET /product/subscription-plan/list` —— 可售周期（**匿名可访问**）

```jsonc
{ "code": 0, "data": [
  { "spuId": 300, "skuId": 301, "name": "MaxLabel 会员授权（月）", "edition": "professional",
    "periodMonths": 1, "price": 2900, "picUrl": "…", "stock": 999, "sort": 1 },
  { "spuId": 300, "skuId": 302, "name": "MaxLabel 会员授权（年）", "edition": "professional",
    "periodMonths": 12, "price": 29900, "picUrl": "…", "stock": 999, "sort": 2 }
], "msg": "" }
```

- 价格单位：**分**（与 yudao 商品一致）；
- `edition`：该周期授予的桌面端版本（`standard` / `professional` / `enterprise`，Q80）；
- 只返回 `enabled=1` 的计划；商品下架/售罄时 `stock` 反映实时值；
- 桌面端与门面站都只做展示，不自行算价。

---

## 2. 私有云端模板

> 所有接口只允许**本人**；写操作（create/update/delete/submit-share）额外要求**当前订阅有效**。

### 2.1 `GET /member/cloud-template/page`

Query：`pageNo`（≥1）、`pageSize`（1–200）、`keyword?`（名称/关键词模糊）。

```jsonc
{ "code": 0, "data": { "total": 2, "list": [
  { "id": 9001, "name": "顺丰面单 100×150", "sizeBytes": 48211,
    "widthMm": 100.0, "heightMm": 150.0, "category": "物流",
    "keywords": "顺丰,面单", "shareStatus": 2,
    "thumb": "data:image/svg+xml;base64,PHN2Zy…",
    "createTime": "2026-10-20T10:00:00+08:00", "updateTime": "2026-10-20T10:00:00+08:00" }
] }, "msg": "" }
```

`shareStatus`：`null` 未共享 / `0` 待审核 / `1` 已公开 / `2` 已驳回 / `3` 已下架。
`thumb`：后端从 `.msdx` 正文即时生成的 SVG data URL 示意图（2026-10-27 新增）。

### 2.2 `GET /member/cloud-template/get?id=`

```jsonc
{ "code": 0, "data": {
  "id": 9001, "name": "顺丰面单 100×150",
  "content": "{\"format\":\"maxlabel-msdx\",\"version\":2,…}",
  "sizeBytes": 48211, "widthMm": 100.0, "heightMm": 150.0,
  "category": "物流", "keywords": "顺丰,面单", "shareStatus": 2,
  "thumb": "data:image/svg+xml;base64,PHN2Zy…",
  "createTime": "…", "updateTime": "…"
}, "msg": "" }
```

- 非本人：返回 `MEMBER_TEMPLATE_NOT_FOUND`（模板不存在或无权限），**不区分**"不存在"与"非本人"。

### 2.3 `POST /member/cloud-template/create`

```jsonc
{ "name": "顺丰面单 100×150",
  "content": "{\"format\":\"maxlabel-msdx\",…}",
  "widthMm": 100.0, "heightMm": 150.0,
  "category": "物流", "keywords": "顺丰,面单" }
```

| 校验 | 规则 |
|---|---|
| 登录 | 必须 |
| 订阅 | 必须有效（403 `MEMBER_SUBSCRIPTION_REQUIRED`） |
| `name` | 1–255 字符 |
| `content` | 合法 JSON，≤ **16 MB**；服务端计算 `sizeBytes`/`contentHash` |
| `widthMm`/`heightMm` | ≥ 0，≤ 10000 |
| 配额 | 数量 ≤ 1000 且总量 + 新正文 ≤ 256 MB |
| 清洗 | 服务端删除 `connections[*].password` 与 RFID `accessPwd/killPwd` 后再存 |

响应：`{ "code": 0, "data": { "id": 9001 }, "msg": "" }`

**同名**：服务端不阻止同名；覆盖由桌面端调用 `update` 完成。

### 2.4 `PUT /member/cloud-template/update`

```jsonc
{ "id": 9001, "name": "顺丰面单（新）",
  "content": "…",              // 可选；传了才替换正文并重算 size/hash
  "widthMm": 100.0, "heightMm": 150.0,
  "category": "物流", "keywords": "顺丰,面单" }
```

- 归属与订阅校验同 create；
- 若替换正文，配额按**差值**校验（先释放旧大小再加新大小）；
- 已共享的模板更新后：共享副本**保持不变**，需用户重新提交才会同步（明确行为）。

### 2.5 `DELETE /member/cloud-template/delete?id=`

- 删除私有模板；若存在共享副本，**一并删除/下架**（FR 边界）；
- 本地文件不受影响；
- **不要求有效订阅**：删除属于数据处置权（FR-219）。

### 2.6 `POST /member/cloud-template/submit-share`

```jsonc
{ "id": 9001, "category": "物流", "keywords": "顺丰,面单" }
```

```jsonc
{ "code": 0, "data": { "sharedTemplateId": 7001, "auditStatus": 0 }, "msg": "" }
```

| 规则 | 说明 |
|---|---|
| 订阅 | 必须有效 |
| 归属 | 必须是本人模板 |
| 清洗 | 服务端生成共享副本：删数据库口令、RFID 口令、`datasets[*].rows` |
| 重复提交 | 复用同一 `source_template_id`，状态改回待审核、清空驳回原因 |
| 审核 | 状态从 `0` 开始；**不能**直接公开 |

### 2.7 `DELETE /member/cloud-template/cancel-share?id=`

- `id` 为**私有模板 id**；删除其共享副本（软删）；
- 已下载的本地副本不受影响；
- **不要求有效订阅**：取消共享属于数据处置权（FR-219）。

---

## 3. 共享模板库

### 3.1 `GET /member/shared-template/page`

Query：`pageNo`、`pageSize`（1–200）、`keyword?`、`category?`、`widthMin?`、`widthMax?`、`heightMin?`、`heightMax?`。

```jsonc
{ "code": 0, "data": { "total": 12, "list": [
  { "id": 7001, "name": "标准物流面单", "widthMm": 100.0, "heightMm": 150.0,
    "category": "物流", "keywords": "面单,快递",
    "publisherNickname": "张**", "publishedTime": "2026-10-20T12:00:00+08:00",
    "thumb": "data:image/svg+xml;base64,PHN2Zy…" }
] }, "msg": "" }
```

- 只返回 `audit_status=1`（已通过）；
- 登录即可浏览（**不要求有效订阅**，Q54）；
- 发布者只展示脱敏昵称。

### 3.2 `GET /member/shared-template/get?id=`

```jsonc
{ "code": 0, "data": {
  "id": 7001, "name": "标准物流面单",
  "content": "{…清洗后的 msdx…}",
  "widthMm": 100.0, "heightMm": 150.0,
  "category": "物流", "keywords": "面单,快递",
  "publisherNickname": "张**", "publishedTime": "2026-10-20T12:00:00+08:00",
  "thumb": "data:image/svg+xml;base64,PHN2Zy…"
}, "msg": "" }
```

- 只返回已通过条目；**不返回**发布者会员 id、手机号、来源模板 id。

---

## 4. 打印记录上报

### 4.1 `POST /member/print-record/create`

```jsonc
{ "recordUid": "8f1c…-uuid",
  "deviceId": "2b7e…-uuid",
  "printTime": "2026-10-20T10:00:00+08:00",
  "templateTitle": "顺丰面单 100×150",
  "mode": "driver",
  "count": 10, "copies": 1,
  "physicalCount": 10, "sentCount": 10,
  "status": "completed",
  "printer": "Zebra ZT410" }
```

响应：`{ "code": 0, "data": { "id": 5001 }, "msg": "" }`

| 校验 | 规则 |
|---|---|
| 登录 | 必须；**不要求有效订阅**（Q54） |
| `recordUid`/`deviceId` | UUID 字符串（36 位） |
| `mode` | `driver` / `command` |
| `count`/`copies`/`physicalCount`/`sentCount` | 非负；上限与桌面端校验一致 |
| `status` | `completed/submitted/partial/failed/canceled/unknown` |
| 禁止字段 | `dataSnapshot`、模板正文、连接信息；出现即忽略（不落库） |
| 幂等 | 同 `(tenant, member, recordUid)` 重复提交返回已有 `id`，不报错 |
| 频率 | 单会员默认不限流；服务端按 IP/会员做既有网关限流兜底 |

---

## 5. 错误码（新增，按 member 模块既有分段）

| 名称 | 含义 | 触发 |
|---|---|---|
| `MEMBER_SUBSCRIPTION_REQUIRED` | 订阅已过期或未订阅 | 模板 create/update/submit-share |
| `MEMBER_TEMPLATE_NOT_FOUND` | 模板不存在或非本人 | get/update/delete/submit |
| `MEMBER_TEMPLATE_TOO_LARGE` | 单文件 > 16 MB | create/update |
| `MEMBER_TEMPLATE_QUOTA_EXCEEDED` | 数量/总量超限 | create/update |
| `MEMBER_TEMPLATE_INVALID` | JSON/字段非法 | create/update |
| `MEMBER_SHARED_TEMPLATE_NOT_FOUND` | 共享条目不存在/未通过 | get |
| `MEMBER_PRINT_RECORD_INVALID` | 字段非法 | create |

> 具体数字码在实现时按 `ErrorCodeConstants` 现有分段分配；契约只固定**名称与含义**。
