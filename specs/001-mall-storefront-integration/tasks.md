---
description: "Task list for 赋签门面站与 yudao 后台打通"
---

# Tasks: 赋签门面站与 yudao 后台打通

**Input**: Design documents from `/specs/001-mall-storefront-integration/`
**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/app-api.md](./contracts/app-api.md)、[quickstart.md](./quickstart.md)

**Tests**: **测试任务是必需的，且先于其实现任务。** 依据 `.specify/memory/constitution.md` 原则 I（测试先行，不可协商）：

- **Vitest 单元** — 金额换算与六项核对、订单状态映射、角标派生、响应适配
- **Vitest 组件** — 协议门禁、金额明细、角标渲染、登录态、失效条目、空/错误态
- **Playwright 端到端（1 条，两段，Phase 10）** — **它是事后验收/回归护栏，不是 TDD 证据**（T131）

每个配对内**先写测试、确认失败、再实现**。同一配对的测试与其实现**不得同标 `[P]`** —— 那等于让它们并行跑，"先行"就不存在了。

**Organization**: 按用户故事分组。**顺序经过修订**（相对早期版本）：
1. `SiteHeader` 的实时状态拆为三个增量，各自随所属故事交付（早期版本把登录态与角标的断言放在 Phase 2，而实现在 Phase 4/5，导致 Phase 2 内不可满足）；
2. 「下单后角标刷新」的断言移到 US4（实现在 Phase 6）；
3. **US6（全站贯通）排在 US7 之后** —— 它要核对包含 `EnterpriseView` 在内的全部 16 个 view。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US1–US7），仅用户故事阶段使用
- 所有任务均含确切文件路径

## Path Conventions

新站位于仓库根的 **`storefront/`**（不放进 `yudao-cloud/`，纯为目录语义清晰）。原先与设计稿 `www/` 平级，**该目录已于 2026-09-27 删除**。

**两条硬约束（全任务通用）**：
1. 不引入 UI 组件库
2. **测试先于实现**（宪法原则 I）

> **2026-09-27 修订（两次）**：
> - 原第 1 条「不得改动 `yudao-cloud/`」删除（随宪法 2.0.0）：本项目定位为**二开**，
>   改管理端的加载动画、首页看板、DocAlert 提示都是正常需求。**这不是"放宽"，是移除**。
> - 原第 1 条「不得改动 `www/`」删除（随宪法 2.1.0 / spec.md FR-046 修订）：设计稿基线
>   （FR-046 / SC-012）已放开，**允许在设计稿基础上做合理创新**（更符合使用习惯与观感），
>   无需逐处声明。同时：
>   · `www/` 目录**已从仓库删除**（可回溯：git 历史 `9df135c3`）
>   · `design.css` **不再是** `www/css/style.css` 的逐字拷贝 —— 它现在是本项目**可演进**的
>     样式基线；原先那条字节一致断言已一并删除
>   · 视觉一致性改由 `design-consistency.spec.ts` 的可执行断言保障（16 个新页面逐个查
>     色表/断点/不自带顶栏页脚），不再依赖任何外部参照物

**术语对照**：
- **用户券** = 实体；**我的券** = 展示它的页面（`/coupon/mine`）；接口 `/promotion/coupon/page`
- **领券中心** = 可领取券模板的页面（`/coupon`）；接口 `/promotion/coupon-template/list`
- **待发货** = 后端 `status=10`，即**支付成功后的状态**，**不是「已支付」**
- **金额单位** = 分（`Integer`）；换算只走 `utils/money.ts`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 工程初始化、测试框架就位、占位组件让路由可编译

- [x] T001 创建 `storefront/` 目录结构（`src/{api,components,config,data,layouts,router,store,styles,types,utils,views}` + `e2e/`）。注：`src/data/placeholders.ts` **已预置**（占位内容种子），无需重新生成与 `index.html`，按 [plan.md](./plan.md) 的 Source Code 树
- [x] T002 创建 `storefront/package.json`：运行时 Vue 3.5 / Vite 8 / TS 6 / Pinia 3 / vue-router 5 / axios 1.16 / @vueuse/core 14；开发依赖 vitest / @vue/test-utils / jsdom / @playwright/test；`engines` node>=20.19；scripts 含 `dev`/`build`/`preview`/`test`/`test:e2e`/`ts:check`
- [x] T003 [P] 创建 `storefront/vite.config.ts`（`@/` 别名、`VITE_PORT`、`VITE_BASE_PATH`、`VITE_OUT_DIR`）。**不配置 server.proxy** —— 后端 CORS 已放开所有来源（[research.md](./research.md) R4）
- [x] T004 [P] 创建 `storefront/tsconfig.json`
- [x] T005 [P] 创建 `storefront/vitest.config.ts`（jsdom、`@/` 别名、`src/**/*.spec.ts`、**排除 `e2e/`**）
- [x] T006 [P] 创建 `storefront/playwright.config.ts`（`testDir: 'e2e'`、baseURL `http://localhost:5173`、单 worker、失败保留 trace）
- [x] T007 [P] 创建 `storefront/eslint.config.js`（**flat config**，与 eslint 10 的 admin-vue3 形态一致）
- [x] T008 [P] 创建 `storefront/.env` 与 `.env.local`：`VITE_BASE_URL=http://localhost:48080`、`VITE_API_URL=/app-api`、`VITE_TENANT_ID=1`、`VITE_PORT=5173`、`VITE_TITLE`
- [x] T009 [P] 复制设计稿的**样式与图片资产**：① 将 `www/css/style.css` **原样**复制为 `storefront/src/styles/design.css`（不改一个字符），文件头加来源注释；② 把 `www/assets/logo.png` 复制到 `storefront/public/assets/logo.png`。**logo 是设计稿唯一的图片资产，被 18 处引用（每页 favicon + 顶栏 logo + 页脚 logo），全设计稿 12 个 `<img>` 全是它 —— 漏了它全站 logo 就挂**（`logo-original.png` 未被任何页面引用，不搬）
  → **2026-09-27 修订**：「原样、不改一个字符」这半句**已失效**（设计稿基线放开，`design.css` 可演进）；`www/` 也已删除。**logo 的复制仍然有意义** —— `storefront/public/assets/logo.png` 是站点实际使用的图片资产，与设计稿目录无关
- [x] T010 [P] 创建 `storefront/src/styles/store.css`，**按 [design-new-pages.md](./design-new-pages.md) 落地**：① §1 的令牌（配色/圆角/字号/断点，全部取自 `design.css`）；② §2 的 **8 个新组件契约 C1–C8**（`.ml-modal` 弹层 / `.ml-toast` 轻提示 / `.ml-check` 复选框 / `.ml-field` 表单 / `.ml-skeleton` 骨架屏 / `.ml-pill` 状态标签 / `.ml-steps` 步骤条 / `.ml-amount-row` 金额行）；③ 文件顶部写明「新增页面 MUST 复用 `design.css` 的类与本文的 C1–C8，不得引入第二套按钮或卡片样式」。**这是 17 条无设计稿路由的版式归口**，由 T129 用 §5 的口径核对
- [x] T011 [P] 创建 `storefront/src/types/`（**`src/data/placeholders.ts` 已存在，占位内容已在其中**，此处只补类型）：`common.ts`（`CommonResult`/`PageResult`）、`product.ts`、`category.ts`、`cart.ts`、`order.ts`、`member.ts`、`address.ts`、`coupon.ts`、`pay.ts`（按 [contracts/app-api.md](./contracts/app-api.md)，**金额字段注释为分**）
- [x] T012 创建 22 个 view 占位组件（`storefront/src/views/` 下各一个最小可渲染组件）—— 目的是让 T013 的路由表能编译通过；各故事阶段会逐个替换为真实实现
- [x] T013 创建 `storefront/src/router/index.ts`：声明 **23 条**路由（6 条设计稿 + 11 条功能页 + 6 条信息页）。**不设 `/login` 路由** —— 登录由 `LoginDialog` 承载，需登录时在当前路由加 `?login=1&redirect=<path>`
- [x] T014 创建 `storefront/src/store/index.ts` 组装 Pinia
- [x] T015 创建 `storefront/src/main.ts` 与 `storefront/src/App.vue`，注册 Pinia、router，导入两份样式表（依赖 T009/T010）

**Checkpoint**: `pnpm install && pnpm dev` 起得来且 17 条路由都能打开（占位页），`pnpm test` 能跑


> **Phase 1 实施记录（2026-09-24）**
>
> 已完成 T001–T015，Checkpoint 全部实测通过：
> `pnpm install` 退出 0 · `pnpm ts:check` 退出 0 · `pnpm test` 退出 0 · `pnpm build` 退出 0 ·
> 23 条路由与 22 个视图文件逐一核对无缺失 · `pnpm check:placeholders` 退出 1（正确地挡住发布）。
>
> 实施中发现并修正的 **4 处与计划的偏差**（都是只有真正构建才会暴露的问题）：
> 1. **vitest 必须用 5.x，不是 3.x**。Vite 8 已改用 rolldown，而 vitest 3 依赖 rollup 版 vite 7，
>    两者 `Plugin` 类型不兼容。`vitest@5.0.1` 的 peer 明确支持 `vite ^8`。计划里写的「vitest 3.x」是错的。
> 2. **需要 `@types/node`**，且 tsconfig 的 `types` 要含 `node` —— 配置文件用了 `node:url` 与 `process`。
> 3. **`tsconfig` 不能用 `baseUrl`** —— TypeScript 6 已废弃它；`paths` 改为相对 tsconfig 的 `./src/*`。
> 4. **pnpm 12 的依赖构建白名单叫 `allowBuilds`，写在 `pnpm-workspace.yaml`**（不是 package.json 的 `pnpm.onlyBuiltDependencies`）。
>    不配它 `pnpm install` 会因 esbuild 的 postinstall 被拦截而以退出码 1 失败。
>
> 另：`siteMeta`/`SiteHeader`/`SiteFooter` 在 Phase 1 先建**最小占位**，否则 `DefaultLayout` 无法编译；
> 由 T026 / T027 / T079 等替换为按设计稿还原的真实实现。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有用户故事共同依赖的基础设施

**⚠️ CRITICAL**: 本阶段完成前，任何用户故事都无法开始

### 令牌存取（先于请求层，因为拦截器依赖它）

- [x] T016 [P] 实现 `storefront/src/utils/auth.ts`：令牌存取，key 固定 `ACCESS_TOKEN` / `REFRESH_TOKEN`（**不与 uniapp 的 `token` 混用**）

### 请求层（测试先行）

- [x] T017 写 `storefront/src/config/http/service.spec.ts`（**先失败**）：① `tenant-id` 被**无条件**注入（不依赖开关）；② 成功判定按 `body.code`，含 **HTTP 200 + `body.code=400`（租户缺失）必须判为失败**；③ 401 触发去重刷新与队列回放；④ 刷新失败清登录态
- [x] T018 实现 `storefront/src/config/http/config.ts`：`baseURL = VITE_BASE_URL + VITE_API_URL`、超时 30s、**成功码 `0`**
- [x] T019 实现 `storefront/src/config/http/index.ts`：导出 `get/post/put/delete`，统一剥一层 `res.data`
- [x] T020 在 `storefront/src/config/http/service.ts` 实现请求拦截器：注入 `tenant-id`（**无条件、不加开关**）、`terminal: 20`、需登录时注入 `Authorization: Bearer <token>`
- [x] T021 在 `storefront/src/config/http/service.ts` 实现响应拦截器：**按 `body.code` 判成功**、统一错误提示、可重试
- [x] T022 在 `storefront/src/config/http/service.ts` 实现 401 无感刷新（**`refreshToken` 必须是 query 参数**）—— 使 T017 转绿

### 金额（测试先行）

- [x] T023 写 `storefront/src/utils/money.spec.ts`（**先失败**）：分→元换算、两位小数、零与负数、以及 `reconcile()`。`reconcile` MUST 按**六项**核对 `totalPrice - couponPrice - pointPrice - discountPrice + deliveryPrice - vipPrice`（**SC-007**；漏掉 `vipPrice` 会在会员折扣生效时假失败）
- [x] T024 实现 `storefront/src/utils/money.ts`：换算与格式化的唯一实现处 + `reconcile()` —— 使 T023 转绿

### 外壳与原子组件（SiteHeader 分三个增量，本阶段只做静态结构）

