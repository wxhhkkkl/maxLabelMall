---
description: "Task list for 019-product-browse-level"
---

# Tasks: 商品浏览等级（与会员等级关联）

**Input**: Design documents from `specs/019-product-browse-level/`
**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 本项目宪法原则 I（测试先行，不可协商）→ 测试任务必须包含、必须先于同故事的实现任务，并先确认失败。

**Organization**: 任务按用户故事分组，每个故事可独立实现、独立验证。

**Path Conventions**: 仓库根为 `d:\workspace\maxLabelMall`。Java 代码在 `yudao-cloud/`；管理端在
`yudao-cloud/yudao-ui/yudao-ui-admin-vue3/`；SQL 在 `yudao-cloud/sql/mysql/`；C 端 storefront 无需改代码。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US63 / US64 / US65 / US66）
- 每个任务都带精确文件路径

---

## Phase 1: Setup（共享基础设施）

- [X] T001 创建 `yudao-cloud/sql/mysql/041-product-browse-level.sql`：给 `product_spu` 增加 `browse_level_type`（`tinyint NOT NULL DEFAULT 0`，0 公开 / 1 全部登录会员 / 2 指定等级会员）与 `browse_level_ids`（`varchar(512) NULL DEFAULT ''`，逗号分隔的 `member_level.id`，仅 type=2 有意义），位置在 `delivery_template_id` 之后
- [X] T002 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/resources/sql/create_tables.sql` 的 `product_spu` 表增加同两列（`browse_level_type` NOT NULL DEFAULT 0、`browse_level_ids` NULL DEFAULT ''），与生产 DDL 保持一致
- [X] T003 [P] 新建枚举 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/enums/spu/ProductSpuBrowseLevelTypeEnum.java`：`PUBLIC(0)` / `LOGIN(1)` / `LEVEL(2)`，提供 `isValid(Integer)` 与按 code 取值的方法
- [X] T004 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-api/src/main/java/cn/iocoder/yudao/module/product/enums/ErrorCodeConstants.java` 新增：`SPU_BROWSE_LEVEL_IDS_EMPTY = 1_008_005_005`（指定等级会员时，至少选择一个会员等级）、`SPU_BROWSE_LEVEL_NOT_EXISTS = 1_008_005_006`（会员等级({})不存在）、`SPU_BROWSE_LEVEL_TYPE_INVALID = 1_008_005_007`（浏览等级类型不合法）
- [X] T005 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java` 新增 `PRODUCT_SPU_NOT_VISIBLE = new ErrorCode(1_011_010_005, "商品不存在或不可购买")`

---

## Phase 2: Foundational（阻塞所有用户故事）

**Purpose**: 数据映射、唯一判定 Service、跨模块 RPC、SQL 过滤——所有故事都依赖。

**⚠️ CRITICAL**: 本阶段完成前，任何用户故事都不能开始。

