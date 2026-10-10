---

description: "Task list for 商品详情、评价（展示与撰写）与商城页 banner"
---

# Tasks: 商品详情、评价（展示与撰写）与商城页 banner

**Input**: Design documents from `/specs/002-product-detail-reviews-banner/`
**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 本项目的宪法 **原则 I（测试先行）是不可协商的** —— 每个故事内的测试任务 MUST 排在实现任务之前，
且 MUST 先失败。本清单按此组织。凡确实无法先写测试的（如 SQL 迁移脚本、生产库执行），在该任务上注明理由。

**Organization**: 按用户故事分组，每个故事可独立实现、独立验证、独立交付。

## Format: `[ID] [P?] [Story] Description （FR/SC 引用）`

- **[P]**: 可并行（不同文件、无未完成的依赖）
- **[Story]**: 所属用户故事（`[US9]`~`[US12]`，沿用 spec.md 的编号）
- 每条任务都写明确切的文件路径，**并挂上它承接的需求编号**

> **任务编号从 T198 起**（001 止于 T197）—— 与本仓库 FR / SC / US 同理，`T` 也是**全仓唯一**命名空间，
> 代码注释与文档里会直接引用（例如 001 的「见 tasks.md T134」）。
>
> **需求引用（`（FR-0xx）`）不是装饰**：本仓库的代码注释会直接引 FR 号
> （`ProductView.vue` 里就写着 `FR-005a` / `FR-005c` / `SC-020`）。任务清单是回溯链条的起点，
> 断了之后"这行代码为什么在这儿"就查不回去了。

---

## Phase 1: Setup

**Purpose**: 开工前的基线与一条**必须先钉死的事实**

- [x] T198 跑一遍既有全量测试并记录基线（`cd storefront && npx vitest run`，预期 859 条全绿）。
      这是后面红绿对比的起点 —— **基线不绿就不要开始**，否则分不清失败是新写的还是本来就有的
      ✅ **实测基线：859 条 / 63 文件全绿**
- [x] T199 [P] 把**后端评价列表没有排序**这一事实回填到 `specs/002-product-detail-reviews-banner/contracts/app-api.md` §2.1。
      ✅ 顺带把 `research.md` R4 也从"未确认"改成"已证实" —— 上一轮改了三份文档、漏了这一份，两处说法相反
      **已证实（2026-10-10 查代码）**：`AppCommentPageReqVO extends PageParam`（**不是** `SortablePageParam`），
      而 `PageParam` 不带默认排序；`BaseMapperX.selectPage` 的 ORDER BY 完全来自 `sortingFields`，
      为空则 SQL 里**没有 ORDER BY**。因此返回顺序**不确定**，MySQL 实际多半按主键升序（**最早的在前**）。
      ⚠️ **该事实只影响"展示哪几条"，不影响本次能否调用接口** —— 详见 T243（FR-070）

---

## Phase 2: Foundational（无阻塞前置）

**本阶段为空，这是设计的结果、不是遗漏。**

四个用户故事之间**没有共享的新基础设施**可选：本期不新增任何依赖、不新建公共层，
一切都复用既有件（`MlModal` / `BaseCarousel` / `api/upload.ts` / `formatDateTime` / `design.css` 里的 `.mall-banner`）。
每个故事要用的类型与接口封装都只被它自己消费，因此各自落在故事阶段内，不放这里人为制造耦合。

---

## Phase 3: US9 —— 商品详情真的能看到详情 (P1) 🎯 MVP

**Goal**: 让"内容本来就在、只是没显示出来"的存量商品详情立刻可见。

**Independent Test**: 找一件**纯图片详情**的商品，详情页能看到内容且无占位文案；
再找一件**详情为空**的，区块不出现且无空白框。（SC-025）

### 测试（先写、先失败）

- [x] T200 [P] [US9] 在 `storefront/src/utils/sanitize.spec.ts` 增 `hasVisibleContent()` 的**穷举**用例：
      纯 `<img>` 算有内容、空壳标签（`<p></p>`/`<div></div>`/`<br>`）不算、被清洗规则整体移除后为空不算、
      纯文本算、图文混排算、`<table>` 内嵌图片算。**先确认这些用例失败**（FR-064）
      ✅ 先失败：8 条全红（函数不存在）
