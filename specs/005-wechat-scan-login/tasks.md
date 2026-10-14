---
description: "Task list for 微信扫码登录与首次登录补全资料"
---

# Tasks: 微信扫码登录与首次登录补全资料

**Input**: Design documents from `/specs/005-wechat-scan-login/`
**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需）、
[research.md](./research.md)、[data-model.md](./data-model.md)、
[contracts/](./contracts/)、[quickstart.md](./quickstart.md)

**Tests**: 本项目宪法**原则 I（测试先行，不可协商）** 使测试任务**强制**——每个故事的测试任务
MUST 排在同故事的实现任务之前，且 MUST 先跑一次确认**失败原因正确**。

**Organization**: 按用户故事分组（US16 / US17 / US18，续 004 的 US1~US3）。

## Format: `[ID] [P?] [Story] Description （FR/SC 引用）`

- **[P]**：可并行 —— **不同文件**且不依赖未完成任务。同一文件的多个任务**一律不标 [P]**。
- 任务编号续 004（排到 T336），本特性从 **T337** 起。

**Path Conventions**: 服务端 `yudao-cloud/yudao-module-member/…` + `yudao-module-system/…`；
C 端 `storefront/`；管理端 `yudao-cloud/yudao-ui/yudao-ui-admin-vue3/`。

---

## Phase 1: Setup —— **含前置阻塞，不通过不要往下走**

**Purpose**: 本特性的开工前提**不是写代码**，而是把公众号配置修对。开工基线也要先记。

- [ ] T337 **【阻塞项 · 运营/所有者步骤，非编码】** 走 [quickstart.md](./quickstart.md) §零 的 0.1~0.4：
      ① 后台「系统管理 → 社交平台 → 新增」一条 —— 类型=**微信公众号**、**用户类型选「会员」**、
      填**真实公众号**的 appid/secret；② 微信公众平台配好**网页授权域名**；
      ③ 真机扫一次，确认服务端能拿到 openid。
      ⚠️ **实测现状（2026-10-12）**：库里两条公众号配置的 `user_type` 都是 **1（管理员）**，
      没有会员那条；C 端因此**回退到 yaml**，而线上跑 `local,my`，那份指向的是注释写着
      「测试号（自己的）」的 `wx5b23ba7a5589ecbb` —— **测试号只对体验者白名单开放，真实客户用不了**。
      **不做完这一步，Phase 2 之后全是白做**；若配置拿不到，**回到规格改，别硬做**
- [x] T338 记录基线：`cd storefront && npx vitest run`（预期 **1111 条 / 82 文件**全绿）
      与服务端 `mvn -B test -pl yudao-module-member/yudao-module-member-server`。
      基线不绿就不要开始

---

## Phase 2: Foundational（阻塞所有故事）

**Purpose**: 三个故事都建在"登录票据"和"按 openid 绑定"这两块地基上。

- [x] T339 新增**登录票据**的领域模型与 Redis 存取（`WechatLoginTicket` + 对应的 Redis DAO）：
      字段见 [data-model.md](./data-model.md) §4；键名 MUST 密码学随机、不可推导（FR-149）
- [x] T340 实现票据的**状态机与 TTL**：`待扫 → 已扫待确认 → 待补全 → 已完成`，
      终态 `已过期` / `已作废`；**一次性**（取走即删）、存活 **5 分钟**（FR-149 / FR-152）。
      ⚠️ 不变量：**`已完成` 之前票据里 MUST NOT 出现 `memberId` 或 `token`**（FR-150 的数据形态）
- [x] T341 新增**「按 openid 绑定」**的服务端能力（[research.md](./research.md) R16）：
      既有 `bindSocialUser` 只接受 **code**，而 Q26 把绑定推迟到提交补全页那一刻，
      那时微信授权码（一次性、短命）多半已死。
      ⚠️ **MUST NOT 复用 `bindSocialUser`** —— 它内部有两次无条件解绑，第二条正是
      "该会员原先绑过别的社交账号 → 解绑"（`SocialUserServiceImpl.java:71-76`），
      与 Q21=C 直接冲突（R17）

**Checkpoint**: 地基就位，三个故事可以开始

---

## Phase 3: US16 —— 用微信扫码登录（P1）🎯 MVP

**Goal**: 电脑上出二维码 → 客户用微信扫并确认 → 电脑**自动**进入已登录状态。

**Independent Test**: 用一个**已补全过**的微信号扫码 → 电脑进入已登录、看得到自己的账号。

