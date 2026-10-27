# C 端 / 桌面端接口契约（013 软件与驱动分发）

> 前缀：只走 `/app-api`（宪法）。以下接口**全部公开**，不要求登录（FR-342）。
> 统一响应 `{ "code": 0, "data": …, "msg": "" }`；请求头沿用既有 `tenant-id`。

---

## 1. 服务支持页

### 1.1 `GET /app-api/software/category/list`

只返回**启用**且下挂至少一个可展示软件的**启用分类**，按 `sort` 升序。

```jsonc
{ "code": 0, "data": [
  { "id": 1, "name": "标签打印软件", "sort": 0 },
  { "id": 2, "name": "打印驱动", "sort": 10 }
], "msg": "" }
```

### 1.2 `GET /app-api/software/list?categoryId=1`

返回该分类下 `show_in_support=1` 且启用的软件。

```jsonc
{ "code": 0, "data": [
  { "id": 10, "categoryId": 1, "title": "MaxLabel 标签打印软件",
    "introduction": "…", "type": 1,
    "latestVersion": "1.0.23", "platforms": ["windows", "macos"] }
], "msg": "" }
```

> 列表**只给最新版本号**，不返回历史版本；历史版本仅在详情与后台可见。
> **本期不分页**：软件数量在几十款量级，一次返回该分类下全部可展示项；超过 200 条时再评估分页。

### 1.3 `GET /app-api/software/get?id=10`

```jsonc
{ "code": 0, "data": {
  "id": 10, "title": "MaxLabel 标签打印软件", "introduction": "…", "type": 1,
  "versions": [
    { "version": "1.0.23", "platform": "windows", "arch": "x64",
      "fileName": "MaxLabel-Setup-1.0.23.exe", "fileSize": 264241152,
      "releaseTime": "2026-10-27T10:00:00+08:00",
      "downloadUrl": "https://img.yuwangchenfa.com/software/win-x64/1.0.23/MaxLabel-Setup-1.0.23.exe" }
  ]
}, "msg": "" }
```

| 规则 | 说明 |
|---|---|
| `versions` 只含 `current_flag=1` 且 `status=0` | Q84 / FR-343 |
| 每个平台 × 架构最多一条 | Q86 |
| 软件不存在、已停用、已软删除或 `show_in_support=0` | `code=SOFTWARE_NOT_EXISTS`，**不返回内容**（不区分「不存在」与「不可见」，避免枚举） |
| 地址 | 公开永久直链，不带签名与过期（Q85） |

> **字段名映射**（同一个对象，四层不同叫法，勿当成不同字段）：
> 数据库 `file_url` → 管理端预签名响应 `publicUrl` → C 端详情 `downloadUrl` → `latest.yml` 的 `path` / `files[].url`。

---

## 2. 桌面端更新

### 2.1 `GET /app-api/software/update/check`

客户端**上报**当前版本与平台架构，服务端**判定**（Q89 / FR-334 / FR-347）。

| 参数 | 必填 | 说明 |
|---|---|---|
| `currentVersion` | 是 | 客户端当前版本，格式 `x.y.z` |
| `platform` | 是 | `windows` / `macos` / `linux` |
| `arch` | 是 | `x64` / `arm64` / `x86` |
| `softwareId` | 否 | 缺省时的取值**必须确定且可复现**：取该租户下 `type=1`、启用、且存在当前发布的软件中 `id` 最小的一款；若同时存在多款，额外记录一条告警日志。客户端 SHOULD 显式传入自己所属的软件编号 |

**有更新**

```jsonc
{ "code": 0, "data": {
  "hasUpdate": true,
  "latestVersion": "1.0.23",
  "sha512": "…", "size": 264241152,
  "feedUrl": "https://img.yuwangchenfa.com/software/win-x64/1.0.23/",
  "releaseNotes": "…"
}, "msg": "" }
```

**无更新**（已是最新 / 客户端版本更高 / 平台不匹配 / 无发布 / 软件停用）

```jsonc
{ "code": 0, "data": { "hasUpdate": false }, "msg": "" }
```

| 规则 | 说明 |
|---|---|
| 客户端版本 **≥** 当前发布 | `hasUpdate=false`，**不返回** `feedUrl`（Q90，防降级） |
| 平台/架构无当前发布 | `hasUpdate=false`（FR-350） |
| `currentVersion` 格式非法 | `hasUpdate=false`，不报错（Edge Case） |
| 响应体积 | 只含元数据，**不含文件本体**，< 10 KB（SC-126） |

### 2.2 `GET /app-api/software/feed/{platform}/{arch}/latest.yml`

按当前发布动态生成 electron-updater 清单（R82）。

```yaml
version: 1.0.23
files:
  - url: https://img.yuwangchenfa.com/software/win-x64/1.0.23/MaxLabel-Setup-1.0.23.exe
    sha512: <base64>
    size: 264241152
path: https://img.yuwangchenfa.com/software/win-x64/1.0.23/MaxLabel-Setup-1.0.23.exe
sha512: <base64>
releaseDate: '2026-10-27T02:00:00.000Z'
releaseNotes: |
  …
```

| 规则 | 说明 |
|---|---|
| `Content-Type` | `text/yaml; charset=utf-8` |
| 无当前发布 | `404` |
| `path` | OSS 绝对直链；electron-updater 对绝对 URL 原样使用，**不经过应用服务器**（FR-335） |
| 缓存 | `Cache-Control: no-cache`，避免发布后客户端拿到旧清单 |

---

## 3. 错误码

| 模块 | 名称 | 语义 |
|---|---|---|
| product | `SOFTWARE_CATEGORY_NOT_EXISTS` | 分类不存在 |
| product | `SOFTWARE_NOT_EXISTS` | 软件不存在 |
| product | `SOFTWARE_VERSION_NOT_EXISTS` | 版本不存在 |
| product | `SOFTWARE_VERSION_DUPLICATE` | 同平台同架构版本号重复 |
| product | `SOFTWARE_VERSION_NOT_PUBLISHED` | 未发布版本不能设为当前发布 |
| product | `SOFTWARE_VERSION_CONFLICT` | 并发设置当前发布冲突，请重试 |
| product | `SOFTWARE_FILE_INVALID` | 文件地址/大小/校验和不合法 |
