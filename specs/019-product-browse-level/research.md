# Phase 0 研究：商品浏览等级

**Feature**: `019-product-browse-level` | **Date**: 2026-11-16

本阶段只解决「怎么做」的技术选择；业务口径已在 spec 的 Q148 ~ Q151 定稿。

---

## R1 浏览等级如何存储？

**Decision**: 在 `product_spu` 增加两列：`browse_level_type`（tinyint）与 `browse_level_ids`
（varchar，逗号分隔的 `member_level.id`）。DO 用 `LongListTypeHandler` 映射。

**Rationale**:
- 没有「每个等级对商品有额外属性」的需求，关联表只会多一张表、多一套 CRUD。
- 项目已有 `delivery_types` 用逗号分隔 + `IntegerListTypeHandler`；`LongListTypeHandler` 可直接复用。
- 两列与商品同生命周期，不需要级联维护。

**Alternatives considered**:
- 关联表 `product_spu_browse_level`：被否 —— 无额外属性，查询与保存都更重。
- JSON 列 + `JSON_CONTAINS`：被否 —— H2 单元测试与 MySQL 的 JSON 函数不一致，且项目没有先例。
- 只存「最低等级」：被否 —— Q148 明确是「勾选若干等级集合」。

## R2 存 `member_level.id` 还是 `member_level.level` 数值？

**Decision**: 存 `member_level.id`（等级编号）。

**Rationale**: 会员 `MemberUserDO.levelId` 指向的就是 `member_level.id`，运行时直接比较即可；
`member_level.level` 是业务排序值，运营调整等级顺序时会让存量商品的配置错位。

**Alternatives considered**: 存 `level` 数值 —— 被否，原因同上。

## R3 判定逻辑放在哪里？

**Decision**: 新增 `ProductSpuBrowseLevelService` / `Impl`，作为唯一规则来源：
- `Long getViewerLevelId(Long userId)`：未登录返回 null；已登录调 `MemberUserApi#getUser`，`levelId`
  为 null/0 时返回 null。
- `boolean isVisible(ProductSpuDO spu, Long userId, Long levelId)`：纯函数。
- `void validateVisible(ProductSpuDO spu)`：当前登录用户不可见时抛 `SPU_NOT_EXISTS`。
- `List<Long> filterVisibleSpuIds(Long userId, Collection<Long> spuIds)`：批量过滤。

**Rationale**: 宪法的复用原则要求业务规则只有一处权威实现；分页 SQL 与内存过滤都调用同一规则，
避免「列表能看到、详情说没有」的分裂。

**依赖方向**：BrowseLevelService 只依赖 `MemberUserApi`，入参是已查出的 `ProductSpuDO` / `spuId` 集合，
**不注入** `ProductSpuService`，避免与 `ProductSpuServiceImpl` 形成循环依赖。需要查商品的调用方
（App Controller、`ProductSpuApiImpl`、收藏/历史服务）各自先取 DO，再交给它判定。

**Alternatives considered**: 把判定散落在各 Controller —— 被否，重复且容易漏面。

## R4 分页为什么必须在 SQL 层过滤？

**Decision**: 在 `ProductSpuMapper#selectPage(AppProductSpuPageReqVO, Set<Long> categoryIds)`
增加 `loggedIn` / `levelId` 入参，在 SQL 条件里过滤。使用
`MyBatisUtils.findInSet("browse_level_ids")` 生成跨数据库表达式（H2 用 `POSITION`，MySQL 用 `FIND_IN_SET`）。

**Rationale**:
- 先查一页再在内存里剔除，会让 `total` 虚高、页数不对，甚至出现整页空白（项目在 012 的浏览历史
  已经踩过这个坑，见 `AppProductBrowseHistoryVisibilityTest#nonAdminHistoryTotalMatchesFilteredList`）。
- 框架已有跨库模板，不需要自己写 `FIND_IN_SET`。

**Alternatives considered**:
- 内存过滤：被否，分页总数不正确。
- 用 `LIKE CONCAT('%,', ?, ',%')`：被否，需要处理首尾逗号与 NULL，且 H2/MySQL 行为要额外测试。

## R5 跨模块如何复用判定？

**Decision**: 在 `ProductSpuApi` 增加两个 RPC：
- `List<Long> getVisibleSpuIds(Long userId, Collection<Long> spuIds)`
- `Boolean validateSpuVisible(Long userId, Long spuId)`

