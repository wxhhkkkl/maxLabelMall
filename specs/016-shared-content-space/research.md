# Research（016 共享内容空间）

> 编号续 015 的 R98：本文件从 **R99** 开始。
> 结论全部基于当前仓库代码与 2026-11-05 的所有者拍板（Q115~Q122）。

## R99 —— 个人与企业内容统一进一张表：`maxlabel_share_content`

**Decision**：新建 `maxlabel_share_content`，用 `scope`（1 个人 / 2 企业）区分归属；
把 `maxlabel_label_template` 的存量数据 **按原 id 迁移**进来（scope=1、content_type=1、storage_type=1）。
旧表保留、不再写入。

**Rationale**：
- 个人空间已有云端模板（`maxlabel_label_template`），企业空间又需要同样的模板能力；
  如果只给企业另建一套，模板会出现两张表、两套分页与两套清洗逻辑。
- 保留原 id 可让桌面端已缓存的模板 id 继续有效，旧 `/member/cloud-template/*` 端点无需改返回结构。
- 旧表保留使回滚只需回退代码，不必回滚数据。

**Alternatives considered**：
- *只扩 `maxlabel_label_template` 加 org_id/folder_id*：非模板文件（图片/Excel）无处安放，
  仍要第二张表，且列表要 UNION，复杂度更高。
- *不迁移、新老并存*：个人空间出现两个真相，容量统计要跨表合并，且用户会看到两份模板。

## R100 —— 平台内容独立表：`maxlabel_platform_content`

**Decision**：新建 `maxlabel_platform_content`；把 `maxlabel_shared_template` 中
`audit_status=1` 的条目按原 id 迁移为 `status=1` 的平台内容。旧表与旧审核字段只读保留。

**Rationale**：
- 旧表 `source_template_id` 是 `NOT NULL + UNIQUE`，管理端直传没有来源模板，无法复用；
  改写唯一键的风险高于建新表。
- 平台内容没有容量、没有上传者会员、没有文件夹，生命周期（上架/下架）与用户内容不同；
  独立表能让查询与权限更简单。
- 保留原 id，桌面端已下载记录/旧链接仍可解析。

**Alternatives considered**：
- *复用 `maxlabel_shared_template` 并加列*：需要放宽 `source_template_id` 约束与唯一键，
  还要兼容 `audit_status` 语义，迁移风险大。
- *新建表但不迁移历史*：Q115 要求历史已公开条目保留，不可行。

## R101 —— 容量闸门用「空间行锁 + SUM」：`maxlabel_share_space`

**Decision**：新增 `maxlabel_share_space`（`scope` + `owner_id`，个人 owner=member_id，
企业 owner=org_id），上传/删除时 `SELECT ... FOR UPDATE` 锁住对应行，锁内
`SUM(size_bytes)` 重新计算用量，校验后写入并回写 `used_bytes`。

**Rationale**：
- FR-423 要求并发上传不得超限；只靠 `SUM` 再插入会出现「两个请求同时通过」。
- 个人空间没有天然可锁的业务行；用独立空间行比锁 `member_user` 更窄，不会阻塞登录/资料更新。
- 锁内重算 SUM 可以自愈 `used_bytes` 的漂移；删除在同事务内回写，容量立即释放。

**Alternatives considered**：
- *只做 SUM 不做锁*：并发下必然超限，且无法满足 SC-150。
- *锁 `member_user` / `maxlabel_org`*：会把文件上传与账号/企业其他写操作串行化，影响面更大。
- *单独 Redis 计数*：引入第二真相与丢失风险，宪法要求 MySQL 单一存储。

## R102 —— 版本容量与个人空间

**Decision**：个人配额 = `edition` 为 `professional` 或 `enterprise` 时 100 MB，否则 10 MB。
免费（标准版）用户**允许写入**，不再走 009 FR-219 的订阅闸门；删除仍不受限。

**Rationale**：
- 012 Q80 已把 `edition` 下发到 `GET /member/subscription/get`，直接复用，不新增判定。
- 企业版用户的个人空间按 100 MB（spec Assumptions），企业池另计，避免同一用户两套版本语义。

**Alternatives considered**：
- *按 `member_subscription` 是否有有效行判定*：会漏掉企业席位用户，且与 edition 口径重复。
- *免费用户只读*：与本次「免费 10 MB」的明确描述冲突。

## R103 —— 企业空间访问矩阵

**Decision**：

