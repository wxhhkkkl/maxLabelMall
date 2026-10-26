# Implementation Plan: MaxLabel 桌面端接入商城会员体系

**Branch**: `009-maxlabel-member-integration` | **Date**: 2026-10-20 |
**Spec**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

## Summary

把 `maxlabel`（Electron 桌面端）与 `maxLabelMall`（yudao 商城）打通成**一个身份、一份订阅、
一套模板库、一份打印记录**：

- 桌面端用**商城会员账号**登录（复用商城登录页：验证码 / 密码 / 微信扫码），登录态加密落盘；
- 商城新增**会员授权商品**（单一产品、月/年周期，虚拟商品），**支付成功自动开通/顺延订阅**；
- 桌面端上传模板到**云端**（私有），可提交到**平台共享库**（运营审核后公开），过期订阅对云端写入只读；
- 桌面端可打开商城购买其他商品，登录态自动带入；
- 桌面端把**打印记录**（不含数据快照）上报商城，后台会员详情新增**订阅 / 标签模板 / 打印记录**三个页签，
  并提供**共享模板审核**与**会员授权商品**两个运营页。

技术上是**新增 5 张表 + 1 个 trade 支付后处理器 + 1 组 member 模块接口 + 1 组 product 计划接口
+ 桌面端一组 IPC/窗口**，不改打印流水线、不改模板格式、不新增部署单元。
旧云账户与旧云模板入口按 Q52 **下线**（代码删除待确认）。

## Technical Context

**语言 / 栈**：
- 服务端：Java 17+ / Spring Boot / yudao-cloud（MyBatis-Plus、MySQL 8、Redis）
- 商城前端：Vue 3 + Vite + TypeScript（`storefront`、`yudao-ui-admin-vue3`）
- 桌面端：Electron + React 18 + TypeScript（`../maxlabel/app`，Node ≥ 22.12）

**存储**：MySQL 8，新增 5 张表；模板正文用 `LONGTEXT` 存在自有表（**不用**公共 `infra_file` URL）；
会员令牌 / 订阅状态由 member 模块持有。

**测试**：
- 服务端：JUnit 5 + `BaseDbUnitTest`（H2 `MODE=MYSQL`），新表需同步到对应模块
  `src/test/resources/sql/create_tables.sql` 与 `clean.sql`
- 商城前端：Vitest（storefront / admin）
- 桌面端：Vitest unit / contracts / integration，HTTP 通过 `vi.mock('@main/net')`
- E2E：Playwright 可选用；本期主验证放在服务端集成测试 + quickstart 手工链路

**目标平台**：Linux 服务端（单体 `yudao-server.jar`，模块仍可独立运行）；Windows 桌面端。

**项目类型**：Web 服务 + 两个 Web 前端 + Electron 桌面客户端（跨两个仓库）。

**性能目标**：
- 订阅开通在支付回调一次处理内完成；回调重试不重复累加（SC-078）
- 会员详情三个页签均分页（`pageSize ≤ 200`），打印记录按 `(member_id, print_time)` 走索引
- 模板单文件 ≤ 16 MB，单会员 ≤ 1000 个 / ≤ 256 MB

**约束**：
- C 端只走 `/app-api`、管理端只走 `/admin-api`（宪法）
- 新接口放在既有 `/member/**` 与 `/product/**` 下，复用既有网关路由，**不新增网关规则 / 部署单元**
- 桌面端未登录/断网时本地编辑与打印完全可用（回归 SC-083）
- 所有 app-api 请求带 `tenant-id`（生产 162）与 `terminal`（桌面端用 31 App）

**规模**：会员量级按商城现有量；模板单会员上限 1000 个；打印记录**永久保留**（Q53），
靠分页与索引控制查询成本。

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 可先写的：订阅续费算法、幂等（同一订单项只生效一次）、过期只读、共享审核状态机、打印字段白名单、桌面端令牌刷新与上报跳过测试打印。⚠️ 例外同 007/008：**建表与枚举值本身无法先红**，先落结构、再写行为测试 |
| **II. 先澄清，不猜测** | ✅ 规格已澄清 Q48~Q54；计划期又核实了支付回调、鉴权头、文件公开性、令牌刷新、旧云依赖。**新发现 1 个需所有者确认的副作用**：旧「云数据库导入」依赖旧云账户，随 Q52 下线（见 X6） |
| **III. 复用既有能力** | ✅ 复用会员登录/令牌、虚拟商品结算、交易支付回调、后台会员详情页、`system_menu` 权限、`@LogRecord`、分页与 BaseDO。**不重复实现**账号、订单、支付、文件、权限。新增只有 5 张确实不存在的表与 1 个支付后处理器 |
| **IV. 最小改动** | ✅ 不给 `trade_order` 加列、不改订单状态机；模板正文不进 `infra_file`；桌面端不新建账号体系，直接复用商城登录页。⚠️ 下线旧云账户/模板入口是 Q52 明确要求，不是顺手重构 |
| **V. 增量可验证** | ✅ US28（登录）、US29（购买开通）、US30/31（模板与共享）、US32（商城入口）、US33/34（后台）可独立演示；不自动化验证的项在 quickstart 显式列出 |

