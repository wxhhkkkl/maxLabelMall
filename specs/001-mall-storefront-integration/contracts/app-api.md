# Contracts: 门面站消费的 app-api

**Feature**: `001-mall-storefront-integration`
**Date**: 2026-09-24

本文件只记录**门面站实际调用**的接口。所有路径均相对于 `VITE_API_URL`（`/app-api`）。

## 通用约定

| 项 | 约定 |
|---|---|
| 前缀 | `/app-api`（**不是** 管理端的 `/admin-api`） |
| 响应体 | `{ code, data, msg }`，成功码 `0` |
| 鉴权头 | `Authorization: Bearer <accessToken>`（后端会容忍前缀缺失，但前端统一带 `Bearer `） |
| **租户头** | **`tenant-id` 是全局必填，不是可选**。本地不传会得到 `{code:400, msg:"请求的租户标识未传递"}`（HTTP 仍为 200）。**商品浏览、分类、登录等免登录接口同样必须带**。单租户固定值，不做 `visit-tenant-id` |
| 终端头 | `terminal: 20`（H5）。缺失不报错（回落为 `0 UNKNOWN`），但会影响会员注册来源与下单落库的终端字段，故统一携带 |
| 免登录 | 商品浏览、分类、商品详情、认证相关接口后端已标 `@PermitAll` |
| 401 | 走刷新令牌 + 请求队列回放；刷新失败则清登录态并引导重登，且**保留用户未完成的操作意图**（FR-015） |

**响应码判断**：成功码是 **`0`**（app 端），不是管理端的 `200`。注意租户缺失这类错误是 **HTTP 200 + body.code=400**，所以拦截器必须按 `body.code` 判断，不能只看 HTTP 状态。

**参数序列化注意**：`/trade/order/settlement` 的 `items[]` 为数组参数，SpringMVC 绑定需要 `items[0].skuId=..&items[0].count=..` 形式。既有 C 端工程是**手工拼 query string** 解决的，前端应复用同一做法，不要依赖默认的对象序列化。

---

## 1. 认证（会员）

| 用途 | 方法与路径 | 说明 |
|---|---|---|
| 密码登录 | `POST /member/auth/login` | body: `mobile`、`password` |
| 验证码登录（**兼隐式注册**） | `POST /member/auth/sms-login` | body: `mobile`、`code`；手机号不存在时后端自动建号 |
| 发送验证码 | `POST /member/auth/send-sms-code` | body: `mobile`、`scene`、`captchaVerification?`（见 §1.1）；**无同意字段**，同意由前端门禁保证 |
| 校验验证码 | `POST /member/auth/validate-sms-code` | 用于分步校验（可选） |
| 刷新令牌 | `POST /member/auth/refresh-token` | query: `refreshToken` |
| 登出 | `POST /member/auth/logout` | — |
| 当前会员 | `GET /member/user/get` | 顶栏昵称/手机号；**另返回** `email`/`sex`/`point`/`experience`/`level{id,name,level,icon}` —— 个人资料回填与积分等级展示都靠它，无需额外接口 |
| **修改个人信息** | `PUT /member/user/update` | body 含 `nickname`、`avatar`（带 `@URL`）、`email`（`@Email`、≤50）、`sex`。⚠️ **四个字段都不是必填**（无 `@NotNull`），**只提交改动过的字段**即可；未提交的字段不会被清空（`NOT_NULL` 更新策略）。**清空要显式提交空串**（`@URL`/`@Email` 均放行空串） |
| **积分明细** | `GET /member/point/record/page` | `pageNo`/`pageSize`；返回 `{id, title, description, point, createTime}` —— `point` 正负即增减。后端还支持 `addStatus`/`createTime` 筛选，本期**不用** |
| 设置密码 | `PUT /member/user/update-password` | 登录后设置密码（FR-011）；body `password` + **scene 3** 的短信码 |
| **忘记密码** | `PUT /member/user/reset-password` | **免登录**（`@PermitAll`）；body `mobile` + `password` + **scene 4** 的短信码 |

**登录/刷新的响应字段**（`AppAuthLoginRespVO`）：`accessToken`、`refreshToken`、`expiresTime`、`userId`。
**存储约定**：`accessToken → ACCESS_TOKEN`、`refreshToken → REFRESH_TOKEN`（照搬 admin-vue3 的 key，不与 uniapp 的 `token` 混用）。

