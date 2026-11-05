# 数据模型（016 共享内容空间）

**一句话**：新增 **5 张表**（空间配额锁、企业文件夹、文件夹可见成员、个人/企业内容项、平台内容项），
并把两张旧表的数据**按原 id 迁移**进来；`maxlabel_label_template` 与 `maxlabel_shared_template`
保留为只读回滚依据。

迁移脚本：`yudao-cloud/sql/mysql/033-shared-content-space.sql`。所有表带 `tenant_id`、
`creator/create_time/updater/update_time/deleted`（`BaseDO` 约定），时间语义 Asia/Shanghai。

---

## 1. `maxlabel_share_space` —— 空间配额锁（新增）

> 一行 = 一个个人空间或企业空间。**只用于加锁与缓存用量**；配额上限由版本 / 企业授权实时推导，
> 不落库（改版本或续费后无需迁移数据）。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_share_space` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `scope` tinyint NOT NULL COMMENT '空间类型：1 个人 2 企业',
  `owner_id` bigint NOT NULL COMMENT '归属编号：个人为 member_user.id，企业为 maxlabel_org.id',
  `used_bytes` bigint NOT NULL DEFAULT 0 COMMENT '已用字节数（锁内按内容表重算后回写）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（不删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_scope_owner`(`tenant_id` ASC, `scope` ASC, `owner_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 共享空间配额锁';
```

**规则**：

- 首次上传/首次进入空间时惰性创建；并发创建由唯一键兜底（捕获重复后重查）。
- 上传与删除都在 `SELECT ... FOR UPDATE` 的事务内执行；锁内重算 `SUM(size_bytes)`，
  校验后写入并把 `used_bytes` 更新为新的合计。
- 空间行**永不删除**；企业解散也不删（保留历史用量）。

---

## 2. `maxlabel_share_folder` —— 企业文件夹（新增）

> 单层结构，不设 `parent_id`；不设根目录内容（Q120）。名称允许重复（不做唯一键，
> 避免逻辑删除后无法复用同名）；后台以 id 定位。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_share_folder` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `org_id` bigint NOT NULL COMMENT '企业编号，关联 maxlabel_org.id',
  `name` varchar(64) NOT NULL COMMENT '文件夹名称',
  `creator_member_id` bigint NOT NULL COMMENT '创建的管理员会员编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_org`(`tenant_id` ASC, `org_id` ASC, `deleted` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业共享文件夹';
```

**删除约束**：只有**空文件夹**可删（其下无未删除的内容项）；删除时物理删除
`maxlabel_share_folder_member` 中该文件夹的 ACL 行，文件夹本身软删（Q119）。

---

## 3. `maxlabel_share_folder_member` —— 文件夹可见成员（新增）

> 显式名单：只有名单内的成员能看见文件夹；OWNER / ADMIN 持席时始终可见（FR-407）。
> 成员退出/被移出企业时**物理删除**其 ACL 行，避免重新加入后继承旧权限。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_share_folder_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `folder_id` bigint NOT NULL COMMENT '文件夹编号，关联 maxlabel_share_folder.id',
  `member_id` bigint NOT NULL COMMENT '可见会员编号，关联 member_user.id',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除（名单变更时物理删除）',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_folder_member`(`tenant_id` ASC, `folder_id` ASC, `member_id` ASC) USING BTREE,
  INDEX `idx_member`(`tenant_id` ASC, `member_id` ASC) USING BTREE
) COMMENT = 'MaxLabel 企业文件夹可见成员';
```

**写入方式**：保存名单 = 同一事务内「删旧行 → 插新行」；名单为空表示仅管理员可见。

---

## 4. `maxlabel_share_content` —— 个人 / 企业内容项（新增）

> 个人空间与唯一所属企业空间共用一张表，用 `scope` 区分。
> 模板 / txt / csv 正文进 `content`；excel / 图片进对象存储，表里存 `file_url`。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_share_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `scope` tinyint NOT NULL COMMENT '空间类型：1 个人 2 企业',
  `owner_member_id` bigint NOT NULL COMMENT '上传者 / 个人空间归属会员编号',
  `org_id` bigint NULL DEFAULT NULL COMMENT '企业编号；个人空间为 NULL',
  `folder_id` bigint NULL DEFAULT NULL COMMENT '文件夹编号；企业空间必填，个人空间为 NULL',
  `content_type` tinyint NOT NULL COMMENT '内容类型：1 模板 2 txt 3 excel 4 csv 5 图片',
  `name` varchar(255) NOT NULL COMMENT '名称（含扩展名）',
  `storage_type` tinyint NOT NULL DEFAULT 1 COMMENT '存储方式：1 文本正文 2 文件地址',
  `content` longtext NULL COMMENT '文本正文（模板 / txt / csv）',
  `file_url` varchar(512) NULL DEFAULT NULL COMMENT '对象存储访问地址（excel / 图片）',
  `mime_type` varchar(128) NULL DEFAULT '' COMMENT 'MIME 类型',
  `size_bytes` bigint NOT NULL COMMENT '字节大小（计入配额）',
  `content_hash` char(64) NULL DEFAULT NULL COMMENT '文本正文 SHA-256（模板去重/校验）',
  `width_mm` decimal(10,2) NULL DEFAULT 0 COMMENT '模板宽度（毫米），非模板为 0',
  `height_mm` decimal(10,2) NULL DEFAULT 0 COMMENT '模板高度（毫米），非模板为 0',
  `category` varchar(64) NULL DEFAULT '' COMMENT '分类（模板沿用）',
  `keywords` varchar(255) NULL DEFAULT '' COMMENT '关键词（模板沿用）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_personal`(`tenant_id` ASC, `scope` ASC, `owner_member_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_enterprise`(`tenant_id` ASC, `org_id` ASC, `folder_id` ASC, `deleted` ASC) USING BTREE,
  INDEX `idx_type`(`tenant_id` ASC, `scope` ASC, `content_type` ASC, `deleted` ASC) USING BTREE
) COMMENT = 'MaxLabel 个人/企业共享内容';
```

**约束**：

| 字段 | 个人空间 | 企业空间 |
|---|---|---|
| `owner_member_id` | 会员本人 | 上传者 |
| `org_id` | NULL | 必填 |
| `folder_id` | NULL | 必填（Q120） |
| 可删除者 | 本人 | 上传者本人 / OWNER·ADMIN（持席） |
| 计入容量 | 个人空间 | 企业 300 MB 池 |

**存量迁移**（保留原 id，可重复执行）：

```sql
INSERT INTO `maxlabel_share_content`
  (`id`, `scope`, `owner_member_id`, `org_id`, `folder_id`, `content_type`, `name`,
   `storage_type`, `content`, `file_url`, `mime_type`, `size_bytes`, `content_hash`,
   `width_mm`, `height_mm`, `category`, `keywords`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT t.`id`, 1, t.`member_id`, NULL, NULL, 1, t.`name`,
       1, t.`content`, NULL, '', t.`size_bytes`, t.`content_hash`,
       t.`width_mm`, t.`height_mm`, t.`category`, t.`keywords`,
       t.`creator`, t.`create_time`, t.`updater`, t.`update_time`, t.`deleted`, t.`tenant_id`
FROM `maxlabel_label_template` t
WHERE NOT EXISTS (SELECT 1 FROM `maxlabel_share_content` c WHERE c.`id` = t.`id`);
```

**容量回填**（迁移后执行一次）：

```sql
INSERT INTO `maxlabel_share_space`
  (`scope`, `owner_id`, `used_bytes`, `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT 1, c.`owner_member_id`, SUM(c.`size_bytes`), 'system', NOW(), 'system', NOW(), b'0', c.`tenant_id`
FROM `maxlabel_share_content` c
WHERE c.`scope` = 1 AND c.`deleted` = b'0'
GROUP BY c.`tenant_id`, c.`owner_member_id`
ON DUPLICATE KEY UPDATE `used_bytes` = VALUES(`used_bytes`);
```

> 企业空间行不需要回填（本期上线时企业内容为空），首次上传时惰性创建。

---

## 5. `maxlabel_platform_content` —— 平台共享内容（新增）

> 管理端直传即公开，不再有审核状态；`status` 只有上架 / 下架两态。
> 与用户内容完全隔离，不占任何个人/企业容量（FR-418）。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_platform_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `content_type` tinyint NOT NULL COMMENT '内容类型：1 模板 2 txt 3 excel 4 csv 5 图片',
  `name` varchar(255) NOT NULL COMMENT '名称（含扩展名）',
  `storage_type` tinyint NOT NULL DEFAULT 1 COMMENT '存储方式：1 文本正文 2 文件地址',
  `content` longtext NULL COMMENT '文本正文（模板 / txt / csv，模板已清洗）',
  `file_url` varchar(512) NULL DEFAULT NULL COMMENT '对象存储访问地址（excel / 图片）',
  `mime_type` varchar(128) NULL DEFAULT '' COMMENT 'MIME 类型',
  `size_bytes` bigint NOT NULL COMMENT '字节大小',
  `content_hash` char(64) NULL DEFAULT NULL COMMENT '文本正文 SHA-256',
  `width_mm` decimal(10,2) NULL DEFAULT 0 COMMENT '模板宽度（毫米）',
  `height_mm` decimal(10,2) NULL DEFAULT 0 COMMENT '模板高度（毫米）',
  `category` varchar(64) NULL DEFAULT '' COMMENT '分类',
  `keywords` varchar(255) NULL DEFAULT '' COMMENT '关键词',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1 上架 0 下架',
  `publisher_user_id` bigint NULL DEFAULT NULL COMMENT '发布管理员编号（system_users.id）',
  `published_time` datetime NULL DEFAULT NULL COMMENT '上架时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_status_type`(`tenant_id` ASC, `status` ASC, `content_type` ASC, `id` DESC) USING BTREE,
  INDEX `idx_category`(`tenant_id` ASC, `status` ASC, `category` ASC) USING BTREE
) COMMENT = 'MaxLabel 平台共享内容';
```

**存量迁移**（只迁已通过审核的历史条目，保留原 id）：

```sql
INSERT INTO `maxlabel_platform_content`
  (`id`, `content_type`, `name`, `storage_type`, `content`, `file_url`, `mime_type`,
   `size_bytes`, `content_hash`, `width_mm`, `height_mm`, `category`, `keywords`,
   `status`, `publisher_user_id`, `published_time`,
   `creator`, `create_time`, `updater`, `update_time`, `deleted`, `tenant_id`)
SELECT s.`id`, 1, s.`name`, 1, s.`content`, NULL, '',
       s.`size_bytes`, NULL, s.`width_mm`, s.`height_mm`, s.`category`, s.`keywords`,
       1, s.`audit_user_id`, s.`published_time`,
       s.`creator`, s.`create_time`, s.`updater`, s.`update_time`, s.`deleted`, s.`tenant_id`
FROM `maxlabel_shared_template` s
WHERE s.`audit_status` = 1 AND s.`deleted` = b'0'
  AND NOT EXISTS (SELECT 1 FROM `maxlabel_platform_content` p WHERE p.`id` = s.`id`);
```

> 历史待审核 / 已驳回条目**不迁移**（会员投稿已关闭，Q115）；旧表保留供对账。

---

## 状态机

```text
内容项（个人/企业）：有效(deleted=0) ──删除──> 已删除(deleted=1)   # 软删、对用户不可恢复，立即释放配额
平台内容：上架(status=1) ──下架──> 下架(status=0) ──上架──> 上架；任一状态 ──删除──> 已删除
文件夹： 有效(deleted=0) ──删除（仅当无内容）──> 已删除(deleted=1) + 物理删除 ACL 行
空间：   首次写入创建 ──> 持续存在（used_bytes 随内容增删回写）
```

## 访问矩阵（服务端裁决）

| 操作 | 个人空间 | 企业空间 | 平台内容 |
|---|---|---|---|
| 浏览 / 下载 | 本人 | 席位未撤销 + 文件夹可见（管理员绕过） | 登录 |
| 上传 | 本人 | 席位未撤销 + 企业启用 + 有未过期未退款批次 + 文件夹可见 | ❌ |
| 删除 | 本人 | 上传者本人 / 管理员（持席）；不要求授权有效，也不要求当前文件夹可见 | ❌ |
| 建/改/删文件夹、改名单 | ❌ | 管理员（持席）+ 企业启用 + 授权有效 | ❌ |
| 上传 / 下架 / 删除平台内容 | ❌ | ❌ | 管理端权限点 |

## 容量口径

| 空间 | 上限来源 | 计算公式 |
|---|---|---|
| 个人 | `edition`：`professional`/`enterprise` → 100 MB；其余 → 10 MB | `SUM(size_bytes) WHERE scope=1 AND owner_member_id=? AND deleted=0` |
| 企业 | 固定 300 MB（授权有效期内可写） | `SUM(size_bytes) WHERE scope=2 AND org_id=? AND deleted=0` |
| 平台 | 不限额、不计入任何空间 | — |

**单位**：全部按字节（`1 MB = 1024 × 1024`）。删除即释放；`used_bytes` 仅作展示缓存，
写事务内以 `SUM` 为准。
