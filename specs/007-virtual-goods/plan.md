# Implementation Plan: 虚拟商品（软件）—— 买软件不填收货地址

**Branch**: 与 006 同一条 `006-order-invoice` | **Spec**: [spec.md](./spec.md) |
**Quickstart**: [quickstart.md](./quickstart.md)

## Summary

给商品加一档 `VIRTUAL(3)「虚拟商品」`配送方式，并让**服务端按商品自动判定**本单的配送方式 ——
整单都是虚拟商品时，客户下单**不需要收货地址**。

**改动分布在四端，但只增加一个字段、一个枚举值、一行字典数据，不建表、不加列。**

## Technical Context

| 项 | 值 |
|---|---|
| 服务端 | Java + yudao（`yudao-cloud`），改动集中在 `yudao-module-trade-{api,server}` |
| C 端 | Vue 3 + Vite（`storefront/`），只走 `/app-api` |
| 管理端 | yudao 既有 `yudao-ui-admin-vue3`，只走 `/admin-api` |
| 数据 | MySQL。**无 DDL**，只有一条 `system_dict_data` 的 INSERT |
| 测试 | 服务端 JUnit（纯函数）/ C 端 vitest / 管理端 vitest |

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 判定规则（`TradePriceServiceImplTest`）与 C 端结算行为（`CheckoutView.spec.ts`）都先写测试，并**验证过"改回旧逻辑会变红"**。 ⚠️ 例外：`DeliveryTypeEnum` 加枚举值本身无法"先红"（会变成编译错误，不是"失败原因正确"），所以那一处是"先加枚举、再补规则测试" |
| **II. 先澄清，不猜测** | ✅ 改动跨四端，触发澄清。已就 5 个业务口径提问并得到所有者拍板（Q39①~⑤） |
| **III. 复用既有能力** | ✅ **不加一张表、不加一列、不加一个接口**：复用「无需发货」（`logisticsId=0`）、复用既有的 `deliveryType` 字段与状态机。判定放在服务端，前端只显示（业务规则不由前端裁决） |
| **IV. 最小改动** | ✅ 后端净改动约 40 行。⚠️ 有两处**看起来像越界**、但实为必需的改动：<br>① `DeliveryForm.vue` 的运费模板必填 —— **不修则虚拟商品存不下来**（前置条件）<br>② `api/order.spec.ts` 那条过时测试 —— 它钉的是"结算固定传快递"这个被本特性推翻的行为 |
| **V. 增量可验证** | ✅ US24（免地址）与 US25（后台标记交付）可独立实现、独立验证。**不自动化验证的项已在 quickstart §五 显式列出**（并说明理由） |

**Complexity Tracking**：无。本计划不引入新抽象层、不加新依赖、不做超出需求的设计。

## 关键设计决策

### 1. 「整单虚拟」由**服务端**判定（而不是前端）

前端**没有依据**可判：`SettlementResp` / `SettlementItem` / `CartItem` 都不带商品的配送方式；
`deliveryTypes` 只存在于 `ProductSpu`，且生产代码从未消费过它。

于是：**结算时前端不传 `deliveryType`**，服务端在 `TradePriceServiceImpl` 里按
已经加载好的 `spuList` 判定，把结果放进结算响应的 `deliveryType`；前端据此
决定"要不要地址"，并在下单时**原样回传**。

### 2. 判定规则刻意保守

「所有商品都**只支持**虚拟商品」才算虚拟。两条边界都是有意的：

- **空配送方式 → 快递**：`delivery_types` 的建表默认值就是 `''`，历史数据里有若干条。
  当成虚拟会让那些**实物**商品收不到地址。
- **同时勾快递与虚拟 → 快递**：它支持快递，就该要地址。

### 3. 落库以服务端的值为准

`TradeOrderConvert` 显式映射 `calculateRespBO.deliveryType → trade_order.deliveryType`，
`buildTradeOrder` 的地址分支也改用 `order.getDeliveryType()`。
客户端传的值**不参与**落库与分支 —— 否则传个 `1` 就能让虚拟单去要地址。

⚠️ 这带来一个 MapStruct 的必答题：`createReqVO` 与 `calculateRespBO` **都有** `deliveryType`，
不写显式 `@Mapping` 会报 "Several possible source properties"。

### 4. 发货复用既有「无需发货」

`deliveryOrder` 早就支持 `logisticsId = 0`（前端叫「无需发货」），
唯一的拦路虎是它前面那句 `deliveryType != 快递 就抛异常`。改成**只拒自提**即可 ——
不新增接口、不改请求体。

## 实施分期

| 阶段 | 内容 | 交付后能做什么 |
|---|---|---|
| **① Foundational** | 字典 SQL + 枚举 | 商品能勾「虚拟商品」（还下不了单） |
| **② US24** | 判定规则 + 结算响应 + 建单 + C 端 | **买软件不填地址，能下单** |
| **③ US25** | 发货放行 + 管理端按钮/展示 | 运营能把软件单标记为已交付（闭环） |

## 主要风险与对策

| # | 风险 | 对策 |
|---|---|---|
| R38 | **字典脚本漏执行** → 商品编辑勾不出「虚拟商品」，而接口全通、测试全绿 | quickstart §零 列为**上线必做**；症状与排查方式写在那里 |
| R39 | **微信小程序物流同步**：虚拟单走发货会触发 `afterDeliveryOrder`，那里依赖 `receiverMobile`（虚拟单为 null） | ⚠️ **单测覆盖不到** —— quickstart §四.2 要求**真走一遍"发货"**再看日志 |
| R40 | **上线顺序**：枚举没上时，"商品勾了虚拟商品"会在下单时报「配送方式不正确」 | 顺序写死在 `011` 脚本头与 quickstart §零：**字典 → 后端 → 前端** |
| R41 | 判定规则被后来人"改宽松"（比如把空配送方式也算虚拟） | 规则的两条边界都**写成测试**，并在代码与 spec 的 Edge Cases 里写明为什么 |

## 明确不做

自动交付（授权码/下载）、虚拟商品不限库存、客户端「待发货」的文案优化、
改状态机省掉"确认收货"、uniapp 端同步 —— 理由见 spec 的 Assumptions 与 quickstart §五。
