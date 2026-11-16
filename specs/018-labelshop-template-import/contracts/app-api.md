# C 端接口契约（个人空间文件夹 / 云端模板转换）

> 2026-11-16 追加。个人空间支持多层文件夹与 CRUD；云端模板转换发生在导入侧，不新增 C 端接口。

## 1. 个人文件夹

权限：登录会员本人；所有接口只操作自己的文件夹。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/member/share-folder/personal/list` | 返回全部个人文件夹（含 `parentId`，前端组树） |
| POST | `/member/share-folder/personal/create` | body `{ name, parentId? }`；`parentId` 空 = 根目录 |
| PUT | `/member/share-folder/personal/update` | body `{ id, name }`；同一父级下重名拒绝 |
| PUT | `/member/share-folder/personal/move` | body `{ id, parentId? }`；移动到自身/子级拒绝 |
| DELETE | `/member/share-folder/personal/delete?id=` | 仅允许删除空文件夹（无内容、无子文件夹） |

错误码复用：`MEMBER_SHARE_FOLDER_NOT_FOUND`、`MEMBER_SHARE_FOLDER_NOT_EMPTY`、
`MEMBER_SHARE_FOLDER_NAME_EXISTS`（`1_004_016_017`）、
`MEMBER_SHARE_FOLDER_MOVE_INVALID`（`1_004_016_018`）。

## 2. 个人内容分页 / 上传 / 保存

- `GET /member/share-content/page?scope=1&folderId=`
  - `folderId` 缺省 = 全部（兼容起始页平铺）；`0` = 根目录；`>0` = 指定文件夹（校验归属）。
- `POST /member/share-content/create`、`POST /member/share-content/upload`
  - 个人空间可选 `folderId`；落库时写入 `maxlabel_share_content.folder_id`。
- 管理端会员个人空间只读接口在内容响应中带 `folderName`（个人文件夹名）。

## 3. 导入侧口径

- `Own_Type=0` 云端 JSON 在导入时转 `.lsdx`（`cloud_to_lsdx` 同规则）；转换失败记模板级失败。
- 个人内容按来源分类路径逐级创建/复用文件夹；企业内容仍只落单层 `<时间>导入` 文件夹。
