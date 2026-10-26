# 管理端接口契约（012 企业客户与席位授权）

> 前缀：**只走 `/admin-api`**（宪法）。统一响应 `{ code, data, msg }`；分页 `data = { list, total }`。
> 所有接口都标了 `@PreAuthorize` 权限串；权限串必须同时登记进 `system_menu`（`021` 迁移 SQL）。

---

## 1. 企业管理（member-server）

### 1.1 `GET /member/org/page` —— 企业分页

权限：`member:org:query`

| 参数 | 必填 | 说明 |
|---|---|---|
| `pageNo` / `pageSize` | 是 | 分页 |
| `name` | 否 | 企业名称模糊 |
| `status` | 否 | 0 启用 / 1 停用 |

```jsonc
{ "code": 0, "data": { "list": [{
  "id": 88, "name": "南京路桥机械有限公司",
  "ownerMemberId": 1001, "ownerNickname": "张三", "ownerMobile": "138****8000",
  "status": 0, "memberCount": 5,
  "seatTotal": 20, "seatUsed": 3, "licenseEndDate": "2027-10-26",
  "createTime": "2026-10-26T10:00:00+08:00"
}], "total": 1 }, "msg": "" }
```

> `seatTotal/seatUsed` 由服务端批量计算，**禁止 N+1**（一页一次聚合查询）。

### 1.2 `GET /member/org/get?id=88` —— 企业详情

权限：`member:org:query`

返回企业基本信息 + `seatTotal/seatUsed/seatAvailable` + `licenseEndDate`。

### 1.3 `POST /member/org/create` —— 创建企业

权限：`member:org:create`

```jsonc
{ "name": "南京路桥机械有限公司", "ownerMemberId": 1001, "remark": "" }
```

- `ownerMemberId` 必须是已注册会员；创建时同事务写入 `maxlabel_org_member(role=OWNER)`。
- 若该会员已属于其他企业 → `MEMBER_ORG_MEMBER_IN_OTHER`。

### 1.4 `PUT /member/org/update` —— 修改企业

权限：`member:org:update`

```jsonc
{ "id": 88, "name": "新名称", "remark": "…" }
```

> 不允许通过此接口改 `ownerMemberId`；转交走 1.8。

### 1.5 `PUT /member/org/update-status` —— 启用 / 停用

权限：`member:org:update`

```jsonc
{ "id": 88, "status": 1 }
```

- 停用后所有企业来源权益立即失效（解析时判断，不改席位状态）。
- 不删除成员、订单、模板、打印记录（FR-317）。

### 1.6 `GET /member/org/member/page` —— 企业成员与席位

权限：`member:org:query`

参数：`orgId`（必填）、`pageNo/pageSize`、`keyword`（选填）。
返回字段同 C 端 2.2，外加 `joinedTime`。

### 1.7 `POST /member/org/member/add` / `remove` / `update-role`

| 接口 | 权限 | 说明 |
|---|---|---|
| `POST /member/org/member/add` | `member:org:member:add` | `{orgId, mobile}`，按手机号加入已注册会员 |
| `POST /member/org/member/remove` | `member:org:member:remove` | `{orgId, memberId}`，不能移出 OWNER |
| `PUT /member/org/member/update-role` | `member:org:member:update-role` | `{orgId, memberId, role}`，只能设 ADMIN/MEMBER |

### 1.8 `POST /member/org/owner/transfer` —— 运营代转 OWNER

权限：`member:org:owner:transfer`

```jsonc
{ "orgId": 88, "memberId": 1002 }
```

- 目标必须是本企业成员；原子换角色（R68）。
- 与 C 端自助转交走同一服务方法，只多一层权限校验与操作日志。

### 1.9 `POST /member/org/seat/assign` / `revoke`

| 接口 | 权限 | 说明 |
|---|---|---|
| `POST /member/org/seat/assign` | `member:org:seat:assign` | `{orgId, memberId}` |
| `POST /member/org/seat/revoke` | `member:org:seat:revoke` | `{orgId, memberId}` |

容量与并发规则同 C 端（R64）；运营代操作也受同样的容量限制。

### 1.10 `GET /member/org/license/page` —— 授权批次

权限：`member:org:license:query`

参数：`orgId`（必填）、`pageNo/pageSize`。
返回字段同 C 端 2.8（含 `folded`），外加 `sourceOrderId/skuId/spuId/createTime`。
折算批次在「有效期」列显示「已折算」标签，不再显示剩余天数（Q78）。

> 运营后台的人工退款通道保留（Q79）：退款后批次打「已退款」标记，列表显示「来源已退款」。

---

## 2. 会员详情：所属企业（只读）

