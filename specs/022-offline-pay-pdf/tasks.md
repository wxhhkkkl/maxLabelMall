# Tasks: 对公转账订单导出 PDF 凭证

**Input**: Design documents from `/specs/022-offline-pay-pdf/`
**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/pdf-content.md](./contracts/pdf-content.md)

**Tests**: 本项目宪法原则 I（测试先行，不可协商）使测试为**强制** —— 每个故事先写测试并确认失败，再实现。

**Organization**: 按用户故事分组；US1 为 MVP，US2 在其之上扩展入口范围。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: US1 / US2
- 所有任务带精确文件路径

## Path Conventions

单端前端项目：`storefront/src/`、`storefront/src/**/*.spec.ts`。

---

## Phase 1: Setup（共享基础设施）

**Purpose**: 引入本特性唯一的新依赖

- [x] T001 在 `storefront/` 安装 `jspdf` 与 `html2canvas`（`pnpm add jspdf html2canvas`，必要时带 npmmirror 镜像），确认 `storefront/package.json` 与 `storefront/pnpm-lock.yaml` 更新

**Checkpoint**: 依赖可解析

---

## Phase 2: Foundational（阻塞性前置）

本特性无跨故事共享的新基础设施；既有 `formatYuan`（`storefront/src/utils/money.ts`）、
`formatDateTime`（`storefront/src/utils/time.ts`）、订单详情接口与页面均已存在。
US1 直接按测试先行的顺序开始。

---

## Phase 3: User Story 1 - 待收款时导出 PDF 去付款 (Priority: P1) 🎯 MVP

**Goal**: 对公「待收款」订单在详情页可一键下载 A4 PDF，含账户、商品、订单与金额信息。

**Independent Test**: 用 `waiting = true` 的订单渲染订单详情 → 入口可见 → 点击后
`jsPDF.save` 收到 `对公转账单_<订单号>.pdf`，内容模型含全部固定字段。

### Tests for User Story 1（宪法原则 I 要求，先写并确认失败）

- [x] T002 [P] [US1] 在 `storefront/src/utils/offlinePayPdf.spec.ts` 写失败测试：内容模型映射（`data-model.md` 全表，含**订单状态**：`waiting=true` → 待收款、已付款用 `orderStatusText`）、空值显示 `—`、金额经 `formatYuan`、时间经 `formatDateTime`、`items[].subtotal = price × count`、金额明细顺序与 7 行、开票（含 `status=20` 作废时不渲染）、留言 `trim()` 为空时不渲染
- [x] T003 [US1] 在 `storefront/src/utils/offlinePayPdf.spec.ts` 写失败测试：HTML 转义（商品名/规格/留言/抬头含 `& < > " '` 时输出转义文本）、文件名 `对公转账单_<订单号>.pdf` 且去除 `\ / : * ? " < > |`、多页参数（画布高度 > 一页时 `addPage` 次数正确）、导出编排（打桩 `jspdf` / `html2canvas`，断言 `addImage` 与 `save`）；**同 T002 同一文件，必须在 T002 之后写**
- [x] T004 [P] [US1] 在 `storefront/src/views/OrderDetailView.spec.ts` 写失败测试：`waiting = true` 的对公订单显示「导出对公转账单」入口；**已取消**（`status=40`）的对公订单不显示入口（SC-222 / Q168）；非对公订单（`offlinePay = null`）不显示入口；点击调用导出（mock `offlinePayPdf`）；导出抛错时显示失败提示且不崩溃（FR-595）
- [x] T005 [US1] 运行 `cd storefront && pnpm vitest run src/utils/offlinePayPdf.spec.ts src/views/OrderDetailView.spec.ts`，确认 T002~T004 的用例**按预期失败**（不是导入/拼写错误）

### Implementation for User Story 1

