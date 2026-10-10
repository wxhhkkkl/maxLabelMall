# Implementation Plan: 商品详情、评价（展示与撰写）与商城页 banner

**Branch**: `002-product-detail-reviews-banner` | **Date**: 2026-10-10 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-product-detail-reviews-banner/spec.md`

## Summary

三件事，性质不同，因此做法也不同：

1. **商品详情**（US9/P1）—— 链路完好，**只改一处判定口径**。现判定是"清洗后剥掉所有标签、
   看还剩不剩文字"，于是纯图片详情被判成空。改为"清洗后是否还有**可见内容**（非空文本**或**图片）"。
   不改清洗规则、不动后端、不加数据。
2. **评价展示**（US10/P2）—— 后端读取接口完备，**纯前端接入**：详情页评价区 + 一个"全部评价"页。
3. **评价撰写**（US11/P3）—— 后端撰写接口完备，**纯前端接入**：订单页入口 + 评价表单
   （两个维度星级、内容、最多 9 张图、匿名）。图片上传复用个人中心那轮已有的通用上传能力。
4. **商城页 banner**（US12/P4）—— **唯一需要动服务端的一处**：给既有的 banner 位置字典
   与后端枚举各加一个「商城页」取值（管理端零改动，它完全靠字典渲染）。前端则把**早已写好的**
   `.mall-banner` 样式与 `BaseCarousel` 组件接上。

**本期不新增任何前端依赖**：轮播组件、弹层、表单、上传能力全部复用既有件。

## Technical Context

**Language/Version**: TypeScript 6.x / Node ≥ 20.19 / Vue 3.5.x（前端）；Java 25 + yudao（服务端，**仅一处枚举**）
**Primary Dependencies**: 既有栈（Vue / Vite / Pinia / vue-router / axios / vitest / @vue/test-utils）。**不新增任何依赖**
**Storage**: 无新增持久化。图片走既有 OSS（`infra_file_config` id=25 阿里云存储）
**Testing**: **测试先行（宪法原则 I）**。三层：
① **Vitest 单元** —— 富文本"有无可见内容"的判定、评价表单的校验纯函数；
② **Vitest 组件** —— 详情区块的显示与否、评价区与空态、评价表单、商城页 banner 的三态；
③ **后端单测** —— 新增的 banner 位置枚举取值；
④ **手工清单** —— [quickstart.md](./quickstart.md)，覆盖自动化测不到的（真实富文本商品、真机写评价）
**Target Platform**: 现代浏览器，桌面优先，移动端宽度 ≥ 375px
**Project Type**: 前端单页应用（消费既有 app-api）+ **一处服务端枚举改动**
**Performance Goals**: 商品详情页首屏内容 ≤ 3 秒可见（沿用 001 的 SC-004）。**评价区 MUST 异步加载**，
其接口失败不得影响详情与购买流程
**Constraints**:
- 不引入 UI 组件库（001 既有约束）
- C 端只走 `/app-api`；管理端零改动
- 后端**不校验**评分范围、**不校验**评价内容长度（DB 为 varchar(1024)）→ 前端必须补齐，并写进契约
- 后端**默认不按评价的"倒序"排序是未知的**，需在实现前确认（见 [research.md](./research.md) R4）
**Scale/Scope**: 3 个用户故事 + 1 个后端枚举取值；新增 **1 个页面**、**3 个组件**、**2 个 api/type 模块**、**2 个纯函数模块**

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

宪法版本 `.specify/memory/constitution.md` **v2.1.0**。逐条校验：

| # | 宪法原则 | 是否满足 | 依据 / 违规处理 |
|---|---|---|---|
| I | **测试先行（不可协商）** | ✅ | 每个故事的测试任务排在实现任务之前（见 [tasks.md](./tasks.md)，由 `/speckit-tasks` 生成）。本计划「测试策略」一节显式列出每层要测什么，并写明**先失败**的要求。富文本判定与评价校验做成纯函数正是为了可穷举测试 |
| II | **先澄清，不猜测** | ✅ | `/speckit-specify` 阶段提出 4 个决策点并全部由所有者拍板（Q5 含撰写 / Q6 接受空态 / Q7 复用后台 Banner 页但新增位置取值 / Q8 保持先审后显）。**Q8 是核查中主动发现的问题** —— 会员提交的评价默认不可见，若不问就实现，用户会以为功能坏了。[research.md](./research.md) 每条决策都记了被否决的方案 |
| III | **复用既有能力** | ✅ | 不新建任何后端能力：读取、撰写、图片上传、banner 查询全部用既有接口。轮播复用 `BaseCarousel`、弹层复用 `MlModal`、上传复用 `api/upload.ts`、版式复用 `design.css` 里**已经写好但没人用**的 `.mall-banner`。**唯一的服务端改动**是给既有位置枚举加一个取值（不是新建能力），理由是复用「首页」位置会迫使两页共用内容 |
| IV | **最小改动** | ✅ | 详情修复只动判定口径这一处，不碰清洗规则；不改首页 `HomeHero`；不为评价引入任何新依赖。新增文件全部落在新的模块内，不改既有组件的契约 |
| V | **增量可验证** | ✅ | 4 个故事各自独立可交付、可验收（见「实施分期」）。SC-025~031 每条都指明了验证方式（自动断言 / 手工清单）。**显式声明不验证的项**见 [quickstart.md](./quickstart.md) §5 |

**技术栈与架构约束**：全部满足。Web 前端只走 `/app-api`；管理端 `/admin-api` 零改动
（banner 位置新增后管理端**自动**多出一个单选项，因为它是字典渲染的）；数据库仍只用 MySQL。

**Gate 结论**：**通过，无违规项**。

**Phase 1 设计完成后复检**：见文末「Post-Design Re-check」。

## Complexity Tracking

> **无违规项。**

但有一处**需要留痕的越界**（不构成违规，宪法 v2.0.0 已明确允许二开）：

| 事项 | 为什么必须 | 被否决的更简做法 |
|---|---|---|
| 改**上游文件** `BannerPositionEnum.java`（加一项枚举） | 后端对 banner 位置做 `@InEnum` **强校验**，不改枚举则新位置值 100% 被 400 拒绝 —— 光加字典行没用 | ① **复用「首页」位置**：零服务端改动，但商城页与首页被迫共用同一批内容。首页现在是写死的静态首屏、不读这个接口，所以**眼下不会真串位**，但那是埋雷 —— 首页哪天接上接口就会一起变。② **前端写死 banner**：与"内容由后台下发"的既有做法相反，运营无法维护，且直接违反 FR-080 |

改动范围已压到最小：**一个枚举加一项 + 一行字典数据 + 一次生产库 INSERT**。管理端前端零改动。

## Project Structure

### Documentation (this feature)

```text
specs/002-product-detail-reviews-banner/
├── plan.md              # 本文件
├── research.md          # Phase 0 产出：9 条技术决策与被否决方案
├── data-model.md        # Phase 1 产出：实体映射、字段、校验规则
├── quickstart.md        # Phase 1 产出：手工验证清单 + 显式不验证项
├── contracts/
│   └── app-api.md       # Phase 1 产出：本期消费/改动的接口契约
├── checklists/
│   └── requirements.md  # /speckit-specify 产出
├── spec.md              # /speckit-specify 产出
└── tasks.md             # /speckit-tasks 产出（本命令不生成）
```

### Source Code (repository root)

```text
storefront/                                    # C 端 Web 前端（本期主战场）
├── src/
│   ├── api/
│   │   ├── comment.ts                         # 新增：评价分页读取
│   │   ├── tradeComment.ts                    # 新增：提交评价（写操作独立成文件，与读取分开）
│   │   └── banner.ts                          # 新增：banner 列表（按位置）
│   ├── types/
│   │   ├── comment.ts                         # 新增：ProductComment
│   │   ├── banner.ts                          # 新增：Banner
│   │   └── order.ts                           # 改动：订单与订单项补 commentStatus（后端已返回，前端未建模）
│   ├── utils/
│   │   ├── sanitize.ts                        # 改动：新增 hasVisibleContent()（判定"有无详情"的唯一实现处）
│   │   └── comment.ts                         # 新增：评价表单的校验纯函数（评分范围/内容长度/图片张数）
│   ├── components/
│   │   ├── CommentCreateDialog.vue            # 新增：写评价表单弹层（仿 ProfileEditDialog）
│   │   ├── MultiImageUploader.vue             # 新增：多图上传（AvatarUploader 只支持单张，不能直接用）
│   │   ├── CommentList.vue                    # 新增：评价列表（详情页与全部评价页共用）
│   │   ├── base/MlRate.vue                    # 新增：五星评分（全仓没有评分组件）
│   │   └── MallBanner.vue                      # 新增：商城页横幅（接 BaseCarousel + .mall-banner 样式）
│   └── views/
│       ├── ProductView.vue                    # 改动：详情判定 + 评价区；.pd-desc 图片限宽
│       ├── ProductCommentListView.vue         # 新增：该商品的全部评价（路由 /product/:id/comments）
│       ├── MallView.vue                        # 改动：面包屑之后插入 banner
│       ├── OrderListView.vue                  # 改动：已完成且未评价时给「去评价」入口
│       └── OrderDetailView.vue                # 改动：每个待评价的订单项给评价入口
│   └── styles/
│       └── design-consistency.spec.ts         # 改动：登记新增的 ProductCommentListView
└── e2e/
    └── banner.spec.ts                          # 可选：banner 无数据时不渲染区块

