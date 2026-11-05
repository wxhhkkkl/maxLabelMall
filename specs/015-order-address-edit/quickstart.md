# Quickstart: 发货前修改收货地址

## 服务端

1. 造一笔 `user_id=当前会员`、`status=10` 的订单。
2. `PUT /app-api/trade/order/update-address`，带新的收件人/手机/地区/详细地址。
3. 断言返回 `data=true`，订单详情 `receiver*` 已更新，`payPrice`/`status` 不变。
4. 用 `status=20` 的订单重试 → 返回 `ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_DELIVERED`。
5. 用别的会员的订单重试 → 返回 `ORDER_NOT_FOUND`。

## 前台

1. 登录会员，打开一笔「待发货」订单详情。
2. 收货信息卡片出现「修改地址」。
3. 选择地址簿中的另一条地址（或先新增），确定。
4. 页面刷新后显示新地址；地址簿与金额不变。
5. 打开「已发货 / 已完成 / 待付款」订单 → 不出现「修改地址」。

## 自动化

- 服务端：`mvn -pl yudao-module-mall/yudao-module-trade-server -Dtest=TradeOrderUpdateServiceImplTest test`
- 前台：`cd storefront && pnpm vitest run src/views/OrderDetailView.spec.ts src/api/order.spec.ts`
- 类型检查：`cd storefront && pnpm ts:check`
