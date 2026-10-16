---

description: "Task list for 订单开票（下单申请 · 后台开具上传 · 前台下载）"
---

# Tasks: 订单开票（下单申请 · 后台开具上传 · 前台下载）

**Input**: Design documents from `/specs/006-order-invoice/`
**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 宪法 **原则 I（测试先行）不可协商** —— 每个故事内的测试任务 MUST 排在实现任务之前，且 MUST 先失败。

| 端 | 测试基础设施（现状） | 本轮 |
|---|---|---|
| **服务端** `yudao-module-trade-server` | `BaseDbUnitTest` / `application-unit-test.yaml`（H2）/ `sql/create_tables.sql` / `clean.sql` **都在**，但该模块**一个 `src/test/java` 都没有** | ⚠️ **本特性是该模块的第一批测试**。**先补 T373 的建表**，否则新测试类**启动就挂**（不是用例失败） |
| **C 端** `storefront` | Vitest 完备（1150 条 / 86 文件） | 全部覆盖（含**接线**：下单请求体里 `invoice` 逐字段一致） |
| **管理端** `yudao-ui-admin-vue3` | Vitest 已有（003 接入，现 4 个 spec） | 只测本特性**新增**的纯函数与上传弹窗，**不做全量改造** |

**Organization**: 按用户故事分组（`[US19]`~`[US23]`，沿用 spec.md 的编号），每个故事可独立实现、独立验证、独立交付。

## Format: `[ID] [P?] [Story] Description （FR/SC 引用）`

- **[P]**: 可并行（不同文件、无未完成的依赖）
- **[Story]**: 所属用户故事；Setup / Foundational / Polish 阶段**不带**故事标签
- 每条任务写明确切文件路径，**并挂上它承接的需求编号**（FR / SC / Q / R）

> **任务编号从 T370 起**（005 止于 T369）。`T` 与 FR / SC / US 一样是**全仓唯一**命名空间，
> 代码注释与文档会直接引用它。**编号 = 执行顺序**，插任务时整段顺延重排，不留空号。
>
> **与 plan 的「实施分期」的对应**：plan 按**依赖**分组（Phase 1 = US19+US20、Phase 2 = US23、
> Phase 3 = US21、Phase 4 = US22），本文件按 **spec 的优先级**重排（US19 → US20 → US21 → US22 → US23）。
> 两组只是排列不同，**范围完全一致**；引用时以本文件的编号为准。

---

## Phase 1: Setup

**Purpose**: 摸清三个端的测试基线，避免"改完才发现基线本来就是红的"

- [x] T370 记录基线：`cd storefront && npx vitest run`（预期 **1150 条 / 86 文件**全绿）、
      `pnpm -C yudao-cloud/yudao-ui/yudao-ui-admin-vue3 test`（4 个 spec 全绿）、
      并确认 `yudao-module-trade-server` 目前**没有任何 `src/test/java`**（本特性是第一批）
- [x] T371 [P] 读一遍 [data-model.md](./data-model.md) §1/§2 的 DDL 与 [contracts/](./contracts/) 的两份契约，
      把**字段名与接口路径**抄成一张速查表（服务端 DO 字段 / 前端类型字段 / 契约 JSON 三者要逐字一致，
      本仓库吃过"字段名对不上但两边都不报错"的亏）

---

## Phase 2: Foundational（阻塞所有故事）

**Purpose**: 两张表 + 枚举 + DO/Mapper 骨架 —— 任何故事都建在它们之上

- [x] T372 [P] 新增建表迁移脚本 `yudao-cloud/sql/mysql/007-order-invoice.sql`：
      `trade_order_invoice` + `trade_invoice_title`（DDL 照 [data-model.md](./data-model.md) §1/§2，
      含列注释、`uk_order_id` 唯一索引、`idx_status`、`tenant_id`、通用字段）。**在开发库执行一次**
