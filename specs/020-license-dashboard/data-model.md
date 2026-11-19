# 数据模型：授权看板（020）

> 变更总计：**新增 1 张表、2 张表各加 1 列、放宽 6 + 6 列为可空、新增 2 条菜单权限**。
> 所有 DDL 走 `sql/mysql/042-license-dashboard.sql`（幂等：先查 `information_schema`）。

---

## 0. 术语对照

| 说法 | 落在模型里 |
|---|---|
| 客户（个人） | `targetType = 1` + `targetId = member_user.id` |
| 客户（企业） | `targetType = 2` + `targetId = maxlabel_org.id` |
| 授权 / 订阅 | 个人：`member_subscription` 的一段；企业：`maxlabel_org_license` 的一批 |
| 席位 | `maxlabel_org_seat`（分配记录）+ 批次 `seats_total`（容量） |
| 赠送 | `member_license_grant` 一行 + 它引起的订阅行 / 批次变更 |

---

## 1. 新增：`member_license_grant`（赠送记录）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK auto | 赠送编号 |
| `target_type` | tinyint NOT NULL | 对象类型：1 个人会员，2 企业 |
| `target_id` | bigint NOT NULL | 会员编号（`member_user.id`）或企业编号（`maxlabel_org.id`） |
| `content_type` | tinyint NOT NULL | 内容：1 时长，2 席位 |
| `edition` | varchar(32) NULL | 赠送版本快照：`professional`（个人）/ `enterprise`（企业），Q158 |
| `months` | int NULL | 赠送月数（`content_type=1` 时必填；档位 1/3/6/12，Q160） |
| `seats` | int NULL | 赠送席位数（`content_type=2` 时必填） |
| `reason` | varchar(255) NOT NULL | **赠送理由**（必填，FR-549） |
| `before_end_time` | datetime NULL | 赠送前的到期时间（个人取最晚订阅 `end_time`；企业取 `MAX(end_time)`；无授权为 NULL） |
| `after_end_time` | datetime NOT NULL | 赠送后的到期时间 |
| `before_seats` | int NULL | 赠送前的席位数（企业席位赠送才有） |
| `after_seats` | int NULL | 赠送后的席位数 |
| `license_id` | bigint NULL | **050 新增**：企业赠送作用到的批次（`maxlabel_org_license.id`）；个人为 NULL。撤回要改回**同一批次**，不能靠重算 `MAX(seats_total)` 猜 |
| `operator_id` | bigint NOT NULL | 操作人（`system_users.id`） |
| `revoke_time` | datetime NULL | **050 新增**：撤回时间；非空 = 已撤回（Q164） |
| `revoke_operator_id` | bigint NULL | **050 新增**：撤回人（`system_users.id`） |
| `revoke_reason` | varchar(255) NULL | **050 新增**：撤回理由（必填） |
| `creator` / `create_time` / `updater` / `update_time` / `deleted` / `tenant_id` | | 沿用 `BaseDO` 约定 |

索引：
- `idx_target(tenant_id, target_type, target_id, create_time)` —— 明细按客户查赠送历史、记录页按对象筛
- `idx_operator(tenant_id, operator_id, create_time)` —— 记录页按操作人筛
- `idx_create_time(tenant_id, create_time)` —— 记录页按时间范围筛、当年统计

**不变式**：一次赠送一行，**不可删除**；`reason` 非空。撤回**不删行、不改赠送内容**，
只写 `revoke_*` 三列（2026-11-19 / Q164，推翻原「不可撤销」口径）。

**撤回的反向操作**（Q164 / FR-559~562）：

| 赠送内容 | 撤回时改什么 | 拦下条件 |
|---|---|---|
| 个人时长 | 删除这笔赠送产生的 `member_subscription` 行（`license_grant_id = grant.id`） | 该行之后又插入过新订阅（购买 / 再次赠送） |
| 企业席位（改现有批次） | 该批次 `seats_total` −= `seats` | `seats_total − seats <` 已分配席位数 |
| 企业时长（改现有批次） | 批次 `end_time` 回到 `before_end_time`、`period_months` −= `months` | 批次 `end_time` 已不是 `after_end_time`（之后又被顺延） |
| 新建批次（无既有批次时） | 整批逻辑删除 | 已分配席位数 > 0 |

