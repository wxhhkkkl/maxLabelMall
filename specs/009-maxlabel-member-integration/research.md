# 技术决策与被否决方案（009 MaxLabel 会员体系）

> 编号续 008：本文件从 **R48** 起（008 排到 R47）。
> 每条都记「选了什么 / 为什么 / 否决了什么」，并以代码事实为依据。

---

## R48 —— 支付成功怎么开通订阅：**`TradeOrderHandler.afterPayOrder` + 数据库幂等**

**Decision**：在 `trade-server` 新增 `TradeMaxLabelSubscriptionOrderHandler implements TradeOrderHandler`，
在 `afterPayOrder(order, orderItems)` 中逐项用 `ProductSubscriptionPlanApi.getBySkuId(skuId)`
解析周期（product-server 持有映射表），再调用 `MemberSubscriptionApi.grant(...)` 发放订阅；
发放落库时用 `(tenant_id, source_order_item_id)` 唯一键做幂等。

**Rationale**（代码事实）：

1. 支付回调的真实路径是：`PayNotifyController` → `PayOrderServiceImpl` 置支付单成功 →
   `pay_notify_task` 在**事务提交后** HTTP 回调 `/app-api/trade/order/update-paid` →
   `TradeOrderUpdateServiceImpl.updateOrderPaid`（`@Transactional`）→
   `tradeOrderHandlers.forEach(handler -> handler.afterPayOrder(order, orderItems))`。
   `afterPayOrder` 是**订单转已支付的唯一事务内扩展点**，积分与分销已用它。
2. 回调带重试（15s/30s/180s/… 共 8 次），且重复回调在 `updateOrderPaid` 只在同
   `payOrderId` 时提前返回；**并发时 CAS 只有一个赢家**。但发放仍必须自己幂等，因为：
   - handler 抛异常会回滚订单并触发重试；
   - 若模块拆分，`MemberSubscriptionApi` 是远程调用，可能"已提交但调用方以为失败"。
3. 唯一键比"先查再插"可靠：两个并发发放只有一个能插入，另一个按重复键吞掉。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| Spring `ApplicationEventPublisher` | 仅进程内；模块拆分即丢事件，且没有数据库兜底 |
| 定时任务轮询已支付订单补发 | 引入新调度与状态表；延迟不可控，违背"支付成功自动开通" |
| 在 `updateOrderPaid` 直接写 SQL | 跨模块直连 member 表，破坏模块边界；模块拆分后不可用 |
| 只靠 `updateOrderPaid` 的提前返回 | 无法覆盖远程调用"已成功但响应丢失"的场景 |

**⚠️ 代码陷阱**：`afterPayOrder` 收到的 `order` 是 CAS 更新**之前**加载的，
`payStatus` 仍为 false、`status` 仍为 UNPAID —— handler 内**不得**用这两个字段做判断。

---

## R49 —— 商品怎么对应订阅周期：**独立映射表 `maxlabel_subscription_plan`**

**Decision**：新增 `maxlabel_subscription_plan`，一条记录 = 一个可售 SKU × 一个周期
（`sku_id`、`period_months`、`enabled`、`sort`）。会员商品是**一个 SPU、多个 SKU**（月/年）。
**表放在 `product-server`**（商品配置归商品模块），通过 `ProductSubscriptionPlanApi` 暴露；
trade-server 已依赖 `product-api`，handler 直接调用，**member-server 不需要反向依赖 product**。

**Rationale**：

- `TradeOrderItemDO` 快照了 `spuId/skuId`，没有商品配置的实时引用；`skuId` 是**精确购买单元**。
- `product_spu` 没有订阅字段；`product_property` 是自由文本规格标签，拿它解析周期会脆弱。
- 商品分类是运营归类，不是机器可读的计划键。
- 给 `product_spu` 加列既动热表，又无法表达"同一 SPU 不同 SKU 周期不同"。
- 放在 product-server 可以**本地**校验 SKU 归属/价格/配送方式并把商品名/价格带给计划列表；
  若放在 member-server，反而要让 member-server 新增对 product-api 的依赖。
