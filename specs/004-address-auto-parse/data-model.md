# 数据模型：收货地址自动解析

> **本特性不改动任何数据库表、任何接口 DTO、任何既有类型定义。**
> 下面三个"实体"里，只有 `ParseResult` 是本特性新增的，且它**只存在于浏览器内存中**——
> 不进 URL、不落库、刷新即失。

---

## 1. 行政区划节点（既有，只读）

来源：`GET /app-api/system/area/tree` → 服务端 `AreaNodeRespVO` → 前端 `Area` 类型。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 行政区划编号（`area.csv` 的第一列，如 `330110`）。**保存地址时落的就是这个值** |
| `name` | `string` | 名称。**都带后缀**：`浙江省` / `杭州市` / `余杭区` |
| `children?` | `Area[]` | 子节点。省 → 市 → 区县，共三层 |

实测形状（`yudao-spring-boot-starter-biz-ip/src/main/resources/area.csv`，3880 行）：

- `type 1`=国家（248）→ 其中 `id=1` 是「中国」
- `type 2`=省（**34**）← `/system/area/tree` 返回的就是这一层
- `type 3`=市（390）
- `type 4`=区县（3207）

**不变量**

- 树的根 = 34 个省，前端拿到的 `Area[]` 就是省数组。
- 名字唯一性**只在本省子树内成立**：跨省重名存在（「朝阳区」至少在北京市与长春市下各一个）。
- **直辖市的中间层没有统一规律**（实测）：
  - 北京市：省 `北京市`(110000) → 市 `北京市`(110100) → 区县 `朝阳区`(110105)（**市级与省级同名**）
  - 重庆市：省 `重庆市`(500000) → 市 `重庆城区`(500100) → 区县 `渝中区`（**市级名字文本里不会出现**）
  - → 解析 MUST NOT 依赖中间层的名字，见 [research.md R3](./research.md#r3--直辖市怎么处理北京市的市级节点也叫北京市)

**前端 MUST NOT 内置本实体的任何数据副本**（FR-118 / SC-046）。测试夹具里的树是**合成的假数据**，
不是区划数据的一份内置拷贝。

---

## 2. 收货地址（既有，本特性不改结构）

前端类型：`storefront/src/types/address.ts` 的 `Address` / `AddressForm`。

| 字段 | 说明 | 本特性是否改动 |
|---|---|---|
| `name` | 收件人 | 识别会改写 —— 仅当姓名紧邻手机号且为 2~4 个汉字（Q55） |
| `mobile` | 手机号 | 识别会改写 —— 仅当文本里有标准 11 位号码（Q55） |
| `areaId` | 地区编号。取**已选中的最小层级**（`lv3 ?? lv2 ?? lv1`），直辖市可能只到市级 | 识别会**改写选择**，但落库口径一字不变 |
| `detailAddress` | 详细地址 | 识别会改写（Q17=A） |
| `defaultStatus` | 是否默认 | **不动** |

**不变量（必须保持）**

- 五字段必填校验与保存链路完全不变（FR-127）。识别只是替客户**做选择**，
  不改变"必须选到地区、必须填详细地址"这两条闸门。
- `#addrName` / `#addrMobile` / `#areaLv1` / `#areaLv2` / `#areaLv3` / `#addrDetail` / `#addrSave`
  这些 **id 一个都不能动** —— `storefront/e2e/helpers.ts` 的 `createAddress()` 依赖它们。

---

## 3. 识别结果 `ParseResult`（本特性新增，仅内存）

```ts
interface ParseResult {
  province: Area | null
  city: Area | null
  district: Area | null
  /** 扣除行政区划后的剩余文本；没有剩余时是空串 */
  detail: string
  /** 未识别到的层级；顺序固定为 province → city → district */
  missing: Array<'province' | 'city' | 'district'>
  /** 识别到的层级数（0/1/2/3）。**0 是 FR-121 的唯一判据** */
  matchedLevels: number
}
```

**不变量**

- `matchedLevels === 0` ⟺ `province === city === district === null` ⟺ `detail === ''`
  → 组件据此**一个字段都不碰**（FR-121）。解析器不负责"要不要写回"，只负责如实报告。
- `district != null` ⟹ `province != null`（区县只能挂在已确定的省下）。
- `city != null` ⟹ `province != null`。
- `missing` 里**不会**出现"识别到了但不显示"的层级 —— 三个层级各自要么填了、要么进了 `missing`。
- **没有"置信度"字段**：本特性不产出任何概率。要么按 R2~R5 的确定性规则定到唯一节点，
  要么就不填。**不猜**是 SC-042「填错条数为 0」的实现基础。
