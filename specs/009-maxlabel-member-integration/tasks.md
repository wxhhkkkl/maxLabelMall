# Tasks: MaxLabel 桌面端接入商城会员体系

**Input**: Design documents from `/specs/009-maxlabel-member-integration/`
**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**测试策略**：宪法原则 I（测试先行，不可协商）→ **每个故事先写会失败的测试，再写实现**。
建表/枚举本身无法先红，先落结构再补行为测试（与 007/008 同一例外）。
桌面端 UI 组件暂无 RTL harness：**逻辑层用 Vitest，UI 交互由 quickstart 手工验证**（显式声明）。

**跨仓库**：本 tasks 同时覆盖 `maxLabelMall`（本仓库）与同级 `../maxlabel`。
路径均相对各自仓库根；桌面端路径以 `../maxlabel/` 开头。

## Format: `[ID] [P?] [Story] Description`

- **[P]**：不同文件、无未完成依赖，可并行
- **[Story]**：US28~US34，对应 spec.md 的用户故事
- 每条任务都有确切文件路径

---

## Phase 1: Setup（共享配置与客户端基础）

**Purpose**：跨仓库共用的配置、HTTP 与清洗能力，先于任何故事。

- [X] T001 [P] 在 `../maxlabel/app/src/renderer/src/dialogs/OptionsDialog.tsx` 的 `AppOptions` 增加 `mallBaseUrl`（默认空）、`mallTenantId`（默认 `162`）、`printReportEnabled`（默认 `true`），补 load/save 校验与设置项
- [X] T002 [P] 扩展 `../maxlabel/app/src/main/net.ts`：`httpRequestJson` 支持 `PUT`，新增可选 `maxResponseBytes`（默认 16 MB）；在 `../maxlabel/app/tests/unit/main/net.test.ts` 先写失败测试（PUT 分支、自定义上限）
- [X] T003 [P] 扩展模板清洗：`../maxlabel/app/src/main/ipc/validation.ts` 与 `../maxlabel/app/src/renderer/src/io/msdx.ts` 除 `connections[*].password` 外删除 RFID `accessPwd`/`killPwd`；在 `../maxlabel/app/tests/unit/main/validation*.test.ts` 先写失败测试
- [X] T004 [P] 在 `yudao-cloud/yudao-module-member/yudao-module-member-api/src/main/java/cn/iocoder/yudao/module/member/enums/ErrorCodeConstants.java` 与 product 模块对应 `ErrorCodeConstants.java` 增加契约中的错误码名称（按既有分段分配数字）

---

## Phase 2: Foundational（阻塞所有故事）

**Purpose**：5 张表、订阅/计划/支付钩子、桌面端令牌与 HTTP 基座、IPC 骨架。
**⚠️ 完成后才能开始任何用户故事。**

### 数据库与测试 DDL

- [X] T005 [P] 创建 `yudao-cloud/sql/mysql/014-maxlabel-member.sql`：按 data-model.md 写 5 张表（`maxlabel_subscription_plan`、`member_subscription`、`maxlabel_label_template`、`maxlabel_shared_template`、`maxlabel_print_record`），全部 `CREATE TABLE IF NOT EXISTS`，含唯一键与索引
- [X] T006 [P] 同步 H2 测试 DDL：product/member/trade 三模块 `src/test/resources/sql/create_tables.sql` 与 `clean.sql` 加入对应新表（去掉 `unsigned`，正文列用 `clob`/`text`）
- [X] T007 在 `014-maxlabel-member.sql` 追加 `system_menu` 页面与按钮权限（幂等 `INSERT … SELECT … WHERE NOT EXISTS`）：共享模板审核、会员授权商品、三个页签所需查询权限（依赖 T005）

### 会员授权商品（product 模块）

