# Implementation Plan: 共享内容空间（个人 / 企业 / 平台，016）

**Branch**: `016-shared-content-space` | **Date**: 2026-11-05
**Spec**: [spec.md](./spec.md) | **Base**: [009 会员体系](../009-maxlabel-member-integration/spec.md) · [012 企业席位](../012-enterprise-seat-licensing/spec.md)

## Summary

把现有「云端模板 + 平台共享模板」扩成三态内容空间，并让容量跟着版本与企业授权走。

- **新增 5 张表**：空间配额锁（个人 / 企业）、企业文件夹、文件夹可见成员、个人+企业内容项、
  平台共享内容项；
- **迁移两张旧表的数据**：`maxlabel_label_template` → `maxlabel_share_content`（保留原 id），
  `maxlabel_shared_template` 中已通过的条目 → `maxlabel_platform_content`（保留原 id）；
  旧表**不删**，作为回滚与对账依据；
- **关闭会员投稿**：`/member/cloud-template/submit-share|cancel-share` 返回明确错误；
  平台内容只由管理端上传 / 改 / 下架（Q115）；
- **容量闸门**：个人按 `edition`（standard 10 MB / professional·enterprise 100 MB），
  企业 300 MB 共享池；上传与删除都在空间行锁内串行，先算用量再写入（Q116、Q121）；
- **企业空间双重门槛**：席位分配（未撤销）决定**能否访问**，企业授权有效决定**能否写入**；
  OWNER / ADMIN 不豁免席位（Q118）；
- **企业文件夹**：单层；管理员建夹并指定可见成员，成员只能进被授权的夹；
  只有空文件夹可删除（Q119、Q120）；
- **桌面端三页签**：个人 / 企业 / 平台；管理端新增「平台共享内容」页，旧「共享模板审核」页退役。

## Technical Context

**语言 / 栈**：Java 17+（yudao-cloud）+ Vue 3 / TypeScript（admin-vue3、maxlabel 桌面端）
**数据**：MySQL；5 张新表；旧表数据迁移；沿用 `tenant_id`、`BaseDO`、逻辑删除
**文件存储**：模板 / txt / csv 存 `longtext`，excel / 图片走 `infra` 的 `FileApi`（OSS / 本地）
**测试**：JUnit 5 + Mockito（member 模块）+ Vitest（admin-vue3、maxlabel 桌面端纯函数与组件）
**接口**：C 端（桌面端）只走 `/app-api`，管理端只走 `/admin-api`（宪法）
**约束**：单文件 ≤ 16 MB（配置项 `yudao.maxlabel.share.content-max-bytes`，默认 16 MB）；
分页 ≤ 200；容量与权限判定全部在服务端
**规模**：企业数量少、单企业成员/文件夹为几十到几百；不做跨租户聚合
**文件读取**：文件类内容经 `FileApi.presignGetUrl` 返回 5 分钟预签名地址；存储后端不支持签名时退回原 URL

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 每个新 Service / Controller 先写失败测试（容量、席位门槛、文件夹 ACL、平台权限、兼容端点）；桌面端与 admin 的纯逻辑先写 Vitest |
| **II. 先澄清** | ✅ Q115~Q122 已拍板；剩余口径（文件夹单层、平台需登录）写入 Assumptions |
| **III. 复用** | ✅ 复用云端模板清洗、`FileApi`、`MaxLabelOrgSeatService`、`MaxLabelOrgLicenseService`、`MemberSubscriptionService.getCurrent`、admin 菜单权限写法；不新建账号、支付或存储引擎 |
| **IV. 最小改动** | ✅ 5 张新表 + 两张旧表只读迁移；`member_subscription`、`maxlabel_org*` 不改语义（仅成员退出时清理 ACL 行）；桌面端只扩模板库入口 |
| **V. 可验证** | ✅ SC-149~SC-160 在 quickstart 有逐条落点；容量、越权、席位撤销、空文件夹删除均可自动化 |

**设计后复核**：Phase 0/1 未新增违规——仍是 MySQL 单一存储；C 端只用 `/app-api`、管理端只用
`/admin-api`；未引入新前端框架或 UI 组件库；测试先行顺序在任务分解中保持。

## 关键设计决策

完整论证见 [research.md](./research.md)（R99~R112）：

| # | 决策 |
|---|---|
| R99 | 个人 + 企业内容统一进 `maxlabel_share_content`；迁移 `maxlabel_label_template` 并**保留原 id**，旧表只读 |
| R100 | 平台内容独立表 `maxlabel_platform_content`；迁移 `maxlabel_shared_template` 已通过条目，旧审核表只读 |
| R101 | 容量用 `maxlabel_share_space` 行锁串行：锁内 `SUM(size_bytes)` 校验 → 写入 → 回写 `used_bytes`；删除同事务释放 |
| R102 | 版本容量：`edition` 为 `professional`/`enterprise` → 100 MB，其余 → 10 MB；企业池固定 300 MB，不叠加、不加购 |
| R103 | 企业访问矩阵：读 = 席位 `status=0`；写 = 席位 + 企业启用 + 有未过期未退款批次；管理员不豁免席位 |
| R104 | 文件夹单层、无根目录内容；管理员可建/改/删夹与改可见名单；非空夹不可删；可见名单按具体成员 |
| R105 | 文件策略：模板/txt/csv 存文本并做 UTF-8 大小校验；excel/图片经 `FileApi` 存 URL；白名单 + 16 MB 上限 |
| R106 | 平台内容不经审核；会员投稿端点返回 `MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED`；历史条目迁移后由管理端下架/删除 |
| R107 | 接口兼容：C 端旧 `/member/cloud-template/*` 与 `/member/shared-template/*` 保留并代理到新服务；管理端旧端点保持原样。先发后端再发桌面端 |
| R108 | 桌面端「共享模板库」改为三页签内容库；移除「发布到共享库」与本地「组模板库」；原本地共享库改名「本机模板库」；企业页签含文件夹管理与容量条 |
| R109 | 管理端新增「平台共享内容」页（上传 / 编辑 / 上下架 / 删除）；旧「共享模板审核」页与菜单下线，旧端点保留但无 UI |
| R110 | 个人空间入口只要求登录（Q122 不可分享）；企业空间入口要求席位；平台内容要求登录 |
| R111 | 删除内容只软删数据库行（对用户不可恢复），OSS 文件不主动删（孤立文件不计配额）；由运维按存储策略清理 |
| R112 | 上线顺序：`033-shared-content-space.sql` → 后端 → 管理端 → 桌面端；旧表保留，回滚不丢数据 |

