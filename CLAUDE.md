<!-- SPECKIT START -->
读当前计划之前，先读项目宪法（最高效力约定，含技术栈与四端约束）：
.specify/memory/constitution.md

For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan:
specs/010-express-track/plan.md

Supporting artifacts for the active feature:
- specs/010-express-track/spec.md — 功能规格（Q55~Q60，含两条棕地事实 E9 / E11）
- specs/010-express-track/research.md — 技术决策与被否决方案（R48~R55）
- specs/010-express-track/data-model.md — `trade_delivery_express.kd100_code` + 13 家编码回填
- specs/010-express-track/contracts/app-api.md — C 端：物流轨迹 / 列表加 `logisticsNo`
- specs/010-express-track/contracts/admin-api.md — 管理端：物流轨迹 / 快递公司编码维护
- specs/010-express-track/quickstart.md — 手工验证清单与显式不验证项
  ⚠️ **最容易踩的实现陷阱**（详见 plan 风险表）：`trade_delivery_express.code` **同时供微信小程序
  物流同步使用**，绝不能改成快递100 全拼编码 —— 快递100 用**新增的 `kd100_code`**（R48）

上一个特性（桌面端接入商城会员体系 —— 跨仓库 `maxlabel` + `maxLabelMall`）：
- specs/009-maxlabel-member-integration/{spec,plan,data-model,quickstart,tasks}.md
- specs/009-maxlabel-member-integration/contracts/{app-api,admin-api}.md

更早的特性（对公转账：客户线下打款，财务确认收款）：
- specs/008-corporate-transfer/{spec,plan,research,data-model,quickstart,tasks}.md
- specs/008-corporate-transfer/contracts/{app-api,admin-api}.md — 确认收款 / 取消订单 / 筛选
  ⚠️ 三条口径已由所有者拍板（Q41~Q46）：自动取消筛选两处（R45）、`needsNoPay` 误判（R47）

更早的特性（虚拟商品：买软件不填收货地址）：
- specs/007-virtual-goods/{spec,plan,data-model,quickstart,tasks}.md
- specs/007-virtual-goods/contracts/{app-api,admin-api}.md — 结算响应加 `deliveryType`、字典加「虚拟商品」

更早的特性（订单开票：下单申请 · 后台开具上传 · 前台下载）：
- specs/006-order-invoice/{spec,plan,research,data-model,quickstart,tasks}.md
- specs/006-order-invoice/contracts/app-api.md · contracts/admin-api.md
  ⚠️ 三处口径**已由所有者拍板**（2026-10-15，Q32~Q34）：**申请与订单同一事务**（FR-181 收窄，plan R1）、
  **部分退款不作废也不改金额**（FR-184，plan R2）、**下载失败只做到"不静默 + 求助路径"**（Edge Cases，plan R10）
  📌 之后又叠加了 **Q35~Q39**：退款撤回**已开票**的申请、抬头加**个人**类型、抬头按**票种**分开管理、
  必填收敛为「**名称 + 税号**」—— 以 spec 的 Clarifications 为准（原文保留，只作来龙去脉）

更早的特性（微信扫码登录与首次登录补全资料）：
- specs/005-wechat-scan-login/{spec,plan,research,data-model,quickstart,tasks}.md
- specs/005-wechat-scan-login/contracts/app-api.md · contracts/admin-api.md — 管理端展示 + 前置配置阻塞

更早的特性（收货地址自动解析 / 商品分类筛选维度 / 商品详情与评价 / 门面站与后台打通）：
- specs/004-address-auto-parse/{spec,plan,research,data-model,quickstart,tasks}.md
- specs/004-address-auto-parse/contracts/address-parse.md — 客户端模块契约（无接口变更）
- specs/003-product-filter-dimensions/{spec,plan,data-model,research,quickstart,tasks}.md
- specs/003-product-filter-dimensions/contracts/{app-api,admin-api}.md — admin-api 是本仓库首份
- specs/002-product-detail-reviews-banner/{spec,plan,data-model,research,quickstart,tasks}.md
- specs/002-product-detail-reviews-banner/contracts/app-api.md

更早的特性（门面站与后台打通 —— 仍是本仓库的规格基线，编号 FR/SC/US 由它起算）：
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