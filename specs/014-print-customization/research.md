# 技术决策与被否决方案（014 印刷定制）

本文件记录 Phase 0 的调研结论。编号 **R87~R98** 接着 013 的 R86 往下排。

---

## R87 —— 定制单用哪套订单载体？**复用 trade_order**

**决策**：定制单仍然是一笔 `trade_order`，订单项落在 `trade_order_item`；定制规格放在
1:1 扩展表 `trade_customization_order`，以 `order_id` 关联。

**理由**：

1. 支付回调、发票、运费模板、发货、物流轨迹、售后、订单日志全部按 `orderNo / orderId`
   工作；另建一套定制订单表等于把这些逐条复制一遍，必然出现两套真相（宪法原则 III）。
2. 定制印刷品是**实物**，需要收货地址与物流，本来就走现有履约链路。
3. 012 / 013 已经用「主表 + 扩展」的先例（`maxlabel_org_id`、`maxlabel_subscription` 列），
   定制这里改用 1:1 扩展表是因为字段多（规格、文件、快照、价格构成），不适合塞进主表。

**被否决**：独立 `trade_customization_order` 作为订单主表 —— 支付与履约要重写；
把定制做成普通商品 SKU 直接卖 —— SKU 价格无法表达「按尺寸与数量实时计价」。

---

## R88 —— 订单项怎么指向 SKU？**预置隐藏 SPU/SKU**

**决策**：迁移脚本预置一个不可售的 SPU/SKU「印刷定制服务」，所有定制订单项都指向它；
库存给足（不参与真实库存），不进任何分类、搜索与推荐。

**理由**：

1. `trade_order_item.spu_id / sku_id` 是 **NOT NULL**（见 `mall-2026-04-18.sql`）。
2. `TradeProductSkuOrderHandler` 在订单创建 / 取消时会按 `skuId` 扣减与回滚库存；
   若 `skuId = 0`，会走到「SKU 不存在」的分支。
3. `TradeBrokerageOrderHandler` 也会按 `skuId` 取 SKU 判断佣金；真实 SKU 让它自然跳过。

**被否决**：`spuId = skuId = 0` —— 必须改共享的库存 / 佣金处理器，回归面远大于预置一行数据；
每个材质建一个 SKU —— 材质增删要同步商品表，且没有任何额外收益。

**注意**：预置 SKU 的 `price` 不会被使用（定制项用服务端算出的 `customPrice` 覆盖），
`stock` 设一个足够大的值即可；`delivery_template_id` 指向一个按件计费的模板，保证运费可算。

---

## R89 —— 计价挂在哪？**既有价格计算链路 + `customPrice`**

**决策**：

1. `TradePriceCalculateReqBO.Item` 增加一个**服务端专用**字段 `customPrice`（单件价由定制
   计价服务算出后填入；**绝不从 HTTP 入参接受**）。
2. `AppTradeOrderCreateReqVO` 增加 `customization` 规格对象。`createOrder` 检测到它时，
   先调用 `CustomizationPriceService` 生成「一个订单项」的 `TradePriceCalculateReqBO`，
   再走原有的 `TradePriceServiceImpl.calculateOrderPrice`。
3. `buildCalculateResp` 在构建订单项时，若 `customPrice != null` 用它代替 SKU 价格。

**理由**：运费计算器、订单类型、价格校验（`payPrice <= 0`）都在既有链路里；把定制
价格作为「价格计算」的一个输入，比在 `createOrder` 里另写一套建单逻辑小得多。

**被否决**：新建 `CustomizationOrderService` 自己拼 `TradeOrderDO` 与订单项 —— 要复制
地址、运费、发票、支付单创建与订单日志；客户端直接传总价 —— 违反宪法原则 III（价格权威在服务端）。

---

## R90 —— 数量与金额怎么落？**count = N，单价向上取整，差额进商品级优惠**

**决策**：