- [x] T373 [P] 把同样两张表补进 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql/create_tables.sql`
      —— **漏了这条，本模块所有新测试类启动就挂**（plan 风险 R5）
- [x] T374 [P] 新增枚举（`yudao-module-trade-api/.../enums/invoice/`）：
      `InvoiceTypeEnum`（普票 1 / 专票 2）、`TradeOrderInvoiceStatusEnum`（待开票 0 / 已开票 10 / 已作废 20）
      （data-model §5）
- [x] T375 [P] 新增两个 DO 与两个 Mapper：`TradeOrderInvoiceDO` / `TradeInvoiceTitleDO`（`extends BaseDO`）、
      `TradeOrderInvoiceMapper` / `TradeInvoiceTitleMapper`（`extends BaseMapperX`）（data-model §1/§2）
- [x] T376 [P] C 端 `storefront/src/types/invoice.ts`：`InvoiceType` / `InvoiceStatus` 常量（取值与后端枚举**逐字对齐**）、
      `InvoiceTitle` / `OrderInvoice` 类型（contracts/app-api.md §1/§3.2）
- [x] T377 服务端冒烟：写一个最小的 Mapper 测试
      `trade-server/src/test/java/cn/iocoder/yudao/module/trade/dal/mysql/invoice/TradeOrderInvoiceMapperTest.java`
      并跑通 —— 确认 H2 能建出两张新表、MyBatis-Plus 映射没问题。**本模块的第一个测试类**，
      它红的话说明 T373 没做对（而不是业务代码有问题）

---

## Phase 3: US19 —— 下单时说清楚要不要发票、开给谁（P1）🎯 MVP

**Goal**: 结算页能选票种、填抬头，提交后申请与订单**同一事务**落库；不开票时与今天逐字一致。
**Independent Test**: 结算页勾开票 → 选票种 → 填抬头 → 提交 → **该订单带着正确的开票申请**
（用接口或库里确认；订单详情的**界面呈现**归 US20 / US22，本故事只负责"需求进了系统"）；
不勾时该订单**没有任何**开票记录（quickstart §一）。

### 测试（先写、先失败）

- [x] T378 [US19] 服务端 `InvoiceValidatorTest`（`trade-server/src/test/java/.../service/invoice/`）：
      **必填矩阵**（**Q37**）—— 名称+税号**必须通过**、缺任一项**必须被拒**、地址/电话/开户行选填；
      税号 15/18/20 位通过、其它长度与非法字符被拒；trim 后为空视为未填（FR-155 / Q/data-model §4）
- [x] T379 [US19] 服务端 `trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/invoice/TradeOrderInvoiceServiceImplTest.java`：
      下单创建申请（1:1）；**同一订单第二次创建被拒**（FR-160，唯一索引 + 服务层判断）；
      **`amount` 取订单实付金额**而不是请求里的值（FR-161）；
      **实付 0 元时不产生申请**（spec Edge Case）；不传 `invoice` 时**不产生任何记录**（FR-159）
- [x] T380 [P] [US19] C 端 `storefront/src/utils/invoice.spec.ts`：`validateInvoice(form, type)` 的纯函数判定 ——
      普票只查名称、专票查五项、税号格式（FR-155/156）
- [x] T381 [P] [US19] C 端 `storefront/src/api/invoice.spec.ts`：请求路径与参数形态（含 `undefined` 而非 `null`）
- [x] T382 [P] [US19] C 端 `storefront/src/components/CheckoutInvoiceCard.spec.ts`：
      默认「不开票」；切到开票才出现票种与抬头字段；必填不全都**提交按钮前置校验**（FR-153/156）；
      **实付 0 元时整块不渲染**（spec Edge Case）

### 实现

- [x] T383 [US19] 服务端 `service/invoice/InvoiceValidator.java`：**全仓唯一一处**票种校验规则
      （下单、补开两处都调它，MUST NOT 各写一份）（data-model §4）
- [x] T384 [US19] 服务端 `service/invoice/TradeOrderInvoiceService(+Impl)` + `convert/invoice/TradeOrderInvoiceConvert.java`：
      `createInvoice(order, invoiceReq)`（校验 → 金额取订单 → 落库）
- [x] T385 [US19] 服务端 `controller/app/order/vo/AppTradeOrderCreateReqVO.java` 加 `invoice` 子对象；
      `service/order/TradeOrderUpdateServiceImpl.java` 的 `createOrder` 内**加一行**调用（**同一事务**，
      位置在订单与订单项落库之后）。⚠️ **实付 0 元时"忽略"而不是"拒绝"**（订单照下，见 contracts §3.1）
- [x] T386 [US19] 服务端 `controller/app/order/vo/AppTradeOrderDetailRespVO.java` 加 `invoice` 对象
      （无申请时为 `null`，contracts §3.2）
- [x] T387 [P] [US19] C 端 `storefront/src/api/invoice.ts` + `storefront/src/utils/invoice.ts`
- [x] T388 [P] [US19] C 端 `storefront/src/components/InvoiceFieldsForm.vue`：票种单选 + 抬头五个字段 + 按票种的必填提示
      （**US20 的补开弹层复用它**，别写两份）
- [x] T389 [US19] C 端 `storefront/src/components/CheckoutInvoiceCard.vue` + 挂进 `storefront/src/views/CheckoutView.vue`：
      开关 + 复用 `InvoiceFieldsForm`；按结算结果的 `payPrice > 0` 决定整块是否渲染；提交时把 `invoice` 塞进下单请求体
- [x] T390 [US19] **接线断言**（本项目吃过亏的地方）：在 `storefront/src/views/CheckoutView.spec.ts` 里断言
      下单请求桩收到的**请求体**里 `invoice` 与表单**逐字段一致**；不开票时**没有**该字段（R7 / SC-062）
- [ ] T391 [US19] 手工验收 [quickstart.md](./quickstart.md) §一（含第 5 步的交互次数、第 7 步的 0 元订单）

---

## Phase 4: US20 —— 下单时漏选了，订单详情里还能补开（P2）

**Goal**: 已支付且未申请过的订单，客户自助补开。
**Independent Test**: 一笔已支付未申请的订单 → 详情出现「申请开票」→ 提交 → 后台待开票列表里能看到（quickstart §二）。

### 测试（先写、先失败）

- [x] T392 [US20] 服务端 `TradeOrderInvoiceServiceImplTest` 增补**补开的七条校验**（contracts/app-api.md §2.1）：
      订单不存在 / **不属于当前会员**（MUST 报"订单不存在"而不是"无权访问"）/ 未支付 / **实付 0 元** /
      已取消 / 已有申请 / 抬头缺项 —— **逐条一个用例**
- [x] T393 [P] [US20] C 端 `storefront/src/components/InvoiceApplyDialog.spec.ts`：
      必填不全拦住提交；提交成功后回调触发重新拉详情（而不是本地改状态）

### 实现

- [x] T394 [US20] 服务端 `controller/app/invoice/AppTradeOrderInvoiceController.java` + `vo/`（补开接口，
      复用 T383 的 `InvoiceValidator`）
- [x] T395 [US20] C 端 `storefront/src/components/InvoiceApplyDialog.vue`（复用 `InvoiceFieldsForm`）
- [x] T396 [US20] C 端 `storefront/src/views/OrderDetailView.vue`：**已支付且未申请**时出「申请开票」入口，
      提交成功后**重新拉订单详情**（状态一律来自后端，FR-039 的既有口径）
- [ ] T397 [US20] 手工验收 [quickstart.md](./quickstart.md) §二（含"提交后没有修改入口"，Q28）

---

## Phase 5: US21 —— 后台看到开票申请，开好后把文件传上来（P2）

**Goal**: 运营在独立菜单里看到申请、上传发票文件、状态变「已开票」；退款/取消时未开具的申请自动作废。
**Independent Test**: 造一条已支付订单的申请 → 后台能看到 → 上传 PDF → 状态已开票、记录时间与操作人（quickstart §四）。

### 测试（先写、先失败）

- [x] T398 [US21] 服务端 `TradeOrderInvoiceServiceImplTest`（同上文件）增补**开具与作废**：
      未支付不许开具；已作废不许开具；**扩展名白名单**（`pdf/jpg/jpeg/png` 之外被拒且**状态不变**，FR-171）；
      **重新上传覆盖**（Q31）；`issue_time` / `issue_user_id` 落上（FR-170）；
      **`TradeInvoiceOrderHandler`：待开票与已开票都作废**（Q30 + **Q35**，2026-10-15 改判）；
      两条钩子（`afterCancelOrder` / `afterCancelOrderItem`）与**幂等**都要有用例
      📌 **原任务写的是"已开票 → 不动"**（Q30）—— 那条口径已被 Q35 推翻，任务描述同步改过了
- [x] T399 [US21] 服务端**分页查询**测试：`status = 待开票` 时**只返回已支付订单**的申请（FR-169 / SC-059）；
      `status = 待开票` 时**已取消订单的申请不出现**；`orderNo` 精确筛能查到（R30）
- [x] T400 [P] [US21] 管理端 `yudao-ui-admin-vue3/src/utils/invoice.ts`（与 C 端同名，便于对照）
      新增纯函数 + 测试：票种 / 状态的中文映射、查询参数组装
- [x] T401 [P] [US21] 管理端 `views/mall/trade/invoice/InvoiceIssueForm.spec.ts`：
      **没有 `issue` 权限时上传控件不可用**（`checkPermi`）、未选文件不许提交

### 实现

- [x] T402 [US21] 服务端 `service/order/handler/TradeInvoiceOrderHandler.java`：`implements TradeOrderHandler`，
      实现 **`afterCancelOrder`（整单取消）+ `afterCancelOrderItem`（部分退款）** ——
      **取消逻辑一行不改**（R28）。📌 原任务只写了一个钩子：部分退款**不取消订单**，
      少第二个钩子就会"退了一件商品、票还挂在前台"且不报错（Q35）
- [x] T403 [US21] 服务端 `TradeOrderInvoiceService` 增补 `issueInvoice(id, fileUrl)`；`TradeOrderInvoiceMapper`
      加 **join `trade_order` 的自定义分页**（XML，照 `resources/mapper/brokerage/BrokerageUserMapper.xml`），
      **并带出订单当前的 `pay_price` / `refund_price`**（`orderPayPrice` / `orderRefundPrice` ——
      plan 风险 R2 与 quickstart §六.3 要用它判断部分退款怎么开，contracts/admin-api.md §1 规则 3）
- [x] T404 [US21] 服务端 `controller/admin/invoice/TradeOrderInvoiceController.java` + `vo/`：
      分页 + 开具，两处都挂 `@PreAuthorize("@ss.hasPermission('trade:order-invoice:query' | ':issue')")`（Q29 / FR-174）
- [x] T405 [P] [US21] 迁移脚本 `yudao-cloud/sql/mysql/008-order-invoice-menus.sql`：菜单 + 两个权限点，
      **幂等**（`INSERT ... SELECT ... WHERE NOT EXISTS`，目标表包一层派生表 —— 见 004 的脚本）
- [x] T406 [P] [US21] 管理端 `api/mall/trade/invoice/index.ts`（照 `api/mall/trade/afterSale/index.ts`）
- [x] T407 [US21] 管理端 `views/mall/trade/invoice/index.vue`：列表 + 状态筛选 + 订单号查询 + 操作列
      （`v-hasPermi="['trade:order-invoice:issue']"`）；订单号可点跳到订单详情（FR-167/168/173）
- [x] T408 [US21] 管理端 `views/mall/trade/invoice/InvoiceIssueForm.vue`：
      `UploadFile`（`:file-type="['pdf','jpg','jpeg','png']"`、`:limit="1"`）+ 提交后 `emit('success')`
- [ ] T409 [US21] **执行 T405 的脚本**（`yudao-cloud/sql/mysql/008-order-invoice-menus.sql`），
      并用一个**受限角色**账号实走一遍：菜单可见、按钮可见、能开具
      （[quickstart.md](./quickstart.md) §零.3 —— ⚠️ 漏登记权限点的症状是"接口能调、按钮不显示，连超管也一样"）
      📌 **2026-10-15 脚本已执行、三端已发版**：菜单 12740 挂在「订单中心」266 下、按钮 12741 挂在它
      下面（幂等重跑不新增）；后端 jar / storefront / admin 均已上线（详见各提交与脚本头部的执行记录）。
      **剩下的一半（受限角色实走）仍未做** —— 需要一个后台账号登录，只有你能做：
      确认「商城 → 订单中心 → 开票申请」菜单与列表里的「开具发票」按钮**都看得见**
      （⚠️ 漏登记权限点的症状正是"接口能调、按钮不显示，连超管也一样"）。
- [ ] T410 [US21] 手工验收 [quickstart.md](./quickstart.md) §四（含第 6 步"直接调接口被拒"）

---

## Phase 6: US22 —— 客户自己看到并下载发票（P3）

**Goal**: 订单详情四态如实呈现；已开票可下载；订单列表能看出开票状态。
**Independent Test**: 后台已上传 → 客户在订单详情看到信息与下载入口 → 点下载拿到该笔订单的文件（quickstart §五）。

### 测试（先写、先失败）

- [x] T411 [US22] 服务端测试（`TradeOrderInvoiceServiceImplTest` 与
      `trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/order/handler/TradeInvoiceOrderHandlerTest.java`）：
      订单分页的 `invoiceStatus` 正确（含"没申请的行是 `null`"）；
      **批量查询不随订单数增长**（断言 mapper 调用次数，risk R3）。
      （"已开票的订单被取消后状态与文件都不变"那条断言的**落点在 T398**，此处不重复）
- [x] T412 [P] [US22] C 端 `storefront/src/views/OrderDetailView.spec.ts` 增补：
      **四态**（FR-176）逐条断言；**下载入口旁常驻求助文案**（R10 的缓解措施，别被当成冗余删掉）；
      **跨账号不可见**（SC-060）
- [x] T413 [P] [US22] C 端 `storefront/src/views/OrderListView.spec.ts` 增补：`invoiceStatus` 的三种展示与 `null` 时**什么都不显示**

### 实现

- [x] T414 [US22] 服务端 `controller/app/order/vo/AppTradeOrderPageItemRespVO.java` 加 `invoiceStatus`；
      在订单分页服务里做**一次 `IN (orderIds)` 批量查询**（MUST NOT 逐笔查）
- [x] T415 [US22] C 端 `storefront/src/views/OrderListView.vue`：卡片上显示开票状态
- [x] T416 [US22] C 端 `storefront/src/views/OrderDetailView.vue`：**已开票**态 —— 票种 / 抬头 / 金额 + 下载链接
      （`<a :href target="_blank" rel="noopener">`）+ **常驻**"文件打不开？请联系我们"（contracts §4.1）
- [ ] T417 [US22] 手工验收 [quickstart.md](./quickstart.md) §五（**含"把链接改坏看真实观感"那一步** —— R10 的复盘点）

---

## Phase 7: US23 —— 抬头簿：下次不用重新填（P3）

**Goal**: 客户在个人中心维护多条抬头、设默认；结算页可直接选。
**Independent Test**: 新增两条抬头并设默认 → 结算页预选默认那条 → 改/删这条抬头，历史订单**逐字段不变**（quickstart §三）。

### 测试（先写、先失败）

- [x] T418 [US23] 服务端 `trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/invoice/TradeInvoiceTitleServiceImplTest.java`：CRUD；
      **设新默认会清掉旧默认**（唯一性在服务层）；**跨会员取不到别人的抬头**；
      **删除/修改抬头后历史申请的开票信息逐字段不变**（FR-166 / **SC-061**，先写先红）
- [x] T419 [P] [US23] C 端 `storefront/src/views/InvoiceTitleView.spec.ts` + `components/InvoiceTitleDialog.spec.ts`：
      列表 / 空态 / 新增 / 编辑 / 删除 / 设默认；删除有确认
- [x] T420 [P] [US23] C 端 `storefront/src/components/CheckoutInvoiceCard.spec.ts` 增补：
      有默认抬头时**默认预选**、可改选；抬头列表请求失败时**不阻塞填新抬头**（降级）

### 实现

- [x] T421 [US23] 服务端 `service/invoice/TradeInvoiceTitleService(+Impl)` + Mapper 方法（按 user 查、默认唯一）
- [x] T422 [US23] 服务端 `controller/app/invoice/AppInvoiceTitleController.java` + `vo/`
      （list / create / update / delete，一律按 `getLoginUserId()` 过滤，contracts §1）
- [x] T423 [P] [US23] C 端 `storefront/src/views/InvoiceTitleView.vue` + `components/InvoiceTitleDialog.vue`
      （照 `AddressView.vue` + `AddressFormDialog.vue` 的形态）
- [x] T424 [US23] C 端 `storefront/src/components/AccountSidebar.vue` 加「发票抬头」（账户资料组）+
      `storefront/src/router/index.ts` 加 `/account/invoice`（`meta: { requiresAuth: true }`）
- [x] T425 [US23] ⚠️ **把新页面登记进 `storefront/src/styles/design-consistency.spec.ts` 的显式清单** ——
      那份清单不自动收录；漏登记 = 色表 / 断点 / 不自带顶栏页脚三条断言**管不到它**（risk R9）
- [x] T426 [US23] C 端 `storefront/src/components/CheckoutInvoiceCard.vue` 接上抬头列表与默认预选
- [ ] T427 [US23] 手工验收 [quickstart.md](./quickstart.md) §三（**第 3、4 步是 SC-061 的现场判据**）

---

## Phase 8: Polish & Cross-Cutting

- [x] T428 全量回归：`storefront` 全量 vitest、管理端 vitest、trade-server 全部单测、
      `vue-tsc --noEmit`、两端 eslint（**与改动前的告警数逐条对比**）、两端构建
- [ ] T429 [P] 断点走查（quickstart §八）：1100 / 768 / 480 三档看抬头页、结算页发票卡片、订单详情发票区块，**无横向滚动**
- [ ] T430 [P] 退款与取消四场景（quickstart §六，**Q35 改判后**）：待开票被取消 → 作废；
      **已开票被退款/取消 → 也作废、前台不再展示发票内容、后台不能再更新**；
      **部分退款 → 同样作废**（走逐订单项钩子）；待开票列表里一条都不留
      📌 原任务写的是"已开票不动 / 部分退款不作废"（Q30+Q33）—— **两口径都已被 Q35 推翻**
- [ ] T431 [P] 不得回归（quickstart §七）：不带开票走完整下单，落库与改动前逐字段一致；上线前的历史订单详情正常且无发票区块
- [x] T432 提交：按"服务端 / C 端 / 管理端"分几个 commit，信息里写明 FR / Q / R 引用与实测数字
      📌 **2026-10-15 已提交并推送**（`006-order-invoice` → GitHub）：规格 / 建表脚本 / 服务端 /
      C 端 / 管理端 五个 commit + 一个合并 `origin/main` 的 commit + 一个 AboutView 配色回归表内的 commit。

---

## Phase 9: Q38 —— 发票加「个人抬头」（2026-10-15）

> 背景：Q37 之后抬头的必填是「名称 + 税号」两项，而**个人客户开普票没有纳税人识别号** ——
> 这类客户连抬头都存不下来。本相位给发票加一维**抬头类型**（企业 / 个人）。
> 仓库里已有一个**未接线的半成品**（`010-invoice-title-kind.sql` 只加了抬头簿那一列 +
> 一个没人引用的 `InvoiceTitleKindEnum`），本相位把它补完并贯穿四端。

- [x] T433 [P] **SQL** `yudao-cloud/sql/mysql/010-invoice-title-kind.sql`：给**两张表**各加
      `title_kind`（1 企业 / 2 个人，`NOT NULL DEFAULT 1`）—— 抬头簿与**开票申请**都要，
      后者是"快照"的一部分，后台靠它区分个人票；沿用 009 的 `information_schema` 幂等写法。
      ⚠️ **同时补 `trade-server/src/test/resources/sql/create_tables.sql` 的两张表** ——
      漏了它，本模块**所有**新测试类**启动就挂**（与 T373 同一条坑）
- [x] T434 [P] [服务端] `InvoiceTitleKindEnum` 口径收口（个人 = 只要名称、税号**选填不是禁止**）；
      `ErrorCodeConstants` 加两条（`INVOICE_TITLE_KIND_INVALID` / `INVOICE_TITLE_KIND_PERSONAL_SPECIAL`）；
      `InvoiceValidator`：类型合法性 → **「个人 + 专票」拒** → **企业**才 `requireText(税号)`；
      `InvoiceValidatorTest` 覆盖六条新断言（含"个人填了税号仍走格式校验"）
- [x] T435 [服务端] 字段贯通：`AppInvoiceApplyBaseVO`（**继承它的三处一次到位**：下单子对象 / 补开 / 抬头簿）、
      `TradeInvoiceTitleDO` 与 `TradeOrderInvoiceDO`、三个 RespVO、`InvoiceConvert` 派生 `titleKindName`；
      `TradeInvoiceTitleServiceImplTest` / `TradeOrderInvoiceServiceImplTest` 各补个人抬头用例
      （**含"抬头类型确实落库"**，别只断言请求体）
- [x] T436 [P] [C端] `types/invoice.ts`（`InvoiceTitleKind` + `titleKind` 进 `InvoiceTitleFields`）、
      `utils/invoice.ts`（选项 + `invoiceFieldError` 分支）、`InvoiceFieldsForm`（抬头类型单选 +
      **两个选择器互为约束**）、以及**所有显式列举字段的拷贝点**（`fillFrom` / `emptyForm` / `payload` /
      `onSetDefault`）—— ⚠️ 漏掉 `InvoiceTitleView.onSetDefault` 会让"设为默认"直接报错；
      `CheckoutView.spec.ts` 的**接线断言**要跟着带上 `titleKind`
- [x] T437 [P] [管理端] 列表加「抬头类型」列（`titleKindName`）、开具弹窗只读信息加一行、
      `api/mall/trade/invoice/index.ts` 的 VO 补字段、`InvoiceIssueForm.spec.ts` 补断言
- [x] T438 文档：spec 的 **Q38** 决策段 + **FR-155 的 📌 取代说明**（顺带点掉 Q37 遗留的陈旧矩阵）、
      data-model §1/§2/§4/§5、contracts 两端、quickstart（§零 迁移、§一/§二/§三/§四 的个人抬头各条）、plan
- [ ] T439 手工验收 quickstart 新增各条（§一.4 / 4b / 4c、§三.6~8、§四.8）

---

## 需求 → 任务 对照

| 需求 | 任务 |
|---|---|
| FR-153 结算页发票区块（默认不开票） | T382, T389 |
| FR-154 两种电子票 | T374, T383, T388 |
| FR-155 字段随**抬头类型**（Q37/Q38：企业=名称+税号，个人=只要名称） | T378, T380, T383, T388, **T433~T437** |
| FR-156 必填不全阻止提交 | T378, T382, T393 |
| FR-157 当场填 or 选已存 | T387, T426 |
| FR-158 下单快照 | T379, T384 |
| FR-159 未选开票不产生记录 | T379 |
| FR-160 一单一条 | T379, T392 |
| FR-161 金额取实付 | T379, T384 |
| FR-162 不影响结算支付 | T390, T431 |
| FR-163 补开入口与校验 | T392, T394, T396 |
| FR-164 抬头簿多条/默认 | T421, T422, T423 |
| FR-165 抬头只对本人可见 | T418, T422 |
| FR-166 删改不影响历史 | T418, T427 |
| FR-167 独立菜单 | T405, T407 |
| FR-168 列表字段与状态筛选 | T399, T407 |
| FR-169 待开票只含已支付 | T399, T403 |
| FR-170 上传即已开票 + 留痕 | T398, T403, T408 |
| FR-171 格式白名单 | T398, T404 |
| FR-172 重传覆盖 | T398, T403 |
| FR-173 申请↔订单对应 | T407 |
| FR-174 菜单权限点 + 接口校验 | T404, T405, T409 |
| FR-175/176 订单详情四态 | T386, T412, T416 |
| FR-177 下载只对本人 | T412, T416 |
| FR-178 列表显示开票状态 | T413, T414, T415 |
| FR-179 历史订单视为未申请 | T372（新型子表天然成立）, T431 |
| FR-180 人工开票、不接平台 | T383~T408 全程无外部调用 |
| FR-181 开票失败不挡下单 | T385（同事务，口径见 plan 风险 R1） |
| FR-182 提交后不可改 | T392（**没有** update 接口即证据）, T396 |
| FR-183 只做下载 | T416（无邮件推送任务即证据） |
| FR-184 取消/退款 → 作废 | T398, T402, T430 |
| SC-056~065 | 见 [quickstart.md](./quickstart.md) §十 的映射（每条都有对应的手工步骤） |
| spec Edge Case：实付 0 元 | T379, T382, T392 |
| spec Edge Case：文件丢失 | T412, T416, T417（**只兑现一半**，见 risk R10） |

---

## Dependencies

### 阶段依赖

```text
Phase 1 (Setup) ──► Phase 2 (Foundational) ──┬──► Phase 3 (US19, P1) 🎯 MVP
                                             │        │
                                             │        ├──► Phase 4 (US20, P2)
                                             │        ├──► Phase 6 (US22, P3)
                                             │        └──► Phase 7 (US23, P3) ──► 回填 Phase 3 的结算页（T426）
                                             └──► Phase 5 (US21, P2) ──► Phase 6 的下载验证需要它（T417）
