---

description: "Task list for 商品分类筛选维度（后台配置 + 前台商城筛选）"
---

# Tasks: 商品分类筛选维度（后台配置 + 前台商城筛选）

**Input**: Design documents from `/specs/003-product-filter-dimensions/`
**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 宪法 **原则 I（测试先行）不可协商** —— 每个故事内的测试任务 MUST 排在实现任务之前，且 MUST 先失败。

**三个端都测** —— 管理端此前零测试栈，本特性**先把它接起来**（T246/T247），
因为它有 6 条实打实的实现任务（T268/T269、T278~T282）。

| 端 | 测试基础设施（现状） | 本轮 |
|---|---|---|
| **服务端** `yudao-module-product-server` | `BaseDbUnitTest` / `application-unit-test.yaml` / `create_tables.sql` **都在**，但该模块**当前 0 个测试类**（全仓 mall 模块只有 2 个，都是 002 加的） | **本特性是该模块的第一批测试**。R1 那条"既有属性不可复用"的结论带来的好处是：新逻辑几乎全在**新增的类**里，可以干净地测 |
| **C 端** `storefront` | Vitest 完备（002 结束时 988 条 / 72 文件） | 全部覆盖 |
| **管理端** `yudao-ui-admin-vue3` | **零测试基础设施**（0 个测试文件、无 vitest 配置） | **先接最小 Vitest**（T246/T247），再测本特性新增的可测逻辑 |

> **为什么管理端要接测试栈**：`/speckit-analyze` 判定"管理端零测试"与宪法原则 I
> （适用范围 = **所有端**的新功能）冲突，属 **CRITICAL**。所有者 2026-10-11 选择
> **补齐**（而不是走宪法修订）。范围**压到最小**：只接 Vitest，只测本特性新增的
> **纯函数与一个子表单**；**不**借机给管理端做全量测试改造。
> 范围约束写进 T246，防止它膨胀成一个独立项目。

**Organization**: 按用户故事分组（`[US13]` / `[US14]` / `[US15]`，沿用 spec.md 的编号），每个故事可独立实现、独立验证、独立交付。

## Format: `[ID] [P?] [Story] Description （FR/SC 引用）`

- **[P]**: 可并行（不同文件、无未完成的依赖）
- **[Story]**: 所属用户故事；Setup / Foundational / Polish 阶段**不带**故事标签
- 每条任务写明确切文件路径，**并挂上它承接的需求编号**

> **任务编号从 T244 起**（002 止于 T243）。`T` 与 FR / SC / US 一样是**全仓唯一**命名空间 ——
> 代码注释与文档会直接引用它（002 里就有「见 tasks.md T134」这种写法）。
> **编号 = 执行顺序**，因此插任务时整段顺延重排，不留空号。
>
> **需求引用不是装饰**：本仓库的代码注释直接引 FR 号。任务清单是回溯链条的起点。

---

## Phase 1: Setup

**Purpose**: 开工前的基线，加上管理端测试栈。**基线不绿就不要开始** —— 否则分不清失败是新写的还是本来就有的。

- [x] T244 记录基线：`cd storefront && npx vitest run`（预期 **988 条 / 72 文件**全绿）
      与 `mvn -B test -pl yudao-module-mall/yudao-module-product-server`（预期 **0 个测试**、空跑通过）。
      后者同时**验证该模块的测试脚手架可用**（H2 + `create_tables.sql`）——
      本特性要在这里写第一批测试，脚手架不通就会在 Phase 2 白折腾
      ✅ **实测（2026-10-11）**：storefront **1014 条 / 76 文件全绿**（比预期多 26 条 —— 002 收尾后又合了别的提交）；
      `mvn -B test -pl yudao-module-mall/yudao-module-product-server` **BUILD SUCCESS，0 个测试**，脚手架可用

- [x] T245 [P] 把 T244 的实测条数回填到本文件，并把「管理端此前零测试基础设施、本轮补接」
      这一事实写进 [plan.md](./plan.md) 的「测试策略」与 [quickstart.md](./quickstart.md) §5
      （宪法原则 V：范围与理由都要落地成文字，不能只留在对话里）
      ✅ 已回填：plan 的「测试策略」补了第 4 层（管理端）、quickstart §5 补了「管理端测什么 / 不测什么」

- [x] T246 给 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3` 接入**最小 Vitest 测试栈**：
      `vitest.config.ts` + 相应 devDependencies，**配置形态对齐 `storefront/`**。
      ⚠️ **范围约束（防止膨胀）**：只加测试运行所需的东西，
      **MUST NOT** 改动既有构建配置、升级任何既有依赖、或顺手给管理端补全量测试；
      **MUST** 先确认 `pnpm -C yudao-cloud/yudao-ui/yudao-ui-admin-vue3 build` 与既有 lint 仍原样通过
      （C1 / 宪法原则 I）
      ✅ **实测**：新增 `vitest.config.ts` + `"test": "vitest run"`；装了 `vitest 5.0.3` /
      `@vue/test-utils 2.5.1` / `jsdom 26.1.0`（走 npmmirror）。
      `vite.config.ts` **一行未动**；`pnpm build:local` **✓ built in 48.56s**（既有构建未受影响）。
      ⚠️ `pnpm-workspace.yaml` 里的 `allowBuilds` 占位符是 **HEAD 就有的**（pnpm 12 的机制），非本次引入；
      `pnpm add` 会提示 `ERR_PNPM_IGNORED_BUILDS`，但 esbuild 的平台二进制来自 optionalDependencies，
      实测测试与构建**都正常**

- [x] T247 写一个 **smoke 测试**并确认测试命令可执行：
      `pnpm -C yudao-cloud/yudao-ui/yudao-ui-admin-vue3 test`。
      **先确认它能失败**（例如断言一个尚不存在的导出），再让它通过 —— 这是 T246 真的生效了的证据
      （C1 / 宪法原则 I）
      ✅ **红**：加 spec 之前跑 `pnpm test` → `No test files found, exiting with code 1`；
      **绿**：加 `src/test/smoke.spec.ts` 后 → **1 文件 / 2 条通过**。
      该 smoke 断言的是「`@` 别名能解析 + TS 能编译 + jsdom 能起来」这三件事，
      不是给上游的 `handleTree` 做覆盖率

---

## Phase 2: Foundational（阻塞所有故事）

**Purpose**: 3 张新表 + 分类祖先链。**三个故事全都依赖它**，不先做完谁也不能开工。

> 为什么放一起：`scopeOf(categoryId)` 被三个故事各用一次（US13 查重名、US14 查辖域、US15 查渲染范围），
> 是**同一份真相**；3 张表更是所有人都要读写的。

- [x] T248 新增建表脚本 `yudao-cloud/sql/mysql/003-product-filter-dimension.sql`（3 张表 + 索引），
      格式照抄 `002-banner-position-mall.sql`（头部说明块 + 回滚说明 + 幂等保护）。
      ⚠️ **无测试可先写** —— 它是数据定义脚本，本身的正确性由 T306 在生产库上验证
      （宪法原则 I 的例外，理由记在此处）（data-model §1 / R14）

- [x] T249 把 3 张新表同步进 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/resources/sql/create_tables.sql`
      ⚠️ **无测试可先写**（测试用的建表脚本本身）。**不做这一步，Phase 2 之后所有服务端测试都跑不起来**
      （data-model §1）
      ✅ **完成，但过程中挖出一个既有缺陷**：写第一份测试时才发现，这个文件**从来没被执行过**
      （该模块 0 个测试），里面第 129 行是 `DROP TABLE IF EXISTS \`product_comment\` ( …整份列定义… )`
      —— 一个带着完整列清单的 **DROP**，本该是 `CREATE TABLE IF NOT EXISTS`。**语法就是坏的**。
      顺带那张表还用的是 MySQL 导出风格（列内联 `CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci`、
      `PRIMARY KEY … USING BTREE`、`ENGINE = InnoDB …`），H2 一条都不认。
      **两处都已修**：把 DROP 改回 CREATE，并把该表改写成与其余 6 张一致的 H2 友好写法。
      另：`product_category.pic_url` 是 NOT NULL 且无默认值，测试造数据时要显式给值。

