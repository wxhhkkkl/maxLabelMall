# 管理端接口契约（018 LabelShop 模板导入）

> 所有接口只走 `/admin-api`（宪法）。权限统一为 `member:org:labelshop-import`（按钮级），
> 挂在「企业管理」页面下。HTTP 与 HTTPS 均可访问（2026-11-13 所有者取消强制 HTTPS）。

---

## 0. 通用约定

- 响应包装：`CommonResult<T>`（`code` / `data` / `msg`）。
- 分页参数：`pageNo`、`pageSize`（≤200）。
- 时间：`yyyy-MM-dd HH:mm:ss`。
- 密码：只在 `start` 请求体出现，字段名固定为 `password`；不进入任何响应、任务记录或日志。
- 错误信息不得包含密码；LabelShop 厂商错误只透出 `code/message`。

### 权限与菜单（038 迁移 SQL）

| 菜单 / 按钮 | permission | 类型 | 父级 |
|---|---|---|---|
| 导入历史模板（企业） | `member:org:labelshop-import` | 按钮 | `member:org:query` 页面菜单 |
| 导入历史模板（个人会员） | `member:user:labelshop-import` | 按钮 | `member:user:query` 页面菜单 |

> 未登记 `system_menu` 时，`v-hasPermi` 会把按钮从 DOM 删掉，连超管也看不到；后端
> `@PreAuthorize` 对超管有旁路，会表现为「接口能调、按钮不显示」。

---

## 1. `GET /member/org/labelshop-import/precheck`

打开对话框时调用，展示目标与前置条件。

权限：`member:org:labelshop-import`

参数：`orgId`（必填）。

```jsonc
{
  "orgId": 88,
  "orgName": "云马科技",
  "ownerMemberId": 1001,
  "ownerNickname": "张三",
  "ownerMobile": "138****8000",
  "orgStatus": 0,                 // 0 启用 1 停用
  "licenseActive": true,
  "licenseEndDate": "2027-10-26",
  "seatCount": 3,                 // 导入开始时写入文件夹可见名单的人数
  "folderName": "20261112173000导入",
  "personalEdition": "professional",
  "personalUsedBytes": 1048576,
  "personalQuotaBytes": 104857600,
  "enterpriseUsedBytes": 20971520,
  "enterpriseQuotaBytes": 314572800,
  "runningTaskId": null           // 非空时禁止再次发起
}
```

> `folderName` 是**预览值**（按 precheck 时刻生成），实际以任务开始时生成的 `<时间>导入` 为准。

**前端处理**：

- `orgStatus != 0` 或 `licenseActive=false` → 显示阻断提示，禁用开始；
- `runningTaskId != null` → 显示「已有导入任务在进行」，跳转到进度视图；
- `seatCount=0` 仍允许导入（文件夹 ACL 为空 = 仅管理员可见），但需提示。

---

## 2. `POST /member/org/labelshop-import/start`

创建任务、文件夹与 ACL，提交异步执行。

权限：`member:org:labelshop-import`

请求：

```jsonc
{
  "orgId": 88,
  "account": "mianbaohuiyou@126.com",
  "password": "******",
  "authorized": true              // 必须为 true：已获得账号所有人授权（FR-484）
}
```

响应：

```jsonc
{ "taskId": 9001 }
```

**服务端校验顺序**：

1. `account/password/authorized` 非空且 `authorized=true`（否则 `MEMBER_LABELSHOP_CREDENTIAL_REQUIRED`）；
2. 企业存在且启用（否则 `MEMBER_ORG_NOT_EXISTS` / `MEMBER_ORG_DISABLED`）；
3. 企业有 OWNER 且会员存在（否则 `MEMBER_LABELSHOP_OWNER_MISSING`）；
4. 企业授权有效（否则 `MEMBER_LABELSHOP_ORG_READ_ONLY`）；
5. 同企业无进行中任务（否则 `MEMBER_LABELSHOP_IMPORT_RUNNING`）；
6. 创建任务（`PENDING`，写 `running_org_id`，`folder_name` 先算好、`folder_id` 暂空），
   同时读取有效席位 memberId 列表作为 ACL 快照；
7. 事务提交后提交线程池；拒绝时任务置 `FAILED` 并返回 `MEMBER_LABELSHOP_TASK_REJECTED`；
8. 异步任务登录 + 枚举成功后再创建 `<时间>导入` 文件夹，并用 §7 的席位快照写 ACL，
   回填 `folder_id`，然后写入内容。

**安全注解**：

- `@ApiAccessLog(requestEnable = false)`：不记录请求体；
- `@LogRecord(type="历史模板导入", subType="发起历史模板导入", bizNo="{{#reqVO.orgId}}", success="发起…")`：
  只记对象编号（企业用 `orgId`、会员用 `memberId`）与结果，不记账号密码；成功文案分别标注
  「企业【…】」/「会员【…】」；
