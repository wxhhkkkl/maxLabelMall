# Implementation Plan: 收货地址自动解析

**Branch**: `004-address-auto-parse` | **Date**: 2026-10-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-address-auto-parse/spec.md`

## Summary

C 端新增 / 编辑收货地址时，客户把**一整段地址文本**（如「浙江省杭州市余杭区文一西路 969 号」）
粘进一个专门的输入框，点「自动识别」，系统从后台给的行政区划树里定位省 / 市 / 区并选中，
剩余文本自动填进详细地址，且可一键撤销。

**技术路线：客户端纯函数解析。** 不加接口、不加表、不加依赖。行政区划继续来自既有
`/app-api/system/area/tree` —— 那棵树本来就是该弹层打开时要拉来渲染三级联动的，解析复用它。

**服务端零改动。**

## Technical Context

**Language/Version**: TypeScript 5.x + Vue 3（仅 `storefront` 端）
**Primary Dependencies**: **无新增**（见 R12：任何第三方地址解析库都自带行政区划数据，与 FR-118 / SC-046 直接冲突）
**Storage**: N/A —— 识别结果只是表单里的一次草稿，不落库、不进 URL
**Testing**: Vitest（storefront 既有设施）。纯函数表驱动 + 组件交互用例
**Target Platform**: C 端 Web 前台（现代浏览器）
**Project Type**: Web 前端（单端改动）
**Performance Goals**: 客户触发到看到结果**无可见等待**（SC-045）。纯内存字符串匹配，树约 3.6k 节点
**Constraints**: 客户端内置行政区划条目数 MUST 为 0（SC-046）；识别 MUST NOT 读剪贴板、MUST NOT 自动触发（FR-124）
**Scale/Scope**: 新增 1 个工具模块 + 其单测；改动 1 个既有组件；新增 1 个组件测试文件。不动接口 / 表 / 状态机

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 本特性如何满足 |
|---|---|
| **I 测试先行**（不可协商） | 解析器写成**不依赖 Vue 的纯函数**，先写表驱动用例见它红再实现；组件层再补交互用例。见 [测试策略](#测试策略)。 |
| **II 先澄清，不猜测** | Q15 / Q16 / Q17 已由所有者答复并写进规格的 Clarifications。另有一条由它们派生的规则（一级行政区都认不出时详细地址也不回填）最初是**规格内部推定**，经 `/speckit-analyze`（2026-10-12）判为违反本原则后**已由所有者确认**，现记为 **Q18=A**。规格中**再无任何未经确认的决策**。 |
| **III 复用既有能力** | 已逐一核实 yudao **没有**地址解析能力：`AreaController` 只有 `/system/area/tree` 与 `/get-by-ip`（按 IP 反查，不是按文本解析）。故本特性**不新建服务端能力**，而是**由前端适配既有数据**——这正是原则 III 里「若既有能力已存在但接口不足，MUST 先评估是否可由前端适配解决」那条。行政区划 MUST NOT 内置到客户端。 |
| **IV 最小改动** | 只碰 `AddressFormDialog.vue` 与新增的 utils 模块及其测试。既有五字段必填校验、保存链路、`#addrName` 等 e2e 依赖的 id **一个都不动**（FR-127）。 |
| **V 增量可验证** | 每条 FR 都落到纯函数断言或组件用例上；SC-042 用一组固定样本做**表驱动**断言，"填错条数为 0" 因而可自动度量。 |

**Gate 结论：通过。无 MUST 违规，Complexity Tracking 留空。**

## Project Structure

### Documentation (this feature)

```text
specs/004-address-auto-parse/
├── plan.md              # 本文件
├── spec.md              # 功能规格（含 Q15~Q17）
├── research.md          # Phase 0 输出：R1~R13
├── data-model.md        # Phase 1 输出：解析结果的数据形状与不变量
├── quickstart.md        # Phase 1 输出：手工验证清单与显式不验证项
├── contracts/
│   └── address-parse.md # Phase 1 输出：客户端模块契约（本特性无接口变更）
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
storefront/src/
├── utils/
│   ├── address-parse.ts          # 新增。纯函数解析器，不 import Vue
│   └── address-parse.spec.ts     # 新增。表驱动单测（先写，见它红）
└── components/
    ├── AddressFormDialog.vue     # 改动。粘贴框 + 识别按钮 + 撤销 + 提示
    └── AddressFormDialog.spec.ts # 新增。弹层交互用例（该文件此前不存在）
```

**Structure Decision**：沿用 storefront 既有约定 —— `utils/<name>.ts` 与 `<name>.spec.ts` 同目录成对
（`sanitize` / `money` / `tel` 等 14 组皆如此）。解析逻辑**不写进 `.vue`**：它需要密集的表驱动
测试，放进 SFC 就只能靠挂载组件才能断言，成本高且测不准。

## 实施分期

### Phase 1 —— 解析器（P1，US1 的全部 + US2 的一半）

1. `utils/address-parse.ts` 定义入口 `parseAddress(text, tree): ParseResult`
2. 先写 `address-parse.spec.ts`，覆盖：省市区齐全 / 缺后缀 / 直辖市 / 只到市 / 只到省 / 全国无行政区划 /
   重名区县 / 多个候选省 / 全角半角与多余空白（FR-122）
3. 再写实现，直到全绿

### Phase 2 —— 接进弹层（P1 收尾，US1 可演示）

4. `AddressFormDialog.vue` 顶部加「粘贴整段地址」输入框 + 「自动识别」按钮（FR-116 / FR-124）
5. 识别结果填进 `lv1 / lv2 / lv3` 与 `detailAddress`（FR-117 / FR-119 / FR-125）
6. 部分识别与完全失败的提示（FR-120 / FR-121）；地区树加载失败时禁用识别（US2 场景 3）

