# 数据模型：软件与驱动分发

**Feature**: `013-software-distribution` | **Date**: 2026-10-27

三张表，全部继承 `BaseDO`（`creator/create_time/updater/update_time/deleted`）并带 `tenant_id`。
表名前缀用 `maxlabel_`，与 012 的 `maxlabel_org_*` 保持一致。

---

## 1. `maxlabel_software_category` —— 软件分类

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK, auto | |
| `name` | varchar(64) | NOT NULL | 分类名称 |
| `sort` | int | NOT NULL DEFAULT 0 | 升序 |
| `status` | tinyint | NOT NULL DEFAULT 0 | 0 启用 / 1 停用 |
| `tenant_id` | bigint | NOT NULL | |

**索引**：`idx_tenant_status (tenant_id, status, sort)`

**规则**：停用分类下的软件**不进入**服务支持页（FR-330 / US50-AC3），但不影响客户端更新（更新按软件维度查询，与分类无关）。

---

## 2. `maxlabel_software` —— 软件 / 驱动

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK, auto | |
| `category_id` | bigint | NOT NULL | 所属分类 |
| `title` | varchar(128) | NOT NULL | 标题 |
| `introduction` | varchar(1024) | NULL | 介绍 |
| `type` | tinyint | NOT NULL | 1 桌面端软件 / 2 打印驱动 |
| `show_in_support` | bit(1) | NOT NULL DEFAULT 0 | 是否在服务支持页展示 |
| `status` | tinyint | NOT NULL DEFAULT 0 | 0 启用 / 1 停用 |
| `tenant_id` | bigint | NOT NULL | |

**索引**：`idx_tenant_category (tenant_id, category_id, status)`

**规则**：

- `type=1` 的软件才会参与客户端更新判定；`type=2` 的驱动只用于下载。
- `show_in_support=0` 只影响服务支持页，**不影响**客户端更新（FR-331 / US50-AC2）。

---

## 3. `maxlabel_software_version` —— 软件版本

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | bigint | PK, auto | |
| `software_id` | bigint | NOT NULL | 所属软件 |
| `version` | varchar(32) | NOT NULL | 语义化三段式，如 `1.0.23` |
| `platform` | varchar(16) | NOT NULL | `windows` / `macos` / `linux` |
| `arch` | varchar(16) | NOT NULL | `x64` / `arm64` / `x86` |
| `file_name` | varchar(255) | NOT NULL | 原始文件名 |
| `file_size` | bigint | NOT NULL | 字节数 |
| `sha512` | varchar(128) | NOT NULL | Base64 编码的 sha512 |
| `file_url` | varchar(512) | NOT NULL | OSS 公开永久直链 |
| `object_key` | varchar(512) | NOT NULL | OSS 对象键（便于运维核对/清理） |
| `release_notes` | varchar(2000) | NULL | 发布说明 |
| `release_time` | datetime | NULL | 发布日期（展示用） |
| `current_flag` | tinyint | NULL | 1 = 当前发布；NULL = 非当前 |
| `status` | tinyint | NOT NULL DEFAULT 1 | 0 已发布 / 1 草稿（未确认上传）/ 2 已下架 |
| `tenant_id` | bigint | NOT NULL | |

**唯一键与索引**：

- `uk_version (tenant_id, software_id, platform, arch, version)` —— 同组合版本号不重复（FR-333）
- `uk_current (tenant_id, software_id, platform, arch, current_flag)` —— 「当前发布」唯一（FR-352 / R79）
  - 依赖 MySQL「唯一索引对 NULL 不生效」的特性：非当前行的 `current_flag` 为 `NULL`，可无限多；`1` 只能有一行。
- `idx_software (tenant_id, software_id, status)`

**状态机**

```text
       登记（上传确认）
  ┌───────────────┐
  ▼               │
草稿(1) ──发布──▶ 已发布(0) ──下架──▶ 已下架(2)
  │                                   │
  └────────软删除（deleted=1，任意状态可删）──────┘
```

- **草稿**：已登记元数据但未确认文件可用；**不可**被客户端或服务支持页消费，也**不可**设为当前发布。
- **已发布**：可被设为当前发布；未设 current 时作为历史版本，仅管理端可见。
- **已下架**：从所有对外入口消失，但 `file_url` 仍可下载（FR-345）。
- **软删除**：记录从所有列表消失，`file_url` 仍可下载（FR-353 / Q87）。

**「当前发布」切换（事务内）**

```sql
UPDATE maxlabel_software_version
   SET current_flag = NULL
 WHERE tenant_id = ? AND software_id = ? AND platform = ? AND arch = ? AND current_flag = 1;

UPDATE maxlabel_software_version
   SET current_flag = 1
 WHERE id = ? AND status = 0;   -- 只有「已发布」才能成为当前发布
```

并发下后者可能撞 `uk_current`，转成业务错误「该组合已有当前发布版本，请刷新后重试」。

---

## 4. 派生数据（不落库）

### 4.1 更新判定结果

由「软件（type=1、status=0） + 该 platform/arch 的 `current_flag=1` 版本」计算：

```jsonc
{
  "hasUpdate": true,          // 服务端比较：客户端上报版本 < 当前发布版本
  "latestVersion": "1.0.23",
  "sha512": "…", "size": 264241152,
  "feedUrl": "https://img.yuwangchenfa.com/software/win-x64/1.0.23/",
  "releaseNotes": "…"
}
```

- 客户端版本 ≥ 当前发布 → `hasUpdate=false`，不返回 `feedUrl`（Q90）。
- 平台/架构无匹配 → `hasUpdate=false`（FR-350）。
- 软件停用或被软删除 → `hasUpdate=false`。

### 4.2 `latest.yml`

按版本记录渲染，字段见 R82；`path` / `files[].url` 均为 OSS 绝对直链。

---

## 5. 校验规则汇总

| 规则 | 来源 |
|---|---|
| `version` 必须匹配 `^\d+\.\d+\.\d+$` | FR-333 / Assumptions |
| 同 `(software, platform, arch)` 下版本号唯一 | FR-333 |
| 同 `(software, platform, arch)` 下至多一个 `current_flag=1` | FR-352 / Q86 |
| 只有 `status=0`（已发布）可设当前发布 | 状态机 |
| `file_size > 0`、`sha512` 非空且长度符合 Base64(sha512)=88 位 | FR-338 |
| `file_url` 必须是 `https://` 且域名属于已配置的存储域名 | FR-335 / Q85 |
| 删除一律软删除 | FR-353 / Q87 |
