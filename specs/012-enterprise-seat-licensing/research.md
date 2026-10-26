# 技术决策与被否决方案（012 企业客户与席位授权）

> 承接规格 Q65~Q74。编号续 011（R57~R61）。

---

## R62 —— 授权主体拆分：4 张新表 + 3 个既有表加列

**问题**：009 把「付钱的人」和「拿权益的人」写成同一个 `member_id`。企业场景必须拆开，
但 `member_subscription` 是刚上线、有流水语义的表，不能改成多态。

**决策**：

| 改动 | 内容 |
|---|---|
| 新增 4 表 | `maxlabel_org`、`maxlabel_org_member`、`maxlabel_org_license`、`maxlabel_org_seat` |
| `maxlabel_subscription_plan` | 加 `scope`（1 个人 / 2 企业） |
| `product_spu` | 加 `enterprise_only`（企业商品不进入公开商城，R66） |
| `trade_order` | 加 `maxlabel_org_id`（下单时快照企业，R65） |

**为什么下单时要快照 `maxlabel_org_id`**：支付回调可能晚于下单；若买家在支付前被移出企业，
按「支付时查买家当前企业」会发放失败或发错企业。规格的边界情况明确要求「购买人被移出企业，
已购批次仍归企业」——不落列就无法保证。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 给 `member_subscription` 加 `subject_type/subject_id` 多态 | 改刚上线的流水表与全部查询、索引与幂等键；回归面远大于新增并行表 |
| 为企业建一个特殊 `member_user` 账号代持权益 | 企业不是登录身份；模板/打印记录归属、席位分配、管理员角色都会被搅在一起 |
| 支付时按买家当前企业解析，不落订单快照 | 支付前换企业/被移出 → 已付款订单无法归属，违反 FR-308 的边界 |

---

## R63 —— 有效权益解析：并集 + `max(end_time)`，出口仍是 `getCurrent`

**决策**：

- 在 `MemberSubscriptionServiceImpl#getCurrent(memberId)` 内部合并两个来源：
  - 个人：`member_subscription` 的最大 `end_time`；
  - 企业：该会员有有效席位、企业启用、且企业至少一条 `maxlabel_org_license.end_time > NOW()` 时，
    取企业所有未过期批次的最大 `end_time`。
- 结果取较晚者；并列时以个人来源为准。
- 响应**只加字段**：`sourceType`（1 个人 / 2 企业）、`orgId`、`orgName`。既有字段语义不变。
- 两个云端写入闸门（`LabelTemplateServiceImpl`、`SharedTemplateServiceImpl`）**一行不改**。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 新增 `/member/subscription/effective`，前端自己合并两个接口 | 桌面端、门面站、小程序三处都要改；任一处漏改就会出现“权益有效但上传被拒” |
| 把企业席位物化成 `member_subscription` 行 | 撤销/过期时要删或改流水；与 009 “续费插新行、不改旧行”的语义冲突 |
| 为每个席位单独记录到期日 | 规格 Q71 已定席位不绑批次；会制造“续费后老席位失效”的客户故障 |

---

## R64 —— 席位容量与并发：企业行锁 + `max(0, …)`

**决策**：

```text
可用容量 = max(0, Σ 未过期批次的 seats_total − 有效席位数)
```

- 分配/撤销/转交 OWNER 都在同一事务内对 `maxlabel_org` 行加 `FOR UPDATE`；
- `maxlabel_org_seat` 唯一键 `(tenant_id, org_id, member_id)`，撤销后复用同一行；
- 容量收缩（旧批次过期、续费变少）**不回收**已有席位，只阻止新增（Q71）；
- 并发争抢最后一个席位时，行锁 + 二次容量校验保证最多一个成功（SC-117）。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 席位绑定具体授权批次 | 续费插新批次后，老席位会失效；要么迁移要么重新分配，客户每年被打断一次 |
| 用数据库计数列 `used_seats` | 撤销、过期、跨批次都要维护计数，容易与实物不一致；派生值更可靠 |
| 不锁，靠唯一键兜底 | 唯一键只能防重复分配同一人，防不住“20 个席位发出 21 个” |

---

## R65 —— 支付发放分支：同一个 Handler，按 `plan.scope` 分流