**三个短信场景不可互换**（服务端按场景核销）：`1` 登录、`3` 改密、`4` 忘记密码。

**明确不调用**（本期）：`social-login`、`weixin-mini-app-login`。
`social-auth-redirect` / `social-user/bind` 已在 §6.1 启用（微信 JSAPI 取 openid 用）。

### 1.1 图形验证码（滑块）—— 发短信前的闸门

**目的**：发短信是真金白银，挡脚本刷短信。**校验点在服务端**（`MemberAuthServiceImpl.sendSmsCode`
的第一步），前端这套只是把凭据拿回来 —— 只画个滑块而服务端不校验等于没加。

| 用途 | 方法与路径 | 说明 |
|---|---|---|
| 开关 | `GET /app-api/system/captcha/enable` | 返回 `Boolean`。**开关的权威在服务端**（`yudao.captcha.enable`），前端不读自己的环境变量 |
| 取码 | `POST /app-api/system/captcha/get` | body `{captchaType}`；返回 `{originalImageBase64, jigsawImageBase64, token, secretKey}` |
| 校验 | `POST /app-api/system/captcha/check` | body `{captchaType, token, pointJson}`；返回 `{success, msg}` |

**这几个接口是本期新增的 app-api 控制器**（`AppCaptchaController`）—— 管理端那套挂在
`/admin-api/system/captcha/*` 下，C 端不能去碰（宪法：C 端只走 `/app-api`）。它把 aj-captcha
原本的 `{repCode, repData}` **重新包成了项目的 `CommonResult` 信封**，前端因此走常规解包。

**完整链路**：

```
点「获取验证码」
  → GET /app-api/system/captcha/enable        关 → 直接发短信（本地开发与 e2e 走这条）
  → POST .../captcha/get                      取图（原图 310×155，拼图块 47×155）+ token + secretKey
  → 用户拖动 → x = 拖动像素 × 310 / 渲染宽度    ⚠️ 必须换算回原图坐标
  → POST .../captcha/check {token, pointJson}  pointJson = AES({x, y:5}, secretKey)
  → 通过 → captchaVerification = AES(token---{x, y:5}, secretKey)   ← 注意 token 用**明文**拼接
  → POST /member/auth/send-sms-code {mobile, scene, captchaVerification}
```

**四条必须遵守的约束**：

- **加密是 AES-128-ECB + PKCS7**，密钥取 `secretKey` 的 UTF-8 字节（服务端没下发时用默认
  `XwKsGlMcdPMEhR1B`）。浏览器原生 WebCrypto **不提供 ECB**，故作弊不得 —— 本项目引入
  `crypto-js`，实现与上游管理端 `Verifition/src/utils/ase.ts` 一致。
- **`captchaVerification` 服务端从不生成**（aj-captcha 里只有 `CaptchaVO` 上的 setter，
  没有任何地方调用），必须由前端按上式算出；服务端在 `check` 通过时就把同一串存进缓存，
  业务接口只做「存在与否」的比对 —— 所以**这一串必须逐字一致**。
- **验证码一次性**：`check` 不通过必须重新取图，同一张图不能反复作答。
- ⚠️ **`pointJson` 的明文必须是紧凑 JSON —— 冒号与逗号后面不能有空格。**
  也就是 `{"x":101,"y":5}`，不是 `{"x": 101, "y": 5}`（后者会判「验证失败」）。
  前端 `JSON.stringify({x, y: 5.0})` 天然就是这个形状，所以代码里看不出这条约束；
  但手工拼请求（排查、脚本、别的客户端）时极易踩 —— 2026-10-09 就因此连挂三次，
  一度误以为是自己算的坐标不对。
- **`captchaVerification` 是可选的**：服务端开关关闭时前端不传，此时
  `CaptchaApi.verification` 直接放行。开关的判定**只在服务端一处**，不会出现
  「前端弹了滑块、服务端没校验」或反过来的错配。

---

## 2. 商品与分类

| 用途 | 方法与路径 | 关键参数 |
|---|---|---|
| 商品分页 | `GET /product/spu/page` | `pageNo`、`pageSize`、`categoryId` / `categoryIds`、`keyword`、`sortField`、`sortAsc` |
| 商品详情 | `GET /product/spu/get-detail` | `id` |
| 按编号批量取商品 | `GET /product/spu/list-by-ids` | `ids` |
| 分类列表 | `GET /product/category/list` | 无（返回全量，前端组树） |

