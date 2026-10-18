---
description: "Task list for 收货地址自动解析"
---

# Tasks: 收货地址自动解析

**Input**: Design documents from `/specs/004-address-auto-parse/`
**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、
[research.md](./research.md)、[data-model.md](./data-model.md)、
[contracts/address-parse.md](./contracts/address-parse.md)、[quickstart.md](./quickstart.md)

**Tests**: 本项目宪法**原则 I（测试先行，不可协商）** 使测试任务**强制**——每个故事的测试任务
MUST 排在同故事的实现任务之前，且 MUST 先跑一次确认**失败原因正确**（不是导入错误、不是拼写错误）。

**Organization**: 按用户故事分组，每个故事可独立实现 / 独立验证 / 独立演示。

## Format: `[ID] [P?] [Story] Description （FR/SC 引用）`

- **[P]**：可并行 —— **不同文件**且不依赖未完成任务。同一文件的多个任务**一律不标 [P]**。
  ⚠️ **例外说明**：T317 / T326 / T330 三条都落在 `AddressFormDialog.spec.ts`，却都标了 [P]。
  它们分属三个**不同的故事阶段**，标 [P] 是说"**在本阶段内**与别的文件并行"；
  三个阶段之间是顺序执行，**不会并发写同一文件**。同阶段内不并行。
- **[Story]**：US1 / US2 / US3，对应 spec.md 里的三个用户故事。
- 任务编号续 003（排到 T311），本特性从 **T312** 起。

**Path Conventions**: 本特性只改 `storefront/`（C 端 Web 前台）。服务端零改动。

---

## Phase 1: Setup

**Purpose**: 开工前的基线。**基线不绿就不要开始** —— 否则分不清失败是新写的还是本来就有的。

- [x] T312 记录基线：`cd storefront && npx vitest run`，
      预期 **1082 条 / 80 文件**全绿（2026-10-12 多规格收尾时实测值）。
      后端**不在此列** —— 本特性零后端改动（plan.md 的 Technical Context）

---

## Phase 2: Foundational（阻塞所有故事）

**Purpose**: 让三个故事的测试都能 `import` 到被测对象。

**⚠️ 关键**：本阶段只建**骨架**，`parseAddress` 一律返回"未识别"。这样第一步跑测试时，
失败会落在**断言**上（`expected 浙江省, received null`）而不是**导入/编译**上 ——
原则 I 明确要求"失败原因正确"。

- [x] T313 新增 `storefront/src/utils/address-parse.ts` 的**类型与导出骨架**：
      导出 `ParseResult` 接口（字段见 [data-model.md](./data-model.md) §3 与
      [contracts](./contracts/address-parse.md) §2.1），`parseAddress(text, tree)` 先恒返回
      `{province:null, city:null, district:null, detail:'', missing:['province','city','district'], matchedLevels:0}`。
      ⚠️ **MUST NOT** `import` Vue（contracts §2.2）—— 它是纯函数，测试不需要 `mount`

**Checkpoint**: 骨架就位，三个故事可以开始（US2 / US3 需 US1 的入口存在，见 Dependencies）

---

## Phase 3: US1 —— 粘贴一整条地址，自动填好省市区 (P1) 🎯 MVP

**Goal**: 客户把整段地址粘进专门输入框、点「自动识别」，省 / 市 / 区被选中，剩余文本落进详细地址。

**Independent Test**: 打开新增地址弹层 → 粘 `浙江省杭州市余杭区文一西路969号` → 点识别
→ 三个下拉被选中、详细地址= `文一西路969号` → 直接保存成功。

### 测试（先写、先失败）

- [x] T314 [US1] `address-parse.spec.ts` in `storefront/src/utils/`：**主路径与书写差异** ——
      省市区齐全 / 无「省市区」后缀 / 全角数字与标点 / 多余空白与换行（FR-117 / FR-119 / FR-122 / R2 / R7）
      表驱动，每例断言 `province.name` / `city.name` / `district.name` / `detail`
      ⚠️ **样本量按 SC-042 定死**（SC-042 的"90%"要有分母）：**「省市区齐全」类 ≥5 条**，
      其余各类 ≥2 条，**合计 ≥20 条**。T314 + T315 + T325 三个任务加起来的用例总数 MUST 满足这个下限
