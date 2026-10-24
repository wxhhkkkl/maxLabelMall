# 任务清单（010 订单物流跟踪）

**Input**: Design documents from `/specs/010-express-track/`
**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**测试策略**：宪法原则 I（测试先行，不可协商）→ **每个故事先写会失败的测试，再写实现**。
加列 DDL、H2 建表、DO/VO 字段、后台表单输入项、服务器配置**无法先红**（会变成编译/建表错误），
先落结构再补行为测试（与 007/008 同一例外，逐条标注）。

**本次最容易踩的坑**：`trade_delivery_express.code` **同时供微信小程序物流同步使用**。
快递100 用**新增的 `kd100_code`**；`code` 一个字节都不动（spec E11 / research R48）。

**命令备忘**：
- 服务端测试必须带 `-am`（否则读到 D 盘本地仓库的旧 jar → 成片 `NoClassDefFound`）；
  `-am` 与 `-Dtest` 同用时依赖模块会报「无匹配测试」，需加 `-Dsurefire.failIfNoSpecifiedTests=false`；
  若出现 `VerifyError`，先 `mvn clean` 清掉 IDE 写进 `target/classes` 的脏 class。
- `cd storefront && npm test -- src/api/order.spec.ts src/views/OrderDetailView.spec.ts src/views/OrderListView.spec.ts`
- `cd yudao-cloud/yudao-ui/yudao-ui-admin-vue3 && npm test -- src/views/mall/trade/order/detail/index.spec.ts src/views/mall/trade/order/components/OrderTableColumn.spec.ts`

## Format: `[ID] [P?] [Story] Description`

- **[P]**：不同文件、无未完成依赖，可并行
- **[Story]**：US35~US37，对应 spec.md 的用户故事
- 每条任务都有确切文件路径

---

## Phase 1: Setup（共享前提）

**Purpose**：迁移文件、真实凭据落位、基线测试。**不写业务代码。**

- [X] T001 [P] 新建 `yudao-cloud/sql/mysql/018-express-track-codes.sql`：先写文件头注释与幂等的 `ALTER TABLE trade_delivery_express ADD COLUMN kd100_code varchar(64) NULL`（用 `INFORMATION_SCHEMA` 判断列是否存在，重复执行不报错）
- [X] T002 [P] 在**本地 / 服务器**的 `application-my.yaml`（gitignored）补 `yudao.trade.express.kd100.customer / key` 真实值；**不改**仓库内的 `application.yaml`（保留上游演示值，FR-268）。**2026-10-24 已完成**：本地 `yudao-cloud/yudao-server/src/main/resources/application-my.yaml`；线上 `/opt/maxlabel/app/application-my.yaml`（备份 `.bak-20261024`），服务已重启生效
- [X] T003 [P] 跑基线测试并记录：`yudao-cloud/yudao-module-mall/yudao-module-trade-server` 的既有订单测试、`storefront` 与 `yudao-ui-admin-vue3` 的 vitest。已知 **pay 模块 5 个 `PayOrderServiceTest#testExpireOrder_*` 失败是既有的**，不在本特性范围

---

## Phase 2: Foundational（阻塞所有故事）

**Purpose**：编码列、错误码、查询服务、客户端日志脱敏、后台快递公司表单字段。
**⚠️ 完成后才能开始任何用户故事。**

### 数据库与结构

- [X] T004 在 `yudao-cloud/sql/mysql/018-express-track-codes.sql` 追加租户 162 的 `kd100_code` 回填（按 [data-model.md](./data-model.md) §2）：**统一按 `tenant_id=162 AND name=…` 匹配**（`圆通快递`/`圆通速递` 都映射 `yuantong`；`跨越`/`顺心捷达` 也按名），**安能不填**；**绝不 UPDATE `code` 列**；末尾加核对查询（13 家中 12 家应有值、安能为空）。按名匹配可避免「所有者已手工改过 code → 旧码匹配不到」的静默漏行（依赖 T001）
- [X] T005 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql/create_tables.sql` 的 `trade_delivery_express` 建表里加 `kd100_code varchar(64)`（H2 写法，与主库类型对齐）
- [X] T006 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/dal/dataobject/delivery/DeliveryExpressDO.java` 加 `private String kd100Code;`，注释写明「快递100 的 com 编码，为空 = 暂不支持查询；**不是**微信同步用的 `code`」
- [X] T007 [P] 在 `.../controller/admin/delivery/vo/express/DeliveryExpressBaseVO.java` 加选填 `kd100Code`；在 `.../DeliveryExpressSimpleRespVO.java` 也加 `kd100Code`（后台详情用精简列表判断「能不能查」，见 T030）；在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/trade/delivery/express/index.ts` 的 `DeliveryExpressVO` 加 `kd100Code?: string`；在 `.../views/mall/trade/delivery/express/ExpressForm.vue` 的 `formData` 与表单里加「快递100 编码」输入项（选填，说明文案点明与「公司编码」的区别）——**表单无单测基建，手工覆盖**
- [X] T008 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java` 新增 `EXPRESS_QUERY_NOT_SUPPORTED = 1_011_004_103`（文案：「该快递公司暂不支持物流查询」）；把 `EXPRESS_API_QUERY_FAILED` 的文案改为**不含第三方原文**的通用文案（FR-259）

