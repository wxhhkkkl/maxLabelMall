---
description: "Task list for 018 LabelShop 模板导入"
---

# Tasks: LabelShop 模板导入（后台批量迁移）

**Input**: Design documents from `specs/018-labelshop-template-import/`
**Prerequisites**: plan.md · spec.md · research.md · data-model.md · contracts/ · quickstart.md

**Tests**: 本项目宪法原则 I（测试先行，不可协商）要求所有新功能先写失败测试。以下每个用户故事的
测试任务 MUST 先于实现任务完成，并确认失败原因正确（不是拼写/导入错误）。若某处确实无法先写测试，
在该任务上注明原因。

**Organization**: 按用户故事分组，每个故事可独立实现、独立验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US60~US62）
- 所有路径均相对仓库根 `d:\workspace\maxLabelMall`；桌面端跨仓库路径以 `/d/workspace/maxlabel/app/` 开头

## Path Conventions

- 后端：`yudao-cloud/yudao-module-member/yudao-module-member-server/src/main/java/cn/iocoder/yudao/module/member/`
- 后端测试：`yudao-cloud/yudao-module-member/yudao-module-member-server/src/test/java/cn/iocoder/yudao/module/member/`
- 管理端：`yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/`
- 桌面端：`/d/workspace/maxlabel/app/src/`
- SQL：`yudao-cloud/sql/mysql/`

---

## Phase 1: Setup（数据层与配置骨架）

**Purpose**: 先落下 2 张表、DO/Mapper、错误码、日志常量、配置与线程池；后续所有故事都引用。

- [x] T001 新建 `yudao-cloud/sql/mysql/038-labelshop-template-import.sql`：幂等创建
  `maxlabel_labelshop_import_task`（`folder_id` 可空、`folder_name varchar(64) NOT NULL`、
  `status 0..4`、`phase LOGIN/LIST/IMPORT/FINISH`、`running_org_id bigint NULL` +
  `UNIQUE uk_running_org(tenant_id, running_org_id)`、`idx_org_id`、`idx_status`）与
  `maxlabel_labelshop_import_item`（`source_library tinyint` 12/13/1、`source_ref varchar(64)`、
  `source_category varchar(255)`、`target_scope 1/2`、`target_owner_id`、`content_id` 可空、
  `format lsdx/cloud-json`、`editable bit`、`status 0..3`、`UNIQUE uk_task_source(tenant_id, task_id, source_ref)`、
  `INDEX idx_target_source(tenant_id, target_scope, target_owner_id, source_ref)`、`idx_task_status`、`idx_task_library`），
  以及按钮权限 `member:org:labelshop-import`（`type=3`，父级 = `member:org:query` 的菜单 id）；
  照 `033-shared-content-space.sql` 的幂等写法；文件头写回滚与「先执行 SQL 再发后端」
- [x] T002 [P] 新建 `.../dal/dataobject/labelshop/LabelshopImportTaskDO.java`：字段与 `data-model.md` §1 一致
  （`orgId/ownerMemberId/folderId/folderName/sourceAccountMasked/status/phase/totalCount/processedCount/
  successCount/skipCount/failCount/personalBytes/enterpriseBytes/errorMessage/operatorId/operatorName/
  startedTime/finishedTime/heartbeatTime/runningOrgId`），继承 `BaseDO`，`@TableName`
- [x] T003 [P] 新建 `.../dal/dataobject/labelshop/LabelshopImportItemDO.java`：字段与 `data-model.md` §2 一致
  （`taskId/sourceLibrary/sourceRef/sourceCategory/sourceName/targetScope/targetOwnerId/contentId/
  format/editable/sizeBytes/status/errorMessage`），继承 `BaseDO`
- [x] T004 [P] 新建 `.../dal/mysql/labelshop/LabelshopImportTaskMapper.java`（继承 `BaseMapperX`）：
  `selectLatestByOrg(orgId)`、`selectRunningByOrg(orgId)`、`selectPageByOrg(orgId, pageParam)`、
  `clearRunningOrg(taskId)`、`selectInterruptedCandidates()`、`markInterrupted(taskId)`；
  跨租户查询/更新用 `@TenantIgnore` 或由 Runner 包 `TenantUtils.executeIgnore`
- [x] T005 [P] 新建 `.../dal/mysql/labelshop/LabelshopImportItemMapper.java`：
  `selectPageByTask(taskId, status, library, pageParam)`、`selectLibraryStats(taskId)`（按
  `source_library,status` 分组）、`selectExistingSuccess(targetScope, targetOwnerId, sourceRefs)`（跨任务）、
  `countByTaskAndFormat(taskId, format)`
- [x] T006 [P] 在 `yudao-cloud/yudao-module-member/yudao-module-member-api/src/main/java/cn/iocoder/yudao/module/member/enums/ErrorCodeConstants.java`
  增加 `1_004_018_001..008`（原 000 HTTPS_REQUIRED 已由 T057 取消；CREDENTIAL_REQUIRED / LOGIN_FAILED / UPSTREAM_ERROR /
  IMPORT_RUNNING / TASK_NOT_FOUND / OWNER_MISSING / ORG_READ_ONLY / TASK_REJECTED，文案见 `contracts/admin-api.md` §6）
