# Implementation Plan: 企业客户与席位授权（012）

**Branch**: `012-enterprise-seat-licensing` | **Date**: 2026-10-26
**Spec**: [spec.md](./spec.md) | **Base**: [009 会员体系](../009-maxlabel-member-integration/spec.md)

## Summary

把「付钱的人」和「拿权益的人」拆开：企业购买按席位的授权，再把席位分配给旗下会员。

- **新增 4 张表**：企业、企业成员、企业授权批次、席位；
- **3 个既有表各加 1 列，批次表再加 `folded_time`**：计划 `scope`、商品 `enterprise_only`、
  订单 `maxlabel_org_id`、批次折算时间；
- **权益出口不变**：`GET /member/subscription/get` 内部合并「个人订阅 ∪ 企业席位」，
  只加 `sourceType/orgId/orgName`；两个云端写入闸门一行不改；
- **不伪造订阅流水**：企业授权单独记账，席位不绑批次（Q71），复购不打断已分配成员；
  复购时把旧批次剩余时长折算进新席位（Q78）；
- **企业商品不进入公开商城**，只能从登录后的企业中心进入（Q72）；
- **企业订单强制企业抬头**（Q73），**用户端不支持退款**（Q79）。

## Technical Context

**语言 / 栈**：Java 17+（yudao-cloud）+ Vue 3 / TypeScript（storefront、admin-vue3、maxlabel 桌面端）
**数据**：MySQL；4 张新表 + 3 列；沿用 `tenant_id` 与 `BaseDO`
**测试**：JUnit（member / product / trade 三模块）+ Vitest（storefront / admin / desktop）
**接口**：C 端只走 `/app-api`，管理端只走 `/admin-api`（宪法）
**约束**：复用 009 的订阅顺延、006/011 的发票抬头、007 的虚拟商品、013 的菜单权限写法
**规模**：一期企业数量少、单企业成员/席位为几十到几百；沿用既有分页，不设硬上限，
也不新增并发规模目标（席位分配用企业行锁串行化）

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 每个新 Service / Controller 先写失败测试（企业、成员、席位、批次、权益解析、下单校验）；DDL 与 H2 同步属于已声明的结构性例外 |
| **II. 先澄清** | ✅ Q65~Q74 已拍板；剩余小口径写入 Assumptions |
| **III. 复用** | ✅ 复用订阅顺延算法、虚拟商品、结算/支付/开票、菜单权限、RPC 模式；不新建支付或身份体系 |
| **IV. 最小改动** | ✅ 4 表 + 3 列；`member_user`、`member_subscription`、模板/打印表不改语义；闸门零改动 |
| **V. 可验证** | ✅ SC-105~SC-120 在 quickstart 有逐条落点；既有 009 用例继续跑 |

## 关键设计决策

完整论证见 [research.md](./research.md)（R62~R74）：

| # | 决策 |
|---|---|
| R62 | 授权主体拆分：4 新表 + 3 列；订单快照 `maxlabel_org_id`，防止支付前买家被移出导致发放失败 |
| R63 | 权益解析 = 个人 ∪ 企业，取较晚到期；出口仍是 `getCurrent`，只加字段 |
| R64 | 席位不绑批次；容量 = `max(0, 未过期席位总数 − 有效席位数)`；企业行锁保证不超发 |
| R65 | 同一个支付 Handler 按 `plan.scope` 分支；企业批次幂等键仍是 `source_order_item_id` |
| R66 | 企业商品用 `product_spu.enterprise_only` + 公开接口过滤（含 `list-by-ids`）+ 促销活动配置拒绝 + 企业中心专用详情 |
| R67 | 企业订单在下单入口校验买家角色与 `invoice.titleKind=企业`，字段规则复用 `InvoiceValidator` |
| R68 | OWNER 唯一由 `maxlabel_org.owner_member_id` 保证；转交在单事务内换角色 |
| R69 | 企业管理页 + 会员详情只读所属企业 + 计划 scope + 商品企业专享开关；权限点登记进 `system_menu` |
| R70 | H2 DDL 同步；Maven 测试带 `-am`，清掉 IDE 脏 class |
| R71 | `/enterprise` 保留公开获客页；交易放登录后的 `/enterprise/center`，现有 FR-049 断言继续成立 |
| R72 | 退款标记扩展现有方法，同时更新个人订阅与企业批次 |
| R73 | 桌面端只加 `subscriptionSource/orgName` 展示，不改权益判定；**离线兜底仍用本机时间判过期**（2026-10-27 所有者确认，见 quickstart §10.4） |
| R74 | 上线顺序：SQL 021 → 后端 → 管理端/门面站 → 桌面端 |
| R75 | 企业商品的结算/购物车/收藏/浏览记录旁路：trade 统一 `validateEnterpriseSku`；`settlement-product` 一律排除；列表按角色过滤 |