- [X] T008 [P] 创建 `MaxLabelSubscriptionPlanDO` 与 Mapper：`yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/{dataobject,mysql}/subscriptionplan/`
- [X] T009 在 `.../product-server/src/test/java/.../service/subscriptionplan/ProductSubscriptionPlanServiceImplTest.java` 先写失败测试（SKU 归属、SPU 仅虚拟商品、已有其他启用 SPU 被拒、价格 > 0、周期 1–120、同 SKU 唯一、停用后不再返回），再实现 `.../service/subscriptionplan/ProductSubscriptionPlanServiceImpl.java`，并对 create/update 加 `@LogRecord`
- [X] T010 [P] 新增 `.../product-api/src/main/java/cn/iocoder/yudao/module/product/api/subscriptionplan/ProductSubscriptionPlanApi.java`（`getBySkuId`、`listEnabled`）与 `.../product-server/.../api/subscriptionplan/ProductSubscriptionPlanApiImpl.java`
- [X] T011 [P]（管理端分页映射已测；匿名可访问由 `@PermitAll` 保证，未做 HTTP 级测试） 在 `.../product-server/src/test/java/.../controller/...SubscriptionPlanControllerTest.java` 先写失败契约测试（匿名可访问、分页、权限），对照 `contracts/app-api.md` §1.2 与 `contracts/admin-api.md` §2
- [X] T012 实现 app 控制器 `.../product-server/.../controller/app/subscriptionplan/AppProductSubscriptionPlanController.java`（`GET /product/subscription-plan/list`，`@PermitAll`）与 admin 控制器 `.../controller/admin/subscriptionplan/ProductSubscriptionPlanController.java`（page/create/update）

### 会员订阅（member 模块）

- [X] T013 [P] 创建 `MemberSubscriptionDO` 与 Mapper：`yudao-cloud/yudao-module-member/yudao-module-member-server/src/main/java/cn/iocoder/yudao/module/member/dal/{dataobject,mysql}/subscription/`
- [X] T014 在 `.../member-server/src/test/java/.../service/subscription/MemberSubscriptionServiceImplTest.java` 先写失败测试：首购、未到期续费、过期后续费、`count=2`、同一 `source_order_item_id` 重复发放、并发发放、`refunded` 标记、**到期日当天仍有效 / 次日 00:00 过期（end_time 存次日 00:00，不含当日）**、**月末 plusMonths 钳制**（1/31→2/28 或 2/29、8/31→9/30）；再实现 `.../service/subscription/MemberSubscriptionServiceImpl.java`（会员行锁 + `startDate=max(today, latestEndTime.toLocalDate())`，不要再 +1 天 + 唯一键）
- [X] T015 [P] 新增 `.../member-api/src/main/java/cn/iocoder/yudao/module/member/api/subscription/MemberSubscriptionApi.java` 与 `.../member-server/.../api/subscription/MemberSubscriptionApiImpl.java`（`grant`、`getCurrent`、`markRefunded`）
- [X] T016 [P] 新增 app 控制器 `.../member-server/.../controller/app/subscription/AppMemberSubscriptionController.java`（`GET /member/subscription/get`）与 admin 控制器 `.../controller/admin/subscription/MemberSubscriptionController.java`（`GET /member/subscription/page?userId=`）；响应必须含 `startDate/endDate/startTime/endTime/remainingDays`，`endTime` 为最后有效自然日的次日 00:00（不含当日）
- [X] T017 [P]（会员列表批量订阅字段已实现；`getLatestByMemberIds` 由 `MemberSubscriptionServiceImplTest` 覆盖） 扩展 `.../member-server/.../controller/admin/user/MemberUserController.java`：会员分页按本页 `userIds` **一次批量**补充 `subscriptionActive`/`subscriptionEndTime`（禁止 N+1），并先写对应服务测试

### 支付钩子（trade 模块）

- [X] T018 在 `.../trade-server/src/test/java/.../service/order/handler/TradeMaxLabelSubscriptionOrderHandlerTest.java` 先写失败测试：命中 SKU 发放、未命中跳过、同一订单项不重复、handler 抛错不吞、0 元单不进入；再实现 `.../trade-server/.../service/order/handler/TradeMaxLabelSubscriptionOrderHandler.java`
- [X] T019 在 `.../trade-server/.../framework/rpc/config/RpcConfiguration.java` 注册 `ProductSubscriptionPlanApi` **与** `MemberSubscriptionApi`（微服务/拆分部署必需），并确认 `afterPayOrder` 内不读 `order.payStatus/status`
- [X] T020 [P] 在 `.../trade-server/.../service/order/handler/` 的同处理器实现退款标记：`afterCancelOrder` 调 `markRefundedByOrder(orderId)`；`afterCancelOrderItem` 在会员订单项全额退款时调 `markRefundedByOrderItem(orderItemId)`（不缩短周期）

### 桌面端令牌、HTTP 与 IPC 基座

