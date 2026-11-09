# Tasks: 发货前修改收货地址

## Phase 1 — 服务端（测试先行）

- [x] T001 在 `TradeOrderUpdateServiceImplTest` 写 3 条失败测试：
  待发货可改 / 非本人订单拒绝 / 非待发货拒绝。
- [x] T002 新增 `AppTradeOrderUpdateAddressReqVO`（app order vo 包）。
- [x] T003 `TradeOrderUpdateService` + Impl 增加
  `updateOrderAddress(Long userId, AppTradeOrderUpdateAddressReqVO)`：
  `selectByIdAndUserId`、`isUndelivered`、更新 4 个 receiver 字段、写订单日志。
- [x] T004 `TradeOrderOperateTypeEnum` 增加 `MEMBER_UPDATE_ADDRESS(12, ...)`。
- [x] T005 `AppTradeOrderController` 增加 `PUT /trade/order/update-address`。
- [x] T006 跑服务端测试至全绿。

## Phase 2 — 前台（测试先行）

- [x] T007 `OrderDetailView.spec.ts` 写失败用例：待发货显示入口 / 非待发货不显示 /
  选择地址调用 `updateOrderAddress` 并刷新详情。
- [x] T008 `types/order.ts` 增加 `OrderAddressUpdateReq`。
- [x] T009 `api/order.ts` 增加 `updateOrderAddress`（PUT）。
- [x] T010 `OrderDetailView.vue` 增加入口、地址选择弹层、`AddressFormDialog` 复用与刷新。
- [x] T011 跑 `pnpm vitest run` 与 `pnpm ts:check` 至全绿。

## Phase 3 — 契约与冒烟

- [x] T012 更新本规格 `contracts/app-api.md`（已随规格落盘，实施时校对）。
- [ ] T013（未执行：端到端需联调环境，本次以服务端单测 + 前台组件单测覆盖） 视需要补一条订单详情 e2e 冒烟（选择地址 → 新地址显示）。
