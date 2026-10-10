# Research: 赋签门面站与 yudao 后台打通

**Feature**: `001-mall-storefront-integration`
**Date**: 2026-09-24
**Input**: [spec.md](./spec.md)、[plan.md](./plan.md)

本文件记录 Technical Context 中每个待定项的**决策、依据与被否决的方案**。所有结论都来自对 `yudao-cloud/` 既有代码与配置的实际查阅，不是推测。

---

## R1. 前端技术栈

**Decision**: 沿用 `yudao-ui-admin-vue3` 的工程骨架 —— Vue `3.5.x` + Vite `8.x` + TypeScript `6.x` + Pinia `3.x` + vue-router `5.x` + axios `1.16`，包管理器 pnpm，Node ≥ 20.19。**不引入 element-plus / UnoCSS 组件库**。

**Rationale**:
- admin-vue3 是仓库里唯一具备完整工程化的前端（有 TS、lock 文件、`engines` 约束、eslint/stylelint/prettier/lint-staged）。照搬它的目录约定（`src/{api,views,store,router,components,utils,config,styles}`、`@/` 别名）能让新站与既有工程认知一致。
- 设计稿是**完全自定义**的官网风格（`www/css/style.css` 自带 reset、品牌色 `#2E7CD6`/`#35A5FF`/`#0A1A38`、字体栈、`::selection`）。Element Plus 的默认设计语言会与之持续冲突，而门面站以展示型布局为主，用不到 Table/Form 这类重组件。
- `@vueuse/core` 足以覆盖轮播、懒加载、媒体查询等需求。

**Alternatives considered**:
- **迁移到 `yudao-ui-mall-uniapp`**：被否决。该工程只有 8 个运行时依赖、无 TS、无 lock 文件、**无 `dev`/`build` script**、无 `node_modules`，`vite.config.js` 依赖外部提供的 `@dcloudio/vite-plugin-uni`，实际上**只能通过 HBuilderX 运行**。它是移动端小程序/H5 工程，与"浏览器端桌面优先的官网商城"定位不符。
- **保留 www 静态页 + 原生 fetch**：被否决（用户在 `/speckit-clarify` 阶段已选择重写）。登录态跨页同步、购物车角标、订单列表、地址管理等交互用原生 DOM 手写会迅速失控。
- **引入 element-plus**：被否决，理由同上。若后续会员中心交互变重，可**仅对那几个路由**做惰性按需引入（保留 `unplugin-vue-components` + `ElementPlusResolver` 配置），不必全站铺开。

---

## R2. 本地后端目标：直连单体 `yudao-server`，不走网关

**Decision**: 本地开发时前端 base URL 指向 **`http://localhost:48080`**，即 `yudao-server`（单体聚合壳）。

**Rationale**:
- `yudao-server/pom.xml` 只依赖各 `-*-server` 模块，**不包含 gateway**；它在 `application-local.yaml` 里监听 `48080`，并通过 `WebProperties`（`appApi = /app-api → **.controller.app.**`）+ `YudaoWebAutoConfiguration` 的 `WebMvcRegistrations` **在同一个进程内同时提供 `/app-api/**` 与 `/admin-api/**`**。
- 它显式排除了 `spring-cloud-starter-openfeign`、并在 `application.yaml` 中关闭了 Nacos 注册与配置中心，即不参与服务发现 —— 网关对它没有意义。
- 旁证：支付种子数据里 `pay_app.order_notify_url` 直接写死 `http://127.0.0.1:48080/app-api/trade/order/update-paid`。
- 既有前端也是这个写法：admin-vue3 的 `.env.local` → `VITE_BASE_URL='http://localhost:48080'`；mall-uniapp 的 `.env` → `SHOPRO_DEV_BASE_URL=http://127.0.0.1:48080`。

**必须注意的互斥**：`yudao-gateway/src/main/resources/application.yaml` **也监听 48080**。两者本地**不能同时启动**（端口冲突）。网关只在微服务部署形态下使用。