### 测试（先写、先失败）

- [x] T342 [US16] 服务端 `WechatLoginTicketServiceTest`：**票据状态机** ——
      签发 → 已扫 → 待补全 → 完成；**一次性**（取走后再查拿不到 token）；
      **过期**（TTL 到即终态）；**键名不可推导**（FR-149 / FR-140）
- [x] T343 [P] [US16] `WechatQrPanel.spec.ts` in `storefront/src/components/`：
      **轮询的停止条件** —— 完成即停、过期即停、组件卸载即停；
      **不叠请求**（响应变慢时不并发重发，照 `QrPayDialog` 的做法）（FR-144 / FR-140）
      ⚠️ 这条是本阶段的核心：无限轮询不会报错，只会一直打后端，很难被发现

### 实现

- [x] T344 [US16] 服务端：`POST /member/auth/wechat/scan/create`（[contracts/app-api.md](./contracts/app-api.md) §1）
- [x] T345 [US16] 服务端：`POST /member/auth/wechat/callback` —— 用 **code 换 openid** 写进票据
      （复用既有的 `getSocialUserByCode`）。
      ⚠️ **这一步 MUST NOT 建会员、MUST NOT 建绑定、MUST NOT 签发令牌**（FR-132 / FR-150）——
      这是整条链路最容易写错的地方，写错了 SC-049 就会挂
- [x] T346 [US16] 服务端：`GET /member/auth/wechat/scan/query`（契约 §2）——
      按状态返回不同载荷；**终态之后 MUST 停止给出任何东西**
- [x] T347 [US16] 前端 `storefront/src/api/wechatLogin.ts`：三个调用的封装（契约 §1~§3）
- [x] T348 [US16] 前端 `storefront/src/components/WechatQrPanel.vue`：
      二维码 + 轮询 + 过期重取。**骨架照 `QrPayDialog.vue`**（宪法原则 III：先看既有范式）
- [x] T349 [US16] 前端 `storefront/src/components/LoginDialog.vue`：加「微信登录」入口，
      **与既有的「验证码登录 / 密码登录」并列**（FR-129）。
      ⚠️ **MUST NOT** 动既有两个页签的行为（SC-052）
- [ ] T350 [US16] **手工验收** [quickstart.md](./quickstart.md) §一 的 1~5 步

**Checkpoint**: US16 完整可用 —— 这是 MVP

---

## Phase 4: US17 —— 首次登录必须补手机号（含选填的公司名称与头像）(P2)

**Goal**: 第一次扫码的客户**不直接建号**；补全页提交成功**才建号**，然后直接进入已登录状态。
关掉补全页 = 这次扫码彻底作废。

**Independent Test**: 用一个从没登录过的微信号扫码 → 出现补全页 → 填站内不存在的手机号 + 验证码 →
**此刻才建出会员**并登录；退出后再扫 → 补全页不再出现。

### 测试（先写、先失败）

- [x] T351 [US17] 服务端 `WechatLoginCompleteServiceTest`：**"新号 → 建号"分支**；
      以及**验证码不通过时三个增量全为 0**（会员 0 / 绑定 0 / 登录态无）（FR-147 / SC-054）
      ⚠️ **SC-049 的断言口径**：断的是「**本次运行产生的会员增量**里没有手机号的为 0」，
      **NOT**「系统里没有手机号的会员为 0」—— 线上存量里本来就有 20 个（demo/测试数据），
      照后者写这条用例会**永远红**（见 spec.md SC-049 的说明）
- [x] T352 [P] [US17] `ProfileCompletionDialog.spec.ts` in `storefront/src/components/`：
      三种提示（空框 / 认不出 / 部分）、**关闭即作废**、验证不过不写入（FR-148 / SC-055）

### 实现

- [x] T353 [US17] `member_user` **加 `company` 列**：
      `yudao-cloud/sql/mysql/006-wechat-login-company.sql` + `MemberUserDO` + 相关 VO（[data-model.md](./data-model.md) §1）
      ⚠️ **可空** —— 存量会员全为空，别加成 NOT NULL
- [x] T354 [US17] 服务端：`POST /member/auth/wechat/complete` 的**新号分支**（契约 §4）——
      验码 → **此刻**建号（带 mobile / company）→ 绑定 openid → 签发令牌。
      ⚠️ 建号时 `nickname` / `avatar` **非空**，微信没给资料时 MUST 补默认值
