# Research（018 LabelShop 模板导入）

**基线**：spec Q134–Q144；016 的共享内容模型与容量口径；012 的企业 / 席位 / 授权服务；
`C:\Users\liyan\labelshop-export\labelshop_export.py`（精简版）与 `labelshop_export.py.bak`（完整版）。

---

## R124 —— 来源映射：以 `Own_Type` 为准，不采信精简脚本的库名

**Decision**：只读取三类来源，目标固定：

| 来源库 | `Own_Type` | 分类树 | 列表 / 下载 | 目标 |
|---|---|---|---|---|
| 我的模板库 | `12` | `GetCateTree(@TYPE=0, @MODE=1)` | `@OWNTYPE=0 → 12 → 11` 取第一个非空；0 为 OSS 云模板（cloud-json），11/12 为 `.lsdx` | OWNER 个人空间 |
| 组模板库 | `13` | `GetCateTree(@TYPE=12, @MODE=4)` | `@OWNTYPE=12 → 11` 取第一个非空（新账号 12、老账号 11） | 企业空间 |
| 企业/组织模板 | `1` | 无（`@CATEID=0`） | `GetCateList(@OWNTYPE=1, @UID=公司ID)` + OSS `lp/{uuid}.json` | 企业空间 |
| 公共模板库 | `14` | — | 不读取 | — |
| 用户模板库 | 其他（客户端存在页签） | — | 不读取 | — |

**Rationale**：客户端二进制中四个页签真实存在；完整版脚本第 642 行的映射为
`12=我的模板、13=群组模板、14=公共模板`。精简脚本把 `12` 叫「组模板库」、`13` 叫「公共模板库」、
`14` 叫「用户模板库」，与客户端页签和完整版映射相反。若按精简脚本命名实现，会把 2019 条
公共模板（约 91 MB）写入个人空间，与所有者口径不符。

**Alternatives considered**：
- 按精简脚本库名映射 → 错误，已排除。
- 运行时探测所有 `@TYPE/@OWNTYPE` 再推断 → 不能从接口返回推断 UI 页签名称，且会多读被排除的库；
  本期用白名单，不做探测。

**补充**：个人模板库存在「未分类」条目（Python 的 `@CATEID=-1, @OWNTYPE=12` 兜底）；
本期同样读取该兜底列表，分类路径记为 `未分类`，避免静默丢失。样例账号该列表为 0 条。

---

## R125 —— 内容落库：复用 016 内容表，但绕过扩展名校验与清洗

**Decision**：导入内容直接写入 `maxlabel_share_content`：

- `scope=1`（个人）/ `2`（企业）；个人 `owner_member_id=OWNER`，企业 `owner_member_id=OWNER`
  （Q143）、`org_id=当前企业`、`folder_id=新建文件夹`；
- `content_type=1`（模板）、`storage_type=1`（文本）、`content=原始正文`、`size_bytes=UTF-8 字节`、
  `content_hash=SHA-256`；
- `.lsdx`：`name` 以 `.lsdx` 结尾，`mime_type=text/xml`；
- 企业/组织私有 JSON：`name` 以 `.json` 结尾，`mime_type=application/json`，明细 `editable=0`；
- 名称归一：源名先去重已有 `.lsdx/.lsd/.msdx/.json`，把 `\\ / : * ? " < > |` 与控制字符替换为 `_`，
  截断到 255（含扩展名）后追加目标扩展名；原始名保留在明细 `source_name`；
  空名回退到 `source_ref`（或 `未命名-<uuid前8>`），保证内容行始终有可展示名称；
- `width_mm/height_mm`：`.lsdx` 从 `<paper ... width="21000" height="29700">` 解析（÷100），
  JSON 记 0；`category` 存来源分类路径（≤64 字符，超长取叶子；完整路径存明细）；
- 不做 `MaxLabelTemplateSanitizer` 清洗（Q138），不做 016 的 `.msdx` 扩展名校验；
- 仍保留**单条 16 MB 上限**（与 016 / 桌面端 `MAX_LSDX_XML_LENGTH` 一致）：超过则明细 `FAILED`
  （`超过 16 MB`），不写入；容量闸门才按 Q136 放开；
- 旧版个人云模板列表会调用 `MaxLabelTemplateSketch` 生成缩略图；`.lsdx` 解析失败会被兜底为空白轮廓，
  不会让列表 500；本期不迁移缩略图，属预期。