## Project Structure

### Documentation（本特性）

```text
specs/016-shared-content-space/
├── plan.md · research.md · data-model.md · quickstart.md
├── contracts/app-api.md · contracts/admin-api.md
└── checklists/requirements.md
```

### Source Code

```text
yudao-cloud/
├── sql/mysql/033-shared-content-space.sql               # 5 表 + 两张旧表迁移 + 菜单权限（幂等）
├── yudao-module-member/
│   ├── yudao-module-member-api/.../enums/ErrorCodeConstants.java
│   │   # 新增 1_004_016_xxx：内容 / 容量 / 文件夹 / 席位只读 / 平台内容 / 投稿关闭
│   └── yudao-module-member-server/src/main/java/.../
│       ├── dal/dataobject/share/
│       │   ├── MaxLabelShareSpaceDO.java                 # scope + owner_id + used_bytes
│       │   ├── MaxLabelShareFolderDO.java
│       │   ├── MaxLabelShareFolderMemberDO.java
│       │   ├── MaxLabelShareContentDO.java               # 个人 / 企业内容
│       │   └── MaxLabelPlatformContentDO.java            # 平台内容
│       ├── dal/mysql/share/{...Mapper}.java
│       ├── service/share/
│       │   ├── ShareSpaceService.java                    # 版本配额、席位/授权判定、overview
│       │   ├── ShareContentService.java                  # 上传 / 列表 / 取正文 / 删除 / 兼容旧模板
│       │   ├── ShareFolderService.java                   # 建改删夹、可见名单、可见性校验
│       │   └── PlatformContentService.java               # 平台内容查询 / 管理端 CRUD / 旧共享库代理
│       ├── enums/LogRecordConstants.java                 # 文件夹与平台内容的关键操作留痕
│       ├── service/org/MaxLabelOrgMemberServiceImpl.java # 退出/移出时清理文件夹 ACL 行
│       ├── controller/app/share/{AppShareSpaceController,AppShareFolderController,
│       │                             AppShareContentController,AppPlatformContentController}.java
│       ├── controller/app/share/vo/*.java
│       ├── controller/app/template/AppLabelTemplateController.java   # 改为代理新服务
│       ├── controller/app/template/AppSharedTemplateController.java  # 改为代理平台内容
│       ├── controller/admin/share/AdminPlatformContentController.java
│       ├── controller/admin/share/vo/*.java
│       ├── controller/admin/template/AdminLabelTemplateController.java  # 会员云端模板预览改读新表
│       ├── controller/admin/template/AdminSharedTemplateController.java # 保持原样（旧表），无 UI
│       └── src/test/java/.../share/*Test.java            # 先写测试
├── yudao-ui/yudao-ui-admin-vue3/src/
│   ├── api/member/platformContent/index.ts
│   └── views/member/platformContent/{index.vue,PlatformContentForm.vue}
└── yudao-ui/yudao-ui-admin-vue3/src/views/member/template/review/  # 下线：删除页面与菜单

maxlabel/（跨仓库，桌面端）
├── app/src/main/shareContentRepository.ts                # 新 API：overview / page / get / upload / delete
├── app/src/main/shareFolderRepository.ts                 # 企业文件夹与可见名单
├── app/src/main/ipc/registerShareContentIpc.ts           # 新增 IPC 通道与入参校验
├── app/src/renderer/src/dialogs/ShareSpaceDialog.tsx     # 三页签（个人 / 企业 / 平台）
├── app/src/renderer/src/dialogs/CloudDialog.tsx          # 移除“发布到共享库”“组模板库”
└── app/src/renderer/src/...                              # 入口按钮、i18n
```

## 分阶段实施

1. **服务端数据层**：5 张表 DDL + 迁移 + DO/Mapper（验证：`mvn -pl yudao-module-member/yudao-module-member-server -am test`）
2. **服务端容量与权限**：`ShareSpaceService` / `ShareFolderService` 单测先行（验证：配额、席位、ACL、只读）
3. **服务端内容与平台**：`ShareContentService` / `PlatformContentService` 单测与控制器测（验证：上传、删除、兼容端点）
4. **管理端**：平台内容页 + 菜单 + Vitest（验证：普通用户不可见入口、上传/下架）
5. **桌面端**：三页签、文件夹管理、容量条、移除投稿（验证：typecheck + 单测；e2e 攒批跑）
6. **上线**：SQL → 后端 → 管理端 → 桌面端（验证：quickstart §9）

## Complexity Tracking

无宪法违规。跨仓库（maxlabel 桌面端）沿用 009 的既有做法；新增表与旧表迁移属于本特性的必要数据模型变更，
没有可用的既有表能同时承载「个人 / 企业 / 平台」三种归属与容量统计。
