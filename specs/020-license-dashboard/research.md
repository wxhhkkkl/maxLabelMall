# Research：授权看板（020）

> 每个决策都给出**决策 / 理由 / 被否方案**，并附代码或 SQL 证据。
> 明确一条：本特性**不新建授权模型**，只在既有模型上补一个"赠送"的入口与留痕。

---

## R1 看板的数据源：只读聚合，不建宽表

**Decision**：看板与明细都**实时聚合**既有表（`member_subscription`、`maxlabel_org`、
`maxlabel_org_license`、`maxlabel_org_seat`、新增的 `member_license_grant`），不建统计宽表、不做定时任务。

**Rationale**：
- 数据量小（现有商品 295 个、会员数百量级），实时聚合完全够用；SC-207 的 2 秒目标留有充足余量。
- 宽表会引入"数字什么时候刷新"的第二份真相 —— 而这正是 020 要消灭的问题（今天会员列表的
  「订阅状态」与会员详情的订阅页签就是两份）。

**Alternatives considered**：定时汇总表（否：多一个作业、多一处可能失准）；
复用 `statistics` 模块的会员统计（否：那边只有注册口径，且不带授权）。

---

## R2 赠送个人时长：放宽来源列 + `license_grant_id`

**Decision**：
- `member_subscription` 的 `plan_id` / `spu_id` / `sku_id` / `source_order_id` /
  `source_order_item_id` / `source_order_no` **放宽为可空**，新增 `license_grant_id bigint NULL`。
- 赠送行：`edition = professional`（Q158），上述来源列全为 NULL，`license_grant_id` 指向赠送记录；
  `start_time` / `end_time` / `period_months` 照既有口径计算（R6）。

**Rationale**：
- 今天 `source_order_item_id` 是 **NOT NULL 且唯一**（`uk_source_item(tenant_id, source_order_item_id)`），
  `grant()` 也强制要求订单号 —— 手工赠送**在结构上就塞不进去**（spec 的 E2）。
- MySQL 唯一索引允许多个 NULL：放宽后既有订单行完全不受影响，赠送行也不会互相冲突。
- 不新增"赠送订阅表"：那会让权益判定（`getCurrent`）要合并第二张表，**改动面反而更大**。

**Alternatives considered**：
- 伪造订单号（`source_order_item_id = -grantId`）：不用改 DDL，但把"订单"语义污染成簿记技巧，
  后续任何按订单反查的代码都要认识这个负数约定 —— 否。
- 新建 `member_gift_subscription`：权益解析要并表，且 012 的"取较晚"逻辑会多一处 —— 否。
- 为赠送建一条隐藏的"系统套餐"：还要给它挂 SPU/SKU，比放宽列更重 —— 否。

**风险与缓解**：所有读取 `planId` 的地方只是**回显**（`AdminMemberOrgManageController` 的 VO 赋值），
没有业务逻辑依赖它非空；任务里要求全仓复核 `getPlanId()` 的调用点。

---

## R3 赠送留痕：新增 `member_license_grant`（不往业务表堆备注列）

**Decision**：新建 `member_license_grant`，一次赠送一行：对象、内容类型（时长/席位）、版本、
时长月数或席位数、**理由**、赠送前后的到期日、赠送前后的席位数、操作人与时间。

**Rationale**：
- 两张业务表（`member_subscription` / `maxlabel_org_license`）都**没有"操作人 / 理由"列**（spec E8）。
  加列只能记录"最近一次"，而赠送是**可多次发生的事件**，天生该是流水。
- 赠送记录页签（Q159）需要按对象 / 操作人 / 时间筛选 —— 流水表直接支持。
- 与 `@LogRecord` 操作日志**双写**：操作日志回答"谁在系统里做了什么"，
  本表回答"这笔授权的来历"，两者互相印证（FR-553）。

**Alternatives considered**：只在操作日志里查（否：日志是通用结构，reason、前后到期日都要塞进 extra，
且无法按客户检索）；给订阅/批次加 `gift_reason` 列（否：只能记最新一次）。

---

## R4 企业赠送：落到"当前唯一的有效批次"

**Decision**：
- **送席位**：给当前**有效批次**（未过期 + 未退款 + 未折算）`seats_total += N`；若历史脏数据下存在多条，
  加到 `seats_total` 最大的那条（并列取 `end_time` 最晚）—— 保证容量**真的**增加 N。