**Rationale**：016 的 `ShareContentServiceImpl.saveInline` 会强制 `.msdx` 并调用 `.msdx` 清洗器，
对 `.lsdx` 与旧版 JSON 不适用。新增一个内部写入方法（直接构造 DO + Mapper）比改造既有上传路径更小、
更安全；既有上传行为保持不变。

**Alternatives considered**：
- 把 `.lsdx` 伪装成 `.msdx` 名称 → 误导用户，且桌面端按扩展名可能误判；排除。
- 新增内容类型 `6=归档` → 需要改 016 的封闭集合与 C 端/桌面端类型映射；本期用 `content_type=1` +
  `editable` 明细标记 + 桌面端内容识别更小。

---

## R126 —— 幂等：导入明细表唯一键 + 内容存在性复核

**Decision**：新增 `maxlabel_labelshop_import_item`，每个任务一行来源模板：
唯一键 `(tenant_id, task_id, source_ref)`；另建
`(tenant_id, target_scope, target_owner_id, source_ref)` 普通索引用于跨任务去重。
导入前按目标预载历史成功明细；命中时检查 `content_id` 对应的内容行是否仍存在
（逻辑删除视为不存在）：

- 存在 → 当前任务明细标记 `SKIPPED`，`content_id` 指向既有内容，不创建新内容；
- 不存在 → 重新导入，当前任务插入新明细并写入新内容。

**Rationale**：016 的 `maxlabel_share_content` 没有来源标识列；加列会改动 016 表结构，且
“删除后重新导入”需要区分“从未导入”和“导入后删除”。明细表既承载幂等键，也承载失败清单与来源追溯；
按任务保留明细可以完整还原每一次导入的失败清单，重复导入不会覆盖历史任务。

**Alternatives considered**：
- 在 `maxlabel_share_content` 加 `source_ref` → 改动 016 表；且删除后重导仍需额外判断；排除。
- 只按内容哈希去重 → 同名不同内容/同内容不同来源会误判；排除。

---

## R127 —— 长任务：专用线程池 + 任务表 + 心跳 + 启动恢复

**Decision**：
- 新增 `labelshopImportExecutor`（`ThreadPoolTaskExecutor`，core=1，max=2，queue=4，
  `AbortPolicy`，线程前缀 `labelshop-import-`）；
- `start` 在事务内只创建任务（`folder_id` 暂空、`folder_name` 先算好），**事务提交后**再把
  `(taskId, tenantId)` 交给线程池（`TransactionSynchronization.afterCommit` 或非事务外层方法在
  `createTask` 返回后提交），避免异步线程读不到未提交的任务；
  异步入口显式 `TenantUtils.execute(tenantId, () -> runTask(taskId))`；
- 文件夹 + ACL 在**登录与枚举成功后、写入第一条内容前**创建（避免登录失败留下空文件夹）；
  创建后回填任务 `folder_id`；
- 持席成员快照在 `start` 时读取并随异步任务传入内存（文件夹创建时使用该快照，
  不重新查询，保证 Q140 的「导入开始时」口径）；
- 任务每批（默认 20 条）更新 `processed_count/heartbeat_time`，并刷新两个空间的 `used_bytes`；
- 启动时 `ApplicationRunner` 把残留 `PENDING/RUNNING` 任务标记为 `INTERRUPTED`，
  清空 `running_org_id`，写 `error_message=服务重启导致任务中断`；清理必须跨租户
  （`TenantUtils.executeIgnore` 或 `@TenantIgnore` Mapper），否则只会处理默认租户；
- 线程池拒绝时任务标记 `FAILED`，返回 `MEMBER_LABELSHOP_TASK_REJECTED`。

**Rationale**：245 条样例需要数百次 LabelShop 调用，同步 HTTP 会超时；`@Async` 默认线程池
无法保证租户上下文与并发上限。显式传递 `tenantId` 是必须的：异步线程没有登录租户上下文，
不设置会读错/写错租户。

**Alternatives considered**：
- 同步请求 → 超时与页面卡死；排除。
- XXL-Job 触发 → 需要调度中心与任务参数，过度设计；排除。
- 多实例心跳恢复 → 本期单实例部署；启动清理足够，`heartbeat_time` 保留给后续多实例扩展。

---

