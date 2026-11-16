# Implementation Plan: 商品浏览等级（与会员等级关联）

**Branch**: `019-product-browse-level` | **Date**: 2026-11-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/019-product-browse-level/spec.md`

## Summary

给商品 SPU 增加「浏览等级」三档配置（公开 / 全部登录会员 / 指定等级会员），复用 yudao 既有会员等级
（`member_level` + 会员 `levelId`）。可见性由服务端统一裁决：

- 商品分页 / 搜索 / 详情按当前登录态与会员等级过滤，等级不足时等同商品不存在；
- 购物车、收藏、浏览历史、营销活动等展示面同样过滤；
- 加购、结算、下单在服务端二次校验，避免绕过；
- 与「企业专享」「会员授权商品」独立叠加，全部条件同时满足才可见。

技术路径：`product_spu` 新增两列（模式 + 等级 ID 数组），新增一个纯判定 Service 作为唯一规则来源，
分页过滤下推到 SQL（用框架已有的跨库 `findInSet` 模板），跨模块通过 `ProductSpuApi` 暴露批量判定，
管理端表单复用 `/member/level/list-all-simple`。

## Technical Context

**Language/Version**: Java 25（`yudao-cloud` 根 pom `java.version=25`）；Vue 3.5 + TypeScript（管理端）

**Primary Dependencies**: Spring Boot 4.1 / MyBatis-Plus / yudao-cloud 既有模块；管理端 Element Plus 2.13.7

**Storage**: MySQL 8.0（`yudao-cloud/sql/mysql`）；单元测试 H2（MySQL 兼容模式）

**Testing**: JUnit 5 + `BaseDbUnitTest`（服务端）；Vitest（管理端 `*.spec.ts`）

**Target Platform**: 服务端本地单体聚合启动；管理端浏览器

**Project Type**: 后端单体（yudao-cloud）+ 管理端 Web + C 端 Web（storefront 无需改代码）

**Performance Goals**: 不新增可感知延迟。商品分页每次请求最多多一次会员 RPC（未登录 0 次）；
其余接口复用既有批量查询模式，不引入 N+1。

**Constraints**:
- 可见性只能在服务端判定，C 端与管理端不得各写一套（spec FR-534）。
- 不改动 012 既有「企业专享」与「会员授权商品」口径（spec FR-532）。
- C 端 storefront 的请求封装已全局注入 `Authorization`，因此无需改前端即可让登录态参与判定。

**Scale/Scope**: 3 档配置；等级数量为个位数到几十；商品量级沿用现有商城；新增 2 列、1 个枚举、
1 个 Service、2 个 RPC 方法。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结论 |
|---|---|---|
| I. 测试先行 | 每个行为变更先写失败测试：判定矩阵、SQL 分页、详情隐藏、收藏/历史/购物车/下单拦截、管理端校验、前端表单 | ✅ 计划中每个实现任务都前置于测试任务 |
| II. 先澄清 | Q148 判定方式、Q149 完全隐藏、Q150 独立叠加、Q151 个人列表处理均已由所有者确认 | ✅ 无遗留澄清 |
| III. 复用既有能力 | 复用 `member_level` / `MemberUserApi` / `MemberLevelApi` / `LongListTypeHandler` / `MyBatisUtils.findInSet` / 既有 `enterpriseOnly` 过滤位置；不新建等级体系 | ✅ 无重复实现 |
| IV. 最小改动 | 只新增本需求需要的列、枚举、Service 与过滤点；不重构企业专享逻辑，不改无关代码 | ✅ diff 可逐行追溯 |
| V. 增量可验证 | SC-195 ~ SC-202 均可通过自动化或手工清单验证；quickstart 给出端到端步骤 | ✅ 计划末尾明确验证方式 |
| 技术栈约束 | 仅 MySQL；C 端只走 `/app-api`，管理端只走 `/admin-api`；不引入新依赖 | ✅ |

**Complexity Tracking**: 无违规项，无需填写。

## Project Structure

### Documentation (this feature)

```text
specs/019-product-browse-level/
├── plan.md              # 本文件
├── research.md          # Phase 0：技术决策与替代方案
├── data-model.md        # Phase 1：字段、枚举、判定矩阵、迁移
├── quickstart.md        # Phase 1：端到端验证清单
├── contracts/
│   ├── admin-api.md     # 商品保存/详情的契约变化
│   └── app-api.md       # C 端各展示面与交易面的行为契约
└── tasks.md             # Phase 2（/speckit-tasks 生成，不在本阶段）
```

### Source Code (repository root)

```text
yudao-cloud/
├── sql/mysql/
│   └── 041-product-browse-level.sql                     # 新增：product_spu 两列
├── yudao-module-mall/
│   ├── yudao-module-product-api/src/main/java/cn/iocoder/yudao/module/product/
│   │   ├── api/spu/ProductSpuApi.java                   # 新增 2 个可见性 RPC
│   │   └── enums/ErrorCodeConstants.java                # 新增浏览等级校验错误码
│   ├── yudao-module-product-server/src/main/java/cn/iocoder/yudao/module/product/
│   │   ├── api/spu/ProductSpuApiImpl.java               # 实现 RPC
│   │   ├── controller/admin/spu/vo/ProductSpuSaveReqVO.java
│   │   ├── controller/admin/spu/vo/ProductSpuRespVO.java
│   │   ├── controller/app/spu/AppProductSpuController.java
│   │   ├── controller/app/comment/AppProductCommentController.java
│   │   ├── controller/app/favorite/AppFavoriteController.java
│   │   ├── controller/app/history/AppProductBrowseHistoryController.java
│   │   ├── dal/dataobject/spu/ProductSpuDO.java
│   │   ├── dal/mysql/spu/ProductSpuMapper.java
│   │   ├── dal/mysql/favorite/ProductFavoriteMapper.java
│   │   ├── dal/mysql/history/ProductBrowseHistoryMapper.java
│   │   ├── enums/spu/ProductSpuBrowseLevelTypeEnum.java # 新增
│   │   ├── service/spu/ProductSpuBrowseLevelService.java      # 新增（唯一规则来源）
│   │   ├── service/spu/ProductSpuBrowseLevelServiceImpl.java  # 新增
│   │   ├── service/spu/ProductSpuServiceImpl.java
│   │   ├── service/favorite/ProductFavoriteServiceImpl.java
│   │   └── src/test/resources/sql/create_tables.sql      # H2 测试表补两列
│   ├── yudao-module-trade-api/src/main/java/cn/iocoder/yudao/module/trade/enums/ErrorCodeConstants.java
│   └── yudao-module-trade-server/src/main/java/cn/iocoder/yudao/module/trade/service/
│       ├── cart/CartServiceImpl.java
│       └── price/TradePriceServiceImpl.java
│   └── yudao-module-promotion-server/src/main/java/cn/iocoder/yudao/module/promotion/controller/app/
│       ├── seckill/AppSeckillActivityController.java
│       ├── combination/AppCombinationActivityController.java
│       ├── point/AppPointActivityController.java
│       └── bargain/AppBargainActivityController.java
└── yudao-ui/yudao-ui-admin-vue3/src/
    ├── api/mall/product/spu.ts                          # Spu 类型加字段
    └── views/mall/product/spu/form/OtherForm.vue        # 浏览等级表单
        └── OtherForm.spec.ts                            # 表单测试