**排序映射**（设计稿三种排序 → 后端 `sortField`）：

| 设计稿 | `sortField` | `sortAsc` |
|---|---|---|
| 综合排序 | 不传（后端默认按 `sort DESC, id DESC`） | — |
| 销量优先 | `salesCount` | `false` |
| 价格从低到高 | `price` | `true` |

> ⚠️ **`sortField` 的取值必须是小驼峰，且只有两个合法值。** 后端 `AppProductSpuPageReqVO` 里常量定义为
> `SORT_FIELD_PRICE = "price"`、`SORT_FIELD_SALES_COUNT = "salesCount"`、
> `SORT_FIELD_CREATE_TIME = "createTime"`，但校验方法是
> `@AssertTrue(message = "排序字段不合法") isSortFieldValid()`，其白名单是
> `StrUtil.equalsAny(sortField, SORT_FIELD_PRICE, SORT_FIELD_SALES_COUNT)`。
> 因此：
> - 传 `price` / `salesCount` ✅
> - 传 `PRICE` / `SALES_COUNT` ❌（**会被判为排序字段不合法**）
> - 传 `createTime` ❌（**常量存在但不在白名单里，同样会被拒**——这是个易踩的坑）

**约束**：
- 后端**不支持**价格区间与服务标签筛选，前端不得传这两个维度的参数（FR-004a）。
- 检索在后台全量范围内进行，**不是**过滤当前页已加载的卡片（FR-003）。
- 商品已下架时 `/get-detail` 会返回明确的业务错误码（非空响应），前端据此展示「商品已下架」而不是空白页（FR-007）。

**首页商品区**：复用 `/product/spu/page`，传 `sortField=salesCount&sortAsc=false`，`pageSize` 由视口宽度决定 —— 超宽屏（≥1601px）取 6 个、其余取 4 个（FR-008，2026-10-07 修订）。跨断点时重新请求一次；`matchMedia` 的 `change` 只在越过该宽度时触发，拖动窗口不会反复请求。

---

## 3. 购物车

| 用途 | 方法与路径 | 说明 |
|---|---|---|
| 加入购物车 | `POST /trade/cart/add` | body: `skuId`、`count` |
| 购物车列表 | `GET /trade/cart/list` | 需登录；返回 `{ validList, invalidList }` |
| 修改数量 | `PUT /trade/cart/update-count` | `id`、`count` |
| 修改勾选 | `PUT /trade/cart/update-selected` | 选中状态**后端持久化** |
| 删除条目 | `DELETE /trade/cart/delete` | `ids` |
| 角标数量 | `GET /trade/cart/get-count` | 顶栏 `购物车 (N)`，与列表分开取 |

**约束**：
- 有效/失效由后端已分好的两个列表决定，**前端不得自行判断**下架与售罄（FR-023）。
- 加购数量上限的**唯一判定方是后端**；前端只做超限提示与回退展示（FR-021）。

---

## 4. 收货地址

| 用途 | 方法与路径 |
|---|---|
| 列表 | `GET /member/address/list` |
| 默认地址 | `GET /member/address/get-default` |
| 单个 | `GET /member/address/get` |
| 新增 | `POST /member/address/create` |
| 修改 | `PUT /member/address/update` |
| 删除 | `DELETE /member/address/delete` |
| **地区树** | `GET /system/area/tree` |

**必填字段**：`name`、`mobile`、`areaId`、`detailAddress`、`defaultStatus`。
`areaId` 由 `/system/area/tree` 三级联动选出；**行政区划必须来自后台**，不得在前端内置。

---

## 5. 结算与下单