- [x] T355 [US17] 服务端：`POST /member/auth/wechat/abandon`（契约 §5）—— 幂等作废
- [x] T356 [US17] 前端 `storefront/src/components/ProfileCompletionDialog.vue`：
      手机号（必填 + 验证码）/ 公司名称（选填）/ 头像（选填，**默认带微信头像**）
- [x] T357 [US17] 前端：个人资料里露出「公司名称」（可看可改，Q24 / FR-138）——
      改 `storefront/src/views/AccountView.vue` 与 `storefront/src/api/profile.ts`
- [x] T358 [P] [US17] 管理端：会员列表 / 详情露出「公司名称」
      （`yudao-cloud/yudao-ui/yudao-ui-admin-vue3` 的会员页与 `MemberUserRespVO`）
- [x] T359 [US17] 头像的**提交后上传**（FR-151 / [research.md](./research.md) R22）——
      补全页出现时客户**尚未登录**，而 `AppFileController.uploadFile` **要求登录**；
      自选的头像先留在浏览器里，提交成功后再传。**MUST NOT 放开上传接口的登录要求**
- [ ] T360 [US17] **手工验收** [quickstart.md](./quickstart.md) §二 的 6~12 步

**Checkpoint**: US16 + US17 都能独立工作

---

## Phase 5: US18 —— 手机号本来就是我方会员时，绑到原账号而不是另建新号 (P3)

**Goal**: 老客户补的手机号如果是**既有会员**，把微信绑到**那个账号**（积分/订单/地址都回来）；
若那个账号**已经绑过别的微信**，则**拒绝**并告知，先绑的一点不动。

**Independent Test**: 老会员（有历史订单）→ 用没绑过的微信号扫码 → 补全页填**那个手机号** →
进入后「我的订单」里能看到历史订单；再用第三个微信填同一手机号 → **被拒绝**。

### 测试（先写、先失败）

- [x] T361 [US18] 服务端 `WechatLoginCompleteServiceTest`（与 T351 同文件，故不标 [P]）：**两个岔口** ——
      ① 手机号命中既有会员 → **复用不新建**（断言"新增会员数**恰好为 0**"，SC-050）；
      ② 该会员**已绑过另一个微信** → **拒绝**，且先绑那条绑定**一个字段都没被改**（SC-053）
      ⚠️ ② 是本阶段的核心，夹具要造出"已绑过另一个微信的会员"这一状态；
      断言要看**绑定表那一行的内容原封不动**，而不只是"抛了异常" ——
      静默解绑是不会抛异常的（R17）

### 实现

- [x] T362 [US18] 服务端 `MemberAuthServiceImpl`（或新的扫码登录 service）：`complete`
      的**既有会员分支** —— 复用那个会员，**MUST NOT 新建**（FR-136 / FR-145）
- [x] T363 [US18] 服务端 `SocialUserService` / 绑定路径：绑定前先查
      "该会员是否已绑过同类型的社交账号"（`system_social_user_bind`），
      有则**拒绝并告知**；**绕开 `bindSocialUser` 的两次无条件解绑**（FR-146 / R17）。
      ⚠️ 落点建议：新增一个"按 openid 绑定且不解除既有绑定"的方法（T341 的产物），
      **不要去改 `bindSocialUser` 本身** —— 它还服务着支付那条 openid 链路，改了会伤到别处（原则 IV）
- [x] T364 [US18] 前端 `ProfileCompletionDialog.vue`：被拒绝时的提示
      「该账号已绑定其他微信」（Q21=C）
- [ ] T365 [US18] **手工验收** [quickstart.md](./quickstart.md) §三 的 13~18 步

**Checkpoint**: 三个故事全部独立可用

---

## Phase 6: Polish & Cross-Cutting

- [x] T366 全量回归：storefront `npx vitest run` + 服务端 `mvn -B test`，
      确认既有 **1111 条**一条不回归，且**既有的验证码/密码登录与 JSAPI 支付的 openid 链路**行为不变（SC-052）
- [x] T367 [P] 回填实测：把 T338/T366 的条数写回本文件与 [plan.md](./plan.md)；
      把"e2e 不做"与其余不验证项的结论与理由落到 [quickstart.md](./quickstart.md) §五（宪法原则 V）
- [ ] T368 按 [quickstart.md](./quickstart.md) 走 §四，**逐条记录结果**（含失败与跳过）
- [x] T369 发版：**这次前端与后端都要发** —— 服务端新增了接口与一列
      （`python scripts/deploy.py frontend` / `python scripts/deploy.py backend`，见 memory 的 `server-deploy`）
      ⚠️ 后端发版要**等约 90 秒**再验（启动约 73 秒）