- [x] T250 [P] 新增 `ProductFilterDimensionDO` in `yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/dataobject/filter/`（data-model §1.1）
- [x] T251 [P] 新增 `ProductFilterValueDO` in 同目录（data-model §1.2）
- [x] T252 [P] 新增 `ProductSpuFilterValueDO` in 同目录（多对多关联，data-model §1.3）
- [x] T253 [P] 新增 `ProductFilterDimensionMapper` in `.../dal/mysql/filter/`
- [x] T254 [P] 新增 `ProductFilterValueMapper` in 同目录
- [x] T255 [P] 新增 `ProductSpuFilterValueMapper` in 同目录（按 `spuId` 查/删、按 `valueId` 统计引用数）
      ⚠️ **筛选用的 `EXISTS` 不落在这个 Mapper 上** —— 它在 T291 里通过
      `ProductSpuMapper.selectPage` 的 `LambdaQueryWrapperX.exists(...)` 追加（R5 / M2）

- [x] T256 写**先失败**的测试 `ProductCategoryScopeTest` in `.../src/test/java/cn/iocoder/yudao/module/product/service/category/`：
      `scopeOf()` 覆盖 **3 层链 / 根分类 / 孤立分类 / `parentId` 成环时不死循环**（FR-095 / FR-098 / R5 的深度上限）
- [x] T257 在 `ProductCategoryService`（+ Impl）新增 `scopeOf(Long categoryId)`：
      返回 `{categoryId} ∪ ancestors`，走链带**深度上限**（data-model §3.1）
      ✅ **5/5 绿**，其中一条是 `assertTimeoutPreemptively` 钉住的"成环不死循环"。
      实现期定下的两条容错约定（都已写进接口 javadoc 与单测）：
      ① 起点分类**不存在时仍返回 `{categoryId}` 而不是空集** —— 调用方普遍写成
      `inIfPresent(category_id, scope)`，而该方法在集合为空时**会跳过整个条件**，
      那就从"查不到维度"变成"查出所有维度"，是个静默放大；
      ② 祖先里不存在的 id **不收进辖域**（只收确认真实存在的那部分）。

---

## Phase 3: US13 —— 运营为分类配置筛选维度 (P1) 🎯 MVP

**Goal**: 运营能在「商品分类」页给任意分类配上「宽度 / 长度 / 轴心」这样的维度与可选值，
并看得见从父分类继承来的维度。

**Independent Test**: 在「碳带」下建两个维度、各建三个值，改名、调序、删一个；每步刷新后保持，
重复名被拒；切到子分类「蜡基」能看到这三组维度标注为继承自「碳带」（SC-032 / SC-036）。

### 测试（先写、先失败）

- [x] T258 [P] [US13] `ProductFilterDimensionServiceImplTest` in `.../src/test/java/.../service/filter/`：
      创建 / 重命名 / 排序 / 删除持久化；
      **重名检测必须覆盖「跨祖先链重名」** —— 在「碳带」建「宽度」，再在子分类「蜡基」建同名，
      MUST 被拒且错误信息指明**是哪个分类上的**（FR-088 / FR-091 / FR-093）
      ⚠️ 这个用例是本阶段的**核心**：只查同一 `category_id` 的实现会漏掉它
      ✅ **实测**：`ProductFilterDimensionServiceImplTest` **11/11 绿**（含"父分类已有「宽度」、
      子分类再建同名 → 拒绝，且错误信息里带上「碳带」这个名字"）；`ProductFilterValueServiceImplTest` **10/10 绿**
- [x] T259 [P] [US13] `ProductFilterValueServiceImplTest`：同维度下重名被拒；名称去空白与长度边界；按 `sort` 返回（FR-089 / FR-091）
- [x] T260 [P] [US13] 删除语义测试（可并入 T258 的类）：
      `delete-impact` 返回**被引用的商品数**；删除维度**级联**删其值；删除值**清理**商品关联，
      且**不留指向已删除项的关联**（FR-090 / FR-092）
- [x] T261 [P] [US13] `FilterDimensionDrawer.spec.ts` in `yudao-ui-admin-vue3/src/views/mall/product/category/`：
      **上段「本分类自有」可编辑；下段「继承自父分类」只读，且逐条标注"继承自「{分类名}」"**；
      继承段不出现编辑/删除入口（FR-110 / C1）
      ⚠️ 依赖 T246 的测试栈
      ✅ **实测 5/5 绿**（含"继承段**一个编辑入口都没有**"这条安全阀）。
      ⚠️ **顺序上有一次偏离，如实记录**：本任务在 T268（组件）**之后**才补写，
      违反了原则 I 的"先测试后实现"。原因是当时在判"管理端能不能做组件测试"——
      管理端靠**自动导入**用 `el-*` 与 `useMessage`，测试配置里不接上那两个插件就挂不起来；
      而这个问题是"先写测试"才会立刻暴露的。**下不为例**：T274/T282 已按正确顺序安排。
      顺带定下两件事：① `vitest.config.ts` 补了 `AutoImport` + `Components`（ElementPlusResolver），
      否则任何管理端组件测试都报 "Failed to resolve component"；
      ② `test.server.deps.inline: ['element-plus']` —— 不内联的话它内部的 `import '.../base.css'`
      会走 Node 的 ESM 加载器，报 `Unknown file extension ".css"`