- [x] T007 [P] 在 `yudao-cloud/yudao-module-member/yudao-module-member-api/src/main/java/cn/iocoder/yudao/module/member/enums/LogRecordConstants.java`
  增加 `MEMBER_LABELSHOP_TYPE`、`MEMBER_LABELSHOP_START_SUB_TYPE`、`MEMBER_LABELSHOP_START_SUCCESS`；
  文案只带 `orgId`/`taskId`，不带账号密码
- [x] T008 [P] 新建 `.../service/labelshop/LabelshopImportProperties.java`：
  `@ConfigurationProperties(prefix="yudao.maxlabel.labelshop")`，字段 `baseUrl`（默认
  `https://o.codingv.com/v1`）、`mid`（可空）、`uaShort/uaLong/uaBuild/uaOs`（默认 6.39/6.39.2511.36/2511/`Windows 10; x64`）、
  `connectTimeoutMs=30000`、`readTimeoutMs=60000`、`batchSize=20`
- [x] T009 [P] 新建 `.../service/labelshop/LabelshopImportExecutorConfig.java`：
  `@Bean("labelshopImportExecutor") ThreadPoolTaskExecutor`（core=1、max=2、queue=4、
  prefix=`labelshop-import-`、`AbortPolicy`）；如项目未启用 `@EnableAsync` 一并加上
- [x] T010 新建 `.../service/labelshop/LabelshopImportRecoveryRunner.java`：
  `ApplicationRunner`，跨租户把 `status IN (0,1)` 的任务置 `4 已中断`、`running_org_id=NULL`、
  `error_message='服务重启导致任务中断'`；用 `TenantUtils.executeIgnore` 或 `@TenantIgnore` Mapper

**Checkpoint**: 表、DO/Mapper、错误码、日志、配置、线程池、恢复 Runner 就绪。

---

## Phase 2: Foundational（LabelShop 客户端、写入器、枚举器）

**Purpose**: 三个可独立测试的核心组件；完成前不能开始任何用户故事的编排。

**⚠️ CRITICAL**: 本阶段完成前，US60/US61/US62 的实现任务不得开始。

- [x] T011 [P] **先**在 `.../src/test/java/cn/iocoder/yudao/module/member/service/labelshop/LabelshopClientTest.java`
  写失败用例：登录 body（`mid/mname/pwd=MD5-UPPER/sid/try/uname`）、Exec 签名
  `MD5(executetype=0&proc=...&procparams=...&time=...+token)`、紧凑 JSON、Cookie `cvstoken/cvsuid`、
  `GetCateTree/GetCateList/GetTemplate`、STS/OSS V1 签名、UA（`M/{mid.toLowerCase()}`）、
  只读白名单拒绝未知 proc；用可替换传输层/纯函数，不访问真实 LabelShop
- [x] T012 新建 `.../service/labelshop/LabelshopClient.java` 与 `.../dto/`：按
  `contracts/labelshop-upstream.md` 实现登录、Exec、树/列表/模板、STS、OSS；
  Hutool 5.8.47 `HttpRequest` + 默认信任链、关闭主机名校验；`mid` 配置或主机名派生；
  `companys` 大小写不敏感解析；只读白名单二次校验；超时 30s/60s；不自动重试
- [x] T013 [P] **先**在 `.../service/labelshop/LabelshopImportContentWriterTest.java` 写失败用例：
  `.lsdx` 原始正文直接入库（不调用 `MaxLabelTemplateSanitizer`、不校验 `.msdx`）、
  `.json` 归档 `editable=0`、个人/企业 `owner_member_id=OWNER`、企业 `folder_id` 必填、
  名称去重扩展名+非法字符替换、空名回退 `source_ref`、`>16MB` 失败、
  `content_hash/size_bytes/width/height` 正确、不调用 `checkQuota`
- [x] T014 新建 `.../service/labelshop/LabelshopImportContentWriter.java`：
  直接构造 `MaxLabelShareContentDO`（`content_type=1`、`storage_type=1`、`mime_type=text/xml|application/json`）
  并经 `MaxLabelShareContentMapper` 插入；名称归一（含空名回退 `source_ref`）；与明细更新同事务；
  每批调用 `ShareSpaceService#refreshUsedBytes`（不调用容量校验）
- [x] T015 [P] **先**在 `.../service/labelshop/LabelshopSourceEnumeratorTest.java` 写失败用例：
  我的模板库 `Own_Type=12` 用 `GetCateTree(@TYPE=12,@MODE=4)`、列表/下载 `@OWNTYPE=12`；
  组模板库 `Own_Type=13` 用 `GetCateTree(@TYPE=14,@MODE=1)`、列表/下载 `@OWNTYPE=13`；
  企业/组织 `@CATEID=0,@OWNTYPE=1,@UID=companyId`；不读取 `14` 与其他类型；
  未分类兜底 `@CATEID=-1,@OWNTYPE=12` 记 `未分类`；分类路径沿 `Cate_ID/Parent_ID/Name`；
  多家公司全部纳入
