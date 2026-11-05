# Implementation Plan: 发货前修改收货地址

**Branch**: `015-order-address-edit` | **Date**: 2026-11-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/015-order-address-edit/spec.md`

## Summary

在订单详情（待发货）提供「修改地址」入口：前台复用地址簿与新增地址弹层，
服务端新增 C 端 `PUT /trade/order/update-address`，校验会员归属 + 待发货状态后
更新订单收货快照。无数据库变更，复用既有错误码与地址能力。

## Technical Context

**Language/Version**: Java 17（yudao-cloud trade 模块）+ TypeScript / Vue 3（storefront）
**Primary Dependencies**: Spring Boot / MyBatis Plus（服务端）；Vue 3 / Pinia / Vitest（前台）
**Storage**: MySQL 既有 `trade_order` 表，无 DDL
**Testing**: JUnit 5（服务端 service 单测）；Vitest + @vue/test-utils（前台组件/API）
**Target Platform**: Web 门面站 + App API 服务端
**Project Type**: 多端 Web（前台 + 服务端）
**Constraints**: 仅待发货；不重算金额；不修改地址簿
**Scale/Scope**: 订单详情 1 个页面 + 1 个 app-api 端点 + 1 个 service 方法

## Constitution Check

- **I 测试先行**：先在 `TradeOrderUpdateServiceImplTest` 与 `OrderDetailView.spec.ts`
  写失败测试，再实现；服务端与前台各自红-绿。
- **II 先澄清**：状态范围（仅待发货）、选地址方式（地址簿）、交付流程已由所有者确认（Q112-Q114）。
- **III 复用既有能力**：复用地址簿 API、`AddressFormDialog`、既有
  `TradeOrderStatusEnum.isUndelivered` 与 `ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_DELIVERED`；
  不新增地址能力、不重复实现状态规则。
- **IV 最小改动**：服务端只增加 app VO/endpoint/一个 service 方法/一个操作类型；
  前台只在订单详情加一个入口与弹层。

## Project Structure

### Documentation

```text
specs/015-order-address-edit/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/app-api.md
└── tasks.md
```

### Source Code

```text
yudao-cloud/yudao-module-mall/
├── yudao-module-trade-api/.../TradeOrderOperateTypeEnum.java       # + MEMBER_UPDATE_ADDRESS
└── yudao-module-trade-server/
    ├── controller/app/order/AppTradeOrderController.java           # + PUT /update-address
    ├── controller/app/order/vo/AppTradeOrderUpdateAddressReqVO.java
    ├── service/order/TradeOrderUpdateService.java                  # + 方法
    ├── service/order/TradeOrderUpdateServiceImpl.java              # + 实现
    └── src/test/.../TradeOrderUpdateServiceImplTest.java           # + 3 条测试

storefront/src/
├── api/order.ts                     # + updateOrderAddress
├── types/order.ts                   # + OrderAddressUpdateReq
├── views/OrderDetailView.vue        # + 入口 / 地址选择弹层
└── views/OrderDetailView.spec.ts    # + 入口与选择地址用例
```

## Phase Plan

1. **Phase 0 契约**：写 `contracts/app-api.md`（请求/响应/错误码）。
2. **Phase 1 服务端测试先行**：在 service 测试里写「成功 / 非本人 / 非待发货」3 条，
   确认失败；再实现 VO/endpoint/service/enum，跑绿。
3. **Phase 2 前台测试先行**：在 `OrderDetailView.spec.ts` 写「待发货显示入口 / 其它状态不显示 /
   选择地址调用接口并刷新」；再实现 API、类型与页面，跑绿。
4. **Phase 3 验证**：服务端单测 + 前台 `pnpm test` + 类型检查；必要时补一条 e2e 冒烟。