**Alternatives considered**: 指向 `yudao-gateway`。被否决 —— 本地单体形态下网关无处可路由，且与单体抢 48080 端口。

---

## R3. 请求头：`tenant-id` 是全局必填

**Decision**: 每个请求都带 `tenant-id`（单租户固定值，默认 `1`）与 `terminal: 20`（H5）；需登录的接口带 `Authorization: Bearer <accessToken>`。

**Rationale**:
- `TenantSecurityWebFilter` 在 `yudao.tenant.enable: true`（本地默认开）且 url 不在 `ignore-urls`（只有 `/jmreport/*`）时，**不传 `tenant-id` 直接返回 `{code: 400, msg: "请求的租户标识未传递"}`**。这个拦截对 `/app-api/**` 全量生效，**包括商品浏览、分类、登录这些免登录接口**。
- 这是个高危陷阱：错误以 **HTTP 200 + body.code=400** 的形式返回，只看 HTTP 状态的拦截器会漏判，表现为"页面拿到空数据但不报错"。因此**响应拦截器必须按 `body.code` 判断**，且成功码是 app 端的 **`0`**（不是管理端的 `200`）。
- `terminal` 缺失不报错（回落 `0 UNKNOWN`），但会影响会员注册来源与下单落库的终端字段，故统一携带。

**Alternatives considered**: 依赖 `visit-tenant-id`。被否决 —— 那是多租户切换场景用的，门面站面向单租户。

---

## R4. 跨域：后端已放开所有来源，**不需要 Vite proxy**

**Decision**: 开发期前端直接从 `http://localhost:5173` 请求 `http://localhost:48080`，**不配置代理**。生产期同域反向代理。

**Rationale**:
- `YudaoWebAutoConfiguration` 注册了 `CorsFilter`，配置为 `allowCredentials(true)` + `addAllowedOriginPattern("*")` + `addAllowedHeader("*")` + `addAllowedMethod("*")`，作用于 `/**`；Spring Security 侧也开了 `.cors()`。因此预检与真实请求都会通过。
- 既然跨域本就不发生，引入代理只是多一层配置与排错成本。

> **自我更正**：在本次规划的早期沟通中我曾提出"开发期用 Vite proxy 规避跨域"，那是**基于尚未查证 CORS 配置的保守假设**。实际查证后该配置已开放全部来源，代理**不必要**。此处以查证结果为准。

**Alternatives considered**: 配 `server.proxy` 转发 `/app-api`。技术上可行（admin-vue3 的 `vite.config.ts` 里有注释掉的示例），但属无必要的复杂度。若后续需要把前端与后端部署在不同域且不开放 CORS，再补。

---

## R5. 登录态存储与令牌刷新

**Decision**: 令牌存浏览器本地存储，key 取 **`ACCESS_TOKEN` / `REFRESH_TOKEN`**；401 触发无感刷新 + 请求队列回放。

**Rationale**:
- 直接复刻 admin-vue3 的 `src/config/axios/service.ts` 三层结构与刷新队列逻辑（`code===401` → 去重刷新 → 批量回放 → 重放当前请求；刷新失败则清登录态并引导重登）。这套实现已在仓库里被验证过两次（admin-vue3 与 mall-uniapp 逻辑几乎逐行对应）。
- 登录响应 `AppAuthLoginRespVO` 提供 `userId`、`accessToken`、`refreshToken`、`expiresTime`，`expiresTime` 可用于提前刷新。

**两个易错点**：
1. **`/member/auth/refresh-token` 的 `refreshToken` 是 query 参数**（`@RequestParam`），不是 body。写成 body 会拿到参数校验失败。
2. 两套既有前端的存储 key **不一致**（admin 用 `ACCESS_TOKEN`/`REFRESH_TOKEN`，uniapp 用 `token`/`refresh-token`）。**必须选定一套并固定**，混用会导致"登录了但请求不带令牌"。

**Alternatives considered**: 照抄 uniapp 的 key 命名。被否决 —— admin 的命名更规范，且本项目骨架来自 admin。