| 用途 | 方法与路径 | 说明 |
|---|---|---|
| 结算（**购物车与直购共用**） | `GET /trade/order/settlement` | 数组参数手工拼 query 且**方括号要百分号编码**；需登录 |
| 下单 | `POST /trade/order/create` | 生成待支付订单 |
| 订单分页 | `GET /trade/order/page` | 我的订单 |
| 订单详情 | `GET /trade/order/get-detail` | `id`；**金额字段平铺在返回对象上** |
| 取消订单 | **`DELETE /trade/order/cancel`** | 仅待支付可用；`id` 是**query 参数**（`@RequestParam`）。**注意是 DELETE 不是 PUT** —— 后端为 `@DeleteMapping("/cancel")`，用 PUT 会得到 405 |
| 各状态数量 | `GET /trade/order/get-count` | 键为 `allCount`/`unpaidCount`/`undeliveredCount`/`deliveredCount`/`uncommentedCount`/`afterSaleCount`；**没有 `canceledCount`** |

> ⚠️ **`/trade/order/settlement-product` 不是直购结算端点。** 早期版本的本文档写着
> 「「立即购买」走这条」，是**误读**：该端点只收 `spuIds: List<Long>`、标着
> `@PermitAll`、返回 `List<AppTradeProductSettlementRespVO>`（列表 / 详情页用的
> **活动价格信息**），与结算下单无关。**直购与购物车结算走同一个 `/trade/order/settlement`**
> —— 后端 `TradeOrderConvert#convert` 的「情况一：`skuId` + `count`」就是直购，
> 「情况二：`cartId`」才是购物车。已实测：只带 `skuId`/`count` 不带 `cartId` 返回 `code=0`。

**结算请求字段**：`items[]`（`skuId`/`count`/`cartId`）、`couponId`、`addressId`、`pointStatus`、`deliveryType`。

**四个必须遵守的契约细节**：

1. **`pointStatus` 是必填**（后端标了 `@NotNull`）。本期不展示积分，**固定传 `false`**，且明细中不出现积分行。
2. **`deliveryType` 固定按快递**，不提供配送方式切换与自提点（FR-027a）。前提是运营在后台关闭自提开关。
3. **`couponId` 可选**。券的可用性由结算响应判定，前端不得自行判断（FR-026b）。
4. **`items[].skuId` 也是必填**（`@NotNull`），**且 query 里的下标方括号要百分号编码**。
   两条合起来意味着**购物车来源也必须知道每个条目的 skuId**，而 URL 上通常只有 cartId
   —— 故结算页要先拉一次购物车列表取 skuId/count，**不能**从结算响应里反查（第一次请求
   时响应还不存在）。编码写成 `items%5B0%5D.skuId=..`：`[` `]` 不是 RFC 7230/3986 的
   合法 query 字符，后端又没配 `server.tomcat.relaxed-query-chars`，字面方括号会被
   Tomcat 以 HTML 400 直接拒收（前端只看到"结算失败"，极易误判成业务错误）。
   SpringMVC 会把 `%5B0%5D` 解码回 `[0]` 并正确绑定。

**结算响应结构**（`AppTradeOrderSettlementRespVO`）：
```
type, items[], coupons[], price{}, address{}, usePoint, totalPoint, promotions[]
  items:  ..., cartId, count            ← 购物车来源时带 cartId
  price:  totalPrice, discountPrice, deliveryPrice, couponPrice, pointPrice, vipPrice, payPrice
  coupons: id, name, usePrice, validStartTime, validEndTime,
           discountType, discountPercent, discountPrice, discountLimitPrice,
           match, mismatchReason      ← 可用性判定由后端给出
```

**订单详情响应结构**（`AppTradeOrderDetailRespVO`）—— **金额是平铺字段，没有嵌套的 `price`**：
```
id, no, status, createTime, payTime, payExpireTime, payChannelName, payStatus,
**payOrderId**,                     ← 支付单号；提交支付要用它（不是交易订单号 id）
totalPrice, discountPrice, deliveryPrice, payPrice, couponPrice, pointPrice, vipPrice,
receiverName, receiverMobile, receiverAreaId, receiverAreaName, receiverDetailAddress,
couponId, couponPrice, items[]{ id, spuId, skuId, spuName, picUrl, properties[], count, price, payPrice }
```

注意：**没有券名**字段（只有 `couponId`）——要展示"所用券"的名称需后端补字段。

> ⚠️ **`payOrderId` 是 2026-09-27 实测补上的**。早期版本的字段清单漏了它，会导出
> 「订单详情页拿不到支付单号、无法发起支付」的错误结论。它**可以是 null**
> （`payPrice === 0` 的订单不创建支付单，见 §6）。

