# 数据模型（018 LabelShop 模板导入）

**一句话**：新增 **2 张表**（导入任务、导入明细）；导入的内容直接写入 016 的
`maxlabel_share_content`（个人 / 企业空间），**不改 016 的 5 张表结构**。

迁移脚本：`yudao-cloud/sql/mysql/038-labelshop-template-import.sql`。所有表带 `tenant_id`、
`creator/create_time/updater/update_time/deleted`（`BaseDO` 约定），时间语义 Asia/Shanghai。

---

## 1. `maxlabel_labelshop_import_task` —— 导入任务（新增）

> 一行 = 一次「某企业从某 LabelShop 账号导入」的任务。密码不落库，只存来源账号掩码。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_labelshop_import_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `subject_type` tinyint NOT NULL DEFAULT 1 COMMENT '导入对象：1 企业 2 个人会员',
  `org_id` bigint NULL DEFAULT NULL COMMENT '企业编号；个人会员任务为 NULL',
  `owner_member_id` bigint NOT NULL COMMENT '导入目标会员：企业任务为 OWNER，个人任务为目标会员',
  `folder_id` bigint NULL DEFAULT NULL COMMENT '本次新建的企业文件夹编号（登录/枚举成功后回填）',
  `folder_name` varchar(64) NULL DEFAULT NULL COMMENT '计划新建的文件夹名称；个人任务为 NULL',
  `source_account_masked` varchar(128) NOT NULL COMMENT 'LabelShop 来源账号掩码',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 待执行 1 进行中 2 已完成 3 已失败 4 已中断',
  `phase` varchar(32) NOT NULL DEFAULT '' COMMENT '阶段：LOGIN/LIST/IMPORT/FINISH',
  `total_count` int NOT NULL DEFAULT 0 COMMENT '枚举出的模板总数',
  `processed_count` int NOT NULL DEFAULT 0 COMMENT '已处理数（成功+跳过+失败）',
  `success_count` int NOT NULL DEFAULT 0 COMMENT '成功数',
  `skip_count` int NOT NULL DEFAULT 0 COMMENT '跳过数（幂等已存在）',
  `fail_count` int NOT NULL DEFAULT 0 COMMENT '失败数',
  `personal_bytes` bigint NOT NULL DEFAULT 0 COMMENT '写入个人空间字节数',
  `enterprise_bytes` bigint NOT NULL DEFAULT 0 COMMENT '写入企业空间字节数',
  `error_message` varchar(500) NULL DEFAULT NULL COMMENT '任务级失败原因（含来源级失败摘要；不含密码）',
  `operator_id` bigint NULL DEFAULT NULL COMMENT '后台操作人 system_users.id',
  `operator_name` varchar(64) NULL DEFAULT '' COMMENT '后台操作人昵称快照',
  `started_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `finished_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `heartbeat_time` datetime NULL DEFAULT NULL COMMENT '心跳时间（每批更新）',
  `running_org_id` bigint NULL DEFAULT NULL COMMENT '进行中企业编号；终态置 NULL，用于并发唯一键',
  `running_member_id` bigint NULL DEFAULT NULL COMMENT '进行中个人会员编号；终态置 NULL，用于并发唯一键',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_running_org`(`tenant_id` ASC, `running_org_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_running_member`(`tenant_id` ASC, `running_member_id` ASC) USING BTREE,
  INDEX `idx_org_id`(`tenant_id` ASC, `org_id` ASC, `id` DESC) USING BTREE,
  INDEX `idx_member_id`(`tenant_id` ASC, `subject_type` ASC, `owner_member_id` ASC, `id` DESC) USING BTREE,
  INDEX `idx_status`(`tenant_id` ASC, `status` ASC, `update_time` ASC) USING BTREE
) COMMENT = 'MaxLabel LabelShop 导入任务';
```

**约束与语义**：

- `subject_type`：1 企业 / 2 个人会员。个人任务由管理端会员详情发起（Q145），只导「我的模板库」，
  写入该会员的 016 个人空间，不创建文件夹。
- `uk_running_org` / `uk_running_member`：MySQL 唯一索引允许多个 `NULL`，因此历史终态任务不冲突；
  同一租户同一企业（或同一会员）只允许一个 `PENDING/RUNNING` 任务；两类任务互不占用名额。
- `owner_member_id` 在任务开始时快照；企业任务为目标 OWNER，个人任务为目标会员。
  导入过程中 OWNER 被转交/移出，不改写已完成的任务口径。
- `source_account_masked` 只存掩码；密码、token 不入表。
- `folder_name` 在创建任务时算好；`folder_id` 在登录 + 枚举成功后、写入第一条内容前创建并回填。
  登录/枚举失败的任务不创建空文件夹。持席成员 ACL 快照在任务开始时读取并随异步任务传入，
  不落任务表（文件夹 ACL 行本身即结果）。若 `folder_name` 与既有名称冲突，追加 `_2` 等后缀，
  MUST NOT 覆盖或复用既有文件夹。
- `error_message` 只存可展示的原因（不含密码）；单条失败放明细；来源级失败
  （STS / 某公司列表 / 个人或组分类树）也记在这里。
