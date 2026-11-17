---
description: "Task list for 020-license-dashboard"
---

# Tasks: 授权看板（软件管理）

**Input**: 设计文档在 `specs/020-license-dashboard/`
**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 宪法原则 I（测试先行，不可协商）→ 测试任务必须包含、必须先于同故事的实现任务，并先确认失败。

**Organization**: 按用户故事分组，每个故事可独立实现、独立验证。

**Path Conventions**: 仓库根为 `d:\workspace\maxLabelMall`。后端在 `yudao-cloud/yudao-module-member/yudao-module-member-server/`；
管理端在 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/`；SQL 在 `yudao-cloud/sql/mysql/`。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属用户故事（US67 / US68 / US69）
- 每个任务都带精确文件路径

---

## Phase 1: Setup（共享基础设施）

- [x] T053 创建 `yudao-cloud/sql/mysql/042-license-dashboard.sql`：① 新建 `member_license_grant`
      （字段与索引**逐字照 data-model §1**：`reason varchar(255) NOT NULL`、`idx_target` / `idx_operator` / `idx_create_time`）；
      ② 给 `member_subscription` 与 `maxlabel_org_license` 各加 `license_grant_id bigint NULL`；
      ③ 把两表的 `plan_id`/`spu_id`/`sku_id`/`source_order_id`/`source_order_item_id`/`source_order_no` 放宽为可空；
      ④ 新增菜单「授权看板」（`/software` 下，`sort=60`）+ 按钮权限 `member:license:gift`
      （`member:license:query` 走菜单本身）。写法照 `026-software-menu-group.sql` 的幂等模式，
      ⚠️ DDL 前先查 `information_schema`（`ADD COLUMN` 不幂等）
- [x] T054 [P] 在 `yudao-cloud/yudao-module-member/yudao-module-member-server/src/test/resources/sql/create_tables.sql`
      同步：新增 `member_license_grant` 表、两张表加 `license_grant_id`、放宽对应列为可空（与 042 保持一致）
- [x] T055 [P] 新建 `.../dal/dataobject/license/MemberLicenseGrantDO.java`：字段照 data-model §1，
      `@TableName("member_license_grant")`、继承 `BaseDO`、类注释写明"一次赠送一行，不可修改/删除"
- [x] T056 [P] 新建 `.../dal/mysql/license/MemberLicenseGrantMapper.java`：`selectPage`（按 targetType/targetId/
      operatorId/createTime 筛，倒序）
- [x] T057 [P] 给 `.../dal/dataobject/subscription/MemberSubscriptionDO.java` 与
      `.../dal/dataobject/org/MaxLabelOrgLicenseDO.java` 各加 `private Long licenseGrantId;`（注释说明：赠送来源）
- [x] T058 [P] 新建管理端 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/api/member/license/index.ts`：
      类型（`LicenseSummaryRespVO` / `LicenseCustomerVO` / `LicenseGrantReqVO` / `LicenseGrantVO`）+
      4 个函数 `getLicenseSummary` / `getLicenseCustomerPage` / `grantLicense` / `getLicenseGrantPage`

## Phase 2: Foundational（阻塞所有用户故事）

**⚠️ CRITICAL**: 本阶段完成前，任何用户故事都不能开始。

- [x] T059 在 `yudao-cloud/yudao-module-member/yudao-module-member-api/src/main/java/cn/iocoder/yudao/module/member/enums/ErrorCodeConstants.java`
      新增：`LICENSE_TARGET_NOT_FOUND`（对象不存在）、`LICENSE_ORG_DISABLED`（企业已停用）、
      `LICENSE_GIFT_MONTHS_INVALID`（时长只能选 1/3/6/12 个月）、`LICENSE_GIFT_LIMIT_EXCEEDED`（赠送后不能超过 12 个月，参数为还能送的月数）、
      `LICENSE_GIFT_SEATS_INVALID`（席位数必须 ≥1）、`LICENSE_GIFT_MONTHS_REQUIRED`（企业无有效批次时赠送席位必须同时填时长）
- [x] T060 [P] 在 `.../module/member/enums/LogRecordConstants.java` 新增赠送操作日志文案
      （成功：「赠送会员授权 {_parameterName}」/ 失败各一条，照该文件既有写法）
- [x] T061 [P] 新建 `.../service/license/LicenseGiftQuota.java`（纯函数工具）：`maxGiftMonths(剩余天数)` =
      `clamp(12 − ceil(剩余月数), 0, 12)`，供明细列与赠送校验**共用同一实现**

## Phase 3: US67 - 一眼看清当前授权规模 (Priority: P1)