- [X] T021 [P] 在 `../maxlabel/app/tests/unit/main/mall-credentials.test.ts` 先写失败测试（加密保存/读取/清除、safeStorage 不可用时拒绝明文、按商城地址隔离）；再实现 `../maxlabel/app/src/main/mallCredentials.ts`
- [X] T022 [P] 在 `../maxlabel/app/tests/integration/main/mall-repository.test.ts` 先写失败测试（`tenant-id`、`terminal:31`、Bearer、401 刷新、错误映射）；再实现 `../maxlabel/app/src/main/mallRepository.ts`，基于 `net.ts`
- [X] T023 [P] 在 `../maxlabel/app/tests/contracts/mall-ipc.test.ts` 先写失败测试（新通道唯一、sender guard、参数校验）；再扩展 `../maxlabel/app/src/shared/ipcContract.ts`、`../maxlabel/app/src/preload/index.ts`、`../maxlabel/app/src/preload/index.d.ts`、`../maxlabel/app/src/renderer/src/env.d.ts`、`../maxlabel/app/src/main/ipc/registerMallIpc.ts`，并在 `../maxlabel/app/src/main/index.ts` 注册
- [X] T024 [P] 在 `../maxlabel/app/tests/integration/main/mall-service.test.ts` 先写失败测试（精确 origin 校验、登录窗口生命周期、窗口复用）；再实现 `../maxlabel/app/src/main/mallService.ts` 的窗口骨架（登录/商城两个入口，`contextIsolation`/`sandbox`，无 preload）

**Checkpoint**：迁移可执行、订阅能通过 API 发放、桌面端能保存令牌并调用 `/member/**`。

---

## Phase 3: US28 — 用商城会员账号登录桌面端（P1）🎯 MVP

**Goal**：桌面端用商城账号登录，显示身份与订阅状态，重启保持，商城免二次登录。
**Independent Test**：登录 → 显示脱敏账号与订阅状态；重启仍登录；注销后云端入口不可用，本地编辑/打印不受影响。

### Tests

- [X] T025 [P] [US28] 在 `../maxlabel/app/tests/integration/main/mall-auth.test.ts` 先写失败测试：从登录窗口读取 `MAXLABEL_ACCESS_TOKEN`/`MAXLABEL_REFRESH_TOKEN`、只接受精确 origin、拿不到令牌时超时清理、注销清除凭据
- [X] T026 [P] [US28] 在 `../maxlabel/app/tests/unit/main/mall-session.test.ts` 先写失败测试：access token 到期前刷新、刷新失败清会话、刷新后写回窗口 localStorage
- [X] T027 [P] [US28]（登录/过期返回已测；未登录 401 由安全过滤器保证） 在 `yudao-cloud/yudao-module-member/yudao-module-member-server/src/test/java/.../controller/app/subscription/AppMemberSubscriptionControllerTest.java` 先写失败测试：未登录 401、登录返回当前订阅、过期返回 `active=false`
- [X] T028 [P] [US28]（令牌落盘已由 `storefront/src/api/member.spec.ts` 覆盖；LoginDialog 通过 `smsLogin/login` 间接使用） 在 `storefront/src/components/LoginDialog.spec.ts` 补一条断言：`?login=1&redirect=/account` 登录完成后仍写两个 localStorage 键（回归桌面端读取前提）

### Implementation

- [X] T029 [US28] 实现 `../maxlabel/app/src/main/mallAuth.ts`：打开登录窗口、轮询/注入读取令牌、调用 `/member/user/get` 获取会员快照、加密保存
- [X] T030 [US28] 在 `../maxlabel/app/src/main/mallService.ts` 完成登录窗口：URL `/?login=1&redirect=/account`、精确 origin、登录成功关闭、用户取消返回明确错误
- [X] T031 [US28] 在 `../maxlabel/app/src/main/ipc/registerMallIpc.ts` 实现 `mall:login`、`mall:logout`、`mall:session` 处理器并接入 `MaxLabelAPI`
- [X] T032 [US28] 新增 `../maxlabel/app/src/renderer/src/dialogs/MallAccountDialog.tsx`：脱敏账号、订阅状态/到期日、续费入口、退出登录；接 `ModalHost.tsx` 与 `modalTypes.ts`
- [X] T033 [US28] 修改 `../maxlabel/app/src/renderer/src/pages/StartPage.tsx` 账号区：未登录显示登录按钮，已登录显示会员身份与订阅状态
- [X] T034 [US28] 修改 `../maxlabel/app/src/renderer/src/features/commands/labelShopMenus.ts`：账户菜单改为「登录…/会员中心…/注销…」，移除旧云登录入口
- [X] T035 [US28] 在 `../maxlabel/app/src/main/mallAuth.ts` 实现刷新与窗口写回（R58），并确保未保存文档不受登录窗口影响
- [X] T036 [US28] 隐藏旧云登录/模板 UI（`../maxlabel/app/src/renderer/src/dialogs/CloudDialog.tsx` 的认证与模板块、旧云菜单项），代码保留待确认

