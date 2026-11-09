# 数据模型（017 代客下单）

**一句话**：不建新表。订单表增加 **1 列 `agent_user_id`**，订单来源增加 **1 个枚举值 + 1 条字典**，
新增 **2 个权限点**；售后单与订单日志沿用现有表，只增加操作类型。

迁移脚本：`yudao-cloud/sql/mysql/036-agent-order.sql`（幂等，先查 `information_schema` / 菜单表）。

---

## 1. `trade_order.agent_user_id` —— 代下单人（新增列）

```sql
ALTER TABLE `trade_order`
  ADD COLUMN `agent_user_id` bigint NULL DEFAULT NULL
    COMMENT '代客下单的后台用户编号；NULL = 客户自助下单'
    AFTER `user_id`;

CREATE INDEX `idx_agent_user_id` ON `trade_order` (`agent_user_id`);
```

**语义**：

| 值 | 含义 |
|---|---|
| `NULL` | 客户在自己的账号下自助下单（历史订单与现有流程保持不变） |
| 非空 | 该订单由该后台用户（`system_users.id`）在管理端代客创建 |

**规则**：

- 只在 `createOrderByAdmin` 写入；后台 MUST NOT 提供修改/清除入口（FR-457）。
- 不冗余昵称：展示时用 `AdminUserApi` 解析当前昵称；用户不存在时前端显示 `后台用户#{id}`（FR-458）。
- 该列是「代客订单」的唯一权威判据：列表筛选、C 端标识、取消/退款守卫都用它，不用订单日志反推。
- 索引用于列表筛选与后续按运营统计；`NULL` 值很多时 MySQL 仍可走索引，数据量小，无需额外优化。

**列表/统计筛选**（R121）：

```text
agentCreated = true  → agent_user_id IS NOT NULL
agentCreated = false → agent_user_id IS NULL
agentCreated = null  → 不拼条件
```

`selectPage` 与 `selectOrderSummaryGroupByRefundStatus` 共用同一个 `applyAgentCreatedFilter`。

---

## 2. 订单来源：`TerminalEnum.ADMIN_AGENT(40)`

```java
ADMIN_AGENT(40, "后台代客"),
```

**字典**（`system_dict_data`，`dict_type = 'terminal'`）：

| label | value | 说明 |
|---|---|---|
| 后台代客 | 40 | 运营在管理端代客户创建 |

**规则**：

- 代客订单落库时 `terminal = 40`（覆盖 `getTerminal()` 读到的 UNKNOWN）。
- 管理端「订单来源」筛选使用 `@InEnum(TerminalEnum)`，枚举不加会导致筛选 400；
  下拉展示使用 `terminal` 字典，字典不加会显示空白 —— **两处必须同时补**（R118 / K6）。
- C 端不展示订单来源，无需改门面站。

---

## 3. 订单日志：新增操作类型

`TradeOrderOperateTypeEnum` 增加：

```java
ADMIN_AGENT_CREATE(3, "运营代客下单"),
```

- 由 `TradeOrderUpdateServiceImpl#createOrderByAdmin` 上的 `@TradeOrderLog` 记录。
- 日志的 `user_id` / `user_type` 由现有切面取当前登录后台用户，天然记录操作人。
- 客户自助下单继续用 `MEMBER_CREATE(1)`，行为不变。

`AfterSaleOperateTypeEnum` 增加：

```java
ADMIN_AGENT_CREATE(13, "运营代客发起售后"),
```

- 由 `AfterSaleServiceImpl#createAfterSaleByAdmin` 上的 `@AfterSaleLog` 记录。
- 售后单的 `user_id` 为客户，`creator`（BaseDO）为当前后台用户 —— 两者共同构成「谁替谁发起」。

---

## 4. 售后单：不加列

| 字段 | 代客售后时的值 | 用途 |
|---|---|---|
| `trade_after_sale.user_id` | 目标客户 | 客户在 C 端「我的售后」能看到该单 |
| `trade_after_sale.creator` | 后台操作人（BaseDO 自动填充） | 追溯是谁代发起的 |
| `trade_after_sale_log.type` | 13（ADMIN_AGENT_CREATE） | 售后日志明确写「运营代客发起售后」 |

