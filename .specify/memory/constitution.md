<!--
Sync Impact Report
==================
Version change: 2.0.0 → 2.1.0
Bump rationale: **MINOR** —— 本次**没有改动宪法正文的任何条款**，改的是本报告：
2.0.0 那条里写着「`www/` 不得改动、`design.css` 是设计稿的逐字拷贝」仍有效，
**该说法已不成立**（设计稿基线同日被放开）。因为被改的是治理记录里对现状的陈述、
而非原则本身，故升 MINOR 而不是 MAJOR。

实际发生的事（在 **spec** 层，不是宪法层）：
  - spec.md **FR-046 修订**：不再要求逐页还原设计稿，也不再限定偏离数量；允许为
    可用性与美观做合理改进，且**无需逐处声明**
  - spec.md **SC-012 删除**：它度量的要求已取消，设计稿也已删除，无从度量
  - `www/` 目录**从仓库删除**（可回溯：git 历史 `9df135c3`）
  - `design.css` **不再是** `www/css/style.css` 的逐字拷贝；那条字节一致断言已移除
  - 视觉一致性改由 `storefront/src/styles/design-consistency.spec.ts` 的**可执行断言**
    保障（16 个新页面 × 色表/断点/不自带顶栏页脚 = 50 条），不再依赖任何外部参照物

动机（项目所有者 2026-09-27 决策）：原约束把设计稿设为不可偏离的基线，导致
「设计稿没有、但更符合使用习惯」的改进一律需要走例外条款 —— 与 2.0.0 删除上游基线
是同一类问题（**约束一旦不能被合理化地满足，就会把每个新需求都变成一次治理流程**）。

被影响的产物（已同步）：
  - specs/.../spec.md —— FR-046 改写、SC-012 删除、决策表新增 Q4、背景段修订
  - specs/.../tasks.md —— 硬约束四条 → 两条；T009/T142/T145/T151 加修订注；
    「三处冲突」重新归类（两条的冲突性质已消失）
  - specs/.../design-new-pages.md —— 改为全站唯一设计规格来源；§5 判定口径修订
  - specs/.../quickstart.md —— 作废"与设计稿比对"条目、§7 冲突表更新
  - storefront：`design-consistency.spec.ts` 删断言、`main.ts`/`SiteHeader.vue`/`mobile.spec.ts` 更新注释

**仍然有效**（本次未触碰）：不引入 UI 组件库、C 端只走 `/app-api`、管理端只走
`/admin-api`、数据库只用 MySQL、服务端本地单体启动等技术栈约束；
以及 FR-054/055/056（业务事实不得编造，必须留可辨识占位符）。

---

Version change: 1.0.0 → 2.0.0
Bump rationale: **MAJOR** —— 删除了「技术栈与架构约束」里的一条 MUST
（「yudao 是引入的上游基线，MUST 保持与其 diff 干净」）。移除 MUST 级约束属于
「移除或重定义原则等向后不兼容的治理变更」，故升 MAJOR。

动机（项目所有者 2026-09-27 决策）：该约束把 yudao-cloud 当作只读基线，但本项目的
定位是**二开**（secondary development）—— 已经需要改管理端的加载动画、首页看板、
DocAlert 提示等上游行为。每改一处都要走一次"例外条款"才合法，成本高于收益。

被影响的产物（已同步）：
  - specs/001-mall-storefront-integration/plan.md —— 约束清单与两处引用该约束的
    选址理由（原文：放在仓库根"以免污染上游基线的 diff"）
  - specs/001-mall-storefront-integration/tasks.md —— 「四条硬约束」改为三条
  - 代码：`yudao-cloud/yudao-ui/yudao-ui-admin-vue3/` 下开始出现本项目自己的改动
    （index.html 的加载动画、Home/Index.vue 的看板、.env.local 的 DocAlert 开关）

**替代做法（非约束，仅为工程建议）**：改动上游文件时，尽量把改动收敛在少量文件、
并在提交信息里写明原因 —— 因为**代价从"违规"变成了"将来升级上游时要手工对账"**。
这条不是 MUST，不参与合规审查。

**明确未受影响**（仍在效力）：
  - ~~`www/` 不得改动、`storefront/src/styles/design.css` 是 `www/css/style.css` 的
    逐字拷贝（那是**设计稿**基线，FR-046 / SC-012，与上游代码基线是两回事）~~
    → **已被 2.1.0 推翻**：设计稿基线同日放开，`www/` 已删除。当时的区分是对的
    （两者确实是两回事），但结论只维持了不到一天 —— 见上面的 2.1.0 条目。
  - 不引入 UI 组件库、C 端只走 `/app-api`、数据库只用 MySQL 等技术栈约束

---

Version change: (未填写模板) → 1.0.0
Bump rationale: 首次正式批准（MAJOR）。此前文件是含 15 处占位符的未填写模板，
本次为初次采纳，无向后兼容问题需要考量，故直接定为 1.0.0。