### 查询服务（先测试）

- [X] T009 新建 `.../yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderQueryServiceImplTest.java`，**先写并确认失败**：① 未发货（`logisticsId=null`）→ 空列表且不调客户端；② `kd100_code` 为空 → 抛 `EXPRESS_QUERY_NOT_SUPPORTED` 且不调客户端；③ 成功 → 按 `time` **倒序**；④ 客户端抛异常 → 原样抛出（服务层记录日志）；⑤ 非本人订单 → `ORDER_NOT_FOUND`（依赖 T005/T006/T008）
- [X] T010 在 `.../service/order/TradeOrderQueryServiceImpl.java` 实现：`getExpressTrackList(order)` 先判 `kd100_code` 是否为空；缓存方法内按 `time` 倒序（空值放末尾）；在调用三方处 `try/catch` 记录 `订单号 + 快递公司 + kd100 编码 + 失败摘要`（**不含手机号**），再原样抛出。缓存方法收到的第 1 个参数就是 `kd100_code`，缓存键 `#code + '-' + 运单号 + '-' + 手机号` 不变（R50，依赖 T009）
- [X] T011 新建 `.../yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/framework/delivery/core/client/impl/kd100/Kd100ExpressClientTest.java`，**先写并确认失败**：① 快递100 返回 `result=false` → 抛出的业务错误**文案不含三方原文**；② 请求体 `com` 为**小写**、`num`/`phone` 正确映射（Mockito mock `RestTemplate`）
- [X] T012 在 `.../framework/delivery/core/client/impl/kd100/Kd100ExpressClient.java` 实现：失败时 `log.warn` 记录三方原因、抛通用错误；`httpRequest` 的 debug 日志**去掉 `param` / `phone`**（只留 customer 与运单号）（依赖 T011）

**Checkpoint**：服务端能对正确编码的运单查到倒序轨迹；编码缺失有专用错误；日志无手机号。

---

## Phase 3: US35 —— 客户在订单详情看到物流轨迹（P1）🎯 MVP

**Goal**：已发货订单的 C 端详情出现物流卡（公司/运单号/发货时间/倒序轨迹），订单列表出现「查看物流」。

**Independent Test**：取一笔有运单号的已发货订单，打开 C 端详情自动看到轨迹；未发货/虚拟/自提不出现物流卡；无运单号的历史单在列表上没有入口。

### 结构准备（无法先红）

- [X] T016 [US35] **（结构性例外）** 在 `.../controller/app/order/vo/AppTradeOrderPageItemRespVO.java` 加 `private String logisticsNo;` —— 新字段无法先红，先落结构
- [X] T017 [US35] 新建 `.../yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/convert/order/TradeOrderConvertTest.java`，断言 `convert02(order, items)` 把 `logisticsNo` 复制到 `AppTradeOrderPageItemRespVO`。⚠️ 这是**映射回归**（MapStruct 按名自动映射），不作为 TDD 红；真正的红-绿在 T020/T021 的界面行为（依赖 T016）

### 测试（先写、先失败）