## 代码落点

```text
yudao-cloud/
├── sql/mysql/021-enterprise-seat-licensing.sql          # 4 表 + 3 列 + 菜单权限（幂等）
├── yudao-module-member/
│   ├── yudao-module-member-api/.../api/org/
│   │   ├── MemberOrgApi.java                             # 供 product / trade 调用
│   │   └── dto/{MemberOrgRespDTO,MemberOrgLicenseGrantReqDTO}.java
│   └── yudao-module-member-server/src/main/java/.../
│       ├── dal/dataobject/org/{MaxLabelOrgDO,MaxLabelOrgMemberDO,
│       │                      MaxLabelOrgLicenseDO,MaxLabelOrgSeatDO}.java
│       ├── dal/mysql/org/{MaxLabelOrgMapper,MaxLabelOrgMemberMapper,
│       │                   MaxLabelOrgLicenseMapper,MaxLabelOrgSeatMapper}.java
│       ├── service/org/{MaxLabelOrgService,MaxLabelOrgMemberService,
│       │                MaxLabelOrgSeatService,MaxLabelOrgLicenseService}.java
│       ├── api/org/MemberOrgApiImpl.java
│       ├── controller/app/org/{AppMemberOrgController,vo/*}.java
│       ├── controller/admin/org/{AdminMemberOrgController,vo/*}.java
│       └── service/subscription/MemberSubscriptionServiceImpl.java
│           # getCurrent 合并企业来源；markRefunded 同时更新企业批次
├── yudao-module-mall/yudao-module-product-api/.../
│   ├── dto/ProductSubscriptionPlanRespDTO.java            # scope
│   ├── dto/ProductSpuRespDTO.java                         # enterpriseOnly
│   └── subscriptionplan/ProductSubscriptionPlanApi.java   # getBySkuId 返回 scope
├── yudao-module-mall/yudao-module-product-server/src/main/java/.../
│   ├── dal/dataobject/spu/ProductSpuDO.java               # enterpriseOnly
│   ├── dal/dataobject/subscriptionplan/MaxLabelSubscriptionPlanDO.java  # scope
│   ├── dal/mysql/spu/ProductSpuMapper.java                # 公开分页过滤
│   ├── service/spu/ProductSpuServiceImpl.java
│   ├── service/subscriptionplan/MaxLabelSubscriptionPlanServiceImpl.java
│   ├── controller/app/spu/AppProductSpuController.java    # 详情拒绝 + list-by-ids 按调用者过滤
│   ├── controller/app/favorite/AppFavoriteController.java # 收藏创建/列表过滤企业商品
│   ├── controller/app/history/AppProductBrowseHistoryController.java  # 浏览记录过滤
│   ├── service/favorite/ProductFavoriteServiceImpl.java   # 收藏创建校验
│   ├── controller/app/subscriptionplan/{AppMaxLabelSubscriptionPlanController,vo/*}.java
│   └── framework/rpc/config/RpcConfiguration.java         # 注册 MemberOrgApi
├── yudao-module-mall/yudao-module-trade-server/src/main/java/.../
│   ├── dal/dataobject/order/TradeOrderDO.java             # maxlabelOrgId
│   ├── service/order/TradeOrderUpdateServiceImpl.java     # 结算/下单校验 + 快照
│   ├── service/cart/CartServiceImpl.java                  # 加购/改数量校验企业商品
│   ├── service/price/TradePriceServiceImpl.java           # settlement-product 排除企业商品
│   ├── service/order/handler/TradeMaxLabelSubscriptionOrderHandler.java
│   └── framework/rpc/config/RpcConfiguration.java         # 注册 MemberOrgApi
├── yudao-module-mall/yudao-module-promotion-server/src/main/java/.../
│   └── service/                                          # 活动保存拒绝企业专享商品
├── yudao-ui/yudao-ui-admin-vue3/src/
│   ├── api/member/org/index.ts
│   ├── api/mall/product/subscriptionPlan/index.ts         # scope
│   ├── api/mall/product/spu/index.ts                      # enterpriseOnly
│   └── views/
│       ├── member/org/{index,OrgForm}.vue
│       ├── member/org/detail/{index,OrgMemberList,OrgSeatList,OrgLicenseList}.vue
│       ├── member/subscription/plan/{index,PlanForm}.vue  # scope 列/筛选/表单
│       ├── member/user/detail/UserBasicInfo.vue           # 只读所属企业
│       └── mall/product/spu/form/OtherForm.vue            # 企业专享开关
├── storefront/src/
│   ├── api/{org.ts,subscription.ts}
│   ├── views/{EnterpriseCenterView,EnterprisePlanView}.vue   # 新增
│   ├── views/EnterpriseView.vue                              # 加“进入企业中心”CTA
│   ├── router/index.ts                                       # /enterprise/center、/enterprise/plan/:skuId
│   └── styles/design-consistency.spec.ts                     # 登记两个新视图
└── ../maxlabel/app/src/
    ├── shared/ipcContract.ts                              # subscriptionSource/orgName
    ├── main/mallRepository.ts                             # 解析 sourceType/orgName
    └── renderer/src/dialogs/MallAccountDialog.tsx         # 显示企业授权
```