**Goal**: 看板顶部三类数字（个人有效 / 企业有效 + 席位 / 当年新获得·延续·合计）。
**Independent Test**: 造「个人有效 / 只有企业席位 / 已过期 / 今年新增 / 去年老客户」五类数据后打开看板核对数字。

- [x] T062 [P] [US67] 先写**失败**测试
      `.../src/test/java/cn/iocoder/yudao/module/member/service/license/LicenseQueryServiceImplTest.java`：
      ① 个人有效数 = 未过期会员数（已过期不计）；② 只有企业席位的会员**不计入个人**；
      ③ 企业有效 = 启用 + 有有效批次（**不看席位是否分配**，Q156）；已折算/已退款/已过期批次不计；
      ④ 当年「新获得」= `MIN(create_time) >= 年初`；「延续」= 当年有行且 `MIN(create_time) < 年初`；
      ⑤ 合计**按客户去重**；⑥ 空库返回全 0 而不是报错
- [x] T063 [US67] 实现 `.../service/license/LicenseQueryService.java` + `Impl#getSummary()`：
      全部用 SQL 聚合（**禁止**先取全量再内存过滤），口径逐条对齐 data-model §4.1 / §4.2
- [x] T064 [US67] 新增 `.../controller/admin/license/LicenseDashboardController.java` 的
      `GET /member/license/summary`（`@PreAuthorize("@ss.hasPermission('member:license:query')")`）+
      `vo/LicenseSummaryRespVO.java`，字段与 contracts §1 一致
- [x] T065 [P] [US67] 先写**失败**测试
      `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/member/license/components/LicenseSummaryCards.spec.ts`：
      断言三个当年数字**并排显示且各自带口径文案**（"新获得 / 延续 / 合计"），不是只显示一个"新增客户数"
- [x] T066 [US67] 实现 `.../views/member/license/components/LicenseSummaryCards.vue`
- [x] T067 [US67] 实现 `.../views/member/license/index.vue` 页面骨架（两卡片区 + 页签容器），
      并确认菜单 `/software` 下能打开（依赖 T053 的菜单数据）；
      **数字下钻**：点「个人有效 / 企业有效 / 三个当年数字」→ 切到明细页签并带上对应筛选（FR-543）

## Phase 4: US68 - 逐个客户看授权到什么时候 (Priority: P2)

**Goal**: 明细表：每客户一行、双来源分别展示 + 合并到期、筛选与关键字、可赠送额度。
**Independent Test**: 用 US67 的五类数据逐行核对起止时间、剩余天数与来源。

- [x] T068 [P] [US68] 先写**失败**测试（同目录 `LicenseQueryServiceImplTest` 追加）：
      ① 双来源会员返回 `personalEndTime` / `orgEndTime` 且合并到期取较晚；
      ② 只有企业席位的会员 `sources` 含 `ORG_SEAT`，**不得**标成未订阅；
      ③ `status=1` 能筛出昨天到期的会员，且 `remainingDays=0`、`expiredDays=1`；
      ④ 关键字命中昵称/手机号/企业名；
      ⑤ `maxGiftMonths` 与 T061 的纯函数一致（含"剩 300 天 = 10 个月 → 最多送 2"的边界）；
      ⑥ **同源同口径**：`status=0` 查出的条数 == `summary` 的「个人有效 + 企业有效」（FR-547 / SC-204）
- [x] T069 [US68] 实现 `LicenseQueryServiceImpl#getCustomerPage()` + `vo/LicenseCustomerPageReqVO.java` /
      `vo/LicenseCustomerRespVO.java`（字段照 contracts §2），在 Controller 加
      `GET /member/license/customer/page`（`member:license:query`）
- [x] T070 [P] [US68] 先写**失败**测试 `.../views/member/license/components/CustomerTable.spec.ts`：
      双来源行显示两个到期日 + 合并到期；过期行剩余天数显示 0 且文案不误导
- [x] T071 [US68] 实现 `.../components/CustomerTable.vue`（筛选：客户类型 / 状态 / 关键字；分页）并接入 `index.vue` 页签

## Phase 5: US69 - 赠送授权并留下记录 (Priority: P3)

**Goal**: 赠送（个人时长 / 企业席位 / 企业时长）+ 必填理由 + 立即生效 + 记录页签。
**Independent Test**: quickstart §四 的 11~21 条。

