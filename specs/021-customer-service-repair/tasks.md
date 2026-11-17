---

description: "Task list for 客户服务（投诉咨询与在线维修）"
---

# Tasks: 客户服务（投诉咨询与在线维修）

**Input**: Design documents from `specs/021-customer-service-repair/`
**Prerequisites**: plan.md（必需）、spec.md（用户故事）、research.md、data-model.md、contracts/

**Tests**: 按宪法原则 I（测试先行，不可协商），每个故事的测试任务 MUST 先写、先失败，
再进入实现。任务编号按**执行顺序**排列。

**Organization**: 按用户故事分阶段；US70 / US71 为 P1，US72 / US73 为 P2，US74 / US75 为 P3。

## Format: `[ID] [P?] [Story] Description`

- **[P]**：不同文件、无未完成依赖，可并行
- **[Story]**：仅用户故事阶段需要
- 路径均为仓库相对路径

## Path Conventions

- 后端：`yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/`
- 后端测试：`yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/`
- 管理端：`yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/`
- 门面站：`storefront/src/`
- SQL：`yudao-cloud/sql/mysql/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建表脚本、枚举、错误码等所有故事共用的基础

- [x] T001 新建 `yudao-cloud/sql/mysql/043-customer-service.sql`：5 张表（`trade_customer_service_ticket`、`trade_repair_request`、`trade_repair_order`、`trade_customer_service_log`、`trade_customer_service_address`）+ 索引 + 唯一键 `uk_request(tenant_id, repair_request_id)`；字段与约束逐条按 `data-model.md` §1~§6。回收地址每租户一条由服务层保证（软删除下不加 DB 唯一键）
- [x] T002 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java` 增加 `1_011_013_xxx` 段错误码（`data-model.md` §7 全部 15 条）
- [x] T003 [P] 在 `.../trade-api/.../enums/LogRecordConstants.java` 增加客户服务操作日志常量（处理、开单、确认收款、改状态、取消、地址修改）
- [x] T004 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/enums/customerservice/` 新建枚举：工单类型、工单状态、维修状态、支付状态、来源、日志目标类型/动作
- [x] T005 同步 H2 测试建表脚本：`.../trade-server/src/test/resources/sql/create_tables.sql` 与 `clean.sql` 加入 5 张表（依赖 T001）
- [x] T006 补齐菜单 SQL（并入 `043-customer-service.sql`）：顶层目录「客户服务」+ 页面「服务工单」+ 按钮权限 `trade:customer-service:query` / `trade:customer-service:handle`，沿用 `026` 的幂等写法

**Checkpoint**: 表、枚举、错误码、菜单就绪。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有故事共用的 DO / Mapper / 日志 / 附件 / 会员检索 / 回收地址 service

**⚠️ CRITICAL**: 本阶段完成前不要进入任何用户故事

- [x] T007 新建 5 个 DO：`.../dal/dataobject/customerservice/CustomerServiceTicketDO.java`、`CustomerServiceRepairRequestDO.java`、`CustomerServiceRepairOrderDO.java`、`CustomerServiceLogDO.java`、`CustomerServiceAddressDO.java`（字段与 `data-model.md` 一致）
- [x] T008 新建 5 个 Mapper：`.../dal/mysql/customerservice/` 下对应 Mapper，分页/按会员/按状态查询用 MyBatis-Plus Wrapper，不写复杂手写 SQL
- [x] T009 实现 `.../service/customerservice/CustomerServiceLogService(+Impl)`：追加日志（目标类型/编号、动作、前后状态、备注、操作人类型/编号），只增不改；单测 `.../src/test/java/.../service/customerservice/CustomerServiceLogServiceImplTest.java` 先覆盖「追加后可查、不会被更新」
- [x] T010 [P] 实现附件校验工具 `.../service/customerservice/CustomerServiceFileValidator`：只允许 JPG/PNG/WEBP、≤ 5 张、单张 ≤ 10 MB、URL 域名合法；单测 `CustomerServiceFileValidatorTest` 先覆盖四类拒绝场景
- [x] T011 [P] 实现 `.../service/customerservice/CustomerServiceMemberSupport`：按关键字（11 位手机号精准 / 昵称模糊）调用 `MemberUserApi` 检索会员并校验存在性；单测覆盖手机号精准、昵称模糊与不存在
- [x] T012 [P] 实现 `.../service/customerservice/CustomerServiceAddressService(+Impl)`：`get`（未配置返回 null）与 `update`（每租户一条，字段必填）；单测 `CustomerServiceAddressServiceImplTest` 先覆盖首次创建、重复保存只留一条、未配置返回 null。**该 service 是 US73 读取与 US74 维护的唯一实现，后续不得重复新建**

**Checkpoint**: 共享能力就绪，故事可并行开始。

---

## Phase 3: User Story 70 - 客户提交投诉咨询并看到处理结果 (Priority: P1) 🎯 MVP

**Goal**: 个人中心提交投诉/咨询；管理端查看并填写处理说明；客户可见状态与说明。

**Independent Test**: 客户 A 提交投诉与咨询各一条；管理端可见内容/电话/图片并处理；A 刷新看到说明；
客户 B 看不到 A 的记录；60 秒内重复提交只生成一条。

### Tests for User Story 70（先写、先失败）

- [x] T013 [P] [US70] 新建 `.../src/test/java/.../service/customerservice/CustomerServiceTicketServiceImplTest.java`：内容/电话必填、60 秒相同内容去重、跨会员不可见、处理说明必填、重复处理被拒、**代建时 `source=2` 且 `creator_id=管理员`**
- [x] T014 [P] [US70] 新建 `.../src/test/java/.../controller/admin/customerservice/AdminCustomerServiceTicketControllerPermissionTest.java`：`query` 与 `handle` 的 `@PreAuthorize` 反射断言
- [x] T015 [P] [US70] 新建 `storefront/src/views/CustomerServiceView.spec.ts`：未登录拦截、内容必填、提交后列表出现、处理说明可见、他人记录不可见

### Implementation for User Story 70

- [x] T016 [US70] 新建 `.../service/customerservice/CustomerServiceTicketService(+Impl)`：创建（接收 `memberId`、`source`、`creatorId`，供 US75 复用）、我的分页/详情、管理端分页/详情、处理；校验与 `data-model.md` §1 一致
- [x] T017 [US70] 新建 `.../controller/app/customerservice/AppCustomerServiceTicketController.java` 与 VO：`POST create`、`GET page`、`GET get`；只返回当前会员数据
- [x] T018 [US70] 新建 `.../controller/admin/customerservice/AdminCustomerServiceTicketController.java` 与 VO：`GET page`（类型/状态/来源/关键字/时间）、`GET get`、`PUT handle`；`@PreAuthorize` 按 T014 断言
- [x] T019 [P] [US70] 新建 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/trade/customerService/index.ts`：工单分页/详情/处理接口与类型
- [x] T020 [US70] 新建 `.../views/trade/customer-service/index.vue` 的「投诉咨询」页签与组件（表格、筛选、详情弹窗、处理弹窗），依赖 T019；组件测试 `.../views/trade/customer-service/components/TicketTable.spec.ts` 先断言筛选与处理按钮
- [x] T021 [P] [US70] 新建 `storefront/src/api/customerService.ts`：工单创建/我的分页/详情
- [x] T022 [US70] 新建 `storefront/src/views/CustomerServiceView.vue` 的「投诉咨询」表单（图片上传复用 `@/api/upload`）与记录列表；`storefront/src/components/AccountSidebar.vue` 增加「客户服务」入口；`storefront/src/router/index.ts` 增加 `/account/customer-service`；依赖 T021

