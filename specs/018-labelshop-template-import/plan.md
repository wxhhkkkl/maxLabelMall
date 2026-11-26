# Implementation Plan: LabelShop 模板导入（018）

**Branch**: `018-labelshop-template-import` | **Date**: 2026-11-12
**Spec**: [spec.md](./spec.md) | **Base**: [016 共享内容空间](../016-shared-content-space/plan.md) · [012 企业席位](../012-enterprise-seat-licensing/plan.md) · [009 会员体系](../009-maxlabel-member-integration/plan.md)

## Summary

在管理端企业详情「成员与席位」页加入一次性的 **LabelShop → 本平台** 模板迁移能力：运营输入客户
LabelShop 账号密码后，服务端只读地读取 `我的模板库`（`Own_Type=12`）与 `组模板库`（`Own_Type=13`）、
`企业/组织模板`（`Own_Type=1`），分别写入该企业 OWNER 的个人空间与当前企业的企业空间；
`公共模板库` / `用户模板库` 一律不读取、不写入。

- **新增 2 张表**：导入任务（进度、状态、计数、目标文件夹、操作人、来源账号掩码）与导入明细
  （来源库 / 分类 / 来源 UUID / 目标 / 内容 id / 结果 / 错误）；不改 016 的 5 张表结构；
- **每次导入新建** `<时间>导入` 企业文件夹，写入导入开始时全体持席成员的可见名单；
  不选择、不复用、不修改既有文件夹；
- **长任务**：专用线程池 + 任务表 + 心跳；同一企业唯一进行中任务；服务重启后标记中断；
- **容量**：按 Q136 不受 016 容量闸门限制，允许超限；仍按实际字节计入已用，超出后转 016 的只读；
- **原样导入**：按 Q138 不做服务端清洗；`.lsdx` 原样存文本，企业/组织私有 JSON 原样归档并标注
  「暂不可直接编辑」；
- **可用性**：桌面端云端加载识别 `.lsdx` 并复用现有 `importLsdx`；`.lsdx` 可在编辑器打开并另存为
  本地文件；企业文档归档 JSON 只允许另存/下载；
- **安全**：HTTP / HTTPS 均可（2026-11-13 取消强制 HTTPS）；密码不落库、不入日志、不回显；来源账号只记掩码；对 LabelShop 只调用
  4 个只读过程；
- **中途停用 / 来源级失败**：每条写入前重查企业启用与授权，失效即停止新增写入；STS / 公司 / 分类树级失败
  记来源级失败，其余来源继续。

## Technical Context

**Language/Version**: Java 17+（yudao-cloud / Spring Boot 3）；TypeScript + Vue 3（`yudao-ui-admin-vue3`）；TypeScript + React/Electron（跨仓库 `maxlabel` 桌面端）

**Primary Dependencies**: Spring Boot 3、MyBatis-Plus、JDK `HttpClient`、Hutool 5.8.47（`DigestUtil`）、Jackson；Element Plus；桌面端既有 `io/lsdxImport.ts`

**Storage**: MySQL；新增 `maxlabel_labelshop_import_task`、`maxlabel_labelshop_import_item` 两张表；导入的模板正文写入既有 `maxlabel_share_content`（`storage_type=1`、`content_type=1`），不新增对象存储桶，不改 016 表结构

**Testing**: JUnit 5 + Mockito（member-server）；Vitest（admin-vue3、maxlabel）；客户端协议用纯函数测试 + 可替换传输层；不引入 WireMock/MockWebServer

**Target Platform**: Linux 应用机（单实例，`8.146.233.197`）；管理端浏览器（HTTP / HTTPS 入口）；Windows 桌面端

**Project Type**: 多端 Web 服务（`/admin-api` 管理端、`/app-api` C 端）+ 桌面端

**Performance Goals**: 提交后 ≤ 10 秒看到任务已开始；样例账号 245 条导入在数分钟内完成；任务轮询 2–3 秒；支持数千条/近百 MB 账号，按批推进，单条失败不阻塞