- [x] T016 新建 `.../service/labelshop/LabelshopSourceEnumerator.java` 与 DTO：
  按 `research.md` R124 输出 `(sourceLibrary, sourceRef, sourceCategory, sourceName, format, targetScope)`；
  企业/组织带公司名；不下载缩略图；STS / 某公司列表 / 个人或组分类树失败按 R137
  记任务 `errorMessage` 并继续其余来源
- [x] T017 [P] **先**在 `.../service/labelshop/LabelshopImportTaskStoreTest.java` 写失败用例：
  创建任务写 `running_org_id`、重复创建冲突、终态清空、启动恢复、计数/心跳更新、
  租户上下文显式传递
- [x] T018 新建 `.../service/labelshop/LabelshopImportTaskStore.java`（或并入 Service 的包内方法）：
  创建/领取/更新/完成/中断任务；`running_org_id` 唯一键冲突映射
  `MEMBER_LABELSHOP_IMPORT_RUNNING`；`TenantUtils.execute(taskTenantId, ...)` 包裹异步执行
- [x] T019 运行 `mvn -pl yudao-module-member/yudao-module-member-server -am test -Dtest='*Labelshop*'`，
  确认 Foundational 测试从红转绿；**进入 Phase 3 前运行 `/speckit-analyze`，CRITICAL 问题先解决**

**Checkpoint**: 客户端、写入器、枚举器、任务存储可独立验证。

---

## Phase 3: User Story 1 — 运营在企业详情完成一次导入（P1 · US60 · MVP）

**Goal**: 输入账号密码后，按来源白名单把「我的模板库」写入 OWNER 个人空间，把「组模板库 + 企业/组织模板」
写入企业空间新建的 `<时间>导入` 文件夹；不读取公共/用户模板库；`.lsdx` 可在桌面端打开。

**Independent Test**: 用一个各来源库都有若干条的测试账号导入某企业 → OWNER 个人云模板中心出现
我的模板库条目；持席成员在企业空间看到新建文件夹及企业模板；被排除库在两边都看不到；
登录失败不留下空文件夹。

### Tests for User Story 1（REQUIRED — 先失败）

- [x] T020 [P] [US60] 在 `.../service/labelshop/LabelshopImportServiceImplTest.java` 写失败用例：
  `start` 校验企业启用/OWNER/授权；创建任务与席位快照；`folder_id` 初始为空；登录失败不建文件夹；
  枚举成功后新建 `<时间>导入` + ACL；来源→目标映射；企业内容 `owner_member_id=OWNER`；
  公共/用户模板库 0 条；`runTask` 写内容与明细；使用非 OWNER 的经授权账号成功（SC-187）；
  既有文件夹 ACL 改动 0、导入后新增席位不被自动加入（SC-188）；普通成员删除被拒、
  OWNER/ADMIN 删除成功；任务记录 `operatorId/operatorName` = 实际操作管理员（SC-190）
- [x] T021 [P] [US60] 在 `.../controller/admin/labelshop/AdminLabelshopImportControllerTest.java` 写失败用例：
  `POST /member/org/labelshop-import/start` 的权限、`authorized=true`、
  返回 `{taskId}`、密码不出现在响应
- [x] T022 [P] [US60] 在 `/d/workspace/maxlabel/app/src/renderer/src/io/msdx.spec.ts` 写失败用例：
  `cloudContentKind` 区分 `.lsdx`（`<?xml...<labelshopdocument`）、归档 JSON（`docid/lfrm/pfrm`）、
  普通 `.msdx`/裸 LabelDoc JSON；并新建 `lsdxImport.spec.ts`，用 `app/fixtures/lsdx/minimal.lsdx`
  与一条**脱敏**的导出 `.lsdx`（复制为 `app/fixtures/lsdx/exported.lsdx`）调用 `importLsdx`，
  断言返回 doc 无致命错误（SC-183）

### Implementation for User Story 1

- [x] T023 [US60] 新建 `.../service/labelshop/LabelshopImportService.java` + `LabelshopImportServiceImpl.java`：
  `start(orgId, account, password, authorized, operatorId, operatorName)` 校验企业/OWNER/授权；
  `folder_name = yyyyMMddHHmmss + "导入"`；读取 `seatService.getActiveList(orgId)` 作为 ACL 快照；
  创建任务（`PENDING`、`running_org_id=orgId`）；事务提交后提交线程池