---

## R6. 短信验证码的本地联调

**Decision**: 本地联调**统一使用固定验证码 `9999`**，发送时传 `scene: 1`（`MEMBER_LOGIN`）。不依赖真实短信通道。

**Rationale**:
- `yudao-server/src/main/resources/application.yaml` 里 `yudao.sms-code.begin-code: 9999` / `end-code: 9999`，生成逻辑在 `[9999, 10000)` 区间取随机数 —— **结果恒为 `9999`**。
- 验证码**先落库再发送**（`system_sms_code` 表），即使发送失败也能从库里取；种子数据里渠道是 `DEBUG_DING_TALK` 且 access token 是占位符，即**本来就发不出真短信**。
- 另有更省事的旁路：本地 `yudao.security.mock-enable: true`，带 `Authorization: Bearer test1` 即可冒充 `userId=1` 的会员，**可跳过登录直接调需登录接口**。这条用于快速验证购物车/下单，但不用于验收登录流程本身。

**Alternatives considered**: 配置真实短信渠道。被否决 —— 沙箱不可得，且无助于功能验证。

---

## R7. 模拟支付：提交即成功，无独立"标记支付成功"接口

**Decision**: 下单拿到 `payOrderId` 后调 `POST /pay/order/submit`（`channelCode: "mock"`）即完成支付；随后**轮询订单详情**确认状态已变为「待发货」。前端**不得**自行把订单标记为已支付。

**Rationale**:
- `MockPayClient.doUnifiedOrder` 直接返回成功；`PayOrderServiceImpl` 在统一下单成功后立刻 `notifyOrder` → `notifyOrderSuccess` 建通知任务 → `PayNotifyServiceImpl` 在**事务提交后异步执行**（不依赖 xxl-job，本地 `xxl.job.enabled: false` 也生效）→ 回调 `pay_app.order_notify_url` → `/app-api/trade/order/update-paid` → 交易订单进入待发货。
- 仓库中**不存在** `PayMockController` 之类的"手动标记成功"接口，也不需要。
- 前置条件：`pay_channel` 需有 `code='mock'` 且 `app_id=1` 的记录（种子 `sql/mysql/pay-2026-04-18.sql` 已含，启用状态）。缺行会导致渠道不可用而提交失败 —— 这是 `quickstart.md` 里必须写明的排查项。

**Alternatives considered**: 由前端直接改订单状态。被否决 —— 违反 FR-039（状态权威在后端），且会掩盖回调链路本身的问题。

---

## R8. 样式复用策略

**Decision**: ~~`www/css/style.css` 原样保留为视觉基线~~ → **2026-09-27 修订**：该约束已放开（FR-046 不再要求逐页还原，`www/` 已删除）。**结论里仍然成立的部分**：Vue 模板沿用设计稿的 class 名（`design.css` 就是那份样式表的搬入，现为本项目可演进的基线）、新增页面另建 `store.css` 并沿用同一套配色与字号 —— 这样做是为了**站内一致**，不再是因为「不能改」。

**Rationale**:
- 该文件 1005 行、纯 class 选择器、**无 CSS 变量**、6 个媒体查询（1100/768/480）。只要模板沿用 `.header`、`.p-card`、`.p-btn`、`.topbar` 等类名，视觉还原几乎零成本，直接服务于 FR-046 与 SC-012。
- 设计稿只覆盖 6 个页面，而交付需要 **23 条路由**（6 + 11 功能页 + 6 信息页；登录为弹层不占路由）。**其中 17 条没有设计稿** —— 该缺口不再留给实现者临场发挥：已由 [design-new-pages.md](./design-new-pages.md) 给出令牌、8 个新组件契约与逐页结构，并由 T129 客观核对。

**Alternatives considered**: 把 CSS 迁移成 CSS 变量 + 组件 scoped 样式。被否决 —— 那是在重做设计系统，超出"还原设计稿"的范围，且会让 FR-046 的可核对性变差。

---

## R9. 测试策略（已定案 —— 原依据曾被推翻，见文末更正与结论）