> ⚠️ **所有 `LocalDateTime` 字段都是 epoch 毫秒数，不是字符串。** yudao 在
> `YudaoJacksonAutoConfiguration` 里给 `LocalDateTime` **全局**注册了
> `TimestampLocalDateTimeSerializer`，所以 `createTime` / `payTime` / `payExpireTime` /
> 券的 `validStartTime` / `validEndTime` 回来都是 `1790486340000` 这种数字。
> **视图直接插值就会把这串数字显示给用户**（实测：订单页曾显示「支付截止 1790486340000」）。
> 前端 MUST 过 `storefront/src/utils/time.ts` 渲染。
> 服务端另有 `TimestampLocalDateTimeDeserializer` 与之对称，故**查询参数传毫秒数**也是对的。

**金额不变量（SC-007）**，以**分**为单位核对：
```
payPrice === totalPrice - couponPrice - pointPrice - discountPrice + deliveryPrice - vipPrice

（**六项，不是四项。** 上式取自后端 `TradePriceCalculateRespBO.Price.payPrice` 的 javadoc 与
`TradePriceCalculatorHelper` 的实际计算。本期 `pointStatus` 固定为 `false`，故 `pointPrice` 恒为 0；
但 **`vipPrice`（会员折扣）不会被强制置零** —— 运营若配置了会员折扣类活动，只按四项重算就会
与后端不符。因此 `reconcile()` MUST 按六项核对，或干脆只核对四个展示行之和、并声明
**服务端返回的 `payPrice` 为权威值**。）
```

---

## 6. 支付

**关键结论：模拟通道下「提交支付即成功」，不存在也不需要单独的「标记支付成功」接口。**

| 用途 | 方法与路径 | 说明 |
|---|---|---|
| 提交支付 | `POST /pay/order/submit` | body: `id`（= 下单返回的 `payOrderId`）、`channelCode: "mock"`、`displayMode`、`returnUrl`、`channelExtras` |
| 查询支付单 | `GET /pay/order/get` | `id` 或 `no`，可选 `sync=true` |
| 同步交易订单 | `GET /trade/order/get-detail` | 可选 `sync=true` |

**完整链路**：
```
POST /trade/order/create
  → 响应 { id: 交易订单号, payOrderId: 支付单号 }
POST /pay/order/submit { id: payOrderId, channelCode: "mock" }
  → MockPayClient 立即返回成功
  → 支付模块在事务提交后异步回调 pay_app.order_notify_url
  → POST /app-api/trade/order/update-paid   （@PermitAll）
  → 交易订单状态 → 待发货(10)
```

**因此前端不需要第三个接口**：提交支付成功后，订单状态由回调推进。前端应轮询（或 `sync=true` 拉取）订单详情以确认状态已变更，而不是自行把订单标记为已支付（FR-039 的状态权威在后端）。

> ⚠️ **`payOrderId` 可能为 `null` —— 必须处理。** 后端在 `TradeOrderUpdateServiceImpl` 里是
> `// 特殊情况：积分兑换时，可能支付金额为零` + `if (order.getPayPrice() > 0) { createPayOrder(order, orderItems); }`，
> 而 `createPayOrder` 是 `order.payOrderId` 的**唯一写入点**。因此当 `payPrice === 0`（例如一张券把订单全额抵扣）时：
> - `create` 的响应里 **`payOrderId` 为 `null`**
> - 没有任何 id 可以传给 `/pay/order/submit`
> - 订单会**停在「待支付」**，前端若直接拿 `payOrderId` 去提交支付会失败
>
> 前端 MUST 在 `payOrderId` 为空时走另一条路：提示"本单无需支付"，并拉取订单详情确认状态；**不得**把空值直接提交给支付接口。

**前置条件**：`pay_channel` 表中需存在 `code='mock'` 且 `app_id=1` 的记录（仓库种子 `yudao-cloud/sql/mysql/pay-2026-04-18.sql` 已包含，`status=0` 启用）。若缺该行，提交支付会因渠道不可用而失败。

**回调地址**：种子数据里 `pay_app.order_notify_url` 写死为 `http://127.0.0.1:48080/app-api/trade/order/update-paid`，与本地单体端口一致，本地无需改动。

### 6.1 微信公众号 JSAPI（渠道码 `wx_pub`）

**这个渠道有两个别处没有的前置条件**，两者缺一都只能报错、拿不到钱：

