# Specification Quality Checklist: 赋签门面站与 yudao 后台打通

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-24
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

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`

### Validation log

**Iteration 1** — 2026-09-24

Findings and resolutions:

1. *"No implementation details"* — the Background section names `www/` and describes the draft as a static site. **Kept intentionally**: this is a fact about the artifact under change (a design draft that currently has no working behaviour), not a technology prescription. No requirement mandates a language, framework, or API. Passes.
2. *"Requirements are testable and unambiguous"* — FR-002/FR-003 were originally phrased as "支持筛选/搜索"; sharpened to distinguish **backend-wide search** from filtering the already-loaded page, because the current draft only does the latter and the two are very different deliverables. FR-026 和 FR-030 were sharpened to state observable outcomes (amounts reconcile; price change requires user confirmation) rather than internal handling. Passes.
3. *"Success criteria are technology-agnostic"* — SC-001..SC-009 只描述运营动作到用户可见的时延、流程耗时、并发正确性、金额正确率、移动端可用性，未提及任何框架、数据库或接口。Passes.
4. *"Scope is clearly bounded"* — bounded negatively in Assumptions: no admin-side build-out, no redesign of the visual draft, no multi-tenant switching, no software-licence fulfilment. Passes.
5. *"Dependencies and assumptions identified"* — external dependencies called out explicitly: SMS channel (conditional on Q2), merchant account / payment channel config (conditional on Q3), backend service reachability. Passes.
6. *"No [NEEDS CLARIFICATION] markers remain"* — **FAILS**. Three markers present (FR-047, FR-048, FR-049), all deliberately critical and all gating scope. Awaiting user decision via Q1/Q2/Q3 below. This is the expected terminal state for a first-pass specification.

**Outstanding (blocking `/speckit-plan`)**: none.

**Iteration 2** — 2026-09-24 (after Q1/Q2/Q3 answers)

All three markers resolved and the spec updated:

- **Q1 落地形态 → 组件化前端工程重写**。FR-047 (which held the marker) was removed; the decision now lives in the new 落地决策 table and is reflected in FR-046 (设计稿作为视觉基准、逐页还原) and Success Criteria SC-012. Added an explicit **本期明确不做** list to stop scope creep now that the rewrite is authorized.
- **Q2 登录方式 → 手机号 + 密码 与 手机号 + 短信验证码 双通道**。FR-010 split into FR-010 / FR-010a / FR-010b（验证码有效期、频率、错误次数）。US2 从 8 条场景扩到 10 条以覆盖第二种登录方式与验证码边界。Added SC-011（两种方式进入同一账号）and a 验证码边界 row to Edge Cases.
- **Q3 支付范围 → 仅模拟支付通道**。US5 retitled 支付闭环（模拟支付通道）并重写场景，FR-038 明确"不接入真实第三方渠道"，新增 FR-041b（支付渠道与订单状态解耦，便于后续替换）。Added SC-010（无商户号即可端到端验证）and a 支付中断 edge case. ← **2026-10-08：SC-010 已删除**（生产改为接入真实支付宝渠道，见 spec.md FR-038 / 决策表 Q3）。此处「模拟支付」改指**测试租户的 mock 渠道** —— e2e 显式点选 `[data-channel="mock"]`，生产链路改为人工验收。

Checklist now fully passes. No blocking items. Ready for `/speckit-plan`.

**Note for planning**: 规格刻意未指定前端框架版本、构建工具、状态管理库与具体接口路径——这些属于 `/speckit-plan` 的决策范畴。已确认的后端能力边界为：会员认证（密码与短信验证码）、商品与分类、购物车、订单结算与创建、订单取消、优惠券、支付（含模拟通道），均已有现成实现可用，本期重点是前端接线而非新建后端能力。


---

## 状态更新（2026-09-24，第三轮之后）

**本文件是 `/speckit-specify` 阶段的规格质量清单，其下方的校验日志记录的是当时的快照。规格此后又经过三轮 `/speckit-clarify`、一次宪法批准、一次跨产物分析与两轮对抗性验证，日志中的部分细节已经过期。**以下为已知偏差，**以 `spec.md` 为准**：

| 日志里的说法 | 当前实际 |
|---|---|
| 「新增 FR-041b（支付渠道与订单状态解耦）」 | 解耦是 **FR-041d**；FR-041b 现在是「不做用户侧确认收货」 |
| 「US2 从 8 条场景扩到 10 条」 | US2 现有 **14** 条场景 |
| 「49 条功能需求」 | 现为 **75** 条 FR（含字母变体）；成功标准 **21** 条 |
| 「路线 18 条」等由 clarify 阶段产生的数字 | 现状见 plan.md：**17 条**路由，登录为弹层不占路由 |
| 「Checklist 13 项全通过 / Ready for /speckit-plan」 | 已过时。当前的合规判定以 `.specify/memory/constitution.md` 与 [plan.md](../plan.md) 的 Constitution Check 为准 |

**本清单在当前阶段的用途**：仅作为规格早期质量的留痕。规格的可信度判定请以 [spec.md](../spec.md)（含 12 条澄清记录）、[plan.md](../plan.md)（Constitution Check + Complexity Tracking）与 [tasks.md](../tasks.md)（依赖表 + 配对表）为准。

---

**Iteration 3** — 2026-10-09（个人中心整理；规格并入本特性而非新建 `002-*`）

所有者要求：整理个人中心的功能排布、补上个人信息管理、补一些入口。

**本轮改了 spec 的哪些地方**

- 新增 **US8「个人中心：个人信息管理与入口补全」**（6 条验收场景，P2）
- 新增 **FR-011b**（个人信息管理：昵称/头像/邮箱/性别，四项一次提交；邮箱前端校验；头像需先上传取 URL；保存后顶栏与个人中心立即反映）
- 新增 **FR-011c**（我的售后：入口 + 列表 + 状态以后端为准 + 空状态）
- 新增 **FR-011d**（积分/经验/等级展示 + 明细入口；无数据如实呈现"暂无"）
- 新增 **FR-011e**（入口按主题分组：账户资料 / 我的交易 / 我的权益；子页共用侧栏）
- 新增 4 条 **Edge Cases**（资料为空、头像上传失败、邮箱边界、积分与等级无数据）
- 新增 3 条 **Success Criteria**（SC-022/023/024）
- 新增 3 条 **Assumptions**（头像需上传能力、个人信息**按需提交**、积分与等级只做展示）
- **移出「本期不做」**：「会员等级与积分体系」（本次只做展示与明细，不做等级规则/积分发放）
- 新增一个 **Clarifications** 小节记录本轮三项决策与数据事实

**逐项校验**

| 检查项 | 结果 | 说明 |
|---|---|---|
| No implementation details | ✅ | 正文只写「要什么」；接口路径与后端字段只在**假设与澄清记录**里出现（用来界定可行性边界），需求句本身不绑定实现 |
| Requirements are testable | ✅ | FR-011b/c/d/e 均可直接观测：保存后顶栏是否变、列表状态是否与订单详情一致、无数据是否为空状态 |
| Success criteria measurable | ✅ | SC-022「不刷新即生效、非法邮箱不发请求」；SC-023「状态一致率 100%」；SC-024「分组呈现 + 子页不丢菜单」 |
| Technology-agnostic SC | ✅ | 三条 SC 都不含框架/数据库/接口名 |
| Edge cases identified | ✅ | 资料为空、上传失败、邮箱格式/长度、积分与等级零数据 |
| Scope clearly bounded | ✅ | 明确「只做展示，不做等级规则与积分发放」；凭证图片上传仍不做 |
| Assumptions identified | ✅ | 头像必须走上传（`avatar` 有 `@URL` 校验）、个人信息**按需提交**、积分/等级只读 |
| No NEEDS CLARIFICATION | ✅ | 三处关键分叉（放哪 / 做到哪一步 / 补哪些入口）已在澄清阶段由所有者拍板 |

**本轮记录的两处事实，planning 时不要当成缺陷**

1. **租户 162 的积分与等级没有任何数据**（`member_level` 零配置、`member_point_record` 零记录、93 个会员积分全为 0）→ FR-011d 上线后**必然是空状态**，直到运营配等级、有积分产生。
2. **前台此前没有任何上传能力**（凭证图片就是因为这个没做）→ 头像要新增一处上传（`/app-api/infra/file/upload` + 自写控件，不引 UI 组件库）。

**顺带订正**：Assumptions 里「支付渠道：本期只用模拟支付通道」一条**已过期**（生产已接支付宝并跑通），已就地划掉并注明，避免同一文档自相矛盾。

**计数**：本轮后 FR **92** 条、SC **22** 条、US **8** 个。无遗留 `[NEEDS CLARIFICATION]`。**Ready for `/speckit-plan`。**