- [x] T025 写 `storefront/src/components/SiteHeader.spec.ts`（**先失败**）：**只断言静态结构** —— 设计稿顶栏的 logo、五个导航项、登录入口都渲染。实时登录态与角标的断言分别在 T066 与 T079 追加（各随所属故事，避免在此断言后续阶段才存在的行为）
- [x] T026 [P] 实现 `storefront/src/components/SiteFooter.vue`：按设计稿页脚还原，「企业批量采购」链接指向 `/enterprise`（FR-048）
- [x] T027 实现 `storefront/src/components/SiteHeader.vue` 的**外壳**（结构 + 静态文案，暂不接实时状态；「登录 / 注册」为触发器，点击调用 T059 的弹层）—— 使 T025 转绿
- [x] T028 [P] 实现 `storefront/src/components/EmptyState.vue`：空状态与错误态（可重试）两种模式（FR-044 / FR-045）
- [x] T029 [P] 实现 `storefront/src/components/LoadingState.vue`：加载中骨架（FR-045）
- [x] T030 [P] 实现 `storefront/src/components/Pagination.vue`：复用设计稿 `.pager` 样式，接分页结果与总数
- [x] T031 实现 `storefront/src/layouts/DefaultLayout.vue`：顶栏 + `<router-view>` + 页脚（FR-042 的结构保证）

### 占位符机制与基础组件（测试先行）

> 门禁与替换流程见 `storefront/src/data/placeholders.ts` 头部注释与 [quickstart.md](./quickstart.md) §4.6。**视图 MUST 用 `<PendingText :value="…" />` 渲染业务文案，不得内联字面量** —— 这是门禁③ 能成立的前提。

- [x] T032 写 `storefront/src/data/placeholders.spec.ts`（**先失败**）：只断言 `isPending()` 的判定契约 —— `isPending('[[x]]') === true`、`isPending('x') === false`、`isPending(undefined) === false`、`isPending(null) === false`。
  **注意（实施时修正）**：本任务早期版本要求同一文件断言 `hasPendingContent() === false` 作为「门禁①」，同时又要求 `pnpm test` 全绿 —— 两者极性相反，**不存在同时满足的状态**。已改为：门禁① 独立成可执行脚本 `pnpm check:placeholders`（见 `storefront/scripts/check-placeholders.mjs`），`pnpm test` 全程可绿。**已双向实测**：未替换的种子 → 脚本退出码 1；替换后的种子 → 退出码 0
- [x] T033 核对 `storefront/src/data/placeholders.ts` 的导出：`isPending` 已导出、**不渲染的核对清单（`PRIVACY_POLICY_CHECKLIST`）不在 `RENDERED` 内**（否则门禁① 永久为真）、`PENDING_ATTR` 与 `RENDERED` 命名一致 —— 使 T032 转绿
- [x] T034 写 `storefront/src/components/base/PendingText.spec.ts` 与 `storefront/src/components/base/index.spec.ts`（**先失败**）：① `PendingText` **从值派生** `data-content-pending`（值含 `[[ ]]` 时属性为 true，否则不渲染该属性）—— 这条保障"替换只需改一个文件"；② C1 弹层的 `Esc` 与点遮罩关闭；③ C2 轻提示 3 秒自动消失且同时最多 3 条；④ C3 复选框禁用态不可点；⑤ C5 骨架屏形状与最终内容一致；⑥ C6 状态变体（FR-041c）：**三个非终态**（待支付 `.is-pending` / 待发货 `.is-awaiting-shipment` / 已发货 `.is-shipped`）的**底色必须两两不同**；已完成与已取消可同底但**文字色必须不同**。
     **注意（实施时修正）**：早期版本写「五个变体背景与文字色两两不同」，而映射表里 30 与 40 共用 `#F0F4FB`、0 与 20 共用 `#FFFFFF` —— 该断言按字面**永远无法变绿**。已改为上面成立的版本；⑦ C7 步骤条三态；⑧ C8 金额行合计为 `.is-total` 且数字等宽
- [x] T035 实现 `storefront/src/components/base/`：`PendingText.vue` + C1–C8 八个组件（`.ml-modal`/`.ml-toast`/`.ml-check`/`.ml-field`/`.ml-skeleton`/`.ml-pill`/`.ml-steps`/`.ml-amount-row` 的**行为**部分；样式部分在 `store.css`，由 T010 交付）—— 使 T034 转绿

**Checkpoint**: 基础设施与测试框架就绪；`SiteHeader` 有结构但无实时状态


> **Phase 2 实施记录（2026-09-24）**
>
> 已完成 T016–T035（20/20）。验证：`pnpm test` **117 例全绿**（8 个测试文件）·
> `pnpm ts:check` 退出 0 · `pnpm build` 退出 0 · `pnpm check:placeholders` 退出 1（正确挡发布）。
> **23 条路由已逐条真实挂载验证**（`src/router/routes.spec.ts`：每条都渲染出外壳与页面主体）——
> `curl` 对 SPA 无效，这是「23 条路由都能打开」的可执行版本。
>
> **一处任务前移**：T091/T092（`utils/orderStatus.ts` 状态映射 + 测试）已随 Phase 2 完成 ——
> C6 状态标签（T035）依赖该映射，不回前移就得在组件里复制一份状态表。
> 其测试含本项目最易错的一处断言：**`status=10` 是「待发货」而不是「已支付」**，
> 并断言变体里不存在历史上那个不可实现的 `is-paid`。
>
> **实施中修正的 3 处问题**（均由工具链发现，非人工 review）：
> 1. axios 响应拦截器返回解包后的数据违反其类型；改为**拦截器原样返回 response、解包在 index.ts**。
> 2. `@vitejs/plugin-vue` 的静态资源 URL 转换让 `public/assets/logo.png` 在 vitest 下解析失败；
>    仅在**测试配置**里关闭该转换，生产构建不受影响。
> 3. 两处我自己写错的测试期望（`yuanToFen(1.005)` 的浮点、路由数多过滤掉首页）——
>    **改的是测试而不是实现**，并在注释里写明判断依据。

---

## Phase 3: User Story 1 - 浏览真实商品 (Priority: P1) 🎯 MVP

**Goal**: 访客看到后台真实商品，可搜索、筛选、排序、进详情；运营改价或下架后前台无需改代码即生效。

**Independent Test**: 后台改价 → 前台刷新即变；搜一个不在当前页的商品名能命中；下架商品详情给「已下架」而非空白。

- [x] T036 写 `storefront/src/utils/badge.spec.ts`（**先失败**）：销量全 0 不产出「热销」、商品数少于名额不铺满、同商品不挂两角标且热销优先、**永不产出「旗舰」「订阅」**（SC-021 / FR-008a-c）
- [x] T037 写 `storefront/src/api/product.spec.ts`（**先失败**）：排序映射（综合不传 / 销量 `salesCount`+`sortAsc=false` / 价格 `price`+`sortAsc=true`）；**必须小驼峰——白名单只认 `price`/`salesCount`，传大写或 `createTime` 会被 `@AssertTrue` 判为「排序字段不合法」**；字段兜底：**只有 `description` 与 `skus` 是详情独有，`introduction` 与 `sliderPicUrls` 在列表接口同样返回**（`AppProductSpuRespVO:19,28`），不得断言它们不存在
- [x] T038 [P] 实现 `storefront/src/api/category.ts`：`/product/category/list`，前端按 `parentId` 组树（后端**不返回**商品数量）
- [x] T039 实现 `storefront/src/utils/badge.ts`（「热销」取当前页销量前 3、「新品」取编号最大的若干）—— 使 T032 转绿
- [x] T040 实现 `storefront/src/api/product.ts`：`page`、`get-detail`、`list-by-ids` —— 使 T033 转绿
- [x] T041 写 `storefront/src/components/ProductCard.spec.ts`（**先失败**）：渲染真实名称/主图/售价/划线价、角标按派生结果渲染、无角标不渲染空占位、**不渲染促销活动标签**（FR-001 / FR-026h / SC-021）
- [x] T042 实现 `storefront/src/components/ProductCard.vue`：复用设计稿 `.p-card`/`.p-title`/`.p-price`/`.p-btn` 类名 —— 使 T037 转绿
- [x] T043 用真实实现替换 `storefront/src/views/HomeView.vue` 占位：还原设计稿 `index.html`；商品区按 `sortField=salesCount&sortAsc=false&pageSize=4` 取真实商品（FR-008）；分类标签可用；**销量全 0 时仍展示真实商品、不空白**
- [x] T044 在 `storefront/src/views/HomeView.vue` 清理写死内容，**并把设计稿自带的未确认声明接到占位符**（FR-043 / **FR-056**）：
  - 客户 logo 行的 6 个真实公司名（顺丰供应链/名创优品/三只松鼠/大润发/国药控股/绝味食品）→ 从 `placeholders.ts` 的 `inheritedClaims.partnerNames` 读取，各带 `data-content-pending`
  - 信任背书数字（50,000+/2,000+/200+/99.9%）→ `inheritedClaims.stats`
  - 客户证言与署名 → `inheritedClaims.testimonial`
  - **这 6 个公司名若并非真实客户，公开宣传可能构成虚假宣传与商标侵权** —— 不得作为"设计稿既有事实"照搬
- [x] T045 用真实实现替换 `storefront/src/views/MallView.vue` 占位：商品网格 + 分页 + 列表头部「共 N 件商品」（总数取分页结果，FR-002a）
- [x] T046 在 `storefront/src/views/MallView.vue` 实现搜索：关键词走**后端全量检索**（不是过滤当前页，FR-003），含无结果空状态
- [x] T047 在 `storefront/src/views/MallView.vue` 实现分类筛选与排序：多级分类（FR-002）+ 三种排序且翻页后保持（FR-004）
- [x] T048 在 `storefront/src/views/MallView.vue` **移除侧栏的「价格区间」与「服务」两组筛选项**（FR-004a）与**全部类目计数**（FR-002a）
- [x] T049 写 `storefront/src/views/ProductView.spec.ts`（**先失败**）：① **富文本安全过滤** —— 渲染含 `<script>alert(1)</script>` 与 `<img src=x onerror=alert(1)>` 的 `description` 后，DOM 中不存在 `<script>`、不存在带 `on*` 属性的元素（**FR-005c / SC-020**，安全相关，MUST 先于实现）；② 富文本为空时详情区整块不渲染（FR-005b）；③ 多规格切换时价格与库存联动、无货规格不可选（FR-006）；④ 商品下架时展示「已下架」而非空白（FR-007）
- [x] T050 用真实实现替换 `storefront/src/views/ProductView.vue` 占位：主图组、名称、副标题、售价、划线价、库存、销量（FR-005）
- [x] T051 在 `storefront/src/views/ProductView.vue` 实现多规格选择：切换时价格与库存联动、无货规格不可选（FR-006）；**单规格商品不渲染空规格表**
- [x] T052 在 `storefront/src/views/ProductView.vue` **用 SKU 规格项承载设计稿「规格参数」表的位置**，移除独立参数表（FR-005a）
- [x] T053 在 `storefront/src/views/ProductView.vue` 实现富文本详情区：渲染 `description` 并**做安全过滤**（FR-005c）；为空时整块不渲染（FR-005b）
- [x] T054 在 `storefront/src/views/ProductView.vue` 处理下架：业务异常时展示「商品已下架」并引导返回商城（FR-007）
- [x] T055 [P] 还原 `storefront/src/views/SoftwareView.vue`（设计稿 `software.html`），版本卡链到对应商品
- [x] T056 [P] 还原 `storefront/src/views/SolutionsView.vue`（设计稿 `solutions.html`）
- [x] T057 [P] 还原 `storefront/src/views/SupportView.vue`（设计稿 `support.html`）
- [x] T058 达到 **SC-004（首屏 ≤ 3 秒）**：对 `MallView.vue` 与 `ProductView.vue` 施加首屏预算——商品图懒加载 + 显式宽高、首屏只发起必要请求、列表请求不阻塞首个卡片渲染；**并按 [quickstart.md](./quickstart.md) §4.1 的 SC-004 条目实测并记录结果**（该条目已写明工具与判定口径）
- [x] T059 核对 `ProductCard.vue` / `MallView.vue` / `HomeView.vue` **不出现促销活动标签**（FR-026h），与 [quickstart.md](./quickstart.md) §4.1 对应条目一致。注意 FR-026h **不在** FR-046 允许的六处偏离内，偏离比对不会替你发现它

**Checkpoint**: US1 可独立演示


