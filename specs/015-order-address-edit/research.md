# Research: 发货前修改收货地址

## R1 — 复用管理端的地址修改规则，不另写状态机

管理端 `TradeOrderUpdateServiceImpl.updateOrderAddress(TradeOrderUpdateAddressReqVO)`
已有「仅待发货」校验和 `ORDER_UPDATE_ADDRESS_FAIL_STATUS_NOT_DELIVERED`。
C 端方法复用同一状态枚举 `TradeOrderStatusEnum.isUndelivered` 与同一错误码，
只额外增加「订单必须属于当前会员」校验。

## R2 — 归属校验用既有 mapper 方法

`TradeOrderMapper.selectByIdAndUserId(id, userId)` 已是既有查询；返回 null 时抛
`ORDER_NOT_FOUND`，与会员收货等其它 C 端操作一致。不要用管理端的 `selectById`。

## R3 — 请求体字段与管理端一致，但使用 app 包内 VO

`AppTradeOrderUpdateAddressReqVO`：`id` / `receiverName` / `receiverMobile` /
`receiverAreaId` / `receiverDetailAddress`。不直接复管理端 VO，避免 app controller
依赖 admin 包。

## R4 — 只更新订单快照，不动地址簿与金额

订单收货字段在下单时已快照；本次只更新这 4 个字段。运费、应付金额、订单状态、
地址簿都不变（已支付订单不应因换地址重新计价）。

## R5 — 前台复用地址簿与新增地址弹层

`listAddress()` + `AddressFormDialog` 已由结算页与地址管理页使用；
订单详情直接复用，避免第二套地址选择 UI。

## R6 — 操作日志

新增 `TradeOrderOperateTypeEnum.MEMBER_UPDATE_ADDRESS(12, "用户修改收货地址")`，
与 `ADMIN_UPDATE_ADDRESS` 区分是谁改的；不改变现有日志展示结构。