- `trade_order_item.count = 定制数量 N`（真实数量，运费按件数计算才合理）；
- `price = ceil(总价 / N)`（分）；
- `discount_price = price × N − 总价`（0 ≤ 差额 < N 分）；
- `pay_price = price × N − discount_price = 总价`，严格等于报价。

**理由**：订单详情、列表与运费都直接使用 `count`；把「最低收费 / 批量折扣」造成的非线性
折进单价，再用一个几分的商品级优惠吸收取整误差，比伪造数量或另加字段更小。

**被否决**：`count = 1`、`price = 总价` —— 订单显示「1 件」，运费按 1 件算、生产看数量要跳转；
新增 `custom_total_price` 字段 —— 要改订单项表、结算与支付金额校验的共享逻辑。

**注意**：差额必须写进 `discount_price` 而不是 `adjust_price`；后者是后台改价的入口，会被
「改价」流程覆盖。

---

## R91 —— 定制域放哪个模块？**trade-server**

**决策**：新建包 `cn.iocoder.yudao.module.trade.customization`，包含材质 / 区间 / 参数 / 定制订单
四张表的 DO、Mapper、Service 与 Controller。

**理由**：定制规格与价格最终服务于订单，且计价分支要改 `TradeOrderUpdateServiceImpl` 与
`TradePriceServiceImpl`；放在同一个模块内可以避免跨模块 API 与事务边界。013 把软件域放在
product-server 是因为它与交易无关，定制不是。

**被否决**：新建 maven 模块（宪法原则 IV）；放 product-server（计价要跨模块回调 trade，
两个服务互相依赖）。

---

## R92 —— 参数快照包含什么？**材质 + 区间 + 全局参数**

**决策**：`trade_customization_order.param_snapshot` 固定为：

```jsonc
{
  "material": { "id": 3, "name": "可移除铜版", "basePrice": 1138,
                "printCoefficient": 4.0, "printingCoefficient": 1.5 },
  "areaRule": { "id": 12, "minArea": 10, "maxArea": 100,
                "printCoefficient": 1.6, "dieCutCoefficient": 0 },
  "config": { "printBasePrice": 20000, "printingBasePrice": 60000,
              "dieCutBasePrice": 40000, "urgentBasePrice": 5000,
              "urgentCoefficient": 1.1 },
  "computed": { "unitArea": 0.005, "totalArea": 5.0,
                "print": 34100, "printing": 0, "dieCut": 0, "urgent": 0,
                "subtotal": 34100, "total": 34100 }
}
```

金额一律以**分**存储；面积以 **㎡**、保留 4 位小数。

**理由**：参数随时会被改价；后台详情要能解释「这单当时为什么是这个价」，也便于客诉时重算。
金额快照与 `trade_order.pay_price` 必须一致（单测锁定）。

---

## R93 —— 后台怎么筛定制单？**trade_order 加列**

**决策**：`trade_order` 增加 `customization bit(1) NOT NULL DEFAULT 0`，索引
`idx_tenant_customization (tenant_id, customization, status)`；管理端订单列表新增
`customization` 查询条件，服务端直接按列筛。C 端订单详情也用它决定是否联查扩展表。

**理由**：008 的教训是「列表与统计两处各写一套筛选」，最后出现「列表筛得出、统计数不对」；
列为单一事实来源，两处共用。

**被否决**：用 `EXISTS (SELECT 1 FROM trade_customization_order ...)` 子查询 —— 与 008 的
offline 子查询同类问题，且统计分页的 SQL 要各写一份。

---

## R94 —— 重复提交怎么防？**Redis 60 秒键**

**决策**：`SET trade:customization:submit:{userId}:{specHash} NX PX 60000`，`specHash` 是
「材质 + 宽高 + 数量 + 印刷 / 模切 / 加急 + 文件 URL 列表 + 备注」的 SHA-256。
- 抢到锁：正常建单；建单成功后把值改成 `orderId`，TTL 保持 60 秒；
- 没抢到：直接返回已建订单 `{id, payOrderId}`（前端继续去支付），不报错；
- 建单异常：删除键，允许重试。

