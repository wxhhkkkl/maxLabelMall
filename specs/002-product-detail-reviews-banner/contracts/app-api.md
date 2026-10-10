# app-api 契约（002）

本期消费的 C 端接口，以及**一处**服务端改动。所有路径以 `/app-api` 为前缀，**只走 `/app-api`**
（管理端 `/admin-api` 零改动）。

> **本文只记本次真正会调用的部分**，不重复 001 `contracts/app-api.md` 已覆盖的接口
> （支付、订单、售后、会员等）。凡本文没写的，本次不碰。

---

## 1. 商品详情 —— 复用，**不改**

`GET /product/spu/get-detail?id=<商品编号>`

- 返回体**已包含** `description`（商品详情富文本），**后端不改、不加字段**。
- 前端本次只改"要不要渲染它"的判定（见 [data-model.md](../data-model.md) §1）。

**约定**：后端不对详情"是否为空"给出标志，**由前端判定**。这是本次修复成立的前提 ——
不需要后端配合。

---

## 2. 评价读取（新增消费）

### 2.1 `GET /product/comment/page`

| 参数 | 必填 | 说明 |
|---|---|---|
| `spuId` | ✅ | 商品编号 |
| `type` | ✅ | `0` 全部 / `1` 好评 / `2` 中评 / `3` 差评。**本期一律传 `0`** |
| `pageNo` / `pageSize` | ❌ | 分页 |

- **无需登录**（该接口对匿名开放）。
- 返回 `PageResult<AppProductCommentRespVO>`。
- **只返回可见的评价** —— 被隐藏的不在返回里。前端**不需要**再过滤一次。

⚠️ **返回顺序：已证实「无排序」**（2026-10-10 查代码，非推测）。
`AppCommentPageReqVO extends PageParam` —— **不是** `SortablePageParam`；`PageParam` 不带默认排序；
`BaseMapperX.selectPage` 的 ORDER BY 完全来自 `sortingFields`，为空则 **SQL 里没有 ORDER BY**。
→ 返回顺序**不确定**；MySQL 实际多半按主键升序，**也就是最早的在前**。

由此定下两条硬约束：
- 契约**不承诺**任何顺序，尤其不得写"返回最近评价"；
- 详情页文案用中性的「用户评价」，**不写**「最新评价」「最近评价」。

👉 **若将来要"真正的最新在前"**：需**再加一处服务端改动**（让该请求 VO 继承 `SortablePageParam`，
或在 mapper 加 `orderByDesc`）。**那超出本次 plan / contracts 的边界 —— 须先改设计，不能顺手做。**

---

## 3. 评价撰写（新增消费）

### 3.1 `POST /trade/order/item/create-comment`

**需要登录**。请求体：

```json
{
  "orderItemId": 123,
  "descriptionScores": 5,
  "benefitScores": 5,
  "content": "字符串",
  "picUrls": ["https://..."],
  "anonymous": false
}
```

返回：新评价的编号（`CommonResult<Long>`）。

**前置条件（后端强制）**：

| 条件 | 失败文案 |
|---|---|
| 订单项属于当前用户 | 交易订单项不存在 |
| 订单属于当前用户 | 交易订单不存在 |
| 订单状态 = **已完成** | 创建交易订单项的评价失败，订单不是【已完成】状态 |
| 订单**整体**尚未评价（订单级 `commentStatus` 为 false） | 创建交易订单项的评价失败，订单已评价 |
| 该订单项未评价过 | 订单的商品评价已存在 |

### 3.2 ⚠️ 校验归属 —— **前端承担，不要误以为后端会挡**

| 规则 | 后端现状 | 本期约定 |
|---|---|---|
| 两个评分在 1~5 | **无 `@Min`/`@Max`**，不校验 | **前端在提交前拦**（FR-075） |
| 内容非空且 ≤1024 | **无 `@Size`**，不校验；只有数据库列宽兜底 | **前端在提交前拦** |
| 图片 ≤9 张 | 有 `@Size(max = 9)` ✅ | 前端也拦一次（给出更早的反馈） |

写这一节的目的就是为了**不再有人以为后端挡住了**。不写清楚，下一个改这块的人会把校验删掉，
然后用户会看到一条数据库层的报错。

👉 **将来可做（本期不做）**：给后端请求 VO 补 `@Min/@Max/@Size`。那会作用于**所有客户**
（含 uniapp），超出本期范围。

