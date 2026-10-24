# 技术决策与被否决方案（010 订单物流跟踪）

> 承接规格 Q55~Q60。本文件只回答"**怎么做、为什么这么做、为什么不那么做**"。
> 编号续 008（R42~R47）。

---

## R48 —— 快递100 编码怎么落地：**新增 `kd100_code` 列，不动 `code`**

**问题**：`trade_delivery_express.code` 现在存的是 `STO / SF / ZTO / YD / YTO / JD / JTSD / DBL / ANE / UC`
（快递鸟 / 微信风格的短码），而快递100 的 `com` 要 `shentong / shunfeng / zhongtong / yunda / ...`（全拼）。
不对齐，查询必然失败。

**决定性事实**：这一列**不是快递100 专用的**。发货时
`TradeStatusSyncToWxaOrderHandler#uploadWxaOrderShippingInfo` 把它原样当作
`expressCompany` 上送微信小程序（微信要的正是 `SF/STO/ZTO/YD` 这类短码）。
**把 `code` 改成全拼 = 微信物流同步拿到非法公司编码** —— 这是一个今天不看、明天才炸的回归。

**决策**：给 `trade_delivery_express` 加一列 `kd100_code`（可空）：

| 列 | 用途 | 谁读 |
|---|---|---|
| `code`（**不动**） | 微信小程序物流同步的公司编码（也是快递鸟编码） | `TradeStatusSyncToWxaOrderHandler` |
| `kd100_code`（**新增**） | 快递100 的 `com` 编码 | `TradeOrderQueryServiceImpl` → `Kd100ExpressClient` |

- 查轨迹时：`kd100_code` 非空 → 用它；为空 → 抛「暂不支持查询」（FR-261）。
- 后台「快递公司」表单加一个 `kd100_code` 输入项（选填），运营可自行补。
- 数据迁移：给租户 162 的 13 家填上能确认的编码（见 [data-model.md](./data-model.md) §2）。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| **直接改 `code` 为全拼** | 破坏微信小程序物流同步（E11）。这是最危险的"看起来最省事"的做法 |
| 静态 Java 映射表（短码 → 全拼） | 后台加了新快递公司就查不了，且没人知道要去改代码；把可运营配置藏进了代码 |
| 按公司名称映射 | 同上；而且名称更容易出现"圆通快递 / 圆通速递"这类近义变体 |
| 改用快递鸟查询（`code` 已兼容） | 所有者已明确选快递100（Q55），且快递鸟免费版只支持少数快递 |

**遗留**：`安能` 在快递100 有 `ane66`（安能快递）与 `annengwuliu`（安能快运）两个编码，
历史数据只有一个"安能"，**无法确认是哪条产品线** → 按 FR-269 留空、走降级，不猜。

---

## R49 —— 失败怎么让前端区分：**保留错误信封，新增一个"不支持查询"错误码**

**现状**：`TradeOrderQueryServiceImpl#getExpressTrackList` 在失败时抛业务异常，
`Kd100ExpressClient` 还会把**第三方返回的原文**塞进错误文案（`EXPRESS_API_QUERY_FAILED` 的 `{}`）。

**决策**：

1. 新增错误码 `EXPRESS_QUERY_NOT_SUPPORTED(1_011_004_103, "该快递公司暂不支持物流查询")`；
   服务端在 `kd100_code` 为空时抛它，**不发起第三方请求**。
2. 把 `EXPRESS_API_QUERY_FAILED` 的文案改成**不含第三方原文**的通用文案；
   第三方返回的原因改在**服务端日志**里记录（FR-259 / FR-274）。
3. 前端按 `HttpError.code` 分支：
   - `1_011_004_103`（不支持）→ 「暂不支持查询」
   - 其它 → 「暂时无法获取物流信息，请稍后重试」
4. **不改响应结构**（仍是 `CommonResult<List<...>>`）。

**被否决的方案**：

| 方案 | 为什么不选 |
|---|---|
| 改成 `{ supported, tracks }` 对象 | 会同时改 C 端、管理端、小程序三处的解包；为一个错误分支改公共契约，收益不抵风险 |
| 出错就返回空数组 | 与"服务商没返回轨迹"无法区分，正好踩中 FR-260 / FR-261 要求的两种不同提示 |
| 前端直接展示后端 `msg` | 第三方错误原文会漏给客户，违反 FR-259 |

---

## R50 —— 轨迹顺序与缓存：**服务端倒序 + 沿用 1 小时合并窗口**

**决策**：

- 在**被缓存的方法内部**按 `time` **倒序**排序后再返回（规格 FR-253 要求"最新在最上面"）。
  这样 C 端与管理端读到的是同一份顺序，不依赖各端自己排。
- 沿用现有 `@Cacheable` 与 1 小时 TTL（`spring.cache.redis.time-to-live`）。
  合并键改为 `kd100_code + '-' + 运单号 + '-' + 手机号`。

**已知取舍**：`unless = "#result == null"` 意味着**空轨迹也会被缓存 1 小时**。
发货后第一次查询若恰好还没轨迹，客户最多 1 小时后才能看到首批节点。

