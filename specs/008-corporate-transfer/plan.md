# Implementation Plan: 对公转账（客户线下打款，财务确认收款）

**Branch**: 与 006/007 同一条 `006-order-invoice` | **Date**: 2026-10-16 |
**Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

## Summary

让企业客户**线下对公打款**也能下单：结算页多一个「对公转账」的支付方式 → 订单进入
「**待收款**」（**不生成在线支付单**，前台也不给在线支付入口）→ 财务对着银行流水在后台
搜到订单、点「**确认收款**」并填流水号 → 订单进「待发货」。

**技术上只做两件新东西**：一张与订单 1:1 的子表（承载"是不是对公单" + 确认痕迹），
以及 pay 模块一个「造已支付 offline 支付单」的能力。**其余全部复用**。

## Technical Context

**语言 / 栈**：Java 17+（yudao-cloud 单体聚合启动）+ Vue 3 / TypeScript（`storefront`、`yudao-ui-admin-vue3`）
**数据**：MySQL。**唯一 DDL 是一张新子表**（`trade_order` 一个字节不动）
**测试**：服务端 JUnit（纯函数 + `BaseDbUnitTest`）、C 端 / 管理端 vitest
**规模**：量级很小（对公客户是低频大额），无性能诉求
**约束**：C 端只走 `/app-api`、管理端只走 `/admin-api`、不引入 UI 组件库（宪法）

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 判定与状态流转都有可先写的测试（子表行 → 待收款；确认收款 → 待发货；重复确认 → 幂等；取消后不可确认）。⚠️ 与 007 同样的例外：**新增枚举值 / 建表本身无法"先红"**（会变成编译或建表错误，不是"失败原因正确"），所以先落结构、再补行为测试 |
| **II. 先澄清，不猜测** | ✅ 改跨四端，触发澄清。**两轮共 6 问**（Q41~Q46）全部由所有者拍板；两处"现状是什么"也查了代码写进规格，没有猜 |
| **III. 复用既有能力** | ✅ **这是本计划的主导思想**：复用支付单 + `updateOrderPaid`（不自己改状态）、复用 `trade_config`（不新建配置表）、复用后台订单列表的筛选（不另做查询）、复用既有的取消链路。**新增的只有"确实没有"的两样** |
| **IV. 最小改动** | ✅ `trade_order` 不加列、状态机不加值。⚠️ 有三处**看起来越界、实为必需**：① 改 `cancelOrderBySystem` 的筛选（不改就被 2 小时超时砍掉，见 R45）② 必须**新增**后台「取消订单」入口（不新增则弃单永久积压）③ 修 C 端 `needsNoPay` 的误判（不修则对公单显示"无需支付"） |
| **V. 增量可验证** | ✅ US26（下单）与 US27（确认收款）可独立实现、独立验证。**不自动化验证的项**已在 quickstart 显式列出并说明理由 |

**Complexity Tracking**：无违规。不引入新抽象层、不加新依赖。

## 关键设计决策（详见 [research.md](./research.md)）

1. **R42 用 1:1 子表表达「待收款」** —— 不新增状态值（`TradeOrderStatusEnum` 有 ~76 处引用），
   不给 `trade_order` 加列（006 先例）。**一行同时解决"是不是对公单"与"确认痕迹落哪"**。
2. **R43 确认收款造一张真支付单** —— `updateOrderPaid`（唯一入口）会校验金额与 `merchantOrderId`，
   绕不开；而且**退款挂在支付单上**，绕开它退款就断了（SC-073）。
3. **R45 改自动取消的筛选条件** —— 且**主筛选与兜底逻辑两处都要改**，否则 `payOrderId` 为 null 会传进 pay RPC。
4. **R47 C 端 `needsNoPay` 会误判对公单** —— 必须让「待收款」分支先判。

## Project Structure

