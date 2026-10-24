# 契约：地址解析模块（客户端）

> **本特性没有任何接口变更。** 服务端不加端点、不改 DTO、不改表 —— 行政区划继续走既有
> `GET /app-api/system/area/tree`（`AreaController#getAreaTree`），一字未动。
>
> 之所以仍出一份契约：本特性的对外界面是**一个客户端模块**，它同时被"解析器单测"与
> "地址弹层组件"两方依赖。把它钉成契约，两边的改动才不会互相踩。

---

## 1. 服务端接口：**无变更**（供核对，非本特性产出）

| 端点 | 用途 | 本特性 |
|---|---|---|
| `GET /app-api/system/area/tree` | 返回 34 个省节点的完整子树 | **复用，不改**。该弹层打开时本来就要拉它渲染三级联动 |
| `GET /app-api/system/area/get-by-ip` | 按 **IP** 反查归属地 | **不使用**。它要的是 IP 不是地址文本，语义不同（见 research.md R1） |

**其余 C 端接口（地址增删改查）零改动。**

---

## 2. 模块契约：`storefront/src/utils/address-parse.ts`

### 2.1 导出

```ts
import type { Area } from '@/types'

export interface ParseResult {
  province: Area | null
  city: Area | null
  district: Area | null
  detail: string
  /** 识别到的手机号（11 位，已去掉内部空格/短横）；没识别到时是空串（Q55） */
  phone: string
  /** 识别到的收件人姓名；没识别到时是空串（Q55） */
  name: string
  missing: Array<'province' | 'city' | 'district'>
  matchedLevels: number
}

/**
 * 从一段自由文本里解析出省 / 市 / 区县。
 *
 * @param text 客户粘贴或输入的整段地址文本（未归一化，函数内部自己归一化）
 * @param tree 行政区划树（省数组），来自 getAreaTree()；为空数组时直接返回"未识别"
 * @returns 解析结果。**永不抛异常**——任何异常输入都退化为 matchedLevels: 0
 */
export function parseAddress(text: string, tree: Area[]): ParseResult
```

### 2.2 依赖约束（**MUST**）

- **MUST NOT** `import` Vue 或任何组件 —— 它是纯函数，测试不需要 `mount`。
- **MUST NOT** 发起任何网络请求；树只从参数进来。
- **MUST NOT** 在模块内定义/内联任何行政区划名称表、省市清单、别名映射（FR-118 / SC-046）。
  **唯一的名称来源是入参 `tree`**。后缀剥离表（`省/市/区/县/…`）不算行政区划数据，
  它是语言层面的词法规则，允许内联。
- **MUST NOT** 抛异常。空文本、`null`/`undefined`（防御性）、树为空、文本里全是火星文 ——
  一律返回 `matchedLevels: 0` 的结果。

### 2.3 行为契约（逐条对应 FR）

| 输入 | 输出 | FR |
|---|---|---|
| `浙江省杭州市余杭区文一西路969号` + 树 | province=浙江省, city=杭州市, district=余杭区, detail=`文一西路969号`, missing=[] | FR-117 / FR-119 |
| `浙江杭州余杭区文一西路969号`（无后缀） | 同上 | FR-122 |
| `北京市朝阳区建国路88号` | province=北京市, city=北京市(110100), district=朝阳区, detail=`建国路88号` | R3 |
| `杭州市余杭区文一西路969号`（漏省份，且"杭州市"全国唯一） | province=浙江省（补出）, city=杭州市, district=余杭区 | R4 |
| `浙江省杭州市` | province + city 有值, district=null, **detail 为空串**, missing=['district'] | FR-120 |
| `某某小区3号楼2单元501` | 三个都是 null, detail=**空串**, missing=['province','city','district'], matchedLevels=**0** | FR-121 |
| `""` / `"   "` / 树为空 | matchedLevels=0 | FR-121 |
| 文本含多个省（`从浙江发到广东`） | 取**位置最靠前**的那个省 | R2 |
| `张三 18748935785 浙江省杭州市余杭区文一西路969号` | name=张三, phone=18748935785, detail=`文一西路969号` | Q55 / FR-128 |
| `浙江省杭州市余杭区文一西路969号 13800138000 张三` | 同上（姓名在号码后面也认） | Q55 |
| `张三 浙江省杭州市余杭区文一西路969号`（姓名不挨着号码） | name='' , phone='' | Q55（宁可少填） |
| `浙江省杭州市余杭区 文一西路 18748935785` | name=''（「文一西路」含地址用字）, detail=`文一西路` | Q55 |
| `张三 18748935785 某某小区3栋`（认不出区划） | matchedLevels=0, detail='', **name=张三, phone=18748935785** | Q56 |

### 2.4 不变量（可用断言表达）

1. `matchedLevels === 0` ⟺ 三个节点全 `null` **且** `detail === ''`（`name` / `phone` 不参与这个判据 —— Q56）
2. `district !== null` ⟹ `province !== null`；`city !== null` ⟹ `province !== null`
3. `matchedLevels === 3 - missing.length`
4. 输出的节点对象**必须是入参 `tree` 里的同一个对象引用**（便于组件直接拿去比 `id` 做选中），
   不得是新建的副本 —— 组件靠 `lv1.value = result.province.id` 选中下拉

---

## 3. 组件契约：`AddressFormDialog.vue` 的接法

| 事项 | 约定 |
|---|---|
| 入口 | 表单顶部一个「粘贴整段地址」输入框 + 「自动识别」按钮（Q15=A） |
| 触发 | **只在点按钮时**执行 `parseAddress`；MUST NOT 在 `detailAddress` 的 input/paste 事件里调用（FR-124） |
| 写回 | 用 `result.province.id / city?.id / district?.id` 赋给 `lv1 / lv2 / lv3`；`result.detail` 非空时写 `form.detailAddress`（Q16=A / Q17=A）；`result.name / phone` 非空时写 `form.name / form.mobile`（Q55） |
| **识别不到的层级** | **保持客户原值**，MUST NOT 清空（Q16=A；FR-120）；姓名/手机号同理 |
| 完全未识别 | `matchedLevels === 0` **且** `name`、`phone` 都为空 → **一个字段都不写**，仅提示（FR-121）；只要认出了姓名或手机号，就只写这两项（Q56） |
| 撤销 | 识别前快照 `{lv1, lv2, lv3, detailAddress, name, mobile}`，点撤销整体还原；**不碰** defaultStatus（R10 / Q55） |
| 树不可用 | `tree` 为空或 `treeError` 非空 → 识别按钮**禁用**并说明原因（R13 / US2 场景 3） |
| 既有 id | `#addrName` / `#addrMobile` / `#areaLv1..3` / `#addrDetail` / `#addrSave` **一个都不动**（e2e 依赖） |
| 既有校验 | 五字段必填与保存链路不变（FR-127） |

---

## 4. 明确不在契约内

- 不识别**寄件人、备注**里的姓名与号码（只认与手机号紧邻的那一个姓名，FR-128 / Q55）
- 不读剪贴板（FR-124）
- 不做拼音 / 繁简 / 错别字纠正（R7）
- 不产出置信度分数（data-model.md 第 3 节末）
- 不加 e2e（plan.md 测试策略）
