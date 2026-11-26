# Data Model：代客开票

**无数据库结构变更**（无新表、无新列、无迁移脚本）。以下为既有实体与本次新增的请求/展示字段。

## 实体：发票申请（`trade_order_invoice`，006 既有）

| 字段 | 说明 | 本特性关系 |
|---|---|---|
| `order_id` | 订单编号，`uk_order_id` 唯一 | 一单一条（含后台补申请） |
| `order_no` | 订单号快照 | 不变 |
| `user_id` | 订单所属会员 | 后台补申请同样取订单会员 |
| `type` / `title_kind` | 票种 / 抬头类型 | 与 C 端同一套枚举 |
| `title_name` / `tax_no` / `title_address` / `title_phone` / `bank_name` / `bank_account` / `bank_code` / `email` | 抬头字段 | 后台可完整填写 |
| `amount` | 开票金额 = 申请时订单实付快照 | 由服务端写入，**不由运营填写** |
| `status` | 0 待开票 / 10 已开票 / 20 已作废 | 创建后为「待开票」 |
| `creator`（BaseDO） | 创建人 | 后台补申请时自动记录后台账号（审计） |

**状态转换**：`待开票 → 已开票`（订单已支付后由后台开具）或 `→ 已作废`（订单退款/取消按 006 既有流程）。
本特性不新增状态，也不改变既有转换。

## 请求/展示模型（不落库）

### AgentInvoiceReq（代客下单请求里的 `invoice`）

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `type` | Integer | 是 | 1 普票 / 2 专票 / 3 收据 |
| `titleKind` | Integer | 是 | 1 企业 / 2 个人 |
| `titleName` | String(255) | 是 | trim 后非空 |
| `taxNo` | String(64) | 条件 | 企业 + 普票/专票必填；个人、收据选填；15/18/20 位数字或大写字母 |
| `titleAddress` / `titlePhone` | String | 否 | 填了要合法（长度 255 / 64） |
| `bankName` / `bankAccount` / `bankCode` | String | 否 | 长度 255 / 64 / 32 |
| `email` | String(128) | 否 | 填了要符合邮箱格式 |

> 规则唯一来源：`InvoiceValidator`（服务端）；前端只是提前提示。

### AdminCreateInvoiceReq（后台补申请）

| 字段 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `orderId` | Long | 是 | 订单必须存在、未删除 |
| 其余 | 同上 | — | 与 `AgentInvoiceReq` 完全相同 |

**服务端校验（与 C 端补开一致）**：
1. 订单存在；
2. `payStatus = true`（已支付）；
3. `payPrice > 0`；
4. 订单未取消；
5. 该订单尚无发票申请（`uk_order_id` 兜底并发）；
6. 抬头矩阵（`InvoiceValidator`）；
7. 订单 `maxlabel_org_id` 非空（企业授权单）→ `titleKind` 必须为企业。

### 订单详情展示新增字段

| 字段 | 类型 | 来源 | 用途 |
|---|---|---|---|
| `enterprise` | Boolean | `trade_order.maxlabel_org_id != null` | 管理端补申请弹窗禁用「个人」抬头 |

## 不变量

- 发票金额永远由服务端取订单实付，运营不能填写（延续 006 FR-161）。
- 一单一条：数据库唯一键 + 服务层明确报错。
- 后台补申请不改变订单金额、支付状态、订单状态。