### Phase 3 —— 撤销（P3，US3）

7. 识别前快照 + 「撤销识别」按钮（FR-123）

### Phase 4 —— 组件用例与回归

8. 新增 `AddressFormDialog.spec.ts`；确认 `AccountView.spec.ts` / `CheckoutView.spec.ts` 仍绿

## 测试策略

**层 1：纯函数（主战场）** —— `address-parse.spec.ts`，表驱动。每个用例是
`{ 输入文本, 期望的省/市/区名（或"未识别"）, 期望的详细地址, 期望的缺失层级 }`。
规格 SC-042 要求"**填错的条数为 0**"，所以断言必须同时包含**不该填的**（例如全国无行政区划时
省市区都必须是空）。

**层 2：组件交互** —— `AddressFormDialog.spec.ts`（新建）。至少要钉住：
- 点识别后三个 `<select>` 的选中值与详细地址框的值
- **未识别到的层级保持客户原值**（Q16=A 的反面：不能把客户填好的区清成空）
- 完全识别不出时**一个字段都不变**（FR-121）
- 地区树加载失败时识别按钮的行为
- 撤销后回到识别前的完整状态（FR-123）
- 既有五字段校验与保存链路不受影响（FR-127 回归）

**层 3：e2e —— 本期不加。** 理由：`storefront/e2e/` 里地址相关的既有用例（`order.spec.ts`）
走的是"直接填 `#addrDetail` + 选下拉"的老路径；识别是**纯前端交互**，组件层已能完整覆盖，
再要一条 e2e 只会引入一个更慢、更脆的重复断言。

## 主要风险与对策

| 风险 | 对策 |
|---|---|
| **直辖市层级形状特殊**：北京市的市级节点**也叫「北京市」**，而重庆市的市级叫「重庆城区」 | 不用"逐级往下匹配"的写法，改为**以区县反推市级**（区县节点的 parent 即市级），见 R3 |
| **重名区县**：全国有多个「朝阳区」 | 在已定省份（必要时再限定到市）的子树内匹配；仍不唯一则**不填**（宁少不错，SC-042） |
| **客户漏写省份**（「杭州市余杭区…」） | 市名**全国唯一**时才向上补省份；不唯一则不填，见 R4 |
| **匹配到行政区划名的一部分**（如「西湖区」 vs 文本里的「西湖」） | 候选名 + 后缀剥离表，取**最早位置、同位置取最长**匹配，见 R2 |
| **把详细地址里的地名误当行政区划**（如「文一西路 969 号」里的「西路」） | 只在**完整行政区划名或其去后缀形式**上匹配，且候选来自后台树；纯后缀（区/县/市）不单独匹配 |
| 解析器写成纯函数后与组件的树结构耦合 | 契约里**只依赖 `{id, name, children}`** 三个字段（`Area` 类型已如此），见 contracts |

## Post-Design Re-check

Phase 1 产出（[research.md](./research.md) R1~R13、[data-model.md](./data-model.md)、
[contracts/address-parse.md](./contracts/address-parse.md)、[quickstart.md](./quickstart.md)）之后，
重新逐条对照宪法：

| 原则 | 设计后复核 | 结论 |
|---|---|---|
| I 测试先行 | 设计把解析**刻意做成无 Vue 依赖的纯函数**，正是为了让"先写测试见红"这件事成本最低；契约 §2.4 给了 4 条可直接写成断言的不变量 | 通过 |
| II 先澄清不猜测 | 设计中**没有**新冒出需要所有者表态的分叉。原唯一一条推定（现 Q18=A）已由所有者确认，规格中不存在未确认决策 | 通过 |
| III 复用既有能力 | contracts §1 明确"服务端接口无变更"；§2.2 把"不得内联行政区划表"写成 MUST，并说明后缀剥离表为什么不算 | 通过 |
| IV 最小改动 | 改动面收敛为 1 个新增 utils 模块 + 1 个组件；契约 §3 把 e2e 依赖的 7 个元素 id 列为"一个都不动" | 通过 |
| V 增量可验证 | Phase 1 结束即可独立演示 US1（解析器 + 接进弹层）；US3 撤销可单独验证；SC-042 的固定样本同时进了自动化与 quickstart 的对照表 | 通过 |

**无新增违规，Complexity Tracking 仍为空。** 可以进入 `/speckit-tasks`。

## 实施结果（2026-10-12 回填）

计划里写"服务端零改动、只碰 1 个 utils 模块 + 1 个组件"——**实施下来确实如此**：

| | |
|---|---|
| 新增 | `storefront/src/utils/address-parse.ts`（~230 行，纯函数）、`address-parse.spec.ts`（17 条）、`AddressFormDialog.spec.ts`（12 条，本文件此前不存在） |
| 改动 | `storefront/src/components/AddressFormDialog.vue`（+粘贴框 / +识别按钮 / +撤销 / +三种提示） |
| 未动 | 服务端一行未改；既有五字段校验与保存链路、e2e 依赖的 7 个元素 id 全部原样 |
| 测试 | 开工 1082 条 → 收尾 **1111 条全绿**，零回归；`vue-tsc` 干净；eslint 0 error |
| 依赖 | **零新增依赖**（合同 R11：任何第三方地址解析库都自带区划数据，与 SC-046 冲突） |

三处偏离与三处上游文档错误已逐条记在 [tasks.md](./tasks.md) 的「本轮实测与偏离」一节。
