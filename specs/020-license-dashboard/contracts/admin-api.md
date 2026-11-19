# admin-api 契约（020 授权看板）

所有路径以 `/admin-api` 前缀，**只走管理端**。C 端与桌面端本期不新增任何接口。

---

## 1. 看板总览

### `GET /member/license/summary`

权限：`member:license:query`

```jsonc
{ "code": 0, "data": {
  "personalActiveCount": 128,            // 订阅会员数（有未过期个人订阅）
  "personalSeatOnlyCount": 23,           // 席位会员数（有有效席位、无未过期订阅）Q161
  "orgActiveCount": 12,                  // 企业有效授权数
  "orgSeatActiveCount": 86,              // 有效企业已分配席位数
  "orgSeatTotalCount": 120,              // 有效企业席位总数（各企业 MAX(seats_total) 之和）
  "yearNewCount": 34,                    // 当年「新获得授权」客户数（按客户去重，保留字段）
  "yearRenewCount": 21,                  // 当年「延续授权」客户数（按客户去重，保留字段）
  "yearTotalCount": 49,                  // 当年合计（按客户去重，保留字段）
  "yearNewPersonalCount": 30,            // Q163：新获得中的个人会员数
  "yearNewOrgSeatCount": 40,             // Q163：新获得企业的当年新增批次席位数
  "yearRenewPersonalCount": 18,          // Q163：延续中的个人会员数
  "yearRenewOrgSeatCount": 15,           // Q163：延续企业的当年新增批次席位数
  "year": 2026                           // 统计所属自然年，前端用于标注口径
} }
```

**服务端规则**

| 规则 | 依据 |
|---|---|
| 当年卡片展示 `个人会员数 / 企业席位数 / 合计`，合计由前端相加（Q163） | FR-542 / Q163 |
| 个人会员数：当年首次订阅（新获得）/ 当年订阅被延长（延续），按会员去重 | FR-542 |
| 企业席位数 = 当年新增授权批次（当年 `create_time`、未退款、未折算）的 `seats_total` 之和；企业**史上第一批**在当年 → 新获得，否则 → 延续 | Q163 |
| `yearNewCount` 等三个旧的客户数保留返回，但看板不再以「按客户去重」展示 | Q163 |
| 「当年」= 自然年 1 月 1 日 00:00 至请求时刻 | spec 边界情况 |
| 全部在 SQL 聚合，禁止内存过滤 | plan Constraints |
| 企业计数不看席位是否已分配 | Q156 |

---

## 2. 客户授权明细

### `GET /member/license/customer/page`

权限：`member:license:query`

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNo` / `pageSize` | 是 | 分页 |
| `targetType` | 否 | `1` 个人 / `2` 企业；不传 = 全部 |
| `status` | 否 | `0` 有效 / `1` 已过期；不传 = 全部 |
| `keyword` | 否 | 会员昵称 / 手机号 / 企业名，模糊匹配 |

```jsonc
{ "code": 0, "data": { "total": 3, "list": [
  { "targetType": 1, "targetId": 88, "targetName": "小王", "targetMobile": "138****8888",
    "orgName": "南京路桥机械有限公司",       // 该会员所在企业（有席位时）
    "sources": ["ORDER", "ORG_SEAT"],      // 订单购买 / GRANT 运营赠送 / ORG_SEAT 企业席位
    "startTime": 1767225600000,            // epoch ms
    "endTime": 1801353600000,              // 合并到期（= max(个人, 企业)，已做 −1 天）
    "remainingDays": 300,                  // 已过期时为 0（不塞负数）
    "expiredDays": 0,                      // 已过期 N 天；未过期为 0
    "active": true,
    "personalEndTime": 1798761599000,      // 个人来源到期；无则 null
    "orgEndTime": 1801353600000,           // 企业来源到期；无则 null
    "maxGiftMonths": 2                     // = clamp(12 − ceil(300/30), 0, 12)（0 = 不能送时长）
  }
] } }
```

**服务端规则**

| 规则 | 依据 |
|---|---|
| 双来源会员：分别给出个人与企业到期，并给**合并到期**（取较晚） | FR-546 / 012 FR-309 |
| 只有企业席位的会员：`sources` 含 `ORG_SEAT`，**不得显示为「未订阅」** | spec US68-2 |
| `maxGiftMonths = clamp(12 − ceil(remainingDays / 30), 0, 12)`；**与后端校验同一实现**（T061） | FR-555 / Q154 |
| 已过期客户在 `status=1` 下可筛出；此时 `remainingDays = 0`、`expiredDays > 0` | FR-545 / US68-3 |

---

## 3. 赠送对象（全部个人与企业）

### `GET /member/license/gift-target/page`

权限：`member:license:gift`

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNo` / `pageSize` | 是 | 分页 |
| `targetType` | 否 | `1` 个人 / `2` 企业；不传 = 全部 |
| `keyword` | 否 | 会员昵称 / 手机号 / 企业名，模糊匹配 |

```jsonc
{ "code": 0, "data": { "total": 2, "list": [
  { "targetType": 1, "targetId": 88, "targetName": "新会员", "targetMobile": "13700008888",
    "status": 0,                       // 0 启用 1 停用
    "endTime": null,                   // 无有效授权时为 null
    "remainingDays": 0, "active": false,
    "maxGiftMonths": 12 },             // 无授权 → 从今天起算，可送满 12 个月
  { "targetType": 2, "targetId": 9, "targetName": "南京路桥机械有限公司", "targetMobile": null,
    "status": 0,
    "endTime": 1801353600000,          // 最后有效日（已 −1 天）
    "remainingDays": 300, "active": true,
    "maxGiftMonths": 2 }
] } }
```