**「最近一笔」判定**：同 `(target_type, target_id)` 下 `MAX(id)` 的那一行；更早的一律拒绝（Q164 ②）。

---

## 2. 改：`member_subscription`（个人订阅流水）

| 变更 | 说明 |
|---|---|
| `plan_id` / `spu_id` / `sku_id` | `NOT NULL` → **`NULL`**（赠送行没有套餐快照） |
| `source_order_id` / `source_order_item_id` / `source_order_no` | `NOT NULL` → **`NULL`**（赠送行没有订单） |
| **新增** `license_grant_id` | `bigint NULL`，赠送来源 → `member_license_grant.id`；订单来源为 NULL |
| `uk_source_item(tenant_id, source_order_item_id)` | **保留不动**（MySQL 唯一索引允许多个 NULL，赠送行不冲突） |

**行的三种来源**（读取方靠这两列区分）：

| 场景 | `source_order_item_id` | `license_grant_id` |
|---|---|---|
| 下单购买 / 续费 | 有值 | NULL |
| 运营赠送 | NULL | 有值 |

`edition` 对赠送固定写 `professional`（Q158）；`period_months` = 本次赠送月数。

---

## 3. 改：`maxlabel_org_license`（企业授权批次）

| 变更 | 说明 |
|---|---|
| `plan_id` / `spu_id` / `sku_id` / `source_order_id` / `source_order_item_id` / `source_order_no` | `NOT NULL` → **`NULL`**（赠送批次没有订单与套餐快照） |
| **新增** `license_grant_id` | `bigint NULL`，赠送批次来源 |
| `seats_total` / `start_time` / `end_time` | 不变（赠送时按 R4 写入或顺延） |

**赠送批次的语义**：只出现在"企业当前没有有效批次"时的首次赠送；此后送席位/送时长
都改**当前有效批次**（R4）。

⚠️ **送时长时 `period_months` 同步增加 N**（`start_time` 不变、`end_time` 顺延）：
企业授权列表按"本段授予月数"展示，若只改 `end_time` 会出现"授 12 个月却多出 3 个月"的观感。
赠送批次自身则直接写 `period_months = N`。

---

## 4. 派生口径（看板与明细的唯一真相）

> 全部在 SQL 里聚合，**禁止**先取全量再在内存过滤（plan 的 Constraints）。

### 4.1 有效（FR-541）

| 指标 | 判定 |
|---|---|
| 订阅会员数 | `COUNT(DISTINCT member_id) FROM member_subscription WHERE deleted=0 AND end_time > NOW()` |
| 席位会员数（Q161） | 有有效企业席位、但**无**未过期个人订阅的会员；单独计数，不混进订阅会员 |
| 企业有效授权数 | `COUNT(*) FROM maxlabel_org o WHERE o.status=0 AND EXISTS(有效批次)`；有效批次 = `end_time > NOW() AND refunded=0 AND folded_time IS NULL` |
| 企业席位（已分配 / 总数） | 已分配 = `COUNT(maxlabel_org_seat WHERE status=0)`；总数 = 有效批次的 `MAX(seats_total)`（与 `getSeatTotal` 同口径） |

### 4.2 当年客户（FR-542 / Q155）

设 `Y` = 当年 1 月 1 日 00:00。

| 指标 | 判定 |
|---|---|
| 个人「新获得」 | `member_id` 满足 `MIN(create_time) >= Y`（该会员**史上第一条**订阅行出现在今年） |
| 个人「延续」 | 当年有订阅行（`create_time >= Y`）或当年有赠送记录，**且** `MIN(create_time) < Y` |
| 企业「新获得」 | 企业史上第一条授权批次 `MIN(create_time) >= Y` |
| 企业「延续」 | 当年有批次或赠送记录，且 `MIN(create_time) < Y` |
| 合计（旧口径，保留字段） | 上述四个集合按客户去重后相加（个人 1 人、企业 1 家各算 1） |