- [x] T006 [US1] 在 `storefront/src/utils/offlinePayPdf.ts` 实现内容模型 `OfflinePayPdfData`、`buildOfflinePayPdfData(order)`（含**订单状态**：`waiting ? '待收款' : orderStatusText(status)`）、`escapeHtml`、`buildFileName`（严格按 `data-model.md` 的字段与规则；开票 `status=20` 时不渲染）
- [x] T007 [US1] 在 `storefront/src/utils/offlinePayPdf.ts` 实现 `renderOfflinePayPdfHtml(data)`：A4 794px 版式、9 个章节（见 `contracts/pdf-content.md` §3）、空值 `—`、无 `<img>`
- [x] T008 [US1] 在 `storefront/src/utils/offlinePayPdf.ts` 实现 `exportOfflinePayPdf(order)`：动态 `import('html2canvas')` / `import('jspdf')`、`html2canvas({ scale: 2 })`、A4 10mm 页边距、超高循环 `addPage()`、`save(fileName)`；`finally` 移除离屏容器
- [x] T009 [US1] 在 `storefront/src/views/OrderDetailView.vue` 增加入口（`v-if="offlineWaiting && order.status !== OrderStatus.CANCELED"`）与处理函数：生成中禁用并显示「生成中…」、失败提示「生成 PDF 失败，请稍后重试」、成功后提示下载（FR-595）；样式沿用 `.od-actions` / `.btn-cart`

**Checkpoint**: US1 独立可用；测试全绿

---

## Phase 4: User Story 2 - 确认收款后导出留档 (Priority: P2)

**Goal**: 已确认收款的对公订单仍可导出同一份 PDF。

**Independent Test**: 用 `waiting = false` 的对公订单渲染详情 → 入口仍可见 → 导出成功且账户字段齐全。

### Tests for User Story 2（宪法原则 I 要求，先写并确认失败）

- [x] T010 [US2] 在 `storefront/src/views/OrderDetailView.spec.ts` 写失败测试：已确认收款（`offlinePay.waiting = false`、`status ≠ 40`）的对公订单仍显示入口；点击调用导出；页面不出现「待收款」文案（既有断言保持）

### Implementation for User Story 2

- [x] T011 [US2] 把 `storefront/src/views/OrderDetailView.vue` 的入口条件从 `offlineWaiting && status !== CANCELED` 改为 `order.offlinePay && order.status !== OrderStatus.CANCELED`（非空即为对公单，取消单仍排除），不改既有 `#offlinePay` 区块的 `v-if="offlineWaiting"`

**Checkpoint**: US1 + US2 均可独立验证

---

## Phase 5: Polish & Cross-Cutting Concerns

- [x] T012 运行 `cd storefront && pnpm test` 全量测试与 `pnpm ts:check`，修复回归
      —— 本特性用例全绿；全量仍有 3 个**既有失败**（AccountView 侧栏条目、routes 路径数、AboutView 颜色），已用 `git stash` 在 HEAD 复现，非本次引入
- [x] T013 运行 `cd storefront && pnpm lint`，确保新增文件无 error（既有 warning 不处理）—— 0 error
- [ ] T014 按 [quickstart.md](./quickstart.md) §3 在本地用真实对公订单手工验收 SC-217~SC-222（§4 的四项显式不验证）；SC-223（≤3 秒）同批计时
- [x] T015 在 `CLAUDE.md` 增加 022 特性条目：仅门面站、无 SQL/后端改动、上线顺序（只发门面站）、依赖变更说明

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **US1 (Phase 3)**: 依赖 T001
- **US2 (Phase 4)**: 依赖 US1（T009 完成后才有入口）
- **Polish (Phase 5)**: 依赖 US1 + US2

### Within Each User Story

- 测试先写（T002~T004 / T010）→ 确认失败（T005）→ 再实现（T006~T009 / T011）

### Parallel Opportunities

- T002 与 T003 同为 `offlinePayPdf.spec.ts` → **不可并行**（同文件），T002 完成后写 T003
- T002/T003（utils 测试）与 T004（view 测试）不同文件 → **可并行**
- T006/T007/T008 同文件 → 顺序执行
- T012~T015 中 T015 与 T012/T013 不同文件 → 可并行进行

## Implementation Strategy

### MVP First（仅 US1）

1. T001 安装依赖
2. T002~T005 写测试并确认失败
3. T006~T009 实现到全绿
4. 用待收款订单手工验证 → MVP 即可交付

### Incremental Delivery

1. US1：待收款可导出（MVP）
2. US2：确认收款后也可导出（一行条件 + 测试）
3. Polish：全量测试、类型检查、手工验收、文档

## Notes

- 不新增后端接口、不改数据库；无 SQL 脚本
- 不发版（宪法原则 VI）：完成后停在「已就绪、待发版」