**Checkpoint**：桌面端可用三种商城登录方式登录、显示订阅、重启保持；本地功能回归。

---

## Phase 4: US29 — 在桌面端购买并开通会员授权（P1）

**Goal**：从桌面端进入 `/membership` 购买，支付成功自动开通/顺延，桌面端显示新到期日。
**Independent Test**：未订阅账号买月卡 → 订阅生效；再买年卡 → 到期日顺延；纯虚拟单不要地址。

### Tests

- [X] T037 [P] [US29]（两会员订单项 handler 用例已加；续费顺延/重复幂等由 `MemberSubscriptionServiceImplTest` 覆盖） 在 `.../trade-server/src/test/java/.../service/order/handler/TradeMaxLabelSubscriptionOrderHandlerTest.java` 补集成用例：订单含两个会员订单项、续费顺延、重复回调幂等、单项退款只标记对应订阅
- [X] T038 [P] [US29] 在 `storefront/src/views/MembershipView.spec.ts` 先写失败测试：加载计划、渲染月/年卡片、点击跳 `/product/:id`、空计划/加载失败提示
- [X] T039 [P] [US29] 在 `storefront/src/api/subscription.spec.ts` 先写失败测试：`GET /product/subscription-plan/list` 的请求与响应映射
- [X] T040 [P] [US29] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/member/subscription/plan/index.spec.ts` 先写失败测试：列表、启停、价格 ≤ 0 的拒绝提示

### Implementation

- [X] T041 [US29] 新增 `storefront/src/api/subscription.ts`（计划列表）与 `storefront/src/views/MembershipView.vue`（周期卡片）
- [X] T042 [US29] 在 `storefront/src/router/index.ts` 增加 `/membership` 路由，并把 `MembershipView` 加入 `storefront/src/styles/design-consistency.spec.ts` 的 `NEW_PAGE_VIEWS`
- [X] T043 [US29] 新增 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/product/subscriptionPlan/index.ts` 与 `src/views/member/subscription/plan/{index.vue,PlanForm.vue}`（SPU/SKU 选择、周期、启用、排序；当前会员 SPU 只读，切换前需先停用旧计划）
- [X] T044 [US29] 在 `../maxlabel/app/src/renderer/src/dialogs/MallAccountDialog.tsx` 增加「购买/续费」按钮：调用 `mall:open` 打开 `/membership`，不内嵌收银台、不显示自算价格
- [X] T045 [US29] 在 `../maxlabel/app/src/main/mallAuth.ts` 增加「回到桌面端后刷新订阅」：窗口关闭/焦点恢复时重新 `GET /member/subscription/get`
- [X] T046 [US29] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/member/subscription/plan/` 增加前置提示与保存拦截：SKU 价格 ≤ 0、SPU 非虚拟商品、已有其他启用会员 SPU；缺少一月/一年周期时只提示、不阻止（对应 R49/H2/H4/L4）

**Checkpoint**：购买闭环可用；续费顺延；后台能看到来源订单。

---

## Phase 5: US33 — 后台查看每个会员的订阅周期（P1）

**Goal**：会员列表显示订阅状态/到期日；会员详情「订阅」页签展示每段周期。
**Independent Test**：有/无/过期三种会员在列表与详情展示正确；续费后出现新段。

### Tests

- [X] T047 [P] [US33] 在 `.../member-server/src/test/java/.../controller/admin/subscription/MemberSubscriptionControllerTest.java` 先写失败测试：按 `userId` 分页、倒序、`active` 计算（到期日当天有效 / 次日 00:00 过期）、`refunded` 展示
- [X] T048 [P] [US33] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/member/user/detail/UserSubscriptionList.spec.ts` 先写失败测试：渲染/空态/分页
- [X] T049 [P] [US33] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/member/user/index.spec.ts` 先写失败测试：三列订阅状态展示

### Implementation

- [X] T050 [US33] 新增 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/member/subscription/index.ts` 与 `src/views/member/user/detail/UserSubscriptionList.vue`（展示 startDate/endDate/remainingDays/来源订单/退款标记）
- [X] T051 [US33] 在 `src/views/member/user/detail/index.vue` 增加「订阅」懒加载页签
- [X] T052 [US33] 在 `src/views/member/user/index.vue` 增加订阅状态/到期时间列（按 `member:subscription:query` 控制）
- [X] T053 [US33] 验证 `014-maxlabel-member.sql` 中 `member:subscription:query` 行已由 T007 插入，并用非超管角色确认列/页签可见性

