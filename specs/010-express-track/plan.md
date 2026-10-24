# Implementation Plan: 订单物流跟踪（对接快递100）

**Branch**: `010-express-track` | **Date**: 2026-10-24
**Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

## Summary

让客户在**发货后**能在 C 端看到真实物流，让运营能在后台回答同一个问题。
服务端的快递100 客户端、两个查询接口、按运单缓存**都已存在**（spec E1~E3），
本特性做的是**接线 + 让它真正可用**：

1. **编码先对齐** —— 现有快递公司 `code` 是微信 / 快递鸟短码，快递100 要全拼；
   **不能改 `code`**（微信物流同步在读它，E11），改为**新增 `kd100_code` 列**（R48）。
2. **C 端补展示** —— 订单详情插入物流卡（自动加载、倒序轨迹）；
   订单列表在有运单号时给「查看物流」入口（Q58 / Q59）。
3. **失败有口径** —— 编码缺失、三方报错、空轨迹分别给不同提示；
   第三方原文只进服务端日志，不进页面（R49 / R53）。
4. **管理端只补降级** —— 列表入口与时间轴维持现状（Q56），补上 try/catch 与空态提示（R52）。

## Technical Context

**语言 / 栈**：Java 17+（yudao-cloud 单体聚合启动）+ Vue 3 / TypeScript（`storefront`、`yudao-ui-admin-vue3`）
**数据**：MySQL。**唯一 DDL 是 `trade_delivery_express` 加一列**（不建表、不动 `trade_order`）；
轨迹走 Redis 缓存（1 小时），**不落库**
**测试**：服务端 JUnit（`BaseDbUnitTest` + Mockito）、C 端 / 管理端 vitest
**规模**：量级很小（按订单详情打开触发的按需查询），无吞吐诉求
**约束**：C 端只走 `/app-api`、管理端只走 `/admin-api`、不引入 UI 组件库（宪法）；
真实快递100 凭据只放服务器 `application-my.yaml`，不进仓库

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 行为都能先写成失败测试（R54）：未发货 → 空；编码空 → 不支持；成功 → 倒序；三方失败 → 通用错误；越权 → `ORDER_NOT_FOUND`；C 端渲染/不渲染/降级；列表入口。⚠️ 与 007/008 同样的例外：**加列 DDL、H2 建表、VO 新字段（`logisticsNo` / `kd100Code`）、后台表单加输入项**无法"先红"，先落结构再补行为测试（tasks 里逐条标注） |
| **II. 先澄清，不猜测** | ✅ Q55~Q60 已拍板。规划期发现 E11（`code` 同时供微信使用）**已写回规格**并调整 FR-269 口径；安能的两种产品线无法确认 → **留空**，不猜（R48） |
| **III. 复用既有能力** | ✅ 复用快递100 客户端、C 端/管理端查询接口、Redis 缓存、后台订单列表入口、后台快递公司管理页。**新增面只有两样"确实没有"的**：`kd100_code` 列、一个"不支持查询"错误码 |
| **IV. 最小改动** | ✅ 不加表、不加缓存层、不改响应结构、不做列表内联展开（Q56）、不做手动刷新（Q58）。`code` 列**一个字节不动** |
| **V. 增量可验证** | ✅ US35（C 端）与 US36（后台）可独立验收；每个 SC 在 [quickstart.md](./quickstart.md) 有落点；**不验证项已显式列出** |

**Complexity Tracking**：无违规。不引入新依赖、不引入新抽象层。

## 关键设计决策（详见 [research.md](./research.md)）

1. **R48 新增 `kd100_code`，不改 `code`** —— 修 `code` 会破坏微信小程序物流同步（E11）。
   这是本特性**最容易踩的坑**。
2. **R49 保留错误信封，新增 `EXPRESS_QUERY_NOT_SUPPORTED`** —— 不改 `CommonResult<List>` 结构，
   避免同时改 C 端 / 管理端 / 小程序三处解包。
3. **R50 服务端倒序 + 沿用 1 小时缓存** —— 顺序只有一处实现；空结果也缓存，
   代价是"发货后首批轨迹最多晚 1 小时"，为避免限频/计费的取舍。
4. **R51 列表入口靠新增 `logisticsNo`** —— 不能用状态判断，历史导入单里存在"已完成但无运单号"。
5. **R53 日志脱敏** —— 现有客户端 debug 日志会打印含手机号的完整请求体，必须收敛。

## Project Structure

```text
specs/010-express-track/
├── spec.md          # 已完成（Q55~Q60）
├── plan.md          # 本文件
├── research.md      # R48~R55
├── data-model.md    # 加列 + 13 家编码 + 展示资格
├── contracts/
│   ├── app-api.md   # 轨迹 / 列表 logisticsNo / 详情字段
│   └── admin-api.md # 轨迹 / 快递公司 kd100Code
├── quickstart.md    # 手工验收（含显式不验证）
└── tasks.md         # /speckit-tasks 产出
```

### 代码落点