| 操作 | 要求 |
|---|---|
| 浏览 / 下载 / 删除内容 | 席位 `status=0`（未撤销）；不要求授权未过期 |
| 上传 / 新建/改名/删除文件夹 / 改可见名单 | 上述 + 企业 `status=0` + 存在未过期未退款批次 |
| OWNER / ADMIN 管理内容 | 同样要求席位，不豁免 |
| 企业停用 / 授权过期 | 转只读：可浏览/下载/删除，不可上传或改文件夹 |

**Rationale**：Q116 要求到期后只读，Q118 要求仅席位成员可用。把「席位记录」与
「授权有效期」拆成两个判据，两者可以同时成立：到期后席位记录仍在 → 可只读；
撤销席位 → 连读取也没有。文件夹变更属于写入，随授权到期一起关闭。

**Alternatives considered**：
- *只按席位判定*：授权到期后仍可上传，违反 Q116。
- *只按授权判定*：未分配席位的人也能用企业池，违反 Q118。

## R104 —— 文件夹模型：单层 + 成员名单

**Decision**：`maxlabel_share_folder`（org_id、name）+ `maxlabel_share_folder_member`
（folder_id、member_id）。文件夹单层、无根目录内容；非空文件夹不可删除；
管理员（OWNER/ADMIN 且持席）可建/改/删夹与改名单，并绕过名单查看全部。

**Rationale**：本次描述只要求「管理员设定文件夹，每个文件夹管理哪些企业内用户可以查看」，
没有多级、移动、根目录的语义。单层 + 显式名单是最小可用模型，权限判定只有一条路径。

**Alternatives considered**：
- *多级树*：需要 `parent_id`、递归可见性、移动与循环校验，超出本期。
- *按角色/部门授权*：012 没有部门概念，无法落地。
- *根目录公共区*：Q120 已否决。

## R105 —— 文件类型与存储方式

**Decision**：
- 模板（`.msdx`）、txt、csv：读取为 UTF-8 文本存 `content longtext`；
- excel（`.xls/.xlsx`）、图片（`.png/.jpg/.jpeg/.gif/.bmp/.webp`）：经 `FileApi.createFile`
  存对象存储，表里存 `file_url` + `mime_type`；
- 单文件 ≤ 16 MB（配置项 `yudao.maxlabel.share.content-max-bytes`，默认 16 MB，与
  `spring.servlet.multipart.max-file-size` 对齐）；扩展名白名单；拒绝 `.exe/.bat/.cmd/.sh/.js` 等可执行/脚本；
- 模板内容沿用 `MaxLabelTemplateSanitizer`：个人/企业保留数据集行，平台副本剥离行与口令；
- 文件类内容的读取接口**不直接返回长期 URL**：权限校验通过后用 `FileApi.presignGetUrl`
  返回 5 分钟预签名地址；本地存储不支持签名时退回原 URL（在 quickstart 显式注明）。

**Rationale**：文本进库便于沿用现有模板 API 与清洗；二进制进对象存储避免 `longtext` 膨胀。
16 MB 与现有 `MEMBER_TEMPLATE_TOO_LARGE` 一致，不新增运营参数。

**Alternatives considered**：
- *全部进 DB*：图片/Excel 会撑大行与备份，且与 `infra` 文件能力重复。
- *全部进对象存储*：模板正文要每次远程读取，旧 API 兼容成本高。

## R106 —— 平台内容：管理端直传，会员投稿关闭

**Decision**：管理端上传即公开（`status=1`），可编辑、下架、删除；会员
`submit-share` / `cancel-share` 返回 `MEMBER_SHARED_TEMPLATE_SUBMIT_CLOSED`。
历史已通过条目迁移后由新页面管理。

**Rationale**：Q115 明确取消会员投稿。保留端点并返回明确错误，可让未升级的桌面端
看到「该功能已关闭」而不是 404/未知错误。009 的 FR-219（云端写入需订阅）、
FR-233/238（会员投稿 + 审核）在 016 中失效，需在 009 规格加「已被 016 取代」注记。

**Alternatives considered**：
- *直接删端点*：老客户端得到 404，排障困难。
- *保留审核流程*：与 Q115 冲突。

## R107 —— 旧接口兼容与发布顺序

**Decision**：
- `/member/cloud-template/*`：保留路径与返回结构，内部代理到 `ShareContentService`
  （个人 + 模板）；`submit-share/cancel-share` 返回关闭错误。
- `/member/shared-template/*`：保留路径，代理到 `PlatformContentService`；
  新增 `contentType` 字段，模板字段保持原样。
