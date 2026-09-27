# Implementation Plan: 赋签门面站与 yudao 后台打通

**Branch**: `001-mall-storefront-integration` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-mall-storefront-integration/spec.md`

## Summary

把 `www/` 下 6 页静态设计稿重写为一个 Vue 3 单页应用，并接上 yudao 既有的 app-api，使商品展示、登录、购物车、下单、支付、优惠券、订单全部走真实后台数据。

技术路线：**照搬 `yudao-ui-admin-vue3` 的工程骨架**（Vue 3.5 + Vite 8 + TS 6 + Pinia 3 + vue-router 5 + axios 三层封装），把 API 前缀换成 `/app-api`、认证换成 `/member/auth/*`，**原样复用 `www/css/style.css`** 作为视觉基线，**不引入 UI 组件库**，**不改动后端任何代码**。

三处支撑这一路线的前置查证（详见 [research.md](./research.md)）：

1. 本地直连单体 `yudao-server`（`http://localhost:48080`）即可同时访问 `/app-api` 与 `/admin-api`，**不需要网关**；后端 CORS 已放开所有来源，**不需要开发代理**。
2. 模拟支付**提交即成功**（`MockPayClient` 立即返回成功并由回调推进订单状态），不存在也不需要"手动标记支付成功"接口。
3. 本地短信验证码**恒为 `9999`**，配合 `Bearer test1` 的 mock 令牌，可在无真实短信通道的条件下完整联调。

## Technical Context

**Language/Version**: TypeScript 6.x / Node ≥ 20.19（本地实测 v24.14.1）/ Vue 3.5.x
**Primary Dependencies**: Vue `3.5.x`、Vite `8.x`、Pinia `3.x`、vue-router `5.x`、axios `1.16`、`@vueuse/core` `14.x`；开发期 `vitest` + `@vue/test-utils`
**Storage**: 浏览器本地存储仅用于访问令牌（`ACCESS_TOKEN` / `REFRESH_TOKEN`）与协议勾选的**内存**状态；**业务数据全部在 yudao 后端（MySQL）**，前端不做业务数据持久化
**Testing**: **测试先行（TDD）**，按宪法原则 I 执行——测试先写、先失败、再实现。三层：
① **Vitest 单元**：金额换算与核对、订单状态映射、角标派生、响应适配、共用的纯函数；
② **Vitest 组件**：协议门禁、结算金额明细、角标渲染、登录态、空/错误态等有行为契约的组件；
③ **Playwright 端到端（4 条，按故事各一）**：`e2e/auth.spec.ts`（注册 + 两种登录方式同账号）、`e2e/cart.spec.ts`（加购后角标）、`e2e/order.spec.ts`（下单进「我的订单」）、`e2e/pay.spec.ts`（模拟支付后变待发货）。**每条都在其故事的实现任务之前创建并先失败**，与单元/组件层同样受原则 I 约束；Phase 10 只做全量运行与计时。
详细策略见下方「测试策略」小节；后端已在 2026-09-24 实测可由 `java -jar` 无头启动，故第三条可行（[research.md](./research.md) R9 已更正）
**Target Platform**: 现代浏览器，桌面优先，移动端宽度 ≥ 375px 可完整走通（SC-008）
**Project Type**: 前端单页应用（web）。**消费既有后端接口，本期不含任何后端改动**
**Performance Goals**: 商品列表页与商品详情页首屏内容 ≤ 3 秒可见（SC-004）
**Constraints**:
- **不得改动 `yudao-cloud/`**（它是引入的二开基线，保持与上游的 diff 干净）
- **不得改动 `www/css/style.css`**（设计稿是视觉基线，FR-046）
- **17 条无设计稿路由 MUST 遵循 [design-new-pages.md](./design-new-pages.md)** 的令牌与组件契约；信息页 MUST NOT 编造业务事实（FR-054）
- 不引入 UI 组件库（reasoning 见 [research.md](./research.md) R1）
- 后端**可由 `java -jar yudao-server/target/yudao-server.jar` 启动**（2026-09-24 实测确认，Tomcat 绑定 48080、约 70 秒启动完成）。⛔ 绝不能加 `--spring.profiles.active=local`，那会覆盖 `application.yaml` 里的 `local,my` 并导致连本机 MySQL 而启动失败。改代码后必须重新 `mvn package`
- **测试先行是硬约束**：宪法原则 I 要求测试先写、先失败、再实现。任务清单中每个故事内**测试任务必须排在其实现任务之前**（见 [tasks.md](./tasks.md)）
**Scale/Scope**: **23 条路由**（6 条来自设计稿 + 11 条功能页 + **6 条信息页**；登录为弹层，不占路由）、**79** 条功能需求、21 条成功标准、消费 40 个 app-api 端点。其中 **17 条无设计稿**，其版式由 [design-new-pages.md](./design-new-pages.md) 规定

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**宪法已批准**：`.specify/memory/constitution.md` 为 **v1.0.0**（Ratified 2026-09-24），含 5 条原则、技术栈与架构约束、开发工作流与质量门禁。逐条校验如下。

| # | 宪法原则 | 是否满足 | 依据 / 违规处理 |
|---|---|---|---|
| I | **测试先行（Test-First，不可协商）** | ✅ | 本计划的三层测试策略（见「测试策略」）明确要求测试先写、先失败、再实现；[tasks.md](./tasks.md) 中每个故事内测试任务排在其实现任务之前。**本条曾一度违规** —— 早期版本以"后端只能从 IDE 启动"为由不做端到端自动化，该依据已于 2026-09-24 实测推翻（`java -jar` 可无头启动），计划已相应修订 |
| II | **先澄清，不猜测（Clarify Before Change）** | ✅ | 三轮 `/speckit-clarify` 共确认 12 项决策；未决项列入 spec 的「另经筛选确认不做」；[research.md](./research.md) 每条决策都记了「被否决的方案」 |
| III | **复用既有能力（Reuse Over Rebuild）** | ✅ | 不新建任何后端能力；[contracts/app-api.md](./contracts/app-api.md) §8 **显式列出不调用的 9 个接口族**，防止实现期顺手接入范围外能力。价格、库存、券可用性、订单状态的裁决权全部留给服务端 |
| IV | **最小改动（Surgical Change）** | ✅ | 不改 `yudao-cloud/`、不改 `www/css/style.css`、保留设计稿 class 名；新代码全部落在新的 `storefront/` 目录内。不引入 UI 组件库；唯一的框架级新增是测试工具（宪法原则 I 要求，非投机性引入） |
| V | **增量可验证（Verifiable Increments）** | ⚠️ | 21 条 SC 均为可度量指标，每条都指明验证方式。**明确声明不验证的有五项**（原先只写了 SC-006，经交叉验证补齐）：SC-006（100 并发不超卖）、FR-033 的超时释放半段、SC-018 的超时自动取消半段、SC-013 的券单次使用半段、SC-002/SC-003 的上限判定（e2e 只记录不硬断言） —— 理由与验证归属逐条写在 [quickstart.md](./quickstart.md) §5，由 [tasks.md](./tasks.md) T134 核对。**这四项已写入 [quickstart.md](./quickstart.md) §5**（逐条含理由与归属） |

**技术栈与架构约束**：全部满足。Web 前端用 Vue 3 + Vite + TS，**只走 `/app-api`**（不跨用 `/admin-api`），数据库无涉及（前端不持久化业务数据），`yudao-cloud/` 保持零改动。

**Gate 结论**：**有一项需要辩护的违规**（见下），无其他违反项。原则 V 的缺口已由 [quickstart.md](./quickstart.md) §5 补上，无需再等任务执行。

**Phase 1 设计完成后复检**：见文末「Post-Design Re-check」。

## Complexity Tracking

> **无违规项，本节为空。**

早先版本此处记录过一条「原则 I（测试先行）违规：端到端验收测试事后编写」，并试图在计划层面为其辩护。
**该做法已撤销**：宪法明确规定「计划或任务层面无权自行豁免任何原则」，且计划门禁要求「MUST NOT 通过重新解释原则来规避」，
因此计划层的辩护本身即违规。

**正确做法已落地**：端到端不再是一条事后的大测试，而是**四个按故事测试先行的 e2e 文件** ——
`e2e/auth.spec.ts`（T060，随 US2）、`e2e/cart.spec.ts`（T075，随 US3）、`e2e/order.spec.ts`（T088，随 US4）、
`e2e/pay.spec.ts`（T114，随 US5）。每个都在其故事的实现任务之前创建、先失败、随该故事完成转绿。
Phase 10 的 T140 只负责**全量运行与计时记录**，不再承担"事后补测试"的职责。

## Project Structure

### Documentation (this feature)

```text
specs/001-mall-storefront-integration/
├── plan.md              # 本文件
├── spec.md              # 功能规格（/speckit-specify + 三轮 /speckit-clarify）
├── research.md          # Phase 0 输出：技术决策与被否决方案
├── data-model.md        # Phase 1 输出：实体映射、派生数据、状态流转、校验规则
├── quickstart.md        # Phase 1 输出：可执行的手工验证清单
├── design-new-pages.md  # 17 条无设计稿路由的补充设计规范（令牌 / 组件契约 / 页面规格 / 内容依赖）
├── contracts/
│   └── app-api.md       # Phase 1 输出：门面站消费的 app-api 契约
├── checklists/
│   └── requirements.md  # 规格质量校验清单
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成，非本命令产物）
```

### Source Code (repository root)

新站放在**仓库根的新目录 `storefront/`**，与设计稿 `www/` 平级。**不放进 `yudao-cloud/yudao-ui/`**，以免污染上游基线的 diff。

```text
storefront/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── vitest.config.ts            # 单元/组件测试（或并入 vite.config.ts 的 test 字段）
├── playwright.config.ts        # 端到端冒烟（1 条）
├── eslint.config.js            # flat config，沿用 admin-vue3 的规则集
├── .env                        # 公共变量
├── .env.local                  # 本地：VITE_BASE_URL=http://localhost:48080
├── e2e/
│   └── purchase-journey.spec.ts  # 注册→加购→下单→模拟支付（SC-010）
├── public/
│   └── assets/
│       └── logo.png              # ← www/assets/logo.png 原样搬入（设计稿唯一的图片资产，18 处引用）
└── src/
    ├── main.ts
    ├── App.vue
    ├── config/
    │   └── http/               # 三层请求封装（照搬 admin-vue3 结构）
    │       ├── config.ts       # baseURL、超时、成功码 0
    │       ├── index.ts        # get/post/put/delete，剥一层 res.data
    │       └── service.ts      # 拦截器：tenant-id / terminal / Bearer / 401 刷新队列
    ├── api/                    # 按域划分，一域一文件
    │   ├── product.ts          # /product/spu/*、/product/category/*
    │   ├── cart.ts             # /trade/cart/*
    │   ├── order.ts            # /trade/order/*
    │   ├── member.ts           # /member/auth/*、/member/user/*
    │   ├── address.ts          # /member/address/*
    │   ├── coupon.ts           # /promotion/coupon*/*
    │   ├── pay.ts              # /pay/order/*
    │   └── area.ts             # /system/area/tree
    ├── store/
    │   ├── user.ts             # 登录态、协议同意（内存）、密码设置
    │   └── cart.ts             # 顶栏角标数量
    ├── router/
    │   └── index.ts            # 路由表 + 需登录路由的守卫
    ├── layouts/
    │   └── DefaultLayout.vue   # 顶栏 + router-view + 页脚（FR-042 的结构保证）
    ├── components/             # 复用设计稿 class 的展示组件
    │   ├── SiteHeader.vue      # 顶栏：登录态 + 购物车角标（全站一致，FR-042）
    │   ├── SiteFooter.vue
    │   ├── ProductCard.vue     # 含角标派生（FR-008a）
    │   ├── Pagination.vue      # 复用设计稿 .pager 样式
    │   ├── LoginDialog.vue     # 含协议门禁（FR-050）
    │   ├── QuantityStepper.vue
    │   └── EmptyState.vue      # 空状态与错误重试（FR-044 / FR-045）
    ├── views/
    │   ├── HomeView.vue        # ← 设计稿 index.html
    │   ├── MallView.vue        # ← 设计稿 mall.html
    │   ├── ProductView.vue     # ← 设计稿 product.html
    │   ├── SoftwareView.vue    # ← 设计稿 software.html
    │   ├── SolutionsView.vue   # ← 设计稿 solutions.html
    │   ├── SupportView.vue     # ← 设计稿 support.html
    │   ├── CartView.vue        # 新增（无设计稿）
    │   ├── CheckoutView.vue    # 新增（无设计稿）
    │   ├── OrderListView.vue   # 新增（无设计稿）
    │   ├── OrderDetailView.vue # 新增（无设计稿）
    │   ├── AccountView.vue     # 新增：个人中心 + 设置密码
    │   ├── AddressView.vue     # 新增：收货地址管理（含地区三级联动）
    │   ├── CouponCenterView.vue# 新增：领券中心
    │   ├── MyCouponView.vue    # 新增：我的券
    │   ├── AgreementView.vue   # 新增：用户协议 / 隐私政策（同一组件两种内容）
    │   ├── EnterpriseView.vue  # 新增：企业采购落地页
    │   ├── TemplateCenterView.vue # 信息页：模板中心（无设计稿，按 design-new-pages §3.12）
    │   ├── ChangelogView.vue   # 信息页：更新日志（§3.13）
    │   ├── AboutView.vue       # 信息页：公司介绍（§3.14）
    │   ├── NewsView.vue        # 信息页：新闻动态（§3.15）
    │   ├── ContactView.vue     # 信息页：联系我们（§3.16）
    │   └── JobsView.vue        # 信息页：加入我们（§3.17）
    ├── utils/
    │   ├── auth.ts             # 令牌存取（ACCESS_TOKEN / REFRESH_TOKEN）
    │   ├── money.ts            # 分 ↔ 元换算与格式化（唯一实现处）
    │   ├── orderStatus.ts      # 5 态映射（FR-041a）
    │   └── badge.ts            # 热销 / 新品派生（FR-008a）
    ├── styles/
    │   ├── design.css          # ← www/css/style.css 原样搬入，不改
    │   └── store.css           # 新增页面样式，沿用同一套配色与字号
    └── types/                  # 后端契约的 TS 类型