**决策**：

- `TradeMaxLabelSubscriptionOrderHandler#afterPayOrder` 继续逐订单项解析计划；
- `scope=个人` → 走现有 `MemberSubscriptionApi.grant`；
- `scope=企业` → 调 `MemberOrgApi.grantLicense`：
  - `seats_total = orderItem.count`（一个购买单位 = 一个席位）；
  - 周期取 `plan.period_months`，**不乘** `orderItem.count`（数量是席位数）；
  - `org_id = order.maxlabelOrgId`（R62 的快照）；
  - 幂等键仍为 `source_order_item_id` 唯一索引；
  - 顺延算法复用 009 的行锁与幂等，但起点取**原到期日**：`max(today, 上批次 end_time 的日期 − 1 天)`。
- `afterCancelOrder/afterCancelOrderItem` 同时标记个人订阅与企业授权批次（R72）。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 新开一个 `TradeEnterpriseOrderHandler` | 同一个订单可混买个人与企业商品，两个 handler 会重复解析 SKU，且执行顺序无人保证 |
| 在 trade 里直接写企业授权表 | 跨模块写库；微服务拆分后立刻失效 |
| 发放时按买家当前企业解析 | 见 R62：支付前换企业/被移出会导致发放失败或错发 |

---

## R66 —— 企业商品可见性：SPU 标记 + 公共接口过滤 + 企业中心专用详情

**决策**：

1. `product_spu.enterprise_only`（默认 0）；商品编辑页加「企业专享」开关。
2. 公开接口一律排除企业商品：
   - `ProductSpuMapper#selectPage(AppProductSpuPageReqVO, categoryIds)` 加 `enterprise_only = 0`；
   - `AppProductSpuController#getSpuDetail` 命中企业商品时直接拒绝；
   - `AppProductSpuController#getSpuList`（`/product/spu/list-by-ids`）**按调用者过滤**：
     未登录/非企业管理员排除，企业 OWNER/ADMIN 保留（购物车/订单渲染需要）；
   - 商品筛选维度（`ProductFilterController`）同步排除。
3. 企业商品详情与列表走**企业中心专用接口**：`/product/subscription-plan/enterprise/*`，
   服务端校验调用者是启用企业的 OWNER/ADMIN（product-server 已依赖 member-api，无新模块依赖）。
4. 计划创建校验：`scope=2` 时 SPU 必须 `enterprise_only=1`；`scope=1` 时必须为 0。
5. 促销活动是公开商城的另一条露出口：活动配置 MUST 拒绝 `enterprise_only=1` 的商品，
   否则它会从秒杀/拼团/砍价/积分页出现。选择“配置时拒绝”而不是“响应时过滤”，
   因为企业商品本来就不该进入促销活动；配置入口挡住一次，后续所有活动接口都不用改。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 放在一个隐藏分类里 | 分类页/搜索/推荐都可能漏出来；运营误改分类就泄露 |
| 只在前端隐藏 | 直接调接口就能看到价格与详情，违反 Q72 |
| 用 SPU 名称或关键词匹配 | 运营改个名字就失效 |

---

## R67 —— 发票抬头强制：下单入口校验，复用 `InvoiceValidator`

**决策**：

- 在 `TradeOrderUpdateServiceImpl#createOrder` 构建订单项之后、落库之前：
  - 任一订单项命中 `scope=2` 的计划 → 必须同时满足：
    1. 买家是启用企业的 OWNER/ADMIN（`MemberOrgApi`）；
    2. 请求里有 `invoice`，且 `invoice.titleKind = 企业(1)`。
  - 不满足时抛新的 trade 错误码，**不创建订单**（不是创建后再作废）。
- 抬头字段的合法性继续由 `InvoiceValidator` 负责（企业抬头要求税号），不复制规则。
- 混合订单整单强制企业抬头；买家要给个人商品开个人票时拆单（Q73 / Assumptions）。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 在 `TradeOrderInvoiceServiceImpl#createInvoice` 里拦 | 它在订单已落库后执行；虽然同事务可回滚，但错误码会与“订单创建”混在一起，前端不好提示 |
| 只在结算页禁用个人抬头 | 直接调接口可绕过；违反宪法原则 III |
| 允许企业订单不开发票 | 与 Q73“必须使用企业类型抬头”直接冲突 |