> **Phase 3（US1 商品展示）实施记录（2026-09-24）**
>
> 已完成 T036–T059（24/24）。验证：`pnpm test` **222 例全绿**（16 个测试文件）·
> `pnpm ts:check` 退出 0 · `pnpm build` 退出 0 · `pnpm check:placeholders` 退出 1。
>
> **新增一个依赖**：`dompurify`。FR-005c / SC-020 要求富文本安全过滤，而**规划产物里
> 没有指定任何过滤机制**（`sanitize` / `DOMPurify` 在四份文档中零命中）。手写正则
> 过滤本身就是 XSS 的常见来源，故引入成熟实现，并把 11 条攻击用例固化成测试
> （`<script>`、大小写混合 `<ScRiPt>`、事件属性、`javascript:` / `vbscript:` 协议、
> `<iframe>`/`<object>`/`<embed>`、`<svg><script>`、`<style>`）。
>
> **实施中发现并修正的 5 处问题**（全部由运行工具链发现，非人工 review）：
> 1. **未处理的请求拒绝**：`MallView` / `HomeView` 的 `load()` 未捕获异常，请求失败会
>    抛出未处理拒绝且页面**永久停在加载态** —— 正是 FR-045 禁止的行为。已补错误态 +
>    重试按钮，并加了对应测试。
> 2. **自造死链**：`SoftwareView` 里写了 `to="/register-pending"`，该路由不存在，
>    违反 FR-043。已修，并新增**静态链接守卫测试**：遍历所有视图/组件的 `to="/..."`
>    并核对是否指向已声明路由。
> 3. 我写的测试期望错误 3 处（`1.005` 的浮点、无货规格被正确拒绝导致价格不变、
>    `EmptyState` 的按钮不会有 `/mall` 字面量、路由测试对加载态页面断言「有文本」）——
>    **改的是测试，不是实现**。
> 4. 轮播的数量靠挂载后查 DOM 得到，而 VTU 的字符串插槽是纯文本 → 改为 `count` prop。
> 5. 富文本过滤后仍需判断「是否只剩空标签」，否则空富文本会渲染出一个空壳。

---

## Phase 4: User Story 2 - 登录（含隐式注册）(Priority: P1)

**Goal**: 手机号 + 验证码登录，手机号不存在时自动建号（即注册）；登录后可设密码，之后可用密码登录。

**Independent Test**: 用全新手机号 + `9999` 登录 → 顶栏出现该用户；设密码后退出，用密码登录 → 同一账号。

- [x] T060 写 `storefront/e2e/auth.spec.ts`（**先失败**）：注册（验证码 `9999`，用可复现测试手机号）→ 顶栏出现该用户（**SC-002**）；设密码 → 退出 → 用手机号 + 密码登录 → 断言 `userId` 一致且购物车与订单一致（**SC-011 / FR-010a**）。**本文件的测试任务在 US2 内、先于其实现** —— 这正是早期"一条事后 e2e"被宪法原则 I 判为违规的修正方式
- [x] T061 [P] 实现 `storefront/src/api/member.ts`：`login`、`sms-login`、`send-sms-code`（`scene: 1`）、`refresh-token`（**query 参数**）、`logout`、`get`、`update-password`
- [x] T062 写 `storefront/src/store/user.spec.ts`（**先失败**）：**冷启动从本地存储恢复登录态**（FR-013）、退出清登录态与令牌、**协议同意刷新后不保留**（仅内存）
- [x] T063 实现 `storefront/src/store/user.ts`：登录态、会员信息、协议同意（仅内存）、`isLogin` —— 使 T056 转绿
- [x] T064 写 `storefront/src/components/LoginDialog.spec.ts`（**先失败**）：① 未勾选协议时「获取验证码」与「登录」**均不可执行**（SC-015 / FR-050）；② 取消勾选立即回到不可执行（不因"之前勾过"放行）；③ 两种登录方式可切换；④ 验证码**失效/已用/超限/触发发送频率限制**分别给出**可区分**提示，限流提示须**告知还需等待多久**（FR-010b）；⑤ **密码错误提示不暴露账号是否存在**（FR-012）
- [x] T065 实现 `storefront/src/components/LoginDialog.vue`：两种方式 + 协议门禁 + 倒计时与可区分错误提示 + 限流等待时长 + 错误文案不区分账号存在性 —— 使 T058 转绿
- [x] T066 在 `storefront/src/components/LoginDialog.vue` 实现**登录后回到登录前页面继续操作**：读取并消费 `?login=1&redirect=<path>`（FR-010 / FR-015），为 US3 的"未登录加购后自动继续"预留回调
- [x] T067 在 `storefront/src/components/LoginDialog.vue` 处理「未设密码却用密码登录」：引导改用验证码
- [x] T068 写 `storefront/src/views/AgreementView.spec.ts`（**先失败**）：两种内容可渲染、**不发起任何网络请求**（SC-016 / FR-051）
- [x] T069 用真实实现替换 `storefront/src/views/AgreementView.vue` 占位：同一组件两种内容，**纯静态、不依赖接口** —— 使 T062 转绿
- [x] T070 在 `storefront/src/views/AgreementView.vue` 落地两份协议正文：隐私政策须覆盖**收集范围（至少含手机号）、收集与使用目的、保存方式、用户查阅/更正/删除权利的行使途径**四项（FR-052）
- [x] T071 用真实实现替换 `storefront/src/views/AccountView.vue` 占位：个人中心——昵称/脱敏手机号、设置密码（FR-011）、退出登录（FR-014）、两份协议入口（FR-051）
- [x] T072 扩展 `storefront/src/components/SiteHeader.spec.ts`（**先失败**）：追加登录态断言——已登录时渲染昵称或脱敏手机号、「登录 / 注册」变为个人中心入口；退出后恢复未登录（FR-016 / FR-014）
- [x] T073 在 `storefront/src/components/SiteHeader.vue` 接上登录态渲染与切换 —— 使 T066 转绿
- [x] T074 在 `storefront/src/router/index.ts` 实现需登录守卫：未登录时在当前路由加 `?login=1&redirect=<path>` 打开弹层，登录成功后按 `redirect` 回原处并**自动继续原操作**（FR-015）

**Checkpoint**: US1 与 US2 均可独立工作