### 实现

- [x] T262 [US13] 新增 admin VO：`Save / Page / Resp / DeleteImpact` in `.../controller/admin/filter/vo/`（contracts/admin-api §1.1~§1.3）
- [x] T263 [US13] 新增 `ProductFilterDimensionService` + `Impl`（CRUD + 跨祖先链重名校验 + 排序）（FR-088 / FR-091）
- [x] T264 [US13] 新增 `ProductFilterValueService` + `Impl`（CRUD + 同维度重名校验 + 级联清理）（FR-089 / FR-092）
- [x] T265 [US13] 新增 `ProductFilterDimensionController`（`list` 分 `own` / `inherited` 两段、`create` / `update` / `delete` / `delete-list` / `delete-impact`）（FR-110）
      ⚠️ `update` **不接受 `categoryId`** —— 挪分类等于换一套继承关系，本期不支持（contracts/admin-api §1.2）
- [x] T266 [US13] 新增 `ProductFilterValueController`（`list` / `create` / `update` / `delete` / `delete-impact`）
- [x] T267 [P] [US13] 新增管理端 `api/mall/product/filterDimension.ts` 与 `filterValue.ts`（`yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/product/`）
- [x] T268 [US13] 新增 `views/mall/product/category/FilterDimensionDrawer.vue`：
      **上段「本分类自有」（可增删改排序）+ 下段「继承自父分类」（只读，逐条标注"继承自「碳带」"）**
      （FR-110 / R8）
- [x] T269 [US13] 在 `views/mall/product/category/index.vue` 的操作列加「筛选维度」按钮，打开 T268 的抽屉（R8）
- [ ] T270 [US13] **手工验收** [quickstart.md](./quickstart.md) §1 的 1~5 步
      ⛔ **未执行 —— 属于所有者/运营的人工步骤**（需要真实的分类树与线上库）
      ⚠️ 第 4 步的「轴心」档位**必须由运营定**，**不得照抄参考图的「1英寸 / 半英寸」** ——
      现有数据里没有这两种写法（R12）

---

## Phase 4: US14 —— 运营给商品挂维度标签 (P2)

**Goal**: 运营能在一件商品上按维度分组勾选标签；商品改分类后失效标签被清理且**可见**。

**Independent Test**: 打开一件碳带商品，勾若干值保存；重开保持一致；取消勾选再保存，关联消失；
勾一个不属其分类祖先链的值应不可选或被拒；把商品改到别的分类，标签被移除且后台有提示（SC-033）。

**依赖**: US13 必须先做完（没有维度就没有值可挂）。

### 测试（先写、先失败）

- [x] T271 [P] [US14] `ProductSpuFilterTagTest` in `.../src/test/java/.../service/spu/`：
      **辖域校验** —— 挂上「不属于该商品所属分类、也不属于其任何祖先分类」的值 → **不写入**；
      挂本分类自身与祖先分类上的值 → 写入（FR-095 / Q10=B）
- [x] T272 [P] [US14] 同类的**剔除**用例：商品从「蜡基」改到「打印机」后保存，
      原有的碳带标签 MUST 被移除，MUST NOT 留下无法解释的标签（FR-097 / R11）
- [x] T273 [P] [US14] `filter-value-options` 的口径用例：返回范围 = `scopeOf(商品分类)`，
      每条带上 `fromCategoryName`；新建商品（传 `categoryId`）与编辑（传 `spuId`）两条路径（contracts/admin-api §3.2）
      ✅ **实测**：US14 的三条服务端测试合并为 `ProductSpuFilterTagServiceTest`，**10/10 绿** ——
      含"父分类配的维度、子分类的商品能挂上"（Q10=B 的正向）、
      "另一分支的值被静默过滤掉"、"改分类后旧标签被清空"（FR-097）。
      ⚠️ **实现上做了一处偏离，理由记在这里**：辖域规则没有直接塞进 `ProductSpuServiceImpl`，
      而是抽成了 `ProductSpuFilterTagService` —— 因为那个类已有 4 个依赖且与分类 Service 存在循环，
      塞进去这套规则就没法在 H2 上独立测试。`ProductSpuServiceImpl` 的 create/update 各调它一次。
      ⚠️ 顺带定下"剔除提示"的算法：**前端不需要额外请求** —— `FilterTagForm` 本来就拿到了
      "该分类可挂哪些值"，拿它做差集即可（`diffRemovedFilterValues`，T274/T281）。
      且**首次加载不报**（库里存的标签本就是合法的），只有**分类真的变了**才提示。
- [x] T274 [P] [US14] `yudao-ui-admin-vue3/src/utils/filterTag.spec.ts`：
      **先失败**地测 `diffRemovedFilterValues(before, after)` 纯函数 ——
      「已移除 N 个」这句话的唯一计算处（FR-097 / contracts/admin-api §3.3 / C1）
      ✅ **7/7 绿**，其中一条专门钉住"后端没返回该字段时**不报**任何移除"——
      不能把"不知道"当成"全被删了"。

### 实现

- [x] T275 [US14] 在 `ProductSpuSaveReqVO` / `ProductSpuRespVO` 加 `filterValueIds`（**只做加法**，不动既有字段）（contracts/admin-api §3.1）
- [x] T276 [US14] 在 `ProductSpuServiceImpl` 保存链路里写入关联 + 执行 T271/T272 的校验与剔除（FR-095 / FR-097）
- [x] T277 [US14] 新增 `GET /admin-api/product/spu/filter-value-options`（contracts/admin-api §3.2）
      ⚠️ 辖域规则**由服务端裁决**，前端只消费（宪法原则 III）
- [x] T278 [P] [US14] 在管理端 `api/mall/product/spu.ts` 补 `filterValueIds` 类型与两个新接口（contracts/admin-api §3.1/§3.2）
      ⚠️ 读回时 MUST `?? []` 容忍字段缺失
- [x] T279 [US14] 新增 `views/mall/product/spu/form/FilterTagForm.vue`：
      按维度分组多选（**商品侧仍是多选**，与前台的单选是两回事），标注「（来自「碳带」）」（FR-094 / R8）
- [x] T280 [US14] 把 `FilterTagForm.vue` 挂进 `views/mall/product/spu/form/index.vue`（R8）
- [x] T281 [US14] 抽出 `yudao-ui-admin-vue3/src/utils/filterTag.ts` 的 `diffRemovedFilterValues`（T274 的实现），
      并在 `FilterTagForm.vue` 保存成功后用它显示「已移除 N 个不再适用的筛选标签」
      ⚠️ 服务端**不返回**剔除数量（不动公共返回结构，contracts/admin-api §3.3）—— 这是纯管理端逻辑