- 启用的计划还必须满足：**SPU 配送方式只含 VIRTUAL(3)**、**同一租户只有一个启用 SPU**
  （Q51/FR-223），否则运营可以配出实物会员或多个会员产品。
- 发放时把 `period_months` 快照进订阅流水，后续改商品或映射**不影响历史**。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 按商品分类判断 | 分类可随时改；一个分类下可能混入非会员商品 |
| 解析 SKU 规格属性（如"12个月"） | 中文/英文/格式都可能变，属于把业务规则寄托在展示文本上 |
| 在 `product_spu` 加 `subscription_months` | 无法按 SKU 区分月/年，且动商品主表 |
| 硬编码 SPU/SKU id | 环境（租户）不同 id 不同；运营无法自助上架 |

---

## R50 —— 订阅怎么累加：**授予流水 + 会员行锁**

**Decision**：`member_subscription` 一行 = 一次支付授予的**一段周期**。
发放算法（同一事务）：

1. `SELECT id FROM member_user WHERE id = ? FOR UPDATE`（以会员行为并发锚点）；
2. `SELECT MAX(end_time) FROM member_subscription WHERE member_id = ?` → `latestEndTime`；
3. `startDate = max(today, latestEndTime.toLocalDate())`
   （**不要再 +1 天**：`end_time` 已是"最后有效日+1天 00:00"），
   `endDate = startDate + period_months × item.count`（按 Asia/Shanghai 加自然月，月末按 `plusMonths` 钳制）；
   `start_time = startDate 00:00:00`，`end_time = endDate + 1 天 00:00:00`（不含当日）；
4. 插入流水行；重复键 `(tenant_id, source_order_item_id)` 直接返回已有行。

有效/过期由 `end_time > now` 推导（`end_time` 为最后有效自然日的**次日 00:00，不含当日**），
不维护状态字段；后台"是否有有效订阅"用同一表达式。

**Rationale**：

- 规格 FR-225 要求"每一段订阅"可见，流水模型天然满足；单行覆盖会丢历史。
- 续费必须"从原到期日顺延、不清零"（Q48/SC-078），`max(today, latestEndTime.toLocalDate())`
  同时覆盖已过期后续费与未到期续费；到期日当天仍有效（`end_time=次日00:00`，不含当日）。
- 并发锚点选 `member_user` 行：该行一定存在，且比"锁不存在的最大订阅行"可靠；
  避免两个首购并发各插一段重叠周期。
- `count > 1` 时按 `period × count` 发放，让"一次买两年"有确定语义；管理端计划页可提示
  会员商品建议限购 1。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 单行 `member_subscription` 直接改 `end_time` | 丢历史段，后台无法展示"每一段"；退款/对账也难追溯 |
| 只存 `expire_time` 在 `member_user` | 动会员主表；历史与来源订单无处安放 |
| 用 Redis 分布式锁 | 引入新依赖；会员行锁已足够，且事务内自然释放 |
| 状态字段（生效/过期） | 需要定时任务翻转；`end_time` 与当前时间比较即可，少一个会漂移的字段 |

**退款**：本期**不自动回收**（规格 Assumptions）。整单全额退款时 `afterCancelOrder` 标记该订单来源的
订阅行；会员订单项单独全额退款时 `afterCancelOrderItem` 按 `source_order_item_id` 标记。
后台展示"来源订单已退款"；不改变 `end_time`。

---

## R51 —— 私有模板正文存哪：**自有表 `LONGTEXT`，不走 `infra_file`**

**Decision**：`maxlabel_label_template.content LONGTEXT` 直接保存 `.msdx` JSON 原文；
下载走鉴权的 `/app-api/member/cloud-template/get`，只允许本人。

**Rationale**：

- `infra_file` **没有 owner/tenant 字段**，且通用下载端点
  `GET /admin-api/infra/file/{configId}/get/**` 是 `@PermitAll @TenantIgnore`
  —— 返回的 URL 就是公开可读，**不能用于私有模板**（FR-232/FR-251）。