Modified principles:
  - [PRINCIPLE_1_NAME] → I. 测试先行（Test-First，不可协商）
  - [PRINCIPLE_2_NAME] → II. 先澄清，不猜测（Clarify Before Change）
  - [PRINCIPLE_3_NAME] → III. 复用既有能力（Reuse Over Rebuild）
  - [PRINCIPLE_4_NAME] → IV. 最小改动（Surgical Change）
  - [PRINCIPLE_5_NAME] → V. 增量可验证（Verifiable Increments）

Added sections:
  - 技术栈与架构约束（原 [SECTION_2_NAME]）
  - 开发工作流与质量门禁（原 [SECTION_3_NAME]）

Removed sections: 无

Templates requiring updates:
  - ✅ .specify/templates/tasks-template.md — 原文「Tests are OPTIONAL」与本宪法
    原则 I（测试先行，不可协商）直接冲突，已改为由宪法决定测试是否强制
  - ✅ .specify/templates/plan-template.md — Constitution Check 段为通用占位，
    无需修改（门禁内容由本文件动态决定）
  - ✅ .specify/templates/spec-template.md — 无强制测试段，无需修改
  - ✅ CLAUDE.md — SPECKIT 区块增加宪法指引
  - ⚠ 无 .specify/templates/commands/ 目录，无需处理

Follow-up TODOs: 无（所有占位符均已替换）

✅ 已解决的既存冲突（原文保留于下，状态已更新）
  本报告最初记录：specs/001-mall-storefront-integration 的测试策略与本宪法原则 I 冲突
  （该计划当时明确「端到端不做自动化、仅纯逻辑用 Vitest」，理由是仓库无测试设施 +
  「后端只能从 IDE 启动、CI 无法无头拉起 48080」）。
  后续处置：
  ① 第二条理由经 2026-09-24 实测推翻 —— `java -jar yudao-server/target/yudao-server.jar`
     可无头启动（Tomcat 绑定 48080，约 70 秒，能响应真实接口）；
  ② 项目所有者于同日选定方案 A：单元层与组件层**测试先行**，端到端只做一条冒烟且
     明确为事后护栏（理由记入 plan.md 的 Complexity Tracking）；
  ③ plan.md、tasks.md、research.md R9 已全部按方案 A 重写。**冲突已消除**。
  留痕价值：这条冲突是「依据未经核实就写进决策」的实例——错误的依据若未被验证，
  会以「项目既有约束」的口吻传播到每一份下游文档。

-->

# 赋签 MaxLabel 商城 Constitution

## Core Principles

### I. 测试先行（Test-First，不可协商）

先写测试，再写实现。每一段新功能或缺陷修复 MUST 遵循以下顺序：

1. 先写表达预期行为的测试
2. 运行并确认它**失败**（且失败原因正确，不是拼写或导入错误）
3. 再写实现，直到测试通过
4. 在重构前保持测试全绿；红-绿-重构循环 MUST 严格遵守

MUST NOT 以「先实现，通过后再补测试」的方式交付。补写的测试无法证明其能捕获
回归——因为它们从未见过失败。

**Rationale**：本项目是多端（Web 前端 / Web 管理端 / 服务端 / 小程序·App）长期
演进的项目，且大量逻辑涉及金额、库存、订单状态等一旦出错就有业务损失的领域。
测试先行是唯一能在改动前锁定既有行为的机制。

**适用范围**：所有端的新功能、缺陷修复与重构。测试类型（单元 / 契约 / 集成）与
深度由具体计划决定，但「先测试后实现」的顺序不可协商。

### II. 先澄清，不猜测（Clarify Before Change）

任何重大重构或范围变更之前，MUST 先澄清，MUST NOT 猜测。

触发澄清的门槛（满足任一即 MUST 澄清）：
- 改动会触及多于一个端（例如同时改服务端与 Web 前端）
- 改动会改变既有的数据模型、接口契约或状态机
- 存在多于一种合理解释，且不同解释导致不同的实现与验收方式
- 缺少做出决定所必需的外部信息（凭据、资质、第三方能力、业务规则）

澄清方式：把疑问显式提出来，附上候选项与各自影响。**允许停下来提问**；不允许
在不确定的情况下静默选一种做法然后继续。

此原则优先于交付速度。若一次澄清会推迟交付，仍然澄清。

**Rationale**：本项目的多数返工来自「对既有后端能力的错误假设」——例如假定存在
某个接口或字段，而它实际不存在。这类错误在实现后期才暴露时修复成本最高。

### III. 复用既有能力（Reuse Over Rebuild）

服务端 MUST 复用 yudao 既有模块与接口。MUST NOT 为已有能力新建重复实现。

- 实现前 MUST 先在服务端代码中确认该能力是否已存在（模块、服务、接口）
- 若既有能力已存在但接口不足，MUST 先评估是否可由前端适配解决，再考虑扩展服务端
- 前端 MUST NOT 自行实现本应由服务端裁决的业务规则（价格、库存、券可用性、
  订单状态、限流）；这些的权威方是服务端
- MUST NOT 在前端内置会过期的业务数据（行政区划、汇率、品类清单），一律取自服务端

**Rationale**：yudao 已提供会员、商品、交易、营销、支付等完整能力，重复实现会
产生两套真相（two sources of truth），且必然与既有能力的行为不一致。

