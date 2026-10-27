# Implementation Plan: 软件与驱动分发

**Branch**: `013-software-distribution` | **Date**: 2026-10-27
**Spec**: [spec.md](./spec.md) | **Base**: [018 在线更新（桌面端）](../../maxlabel/specs/018-online-update/spec.md)

## Summary

把「软件与驱动的上架、版本管理、对外分发」收进商城后台，并让桌面端更新**只走对象存储**：

- **3 张新表**：软件分类、软件、软件版本；版本用 `current_flag` + 唯一索引保证「软件 × 平台 × 架构」至多一个当前发布；
- **复用既有对象存储**：`infra_file_config#25`（阿里云 OSS `maxlabelmall`，域名 `https://img.yuwangchenfa.com`），安装包放独立前缀，**不进应用服务器**；
- **上传走预签名直传**：管理端从后端拿一次预签名地址，浏览器直传 OSS，再回填元数据；不把 200 MB 级文件灌进 JVM；
- **更新判定在服务端**：客户端上报「当前版本 + 平台 + 架构」，后端返回「有无更新 + 目标版本 + 校验和 + 该版本的 feed 目录」；桌面端仍用 electron-updater 执行下载与安装，但不再自己比版本；
- **服务支持页**：分类列表 + 独立详情页，只出驱动与桌面端**当前发布版本**，下载直连 OSS；
- **软删除**：删除只影响可见性，文件与已发出的地址长期可用。

## Technical Context

**Language/Version**：Java 17+（yudao-cloud，构建用 JDK 25）+ Vue 3 / TypeScript（storefront、admin-vue3、maxlabel 桌面端）
**Primary Dependencies**：yudao 既有模块（infra 文件服务 / S3 客户端）、electron-updater（桌面端 018 已接入）
**Storage**：MySQL（3 张新表）；文件存阿里云 OSS（复用 `infra_file_config#25`，bucket `maxlabelmall`，域名 `img.yuwangchenfa.com`）
**Testing**：JUnit（product-server）+ Vitest（storefront / admin）+ 桌面端既有 Node 测试
**Target Platform**：服务端 Linux；管理端与门面站现代浏览器；桌面端 Windows/macOS/Linux
**Project Type**：四端单体聚合（服务端 / 门面站 / 管理端 / 桌面端）
**Performance Goals**：版本查询 p95 < 200 ms；一次更新的安装包流量 100% 走对象存储（应用服务器 0 字节）
**Constraints**：单文件默认 ≤ 2 GB；下载地址公开永久有效；不做灰度/强制更新/增量自研
**Scale/Scope**：软件数量几十款、版本数百条；并发下载由 OSS/CDN 承担

## Constitution Check

| 原则 | 结论 |
|---|---|
| **I. 测试先行** | ✅ 新 Service / Controller 先写失败测试；DDL 与 H2 同步属已声明例外 |
| **II. 先澄清** | ✅ Q83~Q90 已拍板（更新判定、回滚、软删除、直链、详情页、唯一性） |
| **III. 复用** | ✅ 复用 infra 文件服务与 S3 客户端、既有 RBAC 与菜单、既有对象存储桶；**版本比较放在服务端**，客户端不自行实现业务规则 |
| **IV. 最小改动** | ✅ 不新建 maven 模块，新增内容落在 product-server；复用既有上传与鉴权设施 |
| **V. 可验证** | ✅ SC-126~SC-139 在 quickstart 有逐条落点；并发唯一性有负向测试 |

## 关键设计决策

完整论证见 [research.md](./research.md)（R76~R86）：

| # | 决策 |
|---|---|
| R76 | 存储复用 `infra_file_config#25`（OSS `maxlabelmall` / `img.yuwangchenfa.com`），安装包与驱动放 `software/` 前缀，与图片分离 |
| R77 | 上传走**预签名 PUT 直传**（`S3FileClient#presignPutUrl`），浏览器 → OSS 直连，服务端只登记元数据 |
| R78 | 域模型落在 **product-server**（`…product.software` 包），不新建 maven 模块（宪法 IV）；表用 `maxlabel_software_*` 前缀 |
| R79 | 「当前发布」唯一性用 `current_flag tinyint NULL` + 唯一索引 `(tenant_id, software_id, platform, arch, current_flag)`；切换在事务内「先清后置」，并发由唯一索引兜底 |
| R80 | 更新判定在服务端：`GET /app-api/software/update/check` 返回 `hasUpdate / latestVersion / sha512 / size / feedUrl` |
| R81 | 桌面端仍用 electron-updater 执行下载与安装，但**不再自己比较版本**：先调 check，有更新时把 `feedUrl` 交给 `setFeedURL` |
| R82 | 后端动态生成 `latest.yml`（`GET /app-api/software/feed/{platform}/{arch}/latest.yml`），`path` 用 OSS 绝对直链 |
| R83 | 校验和统一 **sha512**（与 `latest.yml` 字段同名同算法），由管理端在上传后计算并回填 |
| R84 | 软删除：`deleted` 逻辑删除，所有对外查询过滤；OSS 文件不删，地址永久有效 |
| R85 | 权限点 `software:category:*` / `software:info:*` / `software:version:*` 幂等写入 `system_menu` |
| R86 | 服务支持页只暴露「当前发布」；历史版本仅管理端可见（Q84） |

## 代码落点

