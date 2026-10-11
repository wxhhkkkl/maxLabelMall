# Implementation Plan: 商品分类筛选维度（后台配置 + 前台商城筛选）

**Branch**: `003-product-filter-dimensions` | **Date**: 2026-10-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-product-filter-dimensions/spec.md`

## Summary

所有者要的是参考图那种筛选条：「分类：蜡基/混合基/全树脂」「宽度：40 110 100 …」。
核查后，**难点不在 UI，而在筛选所依据的数据根本不存在** —— 后端的 C 端商品分页只认
`keyword`（且只对商品名 LIKE）/ `categoryId` / 排序；规格（宽度、长度、轴心）只以**文本形式
混在商品名里**，且写法不统一。这是一次**数据模型 + 后台配置 + 前台消费**的完整链路建设。

三条已拍板决策（spec 的 Q9/Q10/Q11）决定了它的形状：

- **同维度单选、跨维度 AND**（Q9=B）→ 查询侧是「每个已选维度一条**等值** `EXISTS`」。
2. **维度沿分类树向下继承**（Q10=B）→「宽度」在「碳带」配一次，三个子分类页都能用；
   维度的辖域在查询时按**分类祖先链**实时求（分类树只有 3 层 / 42 个分类，无需物化）。
3. **选项照常展示、不随条件收敛**（Q11=A）→ 选项集合只由配置决定，是**静态**的，
   因此服务端**不做聚合计数**，也**顺带消掉了** Q11=B 必然带出的那个陷阱
   （计数若忘了排除本维度自身的已选值，顾客选中一个值后同行其它值会全部消失、连切换都做不到 —— 见 R6 的留档）。

**本期的四块改动**：

| 端 | 改动 |
|---|---|
| **服务端** | 3 张新表；1 个新 app 接口（维度列表）；既有分页接口加 1 个可选参数；一套 admin 维度/值的 CRUD；商品接口的辖域校验与剔除 |
| **管理端** | 既有「商品分类」页加「筛选维度」抽屉（含继承段）；既有商品编辑页加「筛选标签」子表单 |
| **C 端前台** | 商城页筛选条组件 + 两个 api/type 模块 + 筛选状态的一个纯函数 |
| **数据库** | 一个建表迁移脚本，**生产库执行一次**（无字典/枚举数据） |

## Technical Context

**Language/Version**: Java 25 + yudao（服务端）；TypeScript 6.x / Node ≥ 20.19 / Vue 3.5.x（两个前端）
**Primary Dependencies**: **不新增任何依赖**。服务端用既有的 MyBatis-Plus / `BaseMapperX` / `BaseDO`；
前端沿用既有栈（Vue / Vite / Pinia / axios / vitest / @vue/test-utils）
**Storage**: MySQL（宪法约束）。新增 3 张 `product_*` 表，复用既有库与租户机制
**Testing**: **测试先行（宪法原则 I）**。四层：① 服务端 JUnit（本模块已有 `src/test/resources/sql/create_tables.sql` 的先例）；
② 前端 Vitest 单测（api 序列化、纯函数）；③ 前端 Vitest 组件（筛选条各态）；
④ 手工清单 —— [quickstart.md](./quickstart.md)
**Target Platform**: 现代浏览器（桌面优先，移动端宽度 ≥ 375px）+ 管理端后台
**Project Type**: 多端（服务端 + Web 管理端 + Web C 端），符合宪法「四端结构」中的三端；
小程序 / App 端本期不参与（spec 的 Assumptions）
**Performance Goals**: 沿用 001 的 SC-004（首屏 ≤ 3 秒）。筛选查询在本期量级
（全站 282 件商品、碳带 54 件）**不建立性能基线**，也不做缓存
**Constraints**:
- 不引入 UI 组件库；C 端只走 `/app-api`、管理端只走 `/admin-api`
- 既有 `/product/spu/page` **只做加法**：新参数可选、不传时行为逐字不变
- 既有 `product_property` / `product_property_value` / `product_sku.properties` **一律不碰**（R1）
- 辖域规则（哪些值能挂在哪个商品上）**由服务端裁决**，前端只传扁平的值 id 数组（宪法 III）
**Scale/Scope**: 3 个用户故事；服务端 **3 张表 + 约 10 个接口**；管理端 **1 个抽屉 + 1 个子表单**；
前台 **1 个组件 + 2 个 api/type 模块 + 1 个纯函数模块**

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

宪法版本 `.specify/memory/constitution.md` **v2.1.0**。逐条校验：

| # | 宪法原则 | 是否满足 | 依据 / 违规处理 |
|---|---|---|---|
| I | **测试先行（不可协商）** | ✅ | 每个故事的测试任务排在实现之前（`/speckit-tasks` 生成）。本计划「测试策略」一节显式列出**每层要测什么**，其中 **research R5 的"同维度 OR / 跨维度 AND"** 与 **本计划 R1 的"选项集合不被收敛"** 是两条必须钉住的点 —— 它们错了不会报错，只会静默地让筛选条少几个选项、或者选完一个就选不了第二个 |
| II | **先澄清，不猜测** | ✅ | 四个决策点全部由所有者拍板（**Q9=B / Q10=B / Q11=A / Q12=各条件自带清除**），且 **Q9、Q10、Q11 三条都经历过改判** —— 这正是本原则在起作用：把 Q10=A 的后果（「碳带」页不出现筛选条、父分类上的配置变成死配置）说清楚后，所有者改了主意；Q11 在 B 与 A 之间走了一个来回；Q9 由多选改为单选。**每一次改判都连带重算了受影响的范围**（Q11 改回 A 拿掉了整块聚合计数；Q9 改单选把查询从「同组 OR」简化成了等值 `EXISTS`，并新增了「同维度多值 = 非法请求」这条约定）。[research.md](./research.md) 的 R1~R14 每条都记了依据与**被否决的方案** |
| III | **复用既有能力** | ✅ | **本特性正是这条原则被认真执行的结果，而不是违反它**：核查后确认 yudao 既有的属性机制**不能**承载分类级筛选（属性是全局的、没有 `categoryId`、且只服务 SKU 规格 —— R1 有完整代码证据），故新建数据模型并在 research 里留下了否决理由。此前能复用的**全部复用**：分类展开、关键词匹配、排序、分页、租户、审计字段、`BaseMapperX`、上传、管理端的表单与抽屉骨架 |
| IV | **最小改动** | ⚠️ **有留痕，见 Complexity Tracking** | 三处对既有代码做**加法**：分页接口加可选参数、商品详情接口加一个响应字段、管理端两个页面各加一块。均不动既有字段与既有语义；`product_property` 一族**一个字节都不碰** |
| V | **增量可验证** | ✅ | 三个故事各自独立可交付（见「实施分期」）。SC-032~041 每条都在 [quickstart.md](./quickstart.md) §6 指明了对应的人工/自动验证动作；**显式不验证的 6 项**写在 §5 |

**技术栈与架构约束**：全部满足。C 端只走 `/app-api`、管理端只走 `/admin-api`、数据库只用 MySQL、
服务端仍是单体启动形态。**不新增任何依赖**。

**Gate 结论**：**通过**。一处留痕（IV）见下，不构成违规。

## Complexity Tracking

> 无宪法层面的违规项。以下三处**越界需留痕**（宪法 v2.0.0 已允许二开，但代价从"违规"变成"将来升级上游要手工对账"）：

| 事项 | 为什么必须 | 被否决的更简做法 |
|---|---|---|
| **改既有 app 接口** `/product/spu/page`（加可选参数） | 前台筛选结果必须与关键词、分类、排序**在同一次分页查询里**生效（FR-100），否则要么分两次查、要么前端过滤（都违反 FR-100） | ① **新增一个平行的筛选分页接口** —— 会把关键词/分类/排序/分页整套逻辑复制一份，两份真相必然分叉；② **前端拉全量再筛** —— 商品量一大就废，且关键词本来就是服务端的 |
| **改既有 app 接口** `/product/spu/get`（加一个响应字段 `filterValueIds`） | 商品编辑页要能读回当前挂的标签 | 新增一个读接口 —— 无谓地多一次往返，且商品详情页并不需要这个字段（加了也无害，是**加法**） |
| **改既有管理端页面**（商品分类页、商品编辑页） | 维度配置天然属于分类，商品标签天然属于商品编辑 | **新建一级菜单「商品筛选维度」** —— 多一套菜单与权限，而运营的心智是"我给这个分类配筛选"；R8 有完整论证 |

三处改动都压到了**最小值**：两个 app 接口各自是"加一个可选参数 / 加一个响应字段"，
两个管理端页面各自是"加一个按钮 + 一个子表单"，既有分支一行未动。

## Project Structure

### Documentation (this feature)

```text
specs/003-product-filter-dimensions/
├── plan.md                    # 本文件
├── research.md                # Phase 0 产出：R1~R14 技术决策与被否决方案
├── data-model.md              # Phase 1 产出：3 个新实体、映射、派生查询口径
├── quickstart.md              # Phase 1 产出：手工验证清单 + 显式不验证项
├── contracts/
│   ├── app-api.md             # Phase 1 产出：C 端消费与改动
│   └── admin-api.md           # Phase 1 产出：管理端新增能力（本仓库首份 admin-api 契约）
├── checklists/
│   └── requirements.md        # /speckit-specify 产出
├── spec.md                    # /speckit-specify 产出
└── tasks.md                   # /speckit-tasks 产出（本命令不生成）
```

### Source Code (repository root)

```text
yudao-cloud/                                   # ① 服务端
├── yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/
│   ├── dal/dataobject/filter/
│   │   ├── ProductFilterDimensionDO.java              # 新增
│   │   ├── ProductFilterValueDO.java                  # 新增
│   │   └── ProductSpuFilterValueDO.java               # 新增（多对多关联）
│   ├── dal/mysql/filter/
│   │   ├── ProductFilterDimensionMapper.java          # 新增
│   │   ├── ProductFilterValueMapper.java              # 新增
│   │   └── ProductSpuFilterValueMapper.java           # 新增（按 spuId 查/删、按 valueId 统计引用数；
│   │                                                   #  筛选用的 EXISTS 不在这里，在 ProductSpuMapper.selectPage 里追加）
│   ├── dal/mysql/spu/ProductSpuMapper.java            # 改动：selectPage 追加可选的维度 EXISTS 条件
│   ├── controller/admin/filter/
│   │   ├── ProductFilterDimensionController.java      # 新增
│   │   ├── ProductFilterValueController.java          # 新增
│   │   └── vo/…                                       # 新增（Save/Page/Resp/DeleteImpact）
│   ├── controller/app/filter/ProductFilterController.java   # 新增：维度列表
│   ├── controller/app/spu/vo/AppProductSpuPageReqVO.java    # 改动：+ filterValueIds
│   ├── controller/app/spu/vo/AppProductSpuRespVO.java       # 不改（前台每张卡不需要标签）
│   ├── controller/admin/spu/vo/ProductSpuRespVO.java        # 改动：+ filterValueIds
│   ├── controller/admin/spu/vo/ProductSpuSaveReqVO.java     # 改动：+ filterValueIds
│   ├── service/filter/…                                # 新增：维度/值的服务与校验
│   ├── service/category/…                              # 新增方法：scopeOf(categoryId) 祖先链
│   └── service/spu/ProductSpuServiceImpl.java          # 改动：分页接维度条件；保存时管标签与剔除
│   └── src/test/java/…                                 # 新增测试（本模块已有测试先例）
└── sql/mysql/<新迁移脚本>.sql                           # 新增：3 张建表

yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/  # ② 管理端
├── api/mall/product/
│   ├── filterDimension.ts                              # 新增
│   ├── filterValue.ts                                  # 新增
│   └── spu.ts                                          # 改动：Spu 类型 + filterValueIds；+ 两个新接口
├── views/mall/product/category/
│   ├── index.vue                                       # 改动：操作列加「筛选维度」按钮
│   └── FilterDimensionDrawer.vue                       # 新增：维度/值管理（含「继承自父分类」段）
└── views/mall/product/spu/form/
    ├── index.vue                                       # 改动：挂上新的子表单
    └── FilterTagForm.vue                               # 新增：按维度分组多选

storefront/                                    # ③ C 端前台
├── src/
│   ├── api/
│   │   ├── product.ts                                  # 改动：ProductPageParams + filterValueIds
│   │   └── filter.ts                                   # 新增：维度列表
│   ├── types/
│   │   └── filter.ts                                   # 新增：FilterDimension / FilterValue
│   ├── utils/
│   │   └── filter.ts                                   # 新增：切换分类时的保留/重置纯函数
│   ├── components/
│   │   └── ProductFilterBar.vue                        # 新增：筛选条
│   └── views/
│       └── MallView.vue                                # 改动：接筛选条、串进查询与分页
```

**Structure Decision**：
- 服务端新增一个 `filter` **子包**（`dal`/`controller`/`service` 三处各一套），与既有
  `spu` / `sku` / `property` / `category` 平级 —— 沿用本模块既有的"按实体分子包"的组织方式。
  **不放进 `property` 包**，因为 R1 已判定二者语义不同，放一起会诱导后来者去复用。
- 管理端沿用既有的 `api/mall/product/` + `views/mall/product/<实体>/` 分层；
  新页面作为**既有页面的组件**而非独立路由（R8）。
- 前台沿用既有 `api/` / `types/` / `utils/` / `components/` / `views/` 分层，
  筛选条是一个独立组件，`MallView.vue` 只负责把它串进查询链路。

## 实施分期

**本章的「Phase 1 / 2 / 3」对应 [tasks.md](./tasks.md) 的「Phase 3 / 4 / 5」** ——
tasks.md 在前面多了 Setup 与 Foundational 两个阶段，编号因此整体后移两位。
引用时请以 tasks.md 的编号为准。

三个阶段**依次依赖**（配置 → 挂标签 → 前台消费），按"能独立交付、独立验收"排序。
前两个动服务端与管理端，第三个动前台。

### Phase 1 —— US13 后台配置分类维度（P1）

> 交付物：运营能在分类页配出维度与可选值，并能看见继承来的维度。

1. **建表**：新迁移脚本（3 张表 + 索引，见 [data-model.md](./data-model.md) §1）；
   在开发库执行，并在 `create_tables.sql`（服务端测试用）里同步补上 —— 否则服务端测试跑不起来。
2. **先写测试**：维度/值的 CRUD 校验 ——
   **重名检测必须覆盖"跨祖先链重名"**（FR-091，不是只查同一 `category_id`）；
   `scopeOf()` 的祖先链（3 层链 / 根分类 / 孤立分类 / **成环时不死循环**）。
3. 实现 3 个 DO + 3 个 Mapper + 服务层（含 `scopeOf()` 与重名校验）+ admin CRUD 接口。
4. 管理端：`api/mall/product/filterDimension.ts` / `filterValue.ts`；
   分类页操作列加按钮 → `FilterDimensionDrawer.vue`（**上段自有、下段继承**，继承项只读并标注来源）。
5. **手工验收**：[quickstart.md](./quickstart.md) §1 的 1~5 步（含"切到蜡基能看到继承"）。

### Phase 2 —— US14 商品挂维度标签（P2）

> 交付物：运营能在一件商品上多选标签；改分类时失效标签被清理并可见。

1. **先写测试**：
   - 辖域校验（FR-095）：挂上祖先链之外的维度值 → **不写入**；
   - 改分类后的**剔除**（FR-097 / R11）：能跑通"挂 → 改分类 → 标签被移除"；
   - 重名/非法 id 的边界。
2. 服务端：`ProductSpuSaveReqVO` / `ProductSpuRespVO` 加 `filterValueIds`；
   `ProductSpuServiceImpl` 保存时写入、校验、剔除；新增 `filter-value-options` 读接口。
3. 管理端：`api/mall/product/spu.ts` 补类型与接口；
   新增 `FilterTagForm.vue`（按维度分组多选，标注"来自「碳带」"），挂进 `form/index.vue`。
4. 保存后前端**对比前后 `filterValueIds`**，把"已移除 N 个"显示出来（contracts/admin-api.md §3.3）。
5. **手工验收**：[quickstart.md](./quickstart.md) §1 第 6 步 + §4 的第 1、2 步。

### Phase 3 —— US15 前台筛选（P3）

> 交付物：顾客在分类页能按维度挑商品，条件与关键词/排序叠加，翻页语义正确。

1. **先写测试**：
   - **服务端**：分页条件的组装 —— **每个已选维度一条等值 `EXISTS`、跨维度取交集**（FR-101/R5）；
     **不传 `filterValueIds` 时，生成的查询与改动前一致**（这是对既有共享接口的兼容性承诺）；
     维度列表**只按 `categoryId` 取**，不受关键词/已选值影响（FR-111/R6）。
   - **前端单测**：`toPageParams` 对 `filterValueIds` 的**序列化形态**（逗号分隔、空数组不传）；
     `utils/filter.ts` 的"切换分类保留仍适用的选择、重置其余"（FR-103）。
   - **前端组件**：筛选条的有维度/无维度/某行无可选值整行不出现；
     **选项集合稳定**（改关键词或选中值后逐字不变）；
     空结果时**筛选条整块保留**、已选值仍可点，出现空态但**不出现全局清除按钮**（FR-114）。
2. 服务端：`AppProductSpuPageReqVO` 加 `filterValueIds`；`ProductSpuMapper.selectPage` 追加 `EXISTS`；
   新增 `ProductFilterController`（维度列表）。
3. 前台：`types/filter.ts`、`api/filter.ts`、`api/product.ts` 加参数、
   `utils/filter.ts`、`ProductFilterBar.vue`；`MallView.vue` 串起来 ——
   **注意 `pageNo` 的复位**（沿用既有 `onSearch` / `onPickCategory` 口径，FR-102）。
4. 两个请求**并发发出**；**维度列表请求失败只让筛选条降级**（整块不渲染 + 可重试），
   商品列表照常 —— 沿用 002 对评价区的处理（FR-106）。
5. **手工验收**：[quickstart.md](./quickstart.md) §2 全部（**§2 第 3、5 步与 §2.1 的反向验证是核心**）+ §3 三个断点。

## 测试策略

### 四层

① **服务端 JUnit（本模块已有 `src/test/resources/sql/` 的先例，沿用）**
- **筛选语义**：选中「宽度=110」→ 只剩 110；再叠加「长度=300m」→ 两者交集；
  同一维度传两个值是**非法请求**（Q9=B 单选）。**这是 FR-101 的可执行定义。**
- **兼容性**：`filterValueIds` 为空/不传时，查询与改动前**逐字一致**（既有调用方零影响的证据）。
- **选项集合稳定**：改变关键词或已选值后，维度列表接口的返回**逐字不变**（FR-111 / R6）。
- **辖域**：`scopeOf()` 与挂标签校验；改分类后的剔除。
- **重名**：跨祖先链（父子）重名必须被拒 —— 只查同一 `category_id` 的实现会漏掉它。

② **前端 Vitest 单测**
- `toPageParams`：`filterValueIds` 的序列化形态（对齐既有 `categoryIds` 的 `.join(',')`）。
- `utils/filter.ts`：切换分类时**保留仍适用的选择**、重置其余（FR-103）。

③ **前端 Vitest 组件**
- `ProductFilterBar`：有维度 / 无维度整块不渲染 / 某维度无可选值时整行不出现 /
  **一行值放不下时值区可横向滑动且不折行**（Q13）；
  **选项集合稳定**（改关键词或选中值后逐字不变，FR-111）；
  空结果时**筛选条整块保留**、已选值仍可点；出现空态，但**不得**出现全局清除按钮（FR-105 / FR-113 / FR-114）。
- `MallView`：**断言请求体** —— 点选一个值后发出的 `filterValueIds` **精确匹配**；
  **请求失败时高亮退回原值**、且不出现「高亮已变、列表未变」的中间态（FR-115 / Q14）；
  快速连点时以最后一次为准。
  （本项目吃过"单测只断言 UI、不断言请求体"的亏，筛选这种"改的是请求参数"的功能尤其要钉住。）

④ **管理端单元测试（本特性新接的，T246/T247）**
- `yudao-ui-admin-vue3` 此前**一个测试文件都没有**。`/speckit-analyze` 判定"新功能零测试"
  与宪法原则 I（适用范围 = **所有端**）冲突，属 CRITICAL；所有者选择**补齐**而非改宪法。
- **范围刻意压到最小**：只测本特性新增的**纯函数与一个子表单** ——
  `utils/filterTag.ts` 的 diff 函数（T274）、`FilterTagForm.vue` 的分组与剔除提示（T282）、
  `FilterDimensionDrawer.vue` 的继承段只读（T261）。
- **不借机做管理端全量测试改造**；`vite.config.ts` 一行未动，`pnpm build:local` 实测仍通过（48.56s）。

⑤ **手工清单**（[quickstart.md](./quickstart.md)）—— 自动化测不到的：真实数据的观感、
三个断点的版式、**运营从零配一遍的真实耗时**（这是 R3 的判据）、后台破坏性操作。

### e2e：本期**不加**

现有 e2e 需要本地后端 + 开发库。本特性的 e2e 前置要构造"分类 + 维度 + 可选值 + 商品标签"
四层数据，**构造成本高于它能带来的信息** —— 而筛选语义的正确性已由服务端集成测试覆盖。
**不新增 e2e**（本条是显式决定，不是遗漏）。

### 明确不做的

- **不为"能看见效果"造数据**：不预填维度、不预填标签（R12，沿用 002 的 Q6 口径）。
  **也不照抄老站的「1英寸/半英寸」** —— 那两种写法在现有数据里根本不存在，照搬等于编造（R12）。
- **不做筛选条件的 URL 同步 / 分享 / 刷新保持**（quickstart §5）。
- **不做并发控制**（已有后台行为，本次不引入乐观锁）。
- **不做筛选查询的性能优化与缓存**（本期量级不需要）。
- **不改 `product_property` 一族**（R1）。
- **不动小程序 / App 端**。
- **不在商品详情页展示筛选标签**。

## 主要风险与对策

| # | 风险 | 对策 |
|---|---|---|
| **R1** | **选项集合被"顺手"收敛掉** —— "没有商品的选项就不显示"是电商里极常见的做法，实现时很容易觉得那样更好而自作主张加上（可能在前端、也可能在服务端画蛇添足加一块计数）。⚠️ **它不报错**：现象是点了「110」之后同行其它宽度消失、或者干脆选不了第二个值，**最容易被当成"设计如此"放过** | Q11=A 是**明确决策**（spec 的 Clarifications 与 FR-111），实现时**不得**自行加计数或收敛。前端组件测试断言"改关键词/选中值后选项列表**逐字不变**"；[quickstart.md](./quickstart.md) **§2.1 的抄录比对**是现场判据（抄一次、比对两次）。研究里 R6 把 Q11=B 那个版本的陷阱完整留了档 —— 万一将来真要做计数，先读它 |
| **R2** | **改既有 `/product/spu/page`** —— 它是全局共享的分页接口，改错会波及所有商品列表 | 参数**可选**、空值**一条条件都不追加**；单测显式断言"不传时与改动前一致"；排序那段自定义 SQL（销量表达式）**一行不碰** |
| **R3** | **前置录入量让功能形同虚设** —— 碳带 54 件 × 3 维度 ≈ **162 次勾选**，运营可能干脆不配，结果"上线了但没人用" | [quickstart.md](./quickstart.md) §1.1 要求**记录真实耗时**作为判据；若确实不可承受，R12 里记的"预填脚本 + 运营逐条复核"**单独立项**（本期不做，因为它有产生不可控选项集合的风险） |
| **R4** | **建表脚本漏在生产执行** → 后台保存维度直接报错 | 写进 [quickstart.md](./quickstart.md) §0 的前置核对（与 002 的字典 INSERT 同类风险） |
| **R5** | **分类祖先链在数据异常时死循环**（`parentId` 成环） | 走链时带**深度上限**，并在服务端单测里覆盖"成环"这一例 |
| **R6** | **给既有响应体加字段** → 旧前端/旧调用方 | 前端一律 `?? []` 容忍缺失；只加字段，不改既有字段语义 |
| **R7** | **改了上游文件**（分页 VO、SPU VO、管理端两个页面） | 改动压到最小（各加一个可选字段/按钮/子表单），并在提交信息里写明原因（宪法 v2.0.0 的工程建议） |
| **R8** | **筛选条与既有分类树/分类胶囊/排序控件在版式上打架** —— 商城页上方已经有四组控件了；窄屏下再叠 4 行筛选条，商品网格可能被整个挤出屏幕 | 断点走查（1100/768/480）列为 §3 的必做项，并按 **Q13** 确认"480 下页面无横滚、筛选条仍是 4 行不折行"；筛选条整块不渲染时 MUST NOT 留空白（FR-104） |
| **R10** | **乐观更新留下「静默不一致」** —— 高亮先动了、列表没跟上（请求失败、或连点竞态导致旧响应覆盖新选择）。⚠️ **不报错**，只表现为「我明明选了 110，看到的却是全部商品」 | FR-115 把规则钉死：**失败必须退回高亮**、**连点以最后一次为准**、页面不允许停在选中态与列表不一致的状态。组件测试要构造「请求失败」与「乱序返回」两种情形，不能只测顺利路径（Q14） |
| **R9** | **「分类」行与维度行重名**（参考图第 1 行就是子分类，而子分类名完全可能与某维度同名） | FR-110 要求同页不得出现两个同名行；实现时以分类导航行优先，冲突的维度行不渲染，并在后台提示 |

## Post-Design Re-check

Phase 1 设计（[data-model.md](./data-model.md) / [contracts/](./contracts/) / [research.md](./research.md)）完成后复检：

**无新增违规项，Constitution Check 结论不变**（原则 IV 的三处留痕已在设计里进一步收窄为
"加一个可选参数 / 加一个响应字段 / 加一个子表单"）。

设计阶段定案的两处：
- **R2 关联挂 SPU 而非 SKU** —— 由 data-model §1.3 固化；
- **R6 不做聚合计数**（Q11=A 改回后整块拿掉，含它的陷阱留档）—— 由 research R6 定案。

**无未解决的澄清项**（Q9 / Q10 / Q11 全部已由所有者拍板）。