**Checkpoint**：运营能在列表与详情回答"会员到什么时候"。

---

## Phase 6: US30 — 上传本地模板到云端（P2）

**Goal**：会员上传/下载/覆盖/删除自己的私有模板，配额与归属正确，敏感字段被剥离。
**Independent Test**：A 上传 → A 换设备可下载打印；B 看不到；配额与 16 MB 边界明确报错。

### Tests

- [X] T054 [P] [US30] 在 `.../member-server/src/test/java/.../service/template/LabelTemplateServiceImplTest.java` 先写失败测试：归属、16 MB、1000 个/256 MB 配额、同名覆盖/另存、删除、**删除不校验订阅状态**、**100 个模板/100 MB 批量上传**
- [X] T055 [P] [US30]（登录用户委托/正文返回已测；403/404 由服务层测试覆盖） 在 `.../member-server/src/test/java/.../controller/app/template/AppLabelTemplateControllerTest.java` 先写失败测试：未登录 401、无有效订阅写操作 403、非本人 404
- [X] T056 [P] [US30] 在 `../maxlabel/app/tests/integration/main/mall-template-repository.test.ts` 先写失败测试：create/update/delete/list/get 的请求形状、16 MB 拒绝、错误映射
- [X] T057 [P] [US30] 在 `../maxlabel/app/tests/integration/main/template-redaction.test.ts` 先写失败测试：上传前 DB/RFID 口令被剥离

### Implementation

- [X] T058 [US30] 创建 `MaxLabelLabelTemplateDO` 与 Mapper（`.../dal/{dataobject,mysql}/template/`）
- [X] T059 [US30] 实现 `LabelTemplateServiceImpl`：配额、16 MB、归属、SHA-256/大小、服务端清洗
- [X] T060 [US30] 实现 app 控制器 `.../controller/app/template/AppLabelTemplateController.java` 的 page/get/create/update/delete（契约 `app-api.md` §2）
- [X] T061 [US30] 在 `../maxlabel/app/src/main/mallRepository.ts` 增加模板 CRUD 方法
- [X] T062 [US30] 在 `../maxlabel/app/src/main/ipc/registerMallIpc.ts` 增加模板通道：`mall:template-list/load/save/delete`
- [X] T063 [US30] 新增 `../maxlabel/app/src/renderer/src/dialogs/CloudTemplateDialog.tsx`：列表、上传、下载、覆盖/另存、删除、配额错误提示
- [X] T064 [US30] 在 `../maxlabel/app/src/renderer/src/features/commands/labelShopMenus.ts` 与 `ModalHost.tsx` 接入云模板入口，替换旧 `CloudDialog` 的模板功能
- [X] T065 [US30] 在 `../maxlabel/app/src/main/mallRepository.ts` 使用 32 MB 响应上限下载模板（R60），并处理 16 MB 边界

**Checkpoint**：私有模板闭环可用，跨账号不可见。

---

## Phase 7: US31 — 使用平台共享模板库（P2）

**Goal**：会员提交共享 → 运营审核 → 其他会员浏览/下载；副本不回写、不泄露私有数据。
**Independent Test**：A 提交待审核 → 运营通过 → B 可见可下载；B 修改副本不影响 A；驳回有原因。

### Tests