```text
specs/008-corporate-transfer/
├── spec.md          # 已完成（两轮澄清 Q40~Q46）
├── plan.md          # 本文件
├── research.md      # R42~R47
├── data-model.md    # 子表 + trade_config 新字段 + 状态与筛选
├── contracts/
│   ├── app-api.md   # C 端：结算/下单/详情
│   └── admin-api.md # 管理端：确认收款、取消、筛选
├── quickstart.md    # 手工验收（含"显式不验证"）
└── tasks.md         # /speckit-tasks 产出
```

### 代码落点

```text
yudao-cloud/
├── yudao-module-pay/yudao-module-pay-api/enums/    # PayChannelEnum 加 OFFLINE（T464）
├── yudao-module-mall/yudao-module-trade-api/       # （本次**不加**订单状态枚举）
├── yudao-module-mall/yudao-module-trade-server/
│   ├── controller/app/order/vo/                    # 下单入参加"支付方式"
│   ├── controller/admin/order/                     # 确认收款 / 取消订单
│   ├── dal/dataobject/offline/                     # 新子表 DO + Mapper
│   └── service/order/                              # 建单插子表行；改 cancelOrderBySystem
├── yudao-module-pay/yudao-module-pay-api/          # PayOrderApi 加"造已支付 offline 单"
├── yudao-module-pay/yudao-module-pay-server/       # 该能力的实现
└── sql/mysql/012-corporate-transfer.sql            # 建子表 + trade_config 加字段 + 字典

storefront/src/                                     # 结算页支付方式 + 订单详情「待收款」
yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/       # 确认收款弹层 / 取消按钮 / 筛选
```

## 实施分期

| 阶段 | 内容 | 交付后能做什么 |
|---|---|---|
| **① Foundational** | 子表 + `trade_config` 字段 + 字典 + `offline` 渠道 + pay 侧"造已支付单"能力 | 地基（还下不了对公单） |
| **② US26** | 结算页支付方式 + 建单插子表行 + 跳过在线支付单 + 订单详情「待收款」与收款账户 | **客户能用对公转账下单了** |
| **③ US27** | 后台「确认收款」+ 权限点 + 后台「取消订单」+ 筛选 + 改自动取消筛选 | **财务能核销、能清理弃单（闭环）** |

## 主要风险与对策

| # | 风险 | 对策 |
|---|---|---|
| **R42** | **自动取消没改全**：只改主筛选、漏了 `cancelOrderBySystem(order)` 里那句无条件的 `order.getPayOrderId()` → 对公单被选中时把 null 传进 pay RPC | 两处一起改；**写成测试**（待收款单不进候选集） |
| **R43** | **支付单校验对不上**：`updateOrderPaid` 校验 `price == 订单实付` 且 `merchantOrderId == 订单 id`，造单时必须严格对齐 | 单测钉死；金额取订单实付、`merchantOrderId` 取订单 id |
| **R44** | **重复/并发确认收款** | 服务层幂等（子表"未确认"→"已确认"的条件更新只生效一次）+ 单测 |
| **R45** | **C 端显示"无需支付"**：`needsNoPay` 与对公单条件重合 | 「待收款」分支前置；`CheckoutView.spec` / `OrderDetailView.spec` 各加一条断言 |
| **R46** | **对公单被微信物流同步**：`TradeStatusSyncToWxaOrderHandler` 按 `payChannelCode` 判 | 确认收款后 `updateOrderPaid` 会把渠道置为 `offline`（≠ `wx_lite`）→ **自动跳过** ✓ 但**要真走一遍发货看日志** |
| **R47** | **收款账户信息被写死** | 走 `trade_config`（FR-210）；规格与代码里留占位符，**不编造户名账号** |

## Post-Design Re-check（宪法再核）

- 原则 III ✅ 新增面**只有**两处"确实没有的能力"（子表、pay 侧造已支付单），其余全是复用
- 原则 IV ✅ 无违规；三处"看起来越界"的改动都已在上面逐条说明**为什么不做不行**
- 原则 V ✅ 每个 FR 都落到 SC 或 quickstart 的手工条目；不验证项已显式声明