- [x] T315 [US1] `address-parse.spec.ts`（同文件，故不标 [P]）：**结构难点** ——
      北京（市级节点与省级**同名**）/ 重庆（市级叫「重庆城区」，文本里不会出现）/
      漏省份「杭州市余杭区…」补省 / 市名不唯一时**不填** / 两个同名「朝阳区」收敛到正确的那个、
      收敛不了时不填（R3 / R4 / R5）
      ⚠️ 这四条是本特性的**核心风险**，见 plan.md 的「主要风险与对策」
- [x] T316 [US1] `address-parse.spec.ts`（同文件）：`ParseResult` 的 **4 条不变量**
      （[contracts](./contracts/address-parse.md) §2.4）—— 尤其"输出的节点是入参树里的**同一对象引用**"，
      组件靠它直接取 `id` 做选中
- [x] T317 [P] [US1] `AddressFormDialog.spec.ts` in `storefront/src/components/`（**新建**）：
      主路径交互 —— 点识别后三个 `<select>` 的 `value` 与 `#addrDetail` 的值；
      随后点保存能成功（US1 场景 1 / 4）
      ⚠️ **另需断言 SC-045**：识别全程**不出现任何加载态**（无 loading、按钮不被禁用、无骨架屏）。
      这是"感知为即时"唯一可自动化的判据 —— **不要**写成计时断言，毫秒级断言在 CI 上必然 flaky
      ⚠️ 该文件此前**不存在**；现有覆盖只有 `AccountView.spec.ts` / `CheckoutView.spec.ts`
      里"点按钮弹出弹层"这种很浅的层次

### 实现

- [x] T318 [US1] `parseAddress`：**归一化 + 候选名生成** ——
      全角转半角（仅 ASCII 区）、连续空白折叠、去首尾；每个节点生成「完整名 + 去后缀名」，
      后缀剥离表只含 `省/市/区/县/自治区/自治县/自治州/特别行政区/地区/盟/旗`（R2 / R7）
      ⚠️ **MUST NOT** 单独匹配后缀 —— 否则「文一西路」里的「路」「区」都会被误判
- [x] T319 [US1] `parseAddress`：**定位省 / 市 / 区** ——
      先定省（取**位置最靠前**、同位置取**最长**匹配）→ 再在省的子树里找区县 →
      **市 = 该区县节点的 parent**（这一步同时解决北京与重庆，R3）→
      区县没找到时退回在省的子树里直接找市；漏省时用"市名全国唯一"补（R4）；
      重名区县在省子树内收敛、必要时再用市收敛、仍不唯一则不填（R5）
- [x] T320 [US1] `parseAddress`：**剩余文本切分 + 组装 `ParseResult`** ——
      从归一化文本里删掉被消耗的子串、折叠空白、去首尾；
      **只在至少识别到一级行政区划时才产生剩余文本**（FR-121 / R6）；
      组装 `missing` 与 `matchedLevels`；**永不抛异常**
- [x] T321 [US1] `AddressFormDialog.vue`：表单顶部加「粘贴整段地址」输入框 + 「自动识别」按钮
      （FR-116 / FR-124）
      ⚠️ **MUST NOT** 在 `detailAddress` 的 `input`/`paste` 事件里调用解析（FR-124 / Q15=A）
      ⚠️ 既有元素 id（`#addrName` / `#addrMobile` / `#areaLv1..3` / `#addrDetail` / `#addrSave`）
      **一个都不动** —— e2e 的 `createAddress()` 依赖它们
- [x] T322 [US1] `AddressFormDialog.vue`：把识别结果写回 ——
      `lv1/lv2/lv3 = province.id / city?.id / district?.id`；`result.detail` **非空**时写 `form.detailAddress`
      （Q16=A / Q17=A）
- [x] T323 [US1] `AddressFormDialog.vue`：地区树不可用（`tree` 为空或 `treeError` 非空）时
      **识别按钮禁用**并说明原因（R13 / US2 场景 3）
      ⚠️ **不做**任何"内置兜底区划"的降级 —— 那正是 FR-118 禁止的事
- [ ] T324 [US1] **手工验收** [quickstart.md](./quickstart.md) §一 的 1~4 步

**Checkpoint**: US1 完整可用 —— 这是 MVP，可独立演示

---

## Phase 4: US2 —— 识别不全或识别不出来时，不破坏客户已填的内容 (P2)

**Goal**: 部分识别只填识别到的层级；完全识别不出时**一个字段都不改**；两种情况都给出明确提示。