- [X] T013 [P] [US35] 在 `storefront/src/api/order.spec.ts` 加 `getExpressTrackList` 用例：路径 `/trade/order/get-express-track-list`、参数 `{ id }`
- [X] T014 [P] [US35] 在 `storefront/src/views/OrderDetailView.spec.ts` 加用例：① 有 `logisticsNo` → 渲染「物流信息」与倒序轨迹；② 无 `logisticsNo`（含虚拟商品 / 自提）→ 不渲染物流卡；③ 轨迹为 `[]` → 显示「暂无轨迹信息」；④ 时间渲染走 `formatDateTime`（断言与订单其他时间同格式）
- [X] T015 [P] [US35] 在 `storefront/src/views/OrderListView.spec.ts` 加用例：`logisticsNo` 非空才出现「查看物流」；`null` / 缺失时不出现；列表不发轨迹请求

### 实现

- [X] T018 [P] [US35] 在 `storefront/src/types/order.ts`：`OrderPageItem` 加 `logisticsNo?: string`；新增 `ExpressTrack = { time: number | string; content: string }`；`OrderDetail` 补 `logisticsName?: string`、`logisticsNo?: string`、`deliveryTime?: number | string`
- [X] T019 [P] [US35] 在 `storefront/src/api/order.ts` 加 `getExpressTrackList(id): Promise<ExpressTrack[]>`（GET，参数走 query）（依赖 T013）
- [X] T020 [US35] 在 `storefront/src/views/OrderDetailView.vue` 加物流卡：详情加载后若 `logisticsNo` 非空则**自动**请求；插在「收货信息」之后、「买家留言」之前；渲染公司/运单号/发货时间 + 倒序轨迹；**时间一律走 `utils/time.ts` 的 `formatDateTime`**（FR-270）；空数组显示「暂无轨迹信息」（依赖 T014/T018/T019）
- [X] T021 [US35] 在 `storefront/src/views/OrderListView.vue` 的订单卡操作区加「查看物流」`RouterLink`（仅 `o.logisticsNo` 非空；跳 `/order/{id}`）（依赖 T015/T016/T018）

**Checkpoint**：US35 可独立演示 —— C 端能看物流，其他订单不受影响。

---

## Phase 4: US36 —— 运营在后台订单页查询物流（P1）

**Goal**：后台订单列表的「快递编号 + 查看物流」与详情时间轴保持可用；空轨迹有明确提示。

**Independent Test**：后台订单列表点「查看物流」→ 详情时间轴正常；空数组时显示「暂无轨迹信息」；无权限账号被拒。

### 测试（先写、先失败）

- [X] T023 [US36] 新建 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/trade/order/detail/index.spec.ts`，先写失败用例：① 轨迹非空 → 渲染时间轴；② 轨迹为空 → 显示「暂无轨迹信息」（fixture 用内联类型，不依赖 T022）。⚠️ 必须 mock `@/api/mall/trade/order`、`@/api/mall/trade/delivery/express`（详情页会拉精简快递列表）、`pickUpStore` 与 `@/config/axios`，否则会走真实请求；同时在 `.../components/OrderTableColumn.spec.ts` 补一条：`logisticsNo` 非空才出现「查看物流」（**列表入口的 3 条用例本已存在，未重复添加**）

### 实现

- [X] T022 [P] [US36] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/trade/order/index.ts` 把 `getExpressTrackList` 的内联返回类型提取为导出 `interface ExpressTrack { time?: Date | string | number; content?: string }`，并让函数复用它（纯类型重构，无行为变化）
- [X] T024 [US36] 在 `.../views/mall/trade/order/detail/index.vue` 的物流区加**空态**提示（`expressTrackList` 为空且已加载 → 「暂无轨迹信息」）；**错误态留给 T030，不要在这里重复实现**；`.../components/OrderTableColumn.vue` 的入口**保持现状**，只在测试里钉住（依赖 T023）

**Checkpoint**：US36 可独立验收，不改后台列表版式（Q56）。

---

## Phase 5: US37 —— 物流拿不到时不误导客户（P2）

**Goal**：编码缺失、三方报错、越权等异常下，两端都给站内提示，不暴露第三方原文，不阻塞页面。

**Independent Test**：把某快递公司 `kd100_code` 置空 → 显示「暂不支持查询」；把 `key` 改错 → 两端显示「暂时无法获取物流信息」；页面其余内容可用。

### 测试（先写、先失败）