## R128 —— 并发：`running_org_id` 可空唯一键

**Decision**：任务表增加 `running_org_id`（bigint NULL），唯一键 `(tenant_id, running_org_id)`；
创建任务时写 orgId，终态（完成/失败/中断）置 NULL。MySQL 唯一索引允许多个 NULL，
因此历史终态任务不冲突。

**Rationale**：仅靠“先查再插”在并发下会双开；“同一企业只允许一个进行中任务”是 spec FR-500 的硬约束。
唯一键由数据库兜底，冲突时返回 `MEMBER_LABELSHOP_IMPORT_RUNNING`。

---

## R129 —— ~~HTTPS 强制~~（2026-11-13 所有者取消）

**Decision（现行）**：不强制 HTTPS。导入接口在 HTTP 与 HTTPS 下行为一致，Controller 不做协议校验，
管理端对话框不按 `window.location.protocol` 禁用或隐藏；nginx 无需为本功能配置 `X-Forwarded-Proto`。
原先的服务端 `isSecure()` / `X-Forwarded-Proto` 校验与 `MEMBER_LABELSHOP_HTTPS_REQUIRED` 已删除。

**历史**：原方案按 spec Q137 在服务端拒绝非加密请求、前端禁用按钮、nginx 透传原始协议；
所有者于 2026-11-13 取消该要求。密码仍按 FR-484 / FR-485 不持久化、不入日志、不回显。

---

## R130 —— LabelShop 客户端：只读协议、证书链校验、固定 mid/UA

**Decision**（详见 [contracts/labelshop-upstream.md](./contracts/labelshop-upstream.md)）：
- 使用 JDK `HttpClient`（2026-11-13 从 Hutool 切换：同一账号、同一请求体，Hutool 的 Exec
  被上游以 500 / `System.FormatException` 拒绝，JDK 客户端正常）；TLS 使用 JDK 默认证书链
  与主机名校验（实测上游证书有效，不再关闭校验）；
- `mid` 优先取配置 `yudao.maxlabel.labelshop.mid`；未配置时用
  `UUID.nameUUIDFromBytes(("labelshop:" + hostname).getBytes(UTF_8))`，同一主机重启稳定；
- UA 默认 `LabelShop/6.39 (Windows 10; x64; M/{mid}) Version/1.1.0.2511 language/zh-CN`，
  短/长版本与 OS 可配置；
- 登录 `md5Upper(password)`；解析 `datas.token`/`datas.id`，回退 `cvstoken`/`cvsuid` cookie；
- Exec 只允许 4 个只读过程，签名 `MD5(executetype=0&proc=...&procparams=...&time=... + token)`；
- STS + OSS V1 签名（HMAC-SHA1）读取 `lp/{uuid}.json`；缩略图 `.png` 不下载（016 无字段）；
- 模板正文里的外链图片（`labelshop:\\codingv\<name>`）取自公开图片 OSS
  `https://cvsimg.oss-cn-beijing.aliyuncs.com/li/<name>`（实测 200，无需 STS；基地址可配置），
  写入 016 图片内容后内嵌回模板正文（2026-11-26，Q148~Q151）；
- 单条请求超时：连接 30s / 读取 60s；不做自动重试（除会话重登一次）。

**Rationale**：Python 脚本已验证协议；服务端不能依赖 Windows WMI 与本地客户端版本。
固定 mid/UA 不改变只读语义，且让服务端重启后仍像同一台客户端。

**Alternatives considered**：
- 直接调用 Python 脚本 → 引入 Python 运行时与进程管理，违反服务端单体/Java 栈约束；排除。
- 继续使用 Hutool `HttpRequest` → 2026-11-13 实测上游对它的 Exec 请求返回 500；排除。
- 关闭证书校验以兼容旧假设 → 实测证书有效，且无必要削弱 TLS；排除。

---

## R131 —— 凭据处理：不落库、不进日志、不回显

**Decision**：
- `start` 请求字段必须叫 `password`（框架 `ApiAccessLogFilter` 的默认脱敏键包含 `password`），
  并在方法上加 `@ApiAccessLog(requestEnable = false)`，彻底不记录请求体；
- 任务表只存 `source_account_masked`（如 `mi***@126.com`）；密码不进入任务、明细、错误信息；
- `@LogRecord` 的 `bizNo` 只用 `orgId`；成功/失败文案不带账号密码；
- 登录失败返回固定文案 + 厂商 `code/message`（不含密码）；
- 前端在提交后清空密码输入，不写 localStorage/sessionStorage。

