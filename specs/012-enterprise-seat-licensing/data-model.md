# 数据模型（012 企业客户与席位授权）

**一句话**：新增 **4 张表**（企业、企业成员、企业授权批次、席位），并在 3 张既有表上各加 1 列
（计划 `scope`、商品 `enterprise_only`、订单 `maxlabel_org_id`）。
`member_user`、`member_subscription`、既有模板/打印/订单结构**不改语义**。

所有新表都带 `tenant_id`、`creator/create_time/updater/update_time/deleted`（`BaseDO` 约定），
时间语义统一 **Asia/Shanghai**。迁移脚本：`yudao-cloud/sql/mysql/021-enterprise-seat-licensing.sql`。

---

## 1. `maxlabel_org` —— 企业主体（新增）

> 一期由运营建档；`owner_member_id` 是“有且仅有一个 OWNER”的权威字段。
> 企业名称**不设唯一键**（现实中可能重名），靠运营搜索与人工核对。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_org` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(128) NOT NULL COMMENT '企业名称',
  `owner_member_id` bigint NOT NULL COMMENT '企业 OWNER，关联 member_user.id',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 启用 1 停用',
  `remark` varchar(255) NULL DEFAULT '' COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_name`(`tenant_id` ASC, `name` ASC) USING BTREE,
  INDEX `idx_owner`(`tenant_id` ASC, `owner_member_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业主体';
```

**约束**：

- `status=0` 时企业可用；停用后所有席位立即失效（解析时判断，不改席位状态）。
- 停用**不删除**任何数据；可重新启用，未过期批次下的席位随之恢复。
- `owner_member_id` 必须同时存在一条 `maxlabel_org_member.role=OWNER` 的行。

---

## 2. `maxlabel_org_member` —— 企业成员关系（新增）

> **退出/移出为物理删除**（成员关系不是资金数据）；历史操作靠 `system_operate_log` 留痕。
> 唯一键 `(tenant_id, member_id)` 保证“一人同一时刻只属于一个企业”。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_org_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '企业编号，关联 maxlabel_org.id',
  `member_id` bigint NOT NULL COMMENT '会员编号，关联 member_user.id',
  `role` tinyint NOT NULL DEFAULT 3 COMMENT '角色：1 OWNER 2 ADMIN 3 MEMBER',
  `joined_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（退出时物理删除，本列保留）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_member`(`tenant_id` ASC, `member_id` ASC) USING BTREE,
  INDEX `idx_org`(`tenant_id` ASC, `org_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业成员关系';
```

**角色规则**：

| 操作 | OWNER | ADMIN | MEMBER |
|---|---|---|---|
| 查看企业中心 | ✅ | ✅ | ✅ |
| 增删成员 / 分配撤销席位 | ✅ | ✅ | ❌ |
| 设置/取消 ADMIN | ✅ | ❌ | ❌ |
| 转交 OWNER | ✅ | ❌ | ❌ |
| 购买企业授权 | ✅ | ✅ | ❌ |
| 主动退出企业 | ❌（须先转交） | ✅ | ✅ |

- 角色**不自动附带权益**；OWNER/ADMIN 需要云端写入时同样占用一个席位（FR-306）。
- 一人一企业；加入新企业前必须先退出原企业（FR-290）。
- 企业停用**不自动删除成员关系**；成员须先主动退出（或由运营移出），才能加入其他企业。
- 退出/移出时，在同一事务内释放其席位（FR-308）。

---

## 3. `maxlabel_org_license` —— 企业授权批次（新增）

> 一行 = 一次支付授予的**一段周期 × 一批席位**。续费插新行，不改旧行。
> `seats_total = 订单项数量`（一个购买单位 = 一个席位）；
> **周期取 `plan.period_months`，不乘数量** —— 数量是席位数，不是购买周期数。
> 有效期语义与 `member_subscription` 完全一致：`end_time` 是最后有效自然日的次日 00:00。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_org_license` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '企业编号，关联 maxlabel_org.id',
  `plan_id` bigint NOT NULL COMMENT '计划编号，关联 maxlabel_subscription_plan.id',
  `spu_id` bigint NOT NULL COMMENT '商品 SPU 快照',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 快照',
  `period_months` int NOT NULL COMMENT '本段授予月数（= plan.period_months；席位数量只决定 seats_total）',
  `seats_total` int NOT NULL COMMENT '本批授予席位数（= 订单项数量）',
  `start_time` datetime NOT NULL COMMENT '本段开始自然日 00:00:00',
  `end_time` datetime NOT NULL COMMENT '本段最后有效自然日的次日 00:00:00（不含当日）',
  `source_order_id` bigint NOT NULL COMMENT '来源订单编号',
  `source_order_item_id` bigint NOT NULL COMMENT '来源订单项编号（幂等键）',
  `source_order_no` varchar(64) NOT NULL COMMENT '来源订单号快照（后台展示）',
  `refunded` bit(1) NOT NULL DEFAULT b'0' COMMENT '来源订单或订单项是否已全额退款（仅标记，不回收）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_source_item`(`tenant_id` ASC, `source_order_item_id` ASC) USING BTREE,
  INDEX `idx_org_end`(`tenant_id` ASC, `org_id` ASC, `end_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业授权批次';
```

**约束**：`seats_total > 0`、`period_months > 0`、`org_id` 必须是启用中的企业；
`seats_total` 取订单项数量，不接受调用方自报的席位数字。

### 发放算法（事务内）

```text
grantOrgLicense(orgId, orderItem, plan):
  1. SELECT id FROM maxlabel_org WHERE id = orgId AND status = 0 FOR UPDATE  -- 并发锚点
  2. 已存在同 source_order_item_id 的批次 → 直接返回（幂等）
  3. latestEnd = SELECT MAX(end_time) FROM maxlabel_org_license WHERE org_id = orgId
  4. today = 今天（Asia/Shanghai）
     startDate = max(today, latestEnd.toLocalDate().minusDays(1))  -- 原到期日；无批次时 = today
     months    = plan.period_months              -- 数量是席位数，不乘周期
     endDate   = startDate.plusMonths(months)
  5. INSERT maxlabel_org_license(
       seats_total = orderItem.count,
       start_time = startDate 00:00:00,
       end_time   = endDate.plusDays(1) 00:00:00,
       source_order_item_id = orderItem.id)
     重复键 uk_source_item → 返回已有行
```

| 场景 | 结果 |
|---|---|
| 首次购买 2 席位 × 1 年，today=10-26 | `start=10-26`，`endDate=次年 10-26`，`end_time=次年 10-27 00:00`，`seats_total=2` |
| 未到期再买 5 席位 × 1 年 | 新行 `start=原 endDate`（重叠到期日），`seats_total=5` |
| 已过期再买 | 新行 `start=today` |
| 同一订单项重复回调 | 唯一键冲突 → 不新增 |
| 订单退款 | 只打 `refunded=1`，不缩短 `end_time`、不回收席位 |

**0 元订单**：沿用 009 —— `payPrice <= 0` 不触发 `updateOrderPaid`；企业计划页同样校验 SKU 价格 > 0。

---

## 4. `maxlabel_org_seat` —— 席位分配（新增）

> 席位**不绑定具体授权批次**（Q71）：其有效性由“企业是否存在未过期批次”决定。
> 撤销后复用同一行，唯一键 `(tenant_id, org_id, member_id)`。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_org_seat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '企业编号，关联 maxlabel_org.id',
  `member_id` bigint NOT NULL COMMENT '会员编号，关联 member_user.id',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 有效 1 已撤销',
  `operator_type` tinyint NOT NULL DEFAULT 1 COMMENT '操作人类型：1 会员 2 系统用户',
  `operator_id` bigint NOT NULL DEFAULT 0 COMMENT '操作人编号（会员或 system_users.id）',
  `assigned_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次分配时间',
  `revoked_time` datetime NULL DEFAULT NULL COMMENT '撤销时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_org_member`(`tenant_id` ASC, `org_id` ASC, `member_id` ASC) USING BTREE,
  INDEX `idx_org_status`(`tenant_id` ASC, `org_id` ASC, `status` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业席位分配';
```

### 容量与并发

```text
可用容量 = max(0, Σ seats_total(企业未过期批次) − COUNT(席位 status=0))
```

- 分配/撤销/转交 OWNER **同一个事务内**锁 `maxlabel_org` 行；
- 分配前二次校验容量，锁内插入/更新；
- 撤销：`status=1, revoked_time=NOW(), operator=当前操作人`；
- 重新分配：复用同一行，`status=0, assigned_time=NOW(), revoked_time=NULL`；
- 旧批次过期但仍有其他未过期批次 → 席位继续有效（Q71）；
- 全部批次过期 → 席位失去效力，但**不批量改状态**，续费后自动恢复。

---

## 5. 既有表的三处加列

> **没有这 3 列，R62/R66/R67 的口径无法落地。** 均为可空/有默认值，旧代码不受影响。

```sql
-- 5.1 计划类别：1 个人 2 企业（默认 1，存量计划全部是个人）
ALTER TABLE `maxlabel_subscription_plan`
  ADD COLUMN `scope` tinyint NOT NULL DEFAULT 1 COMMENT '类别：1 个人 2 企业' AFTER `period_months`;

-- 5.2 商品是否企业专享（公开商城不展示）
ALTER TABLE `product_spu`
  ADD COLUMN `enterprise_only` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否企业专享：0 否 1 是';

-- 公开过滤范围：/product/spu/page、/product/spu/get-detail、/product/spu/list-by-ids（按调用者）、
-- /product/filter/*；促销活动配置（秒杀/拼团/砍价/积分）MUST 拒绝 enterprise_only=1 的商品。
-- 购买/展示链路：/trade/order/settlement-product 一律排除；/trade/cart/add|update-count、
-- /trade/order/settlement 对非企业管理员拒绝；/product/favorite/create 拒绝；
-- /product/favorite/page、/product/browse-history/page 对非企业管理员过滤。

-- 5.3 订单快照企业（企业订单才有值；支付回调按它发放）
ALTER TABLE `trade_order`
  ADD COLUMN `maxlabel_org_id` bigint NULL DEFAULT NULL COMMENT 'MaxLabel 企业编号（企业授权订单快照）';

CREATE INDEX `idx_maxlabel_org` ON `trade_order`(`tenant_id` ASC, `maxlabel_org_id` ASC);
```

**迁移脚本幂等**：MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`。`021` 用
`information_schema.COLUMNS` 判断 + 动态 SQL（或临时存储过程）实现“已存在则跳过”；
`021` 末尾附回查语句。

**计划校验的连带修改**：

- `scope=2` 时 `product_spu.enterprise_only` MUST 为 1；`scope=1` 时 MUST 为 0；
- “同一租户同一时刻只有一条启用计划”改为**按 scope 分别成立**（个人一条、企业一条）；
- 企业 SPU 仍必须是**只含虚拟商品**、SKU 价格 > 0（沿用 009 的 `validateSku`）。

---

## 6. 有效权益解析

### 6.1 解析算法（`GET /member/subscription/get` 内部）

```text
getCurrent(memberId):
  personalEnd = MAX(member_subscription.end_time WHERE member_id = memberId)

  seat = SELECT * FROM maxlabel_org_seat WHERE member_id = memberId AND status = 0
  orgEnd = null
  orgId = null; orgName = null
  if seat != null:
      org = SELECT * FROM maxlabel_org WHERE id = seat.org_id AND status = 0
      if org != null:
          orgEnd = MAX(maxlabel_org_license.end_time
                       WHERE org_id = org.id AND end_time > NOW())
          if orgEnd != null:
              orgId = org.id; orgName = org.name

  effectiveEnd = max(personalEnd, orgEnd)
  active       = effectiveEnd > NOW()
  sourceType   = orgEnd > personalEnd ? 企业(2) : 个人(1)
  endDate      = effectiveEnd.minusDays(1)
  remainingDays= max(0, DAYS.between(today, endDate))
```

- 并列时以**个人**来源为准；
- 只有企业来源时 `sourceType=2`，返回 `orgId/orgName`；
- 只有个人来源时 `sourceType=1`，`orgId/orgName=null`；
- 两个来源都存在时，`orgName` 仍返回（客户端可展示“同时拥有企业席位”），
  `sourceType` 表示**决定 `endDate` 的那个来源**。

### 6.2 能力矩阵（与 009 Q54 对齐）

| 能力 | 未登录 | 登录但无有效权益 | 个人订阅有效 | 企业席位有效 |
|---|---|---|---|---|
| 本地编辑 / 打印 | ✅ | ✅ | ✅ | ✅ |
| 浏览、下载自己的云端模板 | ❌ | ✅ | ✅ | ✅ |
| 上传 / 覆盖模板 | ❌ | ❌（403） | ✅ | ✅ |
| 删除模板 / 取消共享 | ❌ | ✅ | ✅ | ✅ |
| 浏览、下载共享模板 | ❌ | ✅ | ✅ | ✅ |
| 提交共享 | ❌ | ❌（403） | ✅ | ✅ |
| 打印记录上报 | ❌ | ✅ | ✅ | ✅ |

> 闸门在服务端；企业席位与个人订阅在能力上**完全等价**（Q69）。
> **企业中心 / 席位管理是角色能力，不是权益能力**：由 `maxlabel_org_member.role` 决定，
> 与个人订阅或席位是否存在无关；上表不适用该行。

---

## 7. 状态机

```text
企业：启用(0) ⇄ 停用(1)
      停用 → 所有企业来源权益失效；数据保留；可重新启用

成员：存在（加入） ──退出/移出──> 物理删除
      退出时同事务释放席位；OWNER 不能直接退出（须先转交）

批次：未过期 ──时间─> 已过期        （派生，不改状态列）
      退款 → refunded=1（不改变周期与席位）

席位：有效(0) ──撤销──> 已撤销(1) ──重新分配──> 有效(0)
      企业停用 / 全部批次过期 → 解析时视为无效，但 status 仍为 0
```

---

## 8. 菜单与权限（写进 021 迁移 SQL）

沿用 `system_menu` + `system_role_menu`，参考 `013-corporate-transfer-menus.sql` 的幂等写法。
挂在「会员中心」下。

| 菜单 / 按钮 | permission |
|---|---|
| 企业管理（页面） | `member:org:query` |
| 新增 / 修改 / 停用企业 | `member:org:create` / `member:org:update` |
| 新增成员 | `member:org:member:add` |
| 移出成员 | `member:org:member:remove` |
| 调整 ADMIN 角色 | `member:org:member:update-role` |
| 转交 OWNER | `member:org:owner:transfer` |
| 分配席位 | `member:org:seat:assign` |
| 撤销席位 | `member:org:seat:revoke` |
| 查看授权批次 | `member:org:license:query` |
| 会员详情-所属企业（只读） | 复用 `member:org:query` |

> ⚠️ 权限串必须同时插入 `system_menu.permission`，否则 `v-hasPermi` 会把按钮从 DOM 删掉，
> 超管也看不到（013 已记录）。

---

## 9. H2 测试 DDL 同步

| 模块 | 文件 | 改动 |
|---|---|---|
| member | `src/test/resources/sql/create_tables.sql` | 增加 4 张新表；`clean.sql` 增加删除 |
| product | `src/test/resources/sql/create_tables.sql` | `maxlabel_subscription_plan` 加 `scope`；`product_spu` 加 `enterprise_only` |
| trade | `src/test/resources/sql/create_tables.sql` | `trade_order` 加 `maxlabel_org_id` |

H2 语法沿用 009：去掉 `unsigned`、`AUTO_INCREMENT` 改 `GENERATED BY DEFAULT AS IDENTITY`、
正文列用 `clob/text`、`bit` 用 `boolean`。

---

## 10. 与既有表的边界

- `member_user`：只读；企业成员仍是普通会员，模板/打印记录归属不变。
- `member_subscription`：**不改语义**；退款标记方法同时更新企业批次（R72）。
- `trade_order` / `trade_order_item`：只加 `maxlabel_org_id` 一列；其余只读。
- `product_spu` / `product_sku`：只加 `enterprise_only` 一列；计划映射仍只读商品属性。
- `maxlabel_label_template` / `maxlabel_shared_template` / `maxlabel_print_record`：**不改**；
  企业管理员无权读取成员这些数据（FR-319）。
