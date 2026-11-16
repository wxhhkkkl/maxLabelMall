# 管理端接口契约变化：商品浏览等级

**Feature**: `019-product-browse-level` | **Date**: 2026-11-16

前缀：`/admin-api`。无新增接口、无新增菜单 / 按钮权限。

## 1. 商品保存

### `POST /product/spu/create`

### `PUT /product/spu/update`

在既有请求体上新增两个字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `browseLevelType` | integer | 否 | `0` 公开（默认）/ `1` 全部登录会员 / `2` 指定等级会员 |
| `browseLevelIds` | long[] | 条件必填 | `browseLevelType=2` 时必填且至少 1 个；其余档位忽略并清空 |

请求示例：

```json
{
  "id": 1024,
  "name": "高阶会员专属耗材包",
  "browseLevelType": 2,
  "browseLevelIds": [3, 5]
}
```

规则：

- `browseLevelType` 缺省按 `0` 处理（兼容旧版管理端）。
- `browseLevelType=2` 且 `browseLevelIds` 为空 → 拒绝。
- `browseLevelIds` 中每个编号都必须存在于 `member_level`。
- `browseLevelType != 2` 时，服务端把等级集合清空，避免旧配置残留。
- 重复编号会去重，不视为错误。

错误码：

| 错误码 | 文案 |
|---|---|
| `1_008_005_005` | 指定等级会员时，至少选择一个会员等级 |
| `1_008_005_006` | 会员等级({})不存在 |
| `1_008_005_007` | 浏览等级类型不合法 |

## 2. 商品详情

### `GET /product/spu/get-detail?id={id}`

响应新增：

| 字段 | 类型 | 说明 |
|---|---|---|
| `browseLevelType` | integer | 三档之一；存量商品返回 `0` |
| `browseLevelIds` | long[] | 指定等级集合；非 `2` 档位返回空数组 |

示例：

```json
{
  "id": 1024,
  "name": "高阶会员专属耗材包",
  "browseLevelType": 2,
  "browseLevelIds": [3, 5]
}
```

## 3. 等级下拉数据源

复用既有接口，不新增：

### `GET /member/level/list-all-simple`

- 返回启用状态的会员等级（`id` / `name` / `level`）；
- 无 `@PreAuthorize`，商品编辑人员可直接调用；
- 管理端「指定等级会员」多选只展示这里的选项。

## 4. 不与 012 既有字段合并

`enterpriseOnly` 的语义与校验保持原样。浏览等级是独立字段：

- 商品可以同时是企业专享与指定等级，两个条件都满足才在企业中心 / 公开商城可见；
- 保存商品时**不**新增「企业专享与浏览等级互斥」校验；
- 既有 `validateEnterpriseOnlySwitch` 保持不变。

## 5. 管理端商品列表

本次**不**在商品列表新增「浏览等级」列或筛选条件（spec Assumptions）。列表接口
`GET /product/spu/page` 的契约不变；如需在列表展示，后续单独提需求。