**Rationale**:
- 交易模块需要在加购 / 价格计算 / 下单时判定；营销模块需要在活动商品列表判定。
- 批量接口避免列表场景 N+1；单条接口让加购错误信息直接。
- RPC 只做浏览等级判定，**不**合并企业专享 / 订阅计划过滤，避免改变 012 既有行为。

**Alternatives considered**:
- 交易模块直接读 `product_spu`：被否，跨模块直连表违背现有边界。
- 把浏览等级字段塞进 `ProductSpuRespDTO` 让调用方自己判定：被否，规则会扩散成多份。

## R6 管理端的等级选项从哪里来？

**Decision**: 复用 `GET /member/level/list-all-simple`（`MemberLevelController#getSimpleLevelList`），
该接口只返回启用状态的等级，且无 `@PreAuthorize`。

**Rationale**: 宪法的复用原则；运营只需要在启用等级里选，停用等级不应再被新配置引用。

**Alternatives considered**:
- 新建商品模块自己的等级接口：被否，重复。
- 用 `/member/level/list`：被否，需要 `member:level:query` 权限，而商品编辑人员未必有。

## R7 隐藏商品报什么错？

**Decision**:
- 商品模块（详情 / 收藏）统一抛既有 `SPU_NOT_EXISTS`（`1_008_005_000`，「商品 SPU 不存在」）。
- 交易模块（加购 / 结算 / 下单）新增 `PRODUCT_SPU_NOT_VISIBLE`（`1_011_010_005`，
  「商品不存在或不可购买」）。

**Rationale**: Q149 要求「完全隐藏」，不能返回「等级不足」这类泄露商品存在性的文案。
交易模块使用自己的错误码，避免跨模块异常传播。

**Alternatives considered**:
- 新增 `SPU_BROWSE_LEVEL_DENIED` 并返回「等级不足」：被否，违反 Q149。
- 交易模块直接透传商品模块异常：被否，Feign 异常传播链路不直观。

## R8 C 端 Web 需要改吗？

**Decision**: 不需要改 storefront 代码。

**Rationale**: `storefront/src/config/http/service.ts` 的请求拦截器对每个请求调用
`buildHeaders(getAccessToken())`，有 token 就注入 `Authorization`。因此登录会员访问
`/product/spu/page`、`/product/spu/get-detail` 时服务端能拿到登录用户；未登录则没有 token，
自然按访客判定。

**Alternatives considered**:
- 在每个商品请求里显式传 userId / level：被否，客户端传身份可伪造，且违反服务端裁决原则。

## R9 存量商品与旧前端兼容

**Decision**:
- SQL 迁移用 `NOT NULL DEFAULT 0`，存量商品全部落为「公开」。
- 管理端保存时 `browseLevelType` 缺省按 0 处理，旧版管理端提交不会失败。
- `type != 2` 时后端把 `browseLevelIds` 置为**空列表**而不是 null，确保旧值被清空。

**Rationale**: SC-201 要求升级后 0 个商品消失；MyBatis-Plus `updateById` 默认忽略 null 字段，
置 null 清不掉旧值。

**Alternatives considered**:
- 允许 null 并写自定义 update wrapper：被否，能用一个非 null 空列表解决就不加分支。

## R10 回收站商品的读取

**Decision**: 在 `ProductSpuMapper#selectByIdIncludeDeleted` 的 `@Results` 中补
`browse_level_ids` 的 `LongListTypeHandler` 映射。

**Rationale**: 该方法是 `@Select` 手写 SQL，`autoResultMap` 不会生效；现有代码已经为
`slider_pic_urls`、`delivery_types` 手工补了映射。漏掉会让回收站商品的等级集合读成空，
恢复后可能变成「对所有人不可见」的脏状态。

**Alternatives considered**: 改成 `selectById` 加 `@TableLogic` 绕过 —— 被否，会改变既有回收站语义。

## R11 性能与缓存

**Decision**: 不做缓存。商品分页每次请求最多多一次 `MemberUserApi#getUser` RPC；未登录不发。
批量接口每次请求只解析一次等级，然后内存过滤。

**Rationale**: 会员等级变化要求「下一次请求即生效」（SC-200），缓存会引入失效问题；
当前商品分页本身已有多次 RPC / 联表，多一次按主键查询的会员读取不构成瓶颈。

**Alternatives considered**:
- 本地 Caffeine 缓存 levelId：被否，等级变更后的失效与多实例一致性成本高于收益。
