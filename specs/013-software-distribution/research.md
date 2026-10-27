# Research: 软件与驱动分发

**Feature**: `013-software-distribution` | **Date**: 2026-10-27

本文件记录计划阶段的技术决策与取舍。规格层面的口径已在 spec 的 Q83~Q90 拍板，这里只解决「怎么落地」。

---

## R76 —— 存储复用既有 OSS

**Decision**：复用 `infra_file_config#25`——阿里云 OSS，endpoint `oss-cn-wulanchabu.aliyuncs.com`，bucket `maxlabelmall`，对外域名 `https://img.yuwangchenfa.com`。安装包与驱动放在独立前缀 `software/` 下，与商品图片分离。

**Rationale**：

- 线上已有 1789 个文件走这套存储（`infra_file.url` 均为 `img.yuwangchenfa.com`），域名与凭据都已就绪；
- 宪法原则 III 要求复用既有能力，不新造存储体系；
- 独立前缀可以按目录单独配置 CDN 缓存策略与生命周期，避免与图片相互影响。

**Alternatives considered**：

- 新开一个 bucket：需要新增凭据、域名与备案绑定，收益仅是隔离，成本明显更高；
- 继续用服务器本地磁盘：正是本功能要解决的问题（占用服务器带宽）。

**前置校验（上线前必须做一次）**：在管理端上传一个小文件到 `software/` 前缀，确认返回的 `https://img.yuwangchenfa.com/software/...` 可匿名 GET。

---

## R77 —— 上传走预签名直传

**Decision**：管理端先调 `POST /admin-api/software/version/presign` 拿到预签名 PUT 地址与对象键，浏览器**直传 OSS**，成功后把 `objectKey / url / size / sha512 / 文件名` 回填给后端登记版本。

**Rationale**：

- 安装包 200 MB 级；现有 `FileApi.createFile(byte[] content, …)` 会把整包读进 JVM 堆，一条 250 MB 的上传就可能触发 OOM；
- 走应用服务器会让每次发布占用服务器**上行**带宽与连接时长，与「不占服务器带宽」的初衷相悖（虽然只有一次）；
- `S3FileClient` 已具备 `presignPutUrl(path)` 能力，无需新写客户端。

**Alternatives considered**：

- 复用 `/admin-api/infra/file/upload`（服务端中转）：实现最快，但内存与超时风险高，且 250 MB 走两遍网络；
- 运营用 ossutil 手工上传、后台只登记 URL：零风险但违背「后台自助维护」的诉求，作为预签名不可用时的兜底。

**复核结论（2026-10-27）**：infra **早已具备**该能力 —— `S3FileClient#presignPutUrl` 已实现，
并在 `FileController#getFilePresignedUrl`（`GET /admin-api/infra/file/presigned-url`）对外暴露。
因此**不需要跨模块新增 RPC**，管理端直接复用该端点即可。

**仍需验证**：阿里云 OSS 通过 S3 SDK 的预签名 PUT 兼容性**未经本项目实测**。
上线前必须先做一次技术验证（上传 1 MB 文件 → 用返回的 URL PUT → 再 GET 校验）。
若签名不兼容，降级为「服务端流式中转」（`MultipartFile` 直接经 `FileClient.upload(InputStream)` 转发，不落盘、不整包进内存）。

---

## R78 —— 域模型落在 product-server

**Decision**：新建 `cn.iocoder.yudao.module.product.software` 包，表名 `maxlabel_software_category` / `maxlabel_software` / `maxlabel_software_version`。

**Rationale**：

- 软件与驱动是「可浏览的目录型内容」，与 product 模块已有的分类/商品同属一类职责；
- 不新建 maven 模块可以省掉 pom、ApiConstants、依赖与部署改动（宪法原则 IV）；
- 与 012 把企业域放进 member-server 的做法一致：挂在语义最近的既有模块，而不是为每个域新建模块。

**Alternatives considered**：

- 新建 `yudao-module-software`：边界更干净，但对一个 3 张表的域来说样板成本过高；
- 放进 infra：infra 是基础设施域，无 `app-api` 业务先例，不合适。

---

## R79 —— 「当前发布」的唯一性

**Decision**：版本表增加 `current_flag tinyint(1) NULL`，并建唯一索引
`uk_current (tenant_id, software_id, platform, arch, current_flag)`。
切换当前发布时在**同一事务**内：`UPDATE … SET current_flag = NULL WHERE software_id=? AND platform=? AND arch=? AND current_flag=1` → `UPDATE … SET current_flag = 1 WHERE id=?`。

**Rationale**：

- MySQL 的唯一索引对 `NULL` 不生效，因此「同一组合只能有一行 `current_flag=1`」由数据库直接保证，应用层无需额外锁；
- 并发两次「设为当前发布」时，后提交的事务会撞唯一键，可转成可读的业务错误；
- 比「锁软件行后二次校验」少一次锁竞争，规则也更不容易被后续代码绕过。

**Alternatives considered**：

