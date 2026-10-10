<!-- SPECKIT START -->
读当前计划之前，先读项目宪法（最高效力约定，含技术栈与四端约束）：
.specify/memory/constitution.md

For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
specs/002-product-detail-reviews-banner/plan.md

Supporting artifacts for the active feature:
- specs/002-product-detail-reviews-banner/spec.md — 功能规格（含 4 项已确认决策）
- specs/002-product-detail-reviews-banner/research.md — 技术决策与被否决方案
- specs/002-product-detail-reviews-banner/data-model.md — 实体映射、派生数据、展示状态
- specs/002-product-detail-reviews-banner/contracts/app-api.md — 消费的 app-api 契约（含"校验由谁承担"）
- specs/002-product-detail-reviews-banner/quickstart.md — 手工验证清单与显式不验证项

上一个特性（门面站与后台打通 —— 仍是本仓库的规格基线，编号 FR/SC/US 由它起算）：
- specs/001-mall-storefront-integration/{spec,plan,data-model,research,quickstart,tasks}.md
- specs/001-mall-storefront-integration/contracts/app-api.md
<!-- SPECKIT END -->

# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.