**不改状态机**：售后仍走 `APPLY → SELLER_AGREE/WAIT_REFUND → …` 现有流转；
在线支付走现有 `payRefundApi`，对公/线下由财务线下打款（R120）。

---

## 5. 接口字段（VO）

### 5.1 管理端订单

| VO | 新增字段 | 类型 | 说明 |
|---|---|---|---|
| `TradeOrderBaseVO` | `agentUserId` | `Long` | 列表/详情共用；NULL = 自助 |
| `TradeOrderPageItemRespVO` | `agentUserName` | `String` | 由 `AdminUserApi` 批量解析；不落库 |
| `TradeOrderDetailRespVO` | `agentUserName` | `String` | 同上；详情另展示 `createTime` 作为代下单时间 |
| `TradeOrderPageReqVO` | `agentCreated` | `Boolean` | 全部 / 仅代客 / 仅自助 |

### 5.2 C 端订单

| VO | 新增字段 | 类型 | 说明 |
|---|---|---|---|
| `AppTradeOrderPageItemRespVO` | `agentCreated` | `Boolean` | 列表显示「客服代下单」标签 |
| `AppTradeOrderDetailRespVO` | `agentCreated` | `Boolean` | 详情显示标识；前端据此隐藏取消/退款；服务端同步拒绝 |

> `agentCreated` 由 `agentUserId != null` 派生，不暴露运营身份（FR-459/460）。

### 5.3 管理端代客下单请求 / 响应

```text
POST /admin-api/trade/order/agent-settlement
AdminAgentOrderSettlementReqVO {
  userId: Long,                    // 必填，目标客户
  items: [{ skuId: Long, count: Integer }],  // 必填
  addressId: Long?,                // 实物单需要；虚拟单可空
}
→ AppTradeOrderSettlementRespVO    // 复用现有结算响应（商品、价格、配送方式、地址）

POST /admin-api/trade/order/agent-create
AdminAgentOrderCreateReqVO {
  userId: Long,
  items: [{ skuId, count }],
  addressId: Long?,
  userRemark: String? (≤200),
  payChannelCode: String?,         // 只允许 "offline"；不传 = 在线支付
}
→ { orderId, payOrderId?, payPrice, orderStatus }

POST /admin-api/trade/order/agent-pay
AdminAgentOrderPayReqVO { orderId: Long, channelCode: "wx_native" | "alipay_pc" }
→ { status, displayMode, displayContent }   // displayContent = 二维码内容或支付链接

GET /admin-api/trade/order/agent-pay-status?orderId=
→ { orderId, orderStatus, payStatus, payOrderId }
```

**服务端强制**：

- `couponId` / `pointStatus` 由服务端固定为 `null` / `false`；
- `customization` / `invoice` 不出现在管理端 VO 中；
- `payChannelCode` 只接受 `offline`，其余值走现有 `ORDER_CREATE_FAIL_PAY_CHANNEL_CODE_INVALID`；
- `agent-pay` 只接受 `wx_native` / `alipay_pc`，拒绝 `mock` / `wallet` / 其他；
- 目标客户必须存在且 `status = 0`（`CommonStatusEnum.ENABLE`）；
- 商品包含隐藏定制 SKU/SPU（`CustomizationConstants.HIDDEN_SKU_ID` / `HIDDEN_SPU_ID`）时，
  返回 `AGENT_ORDER_CUSTOMIZATION_NOT_SUPPORTED`，不要落到通用价格错误；
- 实物单没有可选收货地址时返回 `AGENT_ORDER_ADDRESS_REQUIRED`，提示客户先在 C 端添加。

### 5.4 管理端代客售后

```text
POST /admin-api/trade/after-sale/agent-create
AppAfterSaleCreateReqVO {          // 复用 C 端 VO
  orderItemId: Long,
  way: Integer,                    // 1 退款 / 2 退货退款
  refundPrice: Integer,
  applyReason: String,
  applyDescription: String?,
  applyPicUrls: List<String>?,
}
→ Long  // 售后编号
```