- [X] T066 [P] [US31] 在 `.../member-server/src/test/java/.../service/template/SharedTemplateServiceImplTest.java` 先写失败测试：清洗（DB/RFID/数据集行）、状态机、重复提交复用行、删除源模板级联、**取消共享不校验订阅状态**
- [X] T067 [P] [US31]（发布者映射已测；仅返回已通过由 mapper 查询与服务层测试覆盖） 在 `.../member-server/src/test/java/.../controller/app/template/AppSharedTemplateControllerTest.java` 先写失败测试：只返回已通过、未登录 401、分页/筛选
- [X] T068 [P] [US31] 在 `.../member-server/src/test/java/.../controller/admin/template/AdminSharedTemplateControllerTest.java` 先写失败测试：通过/驳回（原因必填）/下架/状态不允许
- [X] T069 [P] [US31] 在 `../maxlabel/app/tests/integration/main/mall-shared-repository.test.ts` 先写失败测试：提交、取消、列表、下载
- [X] T070 [P] [US31] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/member/template/review/index.spec.ts` 先写失败测试：状态筛选、通过、驳回弹窗、下架

### Implementation

- [X] T071 [US31] 创建 `MaxLabelSharedTemplateDO` 与 Mapper（`.../dal/{dataobject,mysql}/template/`）
- [X] T072 [US31] 实现 `SharedTemplateServiceImpl`：服务端清洗、状态机、唯一 `source_template_id`
- [X] T073 [US31] 实现 app 控制器：`submit-share`、`cancel-share`、`shared-template/page`、`shared-template/get`（契约 `app-api.md` §2.6~§3）
- [X] T074 [US31] 实现 admin 控制器：`shared-template/page|get|approve|reject|offline`（契约 `admin-api.md` §4）；通过/驳回/下架写 `audit_user_id`/`audit_time`（模块无 operatelog 依赖，不引入新依赖）
- [X] T075 [US31] 平台共享模板走商城：提交、取消、浏览、下载（实现为独立对话框 `MallSharedLibraryDialog`，未改旧 TemplateLibDialog）
- [X] T076 [US31] 在 `../maxlabel/app/src/main/ipc/registerMallIpc.ts` 增加共享通道，并在 `mallRepository.ts` 增加对应方法
- [X] T077 [US31] 新增 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/member/template/index.ts` 与 `src/views/member/template/review/index.vue`
- [X] T078 [US31] 验证 `014-maxlabel-member.sql` 中共享模板审核页面/按钮权限已由 T007 插入并挂到会员中心菜单
- [X] T079 [US31] 处理边界：删除私有模板级联共享副本、下架后已下载副本不受影响、重新提交复用行

**Checkpoint**：共享闭环与审核闭环可用，私有数据不出现在共享正文。

---

## Phase 8: US32 — 在桌面端浏览并购买商城商品（P2）

**Goal**：桌面端打开商城窗口，自动登录，可浏览/加购/支付；会话与桌面端一致。
**Independent Test**：桌面端打开商城顶栏已登录；加购虚拟/实物商品分别按既有规则结算。

### Tests

- [X] T080 [P] [US32] 在 `../maxlabel/app/tests/integration/main/mall-window.test.ts` 先写失败测试：商城窗口 origin 校验、复用、登录态写回、关闭清理
- [X] T081 [P] [US32]（窗口回读令牌与主进程写回均已测） 在 `../maxlabel/app/tests/unit/main/mall-session.test.ts` 补测试：窗口刷新后回读令牌、主进程刷新后写回

### Implementation

- [X] T082 [US32] 在 `../maxlabel/app/src/main/mallService.ts` 实现商城窗口：`mallBaseUrl`、精确 origin、默认 session、`setWindowOpenHandler` 禁止弹窗
- [X] T083 [US32] 在 `../maxlabel/app/src/main/ipc/registerMallIpc.ts` 实现 `mall:open`
- [X] T084 [US32] 在 `../maxlabel/app/src/renderer/src/pages/StartPage.tsx` 的商城/会员推广区接入 `mall:open`；菜单「云马通首页」改为商城首页
- [X] T085 [US32] 在 `../maxlabel/app/src/renderer/src/features/commands/labelShopMenus.ts` 接入「商城首页 / 会员 / 共享模板库」入口
- [X] T086 [US32] 实现 R58 的双向同步：主进程刷新写回窗口；窗口刷新后主进程回读
- [X] T087 [US32] 在 `../maxlabel/app/src/main/mallService.ts` 处理商城地址未配置/非法 URL 的明确提示（不阻塞本地功能）

**Checkpoint**：桌面端与商城登录态一致，购物链路可用。

---

## Phase 9: US34 — 后台查看模板与打印记录（P2）

**Goal**：打印记录自动上报（无数据快照）；后台会员详情可看模板与打印记录；审核页可预览清洗内容。
**Independent Test**：打印 3 次 → 后台 3 条且与本地一致；测试打印/关闭开关/断网不产生记录；模板页签可预览/下载。

### Tests

- [X] T088 [P] [US34] 在 `.../member-server/src/test/java/.../service/printrecord/PrintRecordServiceImplTest.java` 先写失败测试：字段白名单、`recordUid` 幂等、无 `dataSnapshot`、上限
- [X] T089 [P] [US34] 在 `../maxlabel/app/tests/integration/main/print-report.test.ts` 先写失败测试：已登录+开关开才上报、跳过测试打印、失败吞掉、无补传
- [X] T090 [P] [US34] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/member/user/detail/UserPrintRecordList.spec.ts` 先写失败测试：分页、时间筛选、空态
- [X] T091 [P] [US34] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/member/user/detail/UserLabelTemplateList.spec.ts` 先写失败测试：列表、预览/下载、共享状态