> **2026-09-24 修订说明**：本决策原先的两条依据中，**第 ② 条是错的**，已由实测推翻。
> 下文保留原文以留痕，并在其后写明更正与**最终结论**（2026-09-24 已按方案 A 定案）。

**原 Decision**（保留留痕）：引入 **Vitest** 覆盖纯逻辑；端到端流程以 `quickstart.md` 的**文档化手工验证路径**为准，**不引入 Playwright/Cypress**。

**原 Rationale**:
- 纯逻辑正是本特性最容易出错、也最适合自动化的部分，且与多条成功标准直接对应：金额核对（SC-007）、订单状态映射（SC-017）、角标派生（SC-021）、金额分→元换算、响应适配器、券可用性展示。
- 端到端自动化被两个现实约束挡住：① **两套既有前端完全没有测试设施**（无 vitest/jest/cypress/playwright 依赖、无 test script、无任何 `*.spec.*`），引入 e2e 框架等于从零建一套并自己维护；② ~~**后端只能从 IDE 启动**（项目既有约束），CI 或本地脚本无法无头拉起 48080，e2e 会成为"能写不能跑"的资产。~~
- 因此诚实的做法是：把机器能可靠验证的部分自动化，把流程验证写成可重复执行的手工清单。这是一个**权衡，不是遗漏** —— ~~若后续后端具备无头启动能力，再补 e2e。~~

### 更正（2026-09-24）

**依据 ② 是错的。** 后端**可以由 `java -jar` 无头启动**：

```bash
cd yudao-cloud
mvn -B -DskipTests package
java -jar yudao-server/target/yudao-server.jar     # ⛔ 不要加 --spring.profiles.active=local
```

- 实测：`yudao-server/target/yudao-server.jar`（246 MB 可执行 fat jar，`Main-Class: JarLauncher`、`Start-Class: cn.iocoder.yudao.server.YudaoServerApplication`）能启动，Tomcat 绑定 48080，约 70 秒完成，能成功响应真实接口，无 ERROR 级日志。
- 该能力自 **2026-09-23** 就存在（给 22 个 `yudao-module-*-server` 的 `repackage` 加 `<classifier>boot</classifier>` 修复了 `No MyBatis mapper was found` / 找不到 `ApiErrorLogCommonApi`；`yudao-server` 与 `yudao-gateway` 不能加该 classifier）。磁盘上还留有当时的运行日志。
- 「跑不起来」这个错误结论的**真实成因**是：`yudao-server` 的 `application.yaml` 里 `spring.profiles.active` 已是 `local,my`，而命令行再传 `--spring.profiles.active=local` 会**覆盖**它、丢掉 `my`，于是回落到 `jdbc:mysql://127.0.0.1:3306` 并报 `Connection refused`。**是调用方式的问题，不是能力缺失。**
- 该错误结论此前被我以"项目既有约束"的口吻写进了本文件、`plan.md`、`quickstart.md`、`.specify/memory/constitution.md`，均无出处。已逐处更正。

**因此本决策的现状**：
- 依据 ①（仓库无任何测试设施）**仍然成立且仍然要付成本** —— 引入 Vitest 或 Playwright 都要从零搭。但它只是"成本高"，不是"做不到"，**不足以单独支撑"不做端到端自动化"这个结论**。
- 端到端现在**在原理上可自动化**（后端能无头启动）。剩余顾虑是工程性的：启动依赖远程 Redis 与 CynosDB 可达、CI 需管理这两个外部依赖、首次 jar 启动会改远程 Flowable 表结构。
- **结论已于 2026-09-24 决定（取代上文"待重新决定"）**：采用方案 A ——
  ① 单元层（金额/状态/角标/适配）与组件层（协议门禁、金额明细、角标、登录态、失效条目）**按宪法原则 I 测试先行**，
  测试先写、先失败、再实现，配对关系见 [tasks.md](./tasks.md) 的依赖表；
  ② 端到端只做**一条冒烟**（注册→加购→下单→模拟支付，覆盖 SC-010），且明确它是**事后验收/回归护栏、不是 TDD 证据**， ← **2026-10-08：SC-010 已删除**（生产改为接入真实支付宝渠道，见 spec.md FR-038 / 决策表 Q3）。此处「模拟支付」改指**测试租户的 mock 渠道** —— e2e 显式点选 `[data-channel="mock"]`，生产链路改为人工验收。
  该项经辩护后记入 [plan.md](./plan.md) 的 Complexity Tracking；
  ③ **不铺满 21 条 SC 的 e2e** —— 收益低于维护与外部依赖成本；哪些不验证逐条写在 [quickstart.md](./quickstart.md) §5。
  用户于 2026-09-24 在 A/B/C 三案中选定 A。