**Constraints**: HTTP / HTTPS 均可（不强制 HTTPS）；LabelShop 只读（4 个过程白名单）；密码不持久化；016 的容量与清洗口径按 spec Q136 / Q138 例外；桌面端跨仓库最小改动；单实例部署假设（重启恢复按启动时清理）；中途停用/到期按条拦截；来源级失败不阻断其余来源

**Scale/Scope**: 企业数量数十级；每企业同时只允许 1 个导入任务；单任务明细可达数千行；来源账号通常 2 家公司、245 条纳入模板

**NEEDS CLARIFICATION**: 无（spec Q134–Q144 已全部拍板）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 先写失败测试：来源映射 / 去重 / 容量例外 / 所有者归属 / 文件夹 ACL 快照 / 会话重登 / 任务状态 / 控制器 / 管理端对话框 / 桌面端 `.lsdx` 分发；红-绿-重构后再合入 |
| **II. 先澄清** | ✅ spec Q134–Q144 已拍板；plan 无新增未决口径 |
| **III. 复用既有能力** | ✅ 复用 016 的 `maxlabel_share_content` / 文件夹 / 文件夹成员 Mapper 与 `ShareSpaceService` 的空间锁与用量刷新；复用 012 的 `MaxLabelOrgService` / `MaxLabelOrgMemberService` / `MaxLabelOrgSeatService` / `MaxLabelOrgLicenseService`；复用管理端 `Dialog` / `v-hasPermi` / 请求封装；桌面端复用既有 `importLsdx` |
| **IV. 最小改动** | ✅ 2 张新表 + 1 个新 Controller/Service/Client；016 表结构零改动；管理端只加一个按钮 + 一个对话框；桌面端只改云端内容分发与 2 个对话框的签名；不动 C 端门面站/小程序 |
| **V. 增量可验证** | ✅ SC-174–SC-191 在 tasks.md 的测试或 `quickstart.md` §1–§12 有落点；SC-182（每条写入前拦截）、SC-183（fixture + quickstart 全量抽查）、SC-187（非 OWNER 账号）、SC-188（新席位）、SC-190（删除权 + 操作人）已补显式步骤；明确列出不验证项 |

**设计后复核**：Phase 0/1 未新增违规——仍是 MySQL 单一存储；管理端只走 `/admin-api`；未引入新前端框架或 UI 组件库；测试先行的顺序在任务分解中保持。016 的容量与清洗例外来自 spec Q136 / Q138 的所有者拍板，属于产品口径，不是对宪法原则的豁免。

## 关键设计决策

完整论证见 [research.md](./research.md)（R124–R138）：