### Implementation

- [X] T092 [US34] 创建 `MaxLabelPrintRecordDO` 与 Mapper（`.../dal/{dataobject,mysql}/printrecord/`）
- [X] T093 [US34] 实现 `PrintRecordServiceImpl` 与 app 控制器 `POST /member/print-record/create`（契约 `app-api.md` §4）
- [X] T094 [US34] 实现 admin 控制器 `GET /member/print-record/page`（契约 `admin-api.md` §5）
- [X] T095 [US34] 新增 `../maxlabel/app/src/main/printReport.ts`：生成/持久化设备 UUID、白名单投影、调用上报、失败吞掉
- [X] T096 [US34] 在 `../maxlabel/app/src/main/ipc/registerLogIpc.ts` 的 `log:print` 本地追加成功后接 `printReport`（跳过 `test`）
- [X] T097 [US34] 在 `../maxlabel/app/src/renderer/src/dialogs/OptionsDialog.tsx` 增加「上报打印记录」开关（默认开）
- [X] T098 [US34] 新增 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/member/printRecord/index.ts` 与 `src/views/member/user/detail/UserPrintRecordList.vue`
- [X] T099 [US34] 新增 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/member/template/index.ts`（admin）与 `src/views/member/user/detail/UserLabelTemplateList.vue`（预览/下载）
- [X] T100 [US34] 在 `src/views/member/user/detail/index.vue` 增加「标签模板」「打印记录」懒加载页签
- [X] T101 [US34] 验证 `014-maxlabel-member.sql` 中 `member:print-record:query`、`member:cloud-template:query` 权限行已由 T007 插入

**Checkpoint**：后台可按会员查看模板与打印记录；上报不影响打印。

---

## Phase 10: Polish & Cross-Cutting Concerns

- [X] T102 [P] 在 `../maxlabel/app/src/renderer/src/` 清理旧云入口的孤儿菜单/弹窗引用（保留 `cloud.ts`/`cloudRepository.ts`/`cloudCredentials.ts` 代码一版，等待所有者确认删除）
- [X] T103 [P] 更新 `../maxlabel/app/tests/INDEX.md` 与 `../maxlabel/app/tests/README.md`：新增行为到测试的映射
- [X] T104 [P] 更新 `yudao-cloud/sql/mysql/014-maxlabel-member.sql` 头部说明（表、菜单、回滚方式）与 `specs/009.../quickstart.md` 的执行顺序
- [X] T105 运行服务端全量相关测试：`mvn -B test -pl yudao-module-member/yudao-module-member-server,yudao-module-mall/yudao-module-product-server,yudao-module-mall/yudao-module-trade-server`
- [X] T106 运行（storefront 1300 全绿 + ts:check；admin 42 全绿；admin ts:check 仅剩既有无关错误）门面站与管理端：`cd storefront && pnpm test && pnpm ts:check`；`cd yudao-cloud/yudao-ui/yudao-ui-admin-vue3 && pnpm test && pnpm ts:check`
- [X] T107 运行桌面端：`cd ../maxlabel/app && env -u ELECTRON_RUN_AS_NODE npm test`
- [ ] T108 执行 [quickstart.md](./quickstart.md) §2.1~§2.8 手工验收，记录结果与"显式不验证"项
- [X] T109 安全复核并记录到 `specs/009-maxlabel-member-integration/quickstart.md`：令牌不落日志/标题/错误信息；商城窗口精确 origin；私有模板不返回公开 URL；跨会员接口负向测试通过
- [ ] T110 与所有者确认「旧云数据库导入」是否随旧云账户删除，结论写入 `specs/009-maxlabel-member-integration/plan.md` 的 R57；确认前保持入口隐藏、代码保留

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**：无依赖
- **Foundational (Phase 2)**：依赖 Setup，**阻塞所有故事**
- **US28 (Phase 3)**：依赖 Foundational；是所有后续故事的身份基座
- **US29 (Phase 4)**：依赖 Foundational（订阅/计划/支付钩子）；与 US28 可并行，但演示需要登录
- **US33 (Phase 5)**：依赖 US29 的数据；接口可用种子数据独立测试
- **US30 (Phase 6)**：依赖 US28（登录）与 Foundational
- **US31 (Phase 7)**：依赖 US30（私有模板）
- **US32 (Phase 8)**：依赖 US28（会话）
- **US34 (Phase 9)**：打印上报依赖 US28；后台模板页签依赖 US30；共享审核预览依赖 US31
- **Polish (Phase 10)**：依赖所有目标故事