**Complexity Tracking**：

| 项 | 为什么需要 | 更简单的替代为何不可行 |
|---|---|---|
| 新增 5 张表 | 订阅、计划映射、私有模板、共享副本、打印记录都是净新增实体 | 复用 `member_level` 表达订阅会污染等级语义；复用 `infra_file` 无法做归属与私有读取 |
| trade-server 新增支付后处理器 | `afterPayOrder` 是订单转已支付的唯一事务内扩展点，且已有积分/分销先例 | Spring 事件仅进程内，模块拆分会丢；轮询任务引入新调度 |
| 桌面端内嵌登录窗口 | 复用商城完整登录（验证码/密码/微信 + 首登补全 + 图形验证码），避免在 Electron 重做一套 | 原生表单要复刻验证码/微信/补全资料/UUID 流程，风险与代码量都更大 |

## 关键设计决策（详见 [research.md](./research.md)）

1. **R48 支付成功开通挂在 `TradeOrderHandler.afterPayOrder`**：与订单置为已支付同一事务；
   发放接口用 `(tenant_id, source_order_item_id)` 唯一键做幂等，回调重试不会重复累加。
2. **R49 商品 → 计划用独立映射表** `maxlabel_subscription_plan(sku_id → period_months)`：
   表放 **product-server**，通过 `ProductSubscriptionPlanApi` 暴露；trade-server 已依赖
   product-api，发放前解析周期。一个会员 SPU、多个周期 SKU；启用计划必须**仅虚拟商品**且
   **同一租户只有一个启用 SPU**。订单项只快照 `spu_id/sku_id`，发放时落周期快照，
   后续改价改配置不影响历史。
3. **R50 订阅是「授予流水」**：每次支付插一行 `member_subscription`；
   `startDate = max(today, 上段 end_time 的日期 − 1 天)`（**原到期日**；`end_time` 已是最后有效日 + 1 天），
   `endDate = startDate + period × count`；
   `end_time` 为最后有效自然日的**次日 00:00（不含当日）**，有效/过期由 `end_time > now` 推导，
   不维护额外状态机。退款只打 `refunded` 标记（整单 `afterCancelOrder` / 订单项
   `afterCancelOrderItem`），不缩短周期。
4. **R51 模板正文存自有表 `LONGTEXT`**：`infra_file` 的下载端点是 `@PermitAll` 且无归属，
   私有模板不能走它；单文件 ≤ 16 MB、`LONGTEXT` 避免 `MEDIUMTEXT` 的 16 MiB-1 边界。
5. **R52 共享模板是「清洗后的副本」**：提交时服务端解析 msdx，剥掉数据库口令、RFID 读写口令、
   数据集行，写入 `maxlabel_shared_template`；原私有模板不动，审核状态独立。
6. **R53 桌面端登录用内嵌门面站窗口**：加载商城同源登录页，主进程在该窗口内读取
   `MAXLABEL_ACCESS_TOKEN`/`MAXLABEL_REFRESH_TOKEN` 并加密保存；商城窗口共用默认 session，
   进入商城**不需要二次登录**。⚠️ 旧「云数据库导入」依赖旧云账户，随旧体系下线，需确认。
7. **R54 购买入口用 `/membership` 页**：桌面端「购买/续费」打开商城 `/membership`，
   该页从 `GET /product/subscription-plan/list` 动态渲染周期卡片并跳到既有商品详情/结算。
8. **R55 打印上报在主进程 `log:print` 之后**：本地日志成功后 fire-and-forget 上报，
   只取白名单字段、跳过测试打印、失败只保留本地；开关默认开、可关。
9. **R56 后台在既有会员详情扩展**：新增三个懒加载页签；共享审核与计划维护是 `system_menu`
   驱动的独立页，权限点必须插菜单行，否则超管也看不到按钮。
10. **R57 旧体系下线范围**：旧云账户登录、旧云模板、旧云窗口入口下线（代码删除待确认）；
    既有授权码校验保留（FR-229）。
