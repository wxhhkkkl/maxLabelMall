# Tasks: 印刷定制下单（014）

**Input**: Design documents from `specs/014-print-customization/`
**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 宪法原则 I（测试先行，不可协商）——每个用户故事的测试任务 MUST 先写、先失败，再写实现。

**Organization**: 按用户故事分阶段。US48 / US49 为 P1，US50 为 P2。

## 路径约定

- 后端：`yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/{main,test}/java/cn/iocoder/yudao/module/trade/…`
- SQL：`yudao-cloud/sql/mysql/`
- 门面站：`storefront/src/…`
- 管理端：`yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/…`

---

## Phase 1: Setup（共享基础设施）

- [x] T001 新建迁移脚本 `yudao-cloud/sql/mysql/027-print-customization.sql`：4 张表（材质 / 面积区间 / 计价参数 / 定制订单）、`trade_order ADD COLUMN customization bit(1) NOT NULL DEFAULT 0` 与索引 `idx_tenant_customization (tenant_id, customization, status)`、预置隐藏 SPU/SKU（`990001/990002`，`INSERT ... WHERE NOT EXISTS` 幂等）、菜单与权限点（`trade:customization-material:*`、`trade:customization-config:*`）
- [x] T002 [P] 同步 H2 测试库结构：在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/resources/sql/create_tables.sql` 增加 4 张表与 `trade_order.customization` 列，并在 `src/test/resources/sql/clean.sql` 增加清表语句
- [x] T003 [P] 在 `…/trade/enums/customization/CustomizationConstants.java` 定义隐藏 SPU/SKU 常量、文件白名单（`pdf/ai/cdr/jpg/jpeg/png`）、文件数量上下限（1~5）、Redis 键前缀与 60 秒 TTL
- [x] T004 [P] 在 `…/trade/enums/ErrorCodeConstants.java` 追加 6 个错误码（材质不可用 / 规格越界 / 面积无区间 / 区间配置非法 / 文件非法 / 价格已变）

---

## Phase 2: Foundational（阻塞所有用户故事）

**⚠️ CRITICAL**：本阶段完成前，任何用户故事都不能开始。

- [x] T005 [P] 新建 `…/trade/dal/dataobject/customization/CustomizationMaterialDO.java`（字段与 data-model §1 逐字一致：`base_price int` 分/㎡、系数 `decimal(10,4)`、数量与宽高上下限、`status`、`sort`）
- [x] T006 [P] 新建 `…/trade/dal/dataobject/customization/CustomizationAreaRuleDO.java`（`min_area`/`max_area` `decimal(12,4)`，`max_area = 0` 表示无上限；`print_coefficient`、`die_cut_coefficient`）
- [x] T007 [P] 新建 `…/trade/dal/dataobject/customization/CustomizationConfigDO.java`（四个初始价 `int` 分、`urgent_coefficient decimal(10,4)`）
- [x] T008 [P] 新建 `…/trade/dal/dataobject/customization/CustomizationOrderDO.java`（`order_id`、规格字段、`price_breakdown`/`param_snapshot`/`files` 为 JSON）
- [x] T009 [P] 新建 4 个 Mapper：`…/trade/dal/mysql/customization/{CustomizationMaterialMapper,CustomizationAreaRuleMapper,CustomizationConfigMapper,CustomizationOrderMapper}.java`（订单表按 `orderId` 查询）
- [x] T010 [P] `…/trade/dal/dataobject/order/TradeOrderDO.java` 增加 `private Boolean customization;`
- [x] T011 [P] `…/trade/service/price/bo/TradePriceCalculateReqBO.java` 的 `Item` 增加**服务端专用** `private Integer customPrice;`（不参与 HTTP 反序列化）
- [x] T012 新建 `…/trade/service/customization/CustomizationMaterialService.java` + `Impl`：只读接口 —— 启用材质列表、按 id 取材质与面积区间
- [x] T013 新建 `…/trade/service/customization/CustomizationConfigService.java` + `Impl`：取当前租户参数（无行时返回默认值并允许首次保存）
- [x] T014 新建 `…/trade/service/customization/HiddenCustomizationSkuProvider.java`：按 T003 常量取隐藏 SKU/SPU 的 DTO（供计价与订单项使用）

**Checkpoint**：表、DO、Mapper、只读服务就绪 → 用户故事可并行开始。

---

## Phase 3: User Story 48 - 客户在线定制并支付（P1）

**Goal**：客户在 `/customization` 选材质、填尺寸与数量、选工艺、传文件，看到服务端报价并完成支付。

**Independent Test**：用一组预置材质，走完「选材质 → 报价 → 上传 → 提交 → 支付」，订单金额与页面明细一致。

### Tests for US48（先写、先失败）

- [x] T015 [P] [US48] 计价单测 `…/trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/customization/CustomizationPriceServiceImplTest.java`：区间左闭右开（Q94）、整单总面积匹配（Q103）、双打印系数相乘（Q104）、四个最低收费、加急费、分为单位四舍五入、总面积恰好落在边界
- [x] T016 [P] [US48] 规格校验单测 `…/service/customization/CustomizationSpecValidatorTest.java`：数量 / 宽高按材质上下限、小于最小起始面积
- [x] T017 [P] [US48] 建单单测 `…/service/customization/CustomizationOrderServiceImplTest.java`：数量与金额落法（count=N、单价向上取整、差额进商品优惠）、参数快照、Q101 价格已变不建单、60 秒幂等返回同一单、改价后历史单金额不变（SC-142）、定制单恰好 1 个订单项且无 cartId（FR-379）
- [x] T018 [P] [US48] 门面站接口单测 `storefront/src/api/customization.spec.ts`：三个端点的路径、请求体、错误码透传
- [x] T019 [P] [US48] 门面站页面单测 `storefront/src/views/CustomizationView.spec.ts`：必填与越界校验、报价刷新、文件数量/格式拦截、改价确认弹层、登录后保留已填内容、开票选项、定制单不显示自助退款入口

### Implementation for US48

- [x] T020 [US48] 新建 `…/trade/service/customization/CustomizationPriceService.java` + `Impl`：按 data-model 与 research R90/R92 计算 breakdown、freight（传地址时复用既有配送模板）、payable，并生成 `param_snapshot`
- [x] T021 [US48] 新建 `…/trade/service/customization/CustomizationSpecValidator.java`：规格越界、面积无区间、文件白名单与数量的服务端校验（R97）
- [x] T022 [US48] 新建 `…/trade/service/customization/CustomizationOrderService.java` + `Impl`：60 秒幂等（R94）、`expectedAmount` 比对（R95）、建单后在同一事务写 `trade_customization_order`（R92）
- [x] T023 [US48] 修改 `…/trade/service/price/calculator/TradePriceCalculatorHelper.java`：`customPrice != null` 时用它代替 SKU 价格，并保持 `payPrice = price × count − discount`
- [x] T024 [US48] 修改 `…/trade/controller/app/order/vo/AppTradeOrderCreateReqVO.java`：新增 `private AppCustomizationSpecReqVO customization;`
- [x] T025 [US48] 修改 `…/trade/service/order/TradeOrderUpdateServiceImpl.java`：`calculatePrice` 检测到定制规格时先调 `CustomizationPriceService`；`buildTradeOrder` 写入 `customization = true`
- [x] T026 [US48] 新建 `…/trade/controller/app/customization/AppCustomizationController.java` + `vo/`：`GET /material/list`、`POST /quote`、`POST /order/create`（不新增上传接口）
- [x] T027 [US48] 修改 `…/trade/controller/app/order/vo/AppTradeOrderDetailRespVO.java` 与详情查询：定制单返回 `customizationDetail`（规格、价格明细、文件）；普通订单不返回该字段
- [x] T028 [P] [US48] 新建 `storefront/src/api/customization.ts`：材质列表、报价、建单（含 `CUSTOMIZATION_PRICE_CHANGED` 分支）
- [x] T029 [US48] 新建 `storefront/src/views/CustomizationView.vue`：材质选择、宽高与数量输入、印刷 / 模切 / 加急、文件上传（复用 `api/upload.ts`，未登录时在上传/提交处引导登录并保留已填内容）、报价面板、收货地址与备注、开票选项（复用既有抬头）、提交并跳转支付
- [x] T030 [US48] 在 `storefront/src/router/index.ts` 增加 `/customization` 路由，并在主导航加入口
- [x] T031 [US48] 修改 `storefront/src/views/OrderDetailView.vue`：定制单展示材质、尺寸、数量、工艺、加急、备注、价格明细与文件下载，并**隐藏自助退款/售后入口**（FR-373 / Q96=B）
- [x] T032 [US48] 在 `storefront/src/views/CustomizationView.vue` 实现改价确认交互（Q101）：提交返回 `CUSTOMIZATION_PRICE_CHANGED` 时展示新旧金额与差额，确认后以新 `expectedAmount` 重新提交
- [x] T033 [US48] 定制单支付复用 `storefront/src/api/pay.ts`：建单返回的 `payOrderId` 直接进入既有支付流程

**Checkpoint**：US48 可独立验证（报价正确、能支付、订单详情完整）。

---

## Phase 4: User Story 49 - 运营在后台查看定制订单要求（P1）

**Goal**：运营在订单列表筛出定制单，打开详情即可看到生产所需的全部要求、文件与参数快照。

**Independent Test**：用一笔测试定制单，在后台筛选并打开详情，核对全部字段；普通订单详情不变。

### Tests for US49（先写、先失败）

- [x] T034 [P] [US49] 列表筛选单测 `…/trade-server/src/test/java/cn/iocoder/yudao/module/trade/dal/mysql/order/TradeOrderMapperCustomizationTest.java`：`customization=true` 只返回定制单；统计复用同一条件
- [x] T035 [P] [US49] 详情单测 `…/trade-server/src/test/java/cn/iocoder/yudao/module/trade/controller/admin/order/TradeOrderCustomizationDetailTest.java`：定制单带 `customizationDetail` 与快照，普通单不返回
- [x] T036 [P] [US49] 管理端接口单测 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/trade/order/index.spec.ts`：新增 `customization` 参数透传