- 不改成"空结果不缓存"的理由：快递100 限频「同一运单 30 分钟内不重复查询、24 小时 ≤ 48 次」，
  空结果不缓存会让反复打开详情变成反复计费/触发限频。
- 若所有者日后觉得 1 小时太久，只调 TTL 即可，不影响本规格的用户可见行为。

---

## R51 —— C 端落点：**详情自动加载 + 列表入口靠新增 `logisticsNo`**

**详情页**（Q58：自动加载）：

- 订单详情加载完成后，若 `logisticsNo` 非空，自动请求轨迹；不提供手动刷新（避免绕过合并窗口）。
- 物流卡片插在**「收货信息」之后、「买家留言」之前**，与后台详情页的信息顺序一致。
- 加载中显示占位；成功显示时间轴；失败显示 R49 的降级文案；空数组显示「暂无轨迹信息」。

**列表页**（Q59：加入口）：

- 列表项**新增 `logisticsNo` 字段**（`AppTradeOrderPageItemRespVO`），前端只在它非空时显示
  「查看物流」。
- 为什么不用 `status >= 20` 判断：历史导入单里存在"已完成但没有运单号"的订单，
  按状态给入口会把人送到一个没有物流区块的详情页。

**被否决**：列表内联展示轨迹（Q59 已否；且会让列表页对每一行发起计费查询）。

---

## R52 —— 管理端：**只补降级，不加新功能**

- 列表「快递编号 + 查看物流 → 详情时间轴」**维持现状**（Q56）。
- 详情页现在 `await getExpressTrackList(...)` **没有 try/catch**；第三方失败时时间轴不渲染，
  运营看到的是"什么都没有"。改为：捕获 → 显示「暂时无法获取物流信息」；空数组 → 「暂无轨迹信息」。
- 不新增后台"物流查询异常"标记（Q60）。

---

## R53 —— 凭据与日志脱敏

**凭据**：

- 仓库里的 `kd100.customer / key` 是上游演示值，**保持不动**（它不构成生产凭据泄漏）。
- 真实企业授权码写进**服务器上的 `application-my.yaml`**（`yudao-cloud/.gitignore:51` 已忽略），
  通过 `yudao.trade.express.kd100.*` 覆盖；本地开发同理。
- **不把真实值提交到 `application.yaml`**（FR-268）。

**日志**：

- `Kd100ExpressClient#httpRequest` 现在 `log.debug` 打印**完整请求体**，其中含收件人手机号 →
  改为只记录 `customer` 与运单号，**不记录 `param` / `phone`**（FR-274）。
- 服务层在查询失败时记录：`订单号 + 快递公司 + kd100 编码 + 失败摘要`，同样不含手机号。

---

## R54 —— 测试策略（测试先行）

| 层 | 测试 | 先红的原因 |
|---|---|---|
| 服务端 | `TradeOrderQueryServiceImplTest`（`BaseDbUnitTest` + mock `ExpressClientFactory`）：未发货 → 空；`kd100_code` 为空 → 不支持错误且不调三方；成功 → **倒序**；三方失败 → 通用错误；非本人订单 → `ORDER_NOT_FOUND` | 行为断言都能先写成失败 |
| 服务端 | `Kd100ExpressClientTest`（Mockito mock `RestTemplate`）：`result=false` → 抛**通用**错误（断言文案**不含**第三方原文） | 同上 |
| C 端 | `api/order.spec.ts`：`getExpressTrackList` 的路径与参数 |
| C 端 | `OrderDetailView.spec.ts`：有运单号渲染物流卡；无运单号不渲染；轨迹报错显示提示且页面其余可用；空数组显示「暂无轨迹信息」 |
| C 端 | `OrderListView.spec.ts`：`logisticsNo` 非空才出现「查看物流」 |
| 管理端 | `views/mall/trade/order/detail/index.spec.ts`（**新建**）：时间轴渲染；报错显示降级提示 |
| 管理端 | `views/mall/trade/delivery/express/ExpressForm` 不单测；`kd100_code` 的必填/选填由 VO + 手工清单覆盖 |

**无法先红的部分**（与 007/008 同一例外，写进 tasks）：加列 DDL、H2 建表、后台表单加输入项、
服务器配置 —— 它们先落结构，再补行为测试。

---

## R55 —— 数据迁移的写法

- 新文件 `yudao-cloud/sql/mysql/018-express-track-codes.sql`：
  1. `ALTER TABLE trade_delivery_express ADD COLUMN kd100_code varchar(64) NULL`（幂等判断）。
  2. 按 `tenant_id=162 AND code='旧码'` 回填；空码的两家按 `name` 回填。
  3. **不更新 `code` 列**。
- 同步更新测试用 H2 建表 `yudao-module-trade-server/src/test/resources/sql/create_tables.sql`。
- **不修改** `scripts/backfill_legacy_mall.py`（历史一次性脚本，已执行完）；若要重跑，
  必须先补 `kd100_code` 逻辑 —— 在 quickstart 的"前置核对"里写明。
