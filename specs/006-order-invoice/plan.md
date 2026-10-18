# Implementation Plan: 订单开票（下单申请 · 后台开具上传 · 前台下载）

**Branch**: `006-order-invoice` | **Date**: 2026-10-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-order-invoice/spec.md`

## Summary

今天这个商城**没有任何发票能力**（后端 / C 端 / 管理端全仓搜 `invoice`、`发票` 零命中），
开票全靠客户找张经理、系统不留痕。本特性把这条链路搬进来：**下单时申请 → 后台看到并上传开好的票 → 客户自己下载**。

五条已拍板决策（spec 的 Q27~Q31）决定了它的形状：

| 决策 | 对实现的直接影响 |
|---|---|
| **Q27** 只有两种电子票、不支持纸质邮寄 | 没有邮寄方式、没有物流单号、没有收件地址那一套字段 |
| **Q28** 能补开、**申请提交后不可改** | 申请有**两个入口**（下单 / 订单详情），但**没有 update 接口** —— 少一个接口，且不需要"未开票前可改"的中间态 |
| **Q29** 后台权限走既有角色体系 | 菜单 + 权限点 + 接口 `@PreAuthorize`，不新造角色 |
| **Q30** 退款 / 取消 → **未开具的申请作废** | **复用既有扩展点**：`TradeOrderHandler.afterCancelOrder` —— 见下 |
| **Q31** 重新上传覆盖、不留痕 | 申请上就一个 `invoice_file_url` 单列，不做历史表、不做附件表 |

**本期四块改动**：

| 端 | 改动 |
|---|---|
| **服务端** | 2 张新表；1 个枚举；5 个新 app 接口（抬头簿 CRUD + 补开）；2 个既有 app 接口**只做加法**（下单入参加 `invoice`、订单详情/列表加开票信息）；2 个 admin 接口；**1 个新的 Spring Bean**（`TradeOrderHandler`）负责作废 |
| **管理端** | 1 个新菜单「开票申请」（列表 + 状态筛选）+ 1 个上传弹窗；1 个菜单/权限迁移脚本 |
| **C 端前台** | 1 个新页面（发票抬头簿）+ 结算页发票卡片 + 订单详情两个入口（补开 / 下载）+ 订单列表状态 |
| **数据库** | 1 个建表迁移脚本 + 1 个菜单权限脚本，**生产库各执行一次** |

### 两个"不用改既有逻辑"的地方（本计划的中心思想）

1. **作废靠既有扩展点，不靠改取消逻辑**。`TradeOrderUpdateServiceImpl` 第 104 行注入
   `List<TradeOrderHandler> tradeOrderHandlers`，`afterCancelOrder(order, orderItems)` 在
   **取消订单（第 638 行）与售后全额退款取消订单（第 663 行）两处**都会被调用。
   于是 Q30 只要**新增一个 `TradeInvoiceOrderHandler`** Spring Bean —— C 端取消、系统超时取消、
   全额退款取消三条路一次覆盖，`cancelOrder*` 三个方法**一行都不碰**。
2. **申请挂在新建的 1:1 子表上，不往 `trade_order` 加列**。既有订单表是全局最热的表，
   开票的 6 个字段平铺进去会让每一条订单查询都多背一份与它无关的数据；而 FR-179
   （历史订单视为"未申请"）在子表上**天然成立**（没有行就是没申请），一行数据都不用补。

## Technical Context

**Language/Version**: Java 25 + yudao（服务端）；TypeScript 6.x / Node ≥ 20.19 / Vue 3.5.x（C 端）；
Vue 3.x + Element Plus + Vite（管理端）
**Primary Dependencies**: **不新增任何依赖**。服务端用既有的 `BaseMapperX` / `BaseDO` / MapStruct 转换器 /
`Jackson3TypeHandler`；C 端沿用既有无 UI 组件库的栈；管理端沿用 Element Plus 与既有 `UploadFile` 组件
**Storage**: MySQL（宪法约束）。两张新表 `trade_order_invoice`（1:1 挂订单）与 `trade_invoice_title`（抬头簿）；
发票文件**不落库、只存 URL**，文件本体在既有对象存储（复用 `POST /admin-api/infra/file/upload`）
**Testing**: **测试先行（宪法原则 I）**，五层见「测试策略」。
⚠️ `yudao-module-trade-server` **目前没有任何 `src/test/java`**（只有 `src/test/resources/` 的
`application-unit-test.yaml`、`sql/create_tables.sql`、`sql/clean.sql`）—— 本特性是**该模块第一次写单测**，
照 `yudao-module-product-server` 的 `ProductFilterValueServiceImplTest`（`@Import({...ServiceImpl})` +
`extends BaseDbUnitTest` + H2）来
**Target Platform**: 现代浏览器（桌面优先，C 端已有 1100/768/480 三个断点）+ 管理端后台
**Project Type**: 多端（服务端 + Web 管理端 + Web C 端），符合宪法「四端结构」中的三端；
小程序 / App 端本期不参与（spec 的 Assumptions）
**Performance Goals**: 不新增性能要求。唯一的重查询是后台「待开票」分页（一次 join 分页），
量级是个位数到几十条；客户端侧沿用 001 的 SC-004（首屏 ≤ 3 秒）判据
**Constraints**:
- 不引入 UI 组件库；C 端只走 `/app-api`、管理端只走 `/admin-api`
- **`trade_order` 表结构一个字节都不动**（见 Summary 第 2 点）
- 既有 app 接口（下单、订单详情、订单列表）**只做加法**：新字段可选、不传/无数据时行为逐字不变
- 开票**不接任何开票平台**，也不在服务端做任何外部调用（FR-180）
- 发票文件沿用既有对象存储 → 链接**公网可读**（spec 已作为显式接受的风险记录）
**Scale/Scope**: 5 个用户故事；服务端 **2 张表 + 1 枚举 + 9 个接口 + 1 个 handler**；
管理端 **1 个页面 + 1 个弹窗 + 1 个菜单脚本**；C 端 **1 个新页面 + 3 处改动**

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

宪法版本 `.specify/memory/constitution.md` **v2.1.0**。逐条校验：

| # | 宪法原则 | 是否满足 | 依据 / 违规处理 |
|---|---|---|---|
| I | **测试先行（不可协商）** | ✅ | 每个故事的测试任务排在实现之前（`/speckit-tasks` 生成）。「测试策略」一节显式列出**每层要测什么**；其中 **R28/Q30 的"作废只在取消时发生、已开票的不许被动"** 与 **R27 的"快照不被回溯修改"** 是两条必须钉住的点 —— 它们错了**不会报错**，只会让某张票在客户手里失效、或者历史订单的开票信息被悄悄改掉 |
| II | **先澄清，不猜测** | ✅ | Q27~Q31 五条全部由所有者拍板（`/speckit-clarify` 三轮问答），无 `[NEEDS CLARIFICATION]` 残留。**其中 Q28 与 Q30 是两次方向性选择**：Q28 把"只在下单时申请"改成"还能补开"（新增 US20、订单详情多一个入口）；Q30 把"退款后的申请怎么办"从**规格的空白**补成明确规则（本条是 `/speckit-plan` 前置扫描时发现的漏洞，不是实现时才发现） |
| III | **复用既有能力** | ✅ | 本特性几乎全部建在既有能力上：**作废用既有 `TradeOrderHandler` 扩展点**（不碰取消逻辑）；文件上传复用 `infra/file/upload` 与管理端 `UploadFile` 组件；抬头簿的**页面与接口形状照抄地址簿**（`AddressView` + `AddressFormDialog` + `/member/address/**` 那套）；后台列表/弹窗照抄售后单；菜单权限照 004 的幂等脚本。**唯一新建的数据模型**是两张表 —— R25/R26 记了"为什么既有的 `remark` 字段或订单表加列都承载不了" |
| IV | **最小改动** | ✅ | 对既有代码的改动共 **4 处，全是加法**（订单详情 VO 加字段、订单列表 VO 加字段、下单 `createOrder` 内加一行、`design-consistency.spec.ts` 的清单登记一条）—— 见 Complexity Tracking。**没有一处重构、没有一处改既有语义**；`trade_order` 表、取消逻辑、支付逻辑、售后逻辑**全部零改动** |
| V | **增量可验证** | ✅ | 四个阶段各自独立可交付、独立验收（见「实施分期」）。SC-056~065 每条都在 [quickstart.md](./quickstart.md) §6 指明了验证动作；**显式不验证的项**写在 §5 |

**技术栈与架构约束**：全部满足。C 端只走 `/app-api`、管理端只走 `/admin-api`、数据库只用 MySQL、
服务端仍是单体启动形态。**不新增任何依赖**。**未跨端复用接口前缀**。

**Gate 结论**：**通过**。无宪法层面的违规项，四处加法已在 Complexity Tracking 里逐条说明。

## Complexity Tracking

> **无宪法层面的违规项**。以下四处**对既有代码的加法**需要留痕（宪法 v2.0.0 已允许二开，
> 但代价从"违规"变成"将来升级上游时要手工对账"）：

| 事项 | 为什么必须 | 被否决的更简做法 |
|---|---|---|
| **改既有 app 下单接口** `/trade/order/create`（入参加一个可选 `invoice` 对象） | 开票申请必须与订单**在同一事务**里落库（FR-159 要求"下单时选了开票就同时产生申请"）；拆成两次请求就会出现"订单成了、申请丢了"的静默状态 | ① **下单后前端再单独调一次"申请开票"接口** —— 两次请求之间任何失败（关页面、断网）都留下**永远补不上**的申请（一个订单只允许一条，Q28 又禁止改），这是本仓库最忌讳的静默不一致；② **把发票字段平铺到 `trade_order`** —— 见 Summary 第 2 点 |
| **改既有 app 订单详情接口** `/trade/order/get-detail`（响应加 `invoice` 对象） | 订单详情是客户看到"待开票 / 已开票 + 下载入口"的**唯一**位置（FR-175/176）。不在这里返回，前端就得再发一次请求，且要处理"订单有了、发票请求失败"的中间态 | 新增一个 `/trade/order-invoice/get?orderId=` 并让前端并发拉 —— 多一次往返、多一种半失败状态，而订单详情本来就是这一页的响应 |
| **改既有 app 订单列表接口** `/trade/order/page`（每笔订单加一个开票状态） | FR-178 要求列表能看出开票状态；列表是分页的，逐笔再查等于 N+1 | 列表不带、只在详情看 —— 直接不满足 FR-178 |
| **改管理端一致性断言清单**（`storefront/src/styles/design-consistency.spec.ts` 登记新页面） | 那份清单是**显式**的（不会被自动收录）；不登记 = 新页面**逃过**色表/断点/不自带顶栏页脚这三条断言 | 不登记 —— 等于给自己的新页面开后门，宪法原则 I 的"新页面必须过一致性断言"就落空了 |

**这四处都不是"顺手改"**：每处都是为了不让一个可以静默发生的错误状态存在。

## Project Structure

### Documentation (this feature)

```text
specs/006-order-invoice/
├── plan.md                    # 本文件
├── research.md                # Phase 0 产出：R25~R37 技术决策与被否决方案
├── data-model.md              # Phase 1 产出：2 个新实体、状态机、与既有表的关系
├── quickstart.md              # Phase 1 产出：手工验证清单 + 显式不验证项
├── contracts/
│   ├── app-api.md             # Phase 1 产出：C 端新增/改动接口
│   └── admin-api.md           # Phase 1 产出：管理端新增能力 + 菜单权限点
├── checklists/
│   └── requirements.md        # /speckit-specify 产出
├── spec.md                    # /speckit-specify 产出
└── tasks.md                   # /speckit-tasks 产出（本命令不生成）
```

### Source Code (repository root)

```text
yudao-cloud/                                              # ① 服务端
├── yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/invoice/
│   ├── TradeOrderInvoiceStatusEnum.java                   # 新增：待开票 0 / 已开票 10 / 已作废 20
│   └── InvoiceTypeEnum.java                               # 新增：电子普票 1 / 电子专票 2
└── yudao-module-mall/yudao-module-trade-server/src/
    ├── main/java/cn/iocoder/yudao/module/trade/
    │   ├── dal/dataobject/invoice/
    │   │   ├── TradeOrderInvoiceDO.java                   # 新增：开票申请（1:1 挂订单）
    │   │   └── TradeInvoiceTitleDO.java                   # 新增：发票抬头簿
    │   ├── dal/mysql/invoice/
    │   │   ├── TradeOrderInvoiceMapper.java               # 新增（含 join 订单的自定义分页）
    │   │   └── TradeInvoiceTitleMapper.java               # 新增
    │   ├── service/invoice/
    │   │   ├── TradeOrderInvoiceService.java / Impl       # 新增：申请创建、作废、开具
    │   │   ├── TradeInvoiceTitleService.java / Impl       # 新增：抬头簿 CRUD + 默认唯一
    │   │   └── InvoiceValidator.java                      # 新增：按**票种 + 抬头类型**校验必填与税号格式（**唯一一处**）
    │   ├── service/order/handler/TradeInvoiceOrderHandler.java   # 新增：afterCancelOrder → 作废（Q30）
    │   ├── service/order/TradeOrderUpdateServiceImpl.java  # 改动：createOrder 内加一行（同事务）
    │   ├── convert/invoice/TradeOrderInvoiceConvert.java   # 新增（MapStruct）
    │   ├── controller/app/invoice/
    │   │   ├── AppInvoiceTitleController.java              # 新增：抬头簿 CRUD
    │   │   ├── AppTradeOrderInvoiceController.java         # 新增：订单详情补开
    │   │   └── vo/…                                        # 新增
    │   ├── controller/admin/invoice/
    │   │   ├── TradeOrderInvoiceController.java            # 新增：分页 + 开具（上传后绑定）
    │   │   └── vo/…                                        # 新增
    │   ├── controller/app/order/vo/AppTradeOrderCreateReqVO.java        # 改动：+ invoice 子对象
    │   ├── controller/app/order/vo/AppTradeOrderDetailRespVO.java      # 改动：+ invoice
    │   └── controller/app/order/vo/AppTradeOrderPageItemRespVO.java    # 改动：+ invoiceStatus
    │   └── src/test/resources/sql/create_tables.sql        # 改动：补两张新表（否则单测跑不起来）
    └── test/java/cn/iocoder/yudao/module/trade/service/invoice/…       # 新增：本模块**首批**单测
└── sql/mysql/
    ├── 007-order-invoice.sql                               # 新增：2 张建表（幂等、带注释）
    └── 008-order-invoice-menus.sql                         # 新增：菜单 + 权限点（照 004 的幂等写法）

yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/                # ② 管理端
├── api/mall/trade/invoice/index.ts                         # 新增（照 afterSale/index.ts）
└── views/mall/trade/invoice/
    ├── index.vue                                           # 新增：开票申请列表（状态筛选）
    └── InvoiceIssueForm.vue                                # 新增：上传发票弹窗（UploadFile + 权限指令）

storefront/                                                 # ③ C 端前台
├── src/
│   ├── api/invoice.ts                                      # 新增：抬头簿 CRUD + 补开
│   ├── types/invoice.ts                                    # 新增：InvoiceTitle / OrderInvoice / 票种常量
│   ├── utils/invoice.ts                                    # 新增：按票种 + 抬头类型的必填校验（纯函数，先写测试）
│   ├── components/InvoiceTitleDialog.vue                   # 新增：新增/编辑抬头（照 AddressFormDialog）
│   ├── components/CheckoutInvoiceCard.vue                  # 新增：结算页发票卡片
│   ├── views/InvoiceTitleView.vue                          # 新增：个人中心・发票抬头
│   ├── views/CheckoutView.vue                              # 改动：挂上发票卡片、提交时带上
│   ├── views/OrderDetailView.vue                           # 改动：发票区块（四态）+ 补开弹层
│   ├── views/OrderListView.vue                             # 改动：卡片上显示开票状态
│   ├── components/AccountSidebar.vue                       # 改动：账户资料组加一条
│   ├── router/index.ts                                     # 改动：加 /account/invoice
│   └── styles/design-consistency.spec.ts                   # 改动：新页面登记进显式清单
```

**Structure Decision**：
- 服务端**两张表都放 trade 模块**（`dal/dataobject/invoice` + `dal/mysql/invoice` + `service/invoice` +
  `controller/{app,admin}/invoice`），与既有 `aftersale` / `order` 平级 —— 沿用本模块"按实体分子包"的组织方式。
  **抬头簿没有放进 member 模块**，理由见 R25（放 member 会让 member 反向依赖 `trade-api` 才能复用票种枚举与校验规则，
  而抬头在下单侧**不需要服务端读取** —— 前端提交的就是快照本身）。
- 票种与状态枚举放 **`trade-api`**（与 `AfterSaleStatusEnum` 同处），供 server 与前端契约共用。
- 管理端沿用既有 `api/mall/trade/` + `views/mall/trade/<实体>/` 分层；新页面是**独立菜单**（Q29 + FR-167 要求）。
- C 端沿用既有分层；**新增一个 `InvoiceTitleView` 页面**（不是弹层），因为它要长时间停留、要能反复增删改
  —— 与地址簿同一形态（R33）。

## 实施分期

四个阶段依次依赖，按"能独立交付、独立验收"排序。

### Phase 1 —— US19 + US20 开票申请的两个入口（P1 / P2）

> 交付物：客户下单时能申请开票（选票种 + 填/选抬头）；漏选了还能在订单详情补开。申请进库。

1. **建表**：`sql/mysql/007-order-invoice.sql`（2 张表 + 索引，见 [data-model.md](./data-model.md) §1/§2）；
   在开发库执行，并**同步补进 `trade-server/src/test/resources/sql/create_tables.sql`** —— 否则新单测跑不起来。
   后续加列走增量脚本：`009-invoice-title-type.sql`（Q36 票种）、`010-invoice-title-kind.sql`（Q38 抬头类型）——
   **每一次加列都必须同时补 `create_tables.sql`**（漏了就整模块测试启动即挂）。
2. **先写测试**（服务端，本模块首批）：
   - `InvoiceValidator`：按**抬头类型**的必填矩阵（**企业**要名称 + 税号；**个人**只要名称，
     税号选填不是禁止）（Q37 + Q38）；「个人 + 专票」必须被拒；税号位数与字符校验。
   - `TradeOrderInvoiceService`：下单创建（1:1）；**同一订单第二次创建必须被拒**（FR-160）。
   - `TradeInvoiceTitleService`：默认抬头唯一（设新的默认要清旧的）；**删除抬头后历史申请的开票信息逐字段不变**（FR-166/SC-061）。
3. 实现枚举、两个 DO、两个 Mapper、两个 Service、`InvoiceValidator`、convert。
4. `TradeOrderUpdateServiceImpl.createOrder` 内**加一行**（订单落库后、同一事务内）调 `tradeOrderInvoiceService.createInvoice(order, reqVO.getInvoice())`；
   `AppTradeOrderCreateReqVO` 加 `invoice` 子对象。
5. `AppTradeOrderInvoiceController`：补开接口（校验订单属于当前会员、已支付、且尚无申请）。
6. `AppTradeOrderDetailRespVO` 加 `invoice` 对象（含状态、票种、抬头快照、文件 URL）。
7. C 端：`types/invoice.ts` / `api/invoice.ts`（先写 api 单测）、`utils/invoice.ts`（纯函数，先写测试）、
   `CheckoutInvoiceCard.vue`+ 挂进 `CheckoutView.vue`、`OrderDetailView.vue` 的两个入口（申请开票 / 下载）+ 四态展示。
   ⚠️ **实付 0 元的订单整张发票卡片不渲染**（spec Edge Case：全额抵扣的订单没有可开票金额）。
   与 FR-153「结算页 MUST 提供发票区块」的关系：spec 的 Edge Case 更具体，**以它为准** ——
   0 元时是"整块不出现"，而不是"出现一个选了也没用的可选项"。结算页按 `settlement` 返回的
   `payPrice > 0` 判断（金额由后端给，前端不自己算）。
8. **手工验收**：[quickstart.md](./quickstart.md) §1（下单申请）与 §2（补开）。

### Phase 2 —— US23 发票抬头簿（P3）

> 交付物：客户在个人中心维护多条抬头、设默认；下单时直接选。

1. **先写测试**：服务端抬头簿 CRUD 与默认唯一（并入 Phase 1 的 Service 测试）；
   C 端 `InvoiceTitleView` 组件测试（列表/空态/删除确认）+ 结算页"默认抬头被预选"的组件测试。
2. C 端：`views/InvoiceTitleView.vue` + `components/InvoiceTitleDialog.vue`（照 `AddressView` + `AddressFormDialog`）；
   `AccountSidebar.vue` 加「发票抬头」；`router/index.ts` 加 `/account/invoice`；
   **`design-consistency.spec.ts` 的清单里登记这个新页面**。
3. 结算页发票卡片接上抬头列表（选中默认项）。
4. **手工验收**：[quickstart.md](./quickstart.md) §3（抬头簿）+ §1 第 5 步（改抬头不影响历史订单）。

### Phase 3 —— US21 后台开票工作台（P2）

> 交付物：运营在一个列表里看到谁要开票，开好后把文件传上来，状态变「已开票」。

1. **迁移脚本**：`sql/mysql/008-order-invoice-menus.sql`（菜单「开票申请」+ 按钮权限点，
   照 `004-filter-dimension-menus.sql` 的幂等写法：`INSERT ... SELECT ... WHERE NOT EXISTS`）。
   ⚠️ **不登记权限点 = 连超管都看不到按钮**（004 记着这个坑）。
2. **先写测试**（服务端）：
   - `TradeOrderInvoiceService.issueInvoice`：**未支付的订单不许开具**（FR-169）；
     已作废的不许开具；重复开具走"覆盖"（Q31）；**文件扩展名不在白名单时拒绝且状态不变**（FR-171）。
   - `TradeInvoiceOrderHandler.afterCancelOrder`：**待开票 → 已作废**、**已开票的不动**（Q30 / FR-184）。
   - 分页查询：**未支付订单的申请不出现在「待开票」列表**（FR-169/SC-059）。
3. 服务端：Mapper 加 join 订单的自定义分页（照 `BrokerageUserMapper.xml` 的 XML 写法）；
   `TradeOrderInvoiceController`（分页 + 开具）。
4. 管理端：`api/mall/trade/invoice/index.ts`；`views/mall/trade/invoice/index.vue`（ContentWrap + el-table + Pagination + 状态筛选）；
   `InvoiceIssueForm.vue`（`UploadFile` + `file-type` 限制 + `v-hasPermi`）；**新增纯函数写 vitest**（照 003 的最小范围）。
5. **手工验收**：[quickstart.md](./quickstart.md) §4。

### Phase 4 —— US22 客户查看与下载（P3）

> 交付物：客户在订单详情看到发票状态与下载入口；订单列表能看出开票状态。

1. **先写测试**：C 端 `OrderDetailView` 发票区块的**四态**（FR-176）—— 未支付/已取消且未申请 → 整块不渲染；
   已支付未申请 → 只出「申请开票」；待开票 → 状态文案、**无下载入口**；已开票 → 信息 + 下载入口。
   以及**跨账号**：别人的订单不出现任何开票信息（SC-060）。
2. `AppTradeOrderPageItemRespVO` 加 `invoiceStatus`（列表批量查询，避免 N+1）；C 端 `OrderListView.vue` 显示。
3. `OrderDetailView.vue` 的下载入口。**不能只放一个裸 `<a href>`** —— spec 的 Edge Case 要求
   「文件在对象存储里丢了 → 下载失败 MUST 给出可读的提示与求助路径，MUST NOT 静默失败」。
   但**真正的失败探测在这个文件域上做不到**（见风险 R10 的实测）：链接旁**常驻**一句
   「文件打不开？可能是已失效，请[联系我们](</support/contact>)或致电 010-88613986」，
   并走新标签打开（失败时客户明确看到"打不开"，不会以为自己没点中）。
   ⚠️ 这条**只是缓解**：做到"不静默、有求助路径"，做不到"当场告诉客户文件失效了"。
4. **手工验收**：[quickstart.md](./quickstart.md) §5（含"传错文件重传后客户拿到的是新的那份"）。

## 测试策略

### 五层

① **服务端 JUnit（本模块**首次**，照 product 模块的 `BaseDbUnitTest` + H2）**

- **必填矩阵**（**Q37 + Q38**，取代 Q27/Q36）：**企业**要「名称 + 税号」，**个人只要名称**；
  地址 / 电话 / 开户行一律选填（填了要合法）；税号格式非法要拒；
  **「个人 + 专票」必须被拒**（专票按税法只能开给企业）。
- **1:1 唯一**：同一订单创建第二条申请被拒（FR-160）；重复提交不产生第二条。
- **快照不可回溯**（FR-158/SC-061）：改 / 删抬头后，历史申请的开票信息**逐字段比对不变**。
- **作废只发生在取消**（Q30/FR-184）：待开票的被取消订单 → 已作废；**已开票的 MUST NOT 被动**；
  未支付的订单不出现在待开票列表（FR-169/SC-059）。
- **开具**：未支付 / 已作废不许开具；格式白名单外的文件被拒且状态不变（FR-171）；重传覆盖（Q31）。
- **实付 0 元**（spec Edge Case）：**下单时"忽略"而不是"拒绝"** —— 0 元订单照下、只是不产生申请
  （不因为一个本来就开不了票的选择让客户下不了单）；**补开时"拒绝"**（订单已存在，给明确理由就好）。
  两种行为都要有用例，**别实现成"两边一样"** —— 0 元订单同样满足"已支付"，
  少了这个判断，补开入口会从后端露出去（前端藏了也没用）。
- **兼容性**：不传 `invoice` 下单时，落库与改动前**逐字段一致**（FR-162 的证据）。

② **C 端 Vitest 单测**
- `api/invoice.ts`：请求路径与参数形态；`utils/invoice.ts`：按票种 + 抬头类型的必填判定（FR-155/156）纯函数。
- `types/invoice.ts` 的票种常量与后端枚举值对齐（2 个值，写一条断言钉住）。

③ **C 端 Vitest 组件 / 接线**
- `CheckoutInvoiceCard`：默认不开票；切到开票才出现票种与抬头；**必填不全都点不动提交**（FR-153/156）。
- `InvoiceTitleView` + `Dialog`：空态 / 新增 / 编辑 / 删除 / 设默认（默认项唯一）。
- `OrderDetailView`：**四态**（FR-176）与**跨账号不可见**（SC-060）；
  **下载入口旁常驻求助文案**（R10 的缓解措施，别在重构时被当成冗余删掉）。
- `CheckoutInvoiceCard`：**实付 0 元时整块不渲染**（spec Edge Case）。
- **接线断言（本项目吃过亏的地方）**：点提交后发出的**下单请求体**里，
  `invoice` 对象必须与表单**逐字段一致**；不开票时**请求体里没有 invoice 字段**（而不是传 `null`/空对象）。
  只断言 UI 是测不出"表单填了但没提交出去"的。

④ **管理端 Vitest（003 引入的设施，范围压到最小）**
- 新增纯函数（票种/状态的中文映射、筛选参数组装）。
- `InvoiceIssueForm`：**没有 `issue` 权限时上传控件不可用**（`checkPermi`）、未选文件不许提交。
- **不做**管理端全量测试改造；`vite.config.ts` 一行不动。

⑤ **手工清单**（[quickstart.md](./quickstart.md)）—— 自动化测不到的：真实文件的观感、
后台破坏性操作（传错、重传、对未支付订单开具）、**公网可读链接这一既有风险的实际表现**、
以及"运营从看到申请到传完文件"的真实耗时（SC-063 的判据）。

### e2e：本期**不加**

现有 e2e（`storefront/e2e/`）跑在本地后端 + **租户 1** 的完整种子数据上，而发票链路的 e2e 前置要构造
「下单 → 支付成功 → 后台开具 → 前台下载」四步，其中**支付成功**需要 mock 支付回调
（`pay_channel` + pay 模块回调），构造成本远高于它能带来的信息 —— 而申请、作废、快照三条核心语义
已由服务端集成测试覆盖。**不新增 e2e**（这是显式决定，不是遗漏；沿用 003 的先例）。

### 明确不做的

- **不接任何开票平台 / 不做自动开票**（FR-180，spec 的 Assumptions）。
- **不做红冲 / 作废发票流程**：已开票后订单退款，票**照留可下载**，页面如实同时呈现"订单已退款"与"发票已开出"。
- **不做拆票 / 部分开票**（FR-160），**不做电子发票的邮箱 / 短信推送**（FR-183）。
- **不迁移老站那 4 条发票抬头记录**（spec 的 Assumptions；样本 4 条且是老站表单的一次性提交）。
- **不改 `trade_order` 表、不改取消 / 支付 / 售后逻辑**（Q30 走 handler）。
- **不给发票文件加签名 URL / 鉴权**（spec 已作为**显式接受的风险**记录；要加是另一次改动）。
- **不动小程序 / App 端**。

## 主要风险与对策

| # | 风险 | 对策 |
|---|---|---|
| **R1** | ~~**FR-181 的字面与"同事务落库"相抵**~~ → **2026-10-15 已由所有者拍板（Q32=A：同事务）**，spec 的 FR-181 已同步收窄。留在这里只为记下来龙去脉 | **口径收窄并写明**：FR-181 保护的是"开票域自身的失败"（抬头簿 CRUD、文件下载、后台上传、无外部调用），**不是**"订单与申请要拆开事务"。拆开会出现更坏的结果 —— **订单成了、申请永远补不上**（一个订单只允许一条、Q28 又禁止改），那正是静默不一致。⚠️ 这是**一处需要所有者知情的解释**，不是实现细节；若坚持字面，则要接受"丢单"或改 FR-160/Q28 |
| **R2** | ~~**部分退款**（订单未取消、只退了部分金额）时，申请该不该作废~~ → **2026-10-15 所有者实测后改判：作废（Q35）**。留档（含被推翻的那一版理由）：原计划口径是"**订单取消 / 退款完成**"。**部分退款不取消订单**，若把它也算作废会陷入死结：作废后客户想开剩余金额的票，而一单只能一条申请、申请又不可改 → 无路可走。故口径定为：**只有订单取消（含全额退款导致的取消）才作废**；部分退款**不自动改、不动作**，后台列表把订单的实付 / 已退金额展示出来，运营开具前自行判断（红冲本期不做）。**这条要在 quickstart §4 里实测一次** |
| **R3** | **`trade_order` 列表加开票状态造成 N+1** | 一页 12 条 → **一次 `IN` 批量查询**；服务端测试断言"查询条数不随订单数增长"（用 mapper 调用计数） |
| **R4** | **权限点漏登记** → 接口能调、按钮不显示（004 踩过，超管也一样） | 菜单脚本与代码同批交付；quickstart §0 把"用**受限角色**账号实走一遍"列为上线前置；服务端测试断言 `@PreAuthorize` 存在（SC-065 的后端一半） |
| **R5** | **`create_tables.sql` 漏补两张表** → 本模块新单测**全部**跑不起来（不是某一个用例失败，是启动就挂） | Phase 1 第 1 步就补；quickstart §0 列为前置核对 |
| **R6** | **发票文件链接公网可读**（既有对象存储的既有性质） | 已在 spec 的 Assumptions 里作为**显式接受的风险**留档；本计划**不擅自加鉴权**（那要改下载链路，属另一次改动）。quickstart §5 实测"未登录浏览器直接打开链接"的真实表现，供所有者复盘 |
| **R7** | **客户在结算页填了抬头却没提交出去**（本项目吃过的亏：单测只断言 UI 不断言请求体） | 接入层测试断言**下单请求体**里 `invoice` 逐字段一致；不开票时断言请求体里**没有**该字段（`undefined`，而不是 `null` 或空对象 —— 后者会被后端当成"申请了但字段全空"） |
| **R8** | ~~**作废把已开票的票也作废掉**（原本当作要避免的风险）~~ → **2026-10-15 反过来成了要求（Q35）**：退款了票就该撤回，前台不显示、后台不能更新。现在要防的是**漏实现**：整单取消与**部分退款**是两条不同的钩子（`afterCancelOrder` / `afterCancelOrderItem`），少一个就会出现"票还挂在前台"且**不报错** | 服务端测试同时断言两条路径（含幂等：多个钩子先后触发时第二次影响 0 行）；C 端测试断言作废后**不展示发票内容**、没有下载入口 |
| **R9** | **管理端新页面逃过一致性断言**（色表 / 断点 / 不自带顶栏页脚） | 新页面登记进 `design-consistency.spec.ts` 的显式清单（Complexity Tracking 第 4 条）；不登记那三条断言就管不到它 |
| **R10** | **下载失败探测不到** —— 2026-10-15 实测：文件域 `img.yuwangchenfa.com` **不返回任何 CORS 头**（`access-control-allow-origin` 缺失），缺失文件返回 404。这意味着浏览器里**读不到**那次请求的结果：`fetch` 会被跨域策略挡住，`mode: 'no-cors'` 拿到的是无法区分 200/404 的 opaque 响应。**所以"当场提示客户文件失效"做不到** | **已定案（Q34=A，2026-10-15）**：spec 的 Edge Case 第 7 条已按"只做到不静默 + 常驻求助路径"改写，承诺收窄；`服务端代理下载` 这条路记在 research R37 与 spec 的 Q34 里，**本期不做**。
⚠️ **这是本计划唯一一处"规格要求只兑现了一半"的地方，必须让所有者知情**。要真正兑现，得由服务端**代理下载**（`GET /app-api/trade/order-invoice/download?id=`：后端校验订单归属后取文件转发）—— 顺带还能把"链接公网可读"这条已接受的风险一并收掉。那是**另一次改动**（新接口 + 文件流量过服务端），本计划**不擅自做**。 |

## Post-Design Re-check

Phase 1 设计（[data-model.md](./data-model.md) / [contracts/](./contracts/) / [research.md](./research.md)）完成后复检：

**无新增违规项，Constitution Check 结论不变**。四处加法在设计里进一步收窄为：
订单详情 VO 加一个对象、订单列表 VO 加一个字段、`createOrder` 内加一行、一致性清单加一条。

设计阶段定案的三处：

- **R26 两张表都放 trade 模块**（含抬头簿），由 data-model §1/§2 固化；
- **R28 作废走 `TradeOrderHandler.afterCancelOrder`**（不碰取消逻辑），由 research R28 定案；
- **R27 快照由前端提交的值落库**（trade 不读 member，**不需要任何跨模块 RPC**），由 research R27 定案。

**无未解决的澄清项**（Q27~Q31 全部已由所有者拍板）。**一处需要所有者知情的解释**见风险 R1（FR-181 的口径收窄）。
