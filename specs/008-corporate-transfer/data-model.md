# 数据模型（008 对公转账）

**一句话**：`trade_order` **一个字节不动**、状态机**不加值**；新增**一张 1:1 子表**
承载"是不是对公单 + 确认痕迹"，另给 `trade_config` 加 4 个配置字段。
理由见 [research.md](./research.md) R42 / R44。

---

## 1. `trade_order_offline_payment` —— 对公转账信息（**新增**，与订单 1:1）

> **下单时插入**（只有选了「对公转账」才插）；**确认收款时填上**后半部分字段。
> ⚠️ 这张表**同时回答两个问题**：① 本单是不是对公转账（**有没有这一行**）
> ② 财务凭哪笔流水确认的（**行的内容**）。

```sql
CREATE TABLE `trade_order_offline_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `order_id` bigint UNSIGNED NOT NULL COMMENT '订单编号，关联 trade_order.id',
  `bank_serial_no` varchar(64) NULL DEFAULT NULL COMMENT '银行流水号（**确认收款时必填**，Q40④）',
  `payer_name` varchar(128) NULL DEFAULT NULL COMMENT '打款方名称（选填，开票/对账时有用）',
  `paid_time` datetime NULL DEFAULT NULL COMMENT '到账时间（选填，财务照流水填）',
  `remark` varchar(255) NULL DEFAULT NULL COMMENT '备注（选填）',
  `confirm_user_id` bigint UNSIGNED NULL DEFAULT NULL COMMENT '确认人，关联 system_users.id（财务动作要留痕）',
  `confirm_time` datetime NULL DEFAULT NULL COMMENT '确认时间；**为空即"还没确认"**',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_id`(`order_id` ASC) USING BTREE
) COMMENT = '对公转账信息（订单的线下收款记录）';
```

**关键约束与理由**：

| 列 / 索引 | 理由 |
|---|---|
| **`uk_order_id`（唯一）** | 一个订单最多一条对公信息 —— 与 `trade_order_invoice` 同一做法，**由数据库兜底**，并发也不会插出第二条 |
| `confirm_time` 为空 = 未确认 | 不另设"状态"列：**"有没有值"本身就是状态**，少一个需要维护的字段（与 007 用 `delivery_types` 的取值表达"虚拟商品"同一个思路） |
| `bank_serial_no` 可为空、但**确认时必填** | 空是**下单那一刻**的正常状态（还没确认）；"必填"是**确认收款这个动作**的约束，写在服务层，不是列约束 |
| 其余字段全选填 | 财务手上有什么就填什么；**不因为"少填一个"而拦着不让确认收款**（拦了就等于把货压着不发） |

---

## 2. `trade_config` —— 加 4 个字段（收款账户信息，FR-210）

```sql
ALTER TABLE `trade_config`
  ADD COLUMN `offline_pay_enabled` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否启用对公转账',
  ADD COLUMN `offline_pay_account_name` varchar(128) NULL COMMENT '收款户名',
  ADD COLUMN `offline_pay_account_no` varchar(64)  NULL COMMENT '收款账号',
  ADD COLUMN `offline_pay_bank_name` varchar(128) NULL COMMENT '开户行',
  ADD COLUMN `offline_pay_tip` varchar(255) NULL COMMENT '给客户的提示（如"请在附言里写订单号"）';
```

- 它已经是"交易相关的可运营配置"的既有载体（售后原因 / 配送 / 分销…），后台有现成的「交易配置」页 ✓
- `offline_pay_enabled` 让所有者能**随时关掉**这个支付方式（试点期保险）
- ⚠️ **户名 / 账号 / 开户行是业务事实**：规格与代码里留**占位符**，由所有者填（宪法 FR-054~056）

## 3. `pay_channel` —— 新增一行（`offline` 渠道，R46）

| 列 | 值 |
|---|---|
| `app_id` | `mall` 应用的 id（与既有渠道同一个 app） |
| `code` | `offline` |
| `status` | 启用 |
| `config` | `NonePayClientConfig` 的空 JSON（offline 不发起任何三方调用） |

## 4. `system_dict_data` —— 新增一条（渠道显示名）

| dict_type | label | value |
|---|---|---|
| `pay_channel_code`（管理端用） | 对公转账 | `offline` |

⚠️ **不补这条，订单上的渠道名就是空白** —— `TradeOrderConvert` 与两个前端都是按字典查 label 的（R46）。

## 5. 「待收款」的判定与筛选（**全仓唯一一处**）

```
是对公单   := trade_order_offline_payment 里存在 order_id = 本单
待收款     := 是对公单 AND trade_order.status = UNPAID(0)
尚未确认   := 是对公单 AND confirm_time IS NULL
```

- **财务筛待收款**：订单列表按「对公转账」筛 —— 走 `inSql(order_id IN (SELECT ...))`，
  与既有的 `eqIfPresent` 筛选并列（FR-209）。
- ⚠️ **`pay_channel_code` 仍然是"支付成功的支付渠道"**（该列注释如此）：对公单在**确认收款前
  它是空的**，确认收款后由 `updateOrderPaid` 置为 `offline` ✓ —— 所以我们**没有**篡改它的语义，
  却也白拿了"按渠道筛选"和"自动跳过微信物流同步"两个好处。

## 6. 状态流转（**不加新状态**）

```
下单（选对公转账）
  └─ status = UNPAID(0)，插一行 trade_order_offline_payment，**不建在线支付单**
       ├─ 财务确认收款 → 造 offline 已支付单 → updateOrderPaid() → UNDELIVERED(10)   ← 既有链路
       └─ 财务手工取消 → cancelOrder0()                       → CANCELED(40)        ← 既有链路
```

- **不参与超时自动取消**（FR-212 / R45）—— 改 `cancelOrderBySystem` 的**两处**筛选
- 确认收款后：发货 / 退款 / 售后**与在线支付订单完全一致**（因为它就是走既有链路）✓