1. **必须有该会员在「同一个公众号」下的 `openid`** —— 提交支付时要放进
   `channelExtras.openid`。缺了后端 `WxPubPayClient.getOpenid` 直接抛
   「支付请求的 openid 不能为空」。
2. **只能在微信内置浏览器里唤起收银台** —— 网页里没有别的办法调起微信付款。

**取 openid 用上游既有的三个接口，不新建后端能力**（`type` 固定 `31` =
`SocialTypeEnum.WECHAT_MP`）：

| 用途 | 方法与路径 | 关键参数 / 返回 |
|---|---|---|
| 取微信授权页地址 | `GET /member/auth/social-auth-redirect` | `type=31`、`redirectUri`（**完整页面 URL**，须落在公众号「网页授权域名」下）；返回可直接整页跳转的 URL |
| 用 `code` 换 openid | `POST /member/social-user/bind` | body `{type:31, code, state}`；**返回 openid 字符串**，并把该公众号绑到当前登录会员 |
| 查已绑定的 openid | `GET /member/social-user/get` | `type=31`；未绑定时 `data` 为 `null` |

**完整链路**：

```
微信内 · 订单详情页点「微信支付（公众号）」
  →（内存/后端都查不到 openid 时）GET /member/auth/social-auth-redirect?type=31&redirectUri=<当前订单页>
  → 整页跳微信授权（静默 snsapi_base）
  → 回跳 <订单页>?code=xxx&state=yyy
  → POST /member/social-user/bind {type:31, code, state}   →  openid
  → POST /pay/order/submit { id: payOrderId, channelCode: 'wx_pub', channelExtras: { openid } }
  → 响应 displayMode = 'app'，displayContent = JSAPI 参数 JSON
  → WeixinJSBridge.invoke('getBrandWCPayRequest', …)       →  用户完成付款
  → 支付模块回调 pay_app.order_notify_url → /app-api/trade/order/update-paid
  → 交易订单 → 待发货(10)（前端 `GET /trade/order/get-detail?sync=true` 确认）
```

**`displayContent` 的字段映射**（V2/V3 两个 API 版本字段名一致）：

| 后端 `displayContent` 字段 | 传给 `WeixinJSBridge` |
|---|---|
| `packageValue` | **`package`**（改名，传错手机上弹「参数错误」） |
| `appId` / `timeStamp` / `nonceStr` / `signType` / `paySign` | 同名照传 |

> `timeStamp` 的 `S` 大写是**后端返回**的字段名；JSSDK 侧的入参叫小写 `timestamp`。
> 本实现用 `WeixinJSBridge`，它收的就是 `timeStamp`。

**四条必须遵守的约束**：

- **不引 `weixin-js-sdk`**：`jweixin.chooseWXPay` 要先 `jweixin.config()` 拿到 JSSDK
  签名（`POST /member/auth/create-weixin-jsapi-signature`）并配公众号「JS 安全域名」；
  `WeixinJSBridge` 是微信浏览器自带的，不需要签名。**`create-weixin-jsapi-signature`
  本期不调用**（将来要做定位/分享等 JS-SDK 能力时再评估）。
- **回跳 URL 上的 `code`/`state` 必须在读到后立刻从地址栏抹掉**：这条 URL 事实上可
  重放 —— `SocialUserServiceImpl.authSocialUser` 会先命中自己库里已存的
  `(type, code, state)`，绕过 justauth 的 state 校验。刷新一次就会再绑一次。
- **`bind` 有副作用，必须知情**：它第一步就解绑「该 openid 原先绑的会员」。
  同一台手机的同一个微信号本来就是同一个 openid，而 yudao 的模型是
  「1 openid → 1 会员」，所以会「抢绑定」。**判定为可接受**：订单归属按 memberId
  不按 openid，不会丢单丢钱；被抢的一方下次支付会自动重新授权。也正因如此，
  前端**只把 openid 放内存、不落 localStorage**。
- **收银台返回 `ok` 不等于已付款**：`err_msg=getBrandWCPayRequest:ok` 只说明用户在
  微信那侧走完了，交易订单仍要等后端回调 —— 前端照 `sync=true` 重新拉详情（FR-039）。