- **送时长**：同一批次的 `end_time` 顺延 N 个月（`start_time` 不变）。
- **企业无有效批次**：新建一条批次（`source_order_*` 为 NULL、`license_grant_id` 指向赠送记录，
  `seats_total = N`（送席位时），`end_time = 今天 + N 个月`（送时长或"送席位无批次"时必填）。

**Rationale**：
- `getSeatTotal()` 取的是**有效批次里的 `MAX(seats_total)`**（`MaxLabelOrgLicenseServiceImpl#getSeatTotal`），
  不是求和。若把席位加在一条 seats 更少的批次上，`MAX` 不变 → **客户等于没收到**。
- 折叠语义（`updateFoldedByOrgId`）保证续费后**至多一条**有效批次，正常情况下"唯一"成立，
  Q157 的"跟最晚到期批次同寿命"也就自然满足。
- 企业到期日 = 有效批次 `MAX(end_time) − 1 天`（`getMaxEndTime`），顺延批次即顺延企业到期日。

**Alternatives considered**：把 `getSeatTotal` 改成 `SUM(seats_total)`（否：会改变既有续费后的容量语义，
属于回归风险，且与 022 的折叠设计冲突）；新建独立的"赠送席位池"（否：权益判定要多一处）。

---

## R5 「当年新获得 / 延续」的判定：用 `create_time`，不用 `start_time`

**Decision**：
- **个人**：以 `member_subscription.create_time`（= 发放实际发生时间）落在当年为准。
  某会员在本年的**最早一条**订阅行出现在当年 → 计入「新获得」；
  当年有订阅行、但更早年份已有过订阅 → 计入「延续」。
- **企业**：同法，以 `maxlabel_org_license.create_time`（及企业创建）判定。
- **赠送**：`member_license_grant.create_time` 当年 → 该客户计入「延续」
  （若其首次授权也在当年，同时计入「新获得」，合计按客户去重）。
- **合计**：按客户去重（同一客户同年既新获得又延续只算 1 个），个人与企业的去重结果相加。

**Rationale**：续费行的 `start_time` 是**原到期日**（可能落在明年），按它统计会把"今年续的费"
算到明年去；`create_time` 才是"今年发生了这件事"的证据（`MemberSubscriptionServiceImpl#grant`
里 `startDate = max(today, 上段到期日−1天)`）。

**Alternatives considered**：按 `start_time` 归属年份（否：续费跨年即错）；只统计订单数（否：赠送没有订单，
且一个订单可能买多份）。

---

## R6 赠送的时长与顺延口径：与续费同一套算法

**Decision**：赠送个人时长时，`startDate = max(今天, 当前最晚 end_time 的日期 − 1 天)`，
`endDate = startDate + N 个月`，落库 `end_time = endDate + 1 天 00:00`（与 `grant()` 完全一致）。
企业送时长同理，顺延当前有效批次的 `end_time`。

**Rationale**：客户手册与财务口径都按"自然月顺延、到期日次日零点失效"理解；另造一套算法会让
"买来的"和"送的"出现一日之差的缝隙。

**Alternatives considered**：从今天起算（否：会吞掉客户已付费的剩余时长，FR-551 明确禁止）。

---

## R7 权限点与菜单：新增两个权限，不搭车

**Decision**：新增菜单「授权看板」（挂在 `/software` 下）+ 两个按钮权限点：
`member:license:query`（看板与明细）、`member:license:gift`（赠送按钮与接口）。

**Rationale**：赠送会改变客户的商业权益，是可审计的敏感动作；与"看"分开授权，
才能让只读角色（如客服）看得到、不能送（FR-539 / FR-556）。
004 的踩坑记录：**权限点必须写进 `system_menu`**，否则连超管都看不到按钮。

**Alternatives considered**：复用 `member:subscription:query`（否：把它变成能改钱的口子）；
复用 `member:user:update`（否：语义不对，且会放宽本不该改用户资料的角色）。

---

## R8 明确不碰的东西

- **不改** `getCurrent()` / 权益判定（个人 ∪ 企业取较晚）—— 赠送只新增行，判定式一行不改。
- **不改** 支付回调的发放路径（`TradeMaxLabelSubscriptionOrderHandler`）。
- **不改** C 端与桌面端：赠送后客户下次读取权益即看到新到期日（数据驱动，无需发版）。
- 早期机器绑定授权码（`/api/license/*`，桌面端旧体系）商城侧无数据，**不在看板范围**。
