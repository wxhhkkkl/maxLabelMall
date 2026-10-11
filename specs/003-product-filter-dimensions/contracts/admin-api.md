# admin-api 契约（003）

管理端（`yudao-ui-admin-vue3`）本期新增的配置能力。所有路径以 `/admin-api` 为前缀，
**只走 `/admin-api`**。

> 这是本仓库**第一份 admin-api 契约** —— 001 / 002 的管理端都是零改动，本次不是。

---

## 1. 筛选维度

### 1.1 `GET /product/filter-dimension/list?categoryId=<分类编号>`

返回该分类的维度配置，**分两段**（Q10=B 的继承要在后台可见 —— FR-110）：

```jsonc
{
  "code": 0,
  "data": {
    "own": [                                   // 本分类自有，可增删改
      { "id": 12, "categoryId": 113, "name": "宽度", "sort": 1 }
    ],
    "inherited": [                             // 继承自祖先分类，只读
      { "id": 9, "categoryId": 101, "name": "材质", "sort": 1,
        "inheritedFromCategoryName": "卷筒标签" }
    ]
  }
}
```

**为什么分两段而不是合成一个列表**：两者的**可操作性不同** —— `own` 能改，
`inherited` 改了会影响整个子树，必须让运营看见"这条不是本分类的"。
合成一个列表会让后台显示一个能点但改不动（或改了波及一片）的项。

### 1.2 维度 CRUD

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/product/filter-dimension/create` | `{ categoryId, name, sort?, remark? }` |
| `PUT` | `/product/filter-dimension/update` | `{ id, name, sort?, remark? }`（**不改 `categoryId`** —— 挪分类等于换一套继承关系，本期不支持） |
| `DELETE` | `/product/filter-dimension/delete?id=` | **级联删除其下全部可选值及商品关联** |
| `DELETE` | `/product/filter-dimension/delete-list?ids=` | 批量 |

**错误语义**

| 情况 | code | msg |
|---|---|---|
| 与祖先链上的维度重名 | 业务异常 | `分类【碳带】下已存在维度【宽度】` —— MUST 指明**是哪个分类上的**，否则运营不知道该去哪改（FR-091） |
| `categoryId` 不存在或已禁用 | 业务异常 | 明确说明 |

### 1.3 `GET /product/filter-dimension/delete-impact?id=`

删除**前**的引用查询（FR-092）：返回 `{ valueCount, spuCount }`，供后台二次确认弹窗显示。

---

## 2. 维度可选值

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/product/filter-value/list?dimensionId=` | 该维度下的全部值（含排序） |
| `POST` | `/product/filter-value/create` | `{ dimensionId, name, sort?, remark? }` |
| `PUT` | `/product/filter-value/update` | `{ id, name, sort?, remark? }` |
| `DELETE` | `/product/filter-value/delete?id=` | 删除并清理商品关联 |
| `GET` | `/product/filter-value/delete-impact?id=` | `{ spuCount }`，供删除前确认 |

**错误语义**：同一维度下重名 → 业务异常，msg 指明维度名与重复的值名。

---

## 3. 商品与筛选标签（挂在既有商品接口上）

### 3.1 `GET /product/spu/get?id=<spuId>` —— **响应体新增一个字段**

```jsonc
{
  // …既有的全部字段不变…
  "filterValueIds": [3, 7, 11]      // 新增。该商品当前挂着的筛选值 id
}
```

⚠️ **这是对既有接口的响应体做加法**。MUST NOT 改动任何既有字段的语义或类型。
前端 MUST 容忍该字段缺失（`?? []`），因为**旧的后端（未部署新版）不会返回它**。

### 3.2 `GET /product/spu/filter-value-options?spuId=<spuId>`

返回**该商品可挂的**维度与值（FR-095 的辖域口径，由**服务端**裁决 —— 宪法原则 III）：

```jsonc
{
  "code": 0,
  "data": [
    { "dimensionId": 12, "dimensionName": "宽度", "fromCategoryName": "碳带",
      "values": [ { "valueId": 3, "name": "40" }, { "valueId": 4, "name": "50" } ] }
  ]
}
```

- 范围 = `scopeOf(商品分类)` 上的维度（即本分类 + 全部祖先）。
- `fromCategoryName` 让后台能显示"（来自「碳带」）"，与 §1.1 的 `inherited` 同一口径。
- **商品新建时**（还没有 `spuId`）用 `?categoryId=<分类编号>` 传商品已选的分类，口径相同。

### 3.3 `POST` / `PUT /product/spu/create|update`

请求体新增 `filterValueIds`（可选数组）。

**服务端行为（FR-095 / FR-097 / R11）**

1. 逐个校验：值所属维度的 `category_id` ∈ `scopeOf(商品分类)`。**不满足的不写入**。
2. 若该商品**此前挂有**标签、但已不在新范围内 → **一律删除**。
3. 返回体**不含**剔除数量（既有 `CommonResult` 形状不变 —— 不为了提示改公共返回结构）。
   "已移除 N 个"的提示由**前端对比"保存前 vs 保存后"的 `filterValueIds`** 得出
   （见 §3.1 的读接口）。**这是有意的取舍**：剔除是**幂等的、可见的**（保存后重读就看得见），
   不值得为它改一个全局共用的返回结构。

---

## 4. 权限

新增的管理端接口 MUST 复用既有 mall/product 的权限点风格
（`product:filter-dimension:query` / `:create` / `:update` / `:delete` 之类），
并在菜单/按钮上按既有约定挂 `v-access:code`。**本期不新建一级菜单**（R8）。

---

## 5. 显式不在本期契约内

- ❌ 不提供"把维度挪到另一个分类"的接口（`update` 不接受 `categoryId`）。
- ❌ 不提供维度/值的**启用停用**开关 —— 本期只有"存在/删除"两态（最小改动）。
- ❌ 不提供批量给多个商品打标签的接口。若运营反馈录入量无法承受，单独立项（见 R12）。
- ❌ 不改 `product_property` 相关的任何接口（R1）。