### Implementation for US49

- [x] T037 [US49] 修改 `…/trade/controller/admin/order/vo/TradeOrderPageReqVO.java` 与 `…/trade/dal/mysql/order/TradeOrderMapper.java`：新增 `customization` 条件（列表与统计共用，R93）
- [x] T038 [US49] 修改 `…/trade/controller/admin/order/vo/TradeOrderDetailRespVO.java` 与订单详情查询：填充 `customizationDetail`（含 `paramSnapshot` 与文件 URL）
- [x] T039 [US49] 管理端 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/trade/order/`：列表增加「定制」筛选（tag 与查询区），详情抽屉/页增加定制要求区块（文件可下载）
- [x] T040 [US49] 管理端 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/trade/order/index.ts`：查询参数与详情类型补充 `customization`

**Checkpoint**：US48 + US49 均独立可用；后台能完成生产准备。

---

## Phase 5: User Story 50 - 运营维护材质与计价参数（P2）

**Goal**：运营在后台维护可售材质、面积区间、四个初始价与加急系数；保存即生效，历史订单不变。

**Independent Test**：新增材质与区间、改一个初始价，前台报价随之变化；改动前订单金额不变。

### Tests for US50（先写、先失败）

- [x] T041 [P] [US50] 材质保存校验单测 `…/trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/customization/CustomizationMaterialServiceImplTest.java`：区间连续覆盖、重叠 / 倒挂 / 断档拒绝并指出位置、上下限校验、停用不影响历史单
- [x] T042 [P] [US50] 参数保存单测 `…/service/customization/CustomizationConfigServiceImplTest.java`：初始价 `>= 0`、`urgentCoefficient >= 1`、保存后报价生效
- [x] T043 [P] [US50] 管理端接口单测 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/customization/index.spec.ts`

### Implementation for US50

- [x] T044 [US50] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/customization/CustomizationMaterialServiceImpl.java` 实现新增 / 编辑 / 停用 / 软删除，区间随材质同事务保存并校验连续性（Q100）
- [x] T045 [US50] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/customization/CustomizationConfigServiceImpl.java` 实现保存并写操作日志（操作人、时间、变更前后值，FR-380）
- [x] T046 [US50] 新建 `…/trade/controller/admin/customization/AdminCustomizationMaterialController.java` + `vo/`：page / get / create / update / delete / update-status
- [x] T047 [US50] 新建 `…/trade/controller/admin/customization/AdminCustomizationConfigController.java` + `vo/`：get / save
- [x] T048 [P] [US50] 新建 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/customization/index.ts`
- [x] T049 [US50] 新建 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/customization/material/index.vue`：材质列表 + 表单（区间子表）
- [x] T050 [US50] 新建 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/customization/config/index.vue`：四个初始价 + 加急系数