```

**单元/组件测试与源码同目录**（`*.spec.ts`），符合 Vitest 默认发现规则；端到端单独放 `e2e/`：

以下是**完整清单**（与 [tasks.md](./tasks.md) 一一对应，凡此处列出的都必须在 tasks 中有创建任务）：

```text
src/config/http/service.spec.ts       # T017：tenant-id 无条件注入、按 body.code 判定、401 刷新队列
src/utils/money.spec.ts               # T023：SC-007 金额换算与六项核对
src/data/placeholders.spec.ts         # T032：isPending 契约 + hasPendingContent（门禁①）
src/components/base/PendingText.spec.ts  # T034：PendingText 从值派生属性；C1–C8 行为契约
src/utils/badge.spec.ts               # T036：SC-021 角标派生
src/api/product.spec.ts               # T037：排序映射（小驼峰！）、分页、字段兜底
src/components/ProductCard.spec.ts    # T041：SC-021 角标渲染与去重、FR-026h
src/views/ProductView.spec.ts         # T049：FR-005c/SC-020 安全过滤、FR-006、FR-007
src/store/user.spec.ts                # T062：FR-013 冷启动恢复、同意状态不持久化
src/components/LoginDialog.spec.ts    # T064：SC-015 协议门禁、FR-012、FR-010b
src/views/AgreementView.spec.ts       # T068：SC-016 不依赖接口
src/components/SiteHeader.spec.ts     # T025/T072/T086：静态结构 → 登录态 → 角标
src/views/CartView.spec.ts            # T079：FR-023 失效条目、FR-021 超限回退
src/utils/orderStatus.spec.ts         # T091：SC-017 五态映射、变体可区分（FR-041c）
src/api/order.spec.ts                 # T093：数组参数拼 query、pointStatus 固定 false
src/views/CheckoutView.spec.ts        # T098：SC-007 金额明细、FR-026g 分列两行、FR-030
src/store/cart.spec.ts                # T077/T108：FR-022 登录后重载、下单后刷新
src/views/OrderDetailView.spec.ts     # T116：FR-040 不重复支付、FR-039 状态以后端为准
src/views/CouponCenterView.spec.ts    # T120：FR-026c 重复领取不产生第二张
e2e/auth.spec.ts                      # T060：SC-002 注册、SC-011 两种登录方式同账号
e2e/cart.spec.ts                      # T075：FR-019 加购后角标
e2e/order.spec.ts                     # T088：SC-003 下单进「我的订单」
e2e/pay.spec.ts                       # T114：SC-010 模拟支付后变待发货
```

**Structure Decision**: 采用**单一前端工程**（非 monorepo）。理由：

- 只有一栋站点，没有第二个消费者，monorepo 是无谓的复杂度（CLAUDE.md 原则 2）。
- 骨架从 `yudao-ui-admin-vue3` 复制结构但**不建立包依赖** —— 仓库里两个既有前端也是彼此独立的工程（`pnpm-workspace.yaml` 没有 `packages` 字段），照此惯例。
- 放在仓库根而非 `yudao-cloud/yudao-ui/` 下，是为了让上游基线的 diff 保持干净（CLAUDE.md 原则 3）。

## 路由清单与设计稿覆盖情况

**6 条来自设计稿**（忠实还原，FR-046）、**17 条无设计稿**（11 条功能页 + 6 条信息页，版式依据 [design-new-pages.md](./design-new-pages.md)）。登录不占路由，由 `LoginDialog` 弹层承载：

| 路由 | 来源 | 说明 |
|---|---|---|
| `/` | 设计稿 index.html | 首页；商品区按销量取前 4（FR-008） |
| `/mall` | 设计稿 mall.html | 商城；移除两组筛选项与侧栏计数（FR-004a / FR-002a） |
| `/product/:id` | 设计稿 product.html | 详情；规格参数表改由 SKU 规格项承载（FR-005a）+ 富文本详情 |
| `/software` | 设计稿 software.html | 纯内容页；版本卡需链到商品 |
| `/solutions` | 设计稿 solutions.html | 纯内容页 |
| `/support` | 设计稿 support.html | 纯内容页 |
| `/cart` | **新增** | 购物车 |
| `/checkout` | **新增** | 结算；含券选择、地址切换、金额明细 |
| `/order` | **新增** | 我的订单列表（5 态筛选） |
| `/order/:id` | **新增** | 订单详情；支付入口、支付截止时间 |
| `/account` | **新增** | 个人中心 + 设置密码（FR-011） |
| `/account/address` | **新增** | 收货地址管理（地区三级联动） |
| `/coupon` | **新增** | 领券中心（`coupon-template/list`） |
| `/coupon/mine` | **新增** | 我的券（`coupon/page`） |
| `/agreement/user` | **新增** | 用户协议（静态内容） |
| `/agreement/privacy` | **新增** | 隐私政策（静态内容） |
| `/enterprise` | **新增** | 企业采购落地页（FR-047） |
| `/templates` | **新增（信息页）** | 模板中心（FR-053 / design-new-pages §3.12）→ `TemplateCenterView.vue` |
| `/changelog` | **新增（信息页）** | 更新日志（FR-053 / §3.13）→ `ChangelogView.vue` |
| `/about` | **新增（信息页）** | 公司介绍（FR-053 / §3.14）→ `AboutView.vue` |
| `/news` | **新增（信息页）** | 新闻动态（FR-053 / §3.15）→ `NewsView.vue` |
| `/contact` | **新增（信息页）** | 联系我们（FR-053 / §3.16）→ `ContactView.vue` |
| `/jobs` | **新增（信息页）** | 加入我们（FR-053 / §3.17）→ `JobsView.vue` |
| ~~`/login`~~ | **不设此路由** | 登录由 `LoginDialog` 弹层统一承载。需要登录时在当前路由加 `?login=1&redirect=<path>` 打开弹层，登录成功后按 `redirect` 回到原处（FR-010 / FR-015）。**路由总数为 23 条**：6 条来自设计稿 + 11 条功能页 + 6 条信息页 |

> **风险提示（工作量）**：**17 条路由没有设计稿**（11 条功能页 + 6 条信息页）。**该风险已由 [design-new-pages.md](./design-new-pages.md) 主动消化** —— 该规范给出令牌、8 个新组件契约（C1–C8）与逐页结构，实现者不再需要自行发明版式；T129 用其 §5 的客观口径核对。设计稿覆盖的页面只占交付路由的约四分之一（6/23），这是本期最大的工作量来源。

## 实施分期

**与 [tasks.md](./tasks.md) 的阶段划分逐条对齐**（早期版本两处错位：地址管理曾记为 P2、协议页曾记为 P6，均以 tasks.md 为准）。

| 阶段 | 对应 tasks.md | 内容 | 交付后可独立验证 |
|---|---|---|---|
| **Phase 1** | T001–T015 | 工程骨架 + 测试框架 + 22 个占位视图 + 路由 + **样式与资产搬入（含 C1–C8 组件契约）** | `pnpm dev` 起得来，23 条路由可打开 |
| **Phase 2** | T016–T035 | 请求层（`tenant-id` 注入 / `body.code` 判定 / 401 刷新）+ 金额工具 + 外壳与原子组件 | 基础设施就绪，`SiteHeader` 有结构但无实时状态 |
| **Phase 3** | T036–T059 | **US1** 商品展示：首页 / 商城 / 详情 / 三个内容页 / 首屏性能 | US1 可独立演示 |
| **Phase 4** | T060–T074 | **US2** 登录：隐式注册 + 协议门禁 + 个人中心 + 设密码 + 协议页 | US2 完成（**MVP 才算可用**） |
| **Phase 5** | T075–T087 | **US3** 购物车 + 顶栏角标 | US3 |
| **Phase 6** | T088–T113 | **US4** 结算与下单：**地址管理在此**（不再放 P2）+ 券选择 + 订单列表/详情 + 取消 | 交易闭环最小可用形态 |
| **Phase 7** | T114–T126 | **US5** 模拟支付 + 领券中心 + 我的券 | US5 完成，无需任何商户号 |
| **Phase 8** | T118–T126 | **US7** 企业采购落地页 + **6 个信息页**（模板中心/更新日志/公司介绍/新闻动态/联系我们/加入我们） | US7 + 全站入口可达 |
| **Phase 9** | T136–T138 | **US6** 全站贯通核对 + **17 条新页面视觉一致性归口验证** | 全站一致 |
| **Phase 10** | T139–T151 | e2e 两段 + **全站 48 处死链接线** + 巡检 + 执行 quickstart + 硬约束核对 | 收尾 |

**MVP = Phase 1 + 2 + 3 + 4**（Phase 1–2 是基础设施，US1 与 US2 同为 P1）。仅交付 US1 时页面上的「登录 / 注册」「加入购物车」入口都是死的，属"可演示"而非"可用"。

## 测试策略

**依据**：宪法原则 I（测试先行，不可协商）。测试先写、先失败、再实现；每个故事内测试任务排在其实现任务之前。

### 三层

| 层 | 工具 | 覆盖对象 | 对应成功标准 |
|---|---|---|---|
| 单元 | Vitest | 金额换算与核对、订单状态 5 态映射、角标派生、响应适配器（分→元、字段缺失兜底）、手机号校验等纯函数 | SC-007 / SC-013 / SC-017 / SC-021 |
| 组件 | Vitest + `@vue/test-utils` | 协议门禁（未勾选不得发码/登录）、结算金额明细渲染、角标渲染与去重、顶栏登录态、购物车失效条目、空/错误态 | SC-015 / SC-007 / SC-021 / SC-009 / SC-016 |
| 端到端 | Playwright（**1 条冒烟**） | 注册（验证码 `9999`）→ 加购 → 下单 → 模拟支付 → 订单变「待发货」 | SC-010（无商户号完整闭环），并连带覆盖 SC-002 / SC-003 / SC-011 / SC-017 |

### 端到端的前置（决定了它能跑但成本不低）

1. 后端必须先起来：`mvn -B -DskipTests package` 后 `java -jar yudao-server/target/yudao-server.jar`（**不带** `--spring.profiles.active=local`）。
2. 远程 Redis 与 CynosDB 必须可达（云安全组放行开发机当前出口 IP；家庭宽带 IP 变动后需重新加白）。
3. `pay_channel` 需有 `code='mock'` 且 `app_id=1` 的启用行。
4. 验证码固定 `9999`，故冒烟脚本不依赖短信通道。
5. 首次以 jar 启动会因 `flowable.database-schema-update: true` 变更远程表结构 —— **e2e 不是只读操作**，不要在共享库上随意反复跑。

**结论**：e2e 可行但依赖外部状态，因此**只做一条覆盖核心闭环的冒烟**，不做全量 21 条 SC 的 e2e。其余 SC 由单元/组件测试 + [quickstart.md](./quickstart.md) 的手工清单覆盖。

### 明确不做的

- **不铺满 21 条 SC 的 e2e**：收益低于维护与外部依赖成本。哪些 SC 不做自动化，在 [tasks.md](./tasks.md) 的收尾阶段逐条声明。

## 主要风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| **`tenant-id` 漏传** | 全站接口返回 `{code:400}` 但 **HTTP 200**，表现为"页面空数据却不报错"，极难排查 | 在 `service.ts` 的请求拦截器里**无条件注入**，不依赖任何开关；拦截器按 `body.code` 而非 HTTP 状态判断成功（见 [contracts/app-api.md](./contracts/app-api.md)） |
| **支付成功后误显示为「已支付」** | 后端实际进入的是「待发货」，展示错误会持续存在 | 状态映射集中在 `utils/orderStatus.ts`，并由 `orderStatus.spec.ts` 覆盖 5 态（SC-017） |
| **11 条新路由无设计稿** | 工作量被低估；视觉可能与设计稿割裂 | 提前列出路由清单（见上）；新样式集中在 `store.css`，强制沿用设计稿配色与字号 |
| **`pay_channel` 缺 `mock` 行** | 提交支付因渠道不可用而失败 | 写入 [quickstart.md](./quickstart.md) 的排查项 |
| **金额单位混淆（分/元）** | 金额错 100 倍；SC-007 失败 | 换算只允许走 `utils/money.ts`，由 `money.spec.ts` 覆盖 |
| **协议勾选被持久化** | 刷新后被视为"已同意"，违反同意门禁 | 同意状态**只存内存**，不写本地存储（见 [data-model.md](./data-model.md) §3） |
| **e2e 冒烟受外部状态影响** | 远程 Redis/CynosDB 不可达、安全组未放行、`pay_channel` 缺 mock 行都会让冒烟失败，产生"测试坏了"的误判 | 冒烟只 1 条；失败时先按 [quickstart.md](./quickstart.md) §6 排查环境，再怀疑代码；在 tasks 收尾阶段记录其前置条件 |
| **测试先行与交付顺序的摩擦** | 每层都要先写测试，初期看着更慢 | 这是宪法硬约束，不豁免。测试范围只覆盖有行为契约的部分，不追求行覆盖率指标 —— 避免为了指标写无价值测试 |

## Post-Design Re-check

Phase 1 产物（`data-model.md`、`contracts/app-api.md`、`quickstart.md`）产出后，按**宪法 v1.0.0 的五条原则**复检：

- **I 测试先行**：设计过程确认了可测试的边界 —— 三层测试的对象都已明确到文件级（见上），且 [tasks.md](./tasks.md) 内测试任务前置于实现任务。**此条在早期版本曾违规**（依据一条被推翻的"后端无法无头启动"），已于 2026-09-24 更正。✅
- **II 先澄清，不猜测**：设计过程新暴露的契约陷阱（`pointStatus` 必填、`refreshToken` 走 query、列表 VO 与详情 VO 字段不同、`--spring.profiles.active=local` 会覆盖 `local,my`）均已写入 `contracts/app-api.md` 与 `research.md`，未遗留隐含假设。✅
- **III 复用既有能力**：`data-model.md` 明确声明"不新建持久化实体"，全部为读取-适配-展示；`contracts/app-api.md` 第 8 节显式列出不调用的 9 个接口族。✅
- **IV 最小改动**：Phase 1 未引入 UI 库、未建 monorepo、未改 `yudao-cloud/` 与 `www/`。唯一的框架级新增是测试工具，且是宪法原则 I 强制要求。✅
- **V 增量可验证**：21 条 SC 均可追溯到具体验证方式；每条 SC 的验证手段已逐条落到 [tasks.md](./tasks.md)。**明确声明不验证的是五项**（与上方 Constitution Check 表一致）：SC-006、FR-033 的超时释放半段、SC-018 的超时自动取消半段、SC-013 的券单次使用半段，理由均写在 [quickstart.md](./quickstart.md) §5。⚠️（原文此处仅写 SC-006，与同文件上方矛盾，已更正）

**Gate 结论：无违反项。可以进入 `/speckit-tasks`。**