- `MEDIUMTEXT` 上限 16,777,215 字节，正好比 16 MB 少 1 字节；`LONGTEXT` 无此边界。
- MySQL `JSON` 类型会规范化键顺序/空白，可能破坏 `.msdx` 的字节往返；用 `LONGTEXT` 保原文。
- 配额（1000 个 / 256 MB）在服务层按 `member_id` 统计，索引走 `(tenant_id, member_id)`。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| `infra_file` + 返回 URL | URL 公开、无归属；改 `infra` 的鉴权面影响全站，代价更大 |
| S3 私有桶 + 预签名 | 生产存储确实是 S3，但 `presignGetUrl` 只对 S3 客户端生效，且要新增配置与生命周期管理；本期先用 DB，容量上限可控 |
| 存文件系统 | 多实例部署不共享；备份与权限另做一套 |

**单文件上限**：沿用桌面端 16 MB（`validation.ts:48-56`）；服务端同时校验，避免只看客户端。

---

## R52 —— 共享模板怎么保证不泄露私有数据：**服务端清洗 + 独立副本表**

**Decision**：提交共享时创建/更新 `maxlabel_shared_template`（`source_template_id` 唯一），
服务端解析 msdx 后**二次清洗**再落 `content`：

- 删除 `doc.connections[*].password`（数据库口令）
- 删除 RFID 对象的 `accessPwd` / `killPwd`（读写/销毁口令）
- 删除 `doc.datasets[*].rows`（保留列定义/数据源结构，去掉业务数据行）

私有模板正文**不改**；取消共享/删除源模板时同步删除共享副本；审核状态独立。

**Rationale**：

- FR-236 要求共享模板不携带私有业务数据；仅靠桌面端 redaction 不够——
  `redactDocumentSecrets` 目前**只删 `connections[*].password`**，RFID 口令与数据集行仍在。
- 服务端清洗是最后一道闸：恶意或旧版客户端也可能提交未清洗内容。
- 独立副本让"审核拒绝/下架"不影响原作者私有模板，也让公开内容可以单独审计。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 同一行加 `shared` 标记 | 共享内容与私有内容只能二选一；清洗会破坏原模板 |
| 只靠桌面端清洗 | 客户端可被绕过；且现有 redaction 覆盖不全 |
| 只删数据库口令 | 数据集行可能含客户业务数据，RFID 口令是设备凭据，均属私有 |

**⚠️ 桌面端同步修改**：`msdx.ts` / `validation.ts` 的 redaction 扩展为
"数据库口令 + RFID 口令"；共享提交再由服务端做数据集清洗。

---

## R53 —— 桌面端怎么登录：**内嵌门面站登录窗口 + 同源读取令牌**

**Decision**：桌面端「登录」打开一个**专用 BrowserWindow**，加载商城同源的
`/?login=1&redirect=/account`（或等价登录入口）；主进程在该窗口内轮询
`localStorage.getItem('MAXLABEL_ACCESS_TOKEN' / 'MAXLABEL_REFRESH_TOKEN')`，
拿到后调用 `GET /member/user/get` 确认会员身份，加密保存令牌与会员快照，关闭登录窗口。

**Rationale**：

- 商城登录包含：验证码登录、密码登录、微信扫码、首登补全资料、图形验证码
  （`yudao.captcha.enable`）、SMS 频控。**在 Electron 里重做一套成本与风险都高**。
- 门面站把令牌放在**自己 origin 的 localStorage**（`MAXLABEL_ACCESS_TOKEN` /
  `MAXLABEL_REFRESH_TOKEN`），没有服务端重定向；`redirect` 只是 vue-router 的内部路径。
- 同一 Electron 默认 session 下，登录窗口与商城窗口共享 origin 的 localStorage/cookies，
  所以进入商城**不需要二次登录**（FR-221）。
