# 管理端接口契约（013 软件与驱动分发）

> 前缀：只走 `/admin-api`（宪法）。所有接口都标 `@PreAuthorize`，权限串须同时登记进 `system_menu`（`025` 迁移）。

---

## 1. 软件分类

| 接口 | 权限 | 说明 |
|---|---|---|
| `GET /admin-api/software/category/page` | `software:category:query` | 分页，支持 `name` 模糊、`status` |
| `GET /admin-api/software/category/list` | `software:category:query` | 全量（下拉用） |
| `POST /admin-api/software/category/create` | `software:category:create` | `{name, sort, status}` |
| `PUT /admin-api/software/category/update` | `software:category:update` | `{id, name, sort, status}` |
| `DELETE /admin-api/software/category/delete?id=` | `software:category:delete` | 软删除；**分类下仍有未删除的软件时拒绝** |

---

## 2. 软件

| 接口 | 权限 | 说明 |
|---|---|---|
| `GET /admin-api/software/info/page` | `software:info:query` | 支持 `categoryId`、`type`、`status`、`title` |
| `GET /admin-api/software/info/get?id=` | `software:info:query` | 含版本列表（**全部**版本，含历史与已下架） |
| `POST /admin-api/software/info/create` | `software:info:create` | `{categoryId, title, introduction, type, showInSupport, status}` |
| `PUT /admin-api/software/info/update` | `software:info:update` | 同上 + `id` |

> 删除软件用 `DELETE /admin-api/software/info/delete?id=`（`software:info:delete`），软删除（Q87）。

---

## 3. 版本与文件

### 3.1 上传：复用 infra 既有预签名端点（不再新增）

**不新增** `/software/version/presign`。管理端直接调用 infra 既有接口：

```
GET /admin-api/infra/file/presigned-url?name={fileName}&directory={directory}
→ { configId, path, uploadUrl, url }
```

- `directory` 用 `software/{platform}-{arch}/{version}`（由 `SoftwareStorageService#buildDirectory` 生成）
- 拿到 `uploadUrl` 后浏览器 **PUT 直传 OSS**；`path` / `url` 回填给 3.2 的登记接口
- 存储不支持预签名时该端点会报错，前端降级为既有 `POST /admin-api/infra/file/upload`（服务端中转）

> R77 复核结论：infra 早已具备 `S3FileClient#presignPutUrl` 与上述端点，无需跨模块新增 RPC。

### 3.2 `POST /admin-api/software/version/register` —— 上传后登记

权限：`software:version:create`

```jsonc
{ "softwareId": 10, "version": "1.0.23", "platform": "windows", "arch": "x64",
  "fileName": "MaxLabel-Setup-1.0.23.exe", "fileSize": 264241152,
  "sha512": "…", "objectKey": "software/win-x64/1.0.23/MaxLabel-Setup-1.0.23.exe",
  "fileUrl": "https://img.yuwangchenfa.com/software/win-x64/1.0.23/MaxLabel-Setup-1.0.23.exe",
  "releaseNotes": "…", "releaseTime": "2026-10-27T10:00:00+08:00" }
```

- 建出来的是**草稿**（`status=1`）；必须再调 3.3 才算发布（FR-337）。
- `objectKey` 与 `fileUrl` 直接用 3.1 返回的 `path` 与 `url`。
- 校验：版本号格式与唯一性（FR-333）、`fileUrl` 必须 https 且属于已配置存储域名、`sha512` 长度合法（FR-338）。
- **服务端 MUST 回源 HEAD 一次 `fileUrl` 确认文件存在且 `Content-Length == fileSize`**，否则拒绝登记（防止「有记录、点下载 404」）。
- ⚠️ **服务端不校验 sha512 与文件内容是否一致**（FR-338 已按此收窄）：那需要下载整个安装包。校验和由上传端计算并登记，最终由**客户端在下载完成后**验证（SC-131）。`sha512` 的用途是「防篡改与防截断」，不是「防运营填错」。

### 3.3 `POST /admin-api/software/version/publish?id=&current=false`

权限：`software:version:publish`

- `current=true`：同事务内「先清后置」（data-model §3），撞唯一键返回 `SOFTWARE_VERSION_CONFLICT`。
- `current=false`：仅把 `status` 置 0，不改变当前发布。

### 3.4 其它

| 接口 | 权限 | 说明 |
|---|---|---|
| `GET /admin-api/software/version/page?softwareId=` | `software:version:query` | 含历史与已下架 |
| `PUT /admin-api/software/version/unpublish?id=` | `software:version:publish` | 下架：`status=2`，若是当前发布同时清 `current_flag` |
| `DELETE /admin-api/software/version/delete?id=` | `software:version:delete` | 软删除（Q87），文件不删 |

> 版本**不可编辑版本号/平台/架构/文件**（要改就重新登记一个新版本），避免已发布清单与文件脱节。

---

## 4. 前端上传时序（管理端）

```text
1. 选择文件 → 计算 sha512（Web Crypto，分片读取，不整包进内存）
2. POST /version/presign        → uploadUrl / objectKey / publicUrl
3. PUT uploadUrl（直传 OSS）    → 200
4. POST /version/register       → 服务端 HEAD 校验 → 建草稿
5. POST /version/publish?current=true → 成为当前发布
```

失败处理：第 3 步失败可直接重试（同一 `objectKey` 覆盖写）；第 4 步失败不产生记录。

---

## 5. 权限与菜单（`025` 迁移）

| 菜单 / 按钮 | permission |
|---|---|
| 软件分发（页面） | `software:category:query` |
| 分类增删改 | `software:category:create` / `:update` / `:delete` |
| 软件增删改查 | `software:info:query` / `:create` / `:update` / `:delete` |
| 版本查/建/删 | `software:version:query` / `:create` / `:delete` |
| 发布 / 下架 | `software:version:publish` |

> ⚠️ 权限串没登记成 `system_menu` 行时，`v-hasPermi` 会把按钮从 DOM 删掉，连超管也看不到（012 前车之鉴）。
> 共 **12** 个权限点：category 4 + info 4 + version 3 + publish 1。

---

## 6. 错误码

| 模块 | 名称 | 语义 |
|---|---|---|
| product | `SOFTWARE_CATEGORY_NOT_EXISTS` | 分类不存在 |
| product | `SOFTWARE_NOT_EXISTS` | 软件不存在 |
| product | `SOFTWARE_VERSION_NOT_EXISTS` | 版本不存在 |
| product | `SOFTWARE_VERSION_DUPLICATE` | 同平台同架构下版本号重复 |
| product | `SOFTWARE_VERSION_NOT_PUBLISHED` | 未发布版本不能设为当前发布 |
| product | `SOFTWARE_VERSION_CONFLICT` | 并发设置当前发布冲突，请刷新后重试 |
| product | `SOFTWARE_FILE_INVALID` | 文件地址/大小/校验和不合法 |

> 前端 MUST 用 `code` 分支处理，不能依赖 `msg` 文案（文案会改，`code` 不会）。