---

## R68 —— OWNER 唯一与转交：企业行锁 + 单事务换角色

**决策**：

- `maxlabel_org.owner_member_id` 是唯一权威；`maxlabel_org_member.role` 中 OWNER 行必须与之一致。
- 转交：锁企业行 → 校验目标成员属于本企业 → 原 OWNER 改 ADMIN、目标改 OWNER → 更新 `owner_member_id`。
- 运营代转走同一服务方法，只多一层权限校验与操作日志。
- 任何会令企业失去 OWNER 的操作（移出、退出、取消角色）在服务层拒绝（FR-291）。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 只靠 `role` 判断 OWNER，不落 `owner_member_id` | “有且仅有一个”无法用普通唯一索引约束，并发转交会出现双 OWNER |
| 允许双 OWNER | 与 FR-291 冲突；席位/购买主体归属会变得不唯一 |

---

## R69 —— 管理端、菜单与权限

**决策**：

- 新增「企业管理」页面（会员中心下）与企业的成员/席位/授权批次页签；
- 会员详情新增只读「所属企业」信息（不新增可编辑入口）；
- 菜单与按钮权限写进 `021-enterprise-seat-licensing.sql`（沿用 013 的幂等 `WHERE NOT EXISTS` 写法）：
  `member:org:query/create/update`、`member:org:member:add/remove/update-role`、
  `member:org:owner:transfer`、`member:org:seat:assign/revoke`、`member:org:license:query`。
- 计划页加 `scope` 列与筛选；商品编辑加 `enterpriseOnly` 开关（沿用既有 SPU 权限）。

**为什么权限点要单独登记**：未登记成 `system_menu` 行时，`v-hasPermi` 会把按钮从 DOM 删掉，
超管也看不到；后端 `@PreAuthorize` 却因超管旁路能调 —— 表现为“接口能通、按钮不显示”（013 已踩过）。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 复用 `member:user:update` 做企业操作 | 权限过宽，客服角色会顺带获得改企业/分配席位的能力 |
| 企业管理挂在「系统管理 → 部门」下 | 那套是后台组织架构，与 C 端会员企业不是同一个东西 |

---

## R70 —— H2 测试 DDL 与模块测试

**决策**：

- member：`create_tables.sql` / `clean.sql` 增加 4 张新表；
- product：`create_tables.sql` 的 `maxlabel_subscription_plan` 加 `scope`，`product_spu` 加 `enterprise_only`；
- trade：`create_tables.sql` 的 `trade_order` 加 `maxlabel_org_id`；
- 每个新服务先写失败测试，再写实现（宪法原则 I）。

**已知坑**（沿用项目记忆）：Maven 跑模块测试要带 `-am`，否则会读到 D 盘仓库里过期的兄弟模块 jar；
IDE 写进 `target/classes` 的脏 class 会让测试成片报 `VerifyError`。

---

## R71 —— 既有 `EnterpriseView` 与 FR-049 的冲突

**事实**：`storefront/src/views/EnterpriseView.vue` 是 001 的企业采购落地页，
其测试明确断言「不得产生线上企业交易能力」（FR-049，`EnterpriseView.spec.ts`），
页面上只有权益说明与联系方式。

**决策**：

- `/enterprise` **保留为公开获客页**，新增一个「企业管理员登录 / 进入企业中心」入口；
- 真正的交易与企业商品详情放在**需要登录**的 `/enterprise/center` 与 `/enterprise/plan/:skuId`；
- 公开页仍不下单、不授信、不收集表单，因此现有 FR-049 断言继续成立；
- 更新页面注释与 CTA 文案，新增企业中心的测试；在 spec 中记录 012 只对“已认证企业中心”放开交易能力。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 直接把 `/enterprise` 改成交易页 | 会破坏 FR-049 的既有断言，也未登录用户没有席位上下文 |
| 删掉落地页 | 丢失获客与联系内容，且与既有导航/SEO 冲突 |

---

## R72 —— 退款标记：扩展现有方法，不改 API