**Alternatives considered**（沿用，但评价更新）:
- Playwright 覆盖 21 条 SC：~~被否决，理由是上面的约束②~~ → **不再因约束②被否决**。是否采纳取决于对上述工程性成本的判断。
- 完全不写测试：被否决，理由是本特性有大量可纯函数化的逻辑（金额、状态、角标），放弃自动化等于把 SC-007/017/021 交给人工抽查。

---

## R10. 其他已查证并需固化的契约细节

| 事项 | 结论 |
|---|---|
| 金额单位 | 后端全为 `Integer` **分**；换算与格式化只在**一处**实现（详见 [data-model.md](./data-model.md)） |
| 商品列表 vs 详情字段不同 | **只有 `description` 与 `skus` 是详情独有**；`introduction` 与 `sliderPicUrls` **列表接口同样返回**（`AppProductSpuRespVO:19,28`）。反向的例外：`deliveryTypes` 只在**列表** VO 上。⚠️ 本条早期版本写反了（曾称列表不含 introduction/sliderPicUrls），已在 data-model/tasks 更正后同步至此 |
| 列表销量已合并 | `page` 接口返回前会把 `salesCount + virtualSalesCount` 合并写入 `salesCount`，前端无需再算 |
| 商品下架 | `/get-detail` 对已下架商品抛业务异常（非空响应），前端据此渲染「已下架」而非空白（FR-007） |
| 结算数组参数 | `/trade/order/settlement` 的 `items[]` 需手工拼 query（`items[0].skuId=..&items[0].count=..`），既有 C 端工程就是这么做的 |
| 购物车有效性 | 后端已返回 `validList` / `invalidList`，前端**不得自行判断**下架与售罄（FR-023） |
| 购物车勾选 | 选中状态后端持久化（`update-selected`），不是纯前端状态 |
| 地址地区 | `areaId` **必填**，由 `/system/area/tree` 三级联动选出；不得在前端内置行政区划 |
| `pointStatus` | 结算请求中标记 `@NotNull`，**必须传**；本期固定 `false`，明细不出现积分行 |
| 券可用性 | 只由结算响应的 `coupons[].match` / `mismatchReason` 判定，前端不得自算 |
| 领券中心 vs 我的券 | `coupon-template/*`（可领模板，`@PermitAll`）与 `coupon/*`（我的券，需登录）是两个接口族 |

---

## R11. 头像上传怎么接（前台此前没有任何上传能力）

**Decision**: 用 yudao 现成的 `POST /app-api/infra/file/upload`（**multipart**，字段名 `file`），它**直接返回文件 URL 字符串**，把这个 URL 写进 `avatar` 即可。前台自写一个最小上传控件（原生 `<input type="file">` + 图片预览 + 上传中/失败提示），**不引 UI 组件库、不引上传库**。

**Rationale**:
- 该接口是 C 端专用的 `/app-api` 路径，符合宪法「C 端只走 app-api」；返回的正是 `PUT /member/user/update` 中 `avatar` 需要的值 —— 而那个字段带 `@URL` 校验，所以**必须是可访问的 URL**，不能是 base64 或本地路径。
- 走它**不需要新建后端能力**（宪法原则 III）。文件存储器配置在 `infra_file_config`（线上 master 是阿里云 OSS，见 [[server-deploy]]），前台不必知道存到哪。
- 这也顺带铺好了路：以后要补「退款凭证图片」时，同一套上传控件可复用（本期仍不做，见 spec 的 FR-041e 说明）。