**Independent Test**: 先手动选好「广东省 / 深圳市 / 南山区」，再粘一段无法识别的文本触发识别，
确认三级选择**原样保留**且出现说明。

### 测试（先写、先失败）

- [x] T325 [US2] `address-parse.spec.ts`：**未识别与部分识别** ——
      空串 / 纯空白 / 火星文 / `某某小区3号楼2单元501` → `matchedLevels === 0` 且 `detail === ''`（FR-121）；
      `浙江省杭州市`（只到市）→ `missing === ['district']` 且 **`detail` 为空**（FR-120 / R6）
      ⚠️ **另需一条「空树 + 一条看起来合法的真实地址」**：
      `parseAddress('浙江省杭州市余杭区文一西路969号', [])` → `matchedLevels === 0`。
      这是 **SC-046「客户端零内置区划」唯一的自动守卫** —— 若模块里偷偷内置了省市名清单，
      这条就会失败。只传空串的"空树"用例是**平凡成立**的，守不住任何东西
- [x] T326 [P] [US2] `AddressFormDialog.spec.ts`：**三条最容易写错的** ——
      ① 未识别到的层级**保持客户原值**（Q16=A 的反面，MUST NOT 清空）；
      ② `matchedLevels === 0` 时**一个字段都不写**（FR-121）；
      ③ 树加载失败时识别按钮禁用（R13）
      ⚠️ ① 是本阶段的核心：只填"识别到的"很容易顺手把没识别到的也置空，那是**数据破坏**

### 实现

- [x] T327 [US2] `AddressFormDialog.vue`：写回逻辑改成**只写识别到的层级**，
      未识别到的层级原值一律保留（Q16=A / FR-120）
- [x] T328 [US2] `AddressFormDialog.vue`：**三种**提示 ——
      ① 粘贴框为空 →「请先粘贴地址」；
      ② 粘了内容但完全未识别 →「未识别到行政区划，请手动选择」；
      ③ 部分识别 →「未识别到{区县 / 城市}，请手动补选」（FR-120 / FR-121）
      ⚠️ ① 与 ② MUST **分成两句**：对着一个空框说"未识别到行政区划"是误导，
      客户会以为是自己粘错了内容
      ⚠️ 文案要**说人话**，不要出现"解析失败""matchedLevels"这类内部词汇
- [ ] T329 [US2] **手工验收** [quickstart.md](./quickstart.md) §二 的 5~9 步

**Checkpoint**: US1 + US2 都能独立工作 —— 识别不准时不再有数据破坏风险

---

## Phase 5: US3 —— 识别结果可整体撤销 (P3)

**Goal**: 识别猜错时一键退回，而不是逐字段手工改回去。

**Independent Test**: 触发一次识别后点「撤销」，三级选择与详细地址回到识别前的内容。

### 测试（先写、先失败）

- [x] T330 [P] [US3] `AddressFormDialog.spec.ts`：**撤销语义** ——
      识别后撤销回到识别前；**连续两次识别只回退最近一次**（US3 场景 2）；
      撤销**不碰** 收件人 / 手机号 / 是否默认（R10）

### 实现

- [x] T331 [US3] `AddressFormDialog.vue`：识别前对 `{lv1, lv2, lv3, detailAddress}` 做快照，
      点「撤销识别」整体还原；每次识别**覆盖**上一次快照（R10 / FR-123）
      ⚠️ 快照**只含识别会碰的 4 个字段** —— 把整张表单快照进去会让"撤销"变成"回退用户手打的收件人"
- [ ] T332 [US3] **手工验收** [quickstart.md](./quickstart.md) §三 的 10~13 步

**Checkpoint**: 三个故事全部独立可用

---

## Phase 6: Polish & Cross-Cutting

- [x] T333 跑全量回归：`cd storefront && npx vitest run`，确认既有 **1082 条**一条不回归
      （FR-127 —— 五字段必填校验与保存链路一字未改）
- [x] T334 [P] 回填实测：把 T312/T333 的条数写回本文件与 [plan.md](./plan.md)；
      把"e2e 本期不加"的结论与理由落到 [quickstart.md](./quickstart.md) §六（宪法原则 V）
- [ ] T335 按 [quickstart.md](./quickstart.md) 走 §四~§六，**逐条记录结果**（含失败与跳过）；
      尤其 §四 的两条"不得回归"必须真的手点一遍