- 在软件表上放 `current_version_id`：只能表达「每软件一个当前」，无法满足 Q86 的「按平台 × 架构各自独立」；
- 只用应用层事务校验：并发下会双当前（SC-136 会红）。

**测试要点**：并发 10 次设为当前发布 → 最终 `current_flag=1` 的行数为 1。

---

## R80 / R81 —— 更新判定在服务端，桌面端只执行

**Decision**：新增 `GET /app-api/software/update/check?currentVersion=&platform=&arch=`，服务端返回：

```jsonc
{ "hasUpdate": true, "latestVersion": "1.0.23",
  "sha512": "…", "size": 264241152,
  "feedUrl": "https://img.yuwangchenfa.com/software/win-x64/1.0.23/",
  "releaseNotes": "…" }
```

桌面端（`maxlabel` 仓库 018 的 `updateService`）改为：先调该接口；`hasUpdate=false` 时结束；为 `true` 时把 `feedUrl` 交给 `autoUpdater.setFeedURL({ provider: 'generic', url: feedUrl })` 再 `checkForUpdates()`。

**Rationale**：

- Q89 要求服务端判定；`1.0.9` vs `1.0.10` 这类比较不该由每个平台的客户端各写一遍（宪法原则 III）；
- 桌面端不必重写「下载 + 安装 + 静默重启」——那正是 electron-updater 已经做好的部分；
- 指向**具体版本目录**后，electron-updater 内部的比较必然与我们的结论一致；万一不一致（例如回滚），它的「不降级」行为恰好符合 Q90。

**Alternatives considered**：

- 完全自研下载与安装：要重写差量、签名校验、各平台安装器调用，风险与工作量都远超收益；
- 让 electron-updater 直接指向一个总 feed、由它比较版本：等于把判定放回客户端，违背 Q89。

**跨仓库依赖**：`maxlabel` 仓库需要同步改动；与 009 一样，先发后端接口，再发桌面端。018 之前的客户端本身没有自动更新，不受影响。

---

## R82 —— 后端生成 `latest.yml`

**Decision**：`GET /app-api/software/feed/{platform}/{arch}/latest.yml` 按数据库登记值动态生成 electron-updater 的清单：

```yaml
version: 1.0.23
files:
  - url: https://img.yuwangchenfa.com/software/win-x64/1.0.23/MaxLabel-Setup-1.0.23.exe
    sha512: <base64>
    size: 264241152
path: https://img.yuwangchenfa.com/software/win-x64/1.0.23/MaxLabel-Setup-1.0.23.exe
sha512: <base64>
releaseDate: '2026-10-27T10:00:00.000Z'
releaseNotes: …
```

**Rationale**：

- 运营只登记元数据，清单由系统渲染，避免手写 yml 与登记值不一致；
- `path` 用绝对直链，electron-updater 的 `newUrlFromBase` 对绝对 URL 原样返回，因此**文件仍然直连 OSS**，不经过应用服务器；
- 后端只输出几 KB 文本，满足 SC-126 的「查询请求 < 10 KB」。

**Alternatives considered**：

- 发布时把 `latest.yml` 也上传到 OSS：少一次后端请求，但多一份需要同步的产物，且运营改元数据后要重新上传。

---

## R83 —— 校验和统一 sha512

**Decision**：所有版本记录存 `sha512`（Base64，与 electron-updater 的 `sha512` 字段同算法同编码）。

**Rationale**：桌面端最终由 electron-updater 校验，算法必须一致；用同一份值同时喂给 `latest.yml` 与 check 接口，避免两套校验口径。

**Alternatives considered**：sha256 更短，但与 electron-updater 契约不一致，需要客户端二次转换。

---

## R84 —— 软删除

**Decision**：三张表都继承 `BaseDO`（含 `deleted`），删除即逻辑删除；所有对外与后台列表查询自动过滤；OSS 对象不删除。

**Rationale**：Q87 要求「已发出的链接不失效」；`BaseDO` 的逻辑删除是框架既有能力（宪法原则 III）。

**Alternatives considered**：物理删除 + 墓碑表：复杂且仍要保住文件，与目标不符。

---

## R85 —— 权限点与菜单

**Decision**：`025` 迁移幂等写入权限点：
`software:category:query|create|update|delete`、`software:info:query|create|update|delete`、
`software:version:query|create|update|delete|publish`，并挂到「商城管理 → 软件分发」菜单下。

**Rationale**：管理端的 `v-hasPermi` 依赖 `system_menu.permission` 行；012 已经踩过「权限串没登记 → 按钮不显示」的坑。

**Alternatives considered**：复用现有商品权限：会让「能改商品」隐含「能发安装包」，权限粒度过粗。

---

## R86 —— 服务支持页只暴露当前发布

**Decision**：app-api 的分类/列表/详情接口只返回 `status=启用` 且 `current_flag=1` 的版本；历史版本仅在 admin-api 提供。

**Rationale**：Q84 拍板；同时减少对外暴露面与被刷流量的范围。

**Alternatives considered**：用参数控制是否返回历史：多一个容易被误用的开关，收益不明确。