storefront/                                               # 无需改代码（已全局注入 Authorization）
```

**Structure Decision**: 沿用现有 yudao-cloud 多模块结构。规则实现集中在 `product-server`，
`product-api` 只暴露 RPC 契约；交易与营销模块通过 RPC 复用同一判定，不复制规则。

## Phase 0：关键设计决策

详见 [research.md](./research.md)。摘要：

| # | 决策 | 说明 |
|---|---|---|
| R1 | 用 `product_spu` 两个列，不建关联表 | 没有「每个等级额外属性」；`LongListTypeHandler` + 逗号分隔与既有 `delivery_types` 一致 |
| R2 | 指定等级存 `member_level.id`，不存 `level` 数值 | 会员 `levelId` 指向的是等级编号；存数值会在等级调整后错位 |
| R3 | 判定唯一来源：`ProductSpuBrowseLevelService` | 分页 SQL 与内存判定共用同一规则；跨模块走 RPC |
| R4 | 分页在 SQL 层过滤 | 保证 `total` 与列表一致（SC-197），避免「先取页再剔」 |
| R5 | 新增批量 RPC `getVisibleSpuIds` | 购物车、营销活动等列表场景一次判定，避免 N+1 |
| R6 | 管理端等级选项复用 `/member/level/list-all-simple` | 该接口只返回启用等级，无新增接口 |
| R7 | 隐藏商品统一报「商品不存在」 | 商品模块复用 `SPU_NOT_EXISTS`；交易模块新增 `1_011_010_005` |

## Phase 1：设计与契约

### 1. 数据模型

详见 [data-model.md](./data-model.md)。

`product_spu` 新增：

| 列 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `browse_level_type` | tinyint NOT NULL | 0 | 0 公开 / 1 全部登录会员 / 2 指定等级会员 |
| `browse_level_ids` | varchar(512) NULL | '' | 指定等级集合，逗号分隔的 `member_level.id`；仅 type=2 有意义 |

DO 增加 `Integer browseLevelType` 与 `@TableField(typeHandler = LongListTypeHandler.class) List<Long> browseLevelIds`。
`ProductSpuDO` 已 `autoResultMap = true`；但 `ProductSpuMapper#selectByIdIncludeDeleted` 是 `@Select` 手写结果映射，
必须补一条 `@Result(column = "browse_level_ids", property = "browseLevelIds", typeHandler = LongListTypeHandler.class)`，
否则回收站里的商品读出来等级集合为空。