**Rationale**：spec Q137/FR-480/SC-179 明确要求；访问日志默认会把 JSON body 写入 `infra_api_access_log`，
虽然 `password` 键会被移除，但直接关闭请求体记录更稳妥。

**Alternatives considered**：
- 依赖框架默认脱敏 → 字段名一旦改成 `pwd` 就失效；排除。
- 把密码加密存库 → 无业务必要，且增加密钥管理面；排除。

---

## R132 —— 容量：不校验上限，仍维护已用字节

**Decision**：导入写入前**不**调用 `checkQuota`；每批导入后调用
`spaceService.lockSpace(scope, ownerId)` + `spaceService.refreshUsedBytes(scope, ownerId)`。
任务详情返回导入后的已用 / 上限 / 是否超限。

**Rationale**：Q136 要求导入不受容量闸门限制；016 的 `used_bytes` 只是展示缓存，读写事务内以
`SUM(size_bytes)` 为准。导入后若超限，016 的常规上传闸门（`used < quota`）自然拒绝新上传。

**Alternatives considered**：
- 导入前预检整批拒绝 → 91 MB 量级账号无法迁移；排除。
- 按剩余容量截断 → 半批数据、需要人工补齐；排除。

---

## R133 —— 企业文件夹：每次新建、ACL 取导入开始时的持席快照

**Decision**：
- 文件夹名 `yyyyMMddHHmmss导入`（如 `20261112173000导入`），长度 ≤ 64；
- `creator_member_id=OWNER`，`org_id=当前企业`；
- 可见名单 = `start` 时 `seatService.getActiveList(orgId)` 的 memberId 集合（导入开始时快照，
  随异步任务传入，不在文件夹创建时重查）；
- 不提供文件夹选择；不复用既有文件夹；不改任何既有文件夹的 ACL；
- 文件夹在登录 + 枚举成功后创建；登录/枚举失败的任务不留下空文件夹；
- 若同名（同一秒内不可能，因同企业单任务），追加 `_2` 等后缀，不覆盖。

**Rationale**：Q142 要求只新建；Q140 要求可见名单为全体持席成员。写入既有文件夹会把其原有内容
一并对全员开放，安全上不可接受。

**Alternatives considered**：
- 复用既有文件夹并补齐 ACL → 既有内容意外开放；排除。
- 空 ACL（仅管理员） → 导入后普通成员看不到，与“全公司可用”意图不符；排除。

---

## R134 —— 任务 / 明细状态机与统计

**Decision**：
- 任务：`0 待执行 → 1 进行中 → 2 已完成 / 3 已失败 / 4 已中断`；终态清空 `running_org_id`；
- 阶段：`LOGIN / LIST / IMPORT / FINISH`；
- 明细：`0 待处理 → 1 成功 / 2 跳过 / 3 失败`；
- 任务详情返回按 `source_library` 分组的 `total/success/skip/fail`；
- 失败清单分页返回 `source_name/source_category/error_message`；
- 导入结果固定带 `uncleaned=true` 与 `archiveJsonCount`。

**Rationale**：spec US2/FR-498/FR-499 要求进度、结果与失败清单可核对；状态机与 016 的
“删除/只读”语义一致，便于后续扩展“继续/取消”。

**Alternatives considered**：
- 只存任务汇总、不存明细 → 无法定位失败模板，也无法幂等；排除。
- 明细只存失败项 → 跳过项/成功项无法追溯来源 UUID；排除。

---

## R135 —— 管理端与桌面端

**Decision**：
- 管理端在 `OrgDetail.vue` 的「成员与席位」工具栏加「导入历史模板」按钮（`v-hasPermi`），
  打开 `LabelshopImportDialog.vue`：precheck → 账号/密码/授权确认 → start → 每 2–3 秒轮询
  → 汇总 + 失败分页；刷新后调用 `latest` 恢复。
