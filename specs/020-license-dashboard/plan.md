# Implementation Plan: 授权看板（软件管理）

**Branch**: `020-license-dashboard` | **Date**: 2026-11-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/020-license-dashboard/spec.md`

## Summary

在管理端「软件管理」下新增一个**授权看板**：顶部给三类数字（个人有效授权、企业有效授权 + 席位占用、
当年的新获得 / 延续 / 合计客户数），下部两个页签（**客户授权明细**、**赠送记录**），
并在明细里提供**授权赠送**（个人送时长、企业送席位 / 送时长），必填理由、立即生效、全程留痕。

技术路径：在 **member 模块**内新增一个只读查询 Service + 一个写入 Service，
复用既有 `member_subscription`（个人授权流水）与 `maxlabel_org_license` / `maxlabel_org_seat`
（企业批次与席位）；新增一张 `member_license_grant` 记录每次赠送；给两张既有表各加一列
`license_grant_id` 并把来源订单列放宽为可空 —— 因为**今天这两张表的来源订单列非空且唯一**，
手工赠送在结构上就塞不进去（见 research R2）。

## Technical Context

**Language/Version**: Java 17（`yudao-cloud`，Maven 多模块）+ Vue 3 + TypeScript（管理端）

**Primary Dependencies**: Spring Boot / MyBatis-Plus（yudao 既有）、Element Plus（管理端既有）

**Storage**: MySQL（既有库，新增 1 表 + 2 表各加 1 列 + 放宽若干列为可空）

**Testing**: 后端 JUnit + H2（`BaseDbUnitTest`）；管理端 Vitest + Vue Test Utils

**Target Platform**: 管理端 Web（`/admin-api`），仅桌面浏览器

**Project Type**: 单体 Web 服务 + 后台管理端（本特性**不涉及** C 端与桌面端）

**Performance Goals**: 看板首屏 ≤ 2 秒；明细翻页 ≤ 1 秒（SC-207）

**Constraints**:
- 统计必须**一次查询出数**，禁止"先全量取回再在内存里过滤"
- 赠送必须**原子**：授权变更与赠送记录同一事务
- 赠送时长上限：单次 ≤ 12 个月，且赠送后剩余 ≤ 12 个月（Q154）

**Scale/Scope**: 现有 295 个商品、数百会员量级；明细页分页 20 条；新增 1 个页面、4 个接口、1 张表

## Constitution Check

*GATE: 必须在 Phase 0 前通过；Phase 1 设计后复查。*

| 原则 | 本特性的落实 | 结论 |
|---|---|---|
| I. 测试先行 | 每个用户故事先写失败测试：额度/顺延/无批次/幂等/统计口径 | ✅ 任务里排在最前 |
| II. 先澄清 | Q152~Q160 已在 spec 的 Clarifications 拍板，无遗留 NEEDS CLARIFICATION | ✅ |
| III. 复用既有能力 | 授权仍是 `member_subscription` 与 `maxlabel_org_license`/`maxlabel_org_seat`，**不另建授权模型**；留痕复用 `@LogRecord` → 操作日志 | ✅ |
| IV. 最小改动 | 新增 1 表；两张表各加 1 列、放宽来源列；不改既有发放与权益判定逻辑 | ✅ |
| V. 增量可验证 | 三个用户故事各自可独立验收；SC-203~209 均可度量 | ✅ |
| VI. 发版须所有者确认 | 本特性的上线顺序写在 quickstart §上线顺序；**执行前必须得到所有者当次确认** | ✅ |

**门禁结论**：通过，无违规需要记录到 Complexity Tracking。

## Project Structure

### Documentation (this feature)

```text
specs/020-license-dashboard/
├── plan.md              # 本文件
├── spec.md              # 功能规格
├── research.md          # Phase 0：关键裁决与证据
├── data-model.md        # Phase 1：表结构与派生口径
├── contracts/
│   └── admin-api.md     # Phase 1：管理端接口契约
├── quickstart.md        # Phase 1：手工验证清单与上线顺序
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks 产出
```

### Source Code (repository root)

```text
yudao-cloud/
├── sql/mysql/
│   └── 042-license-dashboard.sql          # 新表 + 加列 + 放宽可空 + 菜单/权限点
├── yudao-module-member/
│   ├── yudao-module-member-api/           # （本次预计不改）
│   └── yudao-module-member-server/
│       ├── controller/admin/license/      # 新增：LicenseDashboardController + VO
│       ├── service/license/               # 新增：查询与赠送两个 Service
│       ├── dal/dataobject/license/        # 新增：MemberLicenseGrantDO
│       ├── dal/mysql/license/             # 新增：MemberLicenseGrantMapper
│       └── dal/mysql/{subscription,org}/  # 改：查询与加列映射
└── yudao-ui/yudao-ui-admin-vue3/src/
    ├── api/member/license/                # 新增：接口封装 + 类型
    └── views/member/license/              # 新增：看板页 + 赠送弹窗 + 明细/记录组件
```

**Structure Decision**: 光看板上"个人"与"企业"两类数据都落在 **member 模块**（`member_subscription`
与 `maxlabel_org_*` 都在 `yudao-module-member-server`），所以整个特性收在 member 模块，
**不新建模块、不跨模块 RPC**。

## Phase 0：关键裁决（详见 research.md）

1. **赠送为什么不能复用 `grant()`**：`member_subscription.source_order_item_id` NOT NULL + 唯一，
   手工赠送没有订单 → 必须放宽来源列并加 `license_grant_id`（R2）。
2. **赠送记录落在哪**：新增 `member_license_grant`，而不是往订阅/批次上堆备注列（R3）。
3. **企业送席位/送时长的落点**：加到**当前唯一的有效批次**（折叠语义保证至多一条）；
   无有效批次时新建一条赠送批次（R4）。
4. **「当年新获得 / 延续」怎么算**：用行/批次的 **create_time**（= 发放发生时间）而不是 `start_time`
   —— 续费行的 `start_time` 是原到期日（可能落在明年），按它统计会把续费算错年（R5）。

## Phase 1：设计与契约

- **data-model.md**：`member_license_grant` 字段与约束；两张既有表的列变更；三个统计口径的判定式。
- **contracts/admin-api.md**：`summary` / `customer/page` / `grant` / `grant/page` 四个接口。
- **quickstart.md**：手工验证清单（含上限、无批次、双来源会员）与上线顺序。

## Complexity Tracking

> 本特性无 Constitution 违规，此表留空。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| — | — | — |