**Checkpoint**：三个故事全部独立可用。

---

## Phase 6: Polish & 跨故事收尾

- [x] T051 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/` 跑 `mvn -pl yudao-module-mall/yudao-module-trade-server -am test`，确认普通订单、对公转账、订阅订单用例全绿
- [x] T052 [P] 在 `storefront/` 与 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/` 跑 `vitest`，确认无回归
- [ ] T053 按 `specs/014-print-customization/quickstart.md` §1~§6 手工走查并记录；§7（SC-144）按计划显式不验证
      —— 2026-11-01：§1 计价表已用线上只读 quote 接口逐条核对通过（1.1~1.6b 全部符合）；**§2~§6 待人工走查**（会创建真实订单 / 改配置）
- [x] T054 用 `specs/014-print-customization/contracts/app-api.md` 的 quote 接口做性能实测（`yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/controller/app/customization/AppCustomizationController.java`），目标 p95 < 200 ms
- [x] T055 在 `storefront/src/views/CustomizationView.vue` 与 `…/controller/app/customization/` 检查四类错误文案与空状态：材质为空、区间未配置、文件不合规、价格已变
- [x] T056 更新 `CLAUDE.md` 的当前特性段落，并把 `027` 加入上线顺序说明

---

## Dependencies & Execution Order