### IV. 最小改动（Surgical Change）

只碰必须碰的。每一行改动 MUST 能直接追溯到本次需求。

- MUST NOT 顺手「改进」相邻代码、注释或格式
- MUST NOT 重构没有坏掉的东西
- MUST 沿用既有代码风格，即使个人偏好不同
- 若发现无关的死代码，MUST 指出而非自行删除
- 引入的新依赖 MUST 有明确理由；MUST NOT 为单一用途引入抽象层或「未来可扩展性」

**Rationale**：diff 越小，审查越有效、回归面越小。大型 diff 会把真正的改动淹没在
噪音里，使审查失去作用。

### V. 增量可验证（Verifiable Increments）

每项交付 MUST 有可独立验证的验收方式。

- 需求 MUST 写成可度量的形式；「快」「稳定」「友好」这类形容词 MUST 被替换为
  具体阈值或可观察行为
- 每个用户故事 MUST 能独立实现、独立验证、独立演示，不得依赖未完成的其他故事
- 计划 MUST 明确每个成功标准的验证方式（自动化测试 / 手工清单 / 明确声明不验证）
- 若某项成功标准**不打算验证**，MUST 显式写出并说明理由；MUST NOT 留下一条
  永不验收的标准充数

**Rationale**：无法验证的完成度等于没有完成度。显式声明「不验证」比伪造一个
永远不会执行的检查更诚实，也便于后续补齐。

## 技术栈与架构约束

本项目为多端结构，共四个端。技术栈为强制约定，MUST NOT 擅自替换：

| 端 | 技术栈 | 面向对象 | 接口前缀 |
|---|---|---|---|
| 服务端 | Java + yudao（`yudao-cloud`） | — | `/app-api` 与 `/admin-api` |
| Web 前端 | Vue 3 + Vite + TypeScript | C 端消费者 | **仅** `/app-api` |
| Web 管理端 | Vue 3（yudao 既有 `yudao-ui-admin-vue3`） | 运营与管理员 | **仅** `/admin-api` |
| 小程序 / App | uni-app（yudao 既有 `yudao-ui-mall-uniapp`） | C 端移动用户 | **仅** `/app-api` |

- 数据库 MUST 使用 MySQL。MUST NOT 引入第二种数据库或数据存储引擎。
- 接口前缀与端 MUST 严格对应：C 端（Web 前端、小程序/App）一律走 `/app-api`；
  管理端一律走 `/admin-api`。MUST NOT 跨用。
- 服务端本地开发形态为单体聚合启动；微服务网关形态仅在部署时使用，二者本地
  互斥。

## 开发工作流与质量门禁

工作流为：规格 → 计划 → 任务 → 实现。各阶段的命令由 spec-kit 提供
（`/speckit-specify`、`/speckit-clarify`、`/speckit-plan`、`/speckit-tasks`、
`/speckit-analyze`、`/speckit-implement`）。

质量门禁：

1. **规格门禁**：需求是否可度量、是否已澄清。存在未解决的澄清项时，MUST NOT
   进入计划阶段。
2. **计划门禁**：Constitution Check MUST 通过。任何违规 MUST 在计划的
   「Complexity Tracking」中逐条说明理由与「为何更简单的替代方案不可行」。
   MUST NOT 通过重新解释原则来规避。
3. **分析门禁**：`/speckit-analyze` MUST 在实现前运行，产出 CRITICAL 级问题时
   MUST 先解决。
4. **实现门禁**：原则 I 的测试先行顺序 MUST 被遵守；任务标记完成前，其测试
   MUST 处于通过状态。

**例外条款**：本宪法为项目最高效力约定。若某条原则确实需要偏离，只有两条合法
路径：(a) 修改计划与任务以符合原则；(b) 通过下方治理流程正式修订宪法。
**计划或任务层面无权自行豁免任何原则**，包括把「不可协商」降级为建议。

## Governance

- **效力**：本宪法优先于其他一切实践与文档。当本文件与其他文档（计划、任务、
  规格、README、既有约定）冲突时，以本文件为准，冲突方 MUST 被修正。
- **修订流程**：修订需（1）说明动机与被影响的模板/产物；（2）产出 Sync Impact
  Report；（3）按语义化版本更新版本号；（4）同步全部依赖产物。修订 MUST 显式
  记录，MUST NOT 静默进行。
- **版本策略**（语义化版本）：
  - **MAJOR**：移除或重定义原则等向后不兼容的治理变更
  - **MINOR**：新增原则或章节，或对既有指引做实质性扩展
  - **PATCH**：措辞澄清、笔误修正、非语义性润色
- **合规审查**：每次 `/speckit-plan` 与 `/speckit-analyze` MUST 校验本宪法。违反
  MUST 条款、且未走例外条款的产物 MUST 被修正后方可继续。原则 I 的违规一律视为
  CRITICAL。
- **运行期指引**：`CLAUDE.md` 承载日常行为准则与当前计划的位置指引，其内容
  MUST NOT 与本宪法冲突。

**Version**: 2.1.0 | **Ratified**: 2026-09-24 | **Last Amended**: 2026-09-27
