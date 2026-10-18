# 管理端接口契约（007 虚拟商品）

> 前缀：**只走 `/admin-api`**（宪法约束）。**本特性不新增、不改任何管理端接口** ——
> 起作用的是一个**字典数据**和几个前端条件分支。

---

## 1. 接口：**零改动**

| 接口 | 为什么不改 |
|---|---|
| `POST /product/spu/create`、`PUT /product/spu/update` | `deliveryTypes` 本来就是 `List<Integer>`，多传一个 `3` 即可（**服务端没有 `@InEnum` 校验它**，一直是靠字典约束的） |
| `PUT /trade/order/delivery` | 复用既有的「无需发货」分支（`logisticsId = 0`），请求体形态不变 |
| `GET /trade/order/page`、`/get-detail` | `deliveryType` 本来就是响应字段，值为 3 时前端按新分支渲染 |

⚠️ 唯一"看起来像要改接口"的地方是发货校验：服务端原来要求 `deliveryType == 快递` 才能发货，
本次改成**只拒自提**（虚拟商品放行）。**这是行为变更，不是契约变更** —— 请求与响应都没变。

## 2. 字典数据（本特性在管理端唯一的"新增物"）

`system_dict_data` 新增一条：

| dict_type | label | value | sort | status |
|---|---|---|---|---|
| `trade_delivery_type` | 虚拟商品 | `3` | 3 | 0（启用） |

执行脚本：`yudao-cloud/sql/mysql/011-virtual-goods-dict.sql`（幂等，可重跑）。

> **为什么必须走字典而不是只改 Java 枚举**：管理端商品编辑的配送方式选项是
> `getIntDictOptions(DICT_TYPE.TRADE_DELIVERY_TYPE)` 渲染的（`DeliveryForm.vue`）。
> 不插这条，商品编辑页**根本勾不出**「虚拟商品」。而服务端的 `@InEnum(DeliveryTypeEnum.class)`
> 校验靠的是枚举 —— **两边都得有**（FR-197）。

## 3. 管理端前端落点

| 文件 | 改动 |
|---|---|
| `utils/constants.ts` | `DeliveryTypeEnum` 副本加 `VIRTUAL: { type: 3, name: '虚拟商品' }` |
| `views/mall/product/spu/form/DeliveryForm.vue` | ① **修既有缺陷**：运费模板改为"勾了快递才必填"；② 勾了虚拟商品时给提示 |
| `views/mall/trade/order/index.vue` | 「发货」按钮：`deliveryType !== 自提`（原来只对快递显示） |
| `views/mall/trade/order/detail/index.vue` | 同上；并且虚拟单**不渲染**空的「收货人 / 联系电话」两行 |
| `views/mall/trade/order/components/OrderTableColumn.vue` | 「买家/收货人」列加虚拟商品分支（只显示买家；原来会是一片空白） |

## 4. 商品编辑的一个既有缺陷（本次一并修）

```js
// 改前（DeliveryForm.vue）
const rules = reactive({ deliveryTypes: [required], deliveryTemplateId: [required] })
```

运费模板输入框是 `v-if="deliveryTypes.includes(快递)"` —— **只勾「用户自提」或「虚拟商品」时
字段被隐藏，而校验照跑**，于是报「【物流设置】不完善」却看不出缺什么。

改后：`deliveryTemplateId` 用自定义 validator，**只在勾了快递时才必填**。

> 这不是"顺手改"，是**本特性的前置条件**：不修的话「虚拟商品」这个选项根本存不下来（SC-069）。