yudao-cloud/                                   # 服务端（本片仅一处）
└── yudao-module-mall/yudao-module-promotion/
    └── yudao-module-promotion-api/
        └── src/main/java/cn/iocoder/yudao/module/promotion/enums/banner/
            └── BannerPositionEnum.java        # 改动：新增 MALL_POSITION(6, "商城页")

yudao-cloud/sql/mysql/
└── <新迁移脚本>                                # 新增：字典 promotion_banner_position 增加一行（生产库需执行）
```

**Structure Decision**: 前端沿用既有 `storefront/` 的分层（`api/` `types/` `utils/` `components/` `views/`），
新文件按既有命名与职责放置。写操作（提交评价）与读操作分开成文件，与既有 `afterSale.ts` 的风格一致
（那里也是读写在同一个文件里，但评价的写走的是 **trade** 模块的接口、读走 **product** 模块，
两者不是同一个域，混在一起会误导）。服务端只碰一个枚举文件 + 一个 SQL 迁移脚本。

## 实施分期

四个故事**互相独立**，按"能独立交付"排序。前三个是纯前端，第四个含一处服务端改动。

### Phase 1 —— US9 商品详情（P1，最小、独立可交付）

1. **先写测试**：`utils/sanitize.spec.ts` 增 `hasVisibleContent` 的穷举用例
   （纯图片 / 纯空标签 / 纯 `<br>` / 空 script 残留 / 纯文本 / 图文混排）；
   `ProductView.spec.ts` 增"纯图片详情要渲染"（**当前必然失败**）。
2. 实现 `sanitize.ts` 的 `hasVisibleContent()`；改 `ProductView.vue` 的 `safeDescription` 用它。
3. 检查并修 `.pd-desc img` 的限宽（详情从"不显示图片"变成"显示图片"后，长图可能撑破版式）。
4. 手工验证：线上挑一件纯图片详情的商品。

### Phase 2 —— US10 评价展示（P2，纯前端）

1. **先写测试**：`api/comment.spec.ts`（路径与参数）、`CommentList.spec.ts`（渲染字段、空态）、
   `ProductView.spec.ts` 增评价区用例。
2. 实现 `types/comment.ts`、`api/comment.ts`、`CommentList.vue`、详情页评价区。
3. 实现 `ProductCommentListView.vue` + 路由 + 在 `design-consistency.spec.ts` **登记**（该文件是显式清单）。
4. 详情页评价区**异步加载**：不阻塞详情渲染；接口失败只让评价区降级，不崩页面。

### Phase 3 —— US11 评价撰写（P3，纯前端）

1. **先写测试**：`utils/comment.spec.ts`（校验穷举）、`MlRate.spec.ts`、
   `MultiImageUploader.spec.ts`（张数上限、上传失败不丢已填内容）、
   `CommentCreateDialog.spec.ts`（**断言请求体**：字段名与取值、匿名、张数）、
   `OrderDetailView.spec.ts` / `OrderListView.spec.ts` 增入口出现/不出现的用例。
2. `types/order.ts` 补 `commentStatus`（订单级 + 订单项级）。
3. 实现 `MlRate`、`MultiImageUploader`、`CommentCreateDialog`、`api/tradeComment.ts`。
4. 接入口：订单详情按**订单项**给按钮（放 `.od-sale-actions` 那个位置，别挂在 grid 的直接子级上）；
   订单列表给**订单级**「去评价」（已完成 + 未评价）。
5. **提交成功后**：关闭弹层 + 重拉订单 + 显示"评价已提交，**审核通过后展示**"（FR-078）。

### Phase 4 —— US12 商城页 banner（P4，含服务端）

1. **先写测试**：后端 `BannerPositionEnum` 取值用例；前端 `MallBanner.spec.ts`
   （有数据渲染、单条不出现轮播控件、无数据整块不渲染、图片失败不撑破布局）。
2. 服务端：枚举加 `MALL_POSITION(6, "商城页")`；写 SQL 迁移脚本插字典行。
3. **生产库执行字典 INSERT**（否则运营在后台选不到这个位置）。
4. 前端：`types/banner.ts`、`api/banner.ts`、`MallBanner.vue`，插到 `MallView.vue` 面包屑之后。
5. 用手工清单验证"无数据时不占位"。

## 测试策略

### 三层

① **Vitest 单元（纯函数，可穷举）**
- `sanitize.hasVisibleContent`：这是本次详情修复的**核心判定**，必须穷举
  （`<img>` / `<p></p>` / `<br>` / `<div></div>` / 空 script 残留 / 纯文本 / 图文混排 / `<table>` 内嵌图片）
- `utils/comment` 的校验：评分 1~5（越界拒绝）、内容必填且 ≤1024、图片 ≤9 张

② **Vitest 组件（断言**请求体**与**是否重拉**，不只断言界面文案）**
- `ProductView`：详情在"纯图片"时**渲染**、在"真为空"时**不渲染**（且不出现占位文案）
- `CommentList`：字段渲染、空态明说、>N 条时给入口
- `CommentCreateDialog`：**提交的请求体精确匹配**（字段名、匿名取值、图片数组）；
  必填为空时不发请求；图片超 9 张时不发请求
- `MallBanner`：三态（多条轮播 / 单条无控件 / 无数据整块不渲染）
- **本项目吃过"单测只断言 UI 不断言请求体"的亏**，所以每处写操作都必须钉住请求体

③ **后端单测**
- `BannerPositionEnum`：新增取值在 `ARRAYS` 里（`@InEnum` 校验靠它）

④ **手工清单**（[quickstart.md](./quickstart.md)）—— 自动化测不到的部分：真实富文本商品的观感、
长图是否撑破版式、真机点击、运营在后台能否选到新位置。

### e2e 的前置与成本

现有 e2e 需要本地 48080 后端 + 开发库。本期只建议加**一条**低成本的：
`e2e/banner.spec.ts` 断言"banner 位置无数据时页面不出现空白区块"。其余靠组件测试与手工清单。
**不新增需要真实支付/真实评价数据的 e2e**。

### 明确不做的

- **不为"能看见效果"造数据**：不导入、不伪造评价（Q6）。线上暂无评价时，空态就是正确表现。
- **不改"先审后显"**：提交后仍不可见，须运营在后台点"显示"（Q8）。本次**不改服务端**。
- **不动首页 `HomeHero`**：它是写死的静态首屏，与本次的 `promotion_banner` 无关。
- **不做商家回复的撰写**：回复仍由运营在既有后台完成，C 端只展示。
- **不接 banner 的浏览计数**：后端有 `add-browse-count`，本期不调用（不加不减）。
- **不放松详情富文本的清洗规则**：这是安全边界，不为显示效果让步。
- **不做评价图片的点击放大**：需要额外的遮罩层，本期不做。
- **不做评价的"好评/中评/差评"筛选 UI**：后端 `type` 参数存在，但本期只做"全部"（传 0）。

## 主要风险与对策

| # | 风险 | 对策 |
|---|---|---|
| R1 | **评价区拖慢详情页首屏** —— 详情页是支付链路的关键页 | 评价区**异步加载**，不阻塞详情；接口失败只降级该区块，**绝不**让整个详情页报错 |
| R2 | **详情修好后，长图撑破版式** —— 从"不显示图片"变成"显示"，这是个新暴露面 | 给 `.pd-desc` 内的图片限宽（`max-width:100%`），并在手工清单里用一件真实长图商品验 |
| R3 | ~~排序未知~~ → **已证实无排序**（`AppCommentPageReqVO extends PageParam`，无 ORDER BY）：返回顺序不确定，MySQL 实际多半升序（**最早的在前**） | 已定案，不再是风险：契约不承诺顺序、详情页文案用中性的「用户评价」（[research.md](./research.md) R4、[contracts/app-api.md](./contracts/app-api.md) §2.1）。**线上当前无评价数据（Q6），影响为零**。要"真正的最新在前"需再加一处服务端改动 —— 超出本次边界，需先改设计 |
| R4 | **后端不校验评分范围与内容长度** —— 会出现"前端放行、后端落库报错" | 前端补校验并写进 [contracts/app-api.md](./contracts/app-api.md)，作为与后端的显式约定（不过度承诺后端行为） |
| R5 | **生产库漏执行字典 INSERT** —— 运营在后台选不到「商城页」 | 把 INSERT 写进本计划的 Phase 4 与 [quickstart.md](./quickstart.md) 的前置核对；**上线时必做** |
| R6 | **`.mbn-*` 那套样式是设计稿遗留的文字型横幅**（渐变底 + 标题 + 描述 + 按钮），与后端只提供 `picUrl` 的图片型 banner **不匹配** | **只复用外层 `.mall-banner`**（容器尺寸与响应式），**不硬套** `.mbn-*`；图片型 slide 自己写少量样式。`.mbn-*` 是死 CSS，本期继续不动它 |
| R7 | **改了上游枚举** —— 将来升级上游需手工对账 | 改动压到最小（一项枚举 + 一行字典），并在提交信息里写明原因（宪法 v2.0.0 的工程建议） |
| R8 | **"审核后展示"被当成 bug** —— 用户写完看不到 | FR-078 要求提交成功时**明说**；并把这条写进手工清单，确认文案真的出现 |

## Post-Design Re-check

Phase 1 设计（[data-model.md](./data-model.md) / [contracts/app-api.md](./contracts/app-api.md) / [research.md](./research.md)）完成后复检：
**无新增违规项**，Constitution Check 结论不变。两处需要设计阶段确认的点已在 research 中定案：
R3（排序）与 R4（校验归属）。**无未解决的澄清项。**