- [X] T025 [P] [US37] 先建 `storefront/src/utils/expressTrack.ts` **桩**（导出 `trackErrorText`，先返回空串），再在 `storefront/src/utils/expressTrack.spec.ts` 写失败用例：错误码 `1_011_004_103` → 「该快递公司暂不支持查询」；其它错误 → 「暂时无法获取物流信息，请稍后重试」；未知 → 通用文案
- [X] T026 [US37] 在 `storefront/src/views/OrderDetailView.spec.ts` 补失败用例：轨迹请求 reject（不支持 / 通用）→ 物流卡仍显示公司/运单号 + 对应提示，订单金额与商品仍渲染（依赖 T025 的接口）
- [X] T027 [US37] 在 `.../yudao-ui-admin-vue3/src/views/mall/trade/order/detail/index.spec.ts` 补失败用例：① 所选快递公司的 `kd100Code` 为空（mock 精简列表）→ 显示「该快递公司暂不支持查询」且**不调**轨迹接口；② 轨迹请求 reject → 显示「暂时无法获取物流信息」，时间轴不渲染，页面其余正常

### 实现

- [X] T028 [P] [US37] 在 `storefront/src/utils/expressTrack.ts` 实现错误码 → 文案映射（依赖 T025）
- [X] T029 [US37] 在 `storefront/src/views/OrderDetailView.vue` 的轨迹请求加 `try/catch`，用 T028 的映射显示提示；保证物流卡的抬头（公司/运单号）不受失败影响（依赖 T020/T026/T028）
- [X] T030 [US37] 在 `.../views/mall/trade/order/detail/index.vue`：① 用已加载的 `deliveryExpressList` 找到所选快递公司，`kd100Code` 为空 → 直接显示「该快递公司暂不支持查询」且**不调**接口（FR-261 后台侧）；② 否则调 `getExpressTrackList`，加 `try/catch` 与 `expressTrackError` 标记，失败显示「暂时无法获取物流信息」；**空态沿用 T024，不重复实现**（依赖 T024/T027）
- [ ] T031 [US37] 按 [quickstart.md](./quickstart.md) §四执行失败演练：编码置空、`key` 改错、空轨迹、越权、服务商不可达 / 限频；确认页面无第三方错误码/英文原文，且服务端日志能按订单号定位；同一运单连续打开 10 次，服务端日志只出现 **1 次**快递100 请求（SC-091）（依赖 T029/T030；**需所有者/运营提供真实运单与凭据**）

**Checkpoint**：所有失败路径都有站内文案；第三方细节只在日志里。

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T032 跑完整目标测试：`cd yudao-cloud && mvn -pl yudao-module-mall/yudao-module-trade-server -am test -Dtest='TradeOrderQueryServiceImplTest,Kd100ExpressClientTest,TradeOrderConvertTest' -Dsurefire.failIfNoSpecifiedTests=false`；`storefront` 全量 vitest；`cd yudao-cloud/yudao-ui/yudao-ui-admin-vue3 && npm test -- src/views/mall/trade/order/detail/index.spec.ts src/views/mall/trade/order/components/OrderTableColumn.spec.ts`（依赖全部实现）
- [X] T033 审查 diff：① `trade_delivery_express.code` / `TradeStatusSyncToWxaOrderHandler` **零改动**；② 仓库内无真实 kd100 凭据；③ 日志无收件人手机号；④ 未新增列表内联展开 / 手动刷新（FR-264 / FR-272）
- [ ] T034 执行 [quickstart.md](./quickstart.md) §零~§七（含 §六 显式不验证项），把结果记入本文件或 PR 说明（**需所有者/运营配合；§六 的项按声明不验证**）
- [X] T035 [P] 同步 `CLAUDE.md` 的当前计划与棕地事实（已由 `/speckit-plan` 完成，复核一次指向 `specs/010-express-track/plan.md`）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**：无依赖，可立即开始
- **Foundational (Phase 2)**：依赖 Setup；**阻塞所有用户故事**
- **US35 (Phase 3)**：依赖 Foundational
- **US36 (Phase 4)**：依赖 Foundational（后台接口已存在，只需补空态测试）
- **US37 (Phase 5)**：依赖 US35 + US36（它细化两者共用的失败路径）
- **Polish (Phase 6)**：依赖全部

### 关键任务依赖

```
T001 → T004
T005/T006/T008 → T009 → T010
T011 → T012
T016 → T017
T013 → T019
T014/T018/T019 → T020
T015/T016/T018 → T021
T023 → T024
T025 → T026 → T029
T025 → T028 → T029
T024/T027 → T030
T029/T030 → T031
全部 → T032 → T033 → T034
```