### 2. 判定规则

```text
type=0（公开）        → 所有人可见
type=1（全部登录会员） → 仅 userId != null
type=2（指定等级）    → userId != null 且 levelId 非空/非 0 且 levelId ∈ browseLevelIds
```

`levelId` 为 `null` 或 `0` 一律视为「无等级」（成员模块 `updateUserLevel` 会把 null 归零）。
企业管理员不豁免；与 `enterpriseOnly`、订阅计划商品过滤各自独立、条件取 AND。

### 3. 服务端改动点

1. **管理端保存校验**（`ProductSpuServiceImpl#createSpu/updateSpu`）：
   - `browseLevelType` 为空按 0 处理；非 0/1/2 拒绝。
   - `type=2` 时 `browseLevelIds` 必须非空、去重、每个等级都存在（`MemberLevelApi#getMemberLevel`）。
   - `type!=2` 时把 `browseLevelIds` 置为空列表（不能置 null —— MyBatis-Plus `updateById` 会忽略 null，
     旧值清不掉）。
2. **C 端列表/搜索**（`ProductSpuMapper#selectPage(AppProductSpuPageReqVO, Set<Long>)`）：
   新增带 `boolean loggedIn, Long levelId` 的重载，在既有企业专享/授权商品条件之后追加
   `(browse_level_type = 0 OR (browse_level_type = 1 AND loggedIn) OR (browse_level_type = 2 AND levelId != null AND findInSet(...)))`；
   旧签名保留并委托为「访客」，既有 Mapper 测试无需改写（它们的语义本来就是公开列表）。
   生产调用走新重载。用 `MyBatisUtils.findInSet("browse_level_ids")` 生成跨库条件（H2 / MySQL 均可用）。
3. **C 端详情**（`AppProductSpuController#getSpuDetail`）：存在性之后、企业专享之前调用
   `browseLevelService.validateVisible(spu)`，不满足抛 `SPU_NOT_EXISTS`。
4. **C 端批量取商品**（`getSpuList`）：在既有企业专享/授权商品过滤后，追加 `isVisible(spu, userId)` 过滤。
5. **评价**（`AppProductCommentController#isPublicSpu`）：追加浏览等级判定，隐藏商品返回空分页。
6. **收藏**：`createFavorite` 校验；`ProductFavoriteMapper` 的两个分页方法**显式接收 `Long levelId`**
   并追加 `EXISTS (SELECT 1 FROM product_spu ps WHERE ... AND <浏览等级条件>)`，保证 total 正确；
   `AppFavoriteController` 必须把当前等级传入，不得省略。
7. **浏览历史**：`ProductBrowseHistoryMapper` 的两个分页方法同样**显式接收 `Long levelId`**，
   由 `ProductBrowseHistoryServiceImpl` / `AppProductBrowseHistoryController` 固定传入。
8. **RPC**（`ProductSpuApi`）：
   - `CommonResult<List<Long>> getVisibleSpuIds(Long userId, Collection<Long> spuIds)`
   - `CommonResult<Boolean> validateSpuVisible(Long userId, Long spuId)`：SPU 不存在或不可见都返回
     `false`（不抛异常，交易模块用自己的错误码统一提示）。
   实现内只做浏览等级判定，不掺入企业专享/订阅计划逻辑（那两块仍由调用方各自处理）。

### 4. 交易与营销

1. **加购**（`CartServiceImpl#checkProductSku`）：企业专享校验之后调用 `validateSpuVisible(userId, spuId)`，
   不通过抛新增交易错误码 `PRODUCT_SPU_NOT_VISIBLE`（`1_011_010_005`，「商品不存在或不可购买」）。
2. **购物车列表**（`CartServiceImpl#getCartList`）：用 `getVisibleSpuIds` 把当前不可见 SPU 的购物项
   从展示列表移除；**不删除**底层购物车行（Q151：等级恢复后重新可见）。
3. **价格计算 / 结算 / 下单**（`TradePriceServiceImpl#calculateOrderPrice` → `checkSpuList`）：
   按 `calculateReqBO.getUserId()` 调用 `validateSpuVisible`，任一 SPU 不可见即拒绝。
   代客下单（017）传入的是**客户** userId，判定客户等级。