- [x] T201 [P] [US9] 在 `storefront/src/views/ProductView.spec.ts` 增两条：
      **纯图片详情要渲染出 `.pd-desc`**、真为空时**不渲染**且页面上**没有**占位文案。**先确认失败**
      （现有用例只覆盖了"空/纯空白不渲染"，纯图片这条从来没被断言过 —— 这正是漏掉它的原因）（FR-063 / FR-065 / FR-066）
      ✅ 先失败：**纯图片那条真的红了** —— 就是本缺陷的复现

### 实现

- [x] T202 [US9] 在 `storefront/src/utils/sanitize.ts` 新增 `hasVisibleContent(html): boolean`
      （存在 `<img>` 或 `textContent.trim()` 非空即算有内容），使 T200 转绿（FR-064）
- [x] T203 [US9] 改 `storefront/src/views/ProductView.vue` 的 `safeDescription`：清洗后改用 `hasVisibleContent`
      判定，使 T201 转绿。**不改 `sanitizeRichText` 本身**（它是安全边界，且其 11 条既有测试都断言返回 HTML 字符串）（FR-063 / FR-065 / FR-066）
- [x] T204 [US9] ~~给 `.pd-desc` 内的图片限宽~~ —— **实查发现这条是多余的**：
      `storefront/src/styles/store.css:580` 早就有 `.pd-desc img { max-width: 100%; height: auto; }`。
      计划里"这一步不能省"的假设**是错的**（当时只看了组件内的 `<style scoped>`，没看全局样式表）。
      **不加重复 CSS**（原则 IV）（FR-063）

---

## Phase 4: US10 —— 买之前能看别人怎么说 (P2)

**Goal**: 详情页能看到该商品的评价；有一个页面能看全部。

**Independent Test**: 有可见评价的商品能看评价（含商家回复）；没有的商品显示明确空态；
评价接口挂掉时**详情页与购买流程不受影响**。（SC-026）

### 测试（先写、先失败）

- [x] T205 [P] [US10] 新建 `storefront/src/api/comment.spec.ts`：路径 `/product/comment/page`、
      参数含 `spuId` 与 `type: 0`（**本期一律传 0**）、分页参数。**先失败**（FR-067）