```

- **Phase 2 是所有故事的前置**：两张表与枚举不在，US19/US21/US23 都动不了。
- **US19 是其余故事的前置**：没有申请，后台没东西可开（US21）、客户没东西可下（US22）；
  US20/US22/US23 都要读订单详情上的 `invoice`（T386）。
- **US21 → US22**：US22 的手工验收要有一份已上传的发票文件才能走通。
- **US23 → US19 的回填**（T426）：US19 只做"当场填"，抬头簿是 US23 才接上去的 —— 这是刻意的，
  US19 单独交付也是完整的（spec US19 的 Independent Test 不依赖抬头簿）。

### 故事内部

**每个故事：测试任务全部排在该故事的实现任务之前**（宪法原则 I）。测试写完后 MUST 先跑一遍确认**红**、
且**红的原因正确**（不是导入错、不是拼写错）。本特性最容易"红错"的两处：
T377（红 = T373 漏补建表，不是业务问题）、T418（SC-061 的用例在实现前必须真的失败）。

### 并行机会

- **Phase 2 整段可并行**：T372~T376 五个任务是五个不同文件（其中 T373 依赖 T372 的 DDL 内容，**排在同一批但 T373 后做**）。
- **各故事的 C 端测试任务互相独立**，可并行（T380~T382、T393、T412~T413、T419~T420）。
- **服务端测试按故事分开写**（T378/T379、T392、T398/T399、T411、T418）—— 同一批里不要两个任务改同一个测试类。

## Parallel Execution Examples

**Phase 2 一次并行**：T372（DDL 脚本）‖ T374（枚举）‖ T375（DO/Mapper）‖ T376（C 端类型）→ 然后 T373（补测试建表）→ T377（冒烟）

**US19 的 C 端测试并行**：T380（utils 纯函数）‖ T381（api 形态）‖ T382（组件行为）

**US21 的管理端测试并行**：T400（纯函数）‖ T401（弹窗权限）

## Implementation Strategy

### MVP First（T370 → T391）

Phase 1 + 2 + **US19** 就是一个完整可交付的增量：客户能在下单时说清楚开票需求、需求进系统。
**它单独上线已经有正收益**（spec US19 的 Why）—— 开票需求从此不再依赖客户记得给张经理打电话。
**但它还不闭环**：申请攒在库里没人处理，所以 UAT 之后应当继续 US20/US21。

### 增量交付

| 增量 | 内容 | 交付后能得到什么 |
|---|---|---|
| ① T370~T391 | US19 | 下单申请进系统 |
| ② T392~T397 | US20 | 漏选也能自助补开 |
| ③ T398~T410 | US21 | 运营有列表、能上传、状态流转、退款/取消自动作废 |
| ④ T411~T417 | US22 | 客户自己下载（**闭环**） |
| ⑤ T418~T427 | US23 | 抬头簿，少填几次 |
| ⑥ T433~T439 | Q38 | **个人抬头**能存能开（在此之前个人客户无路可走） |

### 本清单里要特别当心的六条

1. **T373（补测试建表）** —— 漏了它，本模块**所有**新测试类启动就挂，很容易被误读成"业务代码有问题"。
2. **T385（下单同一事务）** —— 只加一行，但位置必须在订单落库之后；**实付 0 元时是"忽略"不是"拒绝"**。
3. **T390（接线断言）** —— 本项目吃过"单测只断言 UI、不断言请求体"的亏（商城搜索那次）；
   开票是"表单填了但没提交出去"最容易发生的形状。
4. **T402（作废走 handler）** —— 一旦有人图省事去改 `cancelOrder*` 三个方法，将来上游加第四个取消入口就会漏，
   而漏的后果是**票开给了一笔已取消的交易且不报错**。
5. **T409（权限点）** —— 漏登记 = 超管也看不到按钮，而接口是通的；这条**只能手工验**。
6. **T425（登记一致性清单）** —— 不登记，新页面就逃过那三条结构断言；这是给自己的新页面开后门。