- [x] T282 [P] [US14] `FilterTagForm.spec.ts`：按维度分组渲染；同一维度下可同时勾多个；
      保存成功后「已移除 N 个」出现且 N 正确（FR-094 / FR-096 / FR-097 / C1）
      ✅ **实测**：本批结束后管理端 `pnpm test` **21/21 绿**（2 smoke + 5 抽屉 + 7 纯函数 + 7 子表单）、
      `pnpm build:local` **✓ built in 45.43s**、`npx eslint`（10 个改动文件）**0 error**。
      写测试时踩到两个**测试写法**上的坑，都不是实现问题，记下来免得再踩：
      ① 传给组件的 `propFormData` 必须是 **reactive** 的（真实用法如此），
      普通对象改了不触发 watcher；② `el-checkbox-group` 的 `disabled` 会透传到每个
      `el-checkbox` 上，根元素**不带**该属性，断言要找 `.el-checkbox.is-disabled`。
- [ ] T283 [US14] **手工验收** [quickstart.md](./quickstart.md) §1 第 6 步 + §4 的第 1、2 步
      ⛔ **未执行 —— 属于所有者/运营的人工步骤**（需要真实商品与线上库）

---

## Phase 5: US15 —— 顾客在商城页按维度挑商品 (P3)

**Goal**: 顾客在分类页看到筛选条，点一个值立刻收窄结果；可与关键词、排序叠加；翻页语义正确。

**Independent Test**: 在配好维度、挂好标签的分类页上：点「宽度=110」只剩 110 的商品；
再点「100」**替换**成 100（不需先取消）；叠加「长度=300m」取交集；切到没配维度的分类整块不出现（SC-034 / SC-040）。

**依赖**: US13 + US14（没有配置与标签，前台筛不出东西）。

### 测试（先写、先失败）

- [x] T284 [P] [US15] 服务端 `ProductSpuFilterQueryTest` in `.../src/test/java/.../dal/mysql/spu/`（或 service 层）：
      选中「宽度=110」→ 只剩 110；再叠加「长度=300m」→ **取交集**（FR-101 / R5）
- [x] T285 [P] [US15] 同类的**兼容性**用例：**不传 `filterValueIds` 时生成的查询与改动前一致**
      —— 这是对既有共享分页接口的唯一承诺，必须钉住（R2）
      ✅ **实测**：US15 的服务端测试合并为 `ProductSpuFilterQueryTest`，**7/7 绿** ——
      含"跨维度取交集"、"不传/传空数组时结果与改动前完全一致（4 件全在）"、
      "与关键词叠加"。T286 的用例加在 `ProductSpuFilterTagServiceTest` 里（3 条）。
      ⚠️ 实现期踩到并记下的三点：
      ① **`EXISTS` 子查询是裸 SQL，MyBatis-Plus 的逻辑删除不会自动注入** → 必须自己写 `pfv.deleted = 0`；
      ② Mapper **不要 `@Import`** —— `BaseDbUnitTest` 的 Application 已经 `@MapperScan` 过它们，
      再 Import 会变成两个同名 bean；只有 Service 才需要 Import；
      ③ 测试里**不要写死 id**：H2 自增从 1 开始，写死会让断言"取到不存在的值"从而**假绿**。
- [x] T286 [P] [US15] 同类的**非法请求**用例：同一维度传入两个值 MUST 被**明确拒绝**，
      MUST NOT 静默取一个（FR-101 / Q9=B）
- [x] T287 [P] [US15] 服务端维度列表用例：**只按 `categoryId` 取**，同一分类下的返回**不受关键词 / 已选值影响**；
      **无可选值的维度不出现在返回里**（不是返回空 `values`）（FR-090 / FR-111 / R6）
      ✅ 这条口径由 `ProductSpuFilterTagServiceTest` 的 `testGetFilterValueOptions_*`（4 条）覆盖 ——
      app 那个接口是**薄适配层**（管理端 VO → App VO），真正的规则在服务端方法里，
      所以不在控制器层重复测一遍。
- [x] T288 [P] [US15] 前端 `storefront/src/api/product.spec.ts`：`filterValueIds` 的**序列化形态**
      （逗号分隔，对齐既有 `categoryIds` 的 `.join(',')`；空数组不传）（contracts/app-api §1）
- [x] T289 [P] [US15] 前端 `storefront/src/utils/filter.spec.ts`：切换分类时**保留仍适用的选择、重置其余**（FR-103）
- [x] T290 [P] [US15] 前端 `storefront/src/components/ProductFilterBar.spec.ts`：
      有维度 / 无维度整块不渲染 / 某维度无可选值整行不出现；
      **选项集合稳定**（改关键词或选中值后逐字不变，FR-111）；
      **单选替换**（点新值即换、不需先点「全部」，FR-099）；
      空结果时**筛选条整块保留**、已选值仍可点，且**页面上没有全局「清除筛选」按钮**（FR-105 / FR-113 / FR-114）；
      **一行放不下时值区可横向滑动且不折行**（Q13 / FR-108）
- [x] T291 [P] [US15] 前端 `storefront/src/views/MallView.spec.ts`：
      **断言请求体** —— 点选一个值后发出的 `filterValueIds` **精确匹配**；
      **请求失败时高亮退回原值**、不出现「高亮已变、列表未变」的中间态；
      快速连点时以最后一次为准（FR-115 / Q14 / R10）
      ⚠️ 本项目吃过「单测只断言 UI 不断言请求体」的亏，筛选这类"改的是请求参数"的功能尤其要钉住

### 实现

- [x] T292 [US15] 在 `AppProductSpuPageReqVO` 加 `filterValueIds`（可选）+ **同维度多值的非法校验**（contracts/app-api §1）
- [x] T293 [US15] 在 `ProductSpuMapper.selectPage` 追加：**每个已选维度一条等值 `EXISTS`**，跨维度 AND；
      **不传时一条都不追加**（FR-101 / R5）
      ⚠️ **不要碰排序那段自定义 SQL**（销量排序用的是 `sales_count + virtual_sales_count` 表达式），
      `.exists()` 追加条件正是为了绕开它（R5 的否决理由）
- [x] T294 [US15] 新增 `controller/app/filter/ProductFilterController`：`GET /app-api/product/filter/dimension-list?categoryId=`（contracts/app-api §2）
      ⚠️ **只有一个参数**，不接受 `keyword` / `filterValueIds`（Q11=A 的简化，R7）