**Alternatives considered**:
- **预签名直传（模式二，`presigned-url` + `create`）**：多两次请求、前端要处理 OSS 直传与回调登记，复杂度与收益不成比例（头像只有一张、体积小）。
- **头像转 base64 直接塞进 `avatar`**：会被 `@URL` 校验拒掉，而且把二进制塞进业务表。
- **不做头像、只改昵称**：所有者已明确要"四项都给改"，故不采用。

---

## R12. 「编辑资料」的形态，与个人中心的入口分组

**Decision**:
- **编辑资料做成弹层**（新建 `ProfileEditDialog.vue`），沿用 `AddressFormDialog.vue` 的既有范式：`MlModal` + `MlField` + 本地 `formError` + `watch(open)` 重置。
- **入口分三组**（FR-011e）。归属如下：

| 分组 | 入口 |
|---|---|
| **账户资料** | 个人中心（资料总览）· 收货地址 |
| **我的交易** | 我的订单 · **我的售后/退款**（新） |
| **我的权益** | 我的券 · 领券中心 · **积分与等级**（新） |

**Rationale**:
- 弹层而非独立页：本项目已有的弹层表单范式就是 `AddressFormDialog`（结算页与地址页共用），沿用比新造一致；且改资料是"轻动作"，弹层省掉"改完还要返回"的导航成本。**独立页会多一条路由，且回来要重新找入口。**
- 分组解决的是"5 项平铺看不出结构"这个原始诉求（US8 的背景）。归属按**用户心智**而非后端模块：地址属于"账户资料"（和姓名手机号同类），售后属于"我的交易"（它是订单的延伸），积分/券属于"我的权益"。

**Alternatives considered**:
- **独立页面编辑资料**：多一条路由 + 返回成本，且与既有范式不一致。
- **不分组、只调整顺序**：没解决"看不出结构"，等于没整理。
- **把「售后」放进"账户资料"**：语义不对 —— 它是交易的一部分（其 `orderNo`/`orderItemId` 就来自订单）。

---

## R13. 两个新列表的取数与判定口径

**Decision**:
- **我的售后**走 `GET /app-api/trade/after-sale/page`。它的返回体（`AppAfterSaleRespVO`）字段很全：`no` 售后单号、`status` **精确的售后单状态**、`way` 售后方式、`applyReason`、`refundPrice`、`createTime`、`spuName`/`picUrl`/`properties`/`count` 商品快照、`auditReason`。**撤销入口就按这个精确 `status` 判断**（∈ `{10 申请中, 20 卖家同意, 30 待卖家收货}`）。
- **积分明细**走 `GET /app-api/member/point/record/page`，展示 `createTime` 时间 / `title` 事由 / `point` 变动值（正负即增减）。**不加"增加/减少"筛选**。

**Rationale**:
- 列表 VO 自带**精确**的售后单状态 —— 所以"我的售后"页能**准确**判断哪些可撤销；这比订单详情页只能拿到订单项上那个粗粒度的「售后中」强得多（后者分不出"商家已收货待退款"这种不可撤销的状态，只能靠后端拒绝后透文案）。这是把撤销入口同时放进列表页的主要理由，也解释了为什么两个页面的判定精度不同。
- 积分记录字段刚好覆盖"时间/事由/变动值"；后端虽支持 `addStatus` 筛选，但记录条数少时多一排筛选器只是噪音。

**Alternatives considered**:
- **在列表里对每条调 `after-sale/get` 查状态**：N+1 请求，而 `/page` 已经返回了状态。
- **积分明细加增减筛选**：YAGNI（后端支持，需要时再加，前端加一排 pill 即可）。
- **售后列表只读、不给撤销**：所有者已选"列表 + 可撤销"（澄清 Q2）。