- [X] T006 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/dataobject/spu/ProductSpuDO.java` 增加 `Integer browseLevelType` 与 `@TableField(typeHandler = LongListTypeHandler.class) List<Long> browseLevelIds`，补充字段注释
- [X] T007 [P] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/mysql/spu/ProductSpuMapper.java` 的 `selectByIdIncludeDeleted` `@Results` 中补 `@Result(column = "browse_level_ids", property = "browseLevelIds", typeHandler = LongListTypeHandler.class)`
- [X] T008 [P] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/spu/ProductSpuBrowseLevelServiceTest.java`：覆盖判定矩阵（公开对访客/登录均可；全部登录会员对访客不可见；指定等级对无等级/未命中不可见、命中可见；企业管理员不豁免），并确认测试因方法未实现而失败
- [X] T009 实现 `ProductSpuBrowseLevelService` / `ProductSpuBrowseLevelServiceImpl`（`yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/spu/`）：`getViewerLevelId(Long userId)`（未登录 null；`MemberUserApi#getUser` 的 `levelId` 为 null/0 时返回 null）、`isVisible(spu, userId)`、`isVisible(spu, userId, levelId)`、`validateVisible(spu)`（不可见抛 `SPU_NOT_EXISTS`）、`filterVisibleSpuIds(userId, spuIds)`；不得注入 `ProductSpuService`（避免循环依赖）
- [X] T010 [P] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/api/spu/ProductSpuApiBrowseLevelTest.java`：`getVisibleSpuIds` 只返回可见 SPU；`validateSpuVisible` 对不存在/不可见返回 false
- [X] T011 在 `yudao-cloud/yudao-module-mall/yudao-module-product-api/src/main/java/cn/iocoder/yudao/module/product/api/spu/ProductSpuApi.java` 新增 `CommonResult<List<Long>> getVisibleSpuIds(@RequestParam("userId") Long userId, @RequestParam("spuIds") Collection<Long> spuIds)` 与 `CommonResult<Boolean> validateSpuVisible(@RequestParam("userId") Long userId, @RequestParam("spuId") Long spuId)`，并在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/api/spu/ProductSpuApiImpl.java` 实现（只做浏览等级判定）
- [X] T012 [P] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/dal/mysql/spu/ProductSpuBrowseLevelVisibilityTest.java`：访客分页只含公开；登录无等级含公开+全部登录会员；指定等级命中才出现；`total` 与 `list.size()` 一致；另加用例断言 `selectByIdIncludeDeleted` 能读回 `browseLevelIds`（覆盖 T007）
- [X] T013 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/mysql/spu/ProductSpuMapper.java` 新增带 `boolean loggedIn, Long levelId` 的 `selectPage` 重载：追加 `(browse_level_type = 0 OR (browse_level_type = 1 AND :loggedIn) OR (browse_level_type = 2 AND :levelId IS NOT NULL AND find_in_set(:levelId, browse_level_ids)))`，用 `MyBatisUtils.findInSet("browse_level_ids")`；旧签名保留并委托为访客
- [X] T014 [P] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/spu/ProductSpuServiceImplGetSpuPageBrowseLevelTest.java`：mock `ProductSpuMapper` 与 `MemberUserApi`，断言访客传 `(false, null)`、登录无等级传 `(true, null)`、登录有等级传 `(true, levelId)`（覆盖 T015 的身份解析，避免只测 Mapper 漏掉接线）
- [X] T015 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/spu/ProductSpuServiceImpl.java` 的 `getSpuPage(AppProductSpuPageReqVO)` 中解析当前登录用户与等级，调用 T013 的新重载

**Checkpoint**: 数据库列、DO 映射、判定 Service、RPC、SQL 过滤、分页身份接线就绪。

---

## Phase 3: User Story 63 - 运营为商品设置浏览等级 (Priority: P1) 🎯

**Goal**: 管理端商品编辑「其它设置」可配置三档，并完整保存与回显。

**Independent Test**: 默认公开；指定等级空选被拒；勾选两个等级保存后完整回显；改回公开后旧配置失效；保存浏览等级不改变状态/价格。

### Tests for User Story 63（先写并确认失败）

- [X] T016 [US63] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/admin/spu/vo/ProductSpuSaveReqVO.java` 与 `ProductSpuRespVO.java` 增加 `browseLevelType` / `browseLevelIds` 字段（**仅脚手架、无行为**；规则由 T017 先测）
- [X] T017 [P] [US63] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/spu/ProductSpuServiceImplBrowseLevelTest.java`：缺省 type 落 0；type=2 空集合拒绝（`1_008_005_005`）；不存在的等级拒绝（`1_008_005_006`）；非法 type 拒绝（`1_008_005_007`）；type!=2 清空 ids；重复 id 去重；**保存浏览等级后 status / price / stock 不变（FR-536）**
- [X] T018 [P] [US63] 先写失败测试 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/product/spu/form/OtherForm.spec.ts`：mock `@/api/member/level` 的 `getSimpleLevelList`；断言默认「公开」、三档文案、选「指定等级会员」出现多选、空选保存报错、回填后状态正确
- [X] T019 [US63] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/spu/ProductSpuServiceImpl.java` 的 `createSpu` / `updateSpu` 中实现保存校验：type 缺省 0、非法 type 拒绝、type=2 时 ids 非空且每个等级存在（`MemberLevelApi#getMemberLevel`）、type!=2 时把 `browseLevelIds` 置为**空列表**（不能 null，`updateById` 会忽略 null）
- [X] T020 [P] [US63] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/mall/product/spu.ts` 的 `Spu` 接口增加 `browseLevelType?: number` 与 `browseLevelIds?: number[]`
- [X] T021 [US63] 在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/mall/product/spu/form/OtherForm.vue` 的「企业专享」下方增加 `el-radio-group`（公开 / 全部登录会员 / 指定等级会员）与 `el-select multiple`（仅 type=2 显示，选项来自 `getSimpleLevelList()`）；增加「type=2 且 ids 为空」校验；回填后把 type 归一为 0、ids 归一为 `[]`
- [X] T022 [US63] 运行 T017 / T018 并修复至全绿；确认保存 → 回显链路

