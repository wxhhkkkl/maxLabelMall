# 数据模型：商品浏览等级

**Feature**: `019-product-browse-level` | **Date**: 2026-11-16

## 1. 表结构变更

### `product_spu`（既有表）

| 列 | 类型 | NULL | 默认 | 说明 |
|---|---|---|---|---|
| `browse_level_type` | `tinyint` | NOT NULL | `0` | 浏览等级模式：`0` 公开 / `1` 全部登录会员 / `2` 指定等级会员 |
| `browse_level_ids` | `varchar(512)` | NULL | `''` | 指定等级集合，逗号分隔的 `member_level.id`；仅 `type=2` 时有意义 |

建议放在 `delivery_template_id` 之后，语义上属于「可见性 / 物流」这一组商品配置。

```sql
ALTER TABLE `product_spu`
  ADD COLUMN `browse_level_type` tinyint NOT NULL DEFAULT 0
    COMMENT '浏览等级：0 公开 1 全部登录会员 2 指定等级会员' AFTER `delivery_template_id`,
  ADD COLUMN `browse_level_ids` varchar(512) NULL DEFAULT ''
    COMMENT '指定等级会员的会员等级编号数组，逗号分隔；仅 type=2 有意义' AFTER `browse_level_type`;
```

**迁移影响**：存量行由 `DEFAULT 0` 填为「公开」，与 FR-521 / SC-201 一致；不需要数据回填脚本。

## 2. Java 模型

### `ProductSpuDO`（`dal/dataobject/spu/ProductSpuDO.java`）

```java
// ========== 可见性相关字段 =========

/**
 * 浏览等级模式
 *
 * 枚举 {@link ProductSpuBrowseLevelTypeEnum}
 */
private Integer browseLevelType;

/**
 * 指定等级会员的会员等级编号数组
 *
 * 仅 browseLevelType = 2 时有意义；关联 {@link cn.iocoder.yudao.module.member.dal.dataobject.level.MemberLevelDO#getId()}
 */
@TableField(typeHandler = LongListTypeHandler.class)
private List<Long> browseLevelIds;
```

注意 `ProductSpuMapper#selectByIdIncludeDeleted` 是手写 `@Select`，需补：

```java
@Result(column = "browse_level_ids", property = "browseLevelIds", typeHandler = LongListTypeHandler.class)
```

### 枚举 `ProductSpuBrowseLevelTypeEnum`

`enums/spu/ProductSpuBrowseLevelTypeEnum.java`，沿用项目枚举风格：

| code | 名称 | 含义 |
|---|---|---|
| 0 | `PUBLIC` | 公开：含未登录访客 |
| 1 | `LOGIN` | 全部登录会员：任意已登录会员 |
| 2 | `LEVEL` | 指定等级会员：当前等级命中集合 |

## 3. 判定矩阵

| 商品配置 | 未登录访客 | 已登录、无等级（levelId null/0） | 已登录、等级命中 | 已登录、等级不命中 |
|---|---|---|---|---|
| `browse_level_type=0` 公开 | ✅ | ✅ | ✅ | ✅ |
| `browse_level_type=1` 全部登录会员 | ❌ | ✅ | ✅ | ✅ |
| `browse_level_type=2` 指定等级 | ❌ | ❌ | ✅ | ❌ |

补充规则：

- 生产库由 `browse_level_type NOT NULL DEFAULT 0` 保证不存在 null；测试插入时省略该字段，
  MyBatis-Plus 会省略 null 列，由数据库默认值落为 0。因此 SQL 条件只匹配 `= 0`，不额外处理 NULL。
- 企业管理员 / 企业 OWNER 不豁免（FR-533）。
- 与 `enterprise_only`、订阅计划商品过滤是 **AND** 关系（FR-532）。

## 4. SQL 条件

分页 / 收藏 / 浏览历史的 SQL 过滤统一为：

```text
browse_level_type = 0
OR (browse_level_type = 1 AND :loggedIn)
OR (browse_level_type = 2 AND :levelId IS NOT NULL AND find_in_set(:levelId, browse_level_ids))
```

其中 `find_in_set` 由 `MyBatisUtils.findInSet("browse_level_ids")` 生成：

- MySQL：`FIND_IN_SET({0}, browse_level_ids) <> 0`
- H2：`POSITION(',' || CAST({0} AS VARCHAR) || ',' IN ',' || browse_level_ids || ',') > 0`

收藏 / 浏览历史在 `EXISTS` 子查询里使用时，列名传 `ps.browse_level_ids`（工具允许 `表别名.列名`）。

## 5. 管理端校验

保存商品时：

| 条件 | 处理 |
|---|---|
| `browseLevelType` 为 null | 按 `0`（公开） |
| `browseLevelType` 不在 0/1/2 | 拒绝：`1_008_005_007` |
| `type=2` 且 `browseLevelIds` 为空 | 拒绝：`1_008_005_005`「指定等级会员时，至少选择一个会员等级」 |
| `type=2` 且等级编号不存在 | 拒绝：`1_008_005_006`「会员等级({})不存在」 |
| `type!=2` | 把 `browseLevelIds` 置为空列表，清掉旧配置 |
| `type=2` 且编号重复 | 去重后保存（不报错） |

等级是否启用不做后端强校验（UI 只列出启用等级；历史配置引用的等级被停用后仍保留，商品不会自动变成公开）。

## 6. 关键实体关系

```text
member_level (id, level, name, status)
      ▲
      │ browse_level_ids 引用 id（多对多，仅 type=2）
      │
product_spu (id, browse_level_type, browse_level_ids, enterprise_only, status, ...)
      ▲
      │ levelId
      │
member_user (id, level_id, ...)
```

- 会员等级是既有实体，本次不新增表、不新增等级字段。
- 商品与会员等级之间是「商品引用级别集合」的关系，不建关联表（见 research R1）。
- 会员当前等级由 `member_user.level_id` 决定；`level_id = 0` 视为无等级。

## 7. 数据示例

| 商品 | browse_level_type | browse_level_ids | 效果 |
|---|---|---|---|
| A | 0 | `''` | 所有人可见 |
| B | 1 | `''` | 仅登录会员可见 |
| C | 2 | `'3,5'` | 仅等级 id 为 3 或 5 的会员可见 |
| D | 2 | `''` | **不允许**：保存阶段被拒绝 |

## 8. 测试数据要求

- H2 测试建表脚本 `src/test/resources/sql/create_tables.sql` 的 `product_spu` 增加两列，
  与生产 DDL 保持一致（NOT NULL DEFAULT 0）。
- 测试插入商品时不需要显式设置 `browseLevelType`，MyBatis-Plus 会省略 null 字段，由数据库默认值落为 0。
- 需要覆盖的等级场景：无等级（0）、命中集合、未命中集合、集合为空（仅直接构造 DO 时出现）。