- [x] T336 发版前端：`python scripts/deploy.py frontend`，并确认源站入口 chunk 已更新
      （见 memory 里的 `server-deploy`；后端不用发）

---

## 需求 → 任务 对照

| FR | 任务 | FR | 任务 |
|---|---|---|---|
| FR-116 识别入口 | T321 | FR-123 撤销 | T330 / T331 |
| FR-117 识别三级区划 | T314 / T319 | FR-124 只显式触发 | T321 |
| FR-118 区划来自后台 | T313 / T318 | FR-125 覆盖语义 | T322 / T327 |
| FR-119 剩余文本落详细地址 | T314 / T320 / T322 | FR-126 不引第三方数据源 | T313（零依赖） |
| FR-120 部分识别 | T325 / T327 / T328 | FR-127 既有校验不变 | T333 / T335 |
| FR-121 完全未识别不动任何字段 | T325 / T326 / T327 | FR-128 不识别姓名手机号 | 结构性保证（`ParseResult` 里没这两个字段） |
| FR-122 容错书写差异 | T314 / T318 | | |

| SC | 任务 |
|---|---|
| SC-042 填错条数为 0 | T314~T316（表驱动，含"该填的是空的"断言） |
| SC-043 交互次数减少 | T317 / T324 |
| SC-044 不丢已填内容 | T326 / T329 |
| SC-045 不出现加载态 | T317（断言无 loading / 禁用 / 骨架屏）。**计时不做自动化验证**，理由与声明见 spec.md SC-045（符合原则 V） |
| SC-046 客户端零内置区划 | T313 / T318（**审查点**：模块里不得出现省市名清单） |

---

## Dependencies

### 阶段依赖

- **Setup（Phase 1）**：无依赖，可立即开始
- **Foundational（Phase 2）**：依赖 Setup —— **阻塞所有故事**
- **US1（Phase 3）**：依赖 Foundational
- **US2（Phase 4）**：依赖 **US1**（T321~T323 的入口与写回存在，才谈得上"不破坏"）
- **US3（Phase 5）**：依赖 **US1**（同上）
- **Polish（Phase 6）**：依赖三个故事完成

⚠️ **与模板的偏离，如实记录**：模板假设"三个故事都能在 Foundational 之后并行"。本特性**不成立** ——
US2 / US3 说的都是"识别这个动作出问题时怎么办"，没有 US1 的入口就无从谈起。
spec 里写的"US2 独立于 US1"指的是**价值上独立**（识别完全失效时它仍必须不添乱），
不是**依赖上独立**。**不假装它们能并行。**

### 故事内部

- 测试 MUST 先写、先跑、先失败（原则 I）
- T314 → T315 → T316 是**同一个文件**，顺序写，不并行
- T318 → T319 → T320 是**同一个文件**，顺序写，不并行
- T321 → T322 → T323 是**同一个组件文件**，顺序写，不并行

### 并行机会

- T313 与 T312 之间：T312 是记录基线，可先做
- T317 可与 T314~T316 并行（不同文件）
- T326 可与 T325 并行（不同文件）
- T330 独立成文件操作，可与其他并行
- T334 可与 T333 并行

---

## Parallel Execution Examples

```bash
# Foundational 之后，US1 的解析器测试与组件测试可同时开工：
Task: "T314 address-parse.spec.ts 主路径与书写差异"
Task: "T317 AddressFormDialog.spec.ts 主路径交互"     # 不同文件

# US2 阶段：
Task: "T325 address-parse.spec.ts 未识别与部分识别"
Task: "T326 AddressFormDialog.spec.ts 三条最容易写错的"
```

---

## Implementation Strategy

### MVP First（只做 US1）

1. Phase 1 记录基线
2. Phase 2 骨架
3. Phase 3 US1（T314~T324）
4. **停下来验证**：按 quickstart §一 手点一遍 → 可以发版演示
5. 此时已经解决了客户最初的诉求（"自动解析放到对应的省市区县位置"）

### 增量交付

1. Setup + Foundational → 骨架就位
2. US1 → 独立验证 → **发版（MVP）**
3. US2 → 独立验证 → 发版（识别不准时不再破坏数据）
4. US3 → 独立验证 → 发版（可撤销）
5. 每个故事都在不破坏前一个的前提下加价值

### 本清单里要特别当心的四条