**测试落点**

| 模块 | 测试 |
|---|---|
| member | `MaxLabelOrgServiceTest`、`MaxLabelOrgSeatServiceTest`、`MaxLabelOrgLicenseServiceTest`、`MemberSubscriptionServiceImplTest`（合并来源/退款）、app/admin 控制器契约测试 |
| product | `MaxLabelSubscriptionPlanServiceImplTest`（scope/单 SPU/企业标记）、公开商品可见性测试、企业计划接口权限测试 |
| trade | `TradeMaxLabelSubscriptionOrderHandlerTest`（企业分支/幂等/退款）、订单创建测试（买家角色、抬头强制） |
| storefront | `EnterpriseCenterView.spec.ts`、`EnterprisePlanView.spec.ts`、`api/org.spec.ts`、`EnterpriseView.spec.ts`（CTA） |
| admin | 企业列表/详情/成员/席位/批次 spec、计划 scope spec、商品企业专享 spec |
| desktop | `mall-repository` 集成测试（sourceType/orgName）、`MallAccountDialog` 展示测试 |

## 实施分期

| 阶段 | 内容 | 交付后 |
|---|---|---|
| ① 数据层 | `021` SQL、4 个 DO/Mapper、3 列、错误码、H2 DDL | 表与列可用，旧代码不受影响 |
| ② member 域 | 企业/成员/席位/批次服务（测试先行）、权益解析、app/admin 控制器、`MemberOrgApi` | 企业中心接口可用；订阅接口能返回企业来源 |
| ③ product 域 | 计划 `scope`、商品 `enterprise_only`、公开接口过滤、企业计划专用接口 | 企业商品不泄露；企业中心能取到商品 |
| ④ trade 域 | Handler 分支、订单快照、下单校验、退款标记扩展 | 企业订单能买、能发、能退 |
| ⑤ 管理端 | 企业管理页、计划 scope、商品开关、会员详情所属企业 | 运营可建档/代操作 |
| ⑥ 门面站 | 企业中心、企业商品详情、`/enterprise` CTA、路由与设计一致性 | 企业可自助购买与分配 |
| ⑦ 桌面端 | `MallMember` 字段、映射、账户弹层展示、测试 | 成员能看到“企业授权（XX 公司）” |
| ⑧ 上线 | SQL → 后端 → 管理端/门面站 → 桌面端 | 功能可用；按 quickstart 验收 |