---

## 需求 → 任务 对照

| FR | 任务 | FR | 任务 |
|---|---|---|---|
| FR-129 微信登录入口 | T349 | FR-141 不出现"注册"动作 | T354 / T356 |
| FR-130 二维码 + 自动登录 | T344~T349 | FR-142 提交后直接进入 | T354 |
| FR-131 微信内直接授权 | T349 | FR-143 不出现两个账号 | T362 |
| FR-132 只识别、不建号 | T345 | FR-144 轮询有停止条件 | T343 / T346 |
| FR-133 已绑过直接进 | T346 / T347 | FR-145 命中既有会员则复用 | T362 |
| FR-134 补全就是建号那一步 | T354 | FR-146 已绑过别的微信则拒绝 | T363 / T364 |
| FR-135 选填公司与头像 | T356 | FR-147 手机号一律短信验证 | T354 |
| FR-136 命中既有会员则绑定 | T362 | FR-148 关闭即作废 | T352 / T355 |
| FR-137 已有手机号免补全 | T346 | FR-149 票据不可猜测/一次性/短时 | T339 / T340 / T342 |
| FR-138 公司名称是档案一项 | T353 / T357 / T358 | FR-150 提交前不建号不发令牌 | T340 / T345 / T351 |
| FR-139 不影响既有登录 | T366 / T368 | FR-151 头像提交后再传 | T359 |
| FR-140 二维码失效与重取 | T343 / T348 | FR-152 过期后明确提示 | T340 / T348 |

| SC | 任务 |
|---|---|
| SC-047 全程不需要输入 | T350 |
| SC-048 电脑端无需手动动作 | T343 / T350 |
| SC-049 没有手机号的会员为 0 | T351（**断言会员增量为 0**） |
| SC-050 命中既有会员时新增为 0 | T361 |
| SC-051 补全过的不再被要求补全 | T360 |
| SC-052 既有登录方式逐条不变 | T366 |
| SC-053 先绑的微信一条不被改动 | T361 |
| SC-054 未验证通过不写入手机号 | T351 |
| SC-055 关闭补全页三个增量全 0 | T352 |

---

## Dependencies

### 阶段依赖

- **Setup（Phase 1）**：**T337 是硬阻塞** —— 公众号配置不对，后面全白做
- **Foundational（Phase 2）**：依赖 Setup
- **US16（Phase 3）**：依赖 Foundational（票据）
- **US17（Phase 4）**：依赖 **US16** —— 没有扫码链路就没有补全页
- **US18（Phase 5）**：依赖 **US17** —— 它是补全页提交时的另一个分支
- **Polish（Phase 6）**：依赖三个故事完成

⚠️ **三个故事不能并行**：它们是一条链（扫码 → 补全 → 补全的分支），
不像有些特性那样能各自独立开发。**不假装它们能并行。**

### 故事内部

- 测试 MUST 先写、先跑、先失败（原则 I）
- **T337 是人工/运营步骤**，不是编码任务；它不通过时**整个 Phase 2 之后都别开始**
- T339 → T340 是同一块地基的两步，顺序做
- T344 → T345 → T346 是同一批 controller，顺序做

### 并行机会

- T343 可与 T342 并行（不同语言、不同文件）
- T352 可与 T351 并行（同上）
- T358 与 T357 可并行（管理端 vs C 端，不同仓库/文件）
- T367 可与 T366 并行

---

## Parallel Execution Examples

```bash
# US16 阶段：服务端票据测试与前端轮询测试可同时开工
Task: "T342 WechatLoginTicketServiceTest：状态机 / 一次性 / 过期"
Task: "T343 WechatQrPanel.spec.ts：停止条件 / 不叠请求"      # 不同语言不同文件

# US17 阶段：
Task: "T351 服务端：新号建号 / 验证不过三者增量全 0"
Task: "T352 ProfileCompletionDialog.spec.ts：提示与关闭即作废"
```

---

## Implementation Strategy

### MVP First（T337 → T350）

1. **T337 先把公众号配置修对并真机验证**（不通过就停）
2. Phase 2 地基（票据 + 按 openid 绑定）
3. Phase 3 US16
4. 停下验证 quickstart §一 → 已经能演示"扫码就进来"

### 增量交付