- [x] T024 [US60] 在 `LabelshopImportServiceImpl` 实现 `runTask`：`TenantUtils.execute`；
  `LOGIN` → 登录；`LIST` → 枚举并批量插入待处理明细；枚举成功后新建文件夹 + ACL
  （`creator_member_id=OWNER`，同名追加后缀）并回填 `folder_id`；`IMPORT` → 逐条下载/写入/更新明细；
  每条写入前重查企业启用/授权，失效即停止新增写入、任务 `FAILED` 并保留已写入（SC-182）；
  来源级失败按 R137 记 `errorMessage` 并继续；每批更新任务计数与 `heartbeat_time`；
  `FINISH` 置终态并清 `running_org_id`
- [x] T025 [US60] 新建 `.../controller/admin/labelshop/AdminLabelshopImportController.java` +
  `.../vo/AdminLabelshopImportStartReqVO.java`：`start` 接口、`@PreAuthorize('member:org:labelshop-import')`、
  `@ApiAccessLog(requestEnable=false)`、`@LogRecord(bizNo=orgId)`；协议校验已由 T057 取消
- [x] T026 [P] [US60] 在 `/d/workspace/maxlabel/app/src/renderer/src/io/msdx.ts` 实现 `cloudContentKind`；
  在 `msdx.spec.ts` 补齐断言
- [x] T027 [US60] 修改 `/d/workspace/maxlabel/app/src/renderer/src/App.tsx` 的 `onCloudLoad(content, name?)`：
  `lsdx → importLsdx`、`archive-json → 提示旧版归档`、其余 `fromDocJson`；同步修改
  `dialogs/CloudTemplateDialog.tsx`、`dialogs/ShareSpaceDialog.tsx` 的 `onLoad` 签名与调用点，
  以及 `features/shell/ModalHost.tsx` 的 prop 类型
- [x] T028 [US60] 在 `/d/workspace/maxlabel/app/src/shared/ipcContract.ts` 与主进程新增
  `saveTextFile(name, content)` IPC（照 `main/ipc/registerLogIpc.ts` 的 `dialog.showSaveDialog` +
  `writeFile`），并在 `dialogs/ShareSpaceDialog.tsx` / `CloudTemplateDialog.tsx` 为 `.lsdx` 与
  归档 JSON 提供「另存为」；`.lsdx` 打开后也可用应用既有保存路径落盘（SC-183）
- [x] T029 [US60] 管理端：新建 `.../src/api/member/labelshopImport/index.ts`（先含 `start`）、
  `.../src/views/member/org/LabelshopImportDialog.vue`（账号/密码/授权勾选/开始）、
  在 `OrgDetail.vue` 的成员与席位工具栏加 `v-hasPermi` 按钮并挂载对话框
- [ ] T030 [US60] 运行 US60 测试与手工检查：来源映射、文件夹/ACL、OWNER 归属、`.lsdx` 打开与另存、
  登录失败无空文件夹、非 OWNER 账号、既有 ACL 不变、新席位不自动加入、删除权、操作人一致；
  确认测试从红转绿

**Checkpoint**: US60 可独立演示：一次导入把三类来源写对落点，排除库 0 条。

---

## Phase 4: User Story 2 — 导入过程可见、结果可核对（P2 · US61）

**Goal**: 发起后不阻塞页面；展示进度、结果摘要（按来源库分布、字节、超限、未清洗、归档数）、
失败清单；刷新/重进仍可查看最近任务。

**Independent Test**: 发起一次导入，刷新页面后仍看到状态/进度/最终汇总；注入一条失败 →
失败清单能定位到名称、来源库/分类与原因。

### Tests for User Story 2（REQUIRED — 先失败）

- [x] T031 [P] [US61] 在 `LabelshopImportServiceImplTest` 增加失败用例：`getTask/getLatest/getItems/precheck`
  的状态机、计数、按库统计、`exceeded/uncleaned/archiveJsonCount`、失败分页
- [x] T032 [P] [US61] 在 `AdminLabelshopImportControllerTest` 增加失败用例：
  `precheck/task/latest/items` 的权限、参数、响应结构、无任务返回 `null`
- [x] T033 [P] [US61] 在 `.../src/views/member/org/LabelshopImportDialog.spec.ts` 与
  `OrgDetail.spec.ts` 写失败用例：precheck 阻断（HTTP/停用/无授权）、开始请求体、2–3 秒轮询、
  终态停止、汇总/失败清单渲染、刷新后 `latest` 恢复

### Implementation for User Story 2

- [x] T034 [US61] 在 `LabelshopImportServiceImpl` 实现 `getTask/getLatest/getItems/precheck`：
  按明细分组统计、失败分页、导入后已用/上限/是否超限、`uncleaned=true`、
  `archiveJsonCount=format('cloud-json')`；precheck 返回 OWNER/席位数/文件夹预览/容量/进行中任务
- [x] T035 [US61] 在 `AdminLabelshopImportController` 增加 `precheck/task/latest/items` +
  `.../vo/`（`AdminLabelshopImportPrecheckRespVO`、`TaskRespVO`、`ItemRespVO`、`ItemsPageReqVO`）；
  全部 `@PreAuthorize('member:org:labelshop-import')`（协议校验已由 T057 取消）
