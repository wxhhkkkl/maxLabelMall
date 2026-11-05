# Tasks: 共享内容空间（个人 / 企业 / 平台，016）

**Input**: Design documents from `/specs/016-shared-content-space/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: 宪法原则 I（测试先行，不可协商）要求测试任务前置；每个测试任务 MUST 先失败再实现。
DDL、配置、DO/Mapper、枚举等结构性任务不写单元测试，属已声明的结构性例外（沿用 012）。

**Organization**: 按用户故事分组，US50~US53 与 spec 对应；**任务 ID 按执行顺序连续编号**。

**Path Conventions**:
- 服务端：`yudao-cloud/yudao-module-member/...`
- 管理端：`yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/...`
- 桌面端（跨仓库）：`../maxlabel/app/...`（与 `maxLabelMall` 同级）

---

## Phase 1: Setup（共享基础设施）

**Purpose**: 表结构、错误码、枚举、DO/Mapper 等所有故事共用的底座。

- [X] T001 创建 `yudao-cloud/sql/mysql/033-shared-content-space.sql`：5 张新表（`maxlabel_share_space` 唯一键 `(tenant_id, scope, owner_id)`；`maxlabel_share_folder`；`maxlabel_share_folder_member` 唯一键 `(tenant_id, folder_id, member_id)`；`maxlabel_share_content`；`maxlabel_platform_content`），按 `data-model.md` 的字段与索引写；包含两张旧表的 `INSERT ... SELECT` 迁移（保留原 id、`WHERE NOT EXISTS` 幂等）、`maxlabel_share_space` 用量回填、平台内容菜单与权限、软删旧 `member:shared-template:query/audit/offline` 菜单
- [X] T002 在 `yudao-cloud/yudao-module-member/yudao-module-member-api/src/main/java/cn/iocoder/yudao/module/member/enums/ErrorCodeConstants.java` 新增 `1_004_016_xxx` 段错误码（`MEMBER_SHARE_SCOPE_INVALID`、`..._CONTENT_NOT_FOUND`、`..._CONTENT_TYPE_INVALID`、`..._CONTENT_TOO_LARGE`、`..._CONTENT_INVALID`、`..._QUOTA_EXCEEDED`、`..._FOLDER_NOT_FOUND`、`..._FOLDER_NOT_EMPTY`、`..._FOLDER_NO_ROOT`、`..._SEAT_REQUIRED`、`..._ENTERPRISE_READ_ONLY`、`..._FOLDER_MEMBER_INVALID`、`MEMBER_PLATFORM_CONTENT_NOT_FOUND`、`MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED`、`MEMBER_SHARE_FILE_STORAGE_FAILED`）
- [X] T003 创建 `.../service/share/ShareProperties.java`（`@ConfigurationProperties(prefix = "yudao.maxlabel.share")`，`contentMaxBytes` 默认 16777216）与 `ShareConstants.java`（个人 10 MB、专业/企业个人 100 MB、企业 300 MB）；在 `yudao-cloud/yudao-server/src/main/resources/application.yaml` 增加 `yudao.maxlabel.share.content-max-bytes: 16777216`（有默认值，允许不配）
- [X] T004 [P] 创建 `.../dal/dataobject/share/MaxLabelShareSpaceDO.java`：`scope`（1 个人 / 2 企业）、`ownerId`、`usedBytes`
- [X] T005 [P] 创建 `.../dal/dataobject/share/MaxLabelShareFolderDO.java`：`orgId`、`name`（≤64）、`creatorMemberId`
- [X] T006 [P] 创建 `.../dal/dataobject/share/MaxLabelShareFolderMemberDO.java`：`folderId`、`memberId`
- [X] T007 [P] 创建 `.../dal/dataobject/share/MaxLabelShareContentDO.java`：`scope`、`ownerMemberId`、`orgId`、`folderId`、`contentType`（1 模板 2 txt 3 excel 4 csv 5 图片）、`name`（≤255）、`storageType`（1 文本 / 2 文件）、`content`、`fileUrl`、`mimeType`、`sizeBytes`、`contentHash`、`widthMm`、`heightMm`、`category`、`keywords`
- [X] T008 [P] 创建 `.../dal/dataobject/share/MaxLabelPlatformContentDO.java`：`contentType`、`name`、`storageType`、`content`、`fileUrl`、`mimeType`、`sizeBytes`、`contentHash`、`widthMm`、`heightMm`、`category`、`keywords`、`status`（1 上架 0 下架）、`publisherUserId`、`publishedTime`
- [X] T009 [P] 创建 `.../dal/mysql/share/MaxLabelShareSpaceMapper.java`：`selectByScopeAndOwner`、`selectForUpdate`（`FOR UPDATE`）、`insertIgnore` 或唯一键冲突兜底
- [X] T010 [P] 创建 `.../dal/mysql/share/MaxLabelShareFolderMapper.java`：`selectListByOrgId`、`selectByIdAndOrgId`、`countContentByFolderId`
- [X] T011 [P] 创建 `.../dal/mysql/share/MaxLabelShareFolderMemberMapper.java`：`selectMemberIdsByFolderId`、`selectVisibleFolderIds`、`deleteByFolderId`、`deleteByMemberId`
- [X] T012 [P] 创建 `.../dal/mysql/share/MaxLabelShareContentMapper.java`：个人/企业分页、`selectByIdAndScope`、`sumSizeByScopeAndOwner`、`sumSizeByOrgId`、`sumSizeByFolderId`、`update`/`delete`
- [X] T013 [P] 创建 `.../dal/mysql/share/MaxLabelPlatformContentMapper.java`：按类型/分类/关键词/状态分页、详情
- [X] T014 [P] 在 `.../enums/share/` 创建 `MaxLabelShareScopeEnum`、`MaxLabelShareContentTypeEnum`、`MaxLabelShareStorageTypeEnum`、`MaxLabelPlatformContentStatusEnum`
- [X] T015 [P] 在 `yudao-cloud/yudao-module-member/yudao-module-member-server/src/main/java/cn/iocoder/yudao/module/member/enums/LogRecordConstants.java` 增加内容创建/上传/删除、文件夹创建/改名/删除/名单变更、平台内容上传/编辑/上下架/删除的日志常量

**Checkpoint**: 表与底座就绪，可开始服务层。

---

## Phase 2: Foundational（阻塞性前置）

**Purpose**: 三个核心 Service、文件存储与成员退出清理；所有用户故事共用。

**⚠️ CRITICAL**: 本阶段完成前不要开始用户故事。

- [X] T016 先写失败测试 `.../src/test/java/cn/iocoder/yudao/module/member/service/share/ShareSpaceServiceImplTest.java`：个人配额（standard 10 MB、professional/enterprise 100 MB）；企业读（席位 status=0）/写（席位 + 企业启用 + 未过期未退款批次）矩阵；授权过期只读；无席位管理员拒绝；`usedBytes/remainingBytes/canUpload` 计算；**FR-403：加入/退出企业、企业停用不改变个人空间配额与内容**
- [X] T017 实现 `.../service/share/ShareSpaceService.java` 与 `ShareSpaceServiceImpl.java`：复用 `MemberSubscriptionService.getCurrent`、`MaxLabelOrgSeatService.getActiveByMemberId`、`MaxLabelOrgLicenseService.getUnexpiredList`、`MaxLabelOrgService.getOrg`；锁内 `SUM` 与 `used_bytes` 回写；输出 `ShareSpaceOverviewRespVO`（个人 + 企业，含 `edition/quotaBytes/usedBytes/remainingBytes/canUpload/readOnly`）
- [X] T018 先写失败测试 `.../service/share/ShareFolderServiceImplTest.java`：管理员建/改名/删空夹；非空夹拒绝；成员只看名单内文件夹；管理员绕过名单；无席位拒绝；授权过期拒绝写；名单成员必须属于本企业
- [X] T019 实现 `.../service/share/ShareFolderService.java` 与 `ShareFolderServiceImpl.java`：`list/create/update/delete/updateMembers/memberList`；删除前检查文件夹内容为空；删除时清理 ACL 行；名单保存为「删旧 + 插新」
- [X] T020 [P] 先写失败测试 `.../src/test/java/.../service/share/ShareFileStorageTest.java`：文本/文件两种存储、`FileApi.createFile` 调用、`presignGetUrl` 5 分钟、异常转 `MEMBER_SHARE_FILE_STORAGE_FAILED`
- [X] T021 实现 `.../service/share/ShareFileStorage.java`：封装 `FileApi.createFile` 与 `presignGetUrl`；直到 T020 通过
- [X] T022 先写失败测试 `.../service/share/ShareContentServiceImplTest.java`：个人分页/取正文/创建/上传/更新/删除；模板清洗（保留数据集行）；16 MB 与类型白名单；配额锁调用；删除释放容量；越权不可见；**企业矩阵**（无席位、席位撤销、授权过期只读、管理员无席位、300 MB 池、成员删他人被拒、管理员删任意成功、根目录拒绝、被取消文件夹授权后仍可删自己的内容）；**内容上传/删除的 `@LogRecord` 断言**
- [X] T023 实现 `.../service/share/ShareContentService.java` 与 `ShareContentServiceImpl.java`：个人 + 企业两种 scope；文本入库、excel/图片走 `FileApi.createFile`；`downloadUrl` 用 `FileApi.presignGetUrl`（5 分钟）；模板用 `MaxLabelTemplateSanitizer.sanitize(content, false)`；企业写入校验席位/授权/文件夹可见；根目录上传拒绝；删除规则（本人只要求席位、不要求当前文件夹可见；管理员可删全部）；**删除为软删并回写 `used_bytes`**；内容上传/删除加 `@LogRecord`；直到 T022 通过
- [X] T024 [P] 先写失败测试 `.../src/test/java/.../service/org/MaxLabelOrgMemberServiceImplTest.java` 的「退出后重新加入不继承旧文件夹权限」用例
- [X] T025 [P] 修改 `.../service/org/MaxLabelOrgMemberServiceImpl.java`：`removeMember/leave` 时调用 `MaxLabelShareFolderMemberMapper.deleteByMemberId` 清理 ACL；直到 T024 通过

**Checkpoint**: 服务层与权限矩阵可单测通过，用户故事可并行开工。

---

## Phase 3: User Story 50 - 个人空间（Priority: P1 · MVP）

**Goal**: 免费用户 10 MB、专业/企业用户 100 MB；上传模板/txt/csv/excel/图片，跨设备浏览、下载、删除；超出后只读。

**Independent Test**: 免费账号上传 9.9 MB → 成功；再传 1 MB → 拒绝；删除后恢复；换设备同账号可见；他人不可见。

### Tests for US50（先失败）

- [X] T026 [P] [US50] 写失败控制器测试 `.../src/test/java/cn/iocoder/yudao/module/member/controller/app/share/AppShareContentControllerTest.java` 与 `AppShareSpaceControllerTest.java`：登录取 memberId、个人分页/详情/创建/上传/删除、未登录 401、他人内容不可见
- [X] T027 [P] [US50] 写失败兼容测试 `.../controller/app/template/AppLabelTemplateControllerTest.java`：旧 `/cloud-template/*` 返回结构不变、`submit-share/cancel-share` 返回 `MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED`
- [X] T028 [P] [US50] 写失败测试 `.../controller/admin/template/AdminLabelTemplateControllerTest.java`：后台预览读新表、`shareStatus` 恒为 null、权限点不变

### Implementation for US50

- [X] T029 [US50] 创建 `.../controller/app/share/vo/` 下的 `AppShareSpaceOverviewRespVO`、`AppShareContentPageReqVO`、`AppShareContentRespVO`、`AppShareContentDetailRespVO`、`AppShareContentSaveReqVO`、`AppShareContentUploadReqVO`，字段与 `contracts/app-api.md` 一致
- [X] T030 [US50] 实现 `.../controller/app/share/AppShareSpaceController.java`（`GET /member/share-space/overview`）与 `AppShareContentController.java`（`page/get/create/upload/update/delete`）；所有入口用 `SecurityFrameworkUtils.getLoginUserId()`；直到 T026 通过
- [X] T031 [US50] 重构 `.../controller/app/template/AppLabelTemplateController.java`：`page/get/create/update/delete` 代理到 `ShareContentService`（scope=1、contentType=1），`shareStatus` 恒为 null；删除 `MaxLabelSharedTemplateMapper` / `SharedTemplateService` 的旧依赖；`submit-share/cancel-share` 返回 `MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED`；直到 T027 通过
- [X] T032 [US50] 将 `.../service/template/LabelTemplateServiceImpl.java` 改为薄适配（不再校验订阅、不再直接写旧表），更新其测试；删除因本次改动而失效的 `requireActiveSubscription` 调用
- [X] T033 [US50] 重构 `.../controller/admin/template/AdminLabelTemplateController.java` 改为读 `maxlabel_share_content`（scope=1、contentType=1），保留 `member:cloud-template:query` 权限与返回结构；`shareStatus` 恒为 `null` 并移除对旧 `MaxLabelSharedTemplateMapper` 的依赖；直到 T028 通过
- [X] T034 [P] [US50] 桌面端**先写失败测试** `../maxlabel/app/src/main/shareContentRepository.test.ts`：容量格式化、类型白名单、错误码映射（node 环境纯函数）
- [X] T035 [P] [US50] 桌面端实现 `../maxlabel/app/src/main/shareContentRepository.ts`（`overview/page/get/create/upload/delete`）与 `../maxlabel/app/src/main/ipc/registerShareContentIpc.ts`、`../maxlabel/app/src/shared/ipcContract.ts` 的类型/通道；在 `../maxlabel/app/src/main/ipc/validation.ts` 增加入参校验；直到 T034 通过
- [X] T036 [US50] 桌面端新增 `../maxlabel/app/src/renderer/src/dialogs/ShareSpaceDialog.tsx` 的「个人」页签：列表、上传（模板/文件）、下载/打开、**删除二次确认**、容量条；从 `TemplateLibDialog.tsx` 接入入口并补 i18n
- [X] T037 [US50] 桌面端移除投稿链路：`.../dialogs/CloudDialog.tsx` 的「发布到共享库」与「组模板库」、`.../main/mallRepository.ts` 的 `submitMallTemplateShare/cancelMallTemplateShare`、`.../main/ipc/registerMallIpc.ts` 的两个 handler、`.../preload/index.ts` 的对应方法、`.../shared/ipcContract.ts` 的 `mallTemplateShare/mallTemplateUnshare` 通道与 `MaxLabelAPI` 类型；把 `.../dialogs/TemplateLibDialog.tsx` 的「共享模板库」改名为「本机模板库」，发布动作/提示改为「保存到本机模板库」
- [ ] T038 [US50] 按 `specs/016-shared-content-space/quickstart.md` §4.1 验证 SC-149 / SC-153 / SC-155 / SC-160，记录结果

**Checkpoint**: 个人空间可独立上线（MVP）。

---

## Phase 4: User Story 51 - 企业空间（Priority: P1）

**Goal**: 持席成员在授权有效期内使用企业 300 MB 池；可看被授权文件夹、上传、删自己内容；管理员可管理全部；授权到期只读。

**Independent Test**: 管理员建夹授权给成员甲；甲上传 → 管理员可删；甲删不掉管理员内容；无席位成员看不到空间；授权过期后只读。

### Tests for US51（先失败）

- [X] T039 [P] [US51] 在 `.../src/test/java/.../controller/app/share/AppShareContentControllerTest.java` 增加失败用例（服务层矩阵已在 T022 覆盖）：无席位、席位撤销、授权过期只读、管理员无席位、企业容量 300 MB、成员删他人内容被拒、管理员删任意内容成功、根目录上传拒绝
- [X] T040 [P] [US51] 桌面端**先写失败测试** `../maxlabel/app/src/main/shareContentRepository.test.ts` 的 `seatActive/licenseActive/canUpload/readOnly` 状态组合

### Implementation for US51

- [X] T041 [US51] 在 `AppShareContentController` 中启用 `scope=2` 入口（服务层已在 T023 实现）：透传席位/企业/授权/文件夹校验错误；确保 `scope=1` 行为不变；直到 T039 通过
- [X] T042 [US51] 核对 T017/T030 已输出的企业 `seatActive/licenseActive/isAdmin/readOnly/canUpload` 字段；仅补缺失字段与控制器透传，不重复实现
- [X] T043 [US51] 桌面端在 `ShareSpaceDialog.tsx` 增加「企业」页签：席位/授权状态提示、文件夹内列表、上传、**删除二次确认**、容量条；无席位或只读时禁用上传并给出原因
- [X] T044 [US51] 桌面端在 `ShareSpaceDialog.tsx` 的个人页增加「需要企业席位」提示（不展示企业空间入口，符合 Q118 与 spec Edge Cases）
- [ ] T045 [US51] 按 `quickstart.md` §4.2 验证 SC-150 / SC-154 / SC-157，记录结果

**Checkpoint**: 个人 + 企业空间均可独立工作。

---

## Phase 5: User Story 52 - 企业文件夹与可见范围（Priority: P2）

**Goal**: 管理员建/改名/删空夹，为每个夹指定可见成员；成员只能看和进入被授权的夹；管理员看全部。

**Independent Test**: 两个文件夹分别授权甲、乙；甲、乙登录只看到自己的；移除授权后刷新不可见；非空夹不能删。

### Tests for US52（先失败）

- [X] T046 [P] [US52] 写失败控制器测试 `.../src/test/java/cn/iocoder/yudao/module/member/controller/app/share/AppShareFolderControllerTest.java`：列表/建/改名/删/成员列表/保存名单；无席位、非管理员、授权过期、非空删除、名单含外企业成员等失败用例
- [X] T047 [P] [US52] 桌面端**先写失败测试** `../maxlabel/app/src/main/shareFolderRepository.test.ts`：文件夹可见性/管理员绕过/空夹删除的前端判定纯函数

### Implementation for US52

- [X] T048 [US52] 实现 `.../controller/app/share/AppShareFolderController.java` 与 `vo/`：`list/create/update/delete/member-list/update-members`，字段与 `contracts/app-api.md` §3 一致；`contentCount/usedBytes` 仅管理员返回；直到 T046 通过
- [X] T049 [US52] 桌面端在 `ShareSpaceDialog.tsx` 增加文件夹管理：管理员建/改名/**删除空夹（二次确认）**、编辑可见成员；成员按可见夹浏览与上传；删除非空夹时展示「先清空或移动」；直到 T047 通过
- [ ] T050 [US52] 结合 T025 验证「成员退出再重新加入不继承旧 ACL」；按 `quickstart.md` §4.3 验证 SC-151 / SC-158 / SC-159

**Checkpoint**: 企业文件夹与可见范围完整可用。

---

## Phase 6: User Story 53 - 平台共享（Priority: P2）

**Goal**: 仅管理端上传/编辑/上下架/删除；所有登录用户浏览、检索、下载；不占任何容量。

**Independent Test**: 普通用户无上传入口且直接调用失败；管理员上传后 ≤1 分钟可见；下架后不可见；已下载副本不受影响。

### Tests for US53（先失败）

- [X] T051 [P] [US53] 写失败测试 `.../src/test/java/cn/iocoder/yudao/module/member/service/share/PlatformContentServiceImplTest.java`：分页只返回上架、类型/分类/关键词筛选、模板清洗剥离数据集行、下架/删除幂等
- [X] T052 [P] [US53] 写失败控制器测试 `.../controller/app/share/AppPlatformContentControllerTest.java` 与 `.../controller/admin/share/AdminPlatformContentControllerTest.java`：普通会员调用管理端 403、上传/编辑/上下架/删除权限、C 端只读
- [X] T053 [P] [US53] 先写失败兼容测试 `.../controller/app/template/AppSharedTemplateControllerTest.java`：旧 `/shared-template/*` 返回结构不变、`publisherNickname = 平台运营`
- [X] T054 [P] [US53] 管理端**先写失败测试** `.../src/views/member/platformContent/index.spec.ts`：筛选、权限按钮、上下架调用
- [X] T055 [P] [US53] 桌面端**先写失败测试** `../maxlabel/app/src/main/shareContentRepository.test.ts` 的平台页签只读判定与筛选参数

### Implementation for US53

- [X] T056 [US53] 实现 `.../service/share/PlatformContentService.java` 与 `PlatformContentServiceImpl.java`：C 端分页/详情（只 status=1）、管理端 CRUD、上下架、模板清洗 `sanitize(content, true)`、文件走 `ShareFileStorage`；直到 T051 通过
- [X] T057 [US53] 实现 `.../controller/app/share/AppPlatformContentController.java` 与 `vo/`：`GET /member/platform-content/page|get`；直到 T052 通过
- [X] T058 [US53] 实现 `.../controller/admin/share/AdminPlatformContentController.java` 与 `vo/`：`page/get/upload/create/update/update-status/delete`，权限点 `member:platform-content:query|create|update|delete`；直到 T052 通过
- [X] T059 [US53] 重构 `.../controller/app/template/AppSharedTemplateController.java` 代理到 `PlatformContentService`；旧字段保留，新增 `contentType`；直到 T053 通过
- [X] T060 [P] [US53] 管理端新增 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/member/platformContent/index.ts`
- [X] T061 [US53] 管理端新增 `.../src/views/member/platformContent/index.vue` 与 `PlatformContentForm.vue`：列表、筛选、上传/新建、编辑、上下架、删除（二次确认）；权限点与 `admin-api.md` 一致；直到 T054 通过
- [X] T062 [US53] 删除旧审核页 `.../src/views/member/template/review/index.vue` 与其 `index.spec.ts`；确认 `033` 已软删旧菜单
- [X] T063 [US53] 桌面端在 `shareContentRepository.ts` 增加平台分页/详情；`ShareSpaceDialog.tsx` 增加「平台」页签（检索/筛选/下载，无上传入口）；直到 T055 通过
- [ ] T064 [US53] 按 `quickstart.md` §4.4 验证 SC-152，记录结果

**Checkpoint**: 三态空间全部可用。

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T065 [P] 按 `quickstart.md` §4.5 验证旧端点兼容（`cloud-template`、`shared-template`、`submit-share` 关闭错误）
- [X] T066 [P] 安全复核：检查 `controller/app/share/` 与 `controller/admin/share/` 的登录/权限注解；确认 `downloadUrl` 不泄露长期 URL；确认个人空间不可被同企业成员读取
- [X] T067 [P] 更新 `CLAUDE.md`：加入 016 当前特性段、SQL 033 上线顺序与旧表只读说明；复核 `specs/009-maxlabel-member-integration/spec.md` 的 FR-219 / FR-233 / FR-237 / FR-238「已被 016 取代」注记（已添加，后续修订需同步）
- [X] T068 运行服务端全量测试：`mvn -pl yudao-module-member/yudao-module-member-server -am clean test`，修复回归
- [X] T069 运行管理端测试：`cd yudao-cloud/yudao-ui/yudao-ui-admin-vue3 && pnpm vitest run`
- [X] T070 运行桌面端：`cd ../maxlabel && npm run typecheck && npm run test`；e2e 按记忆攒批跑一次
- [ ] T071 按 `quickstart.md` §4 全量手工验收 SC-149~SC-160，记录结果
- [ ] T072 按 `quickstart.md` §9 做上线顺序与回滚演练（在库副本上执行 033，重复执行验证幂等，执行回滚 SQL）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始。
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞所有用户故事**。
- **US50 (Phase 3)**: 依赖 Foundational；可先做，MVP。
- **US51 (Phase 4)**: 依赖 Foundational；企业页签依赖 US50 的 `ShareSpaceDialog` 骨架（T036）。
- **US52 (Phase 5)**: 依赖 US51 的企业空间与文件夹服务（T019/T049）；建议在 US51 之后。
- **US53 (Phase 6)**: 依赖 Foundational；服务端/管理端可与 US50/US51 并行；桌面端平台页签依赖 US50 的 `shareContentRepository.ts`（T035），需等 US50 的桌面端仓库就绪。
- **Polish (Phase 7)**: 依赖所有目标故事完成。

### User Story Dependencies

- **US50**: 无故事依赖（个人空间）。
- **US51**: 依赖 US50 的桌面端对话框骨架（T036）。
- **US52**: 依赖 US51（企业空间入口与文件夹服务）。
- **US53**: 无故事依赖（独立平台目录）。

### Within Each Story

- 测试先写并确认失败 → 再实现。
- 服务/控制器 → 桌面端/管理端 UI → 验收。
- 桌面端/管理端的纯函数与组件测试任务（T034/T040/T047/T054/T055）均**先于**对应实现任务。

### Parallel Opportunities

- Phase 1 的 T004~T015 大多可并行（不同文件）。
- Phase 2 的 T020/T024 可与 T016~T019 并行；T021 依赖 T020，T023 依赖 T019/T021，T025 依赖 T024。
- US50、US51、US53 在 Foundational 完成后可并行（US53 的桌面端页签依赖 US50 的 T035）；US52 等待 US51。
- 各故事的测试任务可并行（不同测试文件）。
- 管理端（T054/T060/T061）与桌面端（T055/T063）可并行。

---

## Parallel Example: US50

```bash
# 三个测试文件可同时写
Task: "AppShareContentControllerTest + AppShareSpaceControllerTest"
Task: "AppLabelTemplateControllerTest 兼容用例"
Task: "AdminLabelTemplateControllerTest 预览用例"

# 桌面端测试先于实现
Task: "shareContentRepository.test.ts（失败）"
Task: "shareContentRepository.ts + registerShareContentIpc.ts"
```

## Parallel Example: 三态并行

```bash
# Foundational 完成后
Developer A: US50 个人空间
Developer B: US51 企业空间（先完成可复用对话框骨架）
Developer C: US53 平台共享（服务端/管理端；桌面端页签等 US50 的 T035）
# US52 在 US51 完成后接手
```

---

## Implementation Strategy

### MVP First（US50）

1. Phase 1 + Phase 2 完成；
2. Phase 3（个人空间）完成；
3. 按 §4.1 验收；可先上线「个人 10/100 MB + 文件类型」这一增量。

### Incremental Delivery

1. Setup + Foundational；
2. US50 → 验收 → 上线；
3. US51 → 验收；
4. US52 → 验收；
5. US53 → 验收；
6. Polish + 全量 quickstart + 上线演练。

### Notes

- 并发容量只靠 Mockito 无法验证行锁；`quickstart.md` §7 用两个并发请求做手工验证。
- 旧表（`maxlabel_label_template`、`maxlabel_shared_template`）**只读**；任何新写入都必须走新表。
- 桌面端测试只收 `*.test.ts`（node 环境），不要写 `.tsx` 测试。
- 服务端测试命令必须带 `-am`，并在 IDE 跑过测试后先 `clean`，避免脏 class。

---

## Phase 8: Convergence

**Purpose**: 关闭 2026-11-05 收敛评估发现的兼容性与桌面端/管理端缺口。

- [X] T073 修复旧模板端点兼容：`AppLabelTemplateController.toSaveReq` 对没有 `.msdx` 后缀的旧模板名补后缀，`copy`/响应时再剥掉，保证旧桌面端上传/显示行为不变 per FR-399 / plan R107 (contradicts)
- [X] T074 桌面端 `ShareSpaceDialog.tsx` 增加企业文件夹改名入口，复用 `window.maxlabel.share.folders.update` par FR-406 / T049 (partial)
- [X] T075 管理端 `AdminPlatformContentRespVO` 补 `storageType`、`content`、`downloadUrl`、`mimeType`，`GET /member/platform-content/get` 返回完整正文 per admin-api §2 (partial)
- [X] T076 桌面端企业空间只读时，用 `canWriteFolder` + `enterprise.readOnly` 禁用文件夹新建/改名/删除/可见成员按钮 per FR-415 / T049 (partial)
- [X] T077 桌面端平台页签补内容类型与分类筛选，调用 `share.platform.page` 的 `contentType/category` per FR-417 / T063 (partial)
- [X] T078 无席位时把「需要企业席位」提示移到个人页或页签栏外侧，保证用户可见 per T044 (partial)
- [X] T079 桌面端保存当前模板 / 上传模板时补 `widthMm`/`heightMm`（从当前文档或 `.msdx` 正文解析），避免新模板尺寸落 0 per FR-393 (partial)

---

## Phase 9: Convergence

**Purpose**: 关闭 2026-11-05 第二次收敛评估发现的桌面端展示与旧入口缺口。

- [X] T080 在 `ShareSpaceDialog.tsx` 的文件夹行展示 `contentCount` 与 `usedBytes`（管理员可见），满足 FR-414 的「每个文件夹内容数与占用字节」 per FR-414 (partial)
- [X] T081 在 `ShareSpaceDialog.tsx` 的个人/企业概览行显示 `remainingBytes`，企业行标明「企业版」，满足 FR-395 的剩余容量与版本展示 per FR-395 (partial)
- [X] T082 用 `overview.personal.canUpload` 禁用「上传文件」并提示容量用尽，避免超配额仍可点击 per FR-402 / SC-156 (partial)
- [X] T083 为被移出文件夹名单的成员提供「我的上传」入口（跨文件夹列出本人企业内容并可删除），或提供不依赖文件夹可见性的自有内容列表 per FR-409 (partial)
- [X] T084 对齐旧「云标签模板库」`CloudTemplateDialog` 的免费用户写权限（FR-399），或引导用户使用共享空间入口 per FR-399 (partial)
- [X] T085 删除已不可达的 `MallSharedLibraryDialog`，或在 ModalHost 中说明保留理由 per plan R108 / FR-417 (unrequested)