**Checkpoint**: 投诉咨询可独立交付并验证。

---

## Phase 4: User Story 71 - 客户提交在线维修申请 (Priority: P1)

**Goal**: 客户提交维修申请（一台设备一条），管理端可查看，客户可跟踪状态。

**Independent Test**: 提交维修申请，后台可见型号/故障/电话/收件信息/图片；客户侧状态「待联系」；
未登录不可提交；两台设备必须分别提交。

### Tests for User Story 71（先写、先失败）

- [x] T023 [P] [US71] 新建 `.../service/customerservice/CustomerServiceRepairRequestServiceImplTest.java`：型号/故障/电话/收件信息必填、跨会员不可见、一申请一台设备、初始状态待联系、**`updateStatus` 仅允许 30→40、40→50，非法迁移拒绝**、**代建 `source=2`/`creator_id`**（`0→10` 的测试归 T030、`20→30` 归 T036）
- [x] T024 [US71] 在 `storefront/src/views/CustomerServiceView.spec.ts` 增加维修申请表单校验与提交后状态断言（与 T015 同文件，串行执行）

### Implementation for User Story 71

- [x] T025 [US71] 新建 `.../service/customerservice/CustomerServiceRepairRequestService(+Impl)`：创建（接收 `memberId`/`source`/`creatorId`）、我的分页/详情、管理端分页/详情、`updateStatus`（仅 30→40、40→50）；字段约束按 `data-model.md` §2。**`cancel` 不在本故事实现**（需引用 US72 才有的订单 service，放到 T032）
- [x] T026 [US71] 新建 `.../controller/app/customerservice/AppCustomerServiceRepairController.java` 与 VO：`POST create`、`GET page`、`GET get`
- [x] T027 [US71] 新建 `.../controller/admin/customerservice/AdminCustomerServiceRepairController.java` 与 VO：`GET page`（状态/来源/关键字/时间）、`GET get`
- [x] T028 [P] [US71] 在管理端 `api/trade/customerService/index.ts` 增加维修申请分页/详情；在 `views/trade/customer-service/index.vue` 增加「在线维修」页签与列表/详情组件（依赖 T020）
- [x] T029 [P] [US71] 在 `storefront/src/api/customerService.ts` 增加维修申请接口；`CustomerServiceView.vue` 增加「在线维修」表单（返修收件地址复用 `@/api/address` 的 `listAddress`，图片上传复用 `@/api/upload`）与记录列表（依赖 T022）