- 桌面端：
  - `io/msdx.ts` 新增 `cloudContentKind(content, name)`：
    `lsdx`（`looksLikeLsdx`）/ `archive-json`（`.json` 且解析出 `docid/lfrm/pfrm`）/
    `doc-json`（其余）；
  - `App.tsx` 的 `onCloudLoad(content, name?)` 按 kind 分发：`.lsdx → importLsdx`（复用现有解析器）、
    `archive-json → 提示“旧版归档，暂不可直接编辑” + 提供另存`、其余 `fromDocJson`；
  - `CloudTemplateDialog` / `ShareSpaceDialog` 的 `onLoad` 签名加 `name?`，把条目名称传下去；
  - 新增 `saveTextFile(name, content)` IPC（沿用 `registerLogIpc.ts` 的 `dialog.showSaveDialog`
    模式），供 `.lsdx` 与归档 JSON「另存为」；`.lsdx` 打开后也可用应用既有保存路径落盘。

**Rationale**：spec Q135 要求 `.lsdx` 可直接打开、企业 JSON 归档标注；桌面端已有 `.lsdx` 解析器，
只需把云端加载从“只认 JSON”改成按内容分发，改动最小。管理端轮询与失败清单是 US2 的验收基础。

**Alternatives considered**：
- 服务端把 `.lsdx` 转成 `.msdx` → 需要 Java 版 XML→LabelDoc 转换器，工作量远大于桌面端复用；
  排除。
- 企业 JSON 在桌面端强行解析 → 无解析器，必然失败；本期只归档，排除转换。

---

## R136 —— 上线顺序与回滚

**Decision**：
1. 执行 `yudao-cloud/sql/mysql/038-labelshop-template-import.sql`（2 表 + 按钮权限）；
2. 发后端（线程池、恢复 Runner、客户端、接口）；
3. 发管理端；
4. 发桌面端（`maxlabel`）。

回滚：先把 `member:org:labelshop-import` 菜单/按钮 `status=1`（或 `deleted=1`），后端接口自然 403；
已导入内容留在 016 空间，不自动删除；2 张导入表保留供审计。桌面端回滚只需回退 `onCloudLoad` 分发。

**Rationale**：沿用 016/017 的“SQL → 后端 → 前端”顺序；导入表与 016 表解耦，回滚不丢用户内容。

---

## R137 —— 来源级失败：记任务错误，其余来源继续

**Decision**：
- STS 获取失败 → 企业/组织模板来源记失败（任务 `error_message`），个人库与组库继续；
- 某一家公司的 `GetCateList` 失败 → 该公司记失败，其余公司与其他来源继续；
- 个人库或组库的分类树失败 → 该来源记失败，另一来源继续；
- 来源级失败不创建明细行、不写残缺内容；任务结果 `libraries` 中该来源为 0，
  `errorMessage` 汇总来源级原因；
- 全部来源都失败 → 任务 `FAILED`；部分来源失败 → 任务 `2 已完成` 但 `errorMessage` 非空，
  `failCount` 只统计模板级失败。

**Rationale**：spec FR-490 要求不静默丢弃任何一家公司，FR-493 要求单条失败不中止整批；
把“来源级失败”与“模板级失败”分开，既能继续搬运可用来源，又能在结果中明确缺口。

**Alternatives considered**：
- 任一来源失败即整批失败 → 一家公司异常会让 245 条全部不可用；排除。
- 静默跳过失败来源 → 违反 FR-490；排除。

---

## R138 —— 中途停用 / 授权到期：每条写入前重查并停止新增写入

**Decision**：
- `runTask` 在**每条内容写入前**重查 `org.status == 0` 且
  `licenseService.getUnexpiredList(orgId)` 非空；
- 不满足 → 立即停止新增写入，任务置 `FAILED`，`error_message` 记录
  「企业已停用或授权已到期」，已写入内容保留；
- 已创建的文件夹与明细保留；重新发起时按来源标识幂等跳过；
- 该检查与 `start` 的前置校验使用同一套判定。

**Rationale**：spec SC-182 要求“导入过程中企业被停用 / 授权到期后，新增写入拒绝率 100%”；
导入写的是 016 内容表，绕过了 016 的上传闸门，必须在导入编排里显式补这道闸门。
每条写入前检查才能保证 100%；245 次 DB 查询相对于 245 次 LabelShop HTTP 调用可以忽略。

**Alternatives considered**：
- 只在 `start` 校验 → 长任务跨停用/到期时会继续写入，违反 SC-182；排除。
- 按批（20 条）重查 → 停用发生在批次中途时仍有最多 19 条写入，无法满足 100%；排除。