- [x] T036 [US61] 扩展 `.../src/api/member/labelshopImport/index.ts`：`precheck/getTask/getLatest/getItems`
- [x] T037 [US61] 完成 `LabelshopImportDialog.vue`：precheck 展示、进度条/阶段、
  汇总（成功/跳过/失败、个人/企业字节、按库分布、未清洗、归档数）、失败清单分页、
  HTTP/停用/无授权阻断、提交后清空密码
- [x] T038 [US61] 在 `OrgDetail.vue` 导入完成后刷新成员/共享内容页签；补齐 `OrgDetail.spec.ts`
  的按钮与对话框打开断言
- [ ] T039 [US61] 运行 US61 测试与手工检查：`LabelshopImportServiceImplTest`、
  `AdminLabelshopImportControllerTest`、`LabelshopImportDialog.spec.ts`；按 `quickstart.md` §4
  验证提交后 ≤10 秒看到进度、刷新恢复、失败清单、轮询停止、汇总数字

**Checkpoint**: US60 + US61 可联合演示，且 US61 可在 US60 已导入的数据上独立验证。

---

## Phase 5: User Story 3 — 重复导入、超配额与凭据保护（P3 · US62）

**Goal**: 同一账号重复导入不产生重复内容；导入不受容量闸门限制但如实反映超限；
密码不落库/不入日志/不回显；会话失效只重登一次。

**Independent Test**: 同一账号连续导入两次 → 第二次新增 0、跳过 = 第一次成功；把空间填到接近上限再导入
→ 仍完成且 `exceeded=true`，之后常规上传被拒绝；检查任务表/日志无密码；HTTP 被拒绝。

### Tests for User Story 3（REQUIRED — 先失败）

- [x] T040 [P] [US62] 在 `.../service/labelshop/LabelshopImportIdempotencyTest.java` 写失败用例：
  跨任务幂等（历史成功项跳过）、内容被删除后重新导入、每任务明细独立、`uk_task_source` 冲突处理
- [x] T041 [P] [US62] 在 `.../service/labelshop/LabelshopImportQuotaTest.java` 写失败用例：
  容量超限仍写入、`used_bytes` 正确、导入后常规上传被拒绝、单条 `>16MB` 失败、
  `personalExceeded/enterpriseExceeded`
- [x] T042 [P] [US62] 在 `AdminLabelshopImportControllerTest` 增加失败用例：任务表/响应无密码、
  `@ApiAccessLog` 不记录请求体、账号掩码格式（HTTPS 拒绝用例已由 T057 取消）
- [x] T043 [P] [US62] 在 `.../service/labelshop/LabelshopImportFailureTest.java` 写失败用例：
  会话失效重登 1 次并继续、再次失效中止且已写入保留、单条失败不触发重登、失败隔离；
  **导入中途企业停用/授权到期 → 每条写入前拦截、任务 FAILED、已写入保留**（SC-182）；
  **STS / 某公司 / 分类树级失败 → 其余来源继续，errorMessage 汇总**（R137）

### Implementation for User Story 3

- [x] T044 [US62] 在 `runTask` 接入去重：导入前按目标预载历史成功明细；命中且内容仍存在 →
  当前明细 `SKIPPED`；内容已删除 → 重新写入；按 `uk_task_source` 防任务内重复
- [x] T045 [US62] 在 `runTask`/`LabelshopImportContentWriter` 接入容量例外：不调用 `checkQuota`；
  每批 `refreshUsedBytes(personal, ownerMemberId)` 与 `refreshUsedBytes(enterprise, orgId)`；
  任务详情计算 `exceeded`；保留单条 16MB 失败
- [x] T046 [US62] 完成凭据保护：账号掩码（`source_account_masked`）、`start` 的
  `@ApiAccessLog(requestEnable=false)`、`@LogRecord` 只用 orgId/taskId、登录/上游错误文案不含密码、
  前端提交后清空密码且不写 localStorage/sessionStorage
- [x] T047 [US62] 在 `.../service/labelshop/LabelshopImportServiceImpl.java` 完成会话策略：
  检测会话失效 → 重登一次并从断点继续；重登失败/再次失效 → 任务 `FAILED`，保留已写入内容，
  失败清单包含未处理项；禁止每条失败重登；复核 T024 的每条企业启用/授权闸门（SC-182）
  与来源级失败 `errorMessage`（R137）
- [x] T048 [US62] 运行 US62 测试与安全审计：`infra_api_access_log` 无 start body、
  任务表无密码/token、上游只出现 4 个只读过程

**Checkpoint**: 三个故事全部独立可验证；重复导入/超限/凭据均有自动化覆盖。

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 文档、全量测试、quickstart、上线顺序与安全复核。