**Checkpoint**: US63 独立可演示。

---

## Phase 4: User Story 64 - 公开与全部登录会员的可见性生效 (Priority: P1)

**Goal**: 访客只看公开；登录会员看公开 + 全部登录会员；列表、搜索、详情一致。

**Independent Test**: 三个商品分别以访客、无等级会员访问首页、搜索、详情，结果符合矩阵；企业专享与浏览等级同时命中才可见。

### Tests for User Story 64（先写并确认失败）

- [X] T023 [P] [US64] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/controller/app/spu/AppProductSpuBrowseLevelTest.java`：访客 `/page` 不含「全部登录会员」；登录会员含；`/list-by-ids` 过滤；`/get-detail` 对不可见返回 `SPU_NOT_EXISTS` 且不返回商品字段；**增加 `enterpriseOnly=true` + `browseLevelType=2` 的 AND 场景（FR-532）**
- [X] T024 [US64] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/app/spu/AppProductSpuController.java`：`getSpuList` 追加 `browseLevelService.isVisible(spu, userId)` 过滤；`getSpuDetail` 在存在性之后、企业专享之前调用 `validateVisible(spu)`（不可见统一「商品不存在」）
- [X] T025 [P] [US64] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/controller/app/comment/AppProductCommentBrowseLevelTest.java`：不可见 SPU 的评价分页返回空（`list=[]`、`total=0`），不泄露 `spuName`
- [X] T026 [US64] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/controller/app/comment/AppProductCommentController.java#isPublicSpu` 追加浏览等级判定
- [X] T027 [US64] 运行 T023 / T025 并修复至全绿；验证 SC-195 / SC-196 / SC-197

**Checkpoint**: US64 独立可演示。

---

## Phase 5: User Story 65 - 指定等级会员的可见性生效 (Priority: P2)

**Goal**: 指定等级商品只对命中集合的会员可见；收藏、浏览历史、营销活动同样过滤；等级变化下一次请求即生效。

**Independent Test**: 商品 C 仅勾选 L2；L1、无等级、访客都不可见；L2 可见；把 L1 调成 L2 后刷新即可见。

### Tests for User Story 65（先写并确认失败）

- [X] T028 [P] [US65] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/service/favorite/ProductFavoriteBrowseLevelTest.java`：不可见商品收藏被拒；收藏分页过滤且 `total` 与列表一致
- [X] T029 [P] [US65] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/test/java/cn/iocoder/yudao/module/product/controller/app/history/AppProductBrowseHistoryBrowseLevelTest.java`：浏览历史分页过滤不可见商品，`total` 与列表一致
- [X] T030 [P] [US65] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-promotion-server/src/test/java/cn/iocoder/yudao/module/promotion/controller/app/seckill/AppSeckillActivityBrowseLevelTest.java`：秒杀活动商品列表不下发不可见 SPU 的名称/图片/价格
- [X] T031 [P] [US65] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-promotion-server/src/test/java/cn/iocoder/yudao/module/promotion/controller/app/point/AppPointActivityBrowseLevelTest.java`：积分活动分页不下发不可见 SPU（覆盖与其它三个 Controller 不同的 `spuMap` 写法）
- [X] T032 [P] [US65] 先写失败测试 `.../controller/app/combination/AppCombinationActivityBrowseLevelTest.java` 与 `.../controller/app/bargain/AppBargainActivityBrowseLevelTest.java`：拼团与砍价各自一个用例（两者共用 `getPublicSpuList` 模式；因包级可见性分属两个包，必须各建一个测试类），断言活动商品列表不下发不可见 SPU

### Implementation for User Story 65