### 并行机会

- Phase 1：T001 / T002 / T003 三个文件互不相干 → 并行
- Phase 2：T005 / T006 / T007 / T008 可并行；T011 / T012（客户端）与 T009 / T010（服务）**文件不同**，可并行
- Phase 3：T013 / T014 / T015 / T016 四个测试文件互相独立 → 并行
- Phase 5：T025（C 端 util）与 T027（后台 spec）可并行

---

## Parallel Example: US35

```bash
# 四个测试文件互不影响，可同时开写（先全部跑红）
Task: "storefront/src/api/order.spec.ts —— getExpressTrackList 路径与参数"
Task: "storefront/src/views/OrderDetailView.spec.ts —— 渲染/不渲染/空轨迹"
Task: "storefront/src/views/OrderListView.spec.ts —— 查看物流入口"
Task: "trade-server .../TradeOrderConvertTest.java —— logisticsNo 复制"
```

---

## Implementation Strategy

### MVP First（只做 US35）

1. Phase 1 + Phase 2（地基，**必须先做完**）
2. Phase 3（US35）
3. **STOP & VALIDATE**：用一笔真实已发货订单，在 C 端详情看到轨迹
4. 此时即可上线"客户看物流"，后台与失败细化随后补

### Incremental Delivery

1. 地基 → 服务端可查（还看不到界面）
2. US35 → **客户能看物流（MVP）**
3. US36 → 后台空态补齐（列表/时间轴本就可用）
4. US37 → 失败路径统一，交付完整
5. 每个故事都能独立演示，不破坏前一个

---

## Notes

- 每条测试任务必须**先跑红**再写实现；红的原因要正确（断言失败，不是编译/导入错误）
- 结构性例外已标注：T004~T008 的 DDL / H2 / DO / VO / 表单 / 配置，以及 T016 的 VO 新字段，无法先红
- `kd100_code` 为空是**合法状态**（安能），不要用 `@NotNull` 或默认值把它填掉
- 不要在服务端或前端写"短码 → 全拼"的硬编码映射（R48）
- 查轨迹**不写任何订单字段**（FR-266）；不要顺手加"取货/签收"状态回写

---

## 执行记录（2026-10-24）

**已完成 54 / 56**（原 32/35 + Phase 7 5/5 + Phase 8 3/3 + T002 + Phase 9 13/13）。自动化测试结果：

| 范围 | 命令 | 结果 |
|---|---|---|
| 服务端 | `mvn -pl .../yudao-module-trade-server -am test -Dtest='TradeOrderQueryServiceImplTest,Kd100ExpressClientTest,TradeOrderConvertTest,DeliveryExpressCreateReqVOValidationTest' -Dsurefire.failIfNoSpecifiedTests=false` | ✅ 全绿 |
| C 端全量 | `cd storefront && npm test` | ✅ 99 文件 / 1342 用例 |
| C 端类型 | `cd storefront && npm run ts:check` | ✅ 无错误 |
| 管理端 | `cd yudao-ui-admin-vue3 && npm test -- .../order/detail/index.spec.ts .../OrderTableColumn.spec.ts .../order/index.spec.ts .../api/mall/trade/order/index.spec.ts` | ✅ 4 文件 / 25 用例 |
| 管理端类型 | `npm run ts:check` | ⚠️ 仅**既有**错误（invoice / fms / login / member），本特性的改动文件无报错 |
| Phase 7 收敛 | 快递100 编码错误 / 超时映射、后台列表失败、发货后刷新、kd100Code 长度 | ✅ 全部通过 |
| Phase 8 收敛 | `getDetail` 空订单返回、空响应/非法 JSON 映射、后台 `silent` 通道 | ✅ 全部通过 |

**仍有 2 条未完成**：

- **T031**：按 quickstart §四 做失败演练（需要真实运单与凭据）。
- **T034**：执行 quickstart §二~§九 的人工验收。

**上线记录（2026-10-24）**：