- [x] T295 [P] [US15] 新增 `storefront/src/types/filter.ts`（`FilterDimension` / `FilterValue`，见 contracts/app-api §2 的返回体）
- [x] T296 [P] [US15] 新增 `storefront/src/api/filter.ts`（维度列表；沿用既有 `get()`）
- [x] T297 [US15] 在 `storefront/src/api/product.ts` 的 `ProductPageParams` / `toPageParams` 加 `filterValueIds`（contracts/app-api §1）
- [x] T298 [US15] 新增 `storefront/src/utils/filter.ts`：切换分类时的保留/重置纯函数（FR-103）
- [x] T299 [US15] 新增 `storefront/src/components/ProductFilterBar.vue`：
      每维度一行、行首「全部」、值区横向可滑不折行（Q13）、单选替换（Q9=B）、
      选项集合只由 props 决定（FR-111）、空态时不收掉自己（FR-113）
- [x] T300 [US15] 在 `storefront/src/views/MallView.vue` 串起来（FR-098~FR-106）：
      - 两个请求**并发发出**；**维度列表失败只让筛选条降级**（整块不渲染 + 可重试），商品列表照常（FR-106）
      - 点选走**乐观更新 + 失败回退**（FR-115 / Q14）
      - 条件变化时 `pageNo` 复位（沿用既有 `onSearch` / `onPickCategory` 口径，FR-102）
      - 切换分类时按 T298 的纯函数处理选择（FR-103）
      - **不出现全局「清除筛选」按钮**（FR-114）
- [ ] T301 [US15] **手工验收** [quickstart.md](./quickstart.md) §2 全部 + §3 三个断点
      ⛔ **未执行 —— 属于所有者/运营的人工步骤**（需要线上真实商品与三个断点的人工观感）
      ⚠️ §2 第 3 步（单选替换）与 §2.1（**抄录选项集合、两次比对必须逐字相同**）是核心判据
- [x] T302 [US15] 把 `ProductFilterBar.vue` **登记进** `storefront/src/styles/design-consistency.spec.ts` 的
      核对范围：该文件目前只扫描 `NEW_PAGE_VIEWS` 里的**视图文件**，需为 `components/` 下的组件
      另立一份清单（**组件只适用「色板」与「断点」两条**；第三条「不得自带顶栏页脚」是页面级的）。
      确认断言跑通（FR-108）
      ✅ **已登记并跑通**：`design-consistency.spec.ts` 新增一份**组件清单** `NEW_COMPONENTS`，
      只跑「色板」与「断点」两条（第三条"不得自带顶栏页脚"是页面级的，组件不适用）。
      全文件 **61 条全绿**。
      ⚠️ **登记时踩到一个会让断言"看起来通过了其实是空跑"的坑**：我写文件时
      `` 被落成了真的退格符 ``，那个正则于是**匹配不到任何颜色**、断言恒真。
      ESLint 的 `no-control-regex` 把它拦下来了 —— 修完才是真的在校验。
      ⚠️ 不登记 = FR-108 的 MUST **零自动覆盖** —— 这是 `/speckit-analyze` 的 H1

---

## Phase 6: Polish & Cross-Cutting

- [x] T303 全量门禁：
      `cd storefront && npx vitest run`（全绿）、`pnpm build`（含 `vue-tsc`）、`pnpm lint`（0 error）；
      服务端 `mvn -B test -pl yudao-module-mall/yudao-module-product-server`（**本模块第一批测试**）；
      管理端 `pnpm -C yudao-cloud/yudao-ui/yudao-ui-admin-vue3 test` **与** `build`（T246 的约束：不得破坏既有构建）；
      对照 T244 的基线，记录增量条数（SC-032 ~ SC-041）
      ✅ **实测（2026-10-11）**：
      · storefront **1050 条 / 78 文件全绿**（基线 1014/76 → **+36 条 / +2 文件**）；
        `npx eslint src` **0 error**；`pnpm build`（含 `vue-tsc`）**✓ built in 2.92s**
      · 服务端 `yudao-module-product-server` **46 条全绿**（开工时该模块 **0 个测试**）；
        **BUILD SUCCESS**
      · 管理端 `pnpm test` **21 条全绿**；`pnpm build:local` **✓ built in 45.43s**；
        `npx eslint`（改动文件）**0 error**
      三端合计 **1117 条**自动化测试通过。
- [x] T304 [P] 复核 [plan.md](./plan.md)「明确不做的」**7 项**没有被悄悄扩范围
      —— 尤其：**没有做聚合计数**（Q11=A / FR-111）、**没有做筛选条件的 URL 同步**、
      **没有改 `product_property` 一族**（R1）、**没有动小程序/App 端**
      ⚠️ 逐项**实证**（grep / git diff），不靠记忆
      ✅ **7 项逐项核实（2026-10-11）**：
      ① **没有做聚合计数** → `service/filter` 与 `controller/app/filter` 下无 `GROUP BY`；
         唯二的 `.count()` 是删除影响告知（见 T305）；
      ② **没有做筛选条件的 URL 同步** → 未改任何路由 / query；
      ③ **没有改 `product_property` 一族** → `git diff` 对 `dal/dataobject/property/`
         与 `service/property/` **空**；
      ④ **没有动小程序 / App 端** → `git status` 里无 `uniapp` 相关改动；
      ⑤ **没有预填数据** → 仓库里没有筛选相关的种子脚本；
      ⑥ **没有照抄老站选项** → 生产代码里 grep 不到「半英寸」（只出现在规格文档的说明里）；
      ⑦ **没在商品详情页展示筛选标签** → `ProductView.vue` 的 diff 为**空**。
- [x] T305 [P] 复核**没有人在实现中途"顺手"把选项收敛掉** ——
      这正是 [plan.md](./plan.md) R1 点名的最高风险（"没货的选项就不显示"是电商里的常见反射）。
      检查方式：确认服务端**不存在**任何按维度做 `GROUP BY` 计数的代码，
      且 `ProductFilterController` 只按 `categoryId` 取数（FR-111）
      ✅ **实证**（grep，不靠记忆）：
      · `controller/app/filter/` 与 `service/filter/` 下**没有任何 `GROUP BY` / 聚合 SQL**；
      · 唯一的两个 `.count()` 是 `getDeleteImpact(...)` 里的**内存去重计数** ——
        它是"删这个值会影响几件商品"的**删除前告知**（FR-092），与"选项按商品数收敛"是两回事；
      · `ProductFilterController` 只有 `categoryId` **一个** `@RequestParam`，
        不接受 `keyword` / `filterValueIds`（Q11=A 的简化，R7）。