- 只接受 `agent_user_id != null` 的订单；
- 复用现有校验（未重复申请、金额上限、已支付、退货退款需已发货等）；
- 跳过「用户端不允许退款」的三类守卫（代客 / 企业授权 / 个人订阅），由权限点与售后审核把关。

---

## 6. 权限点与菜单

| 权限串 | 名称 | 类型 | 父级 | 用途 |
|---|---|---|---|---|
| `trade:order:agent-create` | 代客下单 | 按钮(3) | 「订单列表」菜单（`trade:order:query` 的 `parent_id`） | 入口、结算/创建/支付/轮询接口 |
| `trade:after-sale:agent-create` | 代客发起售后 | 按钮(3) | 「售后订单」菜单（`trade:after-sale:query` 的 `parent_id`） | 订单详情的「代客发起售后」入口与接口 |

> 与实际售后审核/退款权限（`trade:after-sale:agree` / `refund`）**分离**；两道关（Q133）。
> 菜单 SQL 用子查询取 `parent_id`，不写死 270 / 267（照 013 的做法）。

---

## 7. 错误码（建议，落在 `1_011_012_xxx` 段）

| 错误码 | 常量 | 文案 |
|---|---|---|
| `1_011_012_000` | `AGENT_ORDER_CUSTOMER_NOT_FOUND` | 目标客户不存在 |
| `1_011_012_001` | `AGENT_ORDER_CUSTOMER_DISABLED` | 目标客户已被禁用，无法代客下单 |
| `1_011_012_002` | `AGENT_ORDER_PICKUP_NOT_SUPPORTED` | 该商品仅支持门店自提，暂不支持代客下单 |
| `1_011_012_003` | `AGENT_ORDER_CHANNEL_NOT_SUPPORTED` | 代客下单仅支持微信扫码、支付宝扫码与对公转账 |
| `1_011_012_004` | `AGENT_ORDER_PAY_FAIL_NOT_AGENT_ORDER` | 该订单不是代客订单，无法在后台发起支付 |
| `1_011_012_005` | `AGENT_ORDER_PAY_FAIL_STATUS` | 订单当前状态不可发起支付 |
| `1_011_012_006` | `AGENT_ORDER_CANCEL_NOT_SUPPORTED` | 代客订单不支持客户自行取消，请联系客服 |
| `1_011_012_007` | `AGENT_ORDER_AFTER_SALE_NOT_SUPPORTED` | 代客订单不支持客户自助退款，请联系客服处理 |
| `1_011_012_008` | `AGENT_AFTER_SALE_FAIL_NOT_AGENT_ORDER` | 仅代客订单可由运营代客户发起售后 |
| `1_011_012_009` | `AGENT_ORDER_CUSTOMIZATION_NOT_SUPPORTED` | 印刷定制商品不支持代客下单，请让客户在 C 端使用定制向导 |
| `1_011_012_010` | `AGENT_ORDER_ADDRESS_REQUIRED` | 请选择客户的收货地址；客户没有地址时，请先在 C 端添加 |

> 具体号段在实现时以现有文件为准；新增前先确认未被占用。

---

## 8. 状态与约束

- **订单状态机不变**：待支付 → 待发货 → 待收货 → 已完成 / 已取消；代客订单只是多了归属与来源。
- **代客订单可支付**：客户可在 C 端支付，也可由运营在代客下单页发起扫码支付；两者最终走同一支付单。
- **代客订单不可由客户取消**：C 端无入口，`cancelOrderByMember` 拒绝；运营仍可用现有后台取消（仅未支付）。
- **代客订单不可自助退款**：C 端无入口，会员售后拒绝；运营用 `agent-create` 走后台售后。
- **落地顺序**：`036` → 后端 → 管理端 → 门面站；`agent_user_id` 未执行时后端会报 `Unknown column`（K1）。