**环境过滤**：`wx_pub` 只在微信内渲染、`wx_native`（扫码）只在微信外渲染。
这是**运行环境事实**，不是业务规则 —— 可用性仍完全由
`/pay/channel/get-enable-code-list` 决定，前端只在后端给的启用集合内部做减法。

**真机验收（无法自动化）**：`WeixinJSBridge` 的实际唤起、微信授权往返、
「下单账号与支付账号不一致」这类只有微信能给的错误，都要在微信里手工走一笔。

---

## 6.2 申请退款（售后，按订单项）

**复用 yudao 既有的售后能力，后端与管理端都不改**；审核在管理端「售后退款」页完成
（`/admin-api/trade/after-sale/{agree,disagree}`），运营点同意/拒绝。

| 用途 | 方法与路径 | 说明 |
|---|---|---|
| 申请退款 | `POST /app-api/trade/after-sale/create` | body `{orderItemId, way, refundPrice, applyReason, applyDescription?, applyPicUrls?}`；返回**售后单编号** |
| **售后列表** | `GET /app-api/trade/after-sale/page` | `pageNo`/`pageSize`；返回项含 `no` 售后单号、**`status` 精确售后单状态**、`way`、`applyReason`、`refundPrice`、`createTime`、`auditReason` 与商品快照（`spuName`/`picUrl`/`properties`/`count`） |
| 撤销申请 | `DELETE /app-api/trade/after-sale/cancel` | query `id` = **售后单编号**（订单项上的 `afterSaleId`）。后端只允许售后单处于「申请中 / 卖家同意 / 待卖家收货」时撤销；**撤销后订单项的售后状态回到未售后**，两个入口都会重现 |

**请求体的四个必填字段**：

| 字段 | 值 | 说明 |
|---|---|---|
| `orderItemId` | 订单项编号 | ⚠️ **不是订单编号** —— 传错会「订单项不存在」。一笔订单里**每个商品各一条**售后单 |
| `way` | `10` 仅退款 / `20` 退货退款 | 后端 `AfterSaleWayEnum`，**是 10/20 不是 1/2** |
| `refundPrice` | 分，`≥1` | **不得超过该订单项的实付** `payPrice`（后端按它校验） |
| `applyReason` | 字符串 | 必填，**自由文本**（后端无枚举）；"可选理由清单"属业务规则，不由前端编 |

**服务端校验**（前端据此**不给入口/不发请求**，口径详见 `spec.md` FR-041e）：
订单必须已支付且未取消；该**订单项**未被申请过（卖家拒绝后后端重置回未售后 → 入口自动重现）；
`refundPrice ≤ payPrice`；`way=20` 要求订单**已发货**。

**订单项的售后状态**（`AppTradeOrderItemRespVO.afterSaleStatus`，**订单项级**、不同商品互相独立）：
`0` 未售后 / `10` 售后中（「退款处理中」）/ `20` 售后成功（「已退款」）。

⚠️ **两个粒度别混**：上述订单项状态只有 3 个值，分不出售后单走到了哪一步；
「我的售后」列表拿到的 `AppAfterSaleRespVO.status` 是**售后单的精确状态**（10/20/30/40/50/61/62/63）。
因此**列表页能准确判断能否撤销，订单详情页不能** —— 后者只能在「售后中」时一律给撤销入口、
不可撤销时由后端拒并透出文案。详见 [research.md](./research.md) R13 与 [data-model.md](./data-model.md) §1.10。

**本期的展示口径**：订单项上显示入口或状态标签，提交/撤销成功后都**重拉**以后端为准
（不做本地乐观更新，与支付后 `sync` 同一口径）。

**「撤销申请」的入口判定**：订单项只暴露 `afterSaleStatus`（0/10/20），**看不出**售后单走到了
哪一步 —— 所以「售后中」时一律给撤销入口（有 `afterSaleId` 的前提下），真到不可撤销的状态
（如商家已收货待退款）由后端拒、前端把它「售后单状态不允许取消」的原样透出来。**前端不猜。**

凭证图片、独立售后列表页本期不做。

---

## 7. 优惠券