**Checkpoint**: 投诉咨询与维修申请均可独立使用。

---

## Phase 5: User Story 72 - 客服电话确认后生成维修订单 (Priority: P2)

**Goal**: 客服记录电话结果，填写维修费 + 寄回运费生成维修订单；申请进入「已开单」；客户可见订单。

**Independent Test**: 对「待联系」申请开单（100 + 20 元），客户立即看到费用与应付合计；
再次开单被拒；费用为 0 时显示无需支付；负数/缺说明被拒。

### Tests for User Story 72（先写、先失败）

- [x] T030 [P] [US72] 新建 `.../service/customerservice/CustomerServiceRepairOrderServiceImplTest.java`：两项费用 ≥ 0、说明必填、应付合计、一申请一单（含并发唯一键）、0 元→无需支付、申请状态→已开单、**联系记录使状态 0→10**、**取消编排：reason 必填、50/60 拒绝、未支付→申请 60 + 订单 pay_status=40、已支付→提示先人工退款**、`cancelByRequest` 的订单侧断言
- [x] T031 [P] [US72] 新建 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/trade/customer-service/components/OrderCreateDialog.spec.ts`：费用负数/说明为空拦截、已开单后按钮禁用

### Implementation for User Story 72

- [x] T032 [US72] 新建 `.../service/customerservice/CustomerServiceRepairOrderService(+Impl)`：`createOrder`（事务内校验申请状态、写订单、改申请状态、写日志）、`recordContact`（申请 0→10）、`cancel`（reason 必填、50/60 拒绝；未支付→申请 60 + 订单 40，已支付→提示先人工退款）、`cancelByRequest`（订单侧）、按申请查询订单。**跨聚合编排统一放本 service（依赖请求 service，反向不依赖），避免循环依赖**
- [x] T033 [US72] 在 `AdminCustomerServiceRepairController` 增加 `PUT contact`（调 order service `recordContact`）、`POST order/create`（调 `createOrder`）、`PUT status`（维修中/已完成，调 request service `updateStatus`）、`PUT cancel`（原因必填，调 order service `cancel`）与对应 VO；`@PreAuthorize('trade:customer-service:handle')`
- [x] T034 [US72] 新建 `.../views/trade/customer-service/components/ContactDialog.vue`、`.../components/OrderCreateDialog.vue`（维修费/寄回运费/说明、金额校验），并在 `index.vue` 接入联系记录与开单入口
- [x] T035 [US72] 门面站维修详情展示维修订单卡片（费用明细、应付合计、费用说明、支付状态）；**先**在 `CustomerServiceView.spec.ts` 写对应断言并确认失败，再实现（与 T015/T024 同文件，串行）

**Checkpoint**: 维修报价/开单闭环可验证。

---

## Phase 6: User Story 73 - 客户支付维修订单并查看回收邮寄地址 (Priority: P2)

**Goal**: 客户在线支付或对公转账；支付后标记已寄出并回填运单号；查看回收地址；客服推进维修状态。

**Independent Test**: 支付 120 元订单后状态已支付、地址可见；未支付不能寄出；标记已寄出后后台可见；
对公转账确认后状态已支付；重复回调不重复扣款。

### Tests for User Story 73（先写、先失败）

- [x] T036 [P] [US73] 在 `CustomerServiceRepairOrderServiceImplTest` 增加：在线支付单复用/幂等、支付回调重复、对公可选/确认、未支付禁止寄出、**已支付标记寄出使申请状态 20→30**、运单号必填、地址未配置提示、已支付不可自助退款
- [x] T037 [US73] 在 `storefront/src/views/CustomerServiceView.spec.ts` 增加：未支付时无寄出操作、支付后显示地址与寄出表单、无地址显示求助提示、无退款入口（与 T015/T024/T035 同文件，串行）
- [x] T038 [P] [US73] 新建 `.../src/test/java/.../controller/app/customerservice/AppTradeOrderCallbackRoutingTest.java`：`merchantOrderId` 为 `repair:<id>` 时命中维修服务、纯数字仍走交易订单服务（**唯一既有代码改动的回归护栏**）

### Implementation for User Story 73

- [x] T039 [US73] 实现 `CustomerServiceRepairOrderService#payOnline`：首次创建 pay 单（`merchantOrderId = repair:<id>`），重复调用复用；返回 `payOrderId`；并在 `.../controller/app/customerservice/AppCustomerServiceRepairOrderController.java` 增加 `POST pay-online` 与对应 VO
- [x] T040 [US73] 实现 `CustomerServiceRepairOrderService#chooseOffline`：校验 `trade_config.offline_pay_enabled`（null 兜底），状态→待确认收款，返回收款信息；在同上的 `AppCustomerServiceRepairOrderController` 增加 `POST pay-offline` 与对应 VO
- [x] T041 [US73] 实现 `CustomerServiceRepairOrderService#updateOrderPaid`：支付回调入口；校验已支付+同支付单号幂等；写支付时间/渠道/单号与日志
- [x] T042 [US73] 修改 `.../controller/app/order/AppTradeOrderController.java#updateOrderPaid`：`merchantOrderId` 以 `repair:` 开头时委派给 `CustomerServiceRepairOrderService`，否则维持原逻辑（唯一对既有代码的改动）
- [x] T043 [US73] 实现 `CustomerServiceRepairOrderService#confirmOffline`：条件更新 10→20，调用 `PayOrderApi.createPaidOfflineOrder` 并回写支付单号；管理端 `POST repair-order/confirm-offline` 与 `GET repair-order/get`
- [x] T044 [US73] 在 `CustomerServiceRepairOrderService` 增加 `markShipped`（校验 pay_status ∈ {20 已支付, 30 无需支付}、运单号必填、申请 20→30）与客户可见日志；App `POST repair/ship` 调用该方法
- [x] T045 [US73] 将回收地址读取并入 App 维修详情 `repairOrder/returnAddress/logs`，并实现独立接口 `GET /trade/customer-service/return-address`（复用 T012 的 service，不重复新建）
- [x] T046 [P] [US73] 新建 `storefront/src/components/MemberPayDialog.vue`（复用 `@/api/pay` 的渠道选择/`submitPay`/`getPayOrder`，微信 JSAPI 交互参照 `OrderDetailView.vue` 但不改它）；维修详情接入在线支付、对公转账、寄出表单与地址卡片；**不渲染退款入口**
- [x] T047 [P] [US73] 新建 `.../views/trade/customer-service/components/RepairOrderPanel.vue`：支付状态展示、对公确认收款弹窗（流水号/付款人/到账时间）、状态推进（维修中/已完成）与取消（原因必填）