1. Setup + Foundational → 地基
2. US16 → 独立验证 → **此时功能上还不完整**（新客户会被卡住），故**不发版**
3. US17 → 独立验证 → **到这一步才可发版**（新老客户都走得通）
4. US18 → 独立验证 → 补上老客户的合并与拒绝
5. 每个故事都在不破坏前一个的前提下加价值

⚠️ **与常见做法的不同**：这里 **US16 单独上线是坏的** —— 新客户扫完码会被永远卡在
"还没有补全页"的状态。所以 **T369 发版必须排在 US17 之后**，不能按 MVP 就发。

### 本清单里要特别当心的五条

1. **T337 不做完，别写代码**（Phase 1 的阻塞项）
2. **T345 那一步绝不能建号** —— 扫码回调只做"识别"，建号在 T354。写反了 SC-049 会挂（FR-150）
3. **T363 绝不能复用 `bindSocialUser`** —— 它会静默解绑先绑的微信（R17）
4. **T359 不能为了省事放开上传接口的登录要求** —— 那等于开一个公开写入口
5. **T369 必须前端后端一起发** —— 服务端新增了接口与一列，只发前端会 404

---

## 本轮实测与偏离（2026-10-14，Phase 1 部分 + Phase 2）

### T337 前置阻塞：**配置部分已完成，真机验证待 US16**

所有者已做：确认是**服务号**（网页授权可用 → **整套设计成立**）、配好网页授权域名、
在「社交平台」建了会员类型的公众号配置。库里核对通过：

```
id=48  type=31（微信公众号） user_type=1（会员） status=启用 deleted=0
       appid=wxfa951475f55eae66   创建于 2026-10-14
id=3   type=31 user_type=1 但 deleted=1（逻辑删除，查询自己会过滤掉）—— 不会造成歧义
```

⚠️ 计划里那句「C 端指向微信测试号」**已失效**：DB 里的会员配置存在且未删，
`SocialClientServiceImpl` 会用它覆盖 yaml，yaml 里那个测试号不再生效。

**T337 暂不勾选** —— 它的验收条件是"真机扫一次确认服务端能拿到 openid"，
而那要等 US16 的接口写完才做得了，自然并到 T350 的手工验收里去。

### 实测数字

| 项 | 结果 |
|---|---|
| 开工基线（T338） | 前端 **1111 条 / 82 文件**全绿；member 模块 **4 条**、`BUILD SUCCESS` |
| 票据状态机单测（T342） | **14/14 绿**（纯 Mockito、不起 Spring） |
| 按 openid 绑定的单测（T361 的一部分） | **5 条新增，system 模块 16/16 绿** |
| member 模块回归 | **18/18 绿**（4 既有 + 14 新增），无破坏 |

### 三处偏离，如实记录

**① T342（票据的测试）提前到 Phase 2 写了。** 任务清单把票据**实现**排在 Phase 2（T339/T340）、
把它的**测试**排在 Phase 3（T342 [US16]）—— 这个相位划分本身有瑕疵：原则 I 要求测试先于实现。
**以原则 I 为准**，测试与实现一起放在 Phase 2 完成。

**② Java 里"测试先行"只能是"接口先行 + 桩实现"再写测试。** 与 004 的 T313 同性质：
先建类型与接口（否则测试连编译都过不了，失败原因就变成"找不到符号"而不是断言），
再写桩实现（`create()` 返回 null、绑定方法返回 null），跑出**断言失败**的红，才写真正的实现。
两次都确认过：红的时候失败原因都是行为不对（NPE、该抛的异常没抛），不是编译错误。

**③ 删掉了 `dal/redis/package-info.java`。** 该文件是上游留的占位，内容自己写着
「占位，后续有类后，可以删除」；本轮往 `dal/redis/` 下加了类，它就变成了一句假话。

### 本轮新增/改动（服务端）

```
新增  member/dal/redis/RedisKeyConstants.java                    票据与 state 的 key 前缀
新增  member/dal/redis/wechat/WechatLoginTicketRedisDAO.java     照 OAuth2AccessTokenRedisDAO 的范式
新增  member/service/auth/WechatLoginTicketStatusEnum.java       5 个状态（已过期不是存储态）
新增  member/service/auth/bo/WechatLoginTicketBO.java            票据的载体
新增  member/service/auth/WechatLoginTicketService{,Impl}.java    状态机
新增  member/../WechatLoginTicketServiceTest.java                14 条
改动  member-api/.../ErrorCodeConstants.java                     1_004_013_000/001
新增  system-api/.../dto/SocialUserBindByOpenidReqDTO.java
改动  system-api/.../SocialUserApi.java + ErrorCodeConstants.java 1_002_018_002/003
改动  system/.../SocialUserService{,Impl}.java + api/social/SocialUserApiImpl.java
改动  system/.../SocialUserServiceImplTest.java                  5 条新增
```