**决策**：`MemberSubscriptionServiceImpl#markRefundedByOrder/ByOrderItem` 在更新
`member_subscription.refunded` 的同时，按相同条件更新 `maxlabel_org_license.refunded`。
API 签名不变，trade 的退款钩子无需改动。

**被否决的方案**：新增企业退款 API —— trade 需要多调一次，且两条路径可能一条成功一条失败；
同一事务里一次做完更可靠。

---

## R73 —— 桌面端只加展示字段

**决策**：

- `MallMember` 增加 `subscriptionSource?: 'personal' | 'enterprise'` 与 `orgName?: string`；
- `mallRepository.fetchMallSubscription` 映射 `sourceType/orgName`；
- `MallAccountDialog` 显示「企业授权（XX 公司）」；其余显示逻辑不变；
- `entitlementService` 不感知来源：企业席位与个人订阅在能力上等价（Q69）。
- 新增/扩展 `mall-repository` 与 `MallAccountDialog` 的测试。

**被否决的方案**：把企业来源塞进 `edition` 档位 —— 会把“来源”误当成“版本”，
且 009 的 Q51/Q69 明确不做功能档位差异。

---

## R74 —— 上线顺序与回滚

**上线顺序**（不可乱）：

1. 执行 `sql/mysql/021-enterprise-seat-licensing.sql`（4 表 + 3 列 + 菜单权限）；
2. 发后端（member → product → trade 一起发；三者有 API 依赖）；
3. 发管理端与门面站；
4. 发桌面端（只加展示字段，可与第 3 步并行）。

**回滚**：先回滚桌面端/前端（旧前端不读新字段也能工作），再回滚后端，最后按依赖顺序
drop 4 张表；`trade_order.maxlabel_org_id`、`product_spu.enterprise_only`、
`maxlabel_subscription_plan.scope` 可保留（旧代码忽略未知列），不要在有企业数据时删列。

---

## R75 —— 企业商品的其他可见性旁路：结算、购物车、收藏、浏览记录

**问题**：只过滤“商品列表/详情/搜索”不够。以下入口都能凭 SPU/SKU ID 拿到企业商品的名称或价格：

| 入口 | 现状 | 泄露内容 |
|---|---|---|
| `GET /trade/order/settlement-product` | `@PermitAll`，按 `spuIds` 算价 | 价格 |
| `POST /trade/cart/add` / `update-count` | 只校验 SKU 存在与库存 | 加购后购物车返回名称/价格 |
| `GET /trade/cart/list` | 直接读 `ProductSpuApi.getSpuList` | 历史残留的名称/价格 |
| `GET /trade/order/settlement` | 只算价，不校验企业专享 | 价格 |
| `POST /product/favorite/create` | **完全不校验 SPU** | 收藏后可读名称/价格 |
| `GET /product/favorite/page`、`GET /product/browse-history/page` | 直接读 `ProductSpuService.getSpuList` | 历史残留的名称/价格 |

**决策**：

1. **统一校验**：在 trade 抽 `validateEnterpriseSku(userId, skuId)`，命中 `enterprise_only=1`
   时要求调用者是启用企业的 OWNER/ADMIN；`cart add/update`、`settlement`、`createOrder` 共用。
2. **公开价格展示**：`GET /trade/order/settlement-product` 对**所有人**排除企业商品；
   企业管理员从企业计划接口取价，不依赖这个公开接口。
3. **历史残留**：`cart/list`、`favorite/page`、`browse-history/page` 对非企业管理员过滤企业商品；
   企业管理员保留（购物车/结算需要）。
4. **收藏创建**：`ProductFavoriteServiceImpl#createFavorite` 校验 SPU 存在且非企业专享
   （企业管理员除外）。
5. **不要改 `ProductSpuService.getSpuList`**：管理端、订单内部都在用，过滤会把企业订单也弄坏。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 只靠下单入口拒绝 | 下单前购物车/结算已经展示了价格，泄露已经发生 |
| 在 `ProductSpuService.getSpuList` 统一过滤 | 管理端与订单内部也在用；会把企业订单/后台详情一起过滤掉 |
| 只在前端隐藏购物车/收藏里的企业商品 | 直接调接口仍能拿到；违反宪法原则 III |
