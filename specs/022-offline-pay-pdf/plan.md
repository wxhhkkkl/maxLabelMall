# Implementation Plan: 对公转账订单导出 PDF 凭证

**Branch**: `022-offline-pay-pdf` | **Date**: 2026-11-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-offline-pay-pdf/spec.md`

## Summary

对公转账订单在门面站订单详情页提供「导出对公转账单」：把订单详情接口已有的数据
（收款账户、商品项、订单/金额信息、收货信息、开票信息、买家留言）排成 A4 HTML，
用 `html2canvas` 转成画布、`jsPDF` 写成可下载的 PDF。只改 `storefront`，不新增后端
接口与数据库变更；两个前端依赖按 Q165 引入，并在点击时才动态加载。

## Technical Context

**Language/Version**: TypeScript 6.0.3 / Vue 3.5.34（`storefront` 既有栈）

**Primary Dependencies**: 既有 axios / pinia / qrcode / crypto-js / dompurify；
**新增** `jspdf` + `html2canvas`（Q165，运行时依赖，动态 `import()` 加载）

**Storage**: N/A（不落库、不产生服务端文件）

**Testing**: Vitest 5 + @vue/test-utils 2（`storefront` 既有）；jsdom 环境；
`jspdf` / `html2canvas` 在单测中打桩

**Target Platform**: 现代浏览器（桌面 + 手机浏览器），A4 PDF（210×297mm）

**Project Type**: Web 前端（单项目：`storefront`）

**Performance Goals**: 20 项商品的订单导出在普通桌面浏览器 **≤ 3 秒**；
导出功能不进入首屏包（点击后动态加载依赖）

**Constraints**: 不改后端 / 不改库；不引入 UI 组件库；中文以图片形式渲染（Q165 已接受）；
商品图片不进入 PDF（跨域画布污染）

**Scale/Scope**: 单次导出 1 笔订单；支持 20+ 商品项自动分页；只覆盖门面站

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结论 | 依据 |
|---|---|---|
| I. 测试先行 | ✅ | 先写 `offlinePayPdf` 纯函数测试与订单详情入口测试，再实现（见 tasks） |
| II. 先澄清 | ✅ | Q165~Q167 已由所有者 2026-11-25 拍板 |
| III. 复用既有能力 | ✅ | 数据全部来自既有 `/app-api` 订单详情；复用 `formatYuan` / `formatDateTime`；不新增接口 |
| IV. 最小改动 | ✅ | 新增 1 个工具模块 + 订单详情页 1 个入口；两个依赖有明确理由（Q165）且动态加载 |
| V. 增量可验证 | ✅ | SC-217~SC-221 分别对应单测 / 组件测试 / 手工 quickstart |
| VI. 发版须所有者确认 | ✅ | 本计划不含发版动作 |

**结论**：无违规项，无需 Complexity Tracking。新增依赖属于所有者已批准的技术约束（Q165），
不构成原则偏离。

## Project Structure

### Documentation (this feature)

```text
specs/022-offline-pay-pdf/
├── plan.md              # 本文件
├── research.md          # Phase 0：技术选型与关键决策
├── data-model.md        # Phase 1：PDF 内容模型（不落库）
├── quickstart.md        # Phase 1：手工验收清单
├── contracts/
│   └── pdf-content.md   # PDF 内容契约（章节 / 字段映射 / 文件名 / 分页）
└── tasks.md             # Phase 2 输出（/speckit-tasks）
```

### Source Code (repository root)

```text
storefront/
├── package.json                      # 新增 jspdf / html2canvas
├── pnpm-lock.yaml
└── src/
    ├── utils/
    │   ├── offlinePayPdf.ts          # 新增：内容模型 + HTML 渲染 + 导出编排
    │   └── offlinePayPdf.spec.ts     # 新增：纯函数与导出编排测试
    └── views/
        ├── OrderDetailView.vue       # 对公订单增加「导出对公转账单」入口
        └── OrderDetailView.spec.ts   # 入口可见性测试
```

**Structure Decision**：单端（门面站）单项目。数据整形、HTML 生成做成纯函数放
`utils/`（可单测）；画布/PDF 只在导出函数中动态加载并调用，便于打桩。订单详情页只
增加一个入口与状态，不改既有「待收款」区块的显示条件（008 口径不受影响）。

## Complexity Tracking

无。新增依赖已由所有者拍板（Q165），且采用动态加载把首屏影响降到 0。
