# 任务清单（007 虚拟商品）

> 编号续 006：**本清单从 T440 起**（006 排到 T439）。编号全局唯一、不补空号。

## Format: `[ID] [P?] [Story] Description （FR/SC 引用）`

---

## Phase 1: Setup

- [x] T440 建 `specs/007-virtual-goods/`：`spec.md` / `plan.md` / `data-model.md` /
      `contracts/{app-api,admin-api}.md` / `quickstart.md` / `tasks.md`

## Phase 2: Foundational（US24 / US25 都依赖它）

- [x] T441 [P] **SQL** `yudao-cloud/sql/mysql/011-virtual-goods-dict.sql`：给字典
      `trade_delivery_type` 插一条 `3 虚拟商品`（幂等：`INSERT ... WHERE NOT EXISTS`）。
      ⚠️ **只改 Java 枚举不够** —— 管理端商品编辑的选项来自**字典**，服务端校验来自**枚举**，
      两边都改才算改完（FR-197）
- [x] T442 [P] 枚举 `DeliveryTypeEnum`（trade-api）加 `VIRTUAL(3, "虚拟商品")` ——
      `ARRAYS` 由 `values()` 生成，`@InEnum` 会自动放行 3（FR-197）

## Phase 3: US24 —— 买软件不填收货地址（P1）

### 服务端

- [x] T443 [测试先行] `TradePriceServiceImplTest`：**5 条判定规则** ——
      全虚拟→3 / 混买→1 / 实物→1 / **配送方式为空→1** / **同时勾快递+虚拟→1**
      （FR-186、spec 的两条 Edge Case）。
      ⚠️ 为了能测，`resolveDeliveryType` 从 `private` 改成**包内可见**；
      ⚠️ 这是本模块**唯一**的价格服务测试类，它只覆盖这个纯函数 —— 理由写在类注释里
- [x] T444 服务端 `TradePriceServiceImpl`：新增 `resolveDeliveryType`；
      **请求没传配送方式时按商品判定**；把生效值写进 `TradePriceCalculateRespBO.deliveryType`
      （FR-185 / FR-186）。
      ⚠️ **不放在 `TradeDeliveryPriceCalculator` 里** —— 保持它"null 即跳过"的既有语义不变
- [x] T445 服务端 `AppTradeOrderSettlementRespVO` + `deliveryType` 字段（MapStruct 同名自动映射）
      （FR-185）
- [x] T446 服务端 `TradeDeliveryPriceCalculator`：**显式加 VIRTUAL 分支**（不计运费）。
      原来它是靠"两个 if 都不命中"隐式得到 0 的 —— 显式写出来，将来再加配送方式时
      能分清是"故意不计费"还是"忘了处理"（FR-190）
- [x] T447 服务端 `TradeOrderUpdateServiceImpl.buildTradeOrder`：
      地址分支改用**服务端判定的** `order.getDeliveryType()`，不再用 `createReqVO` 里的
      （FR-189）；并给 `TradeOrderConvert` 加显式 `@Mapping`（两个源都有 `deliveryType`，
      不写 MapStruct 会报 "Several possible source properties"）

### C 端

- [x] T448 [测试先行] `CheckoutView.spec.ts`：**5 条** ——
      虚拟单不渲染地址卡片且能提交 / 请求体带 `deliveryType=3` 且不带 `addressId` /
      **不挑默认地址** / 混买单照旧要地址 / **非虚拟单没选地址仍被拦**（FR-187~191）
- [x] T449 C 端 `api/order.ts`：**结算不传 `deliveryType`**（交服务端判定）；新增
      `DELIVERY_TYPE_VIRTUAL = 3`；`createOrder` 保留快递兜底（FR-185 / FR-189）
- [x] T450 C 端 `types/order.ts`：`SettlementResp.deliveryType`（必填）（FR-185）
- [x] T451 C 端 `CheckoutView.vue`：`isVirtual` computed；不渲染地址卡片、
      `load()` 不为虚拟单挑默认地址、`onSubmit()` 不卡地址、下单回传 `deliveryType`
      （FR-187 / FR-188 / FR-189）
- [x] T452 C 端 `OrderDetailView.vue`：「收货信息」卡片改成 `v-if="order.receiverName"`
      —— 否则虚拟单渲染出一片空白（FR-190）
- [x] T453 C 端 `api/order.spec.ts`：把"结算固定传快递"那条**过时的测试**改到新口径
      （断言**不传** `deliveryType`）—— 它钉的是旧行为，不改就会红

## Phase 4: US25 —— 后台把软件单标记为已交付（P2）

- [x] T454 服务端 `deliveryOrder`：把"非快递一律拒"改成**只拒自提** ——
      虚拟单因此能走**既有的**「无需发货」分支（`logisticsId = 0`）（FR-192 / FR-193）
- [x] T455 管理端：`utils/constants.ts` 加 `VIRTUAL`；订单列表与详情的「发货」按钮
      改成 `deliveryType !== 自提`；`OrderTableColumn` 的「买家/收货人」列加虚拟商品分支；
      订单详情对虚拟单**不渲染**空的「收货人 / 联系电话」（FR-196）
- [x] T456 管理端 `DeliveryForm.vue`：**修既有缺陷** —— 运费模板从"无条件必填"改成
      "勾了快递才必填"（不修则只勾「虚拟商品」根本存不下来）；并加一句运营提示（FR-194 / FR-195）

## Phase 5: Polish

- [x] T457 全量回归：后端 trade 模块 66 条、storefront 1262 条 + `vue-tsc`、管理端 vitest + eslint
- [ ] T458 手工验收 `quickstart.md` §一~§四（**软件单不填地址走通** + **混买单仍要地址** +
      **后台无需发货** —— 这三条是本特性的判据）
- [ ] T459 执行 `011-virtual-goods-dict.sql`（**先问所有者**）+ 发版（顺序：字典 → 后端 → 两个前端）
- [ ] T460 提交

---

## 需求 → 任务 对照

| 需求 | 任务 |
|---|---|
| FR-185 结算响应回传配送方式 | T444, T445, T449, T450 |
| FR-186 判定规则 | T443, T444 |
| FR-187 虚拟单不渲染地址区 | T448, T451 |
| FR-188 虚拟单不挑默认地址 | T448, T451 |
| FR-189 下单回传、服务端为准 | T444, T447, T449, T451 |
| FR-190 无收货信息、无运费 | T446, T452, T447 |
| FR-191 含实物仍要地址 | T448, T451 |
| FR-192 虚拟单可发货 | T454 |
| FR-193 自提不能发货 | T454 |
| FR-194 商品编辑可选虚拟商品 | T441, T455, T456 |
| FR-195 运费模板只在勾快递时必填 | T456 |
| FR-196 后台发货按钮与展示 | T455 |
| FR-197 四端取值一致 | T441, T442, T449, T455 |
| FR-198 不影响既有订单 | T457, T458 |

## 本清单里要特别当心的三条

1. **T441（字典）** —— 漏了它，商品编辑页**勾不出「虚拟商品」**，而接口全是通的、测试也全绿
   —— 属于"单测发现不了"的一类（与本项目 T409 菜单脚本同一个坑）。
2. **T447（服务端为准）** —— 只改前端不算数：订单落库与地址分支都必须用**服务端判定的**值，
   否则客户端传个 `1` 就能让虚拟单去要地址。
3. **T456（运费模板必填）** —— 它不是"顺手改"，是**本特性的前置条件**：
   不修则「虚拟商品」这个选项存不下来，整个特性用不了。