**理由**：Q99 已拍板 60 秒；用配置指纹而不是单纯时间窗，客户改完配置重新提交不会被误挡。

**被否决**：数据库唯一索引 —— 60 秒是时间窗，不是永久唯一；前端按钮防抖 —— 刷新 / 重试绕不过。

---

## R95 —— 报价与建单怎么分工？**报价不落库，建单重算并比对**

**决策**：

- `POST /app-api/trade/customization/quote`：纯计算，不落库、不需要登录；
- `POST /app-api/trade/customization/order/create`：必须带 `expectedAmount`；服务端按最新
  参数重算，若与 `expectedAmount` 不一致，返回业务错误 `CUSTOMIZATION_PRICE_CHANGED`，
  `data` 里带新报价；**不创建订单**（Q101）。
- 客户确认后再次提交，`expectedAmount` 更新为新值。

**理由**：报价不落库避免垃圾数据；`expectedAmount` 只作为「客户看到的价格」的凭证，
最终金额永远由服务端决定。

**被否决**：报价单落库并绑定 token —— 多一张表与过期清理，收益只是少算一次；
直接按最新价建单 —— 客户会在支付页看到没同意过的金额。

---

## R96 —— 定制单如何隔离促销？**强制不参与**

**决策**：创建定制单时强制 `couponId = null`、`pointStatus = false`；隐藏 SKU 不参加任何
折扣 / 秒杀 / 积分活动；运费仍由既有配送模板计算。

**理由**：规格已满足「默认不叠加」；定制价已是按成本系数算出的价格，叠加优惠券会让
「最低收费」失去意义，也需要重新定义券的分摊规则。

**被否决**：允许用券 —— 要定义「券作用在材料费还是总价」、退款时如何分摊，超出本期范围。

---

## R97 —— 设计文件怎么存与访问？**复用现有上传 + 白名单校验**

**决策**：

- 前端调用既有 `POST /app-api/infra/file/upload` 上传，拿到 URL 后随规格提交；
- 服务端校验：数量 1~5、扩展名 ∈ {pdf, ai, cdr, jpg, jpeg, png}、URL 必须以配置的
  存储域名开头（`yudao.customization.file-domain`，默认 `https://img.yuwangchenfa.com`）；
- URL 与元数据（文件名、大小、类型）以 JSON 存在 `trade_customization_order.files`；
- 后台与客户都通过订单详情里的 URL 下载。

**已知限制（本期接受）**：文件存的是**公开可读**的对象存储地址（与商品图一致），URL 不带
签名、不可枚举但泄露即可访问。单文件上限与上传接口的 **16MB** 对齐（Q106），
所以任何经本商城上传通道的文件都不会超过该值；服务端仍无法核验「URL 指向文件的真实大小」，
这一条保留为已知限制。后续如需要私密存储，再加「私有 bucket + 签名 URL / 代理下载」，不改订单结构。

**被否决**：为此新建一条私有上传通道 —— 要改 infra 文件模块与 OSS 配置，超出本期；
把文件塞进数据库 —— 16MB × 5 的单子会拖垮库。

---

## R98 —— 管理端怎么组织？**一个分组、两个页面、幂等菜单**

**决策**：

- 菜单分组「定制印刷」，下挂「材质管理」（材质 + 面积区间同表单）与「计价参数」；
- 权限点 `trade:customization-material:*`、`trade:customization-config:*`，订单查询复用既有
  `trade:order:query`；
- 菜单与权限用既有的幂等 SQL 写法（`INSERT ... ON DUPLICATE KEY UPDATE`，与 026 一致）。

**理由**：材质与区间必须一起保存才能校验连续性；计价参数是全局单行配置，单独一页更清楚。