### User Story Dependencies

| 故事 | 可独立开始 | 外部依赖 |
|---|---|---|
| US28 | Foundational 完成 | 无 |
| US29 | Foundational 完成 | 商品/计划需先配置（quickstart 0.2） |
| US33 | US29 完成（或种子订阅） | 无 |
| US30 | US28 完成 | 无 |
| US31 | US30 完成 | 审核页依赖 admin 基座 |
| US32 | US28 完成 | 商城地址已配置 |
| US34 | US28 完成；模板页签依赖 US30 | 共享审核预览可选 |

### Within Each Story

- 测试先行并确认失败 → 模型 → 服务 → 接口 → UI/IPC
- 建表/枚举例外：先落结构，再补行为测试
- 每个 Checkpoint 必须能独立演示

---

## Parallel Opportunities

- **Setup**：T001~T004 可并行（不同文件）
- **Foundational**：T005~T007（SQL/测试 DDL）、T008~T012（product）、T013~T017（member）、T018~T020（trade）、T021~T024（桌面端）可分组并行；注意 T009/T014/T018 的测试依赖结构先落
- **US28**：T025~T028 测试并行；T029/T030/T031 按依赖顺序
- **US29**：T037~T040 测试并行；storefront（T041/T042）与 admin（T043/T046）与 desktop（T044/T045）可并行
- **US30/31**：后端（T058~T060 / T071~T074）与桌面端（T061~T065 / T075~T076）可并行
- **US34**：后端（T092~T094）与桌面端（T095~T097）与 admin（T098~T100）可并行

### Parallel Example: US28

```text
Task: "T025 登录窗口令牌读取集成测试"
Task: "T026 令牌刷新/写回单元测试"
Task: "T027 当前订阅接口测试"
Task: "T028 门面站登录 localStorage 回归断言"
```

### Parallel Example: US29

```text
Task: "T037 支付→发放集成测试"
Task: "T038 MembershipView 组件测试"
Task: "T039 计划列表 API 测试"
Task: "T040 后台计划页测试"
```

### Parallel Example: US33

```text
Task: "T047 后台订阅接口测试"
Task: "T048 订阅页签组件测试"
Task: "T049 会员列表订阅列测试"
```

### Parallel Example: US30

```text
Task: "T054 模板服务测试"
Task: "T055 模板接口契约测试"
Task: "T056 桌面端模板仓库测试"
Task: "T057 redaction 测试"
```

### Parallel Example: US31

```text
Task: "T066 共享清洗/状态机测试"
Task: "T067 共享列表接口测试"
Task: "T068 审核接口测试"
Task: "T069 桌面端共享仓库测试"
Task: "T070 后台审核页测试"
```

### Parallel Example: US32

```text
Task: "T080 商城窗口集成测试"
Task: "T081 双端令牌同步测试"
```

### Parallel Example: US34

```text
Task: "T088 打印记录服务测试"
Task: "T089 桌面端上报测试"
Task: "T090 后台打印记录页测试"
Task: "T091 后台模板页签测试"
```

---

## Implementation Strategy

### MVP First（US28 + US29 + US33）

1. Phase 1 + Phase 2 地基
2. US28 登录
3. US29 购买/自动开通
4. US33 后台订阅
5. **STOP & VALIDATE**：登录 → 买会员 → 后台可见周期

### Incremental Delivery

1. 地基 → 订阅闭环（US28/US29/US33）→ 部署/演示
2. US30 私有模板 → 演示
3. US31 共享模板 + 审核 → 演示
4. US32 商城入口 → 演示
5. US34 打印记录 + 后台页签 → 演示
6. Polish + 手工验收

### 显式不作为本期验收

- 真实微信扫码、真实支付、真实短信（见 quickstart §3）
- 0 元会员订单
- 旧云数据库导入（待所有者确认）

---

## Notes

- 每个故事结束后运行该层测试并保持全绿；再进入下一故事
- 新增表必须同步 `create_tables.sql`/`clean.sql`，否则模块测试起不来
- 权限字符串必须同时插入 `system_menu`，否则按钮不显示
- 桌面端 `net.ts` 的 PUT 与 32 MB 响应上限是模板下载的前置（R60）
- 旧云代码删除待所有者确认，先隐藏入口