**服务端规则**

| 规则 | 依据 |
|---|---|
| 列出**全部**注册会员与全部企业（`deleted = 0`），**不要求已有授权** | Q162 / FR-548 |
| 个人到期取全部订阅的 `MAX(end_time)`；企业只取有效批次（未过期、未退款、未折算） | 与赠送服务顺延口径一致 |
| 无授权 / 已过期客户：`endTime` 为 null、`remainingDays = 0`、`maxGiftMonths = 12` | Q162 / FR-555 |
| 停用企业照常列出（`status = 1`），提交赠送时由服务端拒绝并提示先启用 | FR-541 / 边界情况 |
| 权限挂 `member:license:gift`：能送的人必须能选到对象 | FR-556 |

---

## 4. 授权赠送

### `POST /member/license/grant`

权限：`member:license:gift`

```jsonc
{ "targetType": 1,            // 1 个人 / 2 企业
  "targetId": 88,             // 会员编号或企业编号
  "contentType": 1,           // 1 时长 / 2 席位
  "months": 3,                // contentType=1 时必填，且只能是 1 / 3 / 6 / 12（Q160）
  "seats": null,              // contentType=2 时必填，≥1；企业无有效批次时还需 months
  "reason": "客户投诉打印机故障，补偿 3 个月"   // 必填，≤255 字
}
```

```jsonc
{ "code": 0, "data": { "grantId": 501, "afterEndTime": 1801353600000, "afterSeats": 25 } }
```

**服务端规则（逐条都能验收）**

| 规则 | 依据 |
|---|---|
| 对象必须存在且未注销；企业必须**启用** | 边界情况 / FR-548 |
| `contentType=1` 时 `months ∈ {1,3,6,12}` 且 ≤ 12 | Q160 / Q154 |
| 赠送后剩余 ≤ 12 个月，否则拒绝并返回**还能送多少** | Q154 / FR-555 / SC-209 |
| `contentType=2` 时 `seats ≥ 1`；企业无有效批次时必须同时给 `months` | Q157 / FR-548 |
| 个人版本固定 `professional`、企业固定 `enterprise`，请求里**不接受版本字段** | Q158 |
| 已有有效授权 → 从原到期日顺延；已过期/无授权 → 从今天起算 | FR-551 / research R6 |
| 赠送记录 + 授权变更为**同一事务**；失败全部回滚 | FR-554 |
| 同一请求重复提交（连点）只生效一次：以 `reason + 对象 + 时间窗` 做幂等保护，或前端提交后禁用按钮 | 边界情况「连点」 |
| 接口权限校验 MUST NOT 只依赖前端 | FR-556 |

---

## 4.1 撤回赠送（Q164，2026-11-19）

### `POST /member/license/grant/revoke`

权限：**复用** `member:license:gift`（不新增权限点）

```jsonc
{ "id": 501,                       // 赠送记录编号
  "reason": "误送给该客户，实际应为另一家"   // 必填，≤255 字
}
```

```jsonc
{ "code": 0, "data": true }
```

**服务端规则（逐条都能验收）**

| 规则 | 错误码 |
|---|---|
| 理由为空 / 超过 255 字 | `1_004_019_014` 请填写撤回理由 |
| 记录不存在 | `1_004_019_009` 赠送记录不存在 |
| 已经撤回过 | `1_004_019_010` 这笔赠送已经撤回过了 |
| 不是该对象**最近一笔**赠送 | `1_004_019_011` 只能撤回该客户最近一笔赠送 |
| 个人：这笔赠送产生的订阅行之后又有新订阅 / 找不到该行 | `1_004_019_013` 之后的权益已变化，无法撤回 |
| 企业：批次已退款 / 已折算 / 到期日已不是赠送后的值 | `1_004_019_013` 同上 |
| 企业：批次上还有已分配席位（**不连带收回成员权益**） | `1_004_019_012` 还有 N 个席位已分配，请先撤销席位分配 |

反向操作与记录留痕口径见 `data-model.md` §1「撤回的反向操作」。撤回与原赠送同一事务，
失败不留痕；赠送记录**不删除**，只写 `revoke_time / revoke_operator_id / revoke_reason`。

---

## 5. 赠送记录

### `GET /member/license/grant/page`

权限：`member:license:query`

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNo` / `pageSize` | 是 | 分页 |
| `targetType` / `targetId` | 否 | 按被赠对象筛 |
| `operatorId` | 否 | 按操作人筛 |
| `createTime` | 否 | 时间范围（`[起, 止]`） |

```jsonc
{ "code": 0, "data": { "total": 1, "list": [
  { "id": 501, "targetType": 1, "targetId": 88, "targetName": "小王",
    "contentType": 1, "months": 3, "seats": null, "edition": "professional",
    "reason": "客户投诉打印机故障，补偿 3 个月",
    "beforeEndTime": 1798761599000, "afterEndTime": 1801353600000,
    "beforeSeats": null, "afterSeats": null,
    "operatorId": 1, "operatorName": "张经理",
    "createTime": 1794900000000,
    "revokeTime": null, "revokeOperatorName": null, "revokeReason": null }  // Q164：非空 = 已撤回
] } }
```

**服务端规则**：记录**不可删除**，撤回只写 `revoke_*` 三列；`reason` 恒非空；按 `create_time` 倒序。