4. **商品价格展示**（`calculateProductPrice(userId, spuIds)`）：沿用既有「静默过滤」方式，
   用 `getVisibleSpuIds` 过滤后再返回。
5. **营销活动**（秒杀 / 拼团 / 积分 / 砍价）：在各 Controller 既有 `getPublicSpuList` / `spuMap` 过滤处，
   追加 `productSpuApi.getVisibleSpuIds(getLoginUserId(), spuIds)` 过滤。未登录时只保留公开商品。

### 5. 管理端

`OtherForm.vue` 在「企业专享」下方新增：

- `el-radio-group v-model="formData.browseLevelType"`：公开 / 全部登录会员 / 指定等级会员；
- 仅当 `browseLevelType === 2` 时展示 `el-select multiple`，选项来自 `getSimpleLevelList()`；
- 校验规则：`type=2` 且 `browseLevelIds` 为空时报「请选择至少一个会员等级」；
- 从接口回填后把 `browseLevelType` 归一为 `0`（null/undefined → 0）、`browseLevelIds` 归一为 `[]`；
- 提交时 `OtherForm` 既有的 `Object.assign(props.propFormData, formData.value)` 会把两个字段带进保存请求。

`Spu` 类型增加 `browseLevelType?: number` / `browseLevelIds?: number[]`。

`OtherForm.spec.ts` 需 mock `@/api/member/level` 的 `getSimpleLevelList`（表单挂载即拉取选项），
并覆盖：默认「公开」、选「指定等级」后出现多选、空选保存被拦截、回填后状态正确。

### 6. C 端 Web（storefront）

**无需改代码**：`src/config/http/service.ts` 的请求拦截器已经对每个请求注入
`Authorization: Bearer <token>`（有 token 时），因此登录会员访问 `/product/spu/page` 与
`get-detail` 时服务端能识别身份。发版后做回归即可（见 quickstart）。

### 7. 数据库脚本与发版顺序

`yudao-cloud/sql/mysql/041-product-browse-level.sql`：

```sql
ALTER TABLE `product_spu`
  ADD COLUMN `browse_level_type` tinyint NOT NULL DEFAULT 0
    COMMENT '浏览等级：0 公开 1 全部登录会员 2 指定等级会员' AFTER `delivery_template_id`,
  ADD COLUMN `browse_level_ids` varchar(512) NULL DEFAULT ''
    COMMENT '指定等级会员的会员等级编号数组，逗号分隔；仅 type=2 有意义' AFTER `browse_level_type`;
```

发版顺序：

1. 执行 `041-product-browse-level.sql`；
2. 发布后端；
3. 发布管理端；
4. C 端无需发版，发版后做一次回归（guest / 登录会员 / 指定等级会员）。

无新增菜单或按钮权限：表单字段复用商品编辑权限；等级下拉复用无鉴权的 `list-all-simple`。

## Phase 1 输出物

- [research.md](./research.md)
- [data-model.md](./data-model.md)
- [contracts/admin-api.md](./contracts/admin-api.md)
- [contracts/app-api.md](./contracts/app-api.md)
- [quickstart.md](./quickstart.md)

## 验证策略

| 成功标准 | 验证方式 |
|---|---|
| SC-195 / SC-196 | `ProductSpuBrowseLevelVisibilityTest`（Mapper）+ `AppProductSpuBrowseLevelTest`（详情）+ `AppProductCommentBrowseLevelTest`（评价） |
| SC-197 | Mapper 分页测试 + `ProductSpuServiceImplGetSpuPageBrowseLevelTest`：3 种身份 × 3 档配置，断言 `total` 与 `list.size()` |
| SC-198 | `ProductSpuServiceImplBrowseLevelTest`（保存校验、FR-536 状态/价格不变）+ `OtherForm.spec.ts` |
| SC-199 | `CartBrowseLevelTest` + `TradePriceServiceBrowseLevelTest` |
| SC-200 | 判定 Service 单测：等级变化后同一商品判定结果随之变化 |
| SC-201 | SQL 迁移验证：升级后全部商品 `browse_level_type=0` |
| SC-202 | 收藏/历史 Mapper 测试 + 购物车列表测试 |
| FR-531 / FR-532 | `TradeOrderHistoryBrowseLevelTest`（历史订单）+ `AppProductSpuBrowseLevelTest` 的 enterpriseOnly AND 场景 |

手工端到端清单见 [quickstart.md](./quickstart.md)。明确**不验证**：高并发性能、uni-app 原生 UI、
购物车角标数量与列表条数的一致性（沿用既有企业专享行为，不在本次范围）。