11. **R60 桌面端 HTTP 客户端补 `PUT` 与可配置响应上限**：`net.ts` 现在只有 GET/POST/DELETE，
    且响应硬上限 16 MB；模板下载需 32 MB 上限，否则"能上传、不能下载"。

## Project Structure

### Documentation (this feature)

```text
specs/009-maxlabel-member-integration/
├── spec.md              # 已完成（Q48~Q54）
├── plan.md              # 本文件
├── research.md          # R48~R60
├── data-model.md        # 5 张新表 + 发放算法 + 状态/清洗规则
├── contracts/
│   ├── app-api.md       # 桌面端 / 门面站：登录、订阅、模板、共享、打印上报
│   └── admin-api.md     # 管理端：订阅、模板、共享审核、打印记录、计划维护
├── quickstart.md        # 手工验收 + 显式不验证项
└── tasks.md             # /speckit-tasks 产出
```

### Source Code — maxLabelMall

```text
yudao-cloud/
├── yudao-module-member/yudao-module-member-server/
│   ├── controller/app/subscription/          # 当前订阅
│   ├── controller/app/template/              # 私有云端模板、共享模板
│   ├── controller/app/printrecord/           # 打印记录上报
│   ├── controller/admin/subscription/        # 会员订阅查询；会员列表批量订阅列
│   ├── controller/admin/template/            # 会员模板、共享审核
│   ├── controller/admin/printrecord/         # 打印记录查询
│   ├── service/subscription/                 # 发放/顺延/过期判定
│   ├── service/template/                     # 私有模板、共享清洗与审核
│   ├── service/printrecord/                  # 上报校验与查询
│   ├── dal/dataobject/{subscription,template,printrecord}/
│   ├── dal/mysql/{subscription,template,printrecord}/
│   └── convert/…
├── yudao-module-member/yudao-module-member-api/
│   └── api/subscription/                     # MemberSubscriptionApi（供 trade 调用）
├── yudao-module-mall/yudao-module-product-server/
│   ├── controller/app/subscriptionplan/      # 可售周期（匿名）
│   ├── controller/admin/subscriptionplan/    # 计划维护
│   ├── service/subscriptionplan/             # SKU 归属/价格/周期校验
│   └── dal/{dataobject,mysql}/subscriptionplan/
├── yudao-module-mall/yudao-module-product-api/
│   └── api/subscriptionplan/                 # ProductSubscriptionPlanApi（供 trade 调用）
├── yudao-module-mall/yudao-module-trade-server/
│   └── service/order/handler/                # TradeMaxLabelSubscriptionOrderHandler
│                                             # + RpcConfiguration 注册新 api
├── yudao-module-mall/yudao-module-trade-server/pom.xml  # 已依赖 member-api
└── sql/mysql/014-maxlabel-member.sql         # 5 张表 + 字典 + 菜单/权限（幂等）

storefront/src/
├── views/MembershipView.vue                  # /membership 周期卡片
├── api/subscription.ts                       # 计划列表
└── router/index.ts                           # 新路由 + 设计一致性清单

yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/
├── views/member/user/detail/                 # 三个新页签
├── views/member/template/review/             # 共享模板审核
├── views/member/subscription/plan/           # 会员授权商品维护（调用 product API）
├── api/member/{subscription,template,printRecord}/
├── api/mall/product/subscriptionPlan/        # 计划维护（product 模块）
└── router/…（菜单由 SQL 驱动，仅需组件路径）
```

### Source Code — maxlabel（同级仓库）

```text
app/src/
├── main/
│   ├── mallAuth.ts                           # 登录窗口、令牌读取/加密/刷新
│   ├── mallRepository.ts                     # /member/** HTTP 调用（net.ts）
│   ├── mallCredentials.ts                    # 加密令牌 + 会员快照
│   ├── mallService.ts                        # 门面站窗口（登录/商城）
│   ├── printReport.ts                        # 打印记录上报
│   └── ipc/registerMallIpc.ts                # 新 IPC
├── shared/ipcContract.ts                     # 新通道与 DTO
├── preload/index.ts                          # facade
└── renderer/src/
    ├── dialogs/MallAccountDialog.tsx         # 会员状态 / 续费 / 退出
    ├── dialogs/CloudTemplateDialog.tsx       # 替代旧 CloudDialog 的模板管理
    ├── dialogs/TemplateLibDialog.tsx         # shared 页签改走商城
    ├── pages/StartPage.tsx                   # 账号区 / 商城 / 会员入口
    └── features/commands/labelShopMenus.ts   # 菜单
```

**Structure Decision**：服务端沿用 yudao 现有 module-server 分层，新业务全部放在
`yudao-module-member-server` 的 `/member/**` 命名空间，避免新网关路由；支付后处理器放在
`trade-server`（它已依赖 `member-api`）。桌面端新增一组主进程服务与 IPC，渲染层只消费 facade。