## 主要风险

| # | 风险 | 对策 |
|---|---|---|
| R62 | 支付前买家被移出企业，批次发不出去/发错企业 | 下单时把企业快照进 `trade_order.maxlabel_org_id`；支付只认快照 |
| R63 | 两个来源合并后 `endDate/sourceType` 口径不一致 | 单测钉住：只有个人、只有企业、并存取较晚、并列取个人、企业过期 |
| R64 | 并发分配超发最后一个席位 | 企业行锁 + 锁内二次容量校验 + `(org, member)` 唯一键；并发负向测试 |
| R64 | 旧批次过期后误把已有席位回收 | 席位不绑批次；容量按 `max(0, …)`；quickstart 5.3/5.4/5.5 手工验 |
| R65 | 同一个订单项重复回调重复发批次 | `uk_source_item` 唯一键 + 先查后插 + 捕获 `DuplicateKeyException` |
| R66 | 企业商品从搜索/筛选/推荐/促销漏出 | 列表、详情、`list-by-ids`（按调用者）、筛选四处过滤；促销活动配置拒绝；直接访问详情返回 `SPU_ENTERPRISE_ONLY` |
| R67 | 企业订单用个人抬头 | 下单入口硬校验；混合订单整单企业抬头；quickstart 2.5/2.6 |
| R75 | 企业商品从结算/购物车/收藏/浏览记录漏出 | 统一 `validateEnterpriseSku`；`settlement-product` 一律排除；cart/favorite/history 按角色过滤 |
| R68 | 并发转交出现双 OWNER | 企业行锁；转交单事务；SC-120 负向并发测试 |
| R69 | 权限串没登记，后台按钮不显示 | `021` 幂等插入 `system_menu`；上线后人工勾角色 |
| R70 | Maven 测试误报（过期 jar / 脏 class） | 带 `-am`；必要时 `mvn clean`；错误指向未改模块时先怀疑这两点 |
| R71 | 改动 `EnterpriseView` 触发 FR-049 回归 | 公开页不加交易入口，只加“进入企业中心”链接；保留原断言 |
| R74 | SQL 未跑就发后端 → 查询新列 500 | 严格执行 SQL → 后端 → 前端；`021` 可重复执行 |

## Project Structure

```text
specs/012-enterprise-seat-licensing/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── app-api.md
│   └── admin-api.md
├── checklists/requirements.md
└── tasks.md            # 由 /speckit-tasks 生成
```

**Structure Decision**：沿用现有单体聚合 + 模块化结构。企业域放在 `member-server`
（身份与权益的归属模块），商品/计划仍在 `product-server`，下单与发放编排在 `trade-server`；
跨模块只通过 `member-api` / `product-api` 的 RPC 接口，禁止直接写对方表。

## Complexity Tracking

无宪法违规。以下为实现所需、已在 research 中论证的最小扩展：

| 扩展 | 为什么必须 | 更简单的替代为何不可行 |
|---|---|---|
| `trade_order.maxlabel_org_id` | 支付回调必须知道订单属于哪个企业 | 支付时查买家当前企业，买家换企业/被移出会发错或发不出（R62） |
| `product_spu.enterprise_only` | 公开商城必须排除企业商品 | 只在前端隐藏可被直接调接口绕过（R66） |
| 4 张新表而非复用 `member_subscription` | 企业授权有独立主体、席位与批次语义 | 多态改造会动刚上线的流水表与全部查询（R62） |