- [X] T033 [US65] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/service/favorite/ProductFavoriteServiceImpl.java#createFavorite` 增加可见性校验；在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/mysql/favorite/ProductFavoriteMapper.java` 的两个分页方法**显式接收 `Long levelId` 参数**并追加 `EXISTS` 浏览等级条件（列名 `ps.browse_level_ids`）；`AppFavoriteController` 必须把当前等级传入，不能省略
- [X] T034 [P] [US65] 在 `yudao-cloud/yudao-module-mall/yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/dal/mysql/history/ProductBrowseHistoryMapper.java` 的两个分页方法**显式接收 `Long levelId` 参数**并追加同样的 `EXISTS` 条件；在 `ProductBrowseHistoryServiceImpl` 与 `AppProductBrowseHistoryController` 的调用链中固定传入当前等级
- [X] T035 [US65] 在以下 Controller 的**具体方法**追加 `productSpuApi.getVisibleSpuIds(getLoginUserId(), spuIds)`：
  - `.../seckill/AppSeckillActivityController.java#getPublicSpuList`
  - `.../combination/AppCombinationActivityController.java#getPublicSpuList`
  - `.../point/AppPointActivityController.java#buildAppPointActivityRespVOList`
  - `.../bargain/AppBargainActivityController.java#getPublicSpuList`
- [X] T036 [US65] 运行 T028 ~ T032 并修复至全绿；验证 SC-200（等级调整后下一次请求生效）

**Checkpoint**: US65 独立可演示。

---

## Phase 6: User Story 66 - 不符合条件的商品无法加购与下单 (Priority: P2)

**Goal**: 加购、结算、下单全部服务端二次校验；购物车残留不展示、结算被拦截；历史订单不受影响。

### Tests for User Story 66（先写并确认失败）

- [X] T037 [P] [US66] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/cart/CartBrowseLevelTest.java`：不可见商品加购返回 `PRODUCT_SPU_NOT_VISIBLE`；购物车列表移除不可见项但不删除底层行
- [X] T038 [P] [US66] 先写失败测试 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/price/TradePriceServiceBrowseLevelTest.java`：价格计算 / 结算 / 下单遇到不可见 SPU 抛出 `PRODUCT_SPU_NOT_VISIBLE`；代客下单按客户等级判定

### Implementation for User Story 66

- [X] T039 [US66] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/cart/CartServiceImpl.java`：`checkProductSku` 在企业专享校验后调用 `productSpuApi.validateSpuVisible(userId, spuId)`；`getCartList` 用 `getVisibleSpuIds` 移除不可见项（只影响展示，不删除 `trade_cart` 行）
- [X] T040 [US66] 在 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/price/TradePriceServiceImpl.java`：`checkSpuList` 增加 userId 参数并调用 `validateSpuVisible`；`calculateProductPrice` 用 `getVisibleSpuIds` 静默过滤；确保 `createOrderByAdmin` 传入客户 userId
- [X] T041 [US66] 新增回归护栏测试 `yudao-cloud/yudao-module-mall/yudao-module-trade-server/src/test/java/cn/iocoder/yudao/module/trade/service/order/TradeOrderHistoryBrowseLevelTest.java`：会员等级下调后，已生成订单的详情/售后仍能读到原订单项（FR-531）；该测试保护「不改变」行为，允许一开始就是绿的
- [X] T042 [US66] 运行 T037 / T038 / T041 并修复至全绿（自动化已通过；手工验证随 T045 执行）

**Checkpoint**: US66 独立可演示。

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T043 [P] 更新 `specs/019-product-browse-level/quickstart.md`：补入新增测试类（评论、积分、拼团、历史订单回归）与历史订单手工验收步骤
- [X] T044 用 `-am` 运行受影响的三个后端模块测试，并在运行前清理 IDE 写入的 `target/classes`；记录通过的测试清单
- [ ] T045 按 [quickstart.md](./quickstart.md) 手工走访客 / M0 / M1 / M2 四类身份，以及企业专享叠加场景（需启动本地前后端与测试数据）
- [ ] T046 在测试库执行 `041-product-browse-level.sql`，确认 `SELECT COUNT(*) FROM product_spu WHERE browse_level_type <> 0` 为 0（SC-201）（需真实 MySQL，未在本次执行）
- [X] T047 [P] 回归 storefront：确认商品列表、搜索、详情、收藏、历史、购物车、结算页面在无代码改动下按身份过滤；跑现有 storefront 单测（1465/1466；`design-consistency` 一条并行超时，单独复跑 81/81 通过）
- [X] T048 [P] 更新 `CLAUDE.md`：新增 019 特性条目、`041` 脚本上线顺序、「浏览等级与企业专享/授权商品独立叠加」口径

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞所有用户故事**
- **US63 / US64**（P1）: 依赖 Foundational，可并行
- **US65 / US66**（P2）: 依赖 Foundational，可并行；建议在 P1 之后
- **Polish**: 依赖所有要交付的故事完成