- [x] T206 [P] [US10] 新建 `storefront/src/components/CommentList.spec.ts`：
      字段渲染（昵称/评分/内容/时间/图片/**商家回复显示在同一条之内**）、空态**明确说明**、
      条数超限时给"查看全部"入口、**超过 120 字符的评价默认折叠且可展开**。
      **时间字段断言可读格式**（epoch 毫秒经 `formatDateTime`，**不得**直接插值出那串数字）。**先失败**
      （FR-067 / FR-068 / FR-069 / FR-071 / FR-072）
- [x] T207 [P] [US10] 新建 `storefront/src/views/ProductCommentListView.spec.ts`：全量列表 + 分页 + 空态，
      且**该页不折叠**（与详情页相反）。**先失败**（FR-071 / FR-072）
- [x] T208 [P] [US10] 在 `storefront/src/views/ProductView.spec.ts` 增：有评价时渲染评价区；
      **评价接口失败时详情页仍然正常**（只降级该区块，不整页报错）。**先失败**（FR-067 / FR-069）

### 实现

- [x] T209 [US10] 新建 `storefront/src/types/comment.ts`（只建模要展示的字段，见 data-model §2.1）（FR-068）
- [x] T210 [US10] 新建 `storefront/src/api/comment.ts`（依赖 T205）（FR-067）
- [x] T211 [US10] 新建 `storefront/src/components/base/MlRate.vue`：五星评分，支持**只读**与**可交互**两种模式
      （US11 的表单要复用）。全仓没有评分组件，这是新件（FR-068 展示 / FR-074 交互）
- [x] T212 [US10] 新建 `storefront/src/components/CommentList.vue`（详情页与"全部评价"页共用）。
      折叠行为**由参数控制**：「全部评价」页传"不折叠"（依赖 T206/T209/T211）（FR-067 / FR-068 / FR-069 / FR-071 / FR-072）
- [x] T213 [US10] 新建 `storefront/src/views/ProductCommentListView.vue` + 在 `storefront/src/router/index.ts`
      加路由 `/product/:id/comments`（依赖 T207/T212）（FR-071）
- [x] T214 [US10] 改 `storefront/src/views/ProductView.vue` 接入评价区，**异步加载**：
      不阻塞详情渲染，失败只让该区块降级（依赖 T208/T212）（FR-067 / FR-069）
- [x] T215 [US10] 在 `storefront/src/styles/design-consistency.spec.ts` 的 `NEW_PAGE_VIEWS` **登记**
      `'ProductCommentListView'`。⚠️ 那份清单是**显式的、不会自动收录** —— 不登记新页面就不受色板与断点约束（FR-086）

---

## Phase 5: US11 —— 买完能说两句 (P3)

**Goal**: 已完成且未评价的订单能走完评价表单并提交成功。

**Independent Test**: 一笔已完成未评价的订单能从订单页走到表单并提交；未完成的订单没有入口；
提交成功后提示里写明"审核通过后展示"。（SC-027 / SC-028）

### 测试（先写、先失败）

- [x] T216 [P] [US11] 新建 `storefront/src/utils/comment.spec.ts`：校验纯函数穷举 ——
      评分 1~5（**0 与 6 都拒**）、内容去空白后非空、内容 **>1024** 拒、图片 **>9** 张拒。**先失败**（FR-074 / FR-075）
- [x] T217 [P] [US11] 新建 `storefront/src/components/MultiImageUploader.spec.ts`：
      选中多张逐个上传、超过上限**拦在提交前**、**上传失败不丢已填内容**且可重试。**先失败**（FR-076）
- [x] T218 [P] [US11] 新建 `storefront/src/components/CommentCreateDialog.spec.ts`：
      **断言请求体**（字段名与取值、`anonymous`、`picUrls` 数组）、必填为空**不发请求**、
      图片超 9 张**不发请求**、
      **后端拒绝时页面显示的文本与后端返回的那句一致**（不得被前端通用文案替换）。
      ⚠️ 本项目吃过"单测只断言 UI 不断言请求体"的亏 —— 写操作必须钉住请求体。**先失败**
      （FR-074 / FR-075 / FR-076 / FR-077 / FR-079）
- [x] T219 [P] [US11] 在 `storefront/src/views/OrderDetailView.spec.ts` 增：入口在
      「订单已完成 **且** 订单级 `commentStatus` 为 false **且** 该订单项 `commentStatus` 为 false」时出现；
      三者任一不满足都**不出现**。**先失败**（FR-073）
- [x] T220 [P] [US11] 在 `storefront/src/views/OrderListView.spec.ts` 增：已完成且订单未评价时出现「去评价」，
      否则不出现。**先失败**（FR-073）

### 实现

- [x] T221 [US11] 在 `storefront/src/types/order.ts` 给订单与订单项**补 `commentStatus`**。
      后端**已经返回**这两个字段，是前端没建模 —— 补类型即可，无需接口改动（见 data-model §3）（FR-073）
- [x] T222 [US11] 新建 `storefront/src/utils/comment.ts` 校验纯函数（依赖 T216）（FR-074 / FR-075）
- [x] T223 [US11] 新建 `storefront/src/components/MultiImageUploader.vue`，内部复用既有
      `storefront/src/api/upload.ts` 的 `uploadFile()`。**不改 `AvatarUploader`**（它只支持单张，且个人中心在用）（FR-076）
- [x] T224 [US11] 新建 `storefront/src/api/tradeComment.ts` 提交评价
      （读写分文件：读走 product 模块、写走 trade 模块，不是一个域）（FR-074）
- [x] T225 [US11] 新建 `storefront/src/components/CommentCreateDialog.vue`
      （仿 `ProfileEditDialog`；用 `MlModal` + `MlRate` + `MultiImageUploader`）（依赖 T218/T222/T223）（FR-074 / FR-075 / FR-079）
- [x] T226 [US11] 改 `storefront/src/views/OrderDetailView.vue` 接**按订单项**的入口，
      放在既有的 `.od-sale-actions` 容器内。⚠️ `.od-item` 是四列 grid，**不要把按钮挂成 grid 的直接子级**（既有注释已警告）（FR-073）
- [x] T227 [US11] 改 `storefront/src/views/OrderListView.vue` 接**订单级**的「去评价」（跳订单详情）（FR-073）
- [x] T228 [US11] 提交成功后：关弹层 + 重拉订单 + **提示"评价已提交，审核通过后展示"**。
      ⚠️ **这句话不能省**：评价默认不可见（Q8），不说清用户会重复提交，
      第二次会被后端以"订单已评价"拒绝（FR-078）

---

## Phase 6: US12 —— 商城页有 banner (P4)

**Goal**: 运营在后台配的横幅能出现在商城页；没配时页面不留空白。

**Independent Test**: 后台新建一条位置为「商城页」的 banner → 商城页顶部出现且可跳转；
把它删掉/没配 → 顶部无空白、商品列表从顶部正常开始。（SC-029）

### 测试（先写、先失败）

- [x] T229 [P] [US12] 在 `yudao-cloud/.../yudao-module-promotion-api/src/test/.../BannerPositionEnumTest.java`
      增用例：新增取值在 `ARRAYS` 里（`@InEnum` 校验全靠它）。**先失败**（FR-080）
- [x] T230 [P] [US12] 新建 `storefront/src/api/banner.spec.ts`：路径 `/promotion/banner/list`、
      **`position` 必传**。**先失败**（FR-080）
- [x] T231 [P] [US12] 新建 `storefront/src/components/MallBanner.spec.ts`：
      ≥2 条走轮播、**1 条不出现**圆点与箭头、**0 条整块不渲染**、
      `url` 为空时点击**不跳转**、图片加载失败不撑破布局。**先失败**（FR-081 / FR-082 / FR-083）

### 实现

- [x] T232 [US12] 在 `yudao-cloud/.../enums/banner/BannerPositionEnum.java` 新增 `MALL_POSITION(6, "商城页")`，
      使 T229 转绿。⚠️ 这是本期**唯一**的服务端代码改动，理由见 plan.md 的 Complexity Tracking（FR-080）
- [x] T233 [US12] 新增**本项目自建**的迁移脚本 `yudao-cloud/sql/mysql/002-banner-position-mall.sql`：
      向 `system_dict_data` 插一行 `dict_type = 'promotion_banner_position'`、值 `6`、标签 `商城页`。
      ⚠️ 该目录下其它 SQL 是**从外部下载的官方资产** —— 本文件不是，**脚本头必须注明**，
      免得将来重建库时把它当官方资产一并重导或误删。
      （**无对应测试任务** —— 数据迁移写不出可先失败的单测；靠 T234 执行后人工核对）（FR-080）
      ⚠️ **实现时发现的坑**：`.gitignore:23` 把 `yudao-cloud/sql/mysql/*.sql` **整个忽略了**
      （那是给"从知识星球下载的付费模块资产"用的规则）。所以按本任务原样放进去，
      脚本会**静默不入库** —— 别人部署时漏跑这一步，表现是"运营选不到某个选项"，很难倒推回来。
      已在 `.gitignore` 加例外 `!yudao-cloud/sql/mysql/0*.sql`（本项目自建脚本一律 `0xx-` 前缀）
      并写明理由；付费资产仍被忽略（已核验两边都对）
- [x] T234 [US12] **在目标库执行 T233 的 INSERT**。⚠️ **漏了这一步运营在后台选不到「商城页」**，
      后面手工验证全过不了。管理端前端**零改动**（位置是字典渲染的，加完自动出现新选项）（FR-080）
      ✅ **已在线上库执行**（所有者授权后）：执行前 5 行（1~5），执行后 6 行 ——
      新增 `id=1409, label=商城页, value=6, status=0`。**幂等已实测**：再跑一次影响 0 行、
      `value='6'` 仍只有 1 行。
      ⚠️ 两点记录：① 首跑报 `You have an error in your SQL syntax ... near 'dual'` —— 派生表别名
      用了 `dual`（MySQL 保留字），已改成 `one_row`；**当时事务未提交，线上没留半截数据**。
      ② 管理端前端可能缓存了字典，运营**刷新一次后台**才能在下拉里看到新选项
- [x] T235 [US12] 新建 `storefront/src/types/banner.ts`（FR-080）
- [x] T236 [US12] 新建 `storefront/src/api/banner.ts`（依赖 T230）（FR-080）
- [x] T237 [US12] 新建 `storefront/src/components/MallBanner.vue`：给 `BaseCarousel` 传 `class="mall-banner"`；
      ⚠️ **slide 的 class 必须带 `car-slide`** —— `BaseCarousel` 不会替你加，而 `flex: 0 0 100%` 只在 `.car-slide` 上。
      **不套** `.mbn-*`（那是设计稿遗留的**文字型**横幅，后端没有描述字段）（依赖 T231/T235/T236）（FR-081 / FR-083）
      ✅ 另有一处实现取舍：**单条时不走 `BaseCarousel`**（它无论如何都渲染圆点与箭头），
      用同样 class 的一段静态标记代替 —— 宁可重复 6 行，也不去改 `BaseCarousel` 的契约
- [x] T238 [US12] 改 `storefront/src/views/MallView.vue`：在面包屑 `.crumbs` 之后、`.mall-page-main` 之前插入 banner，
      0 条时**整块不渲染**（依赖 T237）（FR-080 / FR-082）

---

## Phase 7: Polish & Cross-Cutting

- [x] T239 [P] 新建 `storefront/e2e/banner.spec.ts`：断言 banner 位置无数据时商城页**不出现空白区块**。
      ⚠️ 需要本地 48080 后端 —— 起不来就记为未执行，别跳过不记（FR-082）
      📝 **文件已写，但本轮未执行**：实测本地 48080 **连不上**（`curl` 返回 000），
      按任务要求**如实记为"未执行"**，不当作通过
- [x] T240 全量门禁：`cd storefront && npx vitest run`（全绿）、`pnpm build`（含 `vue-tsc`）、`pnpm lint`（0 error）；
      后端 `mvn -B -DskipTests -pl yudao-module-promotion-api test`（SC-025 ~ SC-031）
      ✅ 实测：**988 条 / 72 文件全绿**（起始基线 859，本轮 +129）；`vue-tsc` 无输出；
      `pnpm build` 通过；`eslint` **0 error**（1043 warning，与仓库既有一致）；
      后端 `BannerPositionEnumTest` **4/4 通过**
- [ ] T241 按 [quickstart.md](./quickstart.md) 走 §0 前置核对 → §1~§4 手工验证 → **§7 的 10 件抽样**，
      **逐条记录结果**（含失败与跳过）（SC-025 / SC-026 / SC-027 / SC-028 / SC-029 / SC-030）
      ⛔ **未执行 —— 属于所有者的人工步骤**：需要线上真实商品与手机端操作，
      自动化测不到（详见 quickstart §5 的「显式不验证」清单）
- [x] T242 [P] 复核 [plan.md](./plan.md)「明确不做的」**8 项**没有被悄悄扩范围
      —— 这 8 项同时是 FR-084（不动首页）与 FR-085（只消费既有接口）的保证（FR-084 / FR-085）
      ✅ 逐项**实证**（grep / git diff，不靠记忆）：清洗规则的 `ALLOWED_TAGS` 等**一字未改**；
      `components/home/` **0 处改动**；未出现 `add-browse-count`；生产代码只传 `COMMENT_TYPE.ALL`；
      评价图片无放大/遮罩；全程未造任何数据；未改服务端的可见性逻辑
- [x] T243 处理**评价列表无排序**这一已证实事实（T199 记的）：按 **quickstart §5** 的口径
      把它记为「**不验证**」并说明理由 —— 当前线上**没有评价数据**（Q6），顺序暂时无从体现。
      ⚠️ 若所有者要求"真正的最新在前"，需要**再加一处服务端改动**（给该请求 VO 继承 `SortablePageParam`，
      或在 mapper 加 `orderByDesc`），那超出当前 plan/contracts 的边界，**须先改设计再动手**（FR-070）

---

## 需求 → 任务 对照

供 `/speckit-analyze` 与日后回溯使用。**FR-070 与 FR-087 没有专属任务**，理由见末行。

| 需求 | 任务 |
|---|---|
| FR-063 详情展示富文本 | T201 / T203 / T204 |
| FR-064 非文字内容也算有内容 | T200 / T202 |
| FR-065 仅当真无内容时不渲染 | T201 / T203 |
| FR-066 不渲染时无空白占位 | T201 |
| FR-067 详情页展示评价列表 | T205 / T208 / T210 / T212 / T214 |
| FR-068 每条评价的字段 | T206 / T209 / T211 / T212 |
| FR-069 无评价空态 | T206 / T208 / T212 / T214 |
| FR-070 只展示可见评价 | *(无专属任务)* |
| FR-071 查看全部入口 | T206 / T207 / T212 / T213 |
| FR-072 长评价折叠 | T206 / T207 / T212 |
| FR-073 评价入口出现/不出现 | T219 / T220 / T221 / T226 / T227 |
| FR-074 表单字段与范围 | T216 / T222 / T224 / T225 |
| FR-075 提交前校验并定位字段 | T216 / T218 / T222 / T225 |
| FR-076 图片上传 | T217 / T223 |
| FR-077 失败原样透出后端原因 | T218 |
| FR-078 提交后提示"审核通过后展示" | T228 |
| FR-079 匿名不暴露身份 | T218 / T225 |
| FR-080 商城页 banner 与新增位置 | T229 / T230 / T232 / T233 / T234 / T235 / T236 / T238 |
| FR-081 多条轮播 / 单条无控件 | T231 / T237 |
| FR-082 无 banner 不渲染不留占位 | T231 / T238 / T239 |
| FR-083 有 url 跳转 / 无 url 不跳 | T231 / T237 |
| FR-084 不影响首页 banner | T242 |
| FR-085 只消费既有接口 | T242 |
| FR-086 视觉一致性断言 | T215 / T240 |
| FR-087 禁止三类静默缺陷 | *(无专属任务)* |
| SC-025 ~ SC-031 | T240（自动）/ T241（手工） |

> **FR-070 为什么没有专属任务**：后端已保证"只返回可见的评价"，
> 前端要做的恰恰是**不额外处理**。已写进 [contracts/app-api.md](./contracts/app-api.md) §2.1，
> 前端再加一层过滤反而是多余逻辑。
>
> **FR-087 为什么没有专属任务**：它是贯穿三处的**底线条款**（不得出现空白块/占位文案/编造内容），
> 已在每个故事内部具体化为 FR-066 / FR-069 / FR-082 与对应的测试任务，再单列一条会变成重复。

---

## Dependencies

```
T198（基线）
  └─ 此后四个故事互相独立，可任意顺序、可并行：

US9  T200→T202→T203      T201→T203      T204（独立）
US10 T205→T210           T206→T212      T207→T213      T208→T214
     T209 / T211 独立     T215 独立
US11 T216→T222           T217→T223      T218→T225      T219→T226   T220→T227
     T221 独立            T224 独立      T228 收口
US12 T229→T232           T230→T236      T231→T237      T233→T234
     T235 独立            T238 收口
```

- **故事之间无依赖** —— 每个故事都能单独上线
- 故事内部：**测试任务先于其实现任务**（宪法原则 I）
- `T214` 依赖 US10 的类型与组件，但**不依赖 US11/US12**
- **`T234`（生产库执行 INSERT）是 US12 的硬阻塞** —— 不做它，T241 的手工验证一定失败

## Parallel Execution Examples

**US9**（两个测试可同时写，它们改不同文件）：
```
并行：T200（sanitize.spec.ts） + T201（ProductView.spec.ts）
串行：T202 → T203
独立：T204（样式，随时可做）
```

**US11**（五条测试改五个不同文件，是全清单里并行度最高的一段）：
```
并行：T216（utils/comment.spec.ts）+ T217（MultiImageUploader.spec.ts）
    + T218（CommentCreateDialog.spec.ts）+ T219（OrderDetailView.spec.ts）
    + T220（OrderListView.spec.ts）
```

**US12**（前后端各一组，可分头做）：
```
并行：T229（后端枚举单测）+ T230（api/banner.spec.ts）+ T231（MallBanner.spec.ts）
```

## Implementation Strategy

**MVP = Phase 3（US9）单独交付。** 它最小、无外部依赖、且**立刻产生可见价值** ——
存量商品的详情当场可见，不需要任何人录数据。若只做这一件，本次目标也算达成三分之一，
而且是用户最先提到的那一件。

**建议顺序**：US9 → US10 → US11 → US12。
- US9 打底，风险最低
- US10 建立评价的**展示**，US11 再补**产出**（顺序反了会先有一个写了没处看的表单）
- US12 放最后：它是唯一需要动服务端 + 需要运营录内容 + 需要执行生产库 INSERT 的一件，
  外部依赖最多

**每个故事完成后即可单独验收**（用该故事的 Independent Test 与 quickstart 的对应小节），
不必等全部做完。

## Notes

- **`[P]` 的判据是"改不同文件且不依赖未完成任务"** —— 不是"看起来不相关"
- **测试任务必须先失败**，且失败原因要正确（不是拼写或导入错误）
- T233/T234（SQL 迁移与执行）**没有测试任务** —— 数据迁移写不出可先失败的单测，
  靠执行后人工核对，已在任务上注明
- 本期**不新增任何前端依赖**；唯一的服务端改动是 T232 的一行枚举
