# Data Model: 商品详情、评价（展示与撰写）与商城页 banner

本文只记**前端要建模的东西**，以及每个字段的来源与真实性判断。后端实体不在本文重复抄写 ——
凡是后端已有的字段，以接口返回为准。

---

## 1. 商品详情内容

**来源**：`GET /app-api/product/spu/get-detail` 返回体的 `description`（富文本字符串，可为空）。

| 项 | 说明 |
|---|---|
| 字段 | `description?: string` —— **已存在**于 `storefront/src/types/product.ts`，本次不改类型 |
| 加工 | `sanitizeRichText()` 白名单清洗（既有，**不改规则**） |
| 派生 | `hasVisibleContent()` —— **本次新增**，判定"清洗后是否还有可见内容" |
| 真相方 | 内容本身由后端给；**"有没有内容"由前端判**（后端只给原始富文本，不给"是否为空"的标志） |

### 派生规则：`hasVisibleContent(clean): boolean`

清洗后的 HTML 算作"有可见内容"，当且仅当满足任一：

1. 含至少一个 `<img>` 元素；**或**
2. 含至少一个**非空白**文本节点（即 `textContent.trim()` 非空）。

不算有内容的：空串、纯空白、一连串空壳标签（`<p></p>`、`<div></div>`、`<br>`）、
以及被清洗规则整体移除后剩下的空串。

> **为什么这条规则是本期的核心**：原判定是"剥掉所有标签后看还剩不剩文字"，纯图片详情必然判空。
> 新判据把"图片"与"文字"**同等对待**（FR-064）。

### 显示状态机

| 输入 | 清洗后 | 判定 | 渲染 |
|---|---|---|---|
| 未录入 / 全空白 | `''` | 无内容 | **不渲染区块** |
| 只有 `<img>` | 原样保留 | **有内容** | **渲染**（这是当前的失败类） |
| 只有空壳标签 | 原样保留 | 无内容 | 不渲染区块 |
| 只有被禁标签（如 `<script>`） | `''` | 无内容 | 不渲染区块 |
| 纯文本 / 图文混排 | 原样 | 有内容 | 渲染 |

---

## 2. 商品评价

**来源**：`GET /app-api/product/comment/page`（读）、`POST /app-api/trade/order/item/create-comment`（写）。

### 2.1 读模型 `ProductComment`（新增 `src/types/comment.ts`）

后端返回体（`AppProductCommentRespVO`）字段很多，前端**只建模要展示的**，其余不声明
（避免建成"后端有啥我抄啥"的镜像类型）。本次展示所需：

| 字段 | 类型 | 用途 | 备注 |
|---|---|---|---|
| `id` | `number` | 列表 key | |
| `userNickname` | `string` | 评价人昵称 | **匿名时后端已替成「匿名用户」**，前端**不得**再改写 |
| `userAvatar` | `string` | 头像 | |
| `scores` | `number` | 总评分 | 后端由两个维度算出 |
| `descriptionScores` | `number` | 商品质量评分 | 1~5 |
| `benefitScores` | `number` | 服务态度评分 | 1~5 |
| `content` | `string` | 评价内容 | |
| `picUrls` | `string[]` | 评价图片 | |
| `replyContent` | `string?` | 商家回复内容 | 有则展示 |
| `createTime` | `number \| string` | 评价时间 | **epoch 毫秒或可读字符串** —— 一律过 `utils/time.ts` 的 `formatDateTime`，**不得直接插值** |

> ⚠️ 关于 `anonymous`：后端返回里**有**这个字段，但**昵称打码已经在后端完成**。
> 前端不需要、也**不应该**根据它再做什么 —— 拿到的 `userNickname` 就是该展示的值。

### 2.2 写模型（新增 `src/api/tradeComment.ts` 的请求体）

| 字段 | 类型 | 必填 | 前端校验 |
|---|---|---|---|
| `orderItemId` | `number` | ✅ | 由入口带入，不由用户填 |
| `descriptionScores` | `number` | ✅ | **1~5**，必须选中（默认 5 星） |
| `benefitScores` | `number` | ✅ | **1~5**，同上 |
| `content` | `string` | ✅ | 去空白后非空，且 **≤1024** 字符（与数据库列宽一致） |
| `picUrls` | `string[]` | ❌ | **≤9 张**（与后端 `@Size(max=9)` 一致） |
| `anonymous` | `boolean` | ✅ | 默认 `false`，由复选框给出 |

**这些校验全部由前端承担** —— 后端当前**不校验**评分范围与内容长度（只有数据库列宽兜底）。
详见 [research.md](./research.md) R9 与 [contracts/app-api.md](./contracts/app-api.md) §3。