- 读取方式只需 `webContents.executeJavaScript`，不注入 preload、不开 nodeIntegration，
  窗口保持 `contextIsolation/sandbox`。只允许精确 origin（配置的商城地址），只读两个已知键。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 原生表单调 `/member/auth/login|sms-login` | 要复刻图形验证码、SMS 频控、微信扫码与首登补全；验证码开启时直接走不通 |
| 自定义协议 `maxlabel://auth?token=` | 仓库没有单实例/协议注册；令牌进命令行与 URL，风险高 |
| 本机回环 HTTP 回调 | 要新开本地端口、处理防火墙与 state/CSRF；仍要门面站改造 |
| 系统浏览器登录 | 无法共享 Electron session，也拿不回令牌 |

**⚠️ 风险**：从页面读取令牌意味着令牌经过渲染进程内存；必须限定精确 origin、
不把令牌回传渲染层、用 `safeStorage` 加密落盘（FR-218/FR-250）。

---

## R54 —— 桌面端购买/续费入口：**商城 `/membership` 动态页**

**Decision**：门面站新增 `/membership` 路由，调用
`GET /app-api/product/subscription-plan/list` 渲染周期卡片（月/年），点击进入既有
`/product/:spuId` 详情与结算；桌面端「购买/续费」打开 `mallBaseUrl + '/membership'`。

**Rationale**：

- 会员商品就是普通虚拟商品（`deliveryTypes=[3]`），结算/支付/地址豁免全部复用（E3）。
- 现有 `/software` 是静态营销页，`softwarePlans` 未被任何视图渲染；把它改成动态会牵动
  设计一致性与既有测试。新增一个轻量动态页更小、更稳定。
- 桌面端不需要知道 SPU/SKU id，也不需要自行算价（FR-227/FR-240）。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 桌面端硬编码商品 URL | 环境/租户不同 id 不同，运营换品就失效 |
| 打开商城首页让用户自己找 | 不满足"提供购买/续费入口"的可用性 |
| 改造 `/software` 静态页 | 会改动既有页面与设计一致性断言；范围更大 |

**计划列表** `GET /product/subscription-plan/list` 允许匿名（商品公开信息）；其余接口需登录。

---

## R55 —— 打印记录怎么上报：**主进程 `log:print` 内 fire-and-forget**

**Decision**：在 `registerLogIpc.ts` 的 `log:print` 处理器完成本地追加后，若
（a）已登录商城、（b）设置开关开启、（c）不是测试打印，则主进程用白名单字段异步上报
`POST /app-api/member/print-record/create`；失败吞掉，不影响打印与本地日志。

**Rationale**：

- 渲染层 `App.tsx` 的 `logPrint` 已明确"日志失败不影响打印"；把上报放在主进程可以复用
  加密令牌，不把 token 暴露给渲染层。
- 打印机执行链路在测试打印时**根本不会调用 `logPrint`**，天然不产生上报；仍显式跳过
  `test === true` 的旧数据。
- 白名单字段与本地日志一致：`time/title/mode/count/copies/physicalCount/sentCount/status/printer`；
  **排除 `dataSnapshot`**（FR-248）。
- 本期不补传（Q49）：失败只留本地，不做队列。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 渲染层再调一次 `printReport` | 需要把令牌给渲染层，违背加密存储边界 |
| 独立轮询本地日志补传 | 新增游标/幂等/调度，Q49 明确不做 |
| 上报整条日志 | 含 `dataSnapshot`，违反隐私口径 |

**设备标识**：主进程生成随机 UUID，存 `mall-credentials.json`（不取机器指纹）；
上报时作为 `deviceId`，只用于区分设备，不作为鉴权。

---

## R56 —— 管理端怎么放：**既有会员详情三页签 + 两个独立运营页**

**Decision**：

- 会员详情新增懒加载页签：`UserSubscriptionList`、`UserLabelTemplateList`、`UserPrintRecordList`；
- 新增两个 `system_menu` 页面：「共享模板审核」「会员授权商品」；
- 权限点：`member:subscription:query`、`member:cloud-template:query`、
  `member:print-record:query`、`member:shared-template:query|audit|offline`、
  `product:subscription-plan:query|create|update`（计划表在 product 模块）。