- 任务表只存 `source_account_masked`。

---

## 2.1 个人会员代导（`/member/user/labelshop-import/*`）

同一组接口在 `/member/user/labelshop-import` 下也注册，用 `memberId` 代替 `orgId`
（Q145，2026-11-13 所有者拍板：只允许运营在管理端代导，C 端不开放）。

- `GET /precheck?memberId=1001`：返回会员昵称 / 手机号掩码、个人空间用量与配额；
  `orgId/orgName/folderName` 为 `null`，`seatCount=0`，`orgStatus=0`，`licenseActive=true`
  （企业门禁不适用）。
- `POST /start`：body `{ "memberId": 1001, "account": "...", "password": "...", "authorized": true }`；
  `orgId` 与 `memberId` 必须且只能出现一个，否则 `MEMBER_LABELSHOP_SUBJECT_REQUIRED`。
- `GET /task?taskId=`、`GET /items?taskId=`：与企业导入一致；任务响应新增
  `subjectType`（1 企业 / 2 个人会员），个人任务 `orgId/orgName/folderName` 为 `null`。
- `GET /latest?memberId=1001`：该会员最近一次个人代导任务。

**口径**：

- 只读取 `Own_Type=12`（我的模板库），全部写入该会员的 016 个人空间
  （`scope=1`、`ownerMemberId=memberId`）；
- 不创建企业文件夹、不改动企业空间；容量口径同 FR-491 / FR-492（不拦截，导入后按 016 超配额只读）；
- 幂等按 `(个人空间, ownerMemberId, sourceRef)` 判定；
- 同一会员同一时刻最多一个进行中任务，与企业任务互不占用名额；
- C 端 `/app-api` MUST NOT 提供该能力。

---

## 3. `GET /member/org/labelshop-import/task?taskId=9001`

任务状态与结果摘要。权限：`member:org:labelshop-import`

```jsonc
{
  "id": 9001,
  "orgId": 88,
  "orgName": "云马科技",
  "ownerMemberId": 1001,
  "folderId": 900,
  "folderName": "20261112173000导入",
  "sourceAccountMasked": "mi***@126.com",
  "status": 2,                    // 0 待执行 1 进行中 2 已完成 3 已失败 4 已中断
  "phase": "FINISH",              // LOGIN/LIST/IMPORT/FINISH
  "totalCount": 245,
  "processedCount": 245,
  "successCount": 245,
  "skipCount": 0,
  "failCount": 0,
  "personalBytes": 1990000,
  "enterpriseBytes": 19700000,
  "personalUsedBytes": 3030000,
  "personalQuotaBytes": 104857600,
  "personalExceeded": false,
  "enterpriseUsedBytes": 40600000,
  "enterpriseQuotaBytes": 314572800,
  "enterpriseExceeded": false,
  "libraries": [
    { "library": 12, "total": 44, "success": 44, "skip": 0, "fail": 0 },
    { "library": 13, "total": 194, "success": 194, "skip": 0, "fail": 0 },
    { "library": 1,  "total": 7,   "success": 7,   "skip": 0, "fail": 0 }
  ],
  "uncleaned": true,              // Q138 原样导入
  "archiveJsonCount": 7,          // 企业/组织私有 JSON（editable=0）
  "operatorId": 1,
  "operatorName": "运营小王",
  "startedTime": "2026-11-12 17:30:00",
  "finishedTime": "2026-11-12 17:34:12",
  "errorMessage": null
}
```

**前端轮询**：`status IN (0,1)` 时每 2–3 秒轮询；`totalCount=0 && phase='LIST'` 显示
「正在读取模板列表」；终态停止轮询并刷新成员/共享内容页签。

**失败口径**：

- **中途停用 / 授权到期**：每条写入前重查企业启用与授权；失效即停止新增写入，
  `status=3`、`errorMessage` 记录原因，已写入内容保留（SC-182）。
- **来源级失败**（STS / 某公司列表 / 个人或组分类树）：该来源不产生明细，`libraries` 中为 0，
  `errorMessage` 汇总来源级原因；其余来源继续（R137）。全部来源失败时 `status=3`。
  `failCount` 只统计模板级失败，来源级失败不计入。
- `errorMessage` 只含可展示原因，不含密码 / token。

---

## 4. `GET /member/org/labelshop-import/latest?orgId=88`

返回该企业最近一次任务（结构同 §3）；无任务时 `data=null`。用于页面刷新/重新进入后恢复进度。

---

## 5. `GET /member/org/labelshop-import/items`

失败清单 / 明细分页。

权限：`member:org:labelshop-import`

参数：`taskId`（必填）、`status`（选填，1 成功 / 2 跳过 / 3 失败）、`library`（选填 12/13/1）、
`pageNo`、`pageSize`。