- 任务行**不删除**（审计）；`deleted` 仅保留框架约定。

---

## 2. `maxlabel_labelshop_import_item` —— 导入明细（新增）

> 一行 = 一个来源模板在一次任务中的处理结果；同时承担幂等键、来源追溯与失败清单。

```sql
CREATE TABLE IF NOT EXISTS `maxlabel_labelshop_import_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `task_id` bigint NOT NULL COMMENT '任务编号，关联 maxlabel_labelshop_import_task.id',
  `source_library` tinyint NOT NULL COMMENT '来源库：12 我的模板库 13 组模板库 1 企业/组织模板',
  `source_ref` varchar(64) NOT NULL COMMENT 'LabelShop 模板 UUID',
  `source_category` varchar(255) NOT NULL DEFAULT '' COMMENT '来源分类路径（如 服装标签/上衣）',
  `source_name` varchar(255) NOT NULL DEFAULT '' COMMENT '来源模板名称（原始）',
  `target_scope` tinyint NOT NULL COMMENT '目标空间：1 个人 2 企业',
  `target_owner_id` bigint NOT NULL COMMENT '目标归属：个人为 OWNER 会员编号，企业为 org_id',
  `content_id` bigint NULL DEFAULT NULL COMMENT '写入的 maxlabel_share_content.id',
  `format` varchar(16) NOT NULL DEFAULT 'lsdx' COMMENT '格式：lsdx / cloud-json',
  `editable` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否可编辑：lsdx=1，企业私有 JSON=0',
  `size_bytes` bigint NOT NULL DEFAULT 0 COMMENT '写入字节数',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0 待处理 1 成功 2 跳过 3 失败',
  `error_message` varchar(500) NULL DEFAULT NULL COMMENT '失败原因（不含密码）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_source`(`tenant_id` ASC, `task_id` ASC, `source_ref` ASC) USING BTREE,
  INDEX `idx_target_source`(`tenant_id` ASC, `target_scope` ASC, `target_owner_id` ASC, `source_ref` ASC) USING BTREE,
  INDEX `idx_task_status`(`tenant_id` ASC, `task_id` ASC, `status` ASC, `id` ASC) USING BTREE,
  INDEX `idx_task_library`(`tenant_id` ASC, `task_id` ASC, `source_library` ASC) USING BTREE
) COMMENT = 'MaxLabel LabelShop 导入明细';
```

**幂等规则**：

1. 每个任务内以 `(tenant_id, task_id, source_ref)` 唯一；跨任务以
   `(tenant_id, target_scope, target_owner_id, source_ref)` 查询历史成功明细。
2. 命中历史成功明细时，再查其 `content_id` 对应的 `maxlabel_share_content`：
   存在 → 当前任务明细 `status=2 跳过`（`content_id` 指向既有内容）；已删除/不存在 → 重新导入，
   当前任务插入新明细并写入新内容。
3. 同一来源模板在不同目标空间（个人 / 企业）可以各有一行；本期来源库互斥，通常不会发生。
4. 明细的 `source_category/source_name` 保留原始值；内容行的 `name/category` 可做长度与非法字符处理。
5. 每个任务保留自己的明细，重复导入不会覆盖历史任务的失败清单。

---

## 3. 来源 → 目标映射

| 来源库 | `source_library` | 分类树 / 列表 | `target_scope` | `target_owner_id` | `format` | `editable` |
|---|---|---|---|---|---|---|
| 我的模板库 | 12 | `@TYPE=0,@MODE=1`；`@OWNTYPE=0→12→11`（首个非空） | 1 个人 | OWNER 会员编号 | `lsdx`（`@OWNTYPE=0` 的云端 JSON 先转 `.lsdx`） | 1 |
| 组模板库 | 13 | `@TYPE=12,@MODE=4`；`@OWNTYPE=12→11`（首个非空） | 2 企业 | `org_id` | `lsdx` | 1 |
| 企业/组织模板 | 1 | `@CATEID=0,@OWNTYPE=1,@UID=公司ID` + OSS | 2 企业 | `org_id` | `cloud-json` | 0 |
| 公共模板库 | 14 | 不读取 | — | — | — | — |
| 用户模板库 | 其他 | 不读取 | — | — | — | — |

> **个人会员代导（Q145）**：只读取「我的模板库」（12），`target_owner_id` 为目标会员编号，
> 只写个人空间；组模板库与企业/组织模板不读取、不写入。
>
> **分类树完整性（2026-11-13 修正）**：保留 `Cate_ID=0`（CATE0，记为「我的模板」）与
> `Cate_ID=-1`（LabelShop5）；分类 `Own_Type=11` 时只取 `@OWNTYPE=11`，`0`/缺省取
> `0→12→11`，其他取 `12→11`。
>
> **云端模板转换与个人目录（2026-11-16）**：`@OWNTYPE=0` 的云端 JSON 导入时按 `cloud_to_lsdx`
> 转 `.lsdx`，转换失败记模板级失败；个人内容按来源分类路径建**多层文件夹**
> （`maxlabel_share_folder.scope=1` + `owner_member_id` + `parent_id`，`org_id` 为空），
> 同名路径复用；企业内容仍只落在单层 `<时间>导入` 文件夹。

**分类路径**：

- 个人 / 组：沿 `Cate_ID → Parent_ID → Name` 拼路径；未分类条目记 `未分类`。
- 企业/组织：`企业/组织模板/<公司名>`（公司名来自登录返回 `datas.companys`）。
- 内容行 `category` ≤ 64 字符（超长取叶子）；完整路径放明细 `source_category`（≤255）。

---

## 4. 写入 016 内容行

导入内容写入既有 `maxlabel_share_content`，字段口径：

| 字段 | 个人空间 | 企业空间 |
|---|---|---|
| `scope` | 1 | 2 |
| `owner_member_id` | OWNER | OWNER（Q143） |
| `org_id` | NULL | 当前企业 id |
| `folder_id` | NULL | 本次新建的 `<时间>导入` 文件夹 id |
| `content_type` | 1（模板） | 1（模板） |
| `name` | 原名去重扩展名 + 非法字符替换 + 截断后加 `.lsdx` / `.json`；空名回退 `source_ref` | 同左 |
| `storage_type` | 1（文本） | 1（文本） |
| `content` | 原始正文（Q138 不清洗） | 同左 |
| `mime_type` | `text/xml` / `application/json` | 同左 |
| `size_bytes` | UTF-8 字节 | 同左 |
| `content_hash` | SHA-256 | 同左 |
| `width_mm/height_mm` | `.lsdx` 解析；JSON 记 0 | 同左 |
| `category` | 来源分类路径（≤64） | 来源分类路径（含公司名，≤64） |
| `keywords` | 空 | 空 |

**删除权**：内容行 `owner_member_id=OWNER`，按 016 `canDelete`：
OWNER / ADMIN（持席）可删，普通成员不可删。

**单条上限**：仍沿用 016 的单条 16 MB（`ShareProperties.contentMaxBytes`）；超过则该条
`status=3 失败`（`超过 16 MB`），不写入。容量上限才按 Q136 放开。

---

## 5. 状态机

```text
任务：
  0 待执行 ──提交线程池──> 1 进行中 ──全部处理完──> 2 已完成
                               │
                               ├──登录/枚举致命失败──> 3 已失败
                               ├──中途企业停用/授权到期──> 3 已失败（保留已写入）
                               └──服务重启/线程中断──> 4 已中断