**Rationale**：

- 会员详情是现成的 `el-tabs` + `UserOrderList` 模式，子组件只接收 `userId`；
- 普通业务页由后台菜单动态路由驱动（`system_menu.component`），静态路由只放隐藏详情页；
- **权限点必须同时插菜单行**：`@PreAuthorize` 的权限集合来自 `system_menu.permission`，
  只写注解不插行会导致"接口能通但按钮不显示"，超管也一样（013 迁移已踩过）。
- 审核与计划维护是跨会员的运营动作，不塞进会员详情。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 在会员列表加列/弹窗 | 订阅历史、模板、打印记录都是分页数据，列表塞不下 |
| 静态路由硬编码页面 | 与现有菜单/权限体系不一致，角色无法授权 |
| 复用商品评论审核页 | 字段与状态机不同，强行复用会产生条件分支 |

**审计**：共享模板行自带 `audit_user_id/audit_time/reject_reason/public_time`；
计划维护等关键动作加 `@LogRecord`。不依赖"只有 updater 字段"的弱痕迹。

---

## R57 —— 旧体系下线范围：**账户/模板下线，授权码保留**

**Decision**：

- **入口下线**：移除桌面端旧云账户登录、旧云模板列表/上传/下载与旧云窗口入口
  （`CloudDialog` 的认证/模板部分、`cloud:*` 通道与菜单项）；
- **代码删除待确认**：`cloud.ts`、`cloudRepository.ts`、`cloudCredentials.ts` 默认保留一版，
  确认旧「云数据库导入」也随之下线后再删；
- 保留 `license.ts` 与既有授权码校验（FR-229），继续使用旧服务器地址；
- `cloudService.ts` 的旧「云马通」窗口改为商城窗口；`DataPanel` 的**云数据库导入**依赖旧云令牌，
  随旧云账户一起**下线**。

**Rationale**：Q52 明确"更新后都只使用新体系"。保留旧云登录会给用户两套账号入口，
也让"模板归属"出现两套真相。授权码是已售出的权益，不能因账户体系切换而失效。

**⚠️ 需所有者确认的副作用**：`DataPanel` 的云数据库导入是旧云服务的功能，不在本次需求内；
下线后该入口消失。若仍需保留，必须为它单独保留旧云令牌，等于与 Q52 的"只使用新体系"冲突。
**本计划默认先隐藏该入口、保留代码一版；确认后再随旧云账户一起删除。**

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 保留旧云登录，仅新增商城登录 | 两套身份并存，违背 Q52，且模板归属分裂 |
| 把云数据库导入迁移到商城 | 商城没有该能力，属于另一个特性 |
| 保留隐藏的旧令牌 | 旧云端点仍可访问，安全面与维护面都变大 |

---

## R58 —— 双端令牌怎么保持一致：**主进程为写者，窗口为读者**

**Decision**：

1. 登录成功后主进程保存 `accessToken + refreshToken + expiresTime`；
2. 主进程在 access token 到期前用 `POST /member/auth/refresh-token?refreshToken=...` 刷新；
3. 刷新成功后，若商城窗口存在，用 `executeJavaScript` 把新值写回
   `localStorage['MAXLABEL_ACCESS_TOKEN'/'MAXLABEL_REFRESH_TOKEN']` 并 reload 当前页；
4. 若用户在商城窗口内触发了刷新（页面自己的 refresh 队列），主进程在窗口导航/定时检查时
   **重新读取** localStorage 并更新加密存储。

**Rationale**：

- yudao 刷新会**移除旧 access token**、复用 refresh token 直到过期
  （`OAuth2TokenServiceImpl`）；两端各自刷新会互相作废 access token，出现短暂 401 抖动。