### 2.3 提交前置条件（后端强制，前端须据此决定入口是否出现）

| 条件 | 后端错误码 / 文案 | 前端依据 |
|---|---|---|
| 订单项属于当前用户 | `ORDER_ITEM_NOT_FOUND` 交易订单项不存在 | 入口只出现在自己的订单上 |
| 订单属于当前用户 | `ORDER_NOT_FOUND` 交易订单不存在 | 同上 |
| **订单状态 = 已完成** | `ORDER_COMMENT_FAIL_STATUS_NOT_COMPLETED` 创建交易订单项的评价失败，订单不是【已完成】状态 | 只有 `status === 30` 才给入口 |
| **订单整体尚未评价** | `ORDER_COMMENT_STATUS_NOT_FALSE` 创建交易订单项的评价失败，订单已评价 | 订单级 `commentStatus` 为 `false` 才给入口 |
| 该订单项未评价过 | `COMMENT_ORDER_EXISTS` 订单的商品评价已存在 | **订单项级** `commentStatus` 为 `false` 才给入口 |

> **注意后两条的层级不同**，这是实现时最容易搞错的地方：后端拦的是**订单级**的
> `commentStatus`，但重复提交是 **product 层**按 `(userId, orderItemId)` 拦的。
> 所以前端要**同时**看订单级与订单项级两个标记，只判一个就会出现"必然被拒的按钮"。

### 2.4 评价状态（前端不引入状态机）

评价没有前端侧的状态流转。**唯一需要前端表达的"状态"是"提交后仍不可见"** ——
这不是状态，是一句**必须出现的提示文案**（FR-078）：

> 评价已提交，**审核通过后展示**

**理由**：后端默认把会员提交的评价落成"不可见"，C 端只读接口只返回可见的（Q8）。
前端不改这一点，但必须**说清楚**，否则用户会以为提交失败而重复提交 —— 第二次会被后端以
"订单已评价"拒绝，体验更差。

---

## 3. 订单的"待评价"标记（**补建模，不是新增字段**）

| 层级 | 字段 | 现状 |
|---|---|---|
| 订单 | `commentStatus: boolean` | 后端**已返回**（列表与详情都有），但 `storefront/src/types/order.ts` **没有声明** → 本次补上 |
| 订单项 | `commentStatus: boolean` | 同上，内联的 item 类型里也缺 → 本次补上 |

> 这是"**后端有、前端没建模**"，不是后端缺字段。补类型即可，不需要任何接口改动。
> 补完才能实现 FR-073 的"入口出现/不出现"。

派生（前端计算，不来自后端）：

```
可评价(订单, 订单项) =
    订单.status === 已完成(30)
 && 订单.commentStatus === false
 && 订单项.commentStatus === false
```

---

## 4. 商城 banner

**来源**：`GET /app-api/promotion/banner/list?position=<N>`。

| 字段 | 类型 | 用途 |
|---|---|---|
| `id` | `number` | key |
| `title` | `string` | 图片的 `alt`（**不是**展示文案） |
| `picUrl` | `string` | 横幅图片 |
| `url` | `string` | 点击跳转地址，**可为空** |

### 4.1 位置取值（本次新增一个）

后端 `BannerPositionEnum` 与字典 `promotion_banner_position` **必须同时**有这一项，
否则：字典没有 → 运营在后台选不到；枚举没有 → 值提交上去被 `@InEnum` 400 拒绝。

| 值 | 含义 | 状态 |
|---|---|---|
| 1 | 首页 | 既有 |
| 2~5 | 秒杀 / 砍价 / 限时折扣 / 满减送 | 既有 |
| **6** | **商城页** | **本次新增** |

> ⚠️ 该接口**不按状态过滤**（后端只按 position 等值查询，不看 `status`）。
> 也就是说：**停用的 banner 也会被返回**。这是既有行为，本期不掩盖 —— 在
> [contracts/app-api.md](./contracts/app-api.md) 里写明，避免以后有人误以为"停用能挡住"。

### 4.2 展示状态

| 数据 | 渲染 |
|---|---|
| 0 条 | **整块不渲染**（不留占位、不撑高） |
| 1 条 | 渲染单条，**不出现**圆点与箭头 |
| ≥2 条 | 渲染轮播，出现圆点与箭头 |

| 单条情况 | 行为 |
|---|---|
| 有 `url` | 整块可点击跳转 |
| 无 `url` | **不可点击**（不跳转、不报错） |
| 图片加载失败 | 只影响该图，不得撑破布局（容器有固定高度） |