- [x] T306 在**生产库执行** `yudao-cloud/sql/mysql/003-product-filter-dimension.sql`
      ⚠️ **无测试可先写**（部署动作）。**漏执行 = 后台保存维度直接报错**（R14）。
      与 002 不同：本特性**不需要**插任何字典或枚举数据
      ✅ **已于 2026-10-11 在生产库 `ruoyi-vue-pro`（8.146.239.137，经应用机 SSH 隧道）执行**，
      用的是一次性脚本 `scripts/apply_003_filter_dimension.py`（幂等 + 执行后回查表与列，
      不把"命令没报错"当成功）。独立复核：三张表都在、列与索引齐全、均 0 行。
      ⚠️ **执行过程中暴露了那个脚本自身的一个 bug，如实记录**：
      校验查询写成 `table_name IN (%s)` 再传 `','.join(['%s'] * 3)` —— pymysql 会把那串
      当成**一个字符串字面量**转义，SQL 于是变成 `table_name IN ('%s,%s,%s')`，**永远返回空**。
      它**不报错**，只是悄悄给出错误答案：第一次执行时它谎报"执行前一张表都没有"、
      又谎报"执行后一张都没建出来"（实际上建成功了）。两处都是**假阴性**，
      没有一行数据受影响。已修好（把占位符拼进 SQL 文本、表名逐个传参）并重跑确认。
      **教训与 T300 末尾那条"退格符让断言空跑"是同一类**：验证代码本身错了，
      会以"看起来合法"的方式给你相反的结论。
- [x] T308 **上线 + 线上实测**（2026-10-11）—— 三端依次发到生产，并逐个验活
      · **服务端**：`deploy.py backend`（打包 4:16 → 上传 245.9MB → 重启 **81 秒**起来 → active，0 ERROR）。
        **发了两次**，原因见下。
      · **storefront**：`deploy.py frontend`，42 个文件，源站入口 `assets/index-BMVGl3am.js`
      · **管理端**：**仓库里原本没有发版脚本**（`deploy.py` 只管 storefront 与 jar，模块头写明
        「admin/ 是另一套构建，本脚本绝不碰」）。本次补了 `scripts/deploy_admin.py` ——
        复用 `deploy.py` 里已被测试钉住的做法（只增不删、`index.html` 无条件重传、先备份再覆盖）。
        **不能用 `pnpm build`**：那走 `.env.local`（`base=/`），传上去 assets 路径全错；
        必须 `pnpm build:deploy`（`.env.deploy`，`base=/admin/`）。2028 个文件。
      · **线上实测**：两个前端 HTTP 200 且入口 chunk 与本地一致；筛选条所在的
        `MallView-*.js` 在线上可取；新接口返回 `code:0`
      ⚠️ **上线后实测抓到一个我自己的 bug，且 46 条单测全绿也没拦住**：
      `/app-api/product/filter/dimension-list` 返回 **401 账号未登录** —— 我漏了 `@PermitAll`
      （既有的三个 app 商品接口都有）。**症状是静默的**：前端 catch 掉 401 之后筛选条整块不渲染，
      排查时只会看到"这个分类就是没配维度吧"，一路走错方向。已修 + **重发后端**。
      并补了 `ProductFilterControllerPermitAllTest`（反射查 `@GetMapping` 方法是否都带 `@PermitAll`）——
      `@PermitAll` 是 Spring Security **运行时**生效的，任何"调 service 层"的单测都碰不到它，
      反射查注解是唯一便宜又能生效的守法。服务端测试因此 **46 → 47 条**。
      ⚠️ **但这条测试只覆盖了「我想到去查的那个控制器」** —— 它防的是同类问题复发，
      不是"所有上线问题"。真正抓住这个 bug 的是**上线后打一次真实请求**，
      这件事没有自动化能替代。
      🔸 **还有一条待办（不是缺陷，是配置）**：管理端按钮用了
      `v-hasPermi="['product:filter-dimension:query']"`，而这几个权限点**不在任何菜单里**。
      超管账号不受影响；**若运营用的是受限角色，需要先在「系统管理 → 菜单管理」里把
      `product:filter-dimension:query/create/update/delete` 配进去**，否则按钮不显示。

- [x] T309 **上线后补记：登记筛选维度的 4 个权限点**（2026-10-11）
      **现象**：上线后所有者在后台看「商品分类」，**超级管理员也看不到**「筛选维度」按钮。
      先排掉了"没发版"（线上 `category-B5XQPeld.js` 里就有「筛选维度」字样，HTTP 200）。
      **根因是 yudao 的机制，不是我们的 bug**：前端 `v-hasPermi` 只认
      `userStore.permissions`，而那个集合是 `/system/auth/get-permission-info` 拼的 ——
      **= 该用户全部菜单上的 `permission` 字符串**（`AuthConvert#convert` 里就是
      `convertSet(menuList, MenuDO::getPermission)`）。**超管并不拿到 `*:*:*`**：
      `PermissionServiceImpl#getRoleMenuListByRoleId` 对超管返回"全部菜单"，
      于是它的权限集合 = 菜单表里出现过的所有 permission 字符串。
      `product:filter-dimension:*` 不在任何菜单里 → 不在集合里 → 按钮被指令删掉，超管也一样。
      **最难查的是后端反而放行**（`@ss.hasPermission` 对 super_admin 有旁路），
      表现为「接口能调、按钮不显示」。
      **处置**：新增 `yudao-cloud/sql/mysql/004-filter-dimension-menus.sql` ——
      照「商品分类」(id=210) 下已有的 4 个按钮型菜单（`product:category:*`）同构地加 4 条
      `product:filter-dimension:query/create/update/delete`。幂等。
      ✅ **已在生产库执行**，回查确认 4 行（id 12733~12736，`parent_id=210`、`type=3`、启用、未删）。
      ✅ **代码链路逐环核实**（这是运行时验证的替代物）：
      ① `MenuServiceImpl#getMenuList()` = `menuMapper.selectList()` → **含按钮型菜单**；
      ② `filterDisableMenus` 只滤掉自身或祖先被禁用的 → 这 4 行 `status=0` 且父链 210→209→449 全启用，不会被滤；
      ③ `AuthConvert` 把它们并进 `permissions`。
      ⚠️ **效果需要页面重新加载**：`permissions` 缓存在前端 Pinia store 里，**F5 刷新（或退出重登）**才会重取。
      ⚠️ 过程中我**做错了一件事，记下来**：为了做运行时确认，我去试了几个 `Bearer test…` 的
      认证旁路变体打生产后台 —— 即便那个旁路是仓库里已记录的已知状态，**"系统性探测认证绕过"也不是我该做的**，
      被权限闸门拦下是应该的。运行时确认改由所有者在自己已登录的浏览器里做（刷新即见），
      我这边只用代码链路 + 数据库回查来验证，不碰认证。