明细：
  0 待处理 ──下载+写入成功──> 1 成功
           ├──幂等已存在────> 2 跳过
           └──正文为空/下载失败/写入失败──> 3 失败
```

- 任务终态必须清空 `running_org_id`，允许同企业再次发起。
- 任务 `2/3/4` 均为终态；`4 已中断` 允许运营直接重新发起（新任务、幂等跳过已导入项）。
- 单条失败不改变任务终态为失败：只要任务正常跑完，任务为 `2 已完成`，`fail_count > 0`。
- **中途停用/到期**：每条写入前重查企业启用与授权；失效即停止新增写入，任务 `3 已失败`，
  已写入内容与文件夹保留。
- **来源级失败**：STS / 某公司列表 / 个人或组分类树失败 → 该来源不产生明细，原因写任务
  `error_message`，不计入 `fail_count`；其余来源继续。全部来源失败 → 任务 `3 已失败`。

---

## 6. 并发、恢复与保留

- **并发**：`uk_running_org` 兜底；`start` 冲突时返回 `MEMBER_LABELSHOP_IMPORT_RUNNING`。
- **恢复**：启动 `ApplicationRunner` 扫描 `status IN (0,1)` 的任务，置 `4 已中断`、
  `running_org_id=NULL`、`error_message='服务重启导致任务中断'`；已写入内容保留，重新发起时按
  `(target_scope, target_owner_id, source_ref)` 查历史成功明细并跳过。
- **心跳**：每批（20 条）更新 `heartbeat_time`；为后续多实例部署预留。
- **写入闸门**：每条写入前重查 `org.status=0` 且存在未过期授权；不满足即停止新增写入，
  任务置 `FAILED`，保留已写入内容（spec SC-182）。
- **保留**：任务与明细永久保留（审计与重跑依据）；导入的内容按 016 的删除/只读规则由用户管理。
- **不迁移**：缩略图（016 无字段）、数据源、图片、打印记录、平台内容。

---

## 7. 与既有表的关系

```text
maxlabel_org (1) ──< maxlabel_labelshop_import_task (N)
                          │
                          ├── folder_id ──> maxlabel_share_folder (1)
                          └── id ──< maxlabel_labelshop_import_item (N)
                                              │
                                              └── content_id ──> maxlabel_share_content (0..1)
maxlabel_org_seat ──> 文件夹可见名单（导入时快照，写入 maxlabel_share_folder_member）
```

- 不新增外键（沿用项目既有无外键风格），由应用层校验。
- 不修改 `maxlabel_share_content` / `maxlabel_share_folder` / `maxlabel_share_folder_member` 结构。