### 3.3 ⚠️ 提交成功 ≠ 立刻可见

会员提交的评价**默认不可见**（后端转换器没有设置可见标记，取数据库默认值"隐藏"），
而 §2.1 的读取接口只返回可见的评价。

**所以：用户刚提交完，去商品详情页是看不到自己那条的**，须运营在后台点"显示"。

前端必须做的：**提交成功的提示里写明"审核通过后展示"**（FR-078）。
详见 [research.md](../research.md) R7。

---

## 4. 图片上传 —— 复用，**不改**

`POST /infra/file/upload`（multipart，字段名 `file`），返回文件 URL 字符串。

- 前端已有封装 `src/api/upload.ts` 的 `uploadFile(file)`，**直接复用**。
- 大小上限来自服务端配置（`spring.servlet.multipart.max-file-size`，当前 16MB）；
  **没有格式白名单**。前端**不自行设定数字**，超限时透出后端文案（沿用个人中心头像上传的口径）。

---

## 5. 商城页 banner

### 5.1 `GET /promotion/banner/list?position=<位置值>`

- **无需登录**。返回 `AppBannerRespVO[]`：`{ id, title, url, picUrl, memo }`。
  - `picUrl` 是**产品图**（不是整张横幅）：商城的横幅是**组合式**的 —— 后台配图与文案、
    前端负责排版，所以文字不会随图片被裁掉，窄屏也不会缩成一团。
  - 文案按**换行**拆（2026-10-10 与所有者约定，见前端 `src/utils/banner.ts`）：
    `title` 的每一行是主标题的一行；`memo` 第 1 行是副标题、第 2 行是胶囊（没有第 2 行就不渲染胶囊）；
    品牌行由前端固定（站点自己的名字）。**约定隐晦，但换来零数据结构改动、且内容仍由后台下发。**
  - ⚠️ **`memo` 是本项目补的字段**：上游的 `AppBannerRespVO` 只有 `id/title/url/picUrl`，
    后台虽能填「描述」但 C 端拿不到。已加一行并配了单测
    （`BannerConvertTest` —— MapStruct 对"目标有、源没有"只报 WARN，不写测试发现不了）。
- `url` **可为空** —— 为空时前端**不得跳转**（FR-083）。

⚠️ **`position` 是必传参数**（后端没有默认值），缺失即 400。

⚠️ **该接口不按 `status` 过滤** —— 后端只按 position 等值查询。
也就是说**停用的 banner 仍然会被返回**。这是既有行为，本期**不掩盖、不绕过**：
不在前端加"状态判断"（前端拿不到 status 字段），而是把这条事实记在这里。
👉 若将来需要"停用即不展示"，那是后端的改动，不是前端的。

### 5.2 服务端改动：位置枚举与字典**必须同时**新增

| 改动 | 位置 | 说明 |
|---|---|---|
| 枚举加一项 | `BannerPositionEnum.java` | 新增 `MALL_POSITION(6, "商城页")`。**不加则新值被 `@InEnum` 400 拒绝** |
| 字典加一行 | `system_dict_data`（`dict_type = 'promotion_banner_position'`） | 值 = `6`、标签 = `商城页`。**不加则运营在后台选不到** |

- **管理端前端零改动**：`BannerForm.vue` 的位置是 `getIntDictOptions(DICT_TYPE.PROMOTION_BANNER_POSITION)`
  字典渲染，加完字典自动出现新选项。
- **生产库需要执行一次 INSERT**（见 [quickstart.md](../quickstart.md) 的前置核对）。
- 管理端页面路径：**商城系统 → 营销中心 → 内容管理 → Banner**（既有，本次不新建页面）。

---

## 6. 本期**不调用**的接口（防止实现期顺手扩范围）

| 接口 | 为什么不接 |
|---|---|
| `PUT /promotion/banner/add-browse-count` | 浏览计数是运营指标，本期不加 |
| `POST /product/comment/create`（管理端"自评"） | 运营动作，与 C 端无关 |
| `PUT /product/comment/update-visible`（管理端） | **审核动作由运营在既有后台做**，C 端不接。这正是 Q8"先审后显"的落地方式 |
| `PUT /product/comment/reply`（管理端） | 商家回复仍由后台做，C 端只展示 |
| 评价的"好评/中评/差评"筛选（`type` 传非 0） | 本期只做"全部"，入口与 UI 都不做 |