- ✅ `018-express-track-codes.sql` 已在生产库执行并校验：`kd100_code` 列存在；租户 162 共 13 家，12 家有编码，安能为空；`code` 列未改
- ✅ 线上 `/opt/maxlabel/app/application-my.yaml` 已合并真实快递100凭据（备份 `.bak-20261024`）
- ✅ 后端 jar 已上线：`/root/yudao-server.jar.bak-20261024-112208` 为回滚备份；`maxlabel` active，48080 HTTP 200，近期日志 0 ERROR
- ✅ storefront：38 个文件；`/var/www/maxlabel/index.html.bak-20261024-112730`
- ✅ admin：2036 个文件；`/var/www/maxlabel/admin/index.html.bak-20261024-112915`
- ✅ 公网入口：`https://new.yuwangchenfa.com/`、`/admin/`、`http://maxlabel.cn/` 均 200
- ⚠️ 构建时本机 Java 语言服务器会抢占 `target/classes`（本次全量构建失败两次）。后端最终在**工作区外副本**构建：`/d/workspace/_deploy_build_20261024`，再 `deploy.py backend --skip-build` 上传。以后发版可沿用这个办法，或构建前停掉 IDE 的 Java 语言服务器

---

## Phase 7: Convergence

**Purpose**：关闭 spec / plan / tasks 与当前实现之间的差距（`/speckit-converge`，2026-10-24）。
**既有 35 条任务不重写、不重编号。**

- [X] T036 为快递100 查询加**独立超时**：在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/delivery/config/ExpressClientConfig.java` 提供一个带 connect ≤3s / read ≤8s 的专用 `RestTemplate`（或 `SimpleClientHttpRequestFactory`），并让 `ExpressClientFactory` 用它；目标是三方挂起时 SC-088 的「失败 10 秒内降级」成立。per SC-088 / FR-258 (partial)
- [X] T037 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/trade/order/detail/index.vue` 给 `getSimpleDeliveryExpressList()` 包 try/catch：失败时设置 `expressTrackError` 并继续渲染订单其余内容，不能停在「物流信息加载中…」。per FR-258 / US37 (partial)
- [X] T038 在同一文件抽出 `loadExpressTracks()`（精简快递列表 + `kd100Code` 预判 + 轨迹请求），在 `getDetail()` 成功后调用；发货 / 改地址 / 确认收款等 `@success="getDetail"` 之后物流区能刷新，不再停在「加载中…」（依赖 T037）。per FR-264 / US36 (partial)
- [X] T039 把快递100 的「编码无法识别」响应映射为 `EXPRESS_QUERY_NOT_SUPPORTED`：在 `Kd100ExpressQueryRespDTO` 补 `returnCode`（或按 message 识别），`Kd100ExpressClient` 抛专用错误，`Kd100ExpressClientTest` 钉住；限频 / 凭据错误仍走通用失败。per FR-261 (partial)
- [X] T040 给 `kd100_code` 加长度上限：`DeliveryExpressBaseVO` 加 `@Size(max = 64)`（`jakarta.validation.constraints.Size`），`ExpressForm.vue` 的输入框加 `maxlength="64"`；DB 是 `varchar(64)`，超长在严格模式会 500。per plan:data-model §1 (partial)

---

## Phase 8: Convergence

**Purpose**：第二轮收敛（`/speckit-converge`，2026-10-24）。**既有 40 条任务不重写、不重编号。**

- [X] T041 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/trade/order/detail/index.vue` 的 `getDetail` 里，`if (!res) { … close() }` 之后补 `return`；否则 `formData.value = null` 会让新加的 `loadExpressTracks()` 读 `deliveryType` 抛 NPE（T038 引入的回归）。per plan:T038 / FR-258 (partial)
- [X] T042 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/framework/delivery/core/client/impl/kd100/Kd100ExpressClient.java` 的 `httpRequest` 里把**空响应 / JSON 解析失败**也转成 `EXPRESS_API_QUERY_ERROR`（当前直接 `JsonUtils.parseObject`，null/异常会绕过服务层按订单号记日志）；在 `Kd100ExpressClientTest` 补空 body 与非法 JSON 两条用例。per FR-274 / SC-096 (partial)
- [X] T043 后台把「编码无法识别」（`1_011_004_103`）显示为内联「暂不支持」：当前 admin axios 在业务错误时 reject 成字符串 `'error'`，详情页只能显示通用文案；给该请求加一个保留业务 code 的通道（或确认接受 toast 方案后把结论写进 `contracts/admin-api.md` 并关闭本任务）。per FR-261 (partial)

---

## Phase 9: 物流地图（010 追加，2026-10-24）

**Purpose**：前后台订单详情内嵌快递100 地图（`trailUrl`）。规格见 [spec.md](./spec.md) 的「追加：物流地图」。