### Phase 依赖

- **Phase 1**：无依赖，立即开始
- **Phase 2**：依赖 Phase 1，**阻塞所有用户故事**
- **Phase 3 / 4 / 5**：均依赖 Phase 2；可并行（US48 与 US49 都改订单详情，建议先把 US48 的 detail VO 落地再开 US49 的详情任务）
- **Phase 6**：依赖三个故事完成

### 用户故事依赖

- **US48（P1）**：Phase 2 完成后可独立开始；是 US49 的数据来源
- **US49（P1）**：Phase 2 完成后可开始；筛选只依赖 `trade_order.customization`，不依赖 US48 的建单逻辑
- **US50（P2）**：Phase 2 完成后可开始；与 US48 只通过「材质 / 参数」读取耦合

### 同一故事内

- 测试 MUST 先写并确认失败（原则 I）
- DO / Mapper → Service → Controller → 前端页面
- 后端接口先于前端联调

### 并行机会

- T005~T011（DO / Mapper / 字段）可并行
- T015~T019（US48 的 5 个测试文件）可并行
- T028（storefront API）与后端 T020~T027 可并行
- T041~T043（US50 测试）可并行

---

## Parallel Example: US48

```bash
# 测试先行，五个文件互不依赖：
Task: "T015 计价单测 CustomizationPriceServiceImplTest.java"
Task: "T016 规格校验单测 CustomizationSpecValidatorTest.java"
Task: "T017 建单单测 CustomizationOrderServiceImplTest.java"
Task: "T018 storefront/src/api/customization.spec.ts"
Task: "T019 storefront/src/views/CustomizationView.spec.ts"

# 后端实现可以按「计价 → 建单 → 接口 → 详情」串行推进
```

---

## Implementation Strategy

### MVP 定义

- **最小可演示**：US48（客户能定制、看到正确报价、能支付）
- **最小可上线**：US48 + US49（后台能看到并生产）
- US50 是 P2：先按截图参数用迁移脚本初始化，运营改价可等这一阶段完成

### 增量交付

1. Phase 1 + Phase 2 → 地基就绪
2. US48 → 独立验证 → 演示
3. US49 → 独立验证 → 可上线
4. US50 → 独立验证 → 运营自助维护
5. Phase 6 → 回归与 quickstart

### 注意事项

- 不改 `trade_order_item` 结构；金额落法严格按 R90
- 隐藏 SPU/SKU 由迁移预置，**任何回滚都不要删**
- 定制单强制不参与优惠券与积分（R96）
- 改价冲突 MUST NOT 建单（Q101）

---

## Phase 7: Convergence

> 2026-11-01 `/speckit-converge` 对当前代码的核查结果。按严重度排序，前 3 条为测试缺口（宪法原则 I）。

- [x] T057 **CRITICAL** 为 R90 订单项金额落法补单测：`TradePriceCalculatorHelper.buildCalculateResp` 在 `customTotalPrice != null` 时的「单价向上取整、`discountPrice` 吸收取整差额、`payPrice` 等于报价」，以及定制建单后 `customization = true`、订单项恰好 1 条且 `cartId` 为空 per `Constitution I` / `T017`（missing）
- [x] T058 为 `storefront/src/views/CustomizationView.spec.ts` 补齐 T019 声称但缺失的用例：尺寸/数量越界校验、改输入后报价刷新、文件数量与扩展名拦截、登录后已填内容保留、开票选项提交 per `T019`（partial）
- [x] T059 为 `storefront/src/views/OrderDetailView.spec.ts` 补一条「定制单没有自助退款入口」的回归用例（与既有的企业单 / 订阅单两条并列） per `T019` / `Q96` / `FR-373`（partial）
- [x] T060 改价确认弹层展示**新旧金额与差额**（当前只显示新价；提交返回 `changed` 时先留存原 `payable` 再覆盖） per `Q101` / `T032`（partial）
- [x] T061 管理端订单列表加「定制」标记：`TradeOrderPageItemRespVO` 与转换补 `customization` 字段，列表行展示 tag（筛选已可用，缺的是「一眼分清」） per `FR-375` / `US2` / `T039`（partial）
- [x] T062 材质「停用」与「删除」补 `@LogRecord` 操作记录（操作人、时间、变更前后状态），与新增/编辑一致 per `FR-380` / `US50`（partial）