- [x] T072 [P] [US69] 先写**失败**测试
      `.../src/test/java/cn/iocoder/yudao/module/member/service/license/LicenseGrantServiceImplTest.java`：
      ① 有效客户 → 从**原到期日**顺延（自然月，`end_time = endDate+1天 00:00`）；
      ② 已过期/无授权 → 从**今天**起算；③ 剩 10 个月送 3 → 抛 `LICENSE_GIFT_LIMIT_EXCEEDED`（提示还能送 2）；
      ④ 个人固定 `professional`、企业固定 `enterprise`（请求里没有版本字段）；
      ⑤ 企业送席位 → 有效批次 `seats_total + N`、到期日不变；⑥ 企业送时长 → 该批次 `end_time` 顺延；
      ⑦ 企业无有效批次送席位且未填时长 → 抛 `LICENSE_GIFT_MONTHS_REQUIRED`；
      ⑧ 企业停用 → `LICENSE_ORG_DISABLED`；⑨ 失败时**不产生**授权变更与记录（事务回滚）；
      ⑩ 理由为空 → 拒绝（后端也拦，不能只靠前端）；
      ⑪ 赠送后调用 `MemberSubscriptionService#getCurrent`，返回的到期日即为新值（FR-550：立即生效）
- [x] T073 [US69] 实现 `.../service/license/LicenseGrantService.java` + `Impl` 的**个人送时长**分支
      （写 `member_subscription`：来源列 NULL、`license_grant_id` 指向记录、`edition=professional`）
- [x] T074 [US69] 实现企业分支（送席位 / 送时长 / 无批次时新建赠送批次），落点规则照 research R4；
      ⚠️ 送时长时**同步 `period_months += N`**（`start_time` 不变），与授权页签的"本段授予月数"自洽
- [x] T075 [US69] 在 Controller 加 `POST /member/license/grant`
      （`@PreAuthorize("@ss.hasPermission('member:license:gift')")`）+ `vo/LicenseGrantReqVO`，
      `reason` 必填 ≤255；加 `@LogRecord`
- [x] T076 [US69] 实现赠送记录查询：`LicenseGrantServiceImpl#getGrantPage()` + Controller
      `GET /member/license/grant/page`（`member:license:query`），筛选项照 contracts §4；
      记录**不可修改、不可删除**（不提供更新/删除接口，FR-552）
- [x] T077 [P] [US69] 先写**失败**测试 `.../views/member/license/components/GrantForm.spec.ts`：
      理由为空不许提交；剩 10 个月时「3/6/12」置灰只留 1 个月；提交后 emit success
- [x] T078 [US69] 实现 `.../components/GrantForm.vue`（Dialog：内容类型、档位、席位数、理由、赠送后到期日预览）
- [x] T079 [US69] 实现 `.../components/GrantRecordTable.vue` 并接为 `index.vue` 的第三个页签

## Phase 6: Polish & 交叉关注

- [x] T080 全仓 `grep -rn "getPlanId()"`（`yudao-module-member`），确认放宽可空后没有任何业务逻辑依赖非空；必要时补空值守卫
- [x] T081 [P] 回归：`MemberSubscriptionServiceImplTest` 与 `MaxLabelOrgLicense*` 既有用例全绿（发放路径一行未改）
- [x] T082 [P] 文档：在 `CLAUDE.md` 增加 020 的上线顺序与口径摘要（照 019 的写法）
- [ ] T083 按 [quickstart.md](./quickstart.md) §二~§四 走查一遍（至少 11 / 14 / 15 / 17 四条必过）；
      并记录**看板首屏耗时**（SC-207：常规数据量 ≤ 2 秒）与明细翻页耗时（≤ 1 秒）
- [x] T084 复核权限：未勾 `member:license:gift` 的角色看不到赠送按钮且接口返回 403（contracts §3 最后一条）

## Phase 7: 分析修复补漏（2026-11-16 · /speckit-analyze）

> 编号接在 Polish 之后，是为了**不重排已发出的 T053~T084**；执行时机在 US69 之后、手工验收之前。

- [x] T085 [US69] 会员详情「订阅」页签识别赠送行：
      `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/src/views/member/user/detail/UserSubscriptionList.vue`
      在 `license_grant_id` 有值时把「来源订单」列显示为「运营赠送」（否则赠送行会是空白）；
      后端 `MemberSubscriptionRespVO` 增加 `grantId` 透出。⚠️ 先写组件测试（`UserSubscriptionList.spec.ts`）

---

## Dependencies

```
Phase 1 (T053-T058) → Phase 2 (T059-T061) → US67 (T062-T067) → US68 (T068-T071)
→ US69 (T072-T079) → Phase 7 补漏 (T085) → Polish (T080-T084)
```

- **T053 必须先于 T054**（脚本是 H2 建表脚本的参照）。
- **T062/T068 共用同一个测试类**：T068 的用例追加在 T062 之后，不能并行。
- **T061 被 T068 与 T072 同时依赖**（额度计算只能有一份实现）。
- **既有发放路径（支付回调 → `grant()`）全程不动**：T081 是它的回归护栏。