```jsonc
{
  "list": [
    {
      "id": 50001,
      "sourceLibrary": 13,
      "sourceCategory": "服装标签/上衣",
      "sourceName": "测试空模板",
      "targetScope": 2,
      "format": "lsdx",
      "editable": true,
      "sizeBytes": 0,
      "status": 3,
      "errorMessage": "LABEL 为空"
    }
  ],
  "total": 2
}
```

**失败原因**：正文为空、下载失败、名称异常、写入失败、单条超时等；不含密码或 token。
`status=3` 的列表即失败清单；前端默认展示该筛选。来源级失败没有模板明细，见任务
`errorMessage`。

> 上面的失败样例是**示意**：样例账号的 2 条空模板（`新标签模板1`、`实惠装条码`）位于被排除的
> 公共模板库，不在本次导入结果中。验证失败清单请在测试环境注入空正文或断网。

---

## 6. 错误码（新增，`1_004_018_xxx`）

| Code | 名称 | 语义 |
|---|---|---|
| `1_004_018_001` | `MEMBER_LABELSHOP_CREDENTIAL_REQUIRED` | 请填写历史系统账号、密码并确认授权 |
| `1_004_018_002` | `MEMBER_LABELSHOP_LOGIN_FAILED` | 历史系统登录失败，请检查账号密码 |
| `1_004_018_003` | `MEMBER_LABELSHOP_UPSTREAM_ERROR` | 历史系统接口异常，请稍后重试 |
| `1_004_018_004` | `MEMBER_LABELSHOP_IMPORT_RUNNING` | 该企业已有导入任务在进行 |
| `1_004_018_005` | `MEMBER_LABELSHOP_TASK_NOT_FOUND` | 导入任务不存在 |
| `1_004_018_006` | `MEMBER_LABELSHOP_OWNER_MISSING` | 企业缺少有效 OWNER，无法导入 |
| `1_004_018_007` | `MEMBER_LABELSHOP_ORG_READ_ONLY` | 企业授权已到期或企业已停用，不能导入 |
| `1_004_018_008` | `MEMBER_LABELSHOP_TASK_REJECTED` | 导入任务繁忙，请稍后重试 |
| `1_004_018_009` | `MEMBER_LABELSHOP_SUBJECT_REQUIRED` | 请指定企业或会员 |

> 复用既有：`MEMBER_ORG_NOT_EXISTS`、`MEMBER_ORG_DISABLED`、`MEMBER_ORG_NOT_ADMIN` 等。

---

## 7. 前端页面契约

- `src/api/member/labelshopImport/index.ts`：`precheck / start / getTask / getLatest / getItems`。
- `OrgDetail.vue`：在「成员与席位」工具栏加按钮：
  ```vue
  <el-button v-hasPermi="['member:org:labelshop-import']" type="primary" @click="importVisible = true">
    导入历史模板
  </el-button>
  ```
- `LabelshopImportDialog.vue`：
  - 打开即 `precheck`；展示 OWNER、文件夹名、席位人数、个人/企业容量；
  - 账号、密码、授权勾选；密码 `type="password"`、`autocomplete="new-password"`；
  - `start` 后清空密码并进入进度视图；
  - 轮询 `task`；终态展示汇总 + 按来源库分布 + 「原样导入、未清洗」+「企业/组织私有 JSON 暂不可直接编辑」；
  - 失败清单表格（名称 / 来源库 / 分类 / 原因）。
- `views/member/user/detail/index.vue`：个人会员详情的「个人空间」页签（原「标签模板」）：
  - 按钮 `导入历史模板`，`v-hasPermi="['member:user:labelshop-import']"`；
  - 列表改为 016 个人空间内容，复用管理端只读接口（新增，权限 `member:user:query`）：
    - `GET /member/share-content/member-overview?memberId=` → 已用 / 上限 / 剩余；
    - `GET /member/share-content/member-page?memberId=&contentType=&keyword=&pageNo=&pageSize=`；
    - `GET /member/share-content/member-get?memberId=&id=` → 正文或下载地址；
  - 2026-11-16：`member-page` / `member-get` 响应带个人文件夹名 `folderName`（根目录为 `null`）；
  - `GET /member/share-content/member-folders?memberId=`：返回个人空间文件夹（含 `parentId` 与
    `contentCount`），管理端只读；
  - `member-page` 支持 `folderId`：空 = 全部、`0` = 根目录、`>0` = 指定文件夹；
  - 页面改为**左侧树形文件夹导航 + 右侧内容列表**，点击文件夹切换 `folderId` 筛选；
  - 导入完成后刷新该页签（`@completed`）。
- 测试：`LabelshopImportDialog.spec.ts` 覆盖 precheck 阻断、请求体、轮询、失败清单、会员模式；
  `OrgDetail.spec.ts` 覆盖按钮渲染与打开对话框；`UserShareSpaceList.spec.ts` 覆盖容量概览、
  列表渲染、搜索与预览。