### 2.1 `GET /member/org/get-by-member?memberId=1002`

权限：`member:org:query`

```jsonc
{ "code": 0, "data": {
  "orgId": 88, "orgName": "南京路桥机械有限公司",
  "role": 3, "seatActive": true, "seatEndDate": "2027-10-26"
}, "msg": "" }
```

- 无企业时 `orgId=null`。
- 管理端会员详情页用只读字段展示，**不提供**在此处改企业/分配席位的入口（去企业管理页操作）。

---

## 3. 会员授权商品：增加 `scope`

### 3.1 既有接口的字段扩展

| 接口 | 变化 |
|---|---|
| `GET /product/subscription-plan/page` | 响应增加 `scope`；请求增加可选 `scope` 筛选 |
| `GET /product/subscription-plan/get?id=` | 响应增加 `scope` |
| `POST /product/subscription-plan/create` | 请求增加 `scope`（1 个人 / 2 企业，默认 1） |
| `PUT /product/subscription-plan/update` | 请求增加 `scope`（存量计划可改，但必须通过校验） |

```jsonc
{ "spuId": 501, "skuId": 601, "periodMonths": 12, "scope": 2, "enabled": true, "sort": 0 }
```

**校验**：

- `scope=2` → SPU 的 `enterpriseOnly` 必须为 `true`；
- `scope=1` → SPU 的 `enterpriseOnly` 必须为 `false`；
- 同一租户同一 `scope` 同一时刻至多一条启用计划；
- 企业计划仍要求：SPU 只含虚拟商品、SKU 价格 > 0。

### 3.2 错误码

| 名称 | 语义 |
|---|---|
| `MEMBER_SUBSCRIPTION_PLAN_SCOPE_INVALID` | scope 非法 |
| `MEMBER_SUBSCRIPTION_PLAN_SCOPE_SPU_MISMATCH` | 计划类别与商品“企业专享”标记不一致 |

---

## 4. 商品编辑：企业专享开关

### 4.1 `product_spu.enterpriseOnly`

| 接口 | 变化 |
|---|---|
| `GET /product/spu/page`（管理端） | 响应增加 `enterpriseOnly`；请求增加可选筛选 |
| `GET /product/spu/get?id=` | 响应增加 `enterpriseOnly` |
| `POST /product/spu/create` / `PUT /product/spu/update` | 请求增加 `enterpriseOnly`（默认 false） |

- 管理端商品编辑「其他设置」页增加开关：「企业专享（不在公开商城展示）」。
- 勾选后公开商城列表/详情/筛选全部排除；企业中心通过专用接口可见。
- 已上架商品改成企业专享时，若已有个人授权计划映射 → 拒绝并提示先调整计划（避免个人计划指向企业商品）。

---

## 5. 权限与菜单（021 迁移 SQL）

| 菜单 / 按钮 | permission | 类型 |
|---|---|---|
| 企业管理（页面） | `member:org:query` | 页面 |
| 新增企业 | `member:org:create` | 按钮 |
| 修改 / 停用企业 | `member:org:update` | 按钮 |
| 新增成员 | `member:org:member:add` | 按钮 |
| 移出成员 | `member:org:member:remove` | 按钮 |
| 调整 ADMIN | `member:org:member:update-role` | 按钮 |
| 转交 OWNER | `member:org:owner:transfer` | 按钮 |
| 分配席位 | `member:org:seat:assign` | 按钮 |
| 撤销席位 | `member:org:seat:revoke` | 按钮 |
| 查看授权批次 | `member:org:license:query` | 按钮/页签 |

> ⚠️ 权限串没登记成 `system_menu` 行时，`v-hasPermi` 会把按钮从 DOM 删掉，连超管也看不到；
> 后端 `@PreAuthorize` 对超管有旁路，会表现为“接口能调、按钮不显示”（013 已记录）。

---

## 6. 错误码

| 模块 | 名称 | 语义 |
|---|---|---|
| member | `MEMBER_ORG_NOT_EXISTS` | 企业不存在 |
| member | `MEMBER_ORG_DISABLED` | 企业已停用 |
| member | `MEMBER_ORG_MEMBER_IN_OTHER` | 会员已在其他企业 |
| member | `MEMBER_ORG_OWNER_REQUIRED` | 企业必须保留 OWNER |
| member | `MEMBER_ORG_SEAT_FULL` | 席位已用完 |
| product | `MEMBER_SUBSCRIPTION_PLAN_SCOPE_INVALID` | 计划类别非法 |
| product | `MEMBER_SUBSCRIPTION_PLAN_SCOPE_SPU_MISMATCH` | 计划类别与商品标记不一致 |
