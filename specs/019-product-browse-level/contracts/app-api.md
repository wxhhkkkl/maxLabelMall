# C 端接口契约变化：商品浏览等级

**Feature**: `019-product-browse-level` | **Date**: 2026-11-16

前缀：`/app-api`。**不新增任何请求参数**：可见性由服务端根据请求头里的登录态
（`Authorization: Bearer <token>`）裁决，客户端不能传 userId / levelId。

## 1. 统一判定规则

| 商品 `browseLevelType` | 未登录访客 | 已登录会员 |
|---|---|---|
| `0` 公开 | 可见 | 可见 |
| `1` 全部登录会员 | **不可见** | 可见（不区分等级） |
| `2` 指定等级会员 | **不可见** | 仅当前 `levelId` 命中集合时可见 |

- 成员等级为空（`levelId = null` 或 `0`）→ 不满足 `2`。
- 企业管理员 / 企业 OWNER 不豁免。
- 与 `enterpriseOnly`、会员授权商品过滤是 **AND**：全部条件满足才返回。

## 2. 商品展示接口

| 接口 | 行为 |
|---|---|
| `GET /product/spu/page` | 按当前身份过滤；`total` 与当前身份实际可见数量一致，不做「先取页再剔除」 |
| `GET /product/spu/list-by-ids?ids=` | 按当前身份过滤；不可见的 id 直接从返回数组中去掉 |
| `GET /product/spu/get-detail?id=` | 不可见时返回错误码 `1_008_005_000`（「商品 SPU 不存在」），响应不得包含名称、价格、图片等任何商品信息 |
| `GET /product/comment/page?spuId=` | 不可见时返回空分页（`list=[]`，`total=0`），避免从评价里泄露 `spuName` |
| `GET /product/favorite/page` | 过滤掉当前不可见的商品，`total` 与列表一致 |
| `GET /product/browse-history/page` | 同上 |
| `GET /product/favorite/exits?spuId=` | 沿用既有实现；商品不可见时 C 端本就不会进入详情页，不额外调整 |

**详情报错示例**：

```json
{
  "code": 1_008_005_000,
  "msg": "商品 SPU 不存在",
  "data": null
}
```

## 3. 交易接口

| 接口 | 行为 |
|---|---|
| `POST /trade/cart/add` | 不可见商品拒绝加购，返回 `1_011_010_005`（「商品不存在或不可购买」） |
| `PUT /trade/cart/update-count` / `POST /trade/cart/reset` | 同样走加购校验，不可见商品拒绝 |
| `GET /trade/cart/list` | 当前不可见的商品不出现在 `validList` / `invalidList`；不删除底层购物车行，等级恢复后重新出现 |
| `GET /trade/order/settlement` | 含不可见商品时拒绝，返回 `1_011_010_005`，提示用户移除 |
| `POST /trade/order/create` | 同上；直接购买 / 立即购买也走此校验 |
| `GET /trade/order/*`（历史订单） | **不受影响**：已生成的订单、订单详情、售后、已下单未支付订单都按原状态继续 |

代客下单（017）按**客户**的会员等级判定，不按运营账号。

新增交易错误码：

| 错误码 | 文案 |
|---|---|
| `1_011_010_005` | 商品不存在或不可购买 |

## 4. 收藏写入

| 接口 | 行为 |
|---|---|
| `POST /product/favorite/create` | 不可见商品拒绝收藏，返回 `1_008_005_000` |

## 5. 营销活动接口

以下接口在渲染商品时会按当前身份过滤，不可见商品不出现（名称 / 图片 / 价格均不下发）：

| 活动 | 接口 |
|---|---|
| 秒杀 | `GET /promotion/seckill-activity/get-now`、`/page`、`/get-detail` |
| 拼团 | `GET /promotion/combination-activity/page`、`/list-by-ids` |
| 积分商城 | `GET /promotion/point-activity/page`、`/list-by-ids` |
| 砍价 | `GET /promotion/bargain-activity/list`、`/page` |

未登录访客只保留「公开」商品。活动本身仍可见，只是其商品信息不下发；若某活动的全部商品都不可见，
该活动页的商品区域为空。

## 6. 与 012 既有口径的关系

以下行为**不变**：

- 企业专享商品仍不进公开商城，只在企业中心 / 企业管理员场景出现；
- 会员授权商品仍不进入公开商城，只在会员续费 / 企业采购页出现；
- 上述两类商品的过滤与浏览等级独立叠加，不合并、不互相替代。

## 7. 明确不在本次契约内的接口

- 商品价格 / 库存的其它只读接口（如后台统计）不参与 C 端可见性过滤；
- 管理端商品列表不新增浏览等级筛选；
- 购物车角标数量（`GET /trade/cart/get-count`）沿用既有实现，不单独过滤。