**Checkpoint**: 维修支付与寄件闭环完成。

---

## Phase 7: User Story 74 - 管理端维护回收邮寄地址 (Priority: P3)

**Goal**: 客服维护公司回收地址；客户维修单展示最新地址；未配置有明确提示。

**Independent Test**: 修改地址后客户刷新可见；清空配置客户看到求助提示。

### Tests for User Story 74（先写、先失败）

- [x] T048 [P] [US74] 在 `.../service/customerservice/CustomerServiceAddressServiceImplTest.java`（T012 已建）增加管理端保存后的读取一致性、清空/未配置返回 null
- [x] T049 [US74] 在 `storefront/src/views/CustomerServiceView.spec.ts` 增加：地址修改后刷新可见、未配置显示提示（与 T015/T024/T035/T037 同文件，串行）

### Implementation for User Story 74

- [x] T050 [US74] 新建 `.../controller/admin/customerservice/AdminCustomerServiceAddressController.java`：`GET address`、`PUT address`，权限 `handle`；复用 T012 的 service
- [x] T051 [US74] 新建 `.../views/trade/customer-service/components/ReturnAddressForm.vue` 并接入 API；在 `index.vue` 增加「回收地址」页签
- [x] T052 [US74] 门面站维修详情的地址卡片接入真实地址接口（US73 已接读取，本任务补「修改后刷新」与空态验证）