> **Phase 4（US2 登录）实施记录（2026-09-24）**
>
> 已完成 T060–T074（15/15）。验证：`pnpm test` **266 例全绿**（19 个测试文件）·
> `pnpm ts:check` 退出 0 · `pnpm build` 退出 0 · `pnpm check:placeholders` 退出 1。
>
> **✅ 2026-09-27 更新**：`e2e/auth.spec.ts` 已**实跑通过（5/5）**。本轮重开 US2 修掉了
> 4 处缺陷，其中「设置密码」是**真实产品缺陷**（后端要求 scene 3 验证码，前端从未收集）。
> 详见文末[遗留问题](#遗留问题2026-09-24-首次记录--2026-09-27-重开-us2-修复后更新)。
> 另：`order.spec.ts` 已实测 3/3 绿；`cart.spec.ts` 首次运行 1/4，3 处**全都是测试缺陷**
> （已修，现 4/4 绿）；`pay.spec.ts` 仍待 T114 交付后才有。
>
> **本轮的关键实现取舍**：
> - **登录不设路由**（不建 `/login`）。全站只有顶栏里的一个 `LoginDialog` 实例，
>   所以「任何页面登录 → 所有页面同步」是结构保证，不靠事件广播。
> - **协议门禁卡在「获取验证码」**，不只是提交时校验 —— 同意必须发生在手机号被
>   收集之前（FR-050）。**切换登录方式会重置同意**，否则"在验证码页勾一下再切到
>   密码页"就绕过了门禁。
> - **密码错误提示收敛为一条通用文案**（FR-012）：后端会区分"账号不存在"与
>   "密码错误"，前端必须把它们显示成同一句话。
>
> **实施中发现并修正的 4 处问题**：
> 1. **守卫无限重定向（会导致整个应用挂死）**：守卫把未登录用户重定向到
>    `同一路径 + ?login=1`，而该路径仍需登录 → 又触发重定向。测试运行时 worker
>    被死循环杀掉才发现。已修：带着 `login=1` 再来时直接放行，并加了专门的断言。
> 2. `SiteHeader` 引入 store 后，路由测试因未装 Pinia 而全部失败 —— 测试侧补齐。
> 3. 守卫写在模块级 router 上，测试新建的 router 实例拿不到 → 把守卫导出为
>    `authGuard`，测试**复用同一份逻辑**（而不是在测试里重写一份）。
> 4. 协议页测试用 `vi.mock` 让引入 api 模块直接抛错，从模块层面保证"纯静态、
>    不依赖接口"（FR-051）。

---

## Phase 5: User Story 3 - 购物车 (Priority: P2)

**Goal**: 已登录用户可加购、改量、删除、勾选；顶栏角标显示真实件数。

**Independent Test**: 加购 2 件 → 角标 2；改一件为 3、删另一件 → 角标与金额同步；退出再登录 → 内容不变。

- [x] T075 写 `storefront/e2e/cart.spec.ts`（**先失败**）：登录后加购 → 顶栏角标 +1（FR-019）
- [x] T076 [P] 实现 `storefront/src/api/cart.ts`：`add`、`list`、`update-count`、`update-selected`、`delete`、`get-count`
- [x] T077 写 `storefront/src/store/cart.spec.ts`（**先失败**）：角标数量来自 `get-count` 而非本地计数、**登录成功后重新拉取购物车与角标**（FR-022）、加购/改量/删除后刷新。**「下单后刷新」的断言放在 US4 的 T100** —— 其实现属 Phase 6，此处断言会导致本阶段内不可满足
- [x] T078 实现 `storefront/src/store/cart.ts`：角标数量与刷新时机，含登录后重载 —— 使 T070 转绿
- [x] T079 写 `storefront/src/views/CartView.spec.ts`（**先失败**）：失效条目不可勾选结算（FR-023）、超限数量被回退并提示（FR-021）、合计随勾选变化且与条目小计之和一致
- [x] T080 在 `storefront/src/components/ProductCard.vue` 与 `storefront/src/views/ProductView.vue` 接上「加入购物车」：多规格商品**未选规格时不得加购**并提示（FR-017 / FR-018）
- [x] T081 实现未登录加购的引导：在 `storefront/src/store/cart.ts` 暂存待执行的加购意图，登录成功后自动完成，**不要求用户重新点击**（FR-015；复用 T060 的回调）
- [x] T082 用真实实现替换 `storefront/src/views/CartView.vue` 占位：条目主图/名称/规格/单价/数量/小计 + 勾选合计；改量、删除、勾选（FR-020）
- [x] T083 在 `storefront/src/views/CartView.vue` 处理**失效条目**：用后端已分好的 `invalidList`，标注不可购买且禁止勾选 —— **前端不得自行判断**下架与售罄（FR-023）—— 使 T072 转绿
- [x] T084 在 `storefront/src/views/CartView.vue` 处理库存上限：超限提示并回退（**上限判定以后端为准**，FR-021）
- [x] T085 在 `storefront/src/views/CartView.vue` 提供进入结算的入口，仅提交**被勾选**的条目（FR-025）
- [x] T086 扩展 `storefront/src/components/SiteHeader.spec.ts`（**先失败**）：追加角标断言——未登录不显示数字、已登录显示真实件数、加购后同步
- [x] T087 在 `storefront/src/components/SiteHeader.vue` 接上购物车角标 —— 使 T079 转绿

**Checkpoint**: US1–US3 均可独立工作


> **Phase 5（US3 购物车）实施记录（2026-09-24）**
>
> 已完成 T075–T087（13/13）。验证：`pnpm test` **294 例全绿**（21 个测试文件）·
> `pnpm ts:check` 退出 0 · `pnpm build` 退出 0 · `pnpm check:placeholders` 退出 1。
>
> **两个"不要自作聪明"的约束已落进代码与测试**：
> - **有效/失效由后端 `validList`/`invalidList` 决定**，前端不重新判断下架与售罄
>   （FR-023）。测试里专门有一条：即使后端给的失效条目「库存看起来有 999」，
>   前端也必须标它为失效 —— 判断权不在前端。
> - **角标数量来自 `get-count`**，不是本地累加（FR-019）；件数为 0 时不显示「(0)」。
>
> **未登录加购的完整链路**：卡片把加购意图暂存进 cart store（未登录时不发请求）
> → 跳 `?login=1&redirect=…` 打开弹层 → 登录成功后 `LoginDialog` 调
> `flushPendingIntent()` 自动补上这次加购（FR-015）。**不要求用户重新点一次**。
>
> **实施中发现并修正的 4 处问题**：
> 1. **已登录时的加购路径根本没接到接口** —— 卡片只是 `emit('add')`，而没有任何
>    父组件处理它。是测试断言"点了要发请求"才暴露的。
> 2. `ProductCard` 引入 store 后，它自己 + 两个挂载它的视图测试全部因缺 Pinia 失败 ——
>    三个测试文件补齐。
> 3. `router.push({ query })` 只给 query 不给 path 会去匹配"当前位置"，在未匹配的
>    页面上抛错（表现为未处理的拒绝）；且导航失败本身也会抛。已改显式带 path
>    并兜住导航失败。
> 4. 我写的两条测试期望错误（角标边界、无货规格点了不该改价）——**改测试不改实现**。

---

## Phase 6: User Story 4 - 结算与下单 (Priority: P2)

**Goal**: 从购物车或详情页「立即购买」发起结算，填/选地址、选券、核对金额，提交后生成待支付订单，可查可取消。

**Independent Test**: 从详情页「立即购买」→ 选地址 → 提交 → 订单出现在「我的订单」且为「待支付」。

- [x] T088 写 `storefront/e2e/order.spec.ts`（**先失败**）：从详情页「立即购买」→ 选地址 → 提交 → 订单出现在「我的订单」且状态为「待支付」（**SC-003**）
- [x] T089 [P] 实现 `storefront/src/api/area.ts`：`GET /system/area/tree`（行政区划**必须来自后台**，不得前端内置）
- [x] T090 [P] 实现 `storefront/src/api/address.ts`：`list`、`get`、`get-default`、`create`、`update`、`delete`
- [x] T091 写 `storefront/src/utils/orderStatus.spec.ts`（**先失败**）：5 态（`0 待支付 / 10 待发货 / 20 已发货 / 30 已完成 / 40 已取消`）一一映射、**特别断言 `10` 不是「已支付」**、仅「待支付」提供用户动作、未知状态兜底（SC-017 / FR-041a）
- [x] T092 实现 `storefront/src/utils/orderStatus.ts` —— 使 T083 转绿
- [x] T093 写 `storefront/src/api/order.spec.ts`（**先失败**）：结算的**数组参数手工拼 query**（`items[0].skuId=..&items[0].count=..`）、`pointStatus` 固定 `false`、`deliveryType` 按快递
- [x] T094 实现 `storefront/src/api/order.ts`：`settlement`、`settlement-product`、`create`、`page`、`get-detail`、`cancel`、`get-count` —— 使 T085 转绿
- [x] T095 [P] 实现 `storefront/src/api/coupon.ts`（本阶段只需结算相关部分）：我的券分页 `page`（`status` 1/2/3）与 `get-unused-count`
- [x] T096 用真实实现替换 `storefront/src/views/AddressView.vue` 占位：地址增删改查，`areaId` 用**三级联动选择器**（`name`/`mobile`/`areaId`/`detailAddress`/`defaultStatus` 均必填）（FR-027）
- [x] T097 在 `storefront/src/views/AccountView.vue` 接上「收货地址」入口跳转 `/account/address`
- [x] T098 写 `storefront/src/views/CheckoutView.spec.ts`（**先失败**）：① 金额明细含商品小计/促销优惠/运费/优惠券抵扣/应付总额，且**总额等于各明细之和**（SC-007）；② **促销优惠与优惠券抵扣分列两行**（FR-026g）；③ **无积分抵扣行**；④ 不可用券标注具体原因且选不中（FR-026b）；⑤ 可选「不使用优惠券」且总额恢复；⑥ **价格变动时要求用户确认**（FR-030）
- [x] T099 用真实实现替换 `storefront/src/views/CheckoutView.vue` 占位：金额明细 + `reconcile()` 六项核对 —— 使 T090 ①②③ 转绿
- [x] T100 在 `storefront/src/views/CheckoutView.vue` 实现券选择：**可用性只由结算响应的 `match`/`mismatchReason` 判定** —— 使 T090 ④⑤ 转绿
- [x] T101 在 `storefront/src/views/CheckoutView.vue` 实现地址选择与切换，切换后**运费与总额重新计算**（FR-028）；无地址时引导新增
- [x] T102 在 `storefront/src/views/CheckoutView.vue` 实现「立即购买」入口：只结算该商品与所选规格数量，不经过购物车（FR-024）
- [x] T103 在 `storefront/src/views/CheckoutView.vue` 实现提交订单：**固定传 `pointStatus: false`**（后端标 `@NotNull`）、`deliveryType` 按快递、可选 `couponId`（FR-027a）
- [x] T104 在 `storefront/src/views/CheckoutView.vue` 提交按钮上**防止重复提交产生多笔订单**（提交中禁用 + 幂等保护）（FR-032）
- [x] T105 在 `storefront/src/views/CheckoutView.vue` 处理下单前校验失败：告知是哪件商品库存不足/已下架，且**不丢失已填地址与商品选择**（FR-029）
- [x] T106 在 `storefront/src/views/CheckoutView.vue` 实现价格变动确认：以最新价重算并要求用户确认，**不静默按旧价或新价成交**（FR-030）—— 使 T090 ⑥ 转绿
- [x] T107 下单成功后清理 `storefront/src/store/cart.ts` 中已被结算的条目（FR-031）。**不在此处刷新角标** —— 那由 T100/T101 以测试先行方式交付，此处一并做会让 T100 永远不先红
- [x] T108 扩展 `storefront/src/store/cart.spec.ts`（**先失败**）：追加**「下单成功后角标同步刷新」**的断言（原属 T070，移到此处以避免跨阶段不可满足）
- [x] T109 在 `storefront/src/store/cart.ts` 实现下单后的角标刷新 —— 使 T100 转绿
- [x] T110 用真实实现替换 `storefront/src/views/OrderListView.vue` 占位：订单列表（订单号、时间、金额、状态），支持按状态查看（FR-034）
- [x] T111 用真实实现替换 `storefront/src/views/OrderDetailView.vue` 占位：商品明细、收货信息、金额构成、状态；展示**支付截止时间**（FR-035 / FR-041）与**支付时间**（FR-039）；**体现该订单所用券及其抵扣金额**（FR-026f）；并让**「待发货」与「已发货」在视觉上可区分**（FR-041c —— 需主动实现，不只是两个不同字符串；早期版本无任何任务承载本条）
- [x] T112 在 `storefront/src/views/OrderDetailView.vue` 与 `OrderListView.vue` 实现取消订单：仅「待支付」可用，取消后库存与所用券均释放（FR-036 / FR-026e）
- [x] T113 在 `storefront/src/views/AccountView.vue` 与 `storefront/src/components/SiteHeader.vue` 暴露「我的订单」入口（FR-034）
  → **2026-09-27 补做完成**：当时只做了 AccountView 侧（见下方 Phase 6 记录），SiteHeader 侧因设计稿基线约束而搁置；约束放开后已补上 —— 主导航新增「我的订单」项指向 `/order`（移动端汉堡菜单同源可达）。e2e：`order.spec.ts` 的「顶栏『我的订单』直达订单列表」

**Checkpoint**: 已具备完整交易闭环的下单与查单能力

> **Phase 6（US4 结算与下单）实施记录（2026-09-24）**
>
> 已完成 T088–T113（26/26）。验证：`pnpm test` **376 例全绿**（26 文件）·
> `pnpm ts:check` 退出 0 · `pnpm build` 退出 0 · `node scripts/check-placeholders.mjs` 退出 1（符合预期）。
>
> **⚠️ 本阶段最重要的发现：把 e2e 真正跑起来之后，暴露出一批「单元测试全绿、
> 真实链路走不通」的缺陷。** 根因是**组件测试自己 `setTokens` 造假登录态、
> 自己 `props` 造假路由参数**，于是把三段真实接线整段遮住了：
>
> 1. **登录令牌从未落盘** —— `setTokens` 只在刷新令牌路径里被调用过，登录接口
>    把响应丢掉了。表现是「登录弹层关了、后端也建了号，但刷新一下又成未登录」。
>    `store/user.ts` 的注释一直写着「令牌已由登录接口写入」，是**实现漏了**。
>    修在 `src/api/member.ts`（T108 同批修，属 US2 范围，见文末「遗留」）。
> 2. **`/product/:id` 与 `/order/:id` 缺 `props: true`** —— 组件拿到的 `id` 恒为
>    undefined，请求成了 `get-detail?id=NaN`。详情页在真实链路里从未取到过数据。
> 3. **商城卡片发 `{"skuId":0}`** —— 列表接口 `AppProductSpuRespVO` **不返回 `skus`**，
>    而卡片按 `spu.skus[0].id` 取值。修法：`skus` 为空时按需拉一次详情，
>    并以详情返回的 `specType`/`skus` 为准。
>
> **契约文档本身的错误（已订正 [contracts/app-api.md](./contracts/app-api.md)）**：
>
> 4. `/trade/order/settlement-product` **不是直购结算端点** —— 它只收 `spuIds`、标着
>    `@PermitAll`、返回列表/详情用的活动价格（`List<AppTradeProductSettlementRespVO>`）。
>    **直购与购物车结算走同一个 `/trade/order/settlement`**（后端 `calculatePrice`
>    的「情况一 skuId+count」）。已删除 `settlementProduct`，并把两处调用合并。
> 5. **`items[0].skuId` 的方括号必须百分号编码成 `%5B`/`%5D`** —— 后端没有配
>    `server.tomcat.relaxed-query-chars`，而硬约束不许改后端；字面方括号会被 Tomcat
>    以 HTML 400 直接拒收，前端只看到「结算失败」，极易误判成业务错误。
> 6. **订单详情的金额字段是平铺的**（没有嵌套 `price`），早期按嵌套建模，
>    视图读 `order.price.totalPrice` 直接抛 TypeError。
> 7. **购物车来源的结算入参**：`Item.skuId` 标了 `@NotNull`，所以不能只传 cartId；
>    而 URL 上只有 cartId，故改为**先拉一次购物车列表**取 skuId/count
>    （不能从结算响应里反查 —— 第一次请求时响应还不存在）。
>
> **两处刻意的判断**：
> - **T113 当时未改动 `SiteHeader.vue`**（**该处置已于 2026-09-27 改变，见 T113 的补做注**）：FR-046 把设计稿页面的允许偏离限定为六处，
>   往顶栏加「我的订单」会是第七处。而 FR-016 的措辞是「变为可进入个人中心
>   （**含」我的订单"**）的入口」—— 顶栏 → `/account` → 侧栏「我的订单」已满足。
>   AccountView 另按 design-new-pages §3.5 补了三张 `.entry-card` 快捷入口。
> - **订单列表的「立即支付」只跳详情页**，不在此复制一份支付调用 ——
>   支付的唯一实现处是订单详情（T117）。
>
> **两处金额/文案的诚实处理**：
> - `formatYuan` 的负号改为在货币符号之前（`-¥30.00`，原为 `¥-30.00`），
>   并在 `money.spec.ts` 补了断言 —— 优惠行一直是这么显示的。
> - 订单详情**券名后端不返回**（`AppTradeOrderDetailRespVO` 只有 `couponId`/`couponPrice`），
>   故只展示能拿到的事实（抵扣金额 + 券编号 1024），**不编造券名**。券名需后端补字段。
>
> **测试基建**（不修则整套 e2e 无法运行，见文末「遗留」）：
> - `.ml-check input` 是视觉隐藏控件，`check()` 必然超时 → 统一改用可见的 `.ml-check-box`；
> - 点「获取验证码」后必须等发码请求回来（以按钮进入倒计时为准）再提交登录，
>   否则 `sms-login` 与 `send-sms-code` 并发，后端回「验证码不存在」；
> - Playwright 浏览器从未安装过，`npx playwright install chromium` 后 e2e 才首次真正执行；
> - 属地三级联动**不能盲选 `index 1`**：树的前两项是港澳，`children` 为空。

**Checkpoint**: 已具备完整交易闭环的下单与查单能力

---

## Phase 7: User Story 5 - 支付闭环与优惠券 (Priority: P3)

**Goal**: 待支付订单经模拟通道完成付款进入「待发货」；用户可领券、看券、在结算时用券。

**Independent Test**: 待支付订单走模拟支付 → 变「待发货」；再次发起被拒；领券后结算页可立即选用。

- [x] T114 写 `storefront/e2e/pay.spec.ts`（**先失败**）：对待支付订单走模拟支付 → 订单变「待发货」（**SC-010**）；并记录墙钟时间供 SC-002/SC-003 人工判定
- [x] T115 [P] 实现 `storefront/src/api/pay.ts`：`submit`（body `{id: payOrderId, channelCode: "mock"}`）、`get`
- [x] T116 写 `storefront/src/views/OrderDetailView.spec.ts`（**先失败**）：待支付展示支付入口、已付款**不展示**支付入口（FR-040）、支付后以**后端返回的状态**为准而非前端自判（FR-039）
- [x] T117 在 `storefront/src/views/OrderDetailView.vue` 实现支付入口与状态确认：提交支付后拉取订单详情确认状态（可用 `sync=true`），**不得由前端自行把订单标记为已支付**（FR-037 / FR-039）—— 使 T107 转绿
- [x] T118 在 `storefront/src/views/OrderDetailView.vue` 处理支付放弃与中断：订单仍为「待支付」，可再次发起
- [x] T119 [P] 在 `storefront/src/api/coupon.ts` 追加领券能力：`take`（body `{templateId}`）与 `templateList`（`/promotion/coupon-template/list`）
- [x] T120 写 `storefront/src/views/CouponCenterView.spec.ts`（**先失败**）：展示可领取的券与使用条件、一键领取、**重复领取不产生第二张凭证**并给出提示（FR-026c）
- [x] T121 用真实实现替换 `storefront/src/views/CouponCenterView.vue` 占位 —— 使 T111 转绿
- [x] T122 用真实实现替换 `storefront/src/views/MyCouponView.vue` 占位：我的券按状态分组（未使用/已使用/已过期），可跳转到可用商品（FR-026d）
- [x] T123 在 `storefront/src/views/MyCouponView.vue` 实现新人券呈现：首次验证码登录（即注册）后，若运营配了新人券则其出现在「我的券」中（FR-026c）
- [x] T124 在 `storefront/src/views/OrderListView.vue` 与 `OrderDetailView.vue` 核对**券与抵扣金额的展示**（FR-026f）—— 实现已在 T103，此处按 [quickstart.md](./quickstart.md) §4.4 的对应条目走一遍。**核对结果：只有订单详情页能展示**（`优惠券抵扣` 行 + 券编号 + 抵扣金额，由 `OrderDetailView.spec.ts` 覆盖）；**订单列表页展示不了** —— 后端 `/trade/order/page` 的 `AppTradeOrderPageReqVO`/`OrderPageItem` **不含任何券字段**，前端没有数据可显示。FR-026f 的措辞是「体现**该订单**所用券及其抵扣金额」，详情页即满足；若要列表页也显示，需后端在分页响应里补字段（后端不可改，故记为此处的能力边界）
- [x] **T125 在 `storefront/src/components/SiteHeader.vue` 增加领券中心入口，使 `/coupon` 可达（FR-026c）** —— **2026-09-27 最终完成**（先被裁决为不做，同日设计稿基线放开后改判为做，见下方经过）。
  **实现**：主导航新增「领券中心」项指向 `/coupon`。放在主导航而非右侧 `.header-right`，有两个实际好处：① `.nav` 在 ≤1100px 是 `display:none`，**对窄屏顶栏宽度零影响**（塞进右侧会把 375px 顶栏挤爆，已实测 1101–1440px 全程无溢出）；② 移动端汉堡菜单与主导航同源，**未登录访客在手机上也能领券** —— 这正是当初搁置时记下的那个缺口。
  e2e：`coupon.spec.ts` 的「顶栏能直达领券中心（未登录也够得到）」+「移动端汉堡菜单里也有领券中心入口」。
  **（原「不做」的经过，保留作记录）**理由：FR-046 只约束**设计稿的 6 个页面**（`/`、`/mall`、`/product/:id`、`/software`、`/solutions`、`/support`），顶栏属于这 6 页的版式，且写明「允许的设计稿偏离**仅限六处**」；设计稿顶栏只有 首页/商城/标签软件/行业方案/服务支持 五项，加「领券中心」就是**第七处偏离**，SC-012 明令禁止（「不存在未经声明的额外偏离」）。Phase 6 的 T113 也因同一理由没动顶栏。
  **接受的后果**：`/coupon` 未登录访客**没有入口**（登录用户可达：顶栏 → 个人中心 → 侧栏「我的券」→「去领券中心」，或 `/coupon/mine` 页内链接）。
  → **2026-09-27 修订**：这条裁决的**理由已消失**（FR-046 放开后，顶栏加「领券中心」不再是"第七处偏离"）—— 规格上**重新变为可做**。项目所有者本轮决定**仍先不加**，所以结论不变；但性质从"被约束禁止"变成"产品选择"。将来要加时**不需要**再走任何例外条款
- [x] T126 核对 **SC-014**：在 `/coupon` **手动领取**一张券后，**无需刷新或等待**，回结算页即可选用它 —— 按 [quickstart.md](./quickstart.md) §4.6 的对应条目执行。**注意手动领券与 T114 的注册自动发券是两条不同路径**，T114 不覆盖本条

**Checkpoint**: 交易闭环完整，全程无需任何第三方商户号（SC-010）


> **Phase 7（US5 支付闭环与优惠券）实施记录（2026-09-27）**
>
> 已完成 T114–T124、T126（12/13）。**T125 未做** —— 与 FR-046/SC-012 冲突，见上。
> 验证：`pnpm test` **440 例全绿**（32 文件）· `pnpm ts:check` 退出 0 · `pnpm build` 退出 0 ·
> **全量 e2e 16/16 绿**（auth 5 + cart 4 + coupon 2 + order 3 + pay 2，2.7 分钟）。
>
> **支付（T114–T118）**
> - ⚠️ **契约文档漏了一个关键字段**：`AppTradeOrderDetailRespVO` **有 `payOrderId`**
>   （已订正 [contracts/app-api.md](./contracts/app-api.md) 的字段清单）。原清单没有它，
>   会导致「订单详情页拿不到支付单号、无法发起支付」的错误结论。
> - **不需要轮询**：`MockPayClient.doUnifiedOrder` 直接返回 `successOf(...)`，即支付单
>   **提交即成功**；而 `AppTradeOrderController.get-detail?sync=true` 会调
>   `syncOrderPayStatusQuietly` 主动同步并推进交易订单。所以提交后**一次 `sync=true` 重拉
>   即确定性完成**（实测两次运行均 ~4.4s 完成，无竞态），无需引入轮询机制。
> - `payOrderId` 为 null（`payPrice=0` 的订单）时不给支付入口，只提示「本单无需支付」，
>   并在挂载时同步一次状态 —— 按契约要求，**不臆断它会自动完成**。
>
> **优惠券（T120–T124、T126）**
> - **折扣语义**：`PromotionDiscountTypeEnum` 的 `PERCENT` 下，`discountPercent` 是
>   **用户仍需支付的百分比**（`TradeCouponPriceCalculator`：`couponPrice = total - total*pct/100`），
>   所以 `90` 要显示成「9折」。这条容易反着写，已固化成测试。
> - **`discountPercent` 满减券为 null**（线上实测），类型与文案都按此处理，不显示「0折」。
> - **券有效期**：线上三张模板**全是 `validityType=2`（领取之后）**，
>   `validStartTime`/`validEndTime` **都是 null**，只有相对天数 —— 照常渲染会得到
>   「有效期 至 」这种空文案。已按 `fixedStartTerm/fixedEndTerm` 分别渲染。
> - **T123 是后端既有能力，前端无需改动**：`MemberUserProducer` 发
>   `MemberUserCreateMessage` → `CouponTakeByRegisterConsumer` 按运营配置的
>   `RewardActivityDO.giveCouponTemplateCounts` 发券 → 出现在 `coupon/page` 里，即「我的券」。
> - ⚠️ **SC-014 的 e2e 有两个数据陷阱**（都已在用例里注明）：
>   ① 库里有件「1 分小商品」¥0.01，**不能盲点第一张卡**去结算，否则券永远「差 X 元可用」；
>   ② 券模板带 `fixedStartTerm`（「第 3 天生效」「第 10 天生效」），**不能盲领第一张**，
>   否则结算时必然不匹配。用例改为**按卡片单价挑商品** + **只领即时生效的券**。
> - SC-014 的 e2e **领券之后全程 SPA 点击、不再 `page.goto`** —— 整页重载会把
>   「前端缓存了陈旧券列表」这类问题整段掩盖掉，而这正是 SC-014 要验的东西。

### 顺带修掉的一个**用户可见缺陷**（属 US4，2026-09-27 实测发现）

**订单页把时间显示成了原始毫秒数。** 真实 DOM 实测：
`支付截止 1790486340000`、`下单时间 1790479140000`。

- **根因**：yudao 在 `YudaoJacksonAutoConfiguration` 里给 `LocalDateTime` **全局**注册了
  `TimestampLocalDateTimeSerializer` —— 后端返回的时间**一律是 epoch 毫秒数**，
  不是字符串。而订单页直接插值 `{{ order.payExpireTime }}`。
- **为什么没被发现**：单测 fixture 用的是 `'2026-09-24 12:00:00'` 这种字符串，
  **谎报了线上格式** —— 与 `storefront-unit-tests-mask-wiring` 记的是同一类问题
  （这次是"fixture 与线上不一致"，不是"造假入口"）。
- **修法**：新增 `storefront/src/utils/time.ts`（`formatDateTime` / `formatDate`，
  全站唯一实现处，认 epoch 毫秒 / 可读字符串 / ISO），订单详情与订单列表共 4 处插值改走它；
  `types/order.ts` 与 `types/coupon.ts` 的时间字段类型改为 `number | string`
  （原写 `string` 就是在骗人）；**测试 fixture 一并改成毫秒数**，并加了
  「不许出现原始毫秒数」的回归断言。已双向实测：临时回退那行 → 断言复现
  `支付截止 1790222400000`。
- **同类风险已排查**：`couponValidText` 原先用 `s.slice(0,10)` 处理时间，遇到毫秒数会抛
  `s.slice is not a function`（e2e 里真的抛了），是同一个根因，已一并修掉。

---

## Phase 8: User Story 7 - 企业采购落地页 + 6 个信息页 (Priority: P3)

> **顺序说明**：US7 排在 US6 之前。因为 US6 要核对**全部**页面使用 `DefaultLayout`（含 `EnterpriseView.vue`）；若 US7 在后，US6 的核对会在该文件尚不存在时卡住。

**Goal**: 企业客户从商城入口进落地页了解权益并联系销售；不产生线上企业订单。

**Independent Test**: 从商城「企业采购」入口进落地页，权益与联系方式可用；无导向线上企业订单/授信/月结的入口。

- [x] T127 用真实实现替换 `storefront/src/views/EnterpriseView.vue` 占位：企业采购权益（批量阶梯价、月结账期、专属对接）+ 至少一种联系途径（热线电话 / 企业微信），移动端点击电话可拨号（FR-047）
- [x] T128 在 `storefront/src/views/MallView.vue` 的「企业采购」入口与 `storefront/src/components/SiteFooter.vue` 的「企业批量采购」链接指向 `/enterprise`（FR-048）
- [x] T129 核对 `storefront/src/views/EnterpriseView.vue` **不存在**导向线上企业订单、授信或月结下单的入口（FR-049）

### 6 个信息页（承接全站 48 处死链中的营销/公司入口，FR-053）

> **版式依据**：[design-new-pages.md](./design-new-pages.md) §3.12–3.17。这 6 页**无设计稿**，MUST 在该文件给定的令牌（§1）与组件契约（§2 的 C1–C8，由 T010 实现）内实现，**不得自成一套视觉**。
> **内容依据**：FR-054 —— 涉及真实业务事实的内容（公司简介与数据、新闻条目、电话/地址/邮箱、招聘岗位、更新日志、模板数量）**MUST 由业务方提供**。业务方提供前用**可辨识的占位符**（如 `[[公司简介待补充]]`）并标记 `data-content-pending`，**MUST NOT 编造看似真实的文案**。
> 这 6 页均为**静态内容页，不调用任何接口**。

- [x] T130 [US7] 用真实实现替换 `storefront/src/views/TemplateCenterView.vue` 占位：`/templates` 模板中心 —— 页首 + 行业分类 `.cat-pills` + 模板卡网格（4/3/2 列）+ 分页。按 design-new-pages §3.12。**内容读取 `placeholders.ts` 的 `templates`**，逐处带 `data-content-pending`。**模板数量 MUST 用业务方确认的数字，不得沿用设计稿的"2,000+"**（FR-055）
- [x] T131 [US7] 用真实实现替换 `storefront/src/views/ChangelogView.vue` 占位：`/changelog` 更新日志 —— 版本时间线（左侧版本号与日期 + 竖轴线，右侧 `.f-card` 更新项列表 + C6 类型标签 新增/优化/修复）。按 design-new-pages §3.13。**内容读取 `placeholders.ts` 的 `changelog`**
- [x] T132 [US7] 用真实实现替换 `storefront/src/views/AboutView.vue` 占位：`/about` 公司介绍 —— 图文段交替 + `.stats-row` 数据条 + 发展历程时间线。按 design-new-pages §3.14。**内容读取 `placeholders.ts` 的 `about`**；公司与数据文案 MUST 由业务方提供（FR-054）
- [x] T133 [US7] 用真实实现替换 `storefront/src/views/NewsView.vue` 占位：`/news` 新闻动态 —— `.cat-pills` 分类 + 新闻卡列表（左缩略图 + 右标题/摘要两行截断/日期）+ 分页。按 design-new-pages §3.15。**内容读取 `placeholders.ts` 的 `news`**（已给 6 条示例结构）。**业务方提供至少 3 条真实文章前，本页不得发布**（FR-055）
- [x] T134 [US7] 用真实实现替换 `storefront/src/views/ContactView.vue` 占位：`/contact` 联系我们 —— `.contact-card` 网格（客服/合作/售后/媒体）+ 地址卡（含地图占位）+ 工作时间。按 design-new-pages §3.16。**内容读取 `placeholders.ts` 的 `contact` 与 `siteMeta`**。电话/邮箱/地址 MUST 由业务方提供；**设计稿的 `400-800-XXXX` 是占位，MUST NOT 作为正式号码发布**（FR-054）
- [x] T135 [US7] 用真实实现替换 `storefront/src/views/JobsView.vue` 占位：`/jobs` 加入我们 —— `.cat-pills` 职能分类 + 岗位卡列表 + 展开区（职责/要求/投递方式）。按 design-new-pages §3.17。**内容读取 `placeholders.ts` 的 `jobs`**（已给 6 个岗位示例）

---

## Phase 9: User Story 6 - 全站登录态贯通 (Priority: P3)

> **顺序说明**：本故事是**跨页核对**，必须在 US1–US5 与 US7 的页面都存在之后执行。

**Goal**: 所有页面顶栏一致反映登录态与真实购物车件数，任一页面登录/退出其余页面同步。

**Independent Test**: 首页登录 → 跳「标签软件」页仍登录；在该页退出 → 回商城页已恢复未登录且角标消失。

- [x] T136 逐页核对 `storefront/src/views/{HomeView,MallView,ProductView,SoftwareView,SolutionsView,SupportView,CartView,CheckoutView,OrderListView,OrderDetailView,AccountView,AddressView,CouponCenterView,MyCouponView,AgreementView,EnterpriseView}.vue` 均使用 `DefaultLayout`，确保顶栏唯一且状态同源（FR-042）
- [x] T137 核对 `storefront/src/components/SiteHeader.vue` 未登录时**不显示角标数字**，全站不出现设计稿写死的「购物车 (2)」（FR-042 / FR-043）
- [x] T138 **新页面视觉一致性核对**（17 条无设计稿路由的归口验证，判定口径见 design-new-pages.md §5）：把 `/cart`、`/checkout`、`/order`、`/account`、`/coupon`、`/enterprise`、两个协议页与设计稿的商城页并排比对，确认只使用 `storefront/src/styles/store.css` 顶部规定的视觉语言（T010），**不出现第二套按钮/卡片样式**。判定口径见 [quickstart.md](./quickstart.md) §4.6

**Checkpoint**: 全站一致


> **Phase 8（US7 企业页 + 6 个信息页）与 Phase 9（US6 贯通）实施记录（2026-09-27）**
>
> 已完成 T127–T138（12/12）。验证：`pnpm test` **554 例全绿**（41 文件）·
> `pnpm ts:check` 退出 0 · `pnpm build` 退出 0。每页都有独立 spec（共 **59** 条断言）。
>
> **T138 的判定口径被机械化了 —— 这是本轮最值钱的一步。**
> design-new-pages §5 原本是「与设计稿并排比对」的人眼核对，其中三条其实可以断言。
> 新增 `storefront/src/styles/design-consistency.spec.ts`，对 **16 个新页面逐个**检查：
> ① **色值只用 §1.1 表内色**（`#FFFFFF` 同样受表约束，但它在表内）；
> ② **断点只用 1100 / 768 / 480**；③ **不自带顶栏/页脚**（顶栏页脚由 `DefaultLayout` 提供）；
> 另加两条结构核对：17 条无设计稿路由都落在 `DefaultLayout` 之下（T136）、
> 全站代码里不再出现写死的「购物车 (2)」（T137，会自动剥掉注释再查）。
> **它第一次运行就抓出了 5 处真实违规**：我自己在 3 个页面用了 `1024px` 表外断点、
> 更新日志用了 4 个表外色（`#eafaf1`/`#1f9254`/`#fff1e8`/`#c2410c`）。
> 写测试的过程中还发现两处**测试自身**的错误（`expand()` 忘了去 `#`；断点正则误把
> 普通 CSS `max-width: 760px` 当媒体查询），都已修正。
> **§5 里剩下的人眼项**（与设计稿逐像素比对、字号区间、"是否出现第二套按钮/卡片视觉"）
> **未自动化**，如实留作人工核对 —— 不假装已覆盖。
>
> **三处实现取舍**：
> - **「企业采购」进了商城的 `.cat-pills`**（T128）：设计稿 `www/mall.html:166`（该文件已删除）的最后
>   一个 tab 就是它，且 FR-048 要求它指向落地页。它**不是商品分类**（后端没有该
>   categoryId），所以做成 `RouterLink` 而不是可点筛选。页脚那半原本就已接好。
> - **`tel:` 链接只在值真的像号码时才生成**（新增 `src/utils/tel.ts`，企业页与联系页共用）。
>   占位期间是「企业采购热线待填写」，照直拼会得到 `tel:[[…]]` —— 打不通，还等于在
>   提供该服务（FR-054）。这里有**测试专门盯住"占位期不得出现 `tel:` 链接"**。
> - **类型标签没有复用 C6 `.ml-pill`**（更新日志的 新增/优化/修复）：`store.css` 写明
>   C6 的变体是与后端**订单状态**一一对应的映射、「MUST NOT 增删」，所以另立
>   `.cl-kind` 标签，但仍复用 `--ml-radius-pill` 等令牌。
>
> **信息页的胶囊集合加了唯一来源**：FR-060 / FR-062 要求胶囊以 `placeholders.ts`
> 的分类数组为来源（避免种子与页面漂移），但种子里原本只有 `templates.categories`，
> 新闻与招聘没有。已在 `placeholders.ts` 补 `newsCategories` / `jobCategories`
> （并从它们反推 `NewsCategory` / `JobCategory` 类型，杜绝两处各写一份）。
>
> **顺手修掉一个自己引入的坑**：模板页一度把胶囊文案 `bare()` 后再渲染，那会**抹掉
> `data-content-pending` 标记**，让未确认的行业名看起来像已经确认过 —— 已改为
> 原样渲染（带 `[[ ]]`）、只用去包裹后的值做匹配，并加测试盯住。
>
> **空态覆盖**：更新日志 / 新闻 / 招聘三页的「内容为空」分支用 `vi.hoisted` 注入可变
> 种子来测（种子本身非空，不改真实种子）；模板页的空态用搜索无结果触发。
> 六个页面的**错误态**按 §3.12–3.17 只有模板页涉及数据源，其余为纯静态、无错误态可言。

---

## Phase 10: Polish & Cross-Cutting Concerns

- [x] T139 在 `storefront/playwright.config.ts` 增加后端前置检查，并写下运行前置（依赖 `java -jar` 启动的后端、远程 Redis/CynosDB 可达、`pay_channel` 有 mock 行）
- [x] T140 跑通四个故事的 e2e（**T060** 认证、**T075** 购物车、**T088** 下单、**T114** 支付）**并记录墙钟时间**供 SC-002 / SC-003 人工判定。**本任务不再「写一条事后 e2e」**：早先的单一 `purchase-journey.spec.ts` 事后测试已被宪法原则 I 判为违规，已改为四个**按故事测试先行**的 e2e 文件（T060 / T075 / T088 / T114）
- [x] ~~T141 确保 `storefront/e2e/purchase-journey.spec.ts` 不污染共享数据~~ —— **该文件不存在**（它正是 T140 里被宪法原则 I 判为违规、已拆成四个按故事 e2e 的那个事后测试），故本任务已随 T140 消解。**共享数据**的实际处置：所有 e2e 用 `testMobile()`（时间戳派生的可复现手机号）建独立账号，用例之间不撞号；**但会在共享的开发库上累积测试账号/订单/券**，这是接受的行为（不可清理：无管理员权限，且清理会引入删数据的风险）
- [x] T142 **全站 48 处死链全部接线**（FR-043 / SC-005，范围已由「仅交易相关」扩为全站）：设计稿的 `href="#"` 实测为**每页 8 个、全站 48 个**（`grep -c` 数行会得 4，`grep -o` 数实例才是 8）。其中每页 2 个是交易入口（顶栏「购物车」「登录 / 注册」，共 12 个）→ 指向真实功能；每页 6 个是营销/公司入口（模板中心、更新日志、公司介绍、新闻动态、联系我们、加入我们，共 36 个）→ 由 T121–T126 的 6 个信息页承接。**核对方式：逐页确认 48 处均有真实目的地，无一处仍是 `#`**。**`www/` 保持零改动** <br>→ **2026-09-27 修订**：设计稿基线放开后 `www/` 已整体删除（不再是"零改动"，而是不存在）；本任务里"48 处死链各自有真实目的地"的**实质要求仍然成立**，且已由可执行断言守住（`routes.spec.ts` 的静态链接守卫 + 全站无 `href="#"`）
- [x] T143 巡检 `storefront/src/views/` 与 `storefront/src/components/` 的空状态与错误态：购物车为空、无订单、搜索无结果、后端不可用均有明确提示与重试（FR-044 / FR-045 / SC-009）
- [x] T144 移动端适配巡检 `storefront/src/styles/store.css` 与各 `storefront/src/views/*.vue`：375px 宽度走通「浏览 → 登录 → 加购 → 下单 → 支付」，无需横向滚动（SC-008）
- [x] ~~T145 与设计稿逐页比对 `storefront/src/views/{HomeView,MallView,ProductView,SoftwareView,SolutionsView,SupportView}.vue`，确认差异**仅限 FR-046 列明的六处**（SC-012）~~ —— **本任务已作废（2026-09-27）**：FR-046 不再要求逐页还原，SC-012 已删除，设计稿也已从仓库删除，"与设计稿比对"无从执行。**替代它的是可执行断言**：`storefront/src/styles/design-consistency.spec.ts` 对 16 个新页面逐个查 色表/断点/不自带顶栏页脚（50 条断言，全绿）。原任务里"六处偏离各自独立成立"的部分仍有效，且**各自都有渲染层断言**（见 Phase 10 记录的清单）
- [x] T146 核对 **T049** 的两条安全断言（FR-005c / SC-020）确实落进 `storefront/src/views/ProductView.spec.ts` 且被 `pnpm test` 覆盖（**不要重写一遍**）；并人工核对可维护性：后台改商品详情富文本后前台**刷新即见、无需发版**
- [x] T147 [P] 核对 [quickstart.md](./quickstart.md) 的三组检查：§4.2 的 **FR-009 缺席检查**（登录界面确实不存在独立注册表单）、§4.6 的 **FR-052 四项隐私政策要点**、以及**新增的 FR-053/054/055/056 巡检**（6 个信息页可达且版式合规；`/news` 至少 3 条真实文章；`/templates` 未使用未经确认的数量表述；**设计稿自带的未确认声明已接入占位符** —— 6 个第三方公司名、4 个数字声明、客户证言、8 条营销承诺）。**门禁三条（任一不满足即不得发布）**：
  ① **权威门禁** —— `hasContentPending() === false`（实现为 `hasPendingContent()`，`src/data/placeholders.ts` 导出）返回 `false`；
  ② **DOM 门禁** —— DevTools 中 `document.querySelectorAll('[data-content-pending]').length` 为 `0`；
  ③ **覆盖性（可 grep）** —— `storefront/src/views/` 中**不得出现任何业务文案字面量**：`grep -rn "50,000+\|顺丰供应链\|首单立减\|满 499" storefront/src/views/` 须为空。这条专治「视图绕过占位符直接写死文案」——门禁 ① ② 都发现不了这种情况。**注意：早期版本此处写 `grep -c "p(" src/data/placeholders.ts` 归零，按构造不可能通过**（该文件注释里就含字面 `p(`，且 `grep -c` 数行不数调用），已删除
- [x] T148 [P] 在 `storefront/` 下运行 `pnpm ts:check`、`pnpm test`、lint、**以及 `pnpm build`（生产构建必须跑通）** 全部通过
- [x] T149 核对 [quickstart.md](./quickstart.md) §5 的五项"不验证"声明与 SC-018 口径：**"直接调取消接口"不得被当作 SC-018 超时自动取消的验证**；FR-033 只声明"超时释放"半段不验证，"下单扣减"与"取消释放"是已验证的
- [x] T150 执行 [quickstart.md](./quickstart.md) 的完整验证清单并**把实际偏差回填到该文件**（该文件当前是未经执行的规划产物）
- [x] T151 核对四条硬约束未被破坏：`yudao-cloud/` 无改动、`www/` 无改动、未引入 UI 组件库、每个故事的测试任务均前置于其实现任务
  → **2026-09-27 补注**：本任务完成时（同日早些时候）核对属实。**其后约束清单被修订为三条**（见文首），
  其中「`yudao-cloud/` 无改动」已删除；自那以后管理端确实出现了本项目自己的改动
  （加载动画 / 首页看板 / DocAlert 开关），这是**被允许的**，不再算破坏约束。

> **Phase 10 实施记录（2026-09-27）**
>
> 已完成 T139–T151（13/13）。**但其中若干项的"完成"口径需要说清楚，别被勾选误导**：
>
> **真正跑过的**：`pnpm test` **556 例全绿**（41 文件）· `pnpm ts:check` 退出 0 ·
> `pnpm build` 成功 · `pnpm lint` **0 error**（966 warning，全是既有 `vue/*` 格式族，
> 非阻断；本轮新写的页面各带一点）· **`npx playwright test` 22 条全绿**（2.9 分钟）。
>
> **T139**：新增 `storefront/e2e/global-setup.ts` 作为 `globalSetup`。**双向实测过**：
> 把 `E2E_BACKEND_URL` 指向死端口 → 退出 1 并打印带排查步骤的清晰错误；
> 指回 48080 → 正常。
>
> **T140 / T144 / T147** 的墙钟与门禁：SC-002 **1.4s**（要求 ≤120s）· SC-003 **2.9s** ·
> SC-010 **4.4s**，e2e 会打印但**不硬断言上限**（避免慢机器假失败），故仍需人工核对 ——
> 已把这三个值记进 [quickstart.md](./quickstart.md) §7.2。门禁三条：
> ① `pnpm check:placeholders` **退出 1（符合预期）**；③ 见下（口径需修正）。
>
> **T144 抓到并修掉一个真实缺陷**：新增 `e2e/mobile.spec.ts`（375px 无横向滚动，
> 覆盖浏览/下单/领券/信息页/协议页）。它第一次运行就发现 **≤480px 下登录弹层的提交按钮
> 被 `display:none`** —— 根因是弹层被渲染在 `<nav class="header">` **内部**，于是
> design.css 那条 `@media (max-width:480px){ .header .btn-primary { display:none } }`
> （本意是藏顶栏的「免费试用软件」）把**弹层的提交按钮**一起藏了。
> 修法：把 `LoginDialog` 移到 `.header` **外面**（`SiteHeader.vue` 因此成为双根节点），
> 并加了一条钉住根因的单测（「弹层不在 `.header` 之内」）。**这曾使手机上完全无法登录**，
> 也就使 SC-008 要求的「浏览→登录→加购→下单→支付」在移动端无法走通。
>
> **T147 的门禁③ 口径需修正**：原样执行 `grep -rn "50,000+\|…" src/views/` 会命中
> **`.spec.ts` 里的 fixture**（如 `CheckoutView.spec.ts` 用券名 `'首单立减 30'` 做夹具，
> 那是在测"展示后端给的券名"，不是在视图里内联业务文案）。加上 `--include=*.vue`
> 限定到视图组件后为空，**通过**。已在 [quickstart.md](./quickstart.md) §7 记录该口径。
>
> **T145 只完成了可机械判定的部分**：六处偏离**各自都有渲染层断言**
> （FR-004a / FR-002a → `MallView.spec.ts`；FR-008a 不产出「旗舰」「订阅」→ `badge.spec.ts`；
> FR-043 清理写死内容 → `SiteHeader.spec.ts` + `HomeView.spec.ts` + 新增的
> `design-consistency.spec.ts`；FR-008 首页真实商品 → `HomeView.spec.ts`；
> FR-005a SKU 规格项取代参数表 → `ProductView.spec.ts`）。
> **与设计稿的逐像素/配色并排比对仍是人工项**，本轮未做 —— 不假装已覆盖。
>
> **T149 / T150**：§5 五项「不验证」复核后**全部维持**（SC-018 的「超时自动取消」半段
> 仍未验证，重申**调取消接口不算**）；执行结果与偏差已回填到 quickstart 新增的
> **§7 执行记录**，并修正了 §5 里一处过时的任务引用（原指 `T130–T132`）与顶部的
> 「尚未实际执行过」状态说明。
>
> **T151 四条硬约束**（逐条给证据，不含推测）—— 当时是四条，**同日稍后已改为三条**：
> 1. **`yudao-cloud/` 无改动** —— `git status --short yudao-cloud` 为空 ✓
> 2. **`www/` 无改动** —— ⚠️ **该目录未被 git 跟踪**（`?? www/`），diff 证明不了。
>    以两条证据佐证：① 目录内最新 mtime 为 **2026-09-24 11:13**，早于本次会话（09-27）；
>    ② 新增断言 `design-consistency.spec.ts` 断言 `src/styles/design.css` 与
>    `www/css/style.css` **逐字节相同**（含 BOM）—— 一旦有人动 `www/` 或 `design.css`
>    就会转红。**注意：T009 要求的「文件头加来源注释」并未做**（`design.css` 里没有该注释）；
>    考虑到它会使字节一致失效，改用上面这条**可执行的**断言来保障同一件事，并在
>    `tasks.md` 此处如实记录该偏离。
> 3. **未引入 UI 组件库** —— `package.json` 的 dependencies 只有 vue / vue-router / pinia /
>    axios / @vueuse/core / dompurify，无 Element Plus / Vant / Ant Design 等 ✓
>    （`dompurify` 是安全过滤，非 UI 库，其引入已在 Phase 3 记录理由）
> 4. **测试前置于实现** —— 本轮每个改动都是「先写失败测试 → 再实现」，且**逐个记录了红→绿**
>    的过程；其中冷启动、时间格式化两处还做了**反向验证**（临时回退实现 → 断言复现红）。
>
> **T151 顺带查出的问题**：`www/` 与 `storefront/` **都还没提交进 git**（`?? www/`、`?? storefront/`），
> 所以「`www/` 零改动」目前只能靠 mtime 与字节断言佐证，而不是靠 diff。建议把两者纳入版本控制。
> → **已解决**：两者都已入库（`www/` 见 `9df135c3`、`storefront/` 见 `2420036e`），
> 且此后 `www/` 的每次变动都能被 diff 看见 —— 这也正是后来能干净删掉它的前提。
>
> 🔻 **2026-09-27 补充（本条记录之后发生）**：上表第 2 条已**整体失效** ——
> 设计稿基线（FR-046 / SC-012）放开，`www/` 已删除，`design.css` 不再是逐字拷贝，
> 那条字节断言也已移除。**硬约束现为两条**（不引入 UI 组件库、测试先行）。
> 上面的内容保留作当时的事实记录，别再当成现行约束引用。

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 Setup ──► Phase 2 Foundational ──┬──► Phase 3 US1 ──┐
                                          ├──► Phase 4 US2 ──┼──► Phase 6 US4 ──► Phase 7 US5 ──┐
                                          ├──► Phase 5 US3 ──┘                                  │
                                          └──► Phase 8 US7 ──► Phase 9 US6 ◄───────────────────┘
                                                                                    │
                                                          Phase 10 Polish ◄─────────┘
```

- **Phase 1**：无依赖
- **Phase 2**：依赖 Phase 1，**阻塞全部用户故事**
- **Phase 3–5（US1 / US2 / US3）**：依赖 Phase 2，**三者之间可并行**
- **Phase 6（US4）**：依赖 **US1**（商品与 SKU）、**US2**（登录态与地址）、**US3**（购物车 store 与角标）。这是本特性**真实的跨故事依赖**，与 [spec.md](./spec.md) 中 US4「依赖 US1 与 US2」的说明一致
- **Phase 7（US5）**：依赖 US4（需先有订单）
- **Phase 8（US7）**：仅依赖 Phase 2，**可随时并行**（但排在 US6 前）
- **Phase 9（US6）**：依赖 US1–US5 与 US7 的页面都存在 —— 它要核对包含 `EnterpriseView` 在内的全部 16 个 view
- **Phase 10**：依赖全部所需故事完成

### 测试先行的顺序约束（宪法原则 I）

**每个配对内，测试任务 MUST 先完成且先失败；测试与其实现 MUST NOT 同标 `[P]`。**

| 测试任务 | 使其转绿的实现任务 | 同阶段的其他 constituent |
|---|---|---|
| T017 | T020–T022 | T016（`utils/auth.ts` 令牌存取，被 T017④ / T020 / T022 依赖） |
| T023 | T024 | — |
| T025 | T027 | Phase 2 只断言**静态结构**；登录态与角标断言分别在 T072 / T086 追加 |
| T032 | T033 | — |
| T034 | T035 | C1–C8 的**行为**由 T035 交付；样式在 `store.css`（T010） |
| T036 | T039 | T038（`api/category.ts`，独立） |
| T037 | T040 | — |
| T041 | T042 | T080（在 ProductCard / ProductView 接加购，Phase 5） |
| T049 | T050–T054 | **FR-005c / SC-020 的安全断言在此**，先于其实现 T053 |
| T060 | Phase 4 的实现（T063 起） | **e2e，跨故事**：先于 US2 实现，随 US2 完成转绿 |
| T062 | T063 | — |
| T064 | T065–T067 | — |
| T068 | T069 | T070（协议正文） |
| T072 | T073 | 在 T025 的 spec 上追加登录态断言 |
| T075 | T078 | **e2e，跨故事**（依赖 US2 的登录态） |
| T077 | T078 | T081、T107 |
| T079 | T082–T084 | — |
| T086 | T087 | 在 T072 的 spec 上追加角标断言 |
| T088 | Phase 6 的实现（T090 起） | **e2e，跨故事** |
| T091 | T092 | — |
| T093 | T094 | — |
| T098 | T099–T101 | T102–T113 |
| T108 | T109 | T107（下单后清理条目） |
| T114 | Phase 7 的实现（T116 起） | **e2e，跨故事** |
| T116 | T117、T118 | — |
| T120 | T121 | — |
| T140 | —（**不是 TDD 配对**） | 全量 e2e 冒烟与计时记录；各故事的 e2e 断言已由 T060 / T075 / T088 / T114 **以测试先行交付** |

### Parallel Opportunities

- **Phase 1**：T003–T011 可并行
- **Phase 2**：T016、T028–T030 可并行；**T017–T022 必须串行**（同一文件 `service.ts` 递进）；**T023→T024、T025→T027、T032→T033、T034→T035 各自串行**（测试先于实现）
- **US1**：T038、T055–T057 可并行；**T036→T039、T037→T040、T041→T042、T049→T050 各自串行**
- **US2**：T061、T069 可并行；**T062→T063、T064→T065、T068→T069、T072→T073 各自串行**；**T060（e2e）先做，允许长期为红**
- **US3**：T076、T081、T085 可并行；**T077→T078、T079→T082、T086→T087 各自串行**；**T075（e2e）先做**
- **US4**：T089、T090、T092、T095 可并行；**T091→T092、T093→T094、T098→T099、T108→T109 各自串行**；**T088（e2e）先做**
- **US5**：T115、T119 可并行；**T116→T117、T120→T121 各自串行**；**T114（e2e）先做**
- **Phase 10**：只有 **T147（文档）、T148（命令）、T151（硬约束核对）可并行**；**T142–T146 会读改 `storefront/src/views/`，彼此冲突，必须串行**
- **跨故事**：Phase 2 完成后 US1 / US2 / US3 可由不同人**同时开工**；US7 随时可开；**US4 必须等 US1 与 US2 的接口稳定**

---

## Implementation Strategy

### MVP First

1. Phase 1 Setup → 2. Phase 2 Foundational（**阻塞全部故事**）→ 3. Phase 3 US1 → 4. Phase 4 US2
5. **停下验证**：后台改价前台即变；搜索命中全量；下架有提示；用新手机号 `9999` 注册成功

> **关于 MVP 的诚实说明**：US1 与 US2 在 [spec.md](./spec.md) 中同为 P1。**仅交付 US1 时页面上的「登录 / 注册」「加入购物车」入口都是死的**（属 US2/US3），那是"可演示"而非"可用"。因此 **MVP = Phase 1 + 2 + US1 + US2**。

### Incremental Delivery

1. Setup + Foundational → 基础与测试框架就绪
2. **+ US1** → 真实商品可浏览（可演示）
3. **+ US2** → 可注册登录（MVP 可用）
4. **+ US3** → 可加购
5. **+ US4** → 可下单、可查单（交易闭环最小可用形态）
6. **+ US5** → 支付与优惠券完整，不依赖任何商户号
7. **+ US7** → 企业获客页
8. **+ US6** → 全站一致性核对
9. **+ Phase 10** → e2e、死链清理、巡检、执行 quickstart

### Parallel Team Strategy

Phase 2 完成后：

- 开发者 A：US1（工作量最大）
- 开发者 B：US2（登录 + 协议 + 个人中心）
- 开发者 C：US7（独立无依赖）→ 完成后接手 US3

US4 需等 US1 与 US2 的接口稳定后再开工。

---

## Notes

- `[P]` = 不同文件、无未完成依赖。**测试与其实现不得同标 `[P]`**
- **三条硬约束**（见文首；2026-09-27 由四条修订而来）在每个任务里都适用，不逐条重复
- **本特性不含任何后端改动任务** —— 若实现中发现必须改后端，说明此前调研有遗漏，应先回到 [spec.md](./spec.md) 更新范围，而不是直接动手
- 四处最易踩的契约陷阱：**T020**（`tenant-id` 无条件注入）、**T103**（`pointStatus` 必填）、**T022**（`refreshToken` 走 query）、**T037**（`sortField` 必须小驼峰，传大写或 `createTime` 会被判为不合法）
- **第六处契约陷阱（T094 实测补记）**：**`items[].skuId` 也是必填**，且结算 query 里的
  下标方括号**必须百分号编码**（`items%5B0%5D.skuId=..`）—— 后端没配
  `server.tomcat.relaxed-query-chars`，字面 `[` `]` 会被 Tomcat 以 HTML 400 拒收。
  连带结论：**购物车来源也必须先拿到 skuId**（先拉购物车列表，不能从结算响应反查）。
  详见 [contracts/app-api.md](./contracts/app-api.md) §5
- **两处金额陷阱**：后端金额一律是**分**（**T024** 是唯一换算处）；`reconcile()` 必须按**六项**核对，`vipPrice` 不会被置零（**T023**）
- **占位符门禁三条**：**T032**（`placeholders.spec.ts` 断言，CI 权威）、DOM 扫描（人工）、**视图不得内联业务文案**（可 grep）。业务文案 MUST 经 **T035** 的 `PendingText` 渲染
- 每个任务或逻辑任务组完成后提交；每个 Checkpoint 都可停下来独立验证

---

## 前端商城的租户切换（2026-09-27）

**前台从租户 1（芋道源码）切到租户 162（御旺宸发）。** 配置只有一处：
`storefront/.env` 的 `VITE_TENANT_ID=162`（请求头由 `src/config/http/service.ts` 无条件注入）。

⚠️ **租户是数据隔离的** —— 商品/分类/会员/订单/券都带 `tenant_id`。切租户等于换一整套数据。
切换前的实测：162 下 **0 商品 0 分类**，所以配套做了下面两件事。

### ① 给 162 建了最小可用数据

脚本：`storefront/scripts/seed-demo-tenant.py`（**幂等，可重复跑**；重建库后用它恢复）。

| 内容 | 说明 |
|---|---|
| 品牌 1 个 | 「御旺宸发」（用租户自己的名字，未编造） |
| 分类 14 个 | 一级 5 个取**设计稿 `www/mall.html` 的原分类名**（标签耗材/碳带色带/打印设备/扫描与喷码/软件服务）+ 二级 9 个。**商品必须挂二级及以下**（yudao 校验） |
| 商品 5 件 | 名称一律带 **「【示例】」前缀**，一眼可辨是占位数据，不冒充真实商品 |
| 券模板 / 会员 | **未建** —— 见下方"未覆盖" |

**创建机制**：`tenant-id: 1`（鉴权，超管属于租户 1）+ **`visit-tenant-id: 162`**（切数据上下文）
—— 这是 yudao 官方的"超管切租户"通道（后台顶栏那个「请选择租户」下拉用的就是它）。
**直接用 `tenant-id: 162` 会 403「您无权访问该租户的数据」**，必须走 `visit-tenant-id`。

### ② e2e 覆盖回有完整种子数据的租户 1

`playwright.config.ts` 的 `webServer.command` 改为 `set VITE_TENANT_ID=1&& pnpm dev`：
162 只有 5 件示例商品、**没有券模板**，撑不起 22 条用例。Vite 会把 `process.env` 里
`VITE_` 前缀的变量并入 `import.meta.env`，故它**优先于 `.env`**。
**已双向实测**：默认 `pnpm dev` → 前台发 `tenant-id: 162`、商城 5 件；带 `VITE_TENANT_ID=1` → 发 `1`、6 件。
**全量 e2e 22 条全绿**（2.9 分钟）。

### 162 下未覆盖的东西（业务方接手时要补）

| 项 | 现状 | 影响 |
|---|---|---|
| **真实商品** | 只有 5 件「【示例】」 | 替换即可，**前台已可用** |
| **券模板** | 0 个 | `/coupon` 领券中心显示「暂无可领取的优惠券」；结算页无券可选 |
| **会员** | 0 个 | 用户需自行注册（手机号 + 验证码 `9999`） |
| **162 的后台账号** | `admin`/`admin123` **登不进去**（建租户时由创建人自定义） | 不影响管理：**用租户 1 超管登录 → 顶栏「请选择租户」切到御旺宸发** 即可录入（即上面的 `visit-tenant-id` 机制） |
| 联系人手机号 | `1391180639`（**10 位**，像录入时少了一位数） | 建议在后台「租户管理」里更正 |

---

## 遗留问题（2026-09-24 首次记录 / **2026-09-27 重开 US2 修复后更新**）

**关键背景**：本仓库的 Playwright 浏览器此前**从未安装**，`npx playwright install chromium`
之后 e2e 才第一次真正执行。也就是说 **T060（认证）与 T075（购物车）两条 e2e 尽管已被勾选，
却从未运行过**，"先失败"是自动满足的。

### US2 —— **已修复并通过实测**（2026-09-27）

重开 US2 后 `e2e/auth.spec.ts` **5/5 全绿**（实跑，非规划）。四处缺陷与验证方式：

| 缺陷 | 修法 | 先见红的证据 |
|---|---|---|
| **冷启动不恢复会员信息**：`loadMember()` 只在 `syncAfterLogin()` 里被调用，带本地令牌刷新时 `member` 恒为 null，顶栏 `.login` 渲染成**空文本** | store 创建时若已有令牌即 `void loadMember()`（只发起、不 await，不阻塞首屏） | 单测 `user.spec.ts`、组件测 `SiteHeader.spec.ts` **各自先红**（`expected '' to be '张三'`）；e2e FR-013 在**临时回退该行后复现红**（`Expected: "用户402220" Received: ""`）—— 证明 e2e 是真护栏而非"恰好绿" |
| `auth.spec.ts` 对 `.ml-check input` 调 `check()`（视觉隐藏控件，必然超时） | 改用 `helpers.ts` 的 `checkMlCheck(page, '.ml-modal')`（3 处） | e2e 绿 |
| `auth.spec.ts:27` 只认脱敏手机号；新号后端会自动生成昵称，顶栏按 FR-016 优先显示昵称 | 改为断言「不再是登录入口 **且确实有名字**」 | e2e 绿 |
| 「密码已设置」从未出现 —— 定位为**真实产品缺陷**，见下 | 补 scene 3 发码 + 验证码字段 | 单测 + e2e 均绿 |

**第 4 条是本轮最重要的发现：「设置密码」这条路径从未真正可用。**
后端 `AppMemberUserUpdatePasswordReqVO.code` 是**必填**（`@NotEmpty("手机验证码不能为空")`），
`MemberUserServiceImpl#updateUserPassword` 还会 `useSmsCode(scene=3)` 实际核销；
而前端只传了 `password` → 后端一律 400 → `AccountView` 走 catch，`.acct-ok` 永不出现。
即 **T071 / FR-011 / SC-011 实际从未成立**。它躲过全部既有测试的原因是
**组件测试只断言 UI、不断言请求体**——这正是"单元测试会掩盖接线"的又一实例。

修法（后端不可改）：`api/member.ts` 增 `SMS_SCENE_UPDATE_PASSWORD = 3` 与
`updatePassword(password, code)`；`AccountView.vue` 增「手机验证码」字段 + 内联
「获取验证码」（scene 3）。**设计缺口按既有约定先补了规范**：
[design-new-pages.md](./design-new-pages.md) §3.5 增加「设置密码表单字段」与「关键约束」两条。

> ⚠️ **一条后端既有规则（不是缺陷，但会反复咬人）**：`yudao.sms-code.send-frequency: 1m`，
> 且 `SmsCodeServiceImpl.createSmsCode` 查上一次发码记录时**不筛场景**
> （`selectLastByMobile(mobile, null, null)`）。因此**刚用验证码登录过的手机号，
> 一分钟内再要一张改密验证码会被拒**（`短信发送过于频繁`）。前端行为正确
> （把后端原文透出），但 **e2e 必须等过这个窗口**：`auth.spec.ts` 的 SC-011 用例
> 因此改用 `toPass` 重试等待，并把该用例超时放宽到 180s（单次全量 e2e 因此多约 1 分钟）。

### US3 —— e2e 首次真正执行时 1 通过 / 3 失败，**已全部定位并修复**（2026-09-27）

**三处全部是测试缺陷，`CartView` 没有 bug。** 首次运行前已确认与前一轮 US2 的改动无关
（临时回退 `store/user.ts` 那一行后失败完全相同）。定位与修法：

| 用例 | 现象 | 根因 | 修法 |
|---|---|---|---|
| 未登录加购 → 引导登录 → 登录后自动完成（FR-015） | `cart.spec.ts` 对 `.ml-check input` 调 `check()` 超时 | 与 `auth.spec.ts` 同一处坑：该 input 是视觉隐藏控件 | 换 `checkMlCheck(page, '.ml-modal')` |
| 购物车条目可改量与删除，合计随之更新 | 期望 1 行 `.cart-row`，实得 0，页面显示「购物车还是空的」 | **测试没等加购完成就离开页面**：`onAdd` 不是立刻发请求 —— 商城列表接口不返回 `skus`，它要先 `getProductDetail` 拿 skuId 才发 `cart/add`（见 `ProductCard.vue`）。后端日志证实该次运行**只有 `cart/get-detail`、没有 `cart/add`**；`click()` 后紧接 `page.goto('/cart')`（相隔约 150ms）把在途请求掐掉了。同场景的「角标 +1」用例通过，正因为它断言角标时会隐式等待 | `page.goto('/cart')` 前先 `await expect('.cart-chip')` 等到真实件数 |
| 退出再登录，购物车内容不变（FR-022） | 第二次 `smsLogin` 时 `#getCodeBtn` 始终不进入倒计时 | 同一手机号 1 分钟内第二次要码，撞上后端限流（见上文那条规则） | **修在 `helpers.ts`**：`smsLogin` 改为用 `toPass` 重试到发码成功。对**新手机号**首次即成功、不会多花时间，只有真撞上限流才等；该用例超时放宽到 180s |

**四例现全绿**（`cart.spec.ts` 4/4）。**全量 e2e 12/12 绿**：
`auth.spec.ts` 5 + `cart.spec.ts` 4 + `order.spec.ts` 3，用时 2.4 分钟
（其中两条各含约 1 分钟的后端发码限流等待）。

### US4 —— 已实测通过

`e2e/order.spec.ts` **3/3 全绿**（2026-09-27）。US2 改动未造成回归。

### 原「三处冲突」—— **三条均已处置完**（2026-09-27）

原先这三条都是「规格要求 ↔ 设计稿基线打架」，需所有者按例外条款裁决。
**FR-046 / SC-012 放开、`www/` 删除之后冲突性质即消失，随后三条各自落地：**

| # | 原问题 | 现状 |
|---|---|---|
| 1 | `/mall` 在 **375px 下横向溢出 44px**（违 SC-008） | **✅ 已修**。在 `design.css` 的 ≤1100px 媒体查询里给 `.mall-page-main` 补了 `align-items: stretch`（只改这一个 —— 同选择器组里的 `.pd-main` 等窄屏表现本就正常，不顺手动）。`mobile.spec.ts` 那条已从 `test.fail()` **转为正常断言**；并实测 1101–1440px 全程无溢出（导航加项后最紧的那段） |
| 2 | `/support` 的 FAQ 含未经确认的报价承诺 | **✅ 已按业务方指示改写**（2026-09-27）：「不承诺报价，只引导联系相关人员」。第 5 条原为「基础功能免费使用；专业版按年订阅，企业版支持私有化部署与定制对接」→ 改为「版本与报价请联系我们的销售顾问，会根据你的使用场景给建议」，**不再出现任何价格、档位或计费方式**。<br>⚠️ **仍未处理（更轻的一类）**：第 4 条的「支持 Code128/Code39/EAN-13/QR Code/DataMatrix」是**能力声明**，同属待业务方确认之列（FR-056）。业务方本次只对报价部分给了指示，**不擅自替业务方确认其产品支持哪些条码** |
| 3 | 顶栏未加「领券中心」入口 | **✅ 已加**（T125 补做）。「领券中心」进主导航 → `/coupon`；移动端汉堡菜单同源可达，**未登录访客在手机上也能领券**（搁置时记下的那个缺口随之关闭）。另按所有者指示同时补了「我的订单」（T113） |

**留下的教训（已随这次修订消解）**：这三条曾有同一个结构性根因 —— 设计稿的 6 个页面共享顶栏/样式基线且被规定"逐字不可改"，而后来追加的 FR 要求的能力并不都能在不动基线的条件下实现。现在的处置是**放开基线**（按所有者 2026-09-27 的决定），而不是继续逐条走例外条款。若日后又要给某类页面加硬性视觉约束，注意别重蹈覆辙：**约束一旦不能被合理化地满足，就会把每个新需求都变成一次治理流程**。

**环境提示**：后端为 `java -jar yudao-server/target/yudao-server.jar`（**不要加
`--spring.profiles.active=local`**），e2e 需它在 48080 上就绪；本轮已实测可跑通
（冷启动约 63 秒）。`playwright.config.ts` 的 `webServer` 会自动拉起 `pnpm dev`，
`globalSetup` 会先探一次后端端口并给出可操作的报错。
