-- ============================================================================
-- 003 —— 商品分类筛选维度（维度 / 可选值 / 商品关联）
--
-- ⚠️ 本文件是**本项目自建**的迁移脚本，不是官方资产。
--    同目录下其它 `*.sql`（mall-*、pay-* 等）是从外部下载的模块 SQL，重建库时整套导入；
--    本文件只在**首次上线时执行一次**。重建库时不要把它当成官方资产一并导入
--    （下面写了幂等保护，重复执行也不会出第二份表）。
--
-- 为什么要它：
--   商城页要按「宽度 / 轴心 / 长度」这类**分类级**维度筛选商品，而库里没有承载它的地方。
--   核查过的事实（见 specs/003-product-filter-dimensions/research.md R1）：
--     · `product_property` / `product_property_value` 是**全局**的，没有 `category_id`；
--     · 商品属性值只挂在 **SKU** 上（`product_sku.properties` 内嵌 JSON），**没有 SPU 级属性表**；
--     · 存量 54 件碳带商品全是「单规格 + 名为`默认`的占位属性」，那套机制在线上实际是空的。
--   → 语义务不合，故**新建**，而不是给既有表加列。
--
-- 与测试用建表脚本的关系：
--   服务端单测跑在 H2 上，用的是 `yudao-module-product-server/src/test/resources/sql/create_tables.sql`。
--   那份**必须同步**这三张表（任务 T249），否则本模块的测试根本起不来。
--
-- 回滚：
--   DROP TABLE IF EXISTS `product_spu_filter_value`;
--   DROP TABLE IF EXISTS `product_filter_value`;
--   DROP TABLE IF EXISTS `product_filter_dimension`;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 筛选维度：挂在某个商品分类下的一组筛选条件（例如「碳带」下的「宽度」）
--
--    只存**单一归属** `category_id`；「向下继承到子分类」是查询时按分类祖先链实时求的
--    （决策 Q10=B，见 spec）。故这里**不需要**任何"展开表"。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product_filter_dimension` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `category_id` bigint       NOT NULL COMMENT '归属的商品分类编号（关联 product_category.id）',
    `name`        varchar(32)  NOT NULL COMMENT '维度名称，如「宽度」',
    `sort`        int          NOT NULL DEFAULT 0 COMMENT '展示顺序（前台按它排，不按任何运行时数据重排）',
    `remark`      varchar(255) DEFAULT NULL COMMENT '备注',
    `creator`     varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除，BaseDO 约定）',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_filter_dimension_category` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '商品筛选维度';

-- ---------------------------------------------------------------------------
-- 2. 维度可选值：属于某个维度（例如「宽度」下的「110」）。前台能点的就是它。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product_filter_value` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `dimension_id` bigint       NOT NULL COMMENT '所属筛选维度编号（关联 product_filter_dimension.id）',
    `name`         varchar(32)  NOT NULL COMMENT '可选值名称，如「110」',
    `sort`         int          NOT NULL DEFAULT 0 COMMENT '展示顺序',
    `remark`       varchar(255) DEFAULT NULL COMMENT '备注',
    `creator`      varchar(64)  DEFAULT '' COMMENT '创建者',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      varchar(64)  DEFAULT '' COMMENT '更新者',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
    `tenant_id`    bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_filter_value_dimension` (`dimension_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '商品筛选维度可选值';

-- ---------------------------------------------------------------------------
-- 3. 商品 × 可选值：多对多，前台筛选的**唯一依据**。
--
--    ⚠️ **刻意不加 (spu_id, filter_value_id) 唯一索引**：
--    这三张表继承 BaseDO，删除是**逻辑删除**（`deleted` 置位而非删行）。
--    唯一索引与逻辑删除天然冲突 —— 同一个商品把某个值取消再挂回去，
--    旧行还在（deleted=1），唯一约束会直接报错。
--    去重改由服务层保证：保存商品时**先清理该商品的全部关联再写入**，
--    因此不会产生重复行。这是实现期发现并写下来的取舍，不是遗漏。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product_spu_filter_value` (
    `id`              bigint   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `spu_id`          bigint   NOT NULL COMMENT '商品 SPU 编号（关联 product_spu.id）',
    `filter_value_id` bigint   NOT NULL COMMENT '筛选可选值编号（关联 product_filter_value.id）',
    `creator`         varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         bit(1)   NOT NULL DEFAULT b'0' COMMENT '是否删除（逻辑删除）',
    `tenant_id`       bigint   NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_spu_filter_value_spu` (`spu_id`),
    KEY `idx_spu_filter_value_value` (`filter_value_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '商品与筛选可选值的关联';