**Checkpoint**: 地址维护完整可验证。

---

## Phase 8: User Story 75 - 客服代客户创建工单 (Priority: P3)

**Goal**: 客服按手机号/昵称找到会员，代建投诉咨询或维修申请；来源与创建人可追溯。

**Independent Test**: 代建工单后客户可见；未选会员不能提交；后台可按来源筛选。

### Tests for User Story 75（先写、先失败）

- [x] T053 [P] [US75] 新建 `.../service/customerservice/CustomerServiceMemberSupportTest.java`：手机号精准、昵称模糊、不存在返回空；代建校验会员必填
- [x] T054 [P] [US75] 新建 `.../views/trade/customer-service/components/MemberPickerDialog.spec.ts`：未选会员不能提交、提交带 `memberId` 与来源
- [x] T055 [US75] 在 `storefront/src/views/CustomerServiceView.spec.ts` 增加：客服代建的记录对客户可见，且来源不影响客户侧展示（与既有同文件任务串行）

### Implementation for User Story 75

- [x] T056 [US75] 新建 `.../controller/admin/customerservice/AdminCustomerServiceMemberController.java`：`GET member/search`（`handle` 权限）
- [x] T057 [US75] 在 `AdminCustomerServiceTicketController` / `AdminCustomerServiceRepairController` 增加 `POST create`（带 `memberId`），复用 T016 / T025 的服务方法（`source=2`、`creator_id=当前管理员`）
- [x] T058 [US75] 新建 `.../views/trade/customer-service/components/MemberPickerDialog.vue`，并在 `index.vue` 增加两个页签的「代建」入口、会员检索选择器、代建表单（图片上传复用 `@/api/upload`）与来源筛选

**Checkpoint**: 电话报修/投诉可在后台录单。

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 权限、回归、文档与上线准备