### 2026-10-14 续：US16 服务端（T344 / T345 / T346）

三个 app 端接口实现完毕，**7 条新单测**（`MemberAuthServiceImplTest`，该文件此前 4 条 → 现 11 条）。

| 接口 | 关键点 |
|---|---|
| `POST /member/auth/wechat/scan/create` | 只用既有 `SocialClientApi.getAuthorizeUrl`，再拿 `HttpUtils.replaceUrlQuery` 把系统自带的随机 `state` **换成我们票据的 state** —— 不改 system 模块 |
| `POST /member/auth/wechat/callback` | **只写身份进票据**。测试专门 verify 了「不建号 / 不建绑定 / 不发令牌」四个 `never()`，这是 FR-132/FR-150 的落点 |
| `GET /member/auth/wechat/scan/query` | 不存在 → 已过期；`待补全` 带昵称头像但**无令牌**；`已完成` 交令牌后**立刻删票据**（一次性） |

member 模块回归 **25/25 绿**（11 + 14），无破坏。

**一处实现期发现**：既有 `getAuthorizeUrl` 内部是 `authRequest.authorize(AuthStateUtils.createState())` ——
**state 由它自己生成、没有参数能传我们的**。若直接用，回调就认不出是哪一次扫码。
解法是复用它对 `redirect_uri` 用的同一个 `HttpUtils.replaceUrlQuery`（"先移除再添加"），
把 state 覆盖掉 —— **system 模块一行未改**。

### 2026-10-14 续：US16 前端（T343 / T347 / T348 / T349）+ 一处清单漏项

| 任务 | 产出 |
|---|---|
| T343 | `WechatQrPanel.spec.ts` **6 条**（先写、先红） |
| T347 | `api/wechatLogin.ts`（4 个调用 + 两个类型 + 回跳地址计算） |
| T348 | `WechatQrPanel.vue` —— 照 `QrPayDialog` 的既有范式 |
| T349 | `LoginDialog.vue` 加**第三个页签**「微信登录」+ 协议门禁一视同仁 + 登录成功后的收尾 |

**⚠️ 清单漏了一件必需件，已补做**：微信授权后要跳回一个**属于我们域名的回跳页**
（`/wechat/callback` + `WechatCallbackView.vue`，路由 29 → 30 条）—— 它跑在**手机上**，
是把 `code` 交给服务端的那一环。没有它整条链路走不完。任务清单里没有这一条，属漏项。

**前端实测**：全量 **1118 条 / 83 文件**全绿（原 1111，+6 面板 +1 路由渲染）；
`vue-tsc` 干净；`eslint` 0 error，warning 数 **25 ≤ 改动前 26**（没引入新噪音）。
顺带更新了 `routes.spec.ts` 里那条"路径条数"断言（29 → 30）—— 我的改动让它过期了。

**T343 的测试钉住三件不会报错、只会一直打后端的事**：
① 轮询到「已完成」就停；② 轮询到「已过期」就停；③ **组件卸载就停**。
外加一条**不叠请求**：用一个「永不 resolve」的查询 + fake timers 断言只调了 1 次 ——
写成 `setInterval` 的话这里会是 3 次（那就是在骚扰后端）。

**已知断头路（不是遗漏，是排序）**：扫到「待补全」时面板会 `emit needProfile`，
但补全页在 T356 才做，所以现在新客户会被卡住 —— 计划里已写明 **US16 单独上线是坏的**，
发版必须排在 US17 之后。

### 2026-10-14 续：US17 服务端（T351 / T353 / T354 / T355）

| 任务 | 产出 |
|---|---|
| T353 | `member_user` 加 `company` 列：迁移 SQL `006-wechat-login-company.sql` + `MemberUserDO` + 两个 app VO（资料返回/可改）+ 测试建表脚本 |
| T351 | `MemberAuthServiceImplTest` 新增 **7 条**（该文件 11 → 18 条） |
| T354 | `POST /member/auth/wechat/complete` —— **这一步才建号/绑定/发令牌** |
| T355 | `POST /member/auth/wechat/abandon` —— 幂等作废 |

member 模块回归 **32/32 绿**（18 认证 + 14 票据），`BUILD SUCCESS`。

**两处刻意的设计决定**（都写进了代码注释）：