| 用途 | 方法与路径 | 关键参数 / 返回 |
|---|---|---|
| 领取 | `POST /promotion/coupon/take` | body `{templateId}`；返回 `Boolean`（是否还能继续领） |
| 我的券 | `GET /promotion/coupon/page` | `pageNo`、`pageSize`、`status`（`1 未使用 / 2 已使用 / 3 已过期`） |
| 单张券 | `GET /promotion/coupon/get` | `id`（仅本人券） |
| 未使用数量 | `GET /promotion/coupon/get-unused-count` | 用于「我的券」角标 |
| 可领券模板 | `GET /promotion/coupon-template/list` | `spuId`、`productScope`、`count`（默认 10）；**只返回可直接领取的类型**，`@PermitAll` |
| 模板详情 | `GET /promotion/coupon-template/get` | `id`；返回含 `canTake`（当前用户是否可领） |

**券字段**（`AppCouponRespVO`）：`id`、`name`、`status`、`usePrice`、`productScope`、`productScopeValues`、`validStartTime`、`validEndTime`、`discountType`、`discountPercent`、`discountPrice`、`discountLimitPrice`。

**`couponId` 的传入位置**：

| 场景 | 位置 |
|---|---|
| 结算 | `GET /trade/order/settlement?...&couponId=1024`（**query 参数**） |
| 下单 | `POST /trade/order/create` body 中的 `couponId` |

**约束**：
- 券的可用性**只由结算响应的 `coupons[].match` / `mismatchReason` 判定**，前端不得自行判断（FR-026b）。
- 重复领取同一张券时 `take` 接口的行为须实测确认；无论后端如何响应，前端都不得因重复点击而展示成"领取了两张"（FR-026c）。
- **领券中心的入口**（`coupon-template/list`）与**我的券**（`coupon/page`）是两个不同接口，不要混用。

---

## 8. 明确不调用的接口

| 接口族 | 原因 |
|---|---|
| `/promotion/seckill-activity/*`、`combination-*`、`bargain-*` | 秒杀/拼团/砍价不在本期（不做清单） |
| `/promotion/diy-page/*` | DIY 页面装修不在本期 |
| `/promotion/kefu-message/*` | 站内客服不在本期 |
| `/product/comment/*`、`/product/favorite/*`、`/product/browse-history/*` | 设计稿无对应界面 |
| `/trade/delivery/pick-up-store/*` | 自提不在本期（FR-027a） |
| `/trade/brokerage-*` | 分销不在本期 |
| `/pay/wallet/*`、`/pay/wallet-recharge/*` | 余额与充值不在本期 |
| `/member/auth/social-login` | 本期的微信授权只用来**换取 openid**，不用于登录/建号 —— 一律走 `/member/social-user/bind`（见 §6.1）。用 `social-login` 会把当前手机号账号的登录态换成微信账号，语义不对 |
| `/member/auth/create-weixin-jsapi-signature`、`/member/social-user/wxa-qrcode`、`get-subscribe-template-list` | 属于 JSSDK / 小程序路径，本期不调用（§6.1 说明为何用 `WeixinJSBridge` 绕开 JSSDK 签名） |

---

## 9. 文件上传（头像）

本期**唯一**用到的上传能力，服务于个人资料的头像（FR-011b）。

| 用途 | 方法与路径 | 说明 |
|---|---|---|
| 上传文件 | `POST /app-api/infra/file/upload` | **`multipart/form-data`**，字段名 `file`（另有可选 `directory`）；返回 `CommonResult<String>`，**内容就是文件的访问 URL** |

**三个要点**：

- **返回的字符串直接写进 `avatar`** —— `PUT /member/user/update` 的 `avatar` 带 `@URL` 校验，
  所以必须是可访问的 URL，不能传 base64 或本地路径。
- **前端要自己拼 `FormData`**：项目的 `post()` 统一解包 `CommonResult` ✓ 可以复用，
  但需显式带上 multipart 的 `Content-Type`（axios 传 `FormData` 时会自动补 boundary）。
  `tenant-id` 等请求头由统一拦截器注入，无需手写。
- **体积/格式上限由后台的文件上传配置决定，前端不自行设一个数字**：超限时**原样透出后端文案**，
  而不是前端编一个"文件不能超过 X MB"（见 [research.md](./research.md) R11 与 data-model 的缺失项表）。

> **存储位置与前台无关**：线上 master 配置指向阿里云 OSS（`infra_file_config`），前台拿到的就是
> `img.yuwangchenfa.com` 下的 URL。以后要补「退款凭证图片」时，同一套上传控件可复用。