- [x] T059 [P] 新建 `.../src/test/java/.../controller/admin/customerservice/CustomerServiceControllerPermissionTest.java`：所有管理端接口的 `@PreAuthorize` 全覆盖（query/handle）
- [x] T060 [P] 后端回归：`mvn -pl yudao-module-mall/yudao-module-trade-server -am -Dtest='Customization*Test,CustomerService*Test' test`，确认定制/交易既有用例未被影响
- [x] T061 [P] 前端回归：`storefront` 与 `yudao-ui-admin-vue3` 的客户服务相关 spec 全绿；`vue-tsc`/ESLint 无新增错误
- [x] T062 更新 `CLAUDE.md`：增加 021 的上线顺序与口径摘要（5 表、菜单/权限、维修支付前缀 `repair:`、对公确认）
> ⚠️ T063 需在可运行环境手工走查（当前会话无法启动完整前后端链路）：
- [ ] T063 按 [quickstart.md](./quickstart.md) §二~§四 走查：至少 4 / 8 / 14 / 20 / 23 / 27 / 32 七条必过
- [x] T064 复核 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql/create_tables.sql` 与 `yudao-cloud/sql/mysql/043-customer-service.sql` 字段一致；复核菜单 SQL 幂等（重复执行不产生重复菜单）
- [x] T065 权限与归属自检：逐接口检查 `.../controller/app/customerservice/` 与 `.../controller/admin/customerservice/` 的 `@PreAuthorize`、会员归属校验与「维修单不出现在我的订单」
- [x] T066 按 [quickstart.md](./quickstart.md) §六 输出上线就绪报告：SQL → 后端 → 管理端 → 门面站顺序、回滚说明（含 `repair:` 回调前缀的联调风险）；**不执行发版**

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup（T001~T006）**：无依赖；T005 依赖 T001，T006 并入 T001 的脚本
- **Phase 2 Foundational（T007~T012）**：依赖 Setup；**阻塞所有故事**
- **Phase 3~8（US70~US75）**：依赖 Phase 2；故事之间可并行（若人员允许），按优先级串行更稳
- **Phase 9 Polish（T059~T066）**：依赖所需故事完成

### User Story Dependencies

- **US70（P1，T013~T022）**：无故事依赖，MVP
- **US71（P1，T023~T029）**：无故事依赖；与 US70 共用 `CustomerServiceView.vue` 与 API 文件，文件层面有先后
- **US72（P2，T030~T035）**：依赖 US71（维修申请存在）
- **US73（P2，T036~T047）**：依赖 US72（维修订单存在）；回收地址读取复用 T012
- **US74（P3，T048~T052）**：依赖 T012 与 US73 的维修详情
- **US75（P3，T053~T058）**：依赖 US70/US71 的服务方法（T016/T025）

### Within Each User Story

- 测试先写、先失败 → DO/Mapper（如未在 Foundational）→ Service → Controller → 前端 → 组件测试
- 同一文件的任务不标 [P]：`CustomerServiceView.spec.ts`（T015/T024/T035/T037/T049/T055）串行；
  `api/trade/customerService/index.ts`（T019/T028/T050/T058）串行；
  `storefront/src/api/customerService.ts`（T021/T029）串行；
  `views/trade/customer-service/index.vue`（T020/T028/T034/T051/T058）串行

### Parallel Opportunities

- T002、T003、T004 可并行（不同文件）
- T010、T011、T012 可并行（不同 service 文件）
- 故事内测试任务（T013/T014/T015、T023/T024、T030/T031、T036/T037/T038、T048/T049、T053/T054/T055）可并行
- 管理端与门面站可并行（不同仓库目录）
- US70 与 US71 在 Phase 2 后可由两人并行（注意共享的 `CustomerServiceView.vue` 与 API 文件）

---

## Parallel Example: User Story 70

```bash
# 先写测试（可并行，三个不同文件）
T013  CustomerServiceTicketServiceImplTest
T014  AdminCustomerServiceTicketControllerPermissionTest
T015  storefront CustomerServiceView.spec.ts（工单部分）

