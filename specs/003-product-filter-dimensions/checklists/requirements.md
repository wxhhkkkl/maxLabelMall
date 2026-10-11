# Specification Quality Checklist: 商品分类筛选维度

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-11
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

**Q9 ~ Q13 的最终结论：**

| Q | 结论 |
|---|---|
| Q9 | **B**：同维度单选（点新的即替换）、跨维度 AND |
| Q10 | **B**：维度沿分类树向下继承，配一次、子分类都能用 |
| Q11 | **A**：选项集合只由配置决定，不随条件收敛 |
| Q12 | **各条件自带清除手段，不设全局「清除筛选」按钮**（FR-114） |
| Q13 | **窄屏下每个维度一行、值区横向可滑**，不折行、不产生页面级横滚（FR-108） |
| Q14 | **点选乐观更新、失败回退**；连点以最后一次为准（FR-115） |

**五条里有三条经历过改判**（规格已按最终结论回写）：

| Q | 过程 | 最终 | 改判的代价 |
|---|---|---|---|
| Q9 | A → **B** | **B**：同维度单选、跨维度 AND | 查询从「同组 OR」简化成**等值** `EXISTS`；「同一维度传多个值」成为**非法请求**；交互从"多选"变成"点新的即替换" |
| Q10 | A → **B** | **B**：向下继承，配一次、子分类都能用 | 取消了"父分类上配维度是死配置"，新增"同一祖先链禁止同名维度" |
| Q11 | A → B → **A** | **A**：选项集合只由配置决定 | 整块**聚合计数**能力被拿掉；维度列表接口只剩 `categoryId` 一个参数 |
| Q12 | 新增（clarify 阶段补的） | **各条件自带清除手段** | 不提供全局「清除筛选」按钮（FR-114） |
| Q13 | 新增（clarify 阶段补的） | **窄屏每行横向可滑** | 行数（=高度）不随数据变；与 001 SC-008 不冲突（禁的是页面级横滚） |
| Q14 | 新增（clarify 阶段补的） | **乐观更新 + 失败回退** | 消灭「选中态与列表不一致」这一类静默缺陷（FR-115） |

**Q9 改单选的实际影响**：服务端不再需要"按维度归组后取并集"，每个已选维度一条**等值** `EXISTS` 即可；
但多出一个必须显式处理的约定 —— **同一维度传入多个值算非法请求**，服务端要明确拒绝而不是
静默取一个（否则前端的单选 bug 会表现为"筛选结果莫名其妙"，无从排查）。
交互侧：点新值即替换，**不要求顾客先点「全部」取消**。

**仍然有效的一条注意点**：Q11=B 曾引入的陷阱（计数若忘了排除本维度自身已选的值，
顾客选中一个值后同行其它值会全部消失、连切换都做不到）**现在不存在了**，但它被完整留档在
[research.md](../research.md) R6 —— 将来若要做"选项上显示商品数量"，先读那段。

**另一条仍然有效的**：**同一条分类祖先链上禁止同名维度**（FR-091）——
维度现在向下继承（Q10=B），父子重名会让子分类页出现两行「宽度」。

**留待实现阶段核实（不阻塞本规格）**：既有「商品属性」能力能否复用 ——
**已在 Phase 0 核实完毕，结论是不能**（属性是全局的、无 `categoryId`、只服务 SKU 规格，
54 件碳带商品全是"单规格 + 占位属性"）。证据与被否决方案见 [research.md](../research.md) **R1**。

**参考图口径已固化进 Assumptions**：「分类」那行是既有子分类导航，不是本特性新增；
筛选条点即生效、无确认按钮；维度是两级结构而非扁平标签；**选项不做任何计数、也不显示数字**。

---

## `/speckit-analyze` 的处置记录（2026-10-11）

分析提出 1 条 CRITICAL、2 条 HIGH、5 条 MEDIUM、3 条 LOW。**全部已处置**：

| ID | 问题 | 处置 |
|---|---|---|
| **C1** | 管理端 6 条实现任务零测试，与宪法原则 I（适用范围 = 所有端）冲突 | 所有者选 **(a) 补齐**：tasks.md 新增 **T246/T247**（给 `yudao-ui-admin-vue3` 接最小 Vitest + smoke），并给管理端补 **T261 / T274 / T282** 三条测试。**范围在 T246 里写死**：只加测试运行所需，不动既有构建、不升级依赖、不顺手做全量测试改造。现 64 条任务，三个端都有测试 |
| **H1** | FR-108 要求"通过既有视觉一致性断言"，但该断言只扫 `views/` 下的登记页面，而筛选条是 `components/` 下的组件 → MUST 零覆盖 | 改 **FR-108**：明确要求把该组件**登记进断言范围**（组件只适用色板与断点两条）；tasks.md 的 **T302** 从"确认不登记"改为"**登记并跑通**" |
| **H2** | SC-032「配置到前台可见 ≤1 分钟」不可达也不可测（前台无轮询/推送，必须重新加载） | 改 **SC-032** 为「**下一次加载该分类页**时即生效（无需发版）」，并显式写明**不承诺自动出现** |
| **M1** | research R5 的代码块仍是 Q9=A 时代的 `IN ({0})` + `valueIdsOfDimension`，与下方"等值不是 IN"的 bullet 自相矛盾 | 代码块改为等值 `= {0}`、参数改为该维度选中的单值 |
| **M2** | plan 结构树里 `ProductSpuFilterValueMapper # 筛选 EXISTS 的 @Select 落在这里` 与 R5/T291/T255 不符 | 注释改为该 Mapper 的真实职责，并注明 EXISTS 在 `ProductSpuMapper.selectPage` 里追加 |
| **M3** | FR-104 只覆盖"没配维度"，漏了"配了但每个维度都没有可选值"（会留空条）；data-model §4.1 已覆盖 | FR-104 补上该情形并指向 FR-107 |
| **M4** | FR-105 / FR-113 / FR-107 三处同义重叠（都讲空态下筛选条保留） | FR-105 只管"空态怎么显示"，并显式指向 FR-113；去掉重复句 |
| **M5** | 「不得把商品列表挤出首屏」不可度量；且没有维度数量上限 | Edge Case 改成可度量口径（高度 = 维度行数 × 固定行高，行数不随数据变）；Assumptions 里**显式声明本期不设上限**，并说明为什么现在加限制是预防性设计 |
| **L1** | spec 引用了 001/002 的 FR/SC 号（FR-004a / FR-045 / FR-077 / FR-086 / FR-087 / SC-001 / SC-008），头部未说明归属 | 头部补一句归属说明 |
| **L2** | plan 的「Phase 1/2/3」与 tasks.md 的「Phase 3/4/5」同名不同指 | plan 的「实施分期」开头写明对应关系，并声明以 tasks.md 编号为准 |
| **L3** | FR-110 说了不得两个同名维度行，没说冲突时谁让位 | FR-110 补上「以既有分类导航行优先，同名维度行不渲染，并在后台提示运营」 |

**未处置/仍遗留**：无。`/speckit-analyze` 标记的「Unmapped Tasks」为 0；FR 覆盖率 100%（28/28），
SC 覆盖率 100%（10/10）且 SC-032 已改为可测。
