# C 端接口契约（016 共享内容空间）

> 前缀 `/app-api`；仅接受会员登录态（Bearer），不要求有效订阅。
> 企业空间额外要求席位；平台内容是只读目录。所有权限由服务端裁决。

---

## 1. 空间概览

### `GET /member/share-space/overview`

```jsonc
{ "code": 0, "data": {
  "personal": {
    "edition": "standard",          // standard / professional / enterprise
    "quotaBytes": 10485760,         // 10 MB
    "usedBytes": 9395241,
    "remainingBytes": 1089359,      // max(0, quota - used)
    "canUpload": true
  },
  "enterprise": {                   // 未加入企业时为 null
    "orgId": 3001,
    "orgName": "杭州赋签",
    "edition": "enterprise",
    "seatActive": true,             // 席位分配未撤销
    "licenseActive": true,          // 有未过期未退款批次且企业启用
    "isAdmin": false,               // OWNER / ADMIN
    "quotaBytes": 314572800,        // 300 MB
    "usedBytes": 0,
    "remainingBytes": 314572800,
    "canUpload": true,              // seatActive && licenseActive && used < quota
    "readOnly": false               // true = 授权到期/企业停用，可看/下载/删除，不可上传
  }
}, "msg": "" }
```

- `quotaBytes` 由服务端按版本/企业授权实时计算，前端 MUST NOT 自行推算。
- 未登录：401；未加入企业：`enterprise = null`。
- 已加入企业但**没有席位**：`enterprise` 仍返回，`seatActive=false`、`canUpload=false`；
  前端 MUST NOT 展示企业空间入口，可在个人页/企业中心显示「需要企业席位」提示；
  文件夹与内容接口返回 `MEMBER_SHARE_SEAT_REQUIRED`。

---

## 2. 内容项（个人 / 企业）

### 2.1 `GET /member/share-content/page`

Query：`scope`（1 个人 / 2 企业）、`pageNo`、`pageSize`（1–200）、
`keyword?`、`contentType?`（1–5）、`folderId?`（scope=2 时**必填**）。

```jsonc
{ "code": 0, "data": { "total": 2, "list": [
  { "id": 9001, "scope": 1, "folderId": null, "contentType": 1,
    "name": "标准物流面单.msdx", "sizeBytes": 12345,
    "widthMm": 100.0, "heightMm": 150.0,
    "category": "物流", "keywords": "面单,快递",
    "uploaderMemberId": 1001, "uploaderNickname": "张**",
    "createTime": "2026-11-05T10:00:00+08:00", "canDelete": true }
] }, "msg": "" }
```

| 校验 | 规则 |
|---|---|
| 个人 | 只返回 `owner_member_id = 当前会员` |
| 企业 | 需席位 + 文件夹可见（管理员绕过名单）；缺 `folderId` 返回 `MEMBER_SHARE_FOLDER_NO_ROOT` |
| 排序 | `create_time DESC, id DESC` |
| 正文 | 分页不返回 `content` / `downloadUrl`，只返回元数据 |
| 上传者 | 已退出企业的上传者显示 `已退出成员`；个人空间永远能取到本人 |

### 2.2 `GET /member/share-content/get?id=`

```jsonc
{ "code": 0, "data": {
  "id": 9001, "scope": 1, "folderId": null, "contentType": 1,
  "name": "标准物流面单.msdx", "sizeBytes": 12345,
  "storageType": 1,
  "content": "{…清洗后的 msdx…}",   // storageType=1
  "downloadUrl": null,               // storageType=2：5 分钟预签名地址，不返回长期 URL
  "mimeType": "", "widthMm": 100.0, "heightMm": 150.0,
  "category": "物流", "keywords": "面单,快递",
  "uploaderMemberId": 1001, "uploaderNickname": "张**",
  "createTime": "2026-11-05T10:00:00+08:00", "canDelete": true
}, "msg": "" }
```

- 个人：非本人 → `MEMBER_SHARE_CONTENT_NOT_FOUND`（不泄露存在性）。
- 企业：无席位 / 文件夹不可见（且非管理员）→ 同上。
- 平台内容不走此接口。

### 2.3 `POST /member/share-content/create`

用于模板 / txt / csv（文本正文）。

```jsonc
{ "scope": 1, "folderId": null, "contentType": 1,
  "name": "标准物流面单.msdx", "content": "{…msdx…}",
  "widthMm": 100.0, "heightMm": 150.0,
  "category": "物流", "keywords": "面单,快递" }
```

响应：`{ "code": 0, "data": { "id": 9001 }, "msg": "" }`