1. **短信验证码复用「登录场景」（scene=1）**。FR-147 要求"复用既有的短信验证码能力与场景机制" ——
   新开一个场景意味着运营**还得去申请一张新的短信模板**，那是一条不该由代码强加的前置条件。
   `user-sms-login` 的模板现成，语义上也说得通（这一步就是用短信证明"这个号是我的"）。
2. **补全请求里没有头像字段**。契约 §4 里那个 `avatarUrl` 是可省的：补全页出现时客户还没登录、
   传不了头像（FR-151），所以补全页默认用**微信头像**（建号时一并写入），自选头像**提交成功后**再传。

**⚠️ 发版顺序上的硬约束（T369 会用到）**：`member_user.company` 是**新增列**，
**必须先在生产库跑 `006-wechat-login-company.sql`，再发后端**。顺序反了的话，
后端一启动查询 `member_user` 就会因为列不存在而报错。

### 2026-10-14 续：US17 前端（T352 / T356 / T357 / T359）

| 任务 | 产出 |
|---|---|
| T352 | `ProfileCompletionDialog.spec.ts` —— **8 条**，先写先红 |
| T356 | `ProfileCompletionDialog.vue` —— 手机号+验证码（复用登录场景发码）/ 公司名称 / 头像 |
| T357 | 个人资料里露出公司名称：`utils/profile.ts` 第五项 + `ProfileEditDialog` 字段 + `AccountView` 展示 |
| T359 | 头像**提交后**再传：补全页把 `File` 交给上层，`LoginDialog` 在**登录成功之后**才 `uploadFile` + `updateProfile` |

前端全量 **1130 条 / 84 文件**全绿；`vue-tsc` 干净；新写的源码文件 **eslint 全干净**，
`LoginDialog` 的 warning 数 26 → 23（还少了 3 条）。

**⚠️ 接线时抓到一个真 bug（不是测试先抓到的，如实记录）**：
`WechatQrPanel` 原先在「待补全」分支里也调了 `stopAll()` —— 于是客户在**同一台电脑**上填补全页、
后端把票据标成「已完成」之后，**没有任何人在轮询**，令牌永远取不回来。
表现是"点了完成之后什么都不发生"，而且后端日志一切正常。
已改为「待补全」**继续轮询**，并补了一条回归用例钉住它。

顺带把 `needProfile` 改成**只在第一次进入该状态时**发一次（原先每轮都发）——
幂等但会让上层反复打开同一个补全页。

**一处我的改动让既有测试过期**：`utils/profile.spec.ts` 的夹具 `CURRENT` 是"四字段"的，
加了 `company` 之后 `buildProfileUpdate` 会 NPE。已补上夹具项，并顺手为 `company` 补了
3 条它自己的用例（只改公司名 / 清空发空串 / 长度上限 64）。

### 2026-10-14 续：T358 + US18 + 全量回归（T361~T364 / T366 / T367）

**T358 管理端**：`MemberUserBaseVO` 被**响应与更新共用**，所以后端只加一处就两边都有了；
管理端再加**列表一列**（`member/user/index.vue`）与**详情一项**（`UserBasicInfo.vue`）。

**⚠️ US18（T361/T362/T363）的代码其实在 T341/T354 就已经写完了**，不是这轮补的 —— 如实说明：

| 任务 | 它的要求在哪儿落地的 |
|---|---|
| T362「既有会员分支：复用不新建」 | `completeWechatScan` 的第 3 步（写 T354 时就分了新号/老号两条路） |
| T363「已绑过别的微信就拒绝，绕开 `bindSocialUser` 的解绑」 | `bindSocialUserByOpenid`（T341 新写的那条路径） |
| T361「两个岔口的单测」 | `testCompleteWechatScan_existingMobile_reusesWithoutCreating` / `testCompleteWechatScan_memberAlreadyBoundOtherWechat_rejects`（T351 写的，11 步里的两条）+ `testBindSocialUserByOpenid_memberAlreadyBoundOther_throwsAndKeepsOldBinding`（T341 写的） |

也就是说：**US17 与 US18 共用同一条提交路径**，做 US17 时顺带就把 US18 的岔口做掉了。
这轮只补了 **T364**（前端把「该账号已绑定其他微信」的原话透出来的用例）。

**T366 全量回归**：

| 侧 | 结果 |
|---|---|
| 前端 | **1131 条 / 84 文件**全绿 |
| 服务端 system 模块 | **480 条**（8 skipped）、`BUILD SUCCESS` |
| 服务端 member 模块 | **32 条**、`BUILD SUCCESS` |