**Q163 展示口径（2026-11-17 修订）**：看板不再展示「客户数」，改为 `个人会员数 + 企业席位数`：

| 指标 | 判定 |
|---|---|
| 新获得 · 个人会员数 | 同上「个人新获得」 |
| 新获得 · 企业席位数 | `SUM(seats_total)`，条件：批次 `deleted = 0`、`refunded = 0`、`folded_time IS NULL`、`create_time >= Y`，且该企业 `MIN(create_time) >= Y` |
| 延续 · 个人会员数 | 同上「个人延续」 |
| 延续 · 企业席位数 | 同上求和条件，且该企业 `MIN(create_time) < Y` |
| 合计 | 个人会员数 + 企业席位数（**单位不同，不是客户数**） |

**退款客户照常计入个人会员数**（2026-11-16 裁决）：订阅行的 `refunded` 只是**标记**、不回收授权，
当年确有"获得 / 延续"的事实。**但**企业席位按有效批次求和，退款 / 折算批次不计入；
「当前有效」仍按 §4.1 判 —— 订阅行是否有效只看 `end_time`。几个问题分开判，不要用同一个条件。

⚠️ **必须用 `create_time` 而不是 `start_time`**：续费行的 `start_time` 是原到期日（可能落在明年）—— 见 research R5。

### 4.3 明细行（FR-544 ~ FR-546 / Q163）

⚠️ Q163 起看板「个人授权」卡片只显示 `personalActiveCount`（订阅会员）；`personalSeatOnlyCount`
仍由接口返回，席位会员仍在明细中，只是不再在看板单独展示。

一个客户一行，字段：

| 字段 | 个人 | 企业 |
|---|---|---|
| 客户 | 昵称 + 手机号 | 企业名 + OWNER 昵称 |
| 来源 | 订单购买 / 运营赠送（按 `source_order_item_id` vs `license_grant_id`） | 批次来源同上；会员行另标「企业席位」 |
| 开始 / 到期 | 最晚有效行的 `start_time` / `end_time − 1 天` | `MIN(start_time)` / `MAX(end_time) − 1 天` |
| 剩余天数 / 已过期天数 | `remainingDays = max(0, 到期日 − 今天)`；`expiredDays = max(0, 今天 − 到期日)` | 同左 |
| 合并到期（仅双来源会员） | `max(个人到期, 企业到期)`（沿用 012 FR-309） | — |
| **可赠送额度** | `clamp(12 − ceil(remainingDays / 30), 0, 12)`（FR-555 / T061 唯一实现） | 时长同左；席位无上限 |

### 4.4 赠送对象（Q162，2026-11-17）

赠送对象的集合**不是** 4.3 的「客户明细」：后者只含已有授权的客户，前者覆盖全部客户。

| 项 | 口径 |
|---|---|
| 对象集合 | 全部 `member_user.deleted = 0` + 全部 `maxlabel_org.deleted = 0`（**不限是否已有授权**） |
| 个人当前到期 | 全部订阅行的 `MAX(end_time)`（与 `grantToMember` 顺延口径一致） |
| 企业当前到期 | 有效批次（`end_time > now`、未退款、未折算）的 `MAX(end_time)`（与 `grantToOrg` 一致） |
| 无授权 | `endTime = NULL`、`remainingDays = 0`、`maxGiftMonths = 12`（从当天起算） |
| 停用企业 | 照常列出（`status = 1`），提交赠送时由服务端拒绝 |

---

## 5. 菜单与权限（`042-license-dashboard.sql` 内）

| 菜单/按钮 | type | permission | 说明 |
|---|---|---|---|
| 授权看板 | 2（菜单，挂 `/software`，sort=60） | `member:license:query` | 页面可见性 |
| 授权赠送 | 3（按钮） | `member:license:gift` | 赠送按钮 + 接口 |

写法沿用 `sql/mysql/021` / `026` 的幂等模式（`INSERT ... SELECT WHERE NOT EXISTS`，目标表包一层派生表）。
