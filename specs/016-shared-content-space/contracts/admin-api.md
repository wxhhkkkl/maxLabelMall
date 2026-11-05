# 管理端接口契约（016 共享内容空间）

> 前缀 `/admin-api`；沿用 yudao 的 `@PreAuthorize("@ss.hasPermission('...')")`。
> 本特性管理端只负责**平台共享内容**；个人/企业空间由会员在桌面端自助使用，管理端不介入。

---

## 1. 平台共享内容分页

### `GET /member/platform-content/page`

权限：`member:platform-content:query`

Query：`pageNo`、`pageSize`（1–200）、`keyword?`、`contentType?`、`category?`、`status?`（1 上架 0 下架）。

```jsonc
{ "code": 0, "data": { "total": 2, "list": [
  { "id": 7001, "contentType": 1, "name": "标准物流面单.msdx",
    "sizeBytes": 12345, "widthMm": 100.0, "heightMm": 150.0,
    "category": "物流", "keywords": "面单,快递",
    "status": 1, "publisherUserId": 1,
    "publishedTime": "2026-10-20T12:00:00+08:00",
    "createTime": "2026-10-20T11:00:00+08:00",
    "thumb": "data:image/svg+xml;base64,…" }
] }, "msg": "" }
```

- 返回全部状态（上架 + 下架），按 `id DESC`。
- `thumb` 仅模板有。

## 2. 详情

### `GET /member/platform-content/get?id=`

权限：`member:platform-content:query`

返回完整字段（含 `storageType`、`content` / `downloadUrl`、`mimeType`），供编辑页回填。
`downloadUrl` 为 5 分钟预签名地址；管理端上传的文件同样不暴露长期 URL。

## 3. 上传 / 新建

### `POST /member/platform-content/upload`

权限：`member:platform-content:create`；`multipart/form-data`：
`contentType`、`name`、`category?`、`keywords?`、`file`。

- 模板：读取为文本 → `MaxLabelTemplateSanitizer.sanitize(content, true)`（剥离数据集行）→ 存 `content`；
- txt / csv：读取为 UTF-8 文本存 `content`；
- excel / 图片：经 `FileApi.createFile` 存 `file_url`；
- 单文件 ≤ 16 MB；扩展名白名单与 C 端一致；
- 新建即 `status=1`，记录 `publisher_user_id = 当前管理员`、`published_time = now`。
- 响应：`{ "id": 7001 }`。

### `POST /member/platform-content/create`

权限：`member:platform-content:create`；用于纯文本录入（JSON）：
`{ contentType, name, content, category?, keywords? }`。

- 仅 `contentType=1/2/4`；模板同样清洗并剥离数据集行。

## 4. 编辑

### `PUT /member/platform-content/update`

权限：`member:platform-content:update`

```jsonc
{ "id": 7001, "name": "标准物流面单-新版.msdx", "content": "{…}",
  "category": "物流", "keywords": "面单,快递" }
```

- `content` 仅对 `contentType=1/2/4` 可改；excel/图片只能改名称、分类、关键词。
- 模板正文改动后重新清洗与计算 `size_bytes` / `content_hash`。

## 5. 上下架

### `PUT /member/platform-content/update-status`

权限：`member:platform-content:update`

```jsonc
{ "id": 7001, "status": 0 }
```

- `1` 上架（记录 `published_time`），`0` 下架；下架后 C 端立即不可见。
- 重复设置同状态幂等。

## 6. 删除

### `DELETE /member/platform-content/delete?id=`

权限：`member:platform-content:delete`

- 软删；已下载副本不受影响。
- 幂等：已删除返回 `true`。

---

## 7. 旧端点处置

| 旧端点 | 处置 |
|---|---|
| `GET /member/shared-template/page` | **保持原样**（只查旧表），仅作为回滚兜底；新页面不再调用 |
| `GET /member/shared-template/get` | 保持原样 |
| `PUT /member/shared-template/offline` | 保持原样（只影响旧表） |
| `PUT /member/shared-template/approve` | 保持原样；但不再有 UI 入口 |
| `PUT /member/shared-template/reject` | 保持原样；但不再有 UI 入口 |

> 旧「共享模板审核」菜单与本页删除；历史待审核/已驳回条目留在旧表，不做展示（Q115）。
> 旧端点保留一个发布周期，避免回滚时还要恢复被删掉的接口；新平台内容页只操作新表。

## 8. 权限点与菜单

| 点 | 类型 | 说明 |
|---|---|---|
| `member:platform-content:query` | 菜单（页面） | 平台共享内容页 |
| `member:platform-content:create` | 按钮 | 上传 / 新建 |
| `member:platform-content:update` | 按钮 | 编辑 / 上下架 |
| `member:platform-content:delete` | 按钮 | 删除 |

菜单路径：`/member/platform-content`，组件 `member/platformContent/index`，
挂在会员管理目录下；SQL 与 027 的幂等写法一致。

## 9. 校验与错误

| 场景 | 结果 |
|---|---|
| 非管理员调用 | `@PreAuthorize` 403 |
| 类型/扩展名不支持 | `MEMBER_SHARE_CONTENT_TYPE_INVALID` |
| 文件 > 16 MB | `MEMBER_SHARE_CONTENT_TOO_LARGE` |
| 模板 JSON 非法 | `MEMBER_SHARE_CONTENT_INVALID` |
| 记录不存在 | `MEMBER_PLATFORM_CONTENT_NOT_FOUND` |
| 旧审核动作 | `MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED` |
