# 数据模型（009 MaxLabel 会员体系）

**一句话**：新增 **5 张表**——计划映射、订阅流水、私有模板、共享副本、打印记录。
`trade_order`、`product_spu`、`member_user` **一列不改**；订阅是否有效由 `end_time` 与当前时间推导。

所有表都带 `tenant_id`、`creator/create_time/updater/update_time/deleted`（`BaseDO` 约定），
时间语义统一 **Asia/Shanghai**。迁移脚本：`yudao-cloud/sql/mysql/014-maxlabel-member.sql`（幂等）。

---

## 1. `maxlabel_subscription_plan` —— 会员授权商品映射（新增）

> 一条记录 = 一个可售 SKU × 一个周期。会员商品是**一个 SPU、多个 SKU**（月/年）。
> **表归 product-server**（商品配置），通过 `ProductSubscriptionPlanApi` 暴露给 trade；
> **不做软删除**：停用用 `enabled = 0`，避免"删了再加"撞唯一键。

```sql
CREATE TABLE `maxlabel_subscription_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `spu_id` bigint NOT NULL COMMENT '商品 SPU 编号',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 编号（购买单元）',
  `period_months` int NOT NULL COMMENT '授予周期（自然月，如 1 / 12）',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `sort` int NOT NULL DEFAULT 0 COMMENT '排序（越小越靠前）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（保留列，业务不使用）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tenant_sku`(`tenant_id` ASC, `sku_id` ASC) USING BTREE,
  INDEX `idx_spu`(`spu_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 会员授权商品映射（SKU → 周期）';
```

**校验规则**：

- `period_months > 0`；`sku_id` 必须存在且属于 `spu_id`；
- SPU 的配送方式 MUST **只含 `VIRTUAL(3)`**（不含 EXPRESS/PICK_UP），否则拒绝（FR-223）；
- **单一会员 SPU**：同一租户同一时刻只能有一条启用计划所属的 `spu_id`；新增/启用时若已存在
  其他 `spu_id` 的启用计划则拒绝（Q51）；
- 上架前校验 SKU 价格 **> 0**（R49 风险：0 元订单不会走 `updateOrderPaid`）；
- **周期覆盖**：启用计划 SHOULD 包含 `periodMonths=1` 与 `periodMonths=12` 各一条（FR-223）；
  管理页在缺失时**提示**，但不阻止保存——运营可能临时停用单一周期；
- 同一 SKU 只能映射一次；停用后已发放的订阅不受影响。

---

## 2. `member_subscription` —— 订阅授予流水（新增）

> 一行 = 一次支付授予的**一段周期**。续费插新行，不改旧行。
> 有效/过期：`end_time > NOW()`；`end_time` 是本段**最后有效自然日的次日 00:00:00（不含当日）**。
> `endDate`（展示用）= `end_time` 的前一天。退款只打 `refunded` 标记，不改周期（Q48/Assumptions）。

```sql
CREATE TABLE `member_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_id` bigint NOT NULL COMMENT '会员编号，关联 member_user.id',
  `plan_id` bigint NOT NULL COMMENT '计划编号，关联 maxlabel_subscription_plan.id',
  `spu_id` bigint NOT NULL COMMENT '商品 SPU 快照',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU 快照',
  `period_months` int NOT NULL COMMENT '本段授予月数（plan.period_months × 数量）',
  `start_time` datetime NOT NULL COMMENT '本段开始自然日 00:00:00（=max(今天, 上段 end_time 的前一天 = 原到期日)）',
  `end_time` datetime NOT NULL COMMENT '本段最后有效自然日的次日 00:00:00（不含当日）',
  `source_order_id` bigint NOT NULL COMMENT '来源订单编号',
  `source_order_item_id` bigint NOT NULL COMMENT '来源订单项编号（幂等键）',
  `source_order_no` varchar(64) NOT NULL COMMENT '来源订单号快照（后台展示）',
  `refunded` bit(1) NOT NULL DEFAULT b'0' COMMENT '来源订单或会员订单项是否已全额退款（仅标记，不回收）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_source_item`(`tenant_id` ASC, `source_order_item_id` ASC) USING BTREE,
  INDEX `idx_member_end`(`tenant_id` ASC, `member_id` ASC, `end_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 会员订阅授予流水';
```

### 发放算法（事务内）

```text
grant(memberId, orderItem, plan):
  1. SELECT id FROM member_user WHERE id = memberId FOR UPDATE   -- 并发锚点
  2. latestEndTime = SELECT MAX(end_time) FROM member_subscription
                    WHERE member_id = memberId   -- 排他边界，已是"最后有效日+1天 00:00"
  3. today     = 今天（Asia/Shanghai）
     startDate = max(today, latestEndTime.toLocalDate().minusDays(1))  -- 原到期日；无订阅时 = today
     -- ⚠️ end_time 是"最后有效日 + 1 天"；取前一天才是原到期日，否则每次续费多 1 天（SC-078）
     months    = plan.period_months × orderItem.count
     endDate   = startDate.plusMonths(months)           -- 月末按 plusMonths 钳制
                                                            （1/31 + 1 月 → 2/28 或 2/29）
  4. INSERT member_subscription(..., start_time = startDate 00:00:00,
       end_time = endDate.plusDays(1) 00:00:00, source_order_item_id = orderItem.id)
     重复键 uk_source_item → 直接返回已有行（幂等）
```

| 场景 | 结果 |
|---|---|
| 首次购买 1 个月，`today = 10-20` | `start=10-20 00:00:00`，`endDate=11-20`，`end_time=11-21 00:00:00` |
| 未到期（上段 `endDate=11-20`，`end_time=11-21 00:00`）再买 1 年 | `start=11-20 00:00:00`（原到期日，重叠当天），`endDate=次年 11-20`，`end_time=次年 11-21 00:00:00` |
| 已过期（上段 `endDate=09-01`，`end_time=09-02 00:00`）再买 1 个月 | `start=today 00:00:00`，`endDate=today+1 月`，`end_time=次日 00:00:00` |
| 同一订单项重复回调 | 唯一键冲突 → 不新增 |
| `count=2` 买 1 年 SKU | 本段授予 24 个月 |

`remainingDays = max(0, ChronoUnit.DAYS.between(today, endDate))`（**不含今天**；
10-20 到次年 10-20 为 365；到期日当天为 0，前端显示「今天到期」）。
到期判断用 `end_time > NOW()`：到期日当天全天有效，次日 00:00 起过期。

**退款**：整单全额退款走 `TradeOrderHandler.afterCancelOrder`，按 `source_order_id` 标记；
会员订单项单独全额退款走 `afterCancelOrderItem`，按 `source_order_item_id` 标记。
**不缩短 `end_time`**。后台展示"来源订单已退款"供运营处理。

**0 元订单**：不在本期（`payPrice <= 0` 不触发 `updateOrderPaid`）；计划页校验 SKU 价格 > 0。

---

## 3. `maxlabel_label_template` —— 会员私有云端模板（新增）

> 正文直接存 `.msdx` JSON 原文；**不**使用 `infra_file` 的公开 URL（R51）。

```sql
CREATE TABLE `maxlabel_label_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_id` bigint NOT NULL COMMENT '所属会员，关联 member_user.id',
  `name` varchar(255) NOT NULL COMMENT '模板名称',
  `content` longtext NOT NULL COMMENT '模板正文（.msdx JSON 原文）',
  `size_bytes` int unsigned NOT NULL COMMENT '正文字节数',
  `content_hash` char(64) NOT NULL COMMENT '正文 SHA-256（重复上传/排障用）',
  `width_mm` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标签宽度（毫米，检索用）',
  `height_mm` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标签高度（毫米，检索用）',
  `category` varchar(64) NULL DEFAULT '' COMMENT '分类（自定义，检索用）',
  `keywords` varchar(255) NULL DEFAULT '' COMMENT '关键词（逗号分隔）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_member_time`(`tenant_id` ASC, `member_id` ASC, `create_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 会员私有云端模板';
```

**约束**：

| 规则 | 值 | 依据 |
|---|---|---|
| 单文件上限 | 16 MB | 桌面端既有校验；服务端再校验 |
| 单会员数量 | ≤ 1000 | 现有云模板配额 |
| 单会员总量 | ≤ 256 MB | 现有云模板配额 |
| 归属 | `member_id` 必须等于登录会员 | FR-232/FR-251 |
| 敏感字段 | 桌面端剥离 DB/RFID 口令；服务端按 JSON 解析再剥一次 | FR-231 |

**同名处理**：允许同名；桌面端在上传前询问"覆盖"还是"另存"。覆盖 = `UPDATE content/...`，
另存 = 新行。

---

## 4. `maxlabel_shared_template` —— 平台共享副本（新增）

> 共享不是给私有模板打标记，而是**独立副本**：内容经过服务端清洗，审核状态独立，
> 删除/下架不影响原模板（R52）。

```sql
CREATE TABLE `maxlabel_shared_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `source_template_id` bigint NOT NULL COMMENT '来源私有模板编号',
  `publisher_member_id` bigint NOT NULL COMMENT '发布会员编号',
  `name` varchar(255) NOT NULL COMMENT '模板名称',
  `content` longtext NOT NULL COMMENT '清洗后的模板正文',
  `size_bytes` int unsigned NOT NULL COMMENT '正文字节数',
  `width_mm` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标签宽度（毫米）',
  `height_mm` decimal(10,2) NOT NULL DEFAULT 0 COMMENT '标签高度（毫米）',
  `category` varchar(64) NULL DEFAULT '' COMMENT '分类',
  `keywords` varchar(255) NULL DEFAULT '' COMMENT '关键词',
  `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态：0 待审核 1 已通过 2 已驳回 3 已下架',
  `reject_reason` varchar(255) NULL DEFAULT NULL COMMENT '驳回原因（驳回时必填）',
  `audit_user_id` bigint NULL DEFAULT NULL COMMENT '审核人，关联 system_users.id',
  `audit_time` datetime NULL DEFAULT NULL COMMENT '审核时间',
  `published_time` datetime NULL DEFAULT NULL COMMENT '通过并公开的时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_source`(`tenant_id` ASC, `source_template_id` ASC) USING BTREE,
  INDEX `idx_audit`(`tenant_id` ASC, `audit_status` ASC, `published_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 平台共享模板（清洗后的副本）';
```

### 状态机

```text
私有 ──提交──> 待审核(0) ──通过──> 已通过(1) ──下架──> 已下架(3)
                     │                                   │
                     └──驳回(2)──> 会员可修改后重新提交 ──┘
会员取消发布 / 删除源模板 ──> 删除共享副本（软删）
```

- **只能通过**（不能跳过审核直接公开，Q50）；
- 驳回必须写 `reject_reason`；
- 重新提交时**复用同一行**（`uk_source`），把状态改回待审核并清空 `reject_reason`；
- 下架后已下载的本地副本不受影响（FR-235 的边界）。

### 服务端清洗（提交时）

从 msdx JSON 中删除：

| 路径 | 原因 |
|---|---|
| `doc.connections[*].password` | 数据库口令 |
| 对象中 RFID 的 `accessPwd` / `killPwd` | 设备读写/销毁口令 |
| `doc.datasets[*].rows` | 客户业务数据行；保留列定义/数据源结构 |

保留：页面尺寸、对象、样式、脚本、图片、数据源**结构**（不含口令与数据行）。

---

## 5. `maxlabel_print_record` —— 打印记录（新增）

> 只存结构化字段，**不含 `dataSnapshot`**；永久保留（Q53）。

```sql
CREATE TABLE `maxlabel_print_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `member_id` bigint NOT NULL COMMENT '会员编号',
  `device_id` char(36) NOT NULL COMMENT '桌面端随机设备标识（UUID，非机器指纹）',
  `record_uid` char(36) NOT NULL COMMENT '桌面端生成的记录 UUID（幂等）',
  `print_time` datetime NOT NULL COMMENT '打印时间（桌面端时间）',
  `template_title` varchar(255) NOT NULL COMMENT '模板名称',
  `mode` varchar(16) NOT NULL COMMENT '打印方式：driver / command',
  `count` int NOT NULL COMMENT '计划标签张数',
  `copies` int NOT NULL COMMENT '单签拷贝数',
  `physical_count` bigint NULL DEFAULT NULL COMMENT '实际打印张数（可空）',
  `sent_count` bigint NULL DEFAULT NULL COMMENT '已发送张数（可空）',
  `status` varchar(16) NOT NULL COMMENT '状态：completed/submitted/partial/failed/canceled/unknown',
  `printer` varchar(255) NOT NULL DEFAULT '' COMMENT '打印机名称',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_record`(`tenant_id` ASC, `member_id` ASC, `record_uid` ASC) USING BTREE,
  INDEX `idx_member_time`(`tenant_id` ASC, `member_id` ASC, `print_time` ASC) USING BTREE
) COMMENT = 'MaxLabel 会员打印记录（不含业务数据快照）';
```

**上报字段白名单**：`print_time / template_title / mode / count / copies / physical_count /
sent_count / status / printer / device_id / record_uid`。
**明确不接受**：`dataSnapshot`、模板正文、数据源连接、标签内容。

---

## 6. 会员云端能力矩阵（Q54）

| 能力 | 未登录 | 登录但无有效订阅 | 有效订阅 |
|---|---|---|---|
| 本地编辑 / 打印 | ✅ | ✅ | ✅ |
| 浏览、下载自己的云端模板 | ❌ | ✅ | ✅ |
| 上传 / 覆盖自己的模板 | ❌ | ❌（403） | ✅ |
| 删除自己的模板 / 取消共享 | ❌ | ✅ | ✅ |
| 浏览、下载共享模板 | ❌ | ✅ | ✅ |
| 提交共享 | ❌ | ❌（403） | ✅ |
| 打印记录上报 | ❌（本地保留） | ✅ | ✅ |
| 商城浏览 / 购买 | ❌（先登录） | ✅ | ✅ |

> 闸门在**服务端**：`member_subscription` 当前有效才允许写；桌面端只做提示，不作为唯一判断（原则 III）。

---

## 7. 菜单与权限（迁移 SQL 同步）

沿用 `system_menu` + `system_role_menu`；参考 `013-corporate-transfer-menus.sql` 的幂等写法。

| 菜单 / 按钮 | component / permission |
|---|---|
| 会员详情-订阅（页签） | 无菜单；接口 `member:subscription:query` |
| 会员详情-标签模板（页签） | 无菜单；接口 `member:cloud-template:query` |
| 会员详情-打印记录（页签） | 无菜单；接口 `member:print-record:query` |
| 共享模板审核（页面） | `member/template/review/index`；`member:shared-template:query` |
| 审核通过 / 驳回 / 下架 | `member:shared-template:audit` / `member:shared-template:offline` |
| 会员授权商品（页面） | `member/subscription/plan/index`；`product:subscription-plan:query` |
| 计划新增 / 修改（含停用） | `product:subscription-plan:create/update` |

> ⚠️ 权限字符串必须**同时**插入 `system_menu.permission`，否则即使超管也会出现
> "接口能通、按钮不显示"（013 迁移头部已记录该坑）。

## 8. 与既有表的边界

- `trade_order` / `trade_order_item`：只读，不加列；用 `id / orderId / userId / spuId / skuId / count`。
- `member_user`：只读，用于行锁与身份展示；不新增字段。
- `product_spu` / `product_sku`：只读，用于计划映射校验与商品展示。
- `infra_file`：**不使用**（公开 URL + 无归属）。
- `system_operate_log`：可选，关键运营动作通过 `@LogRecord` 留痕。