**T367 回填**：T337 那步的配置核对、各轮实测数字与偏离都已写在本文件；quickstart §五 的
"显式不验证项"（微信授权本身 / e2e / 小程序端）在写规格时就已落好，本轮无需追加。

### 2026-10-14 发版（T369）—— 三步走完，并查出一个**租户隔离**的真问题

按「先改库 → 再发后端 → 再发前端」执行：

| 步 | 结果 |
|---|---|
| ① 生产库跑 `006-wechat-login-company.sql` | `member_user.company varchar(64) NULL`，位置 12（紧跟 avatar），**468 行不变**、已有值 0 行 |
| ② 发后端 | jar 245.9 MB、**无 IDE 编坏的 class**；重启 75 秒后监听 48080、`is-active=active`、**近 3 分钟 ERROR/Exception 计数 = 0**（证明新列确实先于后端生效）。回滚 `/root/yudao-server.jar.bak-20261014-165342` |
| ③ 发前端 | 51 个文件，源站入口 `assets/index-CHBk6lQV.js`。回滚 `/var/www/maxlabel/index.html.bak-20261014-165907` |

**接口实测（生产）**：

```
签发 → code=0  ticket=5bcaa035…（32 位 hex）  expiresIn=300
轮询同一票据 → status=待扫          ← 票据在 Redis 里真的来回
轮询不存在的票据 → status=已过期     ← 空/过期路径也对
```

**⚠️ 发完查出一个真问题：公众号配置建在了别的租户下。**
`SocialClientDO extends TenantBaseDO`，查询**按租户过滤**；而后台默认在**租户 1**（芋道源码）里建的配置
（`id=48, tenant_id=1`），C 端带的却是 `tenant-id: 162`（御旺宸发）→ 查不到 → **回退到 yaml 的测试号**。
表现是"配置明明填了，二维码里却是测试号的 appid"。

修法（所有者授权）：复制 id=48 整行、只把 `tenant_id` 换成 162 → 新 `id=49`（纯新增，不动 id=48）。
复验：二维码 appid 变成 `wxfa951475f55eae66` ✓。

**顺带证实了一条原本挂着的 Deferred 风险**：授权地址里的
`scope=snsapi_userinfo` —— 有它才拿得到微信昵称与头像。规格 Assumptions 里
"网页授权配置是否满足"这一条**至此实测通过**，补全页的默认微信头像能显示。

---

## ⚠️ 发版后发现的一处**漏做**：FR-131（手机微信内直接授权）

**没实现。** `WechatQrPanel.vue` 里没有任何 UA 判断，手机在微信里打开时**照样会出一个二维码**，
而规格 FR-131 要求它"直接走微信授权，MUST NOT 要求客户扫自己屏幕上的二维码"。

**为什么漏了（如实记录，这比漏做本身更值得记）**：
任务清单的「需求 → 任务」表里，FR-131 **是有任务编号的**（映射到 T349），
所以 `/speckit-analyze` 那轮按"每个 FR 都有 ≥1 个任务"判定它**已覆盖**；
但 T349 的描述只写了"加第三个页签 + 协议门禁 + 登录后的收尾"，**从没提到手机分支**。
**映射表只证明了"挂了个号"，没证明"任务描述真的覆盖了那条要求"** ——
这是分析口径的一个盲区（顺便说，T337 那条"真机扫一次"也是同类：挂得上号、但实际没做）。

**它不是十几行能补完的**：手机侧还有一段独立链路 ——
授权回跳后由 `/wechat/callback` 接管，那个页面（跑在手机上）得自己轮询、自己完成登录并跳转回家，
而现在它只做到"告诉客户回电脑上继续"。所以它是一个独立的小特性，不是给面板加个 if。

### Phase 7：FR-131（手机微信内直接授权）—— **不做**（所有者 2026-10-14 决定）

原计划拆成 T370~T374（UA 判断 / 手机侧回跳页接管 / 回跳接口返回票据 / 桌面端回归 / 手工验收），
**所有者明确决定本期不做**（原话「不补131，不考虑手机微信登录问题」），故这五条**取消**，
不再作为待办。决策与影响记在两处：spec.md 的 FR-131 与 quickstart.md §五 的显式不验证项。

**取消的代价，写清楚免得日后当成"没这回事"**：在微信里打开本站的客户点「微信登录」，
看到的会是**二维码**（要另找一台设备扫），而不是直接授权。电脑端主场景不受影响。