- [x] T049 [P] 更新 `CLAUDE.md`：加入 018 特性段落、上线顺序（`038` SQL → 后端 → 管理端 → 桌面端）、
  nginx `X-Forwarded-Proto`、回滚口径
- [x] T050 [P] 跑后端全量相关测试（`.../src/test/java/.../labelshop/` 与既有 member 测试）：
  `mvn -pl yudao-module-member/yudao-module-member-server -am test`（既有 `PayOrderServiceTest`
  的 5 个失败与本特性无关，不修）
- [x] T051 [P] 跑管理端：`cd yudao-cloud/yudao-ui/yudao-ui-admin-vue3 && npm test`
- [x] T052 [P] 跑桌面端：`cd /d/workspace/maxlabel/app && npm run typecheck && npm run test:unit`
- [ ] T053 按 `quickstart.md` §1–§12 手工验收并记录结果：样例账号 44 + 194 + 7、
  公共/用户库 0、失败 0（2 条空模板在被排除库）、重复导入跳过、超限、中途停用/到期、
  非 OWNER 账号、新席位不自动加入、`.lsdx` 另存与逐字节一致、**全量 `.lsdx` 可打开（失败 0）**、
  操作人一致、HTTP 拒绝、密码不泄漏
- [ ] T054 上线演练：`038` SQL → 后端 → 管理端 → 桌面端；验证回滚只停用按钮权限、不删已导入内容
- [x] T055 最终一致性：运行（或重跑）`/speckit-analyze`，CRITICAL 问题清零；确认
  `spec.md` 的 SC-174–SC-191 均有测试或 quickstart 落点

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup；完成前所有用户故事不得开始
- **US60 (Phase 3)**: 依赖 Foundational；MVP，优先完成
- **US61 (Phase 4)**: 依赖 Foundational；可在 US60 之后顺序做，也可在 US60 的服务骨架上并行
- **US62 (Phase 5)**: 依赖 Foundational 与 US60 的 `runTask`；在 US60 跑通后补齐
- **Polish (Phase 6)**: 依赖三个故事完成

### User Story Dependencies

- **US60**: 无其他故事依赖；是 US61/US62 的承载
- **US61**: 读取 US60 产生的任务/明细；可在 US60 落库后独立验证进度/结果
- **US62**: 修改 US60 的 `runTask` 去重/容量/会话策略；必须在 US60 之后

### Within Each User Story

- 测试 MUST 先写并确认失败；再写实现
- 数据层 → 服务 → 接口 → 前端
- 每个故事结束在 checkpoint 独立验证

### Parallel Opportunities

- Setup 的 T002–T009 可并行（不同文件）
- Foundational 的 T011/T013/T015/T017（测试）可并行；T012/T014/T016/T018 各自依赖对应测试
- US60 的 T020/T021/T022 测试可并行；T026/T028 桌面端任务可并行
- US61 的 T031/T032/T033 测试可并行
- US62 的 T040/T041/T042/T043 测试可并行
- Polish 的 T049–T052 可并行

---

## Parallel Example: User Story 1