| # | 决策 |
|---|---|
| R124 | 来源映射以 `Own_Type` 为准：`12=我的模板库→OWNER 个人空间`、`13=组模板库→企业空间`、`1=企业/组织模板→企业空间`；`14` 与其他类型不读取。**树参数（以完整版脚本为准）**：个人库 `@TYPE=0,@MODE=1`、列表 `@OWNTYPE=0→12→11`（首个非空，0 为 OSS cloud-json）；组库 `@TYPE=12,@MODE=4`、列表 `@OWNTYPE=12→11`；企业库用 `@CATEID=0,@OWNTYPE=1,@UID=公司ID` |
| R125 | 导入内容写既有 `maxlabel_share_content`，但**绕过** 016 的扩展名校验与服务端清洗（Q138）：`.lsdx` 以 `text/xml` 文本存储、名称保留 `.lsdx`；企业私有 JSON 以 `application/json` 归档、`editable=0`。桌面端按内容而非扩展名识别 |
| R126 | 幂等去重放导入明细表：任务内唯一 `(tenant_id, task_id, source_ref)`，跨任务按 `(tenant_id, target_scope, target_owner_id, source_ref)` 查历史成功项；命中时再查 `content_id` 是否仍存在，已删除则允许重新导入。016 表不加列 |
| R127 | 长任务用专用 `ThreadPoolTaskExecutor` + 任务表 + `heartbeat_time`；异步入口显式 `TenantUtils.execute(tenantId, ...)`；启动时跨租户把残留 `PENDING/RUNNING` 任务标记为中断并释放 `running_org_id` |
| R128 | 同一企业并发用 `running_org_id` 可空唯一键（MySQL 允许多个 NULL）：创建任务时写入 orgId，终态置 NULL |
| R129 | ~~HTTPS 强制在 Controller 校验 `request.isSecure()` 或 `X-Forwarded-Proto=https`；HTTP 入口前端禁用按钮；nginx 必须设置 `X-Forwarded-Proto $scheme`~~ → **2026-11-13 所有者取消强制 HTTPS**：前后端均不按协议限制，nginx 无需为本功能配置该头 |
| R130 | LabelShop 客户端用 JDK `HttpClient`（2026-11-13 从 Hutool 切换：同一请求 Hutool 被上游 500 拒绝，JDK 正常）；默认 TLS 校验；`mid` 由主机名派生且可配置；UA 用可配置的 6.39 兜底值；登录支持 token 与 `cvstoken/cvsuid` 两种来源 |
| R131 | 密码只在 `start` 请求体中出现：字段名必须为 `password`（框架访问日志默认脱敏），并对 `start` 加 `@ApiAccessLog(requestEnable=false)`；任务表只存掩码账号；操作日志只记 orgId 与 taskId |
| R132 | 容量按 Q136 不校验上限；仍按批次 `lockSpace` + `refreshUsedBytes` 维护 `used_bytes` 缓存；导入后若超限，016 的常规上传闸门自然拒绝 |
| R133 | 文件夹：每次新建 `<时间>导入`（`yyyyMMddHHmmss导入`），`creator_member_id=OWNER`；ACL 写入导入开始时全体有效席位成员；不改既有文件夹 |
| R134 | 任务/明细状态机与统计：任务 `0待执行/1进行中/2已完成/3已失败/4已中断`；明细 `0待处理/1成功/2跳过/3失败`；任务详情返回按来源库分组的统计与失败分页 |
| R135 | 管理端加「导入历史模板」按钮 + 对话框；桌面端 `onLoad(content, name?)` 增加名称参数，`App.tsx` 用 `cloudContentKind` 分发 `.lsdx` / `.doc-json` / `.archive-json` |
| R136 | 上线顺序：`038-labelshop-template-import.sql` → 后端 → 管理端 → 桌面端；回滚只停用菜单与权限，不自动删除已导入内容 |
| R137 | 来源级失败（STS / 某公司列表 / 个人或组分类树）：该来源记任务 `error_message`，其余来源继续；全部来源失败才任务失败；不创建残缺内容 |
| R138 | 中途停用 / 授权到期：`runTask` **每条写入前**重查企业启用与授权，失效即停止新增写入、保留已写入内容，任务置 `FAILED` 并记录原因 |

## Project Structure

### Documentation（本特性）

```text
specs/018-labelshop-template-import/
├── plan.md · research.md · data-model.md · quickstart.md
├── contracts/admin-api.md · contracts/labelshop-upstream.md
└── checklists/requirements.md
```

### Source Code