1. **直辖市不能靠"逐级往下匹配"**（T315 / T319）。北京省级与市级**同名**、
   重庆市级叫「**重庆城区**」文本里根本不会出现 —— 必须**以区县反推市级**。
   这是最容易一次写错、且只在部分客户身上暴露的坑。
2. **"未识别到的层级保持原值"是数据安全问题**（T326 / T327）。写回时顺手把没识别到的置空，
   会把客户已选好的区县清掉 —— 比没有这个功能更糟（SC-044 要求这种事发生 0 次）。
3. **MUST NOT 在客户端内置任何行政区划名单**（T313 / T318 / SC-046）。
   名称的唯一来源是入参 `tree`。后缀剥离表（省/市/区/县…）是词法规则、不算区划数据，允许内联。
4. **地区树加载失败时不做任何降级**（T323）。禁用识别入口即可 ——
   内置一份兜底区划正是 FR-118 禁止的事。

---

## 本轮实测与偏离（T334 回填，2026-10-12）

### 实测数字

| 项 | 结果 |
|---|---|
| 开工基线（T312） | **1082 条 / 80 文件**全绿 —— 与预期一字不差 |
| 收尾全量（T333） | **1111 条 / 82 文件**全绿（+29 条：解析器 17 + 组件 12），**零回归** |
| 类型检查 `vue-tsc --noEmit` | 干净 |
| `eslint`（新增/改动文件） | **0 error**；`AddressFormDialog.vue` 的 warning 数与改动前**持平**（38） |

### 三处偏离，如实记录

**① US2 的解析器用例（T325）是*补写*的，没见过红（原则 I 相关）。**
它们覆盖的行为（空输入 / 空树 / 部分识别 / 剩余文本判空）在 T318~T320 实现解析器时就一并写好了，
所以 T325 跑起来直接全绿。原则 I 明说"补写的测试无法证明其能捕获回归"，所以另做了
**变异检查**来证伪"这些断言是摆设"：

| 注入的变异 | 结果 |
|---|---|
| 去掉"剩余文本里要有真东西"这条规则（`detail: meaningful(rest) ? rest : ''` → `detail: rest`） | **「只到市」用例立刻变红** ✅ 断言确实会咬人 |
| 去掉空输入守卫（`if (!tree.length \|\| !t)` → `if (false)`） | 全部仍绿 —— 说明这个守卫**是冗余的**（`indexOf` 本来就不会匹配空串）。无害，但记一笔 |

**② T327 与 T322 是同一次编辑完成的**，没有独立的一次"先红后绿"。
两者改的是同一个 `onParse` 里的相邻几行（写回 + "未识别到的层级保持原值"），
拆成两次编辑只会制造无意义的中间态。US2 真正独立的红-绿发生在**提示文案**那一组（T326/T328）。

**③ 组件测试的挂载方式**：`AddressFormDialog` 的地区树是在 `watch(props.open)` 里加载的
（树上没有 `immediate`），所以测试**必须**先 `open: false` 再 `setProps({ open: true })`。
直接以 `open: true` 挂载会让树永远为空、按钮永远禁用 —— 这是测试写法问题，不是组件缺陷
（真实使用里父组件也总是先关着再打开）。已写进 `AddressFormDialog.spec.ts` 的注释。

### 实现期发现并已修正的三处上游文档错误

| 位置 | 原文（错） | 实测/更正 |
|---|---|---|
| [research.md](./research.md) R4 | "全国 390 个市级节点，重名的（同名不同省）确实存在" | **假的**：市级跨省重名 **0 组**；区县才有 **47 组**。R4 的"不唯一就不填"分支在真实数据上不会触发，但仍保留（1 行，让"不猜"不依赖上游数据假设） |
| [research.md](./research.md) R7 | "全角数字**与标点**转半角" | **标点不该转**：把「（」变「(」只会让详细地址难看，对匹配毫无帮助 |
| R4 连带 | —— | 因为市名其实唯一，**区县重名才是常态**，所以 R5 是必须认真实现的一条，不是边角料 |

### 仍未做

- **T324 / T329 / T332 / T335 —— 手工验收，属所有者的人工步骤**（需要真浏览器与真实观感）
- **T336 —— 已发版**（所有者 2026-10-12 明确授权）：源站与公网入口均为 `assets/index-Dkb6Mpcy.js`；新分片 `AddressFormDialog-BJPC2UES.js` 公网 HTTP 200。回滚 = 把 `/var/www/maxlabel/index.html.bak-20261012-162722` 拷回 `index.html`
