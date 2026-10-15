# 数据模型：订单开票

Phase 1 产出。两张新表都落在 `yudao-module-trade-server`，**既有表结构一律不动**（R26）。
DDL 风格照 `trade_after_sale`（`sql/mysql/mall-2026-04-18.sql:1078-1113`）：
`utf8mb4_bin` 排序规则、列注释、`tenant_id`、`creator/create_time/updater/update_time/deleted`。
DO 一律 `extends BaseDO`（不含 `tenant_id` 字段 —— 与既有 trade DO 一致）。

---

## 1. `trade_order_invoice` —— 开票申请（**新增**，与订单 1:1）

```sql
CREATE TABLE `trade_order_invoice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '申请编号',
  `order_id` bigint UNSIGNED NOT NULL COMMENT '订单编号',
  `order_no` varchar(32) NOT NULL COMMENT '订单号（下单时抄一份，仅用于展示与排查）',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '会员编号',
  `type` tinyint NOT NULL COMMENT '发票类型：1 电子普通发票，2 电子增值税专用发票',
  `status` int NOT NULL DEFAULT 0 COMMENT '状态：0 待开票，10 已开票，20 已作废',
  `amount` int NOT NULL COMMENT '开票金额，单位：分（= 申请时的订单实付金额快照）',
  `title_name` varchar(255) NOT NULL COMMENT '抬头名称（公司名或个人名）',
  `tax_no` varchar(64) NULL DEFAULT NULL COMMENT '纳税人识别号',
  `title_address` varchar(255) NULL DEFAULT NULL COMMENT '注册地址（专票必填）',
  `title_phone` varchar(64) NULL DEFAULT NULL COMMENT '注册电话（专票必填）',
  `bank_name` varchar(255) NULL DEFAULT NULL COMMENT '开户行及账号（专票必填）',
  `invoice_file_url` varchar(255) NULL DEFAULT NULL COMMENT '发票文件地址（Q31：只留最新一份）',
  `issue_time` datetime NULL DEFAULT NULL COMMENT '开票时间',
  `issue_user_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '开具人（后台操作者）',
  `cancel_time` datetime NULL DEFAULT NULL COMMENT '作废时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_id`(`order_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) COMMENT = '交易订单开票申请';
```

**关键约束与理由**：

| 列 / 索引 | 理由 |
|---|---|
| `uk_order_id`（**唯一**） | FR-160「一个订单最多一条申请」由数据库保证 —— 并发重复提交也插不进第二条（Q28 允许补开，所以"同一订单再申请一次"是真实可达的路径，不是假想） |
| `amount` | FR-161：取**申请时刻的订单实付金额快照**，由服务端从订单读取（MUST NOT 让客户填、MUST NOT 信前端传的金额） |
| `title_*` 五列 | spec 的 Key Entities：**同一条抬头两种票都能用**，专票字段是普票的超集。所以这里存的是"这次要开的那张票"的字段，普票时后四列可为空 |
| `invoice_file_url`（单列） | Q31：重新上传**覆盖**，只留最新一份；不做附件表、不做历史 |
| `issue_user_id` / `issue_time` | FR-170：记录"谁、什么时候"开的票（发票是财务动作） |
| `order_no`（冗余） | 与 `trade_order` 上的订单号一致，**下单时抄一份、此后不再更新**（订单号不会变）。沿用 `trade_after_sale.order_no` 的同一做法，让后台列表与排查不必回查订单表 |
| **没有** `paid` / `pay_time` 列 | R30：**"是否已支付"不在这里存第二份** —— 它由订单裁决，后台列表 join 订单过滤 |

### 1.1 状态机

```text
                  ┌── 下单时勾选开票 ──┐
                  │                    ├──► 待开票(0) ──(后台开具并上传文件)──► 已开票(10)
                  └── 订单详情补开 ────┘        │                                  │
                                               │                                  └──(重新上传，Q31)──► 已开票(10) 覆盖
                                               │
                                               └──(订单取消 / 全额退款取消)──► 已作废(20) [终态]
```

| 流转 | 触发点（代码位置） | 依据 |
|---|---|---|
| → 待开票(0) | `TradeOrderUpdateServiceImpl.createOrder`（下单，**同一事务**）；`AppTradeOrderInvoiceController.create`（补开） | FR-159 / FR-163 |
| 待开票(0) → 已开票(10) | 后台 `PUT /admin-api/trade/order-invoice/issue` | FR-170 |
| 待开票(0) → 已作废(20) | **`TradeInvoiceOrderHandler.afterCancelOrder`**（既有扩展点，R28）—— 覆盖 C 端取消、系统超时取消、售后全额退款取消三条路 | Q30 / FR-184 |
| 已开票(10) → **不动** | 同上（handler 只处理 `status = 0`） | Q30「已开具的不受影响」/ FR-182 |

**没有"待支付"这一态**（R30）：申请在**下单时就存在**（客户可能还没付款），
"是否已支付"由订单裁决。后台「待开票」列表 = `status = 0 AND 订单已支付`。

## 2. `trade_invoice_title` —— 发票抬头簿（**新增**，会员可维护多条）

```sql
CREATE TABLE `trade_invoice_title` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '抬头编号',
  `user_id` bigint UNSIGNED NOT NULL COMMENT '会员编号',
  `title_name` varchar(255) NOT NULL COMMENT '抬头名称（公司名或个人名）',
  `tax_no` varchar(64) NULL DEFAULT NULL COMMENT '纳税人识别号',
  `title_address` varchar(255) NULL DEFAULT NULL COMMENT '注册地址',
  `title_phone` varchar(64) NULL DEFAULT NULL COMMENT '注册电话',
  `bank_name` varchar(255) NULL DEFAULT NULL COMMENT '开户行及账号',
  `default_status` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否默认',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) COMMENT = '会员发票抬头';
```

**关键约束与理由**：

| 项 | 理由 |
|---|---|
| **票种列 `type`**（**Q36 加的，2026-10-15**） | 一条抬头**只服务一种票**（票种分开管理）；**必填仍是「名称 + 税号」两项**（Q37）。原来"不绑票种、一条两用"的设计**已被 Q36 推翻**（所有者要求"普票与专票分开管理"）。`default_status` 的唯一性也随之从"按会员"改成"**按会员 + 票种**" |
| 五列全可空（除名称） | 只开普票的客户**不该被迫填开户行**；缺项在**选专票时**才被拒（校验在 `InvoiceValidator`，见 §4） |
| `default_status` | US23 场景 2：可设一条默认，结算页预选。**唯一性在服务层保证**（设新的默认时清掉旧的）—— 与 `member_address` 的既有做法一致，不加数据库唯一约束（部分索引在 MySQL 上表达不了"每用户至多一条默认且可为零条"） |
| **没有 `user_id` 相关的唯一索引** | 同一会员允许存在两条内容相同的抬头（客户自己愿意，无业务危害），不做去重 |

## 3. `trade_order`（既有表，**一个字节都不动**）

开票申请通过 `order_id` 与它 1:1 关联。三个既有接口**只做加法**（R32）：

| 接口 | 新增内容 | 数据来源 |
|---|---|---|
| `GET /app-api/trade/order/get-detail` | `invoice` 对象（状态、票种、抬头快照、金额、文件 URL、时间） | 一次按 `order_id` 查询 |
| `GET /app-api/trade/order/page` | 每笔订单一个 `invoiceStatus` | **一次 `IN (orderIds)` 批量查询**（R3：不许 N+1） |
| `POST /app-api/trade/order/create` | 入参 `invoice` 子对象 | 同事务写入 `trade_order_invoice` |

**"未申请开票"的统一表达**：**不存在对应的 `trade_order_invoice` 行**。
于是 FR-179（历史订单视为未申请）**天然成立** —— 一行历史数据都不用补。

## 4. 校验规则（`InvoiceValidator`，服务端唯一一处）

| 规则 | 内容 | 依据 |
|---|---|---|
| **必填矩阵** | **只有两项必填：`title_name` + `tax_no`**（两种票一致）。地址 / 电话 / 开户行及账号一律**选填**（填了要合法） | **Q37**（2026-10-15，取代 Q27/Q36 的"专票五项全填"）/ FR-155 |
| 税号格式 | 长度 15 / 18 / 20 位，仅数字与大写字母；**统一社会信用代码为 18 位**（含数字与字母），不做校验位计算 | FR-155 / spec 的 Edge Cases |
| 长度上限 | 名称 ≤ 255、税号 ≤ 64、地址 ≤ 255、电话 ≤ 64、开户行 ≤ 255（与列宽一致） | 本文件 |
| 前后空格 | 提交时 trim；trim 后为空视为未填 | —— |
| 文件扩展名白名单 | `pdf` / `jpg` / `jpeg` / `png`（**服务端**校验，前端 `accept` 不算数） | FR-171 / R31 |
| 金额 | **服务端从订单读**（`payPrice`），MUST NOT 取请求里的金额 | FR-161 |
| **金额必须 > 0** | 全额券抵扣、实付 0 元的订单**没有可开票金额**：**补开时拒绝**；**下单时忽略 `invoice` 入参**（订单照下）—— 不因为一个本来就开不了票的选择让客户下不了单（FR-181 的精神）。前端在 0 元时**根本不渲染发票卡片** | spec Edge Case |

**同一份规则在三个入口生效**：下单、补开、抬头簿保存 —— 后者的"必填"只要求 `title_name`
（因为抬头可以不完整，缺项在选专票时暴露）。

## 5. 枚举（`yudao-module-trade-api`，与 `AfterSaleStatusEnum` 同处）

```java
// TradeOrderInvoiceStatusEnum
WAITING(0, "待开票")
ISSUED(10, "已开票")
CANCELED(20, "已作废")

// InvoiceTypeEnum
E_INVOICE_NORMAL(1, "电子普通发票")   // 简称「普票」
E_INVOICE_SPECIAL(2, "电子增值税专用发票")  // 简称「专票」
```

放 `trade-api` 的理由：与既有 `AfterSaleStatusEnum` / `AfterSaleWayEnum` 同一处，
server 与前端契约共用一套取值。**不支持纸质与邮寄**（Q27），故没有"配送方式""收件地址"一类的枚举或字段。

## 6. 关系总览

```text
member（会员）                                    trade（交易）
┌──────────────┐                          ┌─────────────────────────────┐
│ member_user  │                          │ trade_order                 │
└──────┬───────┘                          │  （既有，不改）              │
       │ user_id                          └──────────┬──────────────────┘
       │                                             │ order_id（1:1，唯一）
       │  ┌───────────────────────────┐              ▼
       └─►│ trade_invoice_title       │   ┌─────────────────────────────┐
          │  发票抬头簿（新增）        │   │ trade_order_invoice         │
          │  多条 + 默认              │   │  开票申请（新增）            │
          └───────────────────────────┘   │  · 票种 + 抬头字段快照       │
                     │  （客户在这里选一条）│  · 状态 待开票/已开票/已作废  │
                     └──── 值随请求提交 ─►│  · invoice_file_url（文件）   │
                          （R27：快照）    └──────────────┬──────────────┘
                                                        │ 文件本体
                                                        ▼
                                          既有对象存储（infra 文件服务，公网可读）
```

**两条不存在的连线**（都是刻意的）：

- 申请**不引用**抬头簿的任何一行（R27）：存的是快照，所以改 / 删抬头**结构上不可能**影响历史申请。
- trade **不调用** member 的任何 API（R25/R27）：本特性**不需要任何跨模块 RPC**，
  `RpcConfiguration` 一行不改。