```bash
# 先并行写三份失败测试
Task: "LabelshopImportServiceImplTest：start/runTask 映射与落点"
Task: "AdminLabelshopImportControllerTest：start 权限/响应"
Task: "maxlabel msdx.spec.ts：cloudContentKind"

# 再并行做桌面端的两个独立改动
Task: "msdx.ts 实现 cloudContentKind"
Task: "ipcContract.ts + 主进程 saveTextFile"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 Setup
2. 完成 Phase 2 Foundational（客户端/写入器/枚举器/任务存储）
3. 完成 Phase 3 US60
4. **STOP and VALIDATE**：用测试账号验证 44/194/7 的落点与排除库 0 条
5. 再进入 US61/US62

### Incremental Delivery

1. Setup + Foundational → 核心组件可测
2. US60 → 一次导入跑通（MVP）
3. US61 → 进度/结果/失败清单
4. US62 → 幂等/超限/安全
5. Polish → 文档、全量测试、上线演练

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- [Story] 标签映射 `spec.md` 的 US60/US61/US62
- 测试 MUST 先失败再实现；失败原因必须是断言而非拼写/导入错误
- 桌面端跨仓库 `/d/workspace/maxlabel/app`；管理端只走 `/admin-api`，C 端只走 `/app-api`
- `038` SQL 必须先于后端发布；漏跑会报 `Unknown column`/表不存在
- 明确不做：缩略图迁移、平台/公共/用户模板库、C 端门面站/小程序页面、取消/仅重试失败项

---

## Phase 7: Convergence

- [x] T056 在 `CloudTemplateDialog` 与 `ShareSpaceDialog` 为 `.lsdx` 增加「另存为」（复用 `window.maxlabel.saveTextFile`，保存原始 `.lsdx` 正文），并补测试验证另存文件与源端逐字节一致 per FR-504 / T028 (partial)
- [x] T057 **2026-11-13 所有者取消强制 HTTPS**（推翻原 FR-507 / Q137 / R129）：删除
  `MEMBER_LABELSHOP_HTTPS_REQUIRED` 与 5 个接口的协议校验，前端移除按协议阻断；
  T006 / T021 / T025 / T035 / T042 中与 HTTPS 相关的验收项一并作废
- [x] T058 补齐 Q141 / FR-493：任务中止时把未处理明细标记为 `失败（未处理）`，使其出现在失败清单，
  并同步 `failCount/processedCount`
- [x] T059 对齐 `contracts/admin-api.md` §1/§3 的响应示例（precheck / task 的容量字段为扁平结构，
  与实际 VO / 管理端类型一致）
- [x] T060 个人会员代导（Q145）：`038` 增加 `subject_type` / `running_member_id` + 唯一键，
  `org_id` / `folder_name` 放宽为可空，并新增按钮权限 `member:user:labelshop-import`
- [x] T061 后端：`enumeratePersonal`（只读我的模板库）、`startForMember` / `getMemberPrecheck` /
  `getLatestForMember`、`runTask` 个人分支（不建文件夹、不碰企业空间）；`/member/user/labelshop-import/*` 路由
- [x] T062 管理端：会员详情「个人空间」页签加「导入历史模板」入口；对话框支持 `memberId` 模式
  （隐藏企业/文件夹/席位/企业空间，失败清单与轮询复用）；补齐服务与对话框测试
- [x] T063 会员详情「标签模板」页签替换为 016 个人空间：新增管理端只读接口
  `/member/share-content/member-overview|member-page|member-get`（权限 `member:user:query`），
  新组件 `UserShareSpaceList.vue`（容量概览、类型/关键词搜索、预览/下载、导入完成自动刷新）；
  旧 `UserLabelTemplateList.vue` 及其 spec 移除

## Phase 8: Convergence

- [x] T064 拆分企业 / 会员代导的权限边界：`/member/org/labelshop-import/*` 仅接受
  `member:org:labelshop-import`，`/member/user/labelshop-import/*` 仅接受
  `member:user:labelshop-import`；`task` / `items` 按任务 `subjectType` 校验对应权限；
  补控制端越权用例 per FR-476 / FR-512 (contradicts)
  —— 已拆为 `AdminLabelshopImportController` / `AdminMemberLabelshopImportController`，
  响应转换抽到 `LabelshopImportVoAssembler`，两端均校验 `subjectType`
- [x] T065 修正会员代导的操作日志：`MEMBER_LABELSHOP_START_SUCCESS` 改为按导入对象
  （企业 / 会员）与 `subjectId` 输出，不再出现「企业【null】」；补日志文案断言
  per FR-502 / FR-512 (partial)
  —— 拆成 `MEMBER_LABELSHOP_START_SUCCESS_ORG` / `..._MEMBER`，两端分别断言
- [x] T066 为 `038` 增加幂等升级路径（新脚本或 `information_schema` 守卫的 ALTER）：
  补 `subject_type`、`running_member_id`、`uk_running_member`、`idx_member_id` 与
  `member:user:labelshop-import` 权限；更新上线顺序与回滚说明 per plan R136 / T060 (partial)
  —— 新增 `sql/mysql/039-labelshop-member-import.sql`，CLAUDE.md / quickstart §2 已更新
- [x] T067 补桌面端 `app/fixtures/lsdx/exported.lsdx`（脱敏导出文件）并在 `importLsdx`
  用例中断言可打开、失败 0；若确认不做，需在 quickstart §12 显式标注不验证理由
  per SC-183 / T022 (partial)
  —— 工作区无真实厂商导出样本，按备选方案在 quickstart §12 显式记录：仅保留脱敏
  `minimal.lsdx`，SC-183 全量可打开性由 T053 手工遍历验证
- [x] T068 在导入任务详情 / 结果中显式展示目标空间的已用、上限、剩余与是否超限
  （必要时在任务响应补 `remainingBytes`），个人与企业空间都覆盖 per FR-492 / SC-178 (partial)
  —— 任务结果区新增「个人空间用量 / 企业空间用量（剩余）」，剩余由前端按 used/quota 计算
- [x] T069 对齐个人空间落点表述：quickstart §5 与 SC-183 的「个人云模板中心」改为实际入口
  「共享空间 → 个人空间（个人云空间）」，避免手工验收到错误位置 per FR-486 / SC-183 (partial)
- [x] T070 对齐个人会员代导入口文档：FR-512 的「标签模板」改为「个人空间」，
  quickstart §2 增加 `member:user:labelshop-import` 菜单复核与会员详情入口检查
  per FR-512 / quickstart §2 (partial)

## Phase 9: Convergence

- [x] T071 补管理端对话框的轮询与失败清单测试：用假定时器验证 2–3 秒轮询、终态停止与失败清单渲染，
  并覆盖会员模式走 `/member/user/labelshop-import/items` per T033 / FR-497 / FR-499 / SC-180 (partial)
  —— 新增「轮询到终态后停止并渲染失败清单」与「会员模式轮询使用会员端点」两例
- [x] T072 补 `LabelshopImportVoAssemblerTest`：precheck 字段映射、任务响应的 used/quota/exceeded、
  按来源库统计、archiveJsonCount 与明细映射，确保响应转换重构后仍有直接测试
  per FR-492 / FR-498 / FR-499 (partial)
  —— 新增 4 例，覆盖企业任务、会员任务、precheck 与明细映射

## Phase 10: Convergence

- [x] T073 补对话框剩余 UI 断言：`orgStatus=1` 时展示「企业已停用」并禁用开始；终态汇总断言
  「原样导入、未清洗」、归档 JSON 数与按来源库分布 per T033 / T037 / FR-492 / FR-499 (partial)
  —— 新增停用企业用例，并在 latest 恢复用例中补汇总标注断言

## Phase 11: Hotfix 2026-11-13

- [x] T074 修正分类树 / 列表的 LabelShop 参数映射（线上个人代导报
  `CodingV.Template.GetCateTree HTTP 500`）：我的模板库改用 `GetCateTree(@TYPE=0,@MODE=1)`，
  列表按 `@OWNTYPE=0→12→11` 取首个非空（0 走 STS/OSS `cloud-json`）；组模板库改用
  `GetCateTree(@TYPE=12,@MODE=4)`，列表按 `@OWNTYPE=12→11`；不再读取公共树 `TYPE=14`，
  移除 `@CATEID=-1` 兜底；下载按实际命中的 `@OWNTYPE`；同步修正 research / data-model /
  contract / quickstart，并补候选值、老账号 11、云模板 0 的枚举测试 per research R124 /
  FR-485 / FR-488 (contradicts)
- [x] T075 将 LabelShop 传输层从 Hutool `HttpRequest` 换成 JDK `HttpClient`：同一账号 /
  同一请求体下，Hutool 的 `Exec` 被上游以 500 / `System.FormatException` 拒绝，JDK 客户端正常；
  同时改用 JDK 默认 TLS 校验（实测上游证书有效，不再关闭主机名 / 证书链校验）；
  保留上游 500 响应体片段日志；本地真实账号验证 `GetCateTree(0,1)` 成功 per research R130 /
  FR-481 / FR-482 (contradicts)
- [x] T076 修复个人库分类树完整性：`getCateTree` 不再过滤 `Cate_ID<=0`，保留 `CATE0`（记为
  「我的模板」）与 `LabelShop5`；解析分类 `Own_Type`，`11` 时只取 `@OWNTYPE=11`，
  `0`/缺省按 `0→12→11`；补客户端解析与枚举测试 per research R124 / FR-485 / SC-184 (partial)

## Phase 12: Personal cloud conversion & nested folders (2026-11-16)

- [x] T077 将 `cloud_to_lsdx.py` 移植为 `CloudJsonToLsdxConverter`，并用 279 条真实云端 JSON
  与 Python 输出逐条比对（0 差异）；补合成样例单测 per Q146 / FR-514 / SC-193
- [x] T078 个人空间嵌套文件夹：`040-personal-share-folders.sql`（`scope`/`owner_member_id`/`parent_id`，
  `org_id` 可空）；DO/Mapper/Service 支持多层树、按路径复用、CRUD、移动防环；App API
  `/member/share-folder/personal/*`；补服务测试 per Q147 / FR-515 / SC-194
- [x] T079 导入接线：个人 `Own_Type=0` 下载后转 `.lsdx`（失败记模板级失败）；个人内容按
  `sourceCategory` 解析/创建文件夹并写入 `folder_id`；写内容支持个人 `folderId`；
  内容分页支持 `folderId`（空=全部、0=根目录、>0=指定）per FR-514 / FR-515
- [x] T080 桌面端：IPC `share:personal-folder-*`、`mallRepository` 个人文件夹 CRUD；共享空间
  个人页签文件夹树 + 新建/改名/移动/删除，按文件夹上传/保存；管理端会员「个人空间」增加文件夹列
  per FR-515 / SC-194
- [x] T081 上线执行：生产执行 `040`；删除本次测试导入的 333 条内容/明细/任务后重新导入，
  核对 `.lsdx` 转换、文件夹树与 CRUD per Q146 / Q147 / SC-193 / SC-194
  —— 2026-11-16 task 7：333/333 成功、0 失败，333 条全部 `lsdx`；个人空间生成 5 个分类文件夹
  （锡珠钢球 80 50 / 我的模板 / 企业模板 / 斯凯孚各规格内盒粒数 / LabelShop5），无 JSON 回退
- [x] T082 管理端会员「个人空间」页签增加**左侧树形文件夹导航**：新增
  `GET /member/share-content/member-folders`；`member-page` 支持 `folderId`
  （空=全部、0=根目录、>0=指定）；前端左右布局，点击文件夹切换筛选，保留「文件夹」列
  per FR-515 / SC-194