```text
yudao-cloud/
├── sql/mysql/038-labelshop-template-import.sql        # 2 表 + 菜单/权限（幂等）
├── yudao-module-member/
│   ├── yudao-module-member-api/.../enums/
│   │   ├── ErrorCodeConstants.java                     # 1_004_018_xxx
│   │   └── LogRecordConstants.java                     # 导入任务关键操作留痕
│   └── yudao-module-member-server/src/main/java/.../
│       ├── dal/dataobject/labelshop/
│       │   ├── LabelshopImportTaskDO.java
│       │   └── LabelshopImportItemDO.java
│       ├── dal/mysql/labelshop/
│       │   ├── LabelshopImportTaskMapper.java
│       │   └── LabelshopImportItemMapper.java
│       ├── service/labelshop/
│       │   ├── LabelshopImportService.java
│       │   ├── LabelshopImportServiceImpl.java
│       │   ├── LabelshopClient.java                    # 只读协议 + OSS 签名
│       │   ├── LabelshopSourceEnumerator.java          # 12/13/1 枚举
│       │   ├── LabelshopImportContentWriter.java       # 原样写入 + 明细
│       │   ├── LabelshopImageImporter.java             # 外链图片下载 + 入库 + 内嵌（Q148~Q151）
│       │   ├── LabelshopImportTaskStore.java           # 任务/心跳/恢复
│       │   ├── LabelshopImportProperties.java
│       │   ├── LabelshopImportExecutorConfig.java      # 专用线程池
│       │   ├── LabelshopImportRecoveryRunner.java      # 启动清理残留任务
│       │   └── dto/...                                 # 来源树、列表项、任务统计
│       └── controller/admin/labelshop/
│           ├── AdminLabelshopImportController.java
│           └── vo/...                                  # precheck / start / task / item
├── yudao-module-member/yudao-module-member-server/src/test/java/.../labelshop/*Test.java  # 先写测试
├── yudao-ui/yudao-ui-admin-vue3/src/
│   ├── api/member/labelshopImport/index.ts
│   └── views/member/org/
│       ├── OrgDetail.vue                               # 成员与席位页签加按钮 + 挂载对话框
│       ├── LabelshopImportDialog.vue
│       └── LabelshopImportDialog.spec.ts

maxlabel/（跨仓库，桌面端）
├── app/src/renderer/src/io/msdx.ts                     # cloudContentKind(content, name)
├── app/src/renderer/src/io/lsdxImport.spec.ts          # fixture 解析测试
├── app/src/renderer/src/App.tsx                        # onCloudLoad 分发 .lsdx / .json
├── app/src/renderer/src/dialogs/CloudTemplateDialog.tsx # onLoad 传 name；`.lsdx` 另存
├── app/src/renderer/src/dialogs/ShareSpaceDialog.tsx    # onLoad 传 name；`.lsdx` / 归档 JSON 另存
└── app/src/renderer/src/features/shell/ModalHost.tsx    # onCloudLoad 签名
```

**Structure Decision**：沿用 016/012 的 member 模块单体分包；导入任务与 016 内容同库同模块，
避免跨服务调用；管理端沿用既有 `views/member` 与 `api/member`；桌面端只做内容分发的最小改动。

## 分阶段实施

1. **数据层**：`038` DDL + DO/Mapper + 先写 Mapper/状态机测试
   （验证：`mvn -pl yudao-module-member/yudao-module-member-server -am test -Dtest=*Labelshop*`）
2. **LabelShop 客户端**：登录、Exec 签名、分类树/列表、模板下载、STS/OSS 签名、只读白名单、SSL/UA/mid
   （验证：纯函数与假传输层测试；不访问真实 LabelShop）
3. **导入编排**：枚举 → 明细入库 → 逐条导入 → 去重 → 容量例外 → 计数/心跳 → 重登一次 →
   中途停用/到期拦截 → 来源级失败 → 中断恢复
   （验证：Service 单测 + `quickstart.md` 覆盖 SC-174–SC-191）
4. **管理端接口**：precheck / start / task / latest / items + 权限 + 脱敏
   （验证：Controller 测试 + 访问日志断言）
5. **管理端页面**：按钮、对话框、轮询、失败清单、授权/停用提示
   （验证：`npm test`，新增对话框 spec）
6. **桌面端**：`cloudContentKind` 分发、`.lsdx` 打开与另存、归档 JSON 另存
   （验证：Vitest 纯函数 + typecheck；e2e 攒批）
7. **上线**：SQL → 后端 → 管理端 → 桌面端；quickstart §1–§12
   （验证：quickstart 手工清单）

## Complexity Tracking

无宪法违规。需要说明的两点：

| 事项 | 为什么需要 | 更简单的替代为何不可行 |
|---|---|---|
| 016 容量闸门例外（Q136） | 客户历史模板可能超过 10/100/300 MB；迁移工具必须能一次搬完 | 预检整批拒绝会让 91 MB 量级账号无法迁移；按序截断会留下半批数据 |
| 016 模板清洗例外（Q138） | 与 Python 备份口径一致；`.lsdx` 不在现有 `.msdx` 清洗器覆盖范围 | 为 `.lsdx` 新写 XML 清洗器属于本期未要求的能力；所有者已明确接受共享风险 |