# 后端与前端可并行
T016~T018  Ticket service + app/admin controllers
T019~T022  管理端 API/页面 + 门面站 API/页面
```

---

## Implementation Strategy

### MVP First（US70 + US71）

1. 完成 Phase 1 + Phase 2
2. 完成 US70（投诉咨询）
3. 完成 US71（维修申请）
4. **STOP & VALIDATE**：按 quickstart §二/§三 的 1~13 条验收
5. 再进入 US72~US73 的收款闭环

### Incremental Delivery

1. Setup + Foundational
2. US70 → 独立验证（投诉咨询可用）
3. US71 → 独立验证（维修申请可用）
4. US72 → 开单可用
5. US73 → 支付/寄件闭环
6. US74 → 地址维护
7. US75 → 代建
8. Polish → 权限/回归/上线准备

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- 所有管理端接口必须在服务端校验权限，不依赖前端按钮（FR-578）
- 维修单支付 `merchantOrderId` 前缀是 `repair:`，与交易订单号不会冲突；改动只在 T042
- 任何发版动作都必须等所有者当次确认（宪法原则 VI）

## Phase 10: Convergence

> `/speckit-converge` 2026-11-17：实现完成后对照 spec / plan / tasks 的剩余缺口。

- [x] T067 [US73] 补齐维修支付弹窗的微信公众号 JSAPI（`wx_pub`）：复用 `OrderDetailView` 的 openid 授权与 `WeixinJSBridge` 流程（或抽取共享支付交互），当前仅提示改用其他渠道 per FR-571 (partial)
- [x] T068 [US73] 对公转账在「待确认收款」状态下允许重新获取收款信息（`chooseOffline` 幂等返回 `trade_config`，或在维修详情中返回收款信息），并在个人中心提供「查看收款信息」入口 per FR-571 / US73 (partial)
- [x] T069 [US70] 门面站图片上传补客户端 10MB 单张校验（`CustomerServiceImageUploader`）；服务端只能校验 URL，大小由上传接口兜底，需在 quickstart 注明实际上限 per FR-580 (partial)

## Phase 11: Convergence

> `/speckit-converge` 2026-11-17（T067~T069 之后）。

- [x] T070 [US73] 详情接口补齐契约承诺的流转日志与地址：App `repair/get` 返回 `logs`；Admin `ticket/get`、`repair/get` 返回 `logs`；Admin `repair/get` 返回 `returnAddress` 快照 per contracts/app-api §2 / admin-api §1-2, FR-581 (partial)
- [x] T071 [US73] 维修申请列表补齐维修订单支付状态：App / Admin `repair/page` 批量查询订单并回填 `repairOrderId` / `payStatus`（Admin 合同为「维修单支付状态」），列表展示 per contracts/app-api §2 / admin-api §2, FR-570 (partial)

## Phase 12: Convergence

> `/speckit-converge` 2026-11-17（T070~T071 之后）。

- [x] T072 [US73] 对齐响应契约形状：App `repair/get` 增加嵌套 `repairOrder` 对象（id / repairFee / returnFreight / payableAmount / feeRemark / payStatus / payStatusName，保留现有扁平字段不破坏前端）；App `repair/page` 增加 `repairOrderPayStatus` 别名；Admin `ticket/get` / `repair/get` 的 `logs` 补 `operatorName`（按操作人类型批量取昵称） per contracts/app-api §2 / admin-api §1-2 (partial)

## Phase 13: Convergence

> `/speckit-converge` 2026-11-17（T072 之后）。

- [x] T073 [US75] 管理端维修列表补齐契约中的「开单人」：返回来源类型 + 创建人编号/昵称（`source=1` 取会员昵称、`source=2` 取管理员昵称），列表展示 per contracts/admin-api §2, FR-582 (partial)

## Phase 14: Convergence

> `/speckit-converge` 2026-11-17（T073 之后）。

- [x] T074 [US72] 给「业务写入 + 流转日志」的方法补事务：`CustomerServiceTicketService#createTicket`、`CustomerServiceRepairRequestService#createRepair`、`CustomerServiceRepairOrderService#recordContact` / `payOnline` / `chooseOffline` / `markShipped` 加 `@Transactional(rollbackFor = Exception.class)`，保证状态变更与留痕原子 per FR-579 / FR-581 (partial)
- [x] T075 [US73] 对公确认收款留可追溯信息：`confirmOffline` 把 `bankSerialNo` / `payerName` / `paidTime` 写进流转日志备注（或新增列），当前请求字段被接收后丢弃 per contracts/admin-api §3, FR-576 / FR-581 (partial)
