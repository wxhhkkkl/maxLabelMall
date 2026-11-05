# 功能规格：发货前修改收货地址

> 编号续 014：本规格从 **FR-385 / SC-146 / US49 / Q112** 接着往下排
> （014 排到 FR-384 / SC-145 / US48 / Q111）。

**Feature Branch**: `015-order-address-edit`
**Created**: 2026-11-04
**Status**: Draft
**Input**: User description: "订单在发货前，前台给个修改收货地址的功能"

---

## 背景与问题

| # | 现状 | 证据 |
|---|---|---|
| E1 | 客户在订单详情只能看收货信息，不能改 | `storefront/src/views/OrderDetailView.vue` 的「收货信息」卡片只有展示 |
| E2 | 服务端只有**管理后台**的修改地址能力 | `TradeOrderUpdateServiceImpl.updateOrderAddress(TradeOrderUpdateAddressReqVO)` 被 `/admin-api` 调用 |
| E3 | 管理端规则是「仅待发货」 | `ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_DELIVERED`、`TradeOrderStatusEnum.isUndelivered` |
| E4 | 地址簿已有列表 / 新增 / 编辑与结算页选地址链路 | `/app-api/member/address/**`、`AddressFormDialog.vue`、`CheckoutView.vue` |

**问题**：客户付款后、商家发货前发现地址写错，目前只能联系客服人工改；
需要让客户在订单详情自助从自己的地址簿换一个收货地址。

## Clarifications

### Session 2026-11-04

- **Q112 哪些订单状态允许修改？**（所有者确认）
  → **A：仅「待发货」（status=10）**。与管理后台现有规则一致，复用
  `ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_DELIVERED`；待付款、已发货、已完成、已取消均不提供入口，
  服务端也拒绝。
- **Q113 修改时如何选择地址？**（所有者确认）
  → **A：从地址簿选**。复用 `listAddress()` 与 `AddressFormDialog`；可先新增地址再选择。
  修改只更新订单的收货信息快照，**不修改地址簿**。
- **Q114 入口与契约？**（所有者确认）
  → **A：订单详情「收货信息」卡片提供「修改地址」按钮**；服务端新增 C 端
  `PUT /trade/order/update-address`，校验订单归属与状态。不新增数据库字段/表。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 待发货订单修改收货地址 (Priority: P1 · US49)

客户打开一笔「待发货」订单的详情，在收货信息卡片点「修改地址」，
从地址簿选择另一条地址（或先新增），确认后订单的收件人/电话/地区/详细地址立即更新。

**Why this priority**: 这是本特性的唯一目的；没有它，功能不存在。

**Independent Test**: 用一笔 `status=10`、`receiverName` 非空的订单，
在订单详情选择地址簿里的第二条地址，断言页面显示新地址且订单详情被重新拉取。

**Acceptance Scenarios**:

1. **Given** 订单状态为待发货且订单有收货信息，**When** 打开订单详情，
   **Then** 收货信息卡片显示「修改地址」按钮。
2. **Given** 点了「修改地址」，**When** 地址簿有两条地址并选择第二条，
   **Then** 调用修改接口并刷新详情，页面显示新收件人、电话、地区与详细地址。
3. **Given** 地址簿为空，**When** 打开地址选择弹层，**Then** 提供「新增收货地址」入口。
4. **Given** 订单状态不是待发货，**When** 打开订单详情，**Then** 不显示「修改地址」按钮。

---

### Edge Cases

- 虚拟商品订单没有收货信息 → 不显示整块收货信息，也不显示按钮。
- 订单属于别的会员 → 服务端返回 `ORDER_NOT_FOUND`，前端显示失败提示，不泄露订单内容。
- 订单在页面打开后被商家发货 → 服务端按最新状态拒绝，前端提示「订单不是待发货状态」。
- 修改地址**不重算运费/应付金额**：订单已支付，金额是下单时的快照。
- 地址簿里的地址后续被编辑/删除，不影响已写入订单的快照。
- 重复点击/网络失败 → 按钮进入保存中状态，避免重复提交；失败保留弹层并提示。

## Requirements *(mandatory)*

- **FR-385**: 订单详情 MUST 仅在 `status=10`（待发货）且订单有收货人信息时显示「修改地址」入口；
  其它状态 MUST NOT 显示。
- **FR-386**: 点击入口 MUST 打开地址选择弹层，列出当前会员的地址簿；MUST 复用现有的
  新增地址弹层，保存成功后刷新列表。
- **FR-387**: 选择地址后 MUST 调用 `PUT /trade/order/update-address`，
  请求携带订单编号与「收件人 / 手机号 / 地区编号 / 详细地址」。
- **FR-388**: 服务端 MUST 校验登录会员、订单归属与「待发货」状态；
  非本人订单返回 `ORDER_NOT_FOUND`，非待发货返回
  `ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_DELIVERED`（复用既有错误码）。
- **FR-389**: 修改成功 MUST 刷新订单详情；MUST NOT 修改地址簿、商品金额、运费或订单状态。
- **FR-390**: 新增 C 端接口 MUST 只更新订单收货字段，不新增数据库表/列。

### Key Entities

- **TradeOrder（trade_order）**：复用既有 `receiver_name / receiver_mobile / receiver_area_id /
  receiver_detail_address` 快照字段；本期不改变表结构。
- **MemberAddress**：只作为修改时的可选来源读取，不写入。

## Success Criteria *(mandatory)*

- **SC-146**: 待发货订单从打开详情到完成地址修改不超过 3 次点击（修改 → 选地址 → 完成）。
- **SC-147**: 非待发货订单在界面上 0 个「修改地址」入口；服务端 100% 拒绝非待发货修改。
- **SC-148**: 修改后刷新订单详情，收货信息与所选地址一致；金额、状态、地址簿不变。

## Out of Scope

- 管理后台修改地址（已存在，保持不动）。
- 待付款/已发货订单的地址修改。
- 运费重新计算、地址簿内容修改、跨会员订单操作。