- [X] T044 后端：`TradeExpressProperties.Kd100Config` 加 `mapConfigKey`/`mapFrom`；错误码 104/105；`RedisKeyConstants.EXPRESS_MAP`；`ExpressMapQueryReqDTO` + `Kd100ExpressMapReqDTO/RespDTO`；`ExpressClient.getExpressMapUrl` 默认方法；`Kd100ExpressClient` 调 `maptrack.do`
- [X] T045 后端：`TradeOrderQueryService#getExpressMapUrl`（订单归属 + 后台权限）、`AreaUtils.format` 解析 `to`、历史无 `receiver_area_id` 抛 105、1 小时缓存；C 端/后台控制器各加 `get-express-map-url`
- [X] T046 服务端测试：`Kd100ExpressClientTest` 地图请求映射/未配置/三方失败；`TradeOrderQueryServiceImplTest` 成功/无地区/无编码/越权
- [X] T047 C 端：`api/order.ts#getExpressMapUrl`；`OrderDetailView` 物流卡「查看地图」按钮 + iframe + 未配置/无法生成提示；`expressTrack.ts#mapErrorText`；测试
- [X] T048 后台：`api/mall/trade/order#getExpressMapUrl`（silent）；订单详情「查看物流地图」+ iframe + 内联错误；测试
- [X] T049 文档：spec 追加、contracts（C 端/后台）、data-model、plan R56、quickstart §八
- [X] T050 **配置真实发货地址**（省/市/区，单一发货仓）到本地与线上 `application-my.yaml` 的 `yudao.trade.express.kd100.map-from`；**待所有者提供**
- [X] T051 部署地图版本（后端 + storefront + admin）并按 quickstart §八 验证；依赖 T050
- [X] T052 `trade_order` 加 `delivery_from_area_id`/`delivery_from_address`（`019-delivery-from-address.sql` + H2 建表）；`TradeOrderDO`/`TradeOrderDeliveryReqVO` 加字段；`deliveryOrder` 快递分支校验并保存
- [X] T053 后台发货表单加「发货地址（省市区树 + 详细地址）」，默认取 `/trade/order/get-delivery-default-address`，可修改；`DeliveryVO` 加字段
- [X] T054 测试：`TradeOrderUpdateServiceImplTest` 发货地址必填/落库；`TradeOrderQueryServiceImplTest` 地图 from 用订单地址、缺省回退默认；admin API 默认地址
- [X] T055 更新快递100 新账户（customer/key/map 模板）与默认发货地址到本地/线上 `application-my.yaml`；执行 `019`；重新部署后端 + storefront + admin，公网三入口 200

---

## 第二次上线记录（2026-10-24 14:00）

- ✅ `019-delivery-from-address.sql` 已执行：`trade_order.delivery_from_area_id` / `delivery_from_address`
- ✅ 快递100 新账户（customer/key/模板）写入本地与线上 `application-my.yaml`；线上备份 `.bak-20261024-map`
- ✅ 默认发货地址：北京市海淀区 + 西北旺亮甲店永丰中路100号北京人才教育基地红楼107（发货表单预填、可改）
- ✅ 后端：`/root/yudao-server.jar.bak-20261024-135333`；服务 active，48080 HTTP 200，0 ERROR
- ✅ storefront：`index.html.bak-20261024-135844`；admin：`index.html.bak-20261024-140022`
- ✅ 公网：`https://new.yuwangchenfa.com/`、`/admin/`、`http://maxlabel.cn/` 均 200
- [X] T056 物流地图改为**订单详情自动加载**（C 端/后台，移除「查看地图」按钮）；发货表单标签改「发货详细地址」；同步测试/规格/契约/quickstart；重发 storefront + admin

---

## 第三次上线记录（2026-10-24 14:15）

- ✅ 物流地图改为**订单详情自动加载**（C 端/后台，移除「查看地图」按钮）
- ✅ 后台发货表单标签「详细地址」→「**发货详细地址**」
- ✅ 仅重发 storefront + admin（后端 / 配置 / DB 无改动）
- storefront：`/var/www/maxlabel/index.html.bak-20261024-141247`
- admin：`/var/www/maxlabel/admin/index.html.bak-20261024-141421`
- 公网三入口 200；测试：C 端 1348、管理端 29