### User Story Dependencies

| 故事 | 依赖 | 说明 |
|---|---|---|
| US63 运营配置 | Foundational | 只改管理端与保存校验 |
| US64 公开/登录可见 | Foundational | 商品 Controller + 评论 |
| US65 指定等级 | Foundational | 收藏/历史/营销是额外展示面 |
| US66 交易链路 | Foundational | 通过 RPC 复用判定 |

### Within Each User Story

- 测试先写并确认失败 → 再实现 → 再跑测试
- 先字段/枚举脚手架（保证可编译），再行为
- 先 Service，再 Controller / 前端

### Parallel Opportunities

- Phase 1：T002 / T003 / T004 / T005
- Phase 2：T006 / T007 / T008 / T010 / T012 / T014
- US63：T017 / T018 测试可并行；T019 / T020 可并行
- US64：T023 / T025 两个测试可并行；T026 可与 T024 并行
- US65：T028 / T029 / T030 / T031 / T032 五个测试可并行
- US66：T037 / T038 两个测试可并行
- Polish：T043 / T047 / T048 可并行

---

## Parallel Example: User Story 65

```bash
# 五个展示面的失败测试可同时写：
Task: "ProductFavoriteBrowseLevelTest in yudao-module-product-server/src/test/java/.../service/favorite/"
Task: "AppProductBrowseHistoryBrowseLevelTest in yudao-module-product-server/src/test/java/.../controller/app/history/"
Task: "AppSeckillActivityBrowseLevelTest in yudao-module-promotion-server/src/test/java/.../controller/app/seckill/"
Task: "AppPointActivityBrowseLevelTest in yudao-module-promotion-server/src/test/java/.../controller/app/point/"
Task: "AppCombinationActivityBrowseLevelTest in yudao-module-promotion-server/src/test/java/.../controller/app/combination/"
```

---

## Implementation Strategy

### MVP First（建议 US63 + US64）

1. 完成 Phase 1 / Phase 2；
2. 完成 US63（运营能配置）与 US64（公开/登录可见性生效）；
3. 按 quickstart §4.1 / §4.2 独立验证；
4. 此时已能交付「运营可按登录态控制商品可见性」的最小可用版本。

### Incremental Delivery

1. Setup + Foundational → 基础判定可用
2. US63 → 管理端可配置
3. US64 → 公开/登录两档生效
4. US65 → 指定等级 + 收藏/历史/营销
5. US66 → 交易链路封堵 + 历史订单回归
6. Polish → quickstart、SQL 迁移、storefront 回归、CLAUDE.md

### Notes

- 每个任务完成后可提交；测试任务必须先确认失败，失败原因应为「行为未实现」而非编译/导入错误。
- 不要为了让测试通过而放宽 Q149 的「完全隐藏」口径。
- 不修改 012 既有 `enterpriseOnly` / 订阅计划过滤逻辑。
- 新增的 5 个测试类是对分析报告中 CRITICAL 测试先行缺口的修复：`AppProductCommentBrowseLevelTest`、`ProductSpuServiceImplGetSpuPageBrowseLevelTest`、`AppPointActivityBrowseLevelTest`、`AppCombinationActivityBrowseLevelTest`、`TradeOrderHistoryBrowseLevelTest`。

---

## Phase 8: Convergence

- [X] T049 为秒杀 / 拼团 / 积分 / 砍价的活动商品过滤追加 enterpriseOnly 排除（先写失败测试；渲染期兜底，历史活动同样安全） per FR-532 (partial)
- [X] T050 管理端等级回显合并「已选中但已停用」的等级（先写失败测试；避免完整回显退化成裸 ID） per FR-519 (partial)
- [X] T051 把 `TradePriceServiceImpl#checkSpuList` 的逐 SPU `validateSpuVisible` 改为一次 `getVisibleSpuIds` 批量判定（先写失败测试） per plan:R5 (partial)
- [X] T052 让 `ProductSpuApiImpl#getVisibleSpuIds` 复用 `ProductSpuBrowseLevelService#filterVisibleSpuIds`，或删除未使用的方法，消除重复过滤 per Constitution IV (unrequested)