- 管理端旧 `/admin/member/shared-template/*`：**保持原样**（只操作旧表），但删除前端页面与菜单；
  新平台内容页只操作新表。这样回滚时不需要恢复被删掉的接口。
- 管理端会员云端模板预览（`AdminLabelTemplateController`）必须改读新表，否则迁移后
  后台看到的是不再更新的旧数据；权限点 `member:cloud-template:query` 不变。

**Rationale**：部署顺序是先服务端、后桌面端/管理端；旧客户端在窗口期内仍会调用旧端点。
兼容层只在 016 这一个发布周期保留，桌面端与管理端升级完成后由后续特性清理。

**Alternatives considered**：
- *一次性切换新端点*：窗口期内旧桌面端模板列表直接失败。

## R108 —— 桌面端交互

**Decision**：把 `TemplateLibDialog` 的「共享模板库」升级为 `ShareSpaceDialog` 三页签：
- **个人**：上传模板/文件、下载/打开、删除、容量条；
- **企业**：文件夹列表、按权限上传、删除自己上传的内容、管理员建夹/改名单/删空夹、容量条；
- **平台**：检索/筛选/下载，无上传入口。

移除 `CloudDialog` 的「发布到共享库」与本地「组模板库」选项；`sharedLibrary.ts`
（本机 JSON 共享库）保留为离线能力，但界面改名为「**本机模板库**」，
避免与平台共享/企业共享混淆；它不参与云端三态，也不计入任何配额。

**Rationale**：桌面端是共享模板的实际使用入口；三页签与 spec 的三个用户故事一一对应。
不新建独立窗口，减少导航与状态同步成本。

**Alternatives considered**：
- *门面站新增共享空间页*：spec Assumptions 明确本期不做；且下载/打开仍要回到桌面端。
- *复用本机共享库当个人空间*：换机不同步，与 009 已建立的云端模板冲突。

## R109 —— 管理端页面

**Decision**：新增 `views/member/platformContent/index.vue` + `PlatformContentForm.vue`，
权限点 `member:platform-content:query/create/update/delete`；旧 `views/member/template/review`
页面与菜单删除（历史数据已迁移）。`033` 脚本同时软删旧菜单
`member:shared-template:query/audit/offline`，避免两个入口并存。

**Rationale**：平台内容需要「类型 / 分类 / 关键词 / 上下架」四个维度，旧审核页只面向模板与审核状态，
改造成本高于新建；旧页保留会造成两个入口维护同一批数据。

**Alternatives considered**：
- *改造旧审核页*：字段与状态机都要换，且会保留「审核」误导。

## R110 —— 登录与可见性

**Decision**：个人空间、企业空间、平台内容都要求登录；个人空间仅本人可见、不可分享；
平台内容登录即可浏览；企业空间额外要求席位。未登录不展示任何共享空间入口。

**Rationale**：沿用 009 Q54 与本次 Q118/Q122；避免匿名下载平台内容带来的带宽与合规问题。

**Alternatives considered**：平台内容允许匿名下载 —— 与既有共享模板库口径不一致，且无法统计使用。

## R111 —— 删除与对象存储

**Decision**：内容删除只软删数据库行并释放配额（对用户不可恢复，FR-396）；
对象存储中的文件不主动删除，由运维按存储策略清理。文件夹删除物理删除 ACL 行（文件夹本身软删）。

**Rationale**：`FileApi` 没有稳定的删除契约；为避免误删共享文件，先保留孤立对象。
配额以数据库 `size_bytes` 为准，孤立对象不会影响用户容量。

**Alternatives considered**：同步删除 OSS 对象 —— 失败会导致事务回滚或文件丢失，
且历史版本/其他引用可能仍在用同一 URL。

## R112 —— 上线顺序与回滚

**Decision**：
1. 执行 `sql/mysql/033-shared-content-space.sql`（5 表 + 迁移 + 菜单）；
2. 发布后端；
3. 发布管理端（新平台内容页、删旧审核菜单）；
4. 发布桌面端（三页签、移除投稿）。
回滚：先回退桌面端/管理端，再回退后端；旧表数据未被修改，可直接回退代码。
新表可保留，不影响旧逻辑。

**Rationale**：后端先上线可让旧桌面端继续用旧端点；旧表只读保证回滚不丢数据。
迁移脚本幂等（`INSERT ... SELECT ... WHERE NOT EXISTS`），可重复执行。

**Alternatives considered**：先发桌面端 —— 新界面调用的端点尚不存在，必然 404。