```text
yudao-cloud/
├── sql/mysql/018-express-track-codes.sql                  # 加列 + 租户 162 回填
├── yudao-module-mall/yudao-module-trade-api/
│   └── enums/ErrorCodeConstants.java                      # 加 EXPRESS_QUERY_NOT_SUPPORTED
├── yudao-module-mall/yudao-module-trade-server/
│   ├── dal/dataobject/delivery/DeliveryExpressDO.java     # 加 kd100Code
│   ├── controller/admin/delivery/vo/express/
│   │   ├── DeliveryExpressBaseVO.java                     # 加 kd100Code（选填）
│   │   └── DeliveryExpressSimpleRespVO.java               # 精简列表回带 kd100Code（后台判断能否查）
│   ├── controller/app/order/vo/AppTradeOrderPageItemRespVO.java  # 加 logisticsNo
│   ├── service/order/TradeOrderQueryServiceImpl.java      # 编码校验 / 倒序 / 失败日志
│   ├── framework/delivery/core/client/impl/kd100/Kd100ExpressClient.java # 通用错误 + 去手机号日志
│   └── test/
│       ├── service/order/TradeOrderQueryServiceImplTest.java                 # 新建
│       ├── framework/delivery/core/client/impl/kd100/Kd100ExpressClientTest.java # 新建
│       └── resources/sql/create_tables.sql                # H2 加 kd100_code
├── storefront/src/
│   ├── api/order.ts · api/order.spec.ts                   # 加 getExpressTrackList
│   ├── types/order.ts                                     # OrderPageItem 加 logisticsNo；轨迹类型
│   ├── views/OrderDetailView.vue · .spec.ts               # 物流卡 + 自动加载 + 降级
│   ├── views/OrderListView.vue · .spec.ts                 # 「查看物流」入口
│   └── utils/expressTrack.ts · .spec.ts                   # 错误码 → 文案映射
└── yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/
    ├── api/mall/trade/order/index.ts                      # 轨迹类型（已有函数）
    ├── views/mall/trade/order/detail/index.vue · index.spec.ts  # 补 try/catch + 空/错提示，spec 新建
    ├── api/mall/trade/delivery/express/index.ts           # VO 加 kd100Code
    └── views/mall/trade/delivery/express/ExpressForm.vue  # 「快递100 编码」输入项
```

## 实施分期

| 阶段 | 内容 | 交付后能做什么 |
|---|---|---|
| **① Foundational** | `018` SQL（加列 + 回填）+ DO/VO + 错误码 + 查询服务（空编码拦截 / 倒序 / 失败日志）+ 客户端日志脱敏 + 后台快递公司表单字段 | 服务端能对**正确编码**的运单查到轨迹；编码缺失有明确错误 |
| **② US35（C 端）** | `api/order.ts` + 类型 + 订单详情物流卡 + 列表「查看物流」+ 三个 spec | **客户能在发货后看到物流**（本特性的主价值） |
| **③ US36 / US37（后台与降级）** | 后台详情 try/catch + 空/错提示 + spec；两端错误码映射统一；quickstart 走查 | 运营能看到同一份轨迹；异常时两端都不误导 |

## 主要风险与对策

| # | 风险 | 对策 |
|---|---|---|
| **R48** | **把 `code` 改成全拼** → 微信小程序物流同步拿到非法编码 | DDL/回填**只写 `kd100_code`**；后台表单的 `code` 字段说明保持"微信同步用"；quickstart 五、1 专项核对 |
| **R48b** | **先回填、后加列**（会报表不存在）；或重跑 `backfill_legacy_mall.py` —— 它只写 `code`，不知道 `kd100_code` | SQL 文件先 `ALTER` 再 `UPDATE`；quickstart §零 注明：重跑脚本后要再执行一次 018 的回填段，并先确认没有重复插入快递公司（脚本本身不动，R55） |
| **R49** | 第三方错误原文漏给客户 | 错误码文案不含 `{}`；`Kd100ExpressClientTest` 断言"文案不含第三方原文"；前端不展示后端 `msg` |
| **R50** | 空轨迹被缓存 1 小时，客户以为"没物流" | 前端明确显示「暂无轨迹信息」；接受该窗口；若不可接受只调 TTL，不改契约 |
| **R51** | 列表按状态给入口，点进去没有物流区块 | 只认 `logisticsNo`（新字段），并用测试钉住"已完成但无运单号 → 不显示" |
| **R52** | 后台详情静默失败（现有代码没有 try/catch） | 加 catch + 空态/错态文案；新建 detail spec |
| **R53** | 日志里有收件人手机号 | 去掉 `param` / `phone` 日志；服务层只记订单号/公司/编码/摘要；quickstart SC-096 |
| **R54** | H2 测试建表缺 `kd100_code` → 测试成片失败 | `create_tables.sql` 同步加列；改 DO 前先改它 |
| **R55** | 真实凭据被提交 | 只在服务器 `application-my.yaml`（gitignored）；仓库保留演示值 |

## Post-Design Re-check（宪法再核）

- 原则 I ✅ 行为测试可先红；DDL/表单/配置属于已声明的结构性例外
- 原则 III ✅ 新增面只有一列 + 一个错误码；客户端、接口、缓存、后台入口全部复用
- 原则 IV ✅ 不加表、不改 `code`、不改响应结构、不做列表内联展开
- 原则 V ✅ SC-088~096 全部在 quickstart 有落点；六类不验证项已显式声明

## 追加：物流地图（2026-10-24 所有者拍板）

- **R56**：用快递100 `maptrack.do`（`trailUrl`）而非自绘地图；前端 iframe 内嵌。
  必须同时有 `from`（单一发货仓）/`to`（`receiver_area_id`）；历史单无 `area_id` 不显示地图。
  地图查询独立计费，只在点击时调用，服务端 `express_map` 缓存 1 小时。