## Parallel Execution

| 组 | 任务 |
|---|---|
| 后端并行 | T054、T055、T056、T057、T058（不同文件） |
| US67 前端与后端并行 | T062→T063→T064 与 T065→T066（前端只依赖契约） |
| US68 / US69 的前端组件 | T070、T077 可与对应后端任务并行（不同文件） |
| Polish | T081、T082 可并行 |

## MVP

**MVP = US67（T053~T067）**：先让所有者看到三个数字（个人 / 企业 / 当年），
这是本特性唯一无法替代的价值；明细与赠送随后按 P2 / P3 增量交付。

## Format 校验

所有任务均为 `- [ ] T0xx [P?] [USxx?] 描述 + 文件路径`，Setup / Foundational / Polish 不带 Story 标签。

## Phase 8: Convergence

> `/speckit-converge` 2026-11-16 追加：按**当前代码**对照 spec / plan / tasks 的实际缺口。

- [x] T086 把授权看板的**总览与明细改为 SQL 侧聚合与分页**，不再全量取回后内存分组（plan Constraints 明确禁止）per `plan: 统计必须一次查询出数` (contradicts)
- [x] T087 给「赠送记录」页签补筛选：按被赠对象（targetType + targetId）、操作人、时间范围，接上后端已有参数 per FR-553 (partial)
- [x] T088 赠送提交补**幂等**：前端入口早退守卫 + 后端同请求去重（reason + 对象 + 短时间窗），保证连点只生效一次 per spec 边界情况「连点两次确认」 (partial)
- [x] T089 补测试断言：赠送后 `MemberSubscriptionServiceImpl#getCurrent` 立即返回新到期日（FR-550 的用户可见效果） per `T072 ⑪` (partial)

## Phase 9: Convergence

> `/speckit-converge` 2026-11-16（T086 SQL 化之后）追加。

- [x] T090 明确「只有企业席位、没有个人订阅」的会员在**明细与统计**中的归属，并保证 `明细有效条数 == 个人有效 + 企业有效`（SC-204）：当前 SQL 明细包含这类会员，而个人有效数不含，二者会对不上；同时补一条**含该类型数据**的对账用例 per US68-2 / FR-547 (contradicts)

## Phase 10: Convergence

> `/speckit-converge` 2026-11-16（T090 之后）追加。

- [x] T091 按 US68-2 让「只有企业席位、没有个人订阅」的会员在明细中可见（来源=企业席位），并同步调整看板口径使 SC-204 仍成立（例如个人卡片拆出「订阅会员 / 席位会员」两栏）——**需先由所有者裁定规格**（收窄 US68-2，还是新增口径），再实现 per US68-2 (contradicts)

## Phase 11: Convergence

> `/speckit-converge` 2026-11-16（Q161 / B 方案落地后）追加。

- [x] T092 让「席位会员」卡片的**下钻**只列席位会员（明细增加权益类型筛选：订阅 / 席位，或下钻传 seatOnly 参数），当前它下钻到的是全部个人（订阅 + 席位） per FR-543 (partial)

## Phase 12: 赠送独立入口（Q162）

> 2026-11-17 所有者追加：赠送是独立功能，对象范围覆盖全部个人与企业，不限是否已有授权。

- [x] T093 [P] [US69] 后端：新增 `GET /member/license/gift-target/page`（全部个人与企业，按客户类型 / 关键字筛选、分页；权限 `member:license:gift`），到期与额度算法复用赠送服务的同一口径；补服务层与权限测试
- [x] T094 [P] [US69] 管理端：看板顶部新增独立「赠送授权」入口（`GiftTargetPicker` 选对象 → 复用 `GrantForm`）；企业无有效授权时送席位 MUST 同时选时长（Q157 / FR-548）

## Phase 13: 看板卡片口径调整（Q163）

> 2026-11-17 所有者追加：个人卡片只显示订阅会员；当年卡片按「个人会员 + 企业席位」拆分。

- [x] T095 [P] [US67] 后端：总览增加当年分项 `yearNewPersonalCount` / `yearNewOrgSeatCount` / `yearRenewPersonalCount` / `yearRenewOrgSeatCount`，企业席位 = 当年新增有效批次（未退款、未折算）的 `seats_total` 之和；补统计测试
- [x] T096 [P] [US67] 管理端：个人卡片去掉席位会员；当年卡片改为「新获得 / 延续」两组 × 个人会员 / 企业席位 / 合计；三张卡片统一「标题 + 指标」结构并等高对齐