- 让主进程负责刷新、窗口跟随，可以把抖动收敛到"写回 + reload"一次。
- 仍保留"窗口刷新后回读"作为兜底，避免主进程不在请求路径时状态滞后。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 两端各自刷新 | 会互相踢掉 access token，形成刷新乒乓 |
| 只依赖窗口刷新 | 主进程上报打印/模板时窗口可能已关闭，无令牌可用 |
| 每次操作都打开窗口取令牌 | 不可用；窗口不必常开 |

**⚠️ 实现约束**：写回脚本只能对**精确商城 origin** 执行；不得把令牌写进日志、标题或错误信息。

---

## R59 —— 测试与测试 DDL：**每层先写失败测试，DDL 同步**

**Decision**：

- 服务端：`BaseDbUnitTest`（H2 MySQL 模式）覆盖
  `MemberSubscriptionServiceImpl`（首购/续费/并发幂等/过期判断）、
  `LabelTemplateServiceImpl`（配额/归属/共享清洗/审核状态机）、
  `PrintRecordServiceImpl`（字段白名单/去重）、
  `TradeMaxLabelSubscriptionOrderHandler`（按 SKU 发放/重复回调）；
- 商城前端：Vitest 覆盖 `/membership` 计划渲染、后台三个页签与审核页的关键交互；
- 桌面端：Vitest unit/contracts/integration 覆盖
  `mallCredentials`（加密/刷新）、`mallRepository`（tenant 头/Bearer/错误映射）、
  `printReport`（跳过测试打印/开关/失败吞掉）、IPC 契约与 redaction；
- 每张新表同步到所属模块的 `src/test/resources/sql/create_tables.sql` 与 `clean.sql`。
  H2（`MODE=MYSQL`）测试 DDL 用兼容写法：**去掉 `unsigned`**，正文列用 `clob`/`text`
  （生产迁移仍用 `LONGTEXT`）；否则模块测试会在建表阶段失败。

**Rationale**：宪法原则 I 不可协商；商城已有 `BaseDbUnitTest` 与 H2 测试基建；
桌面端已有 `vi.mock('@main/net')` 模式。E2E 需要真实后端 + mock 支付，本期作为可选手工链路。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 只写 E2E | 反馈慢、依赖后端与支付 mock，无法覆盖并发幂等边界 |
| 不写后端 DB 测试 | 订阅算法是金额/权益相关，必须有数据库级测试 |
| 用 MySQL 跑单测 | 既有基建是 H2；改用真实 MySQL 会拖慢并引入环境依赖 |

---

## R60 —— 桌面端 HTTP 客户端要补两处小能力：**PUT + 可配置响应上限**

**Decision**：扩展 `app/src/main/net.ts`：

1. `httpRequestJson` 的 `method` 增加 `PUT`（既有 GET/POST/DELETE）；
2. 增加可选的 `maxResponseBytes` 参数，默认仍是 16 MB；模板下载调用传 **32 MB**
   （16 MB 正文 + JSON 元数据与转义开销）。

**Rationale**：

- 契约里的模板更新、订阅计划维护用 `PUT`，现有客户端**只支持 GET/POST/DELETE**；
  改成 POST 会让契约与 yudao 既有风格分叉，补一个分支成本最低。
- `httpRequestJson` 当前硬编码 16 MB 响应上限（`net.ts:36-40`）；
  一个 16 MB 的模板装进 JSON 响应后会超限，导致"能上传、不能下载"。
- 请求体 16 MB 由 Node `http` 直接发送，无需额外改动。

**Alternatives considered**：

| 方案 | 为什么否决 |
|---|---|
| 把模板上限降到 15 MB | 改变规格与桌面端既有 16 MB 校验；用户可见的限制被无谓收紧 |
| 用 POST 代替 PUT | 契约与 yudao controller 风格不一致，且不能解决响应上限 |
| 模板下载改用二进制流 | 需要新增流式响应与进度 UI，超出本期范围 |

**测试**：`tests/unit/main/net` 覆盖 PUT 分支与自定义上限；模板下载用 16 MB 边界夹具
（不入库，测试时临时构造）。