## 实施分期

| 阶段 | 内容 | 交付后能做什么 |
|---|---|---|
| **① 地基** | 5 张表 + DO/Mapper + 枚举 + 计划映射（product）+ `ProductSubscriptionPlanApi` + 订阅服务/API + trade 处理器 + 测试 DDL | 支付一笔映射过的订单，订阅能自动落库并顺延 |
| **② US28 登录** | 桌面端登录窗口/令牌加密/刷新、会员状态对话框、旧云登录入口下线 | 桌面端用商城账号登录并显示订阅状态 |
| **③ US29 + US33** | `/membership` 页、计划维护页、后台订阅页签、**会员列表订阅列（批量填充）**、购买/续费入口 | 买会员 → 自动开通 → 后台可见周期 |
| **④ US30 + US31** | 模板/共享表与接口、清洗与审核、桌面模板管理、共享库、后台模板页签/审核页 | 上传、下载、共享、审核闭环 |
| **⑤ US32** | 商城窗口、登录态同步、启动页/菜单入口 | 桌面端逛商城并保持登录 |
| **⑥ US34** | 打印上报、设置开关、后台打印记录页签 | 后台按会员查看打印记录 |
| **⑦ 收口** | 旧云入口隐藏（代码删除待所有者确认）、回归、文档、quickstart | 只保留新体系入口，旧授权码仍可用 |

## 主要风险与对策

> 风险编号 **X1~X13** 是执行风险编号，独立于 research.md 的技术决策编号 **R48~R60**；
> 两者不要混用。

| # | 风险 | 对策 |
|---|---|---|
| **X1** | 支付回调重试导致订阅重复累加 | `uk_source_item (tenant_id, source_order_item_id)`；重复键视为成功；handler 内不读 `order.payStatus` |
| **X2** | 0 元订单不走 `updateOrderPaid`，会员不开通 | 计划维护页校验 SKU 价格 > 0；quickstart 明确 0 元单不在本期 |
| **X3** | 桌面端与门面站同时刷新令牌互相踢掉 | 主进程持有刷新令牌；刷新后把新令牌写回已打开商城窗口；窗口内刷新时主进程重新读取 |
| **X4** | 私有模板经 `infra_file` 公开 URL 泄露 | 正文存自有表，下载走鉴权接口；不返回 `infra_file.url` |
| **X5** | 共享副本仍带私有数据 | 服务端二次清洗（连接口令、RFID 口令、数据集行）；桌面端同时扩展 redaction |
| **X6** | 旧「云数据库导入」依赖旧云账户，随之下线 | **需所有者确认**；默认先隐藏入口、保留代码一版，确认后再删；quickstart 显式列出 |
| **X7** | 桌面端缺 `tenant-id` 直接 400 | 所有 mall 请求统一注入 `tenant-id` + `terminal:31`；契约测试断言 |
| **X8** | 权限点未插菜单行，超管也看不到按钮 | 迁移 SQL 同时插 page + button 行；后台冒烟验证 |
| **X9** | 16 MB 边界 / `max_allowed_packet` | 服务端与桌面端双层 16 MB 校验；`LONGTEXT`；上传失败不删本地 |
| **X10** | 打印记录永久保留导致详情页慢 | 会员详情按 `(member_id, print_time)` 索引 + 强制分页；不做全量导出 |
| **X11** | 微信扫码依赖公众号授权域名 | 桌面端不自己实现扫码，直接用门面站登录窗口；域名问题沿用既有商城配置 |
| **X12** | 新表未同步测试 DDL，模块测试起不来 | 每个模块的 `create_tables.sql` / `clean.sql` 与迁移同批提交 |
| **X13** | 桌面端 HTTP 客户端不支持 PUT；16 MB 响应上限导致模板能传不能下 | 扩展 `net.ts`（PUT + 可选 `maxResponseBytes`）；模板下载传 32 MB；边界测试 |

## Post-Design Re-check（宪法再核）

- 原则 I ✅ 每张表/接口都有对应测试任务；建表例外已声明
- 原则 II ✅ 规格 7 项澄清 + 计划期 12 项技术决策；旧云数据库导入的副作用已显式标记待确认
- 原则 III ✅ 账号、订单、支付、权限、分页全部复用；新增面均有"确实不存在"的证据
- 原则 IV ✅ 不改订单表/状态机/打印流水线；下线旧云是 Q52 的要求
- 原则 V ✅ SC-076~SC-087 均能落到自动化测试或 quickstart 手工步骤