| 校验 | 规则 |
|---|---|
| 类型 | 只接受 1 模板 / 2 txt / 4 csv；其他走 upload |
| 名称 | 1–255 字符，非空 |
| 大小 | UTF-8 字节数 ≤ 16 MB；同时计入空间配额 |
| 模板 | MUST 通过 `MaxLabelTemplateSanitizer.sanitize(content, false)`；非法 JSON 拒绝 |
| 企业 | 需席位 + 企业启用 + 授权有效 + `folderId` 必填且可见 |
| 个人 | 仅本人；`folderId` 必须为空 |

### 2.4 `POST /member/share-content/upload`

`multipart/form-data`：`scope`、`folderId?`、`contentType`、`name`、`file`。

- `contentType=1/2/4`：服务端按 UTF-8 读取为文本，走与 create 相同的清洗与配额；
- `contentType=3/5`（excel / 图片）：内容经 `FileApi.createFile` 存对象存储，表里记 `file_url`；
- 单文件 ≤ 16 MB；扩展名白名单：
  - 模板 `.msdx`；文本 `.txt`；表格 `.xls/.xlsx/.csv`；
  - 图片 `.png/.jpg/.jpeg/.gif/.bmp/.webp`；
  - 拒绝 `.exe/.bat/.cmd/.sh/.js` 等可执行/脚本。
- 响应同 2.3。

### 2.5 `PUT /member/share-content/update`

仅模板可改正文与元数据；txt/csv/excel/图片只允许改名称。

```jsonc
{ "id": 9001, "name": "面单-新版.msdx", "content": "{…}", "category": "物流", "keywords": "面单" }
```

- 个人：本人；企业：上传者本人或管理员（持席）。
- 改动正文时按「新大小 − 旧大小」校验配额。
- 企业写操作要求授权有效。

### 2.6 `DELETE /member/share-content/delete?id=`

- 个人：本人；企业：上传者本人或管理员（持席）。
- 只要求席位，不要求授权有效（数据处置权，Q116）；删除自己的内容也不要求当前文件夹可见。
- 软删（对用户不可恢复，FR-396）+ 同事务释放容量；重复删除幂等返回 `true`（已删也返回成功）。

### 2.7 `GET /member/share-content/mine`

Query：`pageNo`、`pageSize`（1–200）。

- 返回**本人上传到企业空间**的全部内容，跨文件夹，不要求文件夹当前可见（FR-409）。
- 只要求席位；用于成员被移出文件夹可见名单后仍能找到并删除自己的内容。
- 响应字段与 2.1 分页项一致。

---

## 3. 企业文件夹

### 3.1 `GET /member/share-folder/list`

```jsonc
{ "code": 0, "data": [
  { "id": 4001, "name": "财务", "memberCount": 3,
    "contentCount": 12, "usedBytes": 123456, "canManage": true },
  { "id": 4002, "name": "门店", "memberCount": 5,
    "contentCount": null, "usedBytes": null, "canManage": false }
], "msg": "" }
```

- 需席位；无席位 → `MEMBER_SHARE_SEAT_REQUIRED`。
- 管理员返回全部文件夹；成员只返回名单中包含自己的文件夹。
- `canManage` = 当前会员为 OWNER/ADMIN 且持席。
- `contentCount` / `usedBytes` 仅供管理员查看（FR-414）；成员为 `null`。

### 3.2 `POST /member/share-folder/create`

```jsonc
{ "name": "财务" }
```

- 仅管理员（持席）+ 企业启用 + 授权有效；名称 1–64 字符。
- 响应 `{ "id": 4001 }`。

### 3.3 `PUT /member/share-folder/update`

```jsonc
{ "id": 4001, "name": "财务部" }
```

- 仅管理员；授权过期 → `MEMBER_SHARE_ENTERPRISE_READ_ONLY`。

### 3.4 `DELETE /member/share-folder/delete?id=`

- 仅管理员 + 企业启用 + 授权有效；文件夹内有内容 → `MEMBER_SHARE_FOLDER_NOT_EMPTY`。
- 成功时删除 ACL 行、软删文件夹；幂等。

### 3.5 `GET /member/share-folder/member-list?folderId=`

管理员编辑可见名单用；返回本企业全部成员 + 是否已勾选。

```jsonc
{ "code": 0, "data": {
  "folderId": 4001,
  "members": [
    { "memberId": 1001, "nickname": "张**", "mobile": "138****0000", "role": 1, "visible": true },
    { "memberId": 1002, "nickname": "李**", "mobile": "139****0000", "role": 3, "visible": false }
  ]
}, "msg": "" }
```

- 仅管理员；`mobile` 脱敏；不返回会员密码等敏感字段。

### 3.6 `PUT /member/share-folder/update-members`

```jsonc
{ "id": 4001, "memberIds": [1001, 1002] }
```