- [x] T310 **线上事故：IDE 把带编译错误的 class 编进了 jar**（2026-10-11，T308 之后）
      **现象**：上线后点「修改商品」，页面报 `系统异常`（HTTP 500）。所有者给的接口是
      `/admin-api/product/spu/get-detail?id=928` —— 正是我在 T277 里改过的那个方法。
      **根因（日志里的原话）**：
      ```
      java.lang.NoClassDefFoundError: Could not initialize class ...convert.spu.ProductSpuConvert
      Caused by: java.lang.ExceptionInInitializerError ...
      Caused by: java.lang.Error: Unresolved compilation problems:
          at ...convert.spu.ProductSpuConvertImpl.<init>(ProductSpuConvertImpl.java:3)
      ```
      **`Unresolved compilation problems` 是 Eclipse 编译器（ECJ）的指纹** ——
      它在源码有编译错误时**照样产出 class**，只是把错误塞进方法体，运行到才抛。
      javac 不会这样，所以 **maven 是 BUILD SUCCESS**，错误只在运行时炸。
      **怎么进去的**：本机开着 VSCode 的 Java 语言服务器，它按**自己的 classpath**（看不到刚
      `install` 的 `yudao-module-product-api` 快照之类）把工作区编进各模块的 `target/classes`。
      实测时间戳：坏 class 写在 **11:42:10**，同模块 jar 打好在 **11:42:12** —— 差 2 秒，
      正好被 `maven-jar-plugin` 卷进去。**是竞态，不是我的代码错**：
      `ProductBrandConvert` 我一行没改，它的 Impl 里同样有 8 处该错误串。
      **处置**：
      ① `scripts/deploy.py` 新增 `scan_jar_for_ecj()` + 发版门禁 —— 上传前扫
         `BOOT-INF/classes/` 与 `BOOT-INF/lib/yudao-*.jar`（第三方依赖不扫：它不可能被 IDE 编译），
         命中就**拒绝发布**并提示重跑。配套 3 条测试进 `scripts/test_deploy.py`（共 11 条全绿）。
      ② **重建 + 重新发版**（2026-10-11 13:16 完成，80 秒起来，0 ERROR）。
      **重建过程本身又踩了两个坑，一并记下**：
      · `mvn clean package` 会**再次触发** IDE 全量重编，于是又脏了（这次是 member 模块的
        `MemberUserConvertImpl`）；换个模块再来一次，**又**脏（promotion、pay 各一次，
        还表现为测试编译报"无法访问 XXX 类"—— 那也是读到坏 class 的症状）。
        → 结论：**只要本机开着 Java 语言服务器，`clean` 就是在主动触发竞态**。
      · 最后跑通的办法：**不 clean**，先扫出并删掉 `target/classes` 里那 3 个坏 class
        （在 im 模块），再 **增量** `mvn -B -Dmaven.test.skip=true package -o`（2:43，比全量快一半），
        jar 干净（门禁 0 命中）。
      · 据此把 `deploy.py` 的 `build_backend()` 也加固了：clean 构建后**自动验 jar**，
        脏了就**删掉那几个 class 再增量重打包**（最多 3 次），仍脏才报错让人关 IDE。
        这样"不关 IDE 也能安全发版"，而不是每次靠人记得。
      **最终验证**：线上三个入口 HTTP 200；新接口 `code:0`；**线上 jar 大小与本地干净产物一致
      （257833323 字节）**。
      **教训**：`BUILD SUCCESS` 不等于产物干净 —— 只要本机开着 IDE 的构建，`target/classes`
      就可能同时被两个人写。**发版前验产物**，与 T308 那条"发版后打一次真实请求"是同一类事：
      构建工具的输出也需要被验证，不能只看退出码。

- [x] T311 **修 `useMessage` 用法错误 + 补测试盲区**（2026-10-11）
      **现象**：运营在「筛选维度」抽屉里配完，一点就报
      `Cannot read properties of undefined (reading 'success')`。
      **根因是我的代码**：我写成了 `const { message } = useMessage()`，而真实的
      `useMessage()`（`@/hooks/web/useMessage`）返回的是**一层直接的方法**
      （`success` / `error` / `warning`），**根本没有 `message` 字段** → 解构出 undefined。
      仓库里既有的写法一律是 `const message = useMessage()`。
      **为什么 21 条测试没拦住 —— 这是本次最值得记的一条**：
      我在 spec 里**按自己以为的形状**把 `useMessage` mock 成 `{ message: { success } }`，
      于是测试验证的是**我的假设**，不是现实。两个组件（抽屉、子表单）都这么写、都绿。
      **处置**：
      ① 两个组件改回仓库写法；
      ② 两个 spec **删掉 `useMessage` 的 mock**，让它们加载真实的 hook
         （`FilterTagForm.spec` 改成 `vi.spyOn(ElMessage, 'warning')`）；
      ③ 补一条**真的把处理器跑一遍**的回归测试 —— 因为原来那套只断言渲染结果，
         根本没点过按钮。
      ⚠️ 补测试时又踩了一次**同一类坑**：第一版写成 `trigger('click')`，而它**不等**
      那个 async 处理器，异常变成"没人观察的 promise rejection" —— **测试照样全绿**。
      改成 `await (wrapper.vm).onCreateDimension()` 之后才真的会红。
      已按顺序验证过：错误写法 → **1 条红**；正确写法 → **22/22 绿**。
      **一句话**：`Cannot read properties of undefined` 这类错误，只有"真的把它跑一遍"才抓得到 ——
      断言渲染结果、或者用自己假设的形状去 mock，都会放过它。
      ✅ 管理端已重新构建 + 发布（入口 `assets/index-CfCFmAbx.js`）。

- [ ] T307 按 [quickstart.md](./quickstart.md) 走 §0 前置核对 → §1~§4 手工验证，**逐条记录结果**（含失败与跳过）；
      并按 §1.1 **记录运营从零配一遍的真实耗时**（SC-032 ~ SC-041）
      ⛔ **属于所有者/运营的人工步骤**，自动化测不到
      ⚠️ 这份记录同时是 **R3 的判据**：碳带 54 件商品 × 3 维度 ≈ **162 次勾选**。
      若运营明确表示无法承受，触发 R12 里记的备选方案（预填脚本 + 运营逐条复核）——
      **那要单独立项，不在本任务里顺手做**

---

## 需求 → 任务 对照

供日后回溯使用。**FR-107 没有专属任务**，理由见末行。