```text
yudao-cloud/
├── sql/mysql/025-software-distribution.sql            # 3 表 + 菜单权限（幂等）
├── yudao-module-mall/yudao-module-product-server/src/main/java/…/product/
│   ├── dal/dataobject/software/{SoftwareCategoryDO,SoftwareDO,SoftwareVersionDO}.java
│   ├── dal/mysql/software/{SoftwareCategoryMapper,SoftwareMapper,SoftwareVersionMapper}.java
│   ├── service/software/{SoftwareCategoryService,SoftwareService,SoftwareVersionService}.java
│   ├── controller/app/software/{AppSoftwareController,AppSoftwareUpdateController}.java
│   ├── controller/admin/software/{SoftwareCategoryController,SoftwareController,SoftwareVersionController}.java
│   ├── controller/app/software/vo/*.java              # 含 latest.yml 生成
│   └── enums/software/{SoftwareTypeEnum,SoftwarePlatformEnum,SoftwareArchEnum,
│                       SoftwareVersionStatusEnum,SoftwareLogRecordConstants}.java
└── yudao-ui/yudao-ui-admin-vue3/src/
    ├── api/mall/software/index.ts
    └── views/mall/software/{category,info,version}/*.vue

storefront/src/
├── api/software.ts
├── views/SupportSoftwareView.vue                       # 分类列表 → /support/software
├── views/SoftwareDetailView.vue                        # 独立可分享详情页 → /support/software/:id
└── router/index.ts                                     # 两条路由均不需登录（FR-342/FR-354）

../maxlabel/app/src/main/update/
└── updateService.ts                                    # 先调 check，再 setFeedURL(feedUrl)
```

**测试落点**

| 模块 | 测试 |
|---|---|
| product-server | 分类/软件/版本的 CRUD 与校验、当前发布唯一性（含并发负向）、更新判定（含回滚与平台不匹配）、latest.yml 生成、软删除后链接仍可用 |
| storefront | `api/software.spec.ts`、`SupportSoftwareView.spec.ts`、`SoftwareDetailView.spec.ts`、路由 |
| admin | 分类/软件/版本页面与请求体断言 |
| 桌面端 | `mall` 无关；`updateService` 改为「先调 check」后补单测 |

## 实施分期

| 阶段 | 内容 | 交付后 |
|---|---|---|
| ① 数据层 | `025` SQL、3 个 DO/Mapper、枚举、H2 DDL、权限点 | 表与权限可用 |
| ② 存入与发布 | 预签名、登记、发布/下架/软删除、唯一性切换、**关键操作留痕（FR-346）** | 运营能把包传上去并发布，且每次操作可追溯 |
| ③ 对外接口 | app-api 分类/列表/详情/check/latest.yml | 客户端与门面站可消费 |
| ④ 管理端 | 分类、软件、版本三页 | 运营自助 |
| ⑤ 门面站 | 服务支持分类列表 + 详情页 + 路由 | 用户可下载 |
| ⑥ 桌面端 | `updateService` 改为先调 check | 自动更新走 OSS |
| ⑦ 上线 | SQL → 后端 → 管理端/门面站 → 桌面端 | 按 quickstart 验收 |

## 主要风险

| # | 风险 | 对策 |
|---|---|---|
| R77 | 阿里云 OSS 对 S3 预签名 PUT 的兼容性未经实测 | 先做一次「预签名上传→可下载」的技术验证；不成立则降级为「服务端流式中转」（仍不落盘长存） |
| R81 | 桌面端改更新链路，跨仓库 | 与 009 同法：先发布后端接口，再发桌面端；旧客户端不受影响（018 前无自动更新） |
| R82 | `latest.yml` 与文件校验不一致会导致安装失败 | 生成时以数据库登记值渲染；发布前用同一份记录核对 |
| R79 | 并发切换当前发布出现双当前 | 唯一索引兜底 + 事务内先清后置；并发负向测试 |
| R76 | 与图片共用桶，误删/误覆盖 | 独立前缀 `software/`，文件名含版本与平台，不做覆盖写 |
| — | 大文件上传中断 | 预签名直传可重试；未确认的版本不进入可发布状态 |

## Project Structure

```text
specs/013-software-distribution/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── app-api.md
│   └── admin-api.md
├── checklists/requirements.md
└── tasks.md            # 由 /speckit-tasks 生成
```

**Structure Decision**：沿用现有「单体聚合 + 模块化」结构。软件分发属于目录型业务，落在 `product-server`，
复用其分页、校验、文件与后台页面的既有骨架；跨端只通过 `/app-api` 与 `/admin-api`，不新增模块间 RPC。

## Complexity Tracking

无宪法违规。以下为实现所需的最小扩展：

| 扩展 | 为什么必须 | 更简单的替代为何不可行 |
|---|---|---|
| `current_flag` + 唯一索引 | 并发下保证「软件 × 平台 × 架构」只有一个当前发布 | 只靠应用层「先查后改」在并发下会双当前发布 |
| 后端生成 `latest.yml` | 桌面端 018 用 electron-updater，需要该文件 | 让运营手工维护 yml 易与登记数据不一致，校验和出错会导致安装失败 |
| 预签名直传 | 200 MB 级文件经服务端会占满内存与上行带宽 | 直接复用 `FileApi.createFile(byte[])` 会把整包读进堆内存 |