- 仅管理员 + 企业启用 + 授权有效；名单中的每个 memberId MUST 属于本企业，否则 `MEMBER_SHARE_FOLDER_MEMBER_INVALID`。
- 空数组 = 仅管理员可见；保存为「删旧 + 插新」的原子操作。
- 管理员本人不需要出现在名单里（自动可见）。

---

## 4. 平台共享内容

### 4.1 `GET /member/platform-content/page`

Query：`pageNo`、`pageSize`（1–200）、`keyword?`、`contentType?`、`category?`。

```jsonc
{ "code": 0, "data": { "total": 1, "list": [
  { "id": 7001, "contentType": 1, "name": "标准物流面单.msdx",
    "sizeBytes": 12345, "widthMm": 100.0, "heightMm": 150.0,
    "category": "物流", "keywords": "面单,快递",
    "publishedTime": "2026-10-20T12:00:00+08:00",
    "thumb": "data:image/svg+xml;base64,…" }
] }, "msg": "" }
```

- 登录即可；只返回 `status=1`，按 `published_time DESC, id DESC`。
- `thumb` 仅模板有（沿用 `MaxLabelTemplateSketch`），其他类型为 `null`。

### 4.2 `GET /member/platform-content/get?id=`

```jsonc
{ "code": 0, "data": {
  "id": 7001, "contentType": 1, "name": "标准物流面单.msdx",
  "storageType": 1, "content": "{…清洗后的 msdx…}", "downloadUrl": null,
  "mimeType": "", "sizeBytes": 12345,
  "widthMm": 100.0, "heightMm": 150.0,
  "category": "物流", "keywords": "面单,快递",
  "publishedTime": "2026-10-20T12:00:00+08:00"
}, "msg": "" }
```

- 下架/删除 → `MEMBER_PLATFORM_CONTENT_NOT_FOUND`。
- 下载得到副本；不返回发布管理员信息。

---

## 5. 兼容端点（保留一个发布周期）

| 旧端点 | 新行为 |
|---|---|
| `GET /member/cloud-template/page` | 代理到 `share-content/page?scope=1&contentType=1`，返回结构不变 |
| `GET /member/cloud-template/get` | 代理到 `share-content/get` |
| `POST /member/cloud-template/create` | 代理到 `share-content/create`（个人 + 模板） |
| `PUT /member/cloud-template/update` | 代理到 `share-content/update` |
| `DELETE /member/cloud-template/delete` | 代理到 `share-content/delete` |
| `POST /member/cloud-template/submit-share` | 返回 `MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED` |
| `DELETE /member/cloud-template/cancel-share` | 返回 `MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED` |
| `GET /member/shared-template/page` | 代理到 `platform-content/page`，保留原字段并加 `contentType`；`publisherNickname` 固定返回 `平台运营` |
| `GET /member/shared-template/get` | 代理到 `platform-content/get` |

> 旧端点的鉴权从「有效订阅」放宽为「登录」（Q121 / FR-399）；这是本特性对 009 FR-219 的唯一放宽。
> 旧的 `shareStatus` 字段继续返回，但恒为 `null`（会员投稿已关闭）；旧桌面端不会因此报错。

---

## 6. 错误码（新增，`1_004_016_xxx`）

| 名称 | 含义 |
|---|---|
| `MEMBER_SHARE_SCOPE_INVALID` | 空间类型不合法 |
| `MEMBER_SHARE_CONTENT_NOT_FOUND` | 内容不存在或无权限 |
| `MEMBER_SHARE_CONTENT_TYPE_INVALID` | 不支持的内容类型或扩展名 |
| `MEMBER_SHARE_CONTENT_TOO_LARGE` | 文件超过 16 MB |
| `MEMBER_SHARE_CONTENT_INVALID` | 名称/字段/模板正文不合法 |
| `MEMBER_SHARE_QUOTA_EXCEEDED` | 空间容量不足（已用/上限） |
| `MEMBER_SHARE_FOLDER_NOT_FOUND` | 文件夹不存在或无权限 |
| `MEMBER_SHARE_FOLDER_NOT_EMPTY` | 文件夹内还有内容，需先清空或移动 |
| `MEMBER_SHARE_FOLDER_NO_ROOT` | 企业空间必须先选择文件夹 |
| `MEMBER_SHARE_SEAT_REQUIRED` | 需要企业席位才能使用企业空间 |
| `MEMBER_SHARE_ENTERPRISE_READ_ONLY` | 企业授权到期或停用，仅可浏览/下载/删除 |
| `MEMBER_SHARE_FOLDER_MEMBER_INVALID` | 可见成员不属于本企业 |
| `MEMBER_PLATFORM_CONTENT_NOT_FOUND` | 平台内容不存在或已下架 |
| `MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED` | 会员投稿已关闭，由平台运营维护 |
| `MEMBER_SHARE_FILE_STORAGE_FAILED` | 文件保存失败，请稍后重试 |