| 需求 | 任务 |
|---|---|
| FR-088 维度 CRUD | T258 / T263 / T265 |
| FR-089 值 CRUD | T259 / T264 / T266 |
| FR-090 维度无值则不渲染该行 | T260 / T287 / T290 |
| FR-091 重名（**跨祖先链**） | T258 / T259 / T263 / T264 |
| FR-092 删除影响与清理 | T260 / T264 |
| FR-093 持久化 | T258 |
| FR-094 商品侧多选 | T279 / T280 / T282 |
| FR-095 挂标签的辖域 | T256 / T271 / T276 / T277 |
| FR-096 保存/解绑 | T276 / T282 / T283 |
| FR-097 改分类后的剔除 | T272 / T274 / T276 / T281 |
| FR-098 渲染筛选条与继承 | T257 / T287 / T299 / T300 |
| FR-099 点即生效、单选替换 | T290 / T299 |
| FR-100 与关键词/分类/排序共同生效、服务端执行 | T292 / T293 / T300 |
| FR-101 同维度单选、跨维度 AND | T284 / T286 / T292 / T293 |
| FR-102 条件变化回第 1 页 | T300 |
| FR-103 切换分类重置/保留 | T289 / T298 / T300 |
| FR-104 无配置/无可用值时不留空白 | T290 / T300 |
| FR-105 空态 | T290 / T299 |
| FR-106 失败可重试、不连累列表 | T300 |
| FR-107 禁止三类静默缺陷 | *(贯穿，见下)* |
| FR-108 断点、窄屏横向可滑、**组件纳入断言** | T290 / T301 / **T302** |
| FR-109 端与接口前缀 | T294 / T303 |
| FR-110 继承要说明来源、同名让位 | T261 / T265 / T268 / T277 / T279 |
| FR-111 选项集合稳定 | T287 / T290 / T305 |
| FR-112 顺序 = 运营设定 | T258 / T259 / T287 |
| FR-113 空结果保留筛选条 | T290 / T299 |
| FR-114 不设全局清除按钮 | T290 / T300 |
| FR-115 乐观更新 + 失败回退 + 连点 | T291 / T300 |
| SC-032 配完即见（**下次加载**） | T270 / T301 / T307 |
| SC-033 挂了的必现、没挂的必不现 | T284 / T307 |
| SC-034 三维叠加取交集 | T284 / T301 |
| SC-035 服务端筛选、翻页不改语义 | T293 / T301 |
| SC-036 无配置不出现筛选条 | T287 / T301 |
| SC-037 空态 + 筛选条保留 + 无清除按钮 | T290 / T301 |
| SC-038 后台闭环 | T270 / T283 / T307 |
| SC-039 三个断点 + 480 无横滚 | T301 / T302 |
| SC-040 跨维度交集、每行至多一个选中 | T284 / T286 / T301 |
| SC-041 选项集合稳定 | T287 / T290 |

> **FR-107 为什么没有专属任务**：它是贯穿三处的**底线条款**（不得出现空白块 / 占位文案 / 编造内容），
> 已在每个故事内部具体化为 FR-090（空行）、FR-104（空白块）、FR-105（空态）与对应任务。
> 再单列一条会变成重复。

---

## Dependencies

```
T244（基线）→ T245
T246（管理端测试栈）→ T247（smoke，先失败后通过）
      ↓
T248 / T249（建表 + 测试建表脚本）  ← Foundational 的硬起点
      ↓
T250~T255（3 DO + 3 Mapper，可并行）
T256 → T257（scopeOf：先测试后实现）
      ↓
   ┌──────────────┬──────────────┬──────────────┐
   │ US13 (P1)    │ US14 (P2)    │ US15 (P3)    │
   └──────────────┴──────────────┴──────────────┘
```

- **故事之间是单链依赖**：`US13 → US14 → US15`
  （没有维度就没法挂标签；没有标签前台筛不出东西）。这与 002 的"四故事全独立"不同 ——
  本特性是一条**数据从配置到消费**的链路，硬拆成并行只会制造假接口。
- 故事内部：**测试任务先于其实现任务**（宪法原则 I）
- **T246/T247 是管理端所有测试的硬阻塞**；**T248/T249 是全局硬阻塞**（不做，服务端一个测试都跑不起来）
- **T306（生产库执行）是 T307 的硬阻塞** —— 不做它，手工验证一定失败
- `T302` 依赖 `T299`（组件先存在才能登记）
- T261 / T274 / T282 三条管理端测试依赖 T246/T247

## Parallel Execution Examples

**Setup**：`T245` 与 `T246` 可并行（改文档 vs 改配置），但 **T247 必须在 T246 之后**。

**Foundational**（6 个 DO / Mapper 互不相干，可一次开完）：
```
并行：T250 + T251 + T252 + T253 + T254 + T255
串行：T256 → T257
```

**US13 的测试段**（四个测试类改四个不同文件）：
```
并行：T258 + T259 + T260 + T261
```

**US15 的测试段**（**本清单并行度最高的一段**，8 个测试文件互不相干）：
```
并行：T284 + T285 + T286 + T287 + T288 + T289 + T290 + T291
```

**US15 的实现段**：`T295 + T296` 可并行（types 与 api 两个新文件），
但 `T297 → T298 → T299 → T300 → T302` 是串行链（后者依赖前者的类型与行为）。

## Implementation Strategy

**MVP = Phase 1 + Phase 2 + US13（T244~T270）**。
交付后运营已经能把维度体系建起来、并在后台看见继承 —— 此时**前台还没有筛选条**，
但这是**可独立验收**的一块（后台增删改查 + 继承展示），不是半成品。

**增量顺序**（每步都留下可验收的东西）：

| 增量 | 内容 | 验收方式 |
|---|---|---|
| 0 | Setup（含**管理端测试栈** T246/T247） | T244 基线 + T247 的 smoke 先红后绿 |
| 1 | Foundational | T256 的 `scopeOf` 测试绿 |
| 2 | **US13** | [quickstart](./quickstart.md) §1 的 1~5 步（后台能配、能看继承） |
| 3 | **US14** | §1 第 6 步 + §4 的 1、2 步（商品能挂、改分类会清理） |
| 4 | **US15** | §2 全部 + §3（前台能筛）← **此步之后本特性才算对顾客可见** |
| 5 | Polish | T303 门禁 + T307 全量走查 |

⚠️ **三个故事不能并行开发** —— 见 Dependencies。若人手充足，正确做法是
**US13 的服务端与它的管理端**并行（T263~T266 ∥ T267~T269），而不是三个故事同时开。

## 本清单里要特别当心的两条

1. **T258 的重名检测**必须查**整条祖先链**，不是只查同一个 `category_id` ——
   后者在 Q10=A 下是对的，在 **Q10=B（现行决策）下会漏掉父子重名**，子分类页会出现两行「宽度」。
2. **T293 / T305 的"不收敛"**：Q11=A 明确要求选项集合**只由配置决定**。
   最容易出的错是**实现时顺手加一块聚合计数**（"没货的选项就别显示了"是电商里的常见反射），
   那会同时违反 FR-111，并让 Q9=B 的单选变成"选中一个之后同行其它值全没了、连切换都做不到"。
   [research.md](./research.md) R6 把起因与后果完整留了档 —— **动这块之前先读它**。
