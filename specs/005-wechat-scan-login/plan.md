# Implementation Plan: 微信扫码登录与首次登录补全资料

**Branch**: `005-wechat-scan-login` | **Date**: 2026-10-12 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/005-wechat-scan-login/spec.md`

## Summary

在 C 端加一条**微信扫码登录**入口：电脑上出二维码 → 客户用微信扫并确认 → 电脑自动登录。
第一次来的客户**不直接建号**，而是走一个**补全页**：必填手机号（短信验证）、选填公司名称与头像；
**提交成功那一刻才建号（或绑到既有账号）**，然后直接进入已登录状态（Q26）。

技术路线：**服务端新增一套"登录票据" + C 端一个轮询二维码组件 + 一个补全页**。
行政区划那类"前端能自己算"的东西这里不存在 —— 微信授权、openid、建号、绑定都必须服务端裁决。

---

## ⚠️ 前置阻塞：C 端现在指向的是微信**测试号**

**这是计划阶段第一件要解决的事，且它不是代码问题。** 实测（2026-10-12）：

| 查的东西 | 结果 |
|---|---|
| `system_social_client` 里 `type=31`（公众号）的配置 | 有 **2 条，`user_type` 全是 `1`（管理员）** |
| 有没有 `type=31, user_type=2`（**会员**）的配置 | **没有** |
| 那 C 端怎么办？ | 走**回退**：`SocialClientServiceImpl:206` 查不到 DB 配置就回退到 yaml。而线上跑 `local,my`，`application-local.yaml:180` 的 `wx.mp.app-id` = **`wx5b23ba7a5589ecbb`** —— 该行注释明写「**测试号（自己的）**」 |

**后果**：微信**测试号只对"体验者"白名单开放**，真实客户扫了也授权不了。也就是说，
**代码全写完，真实客户依然用不了**。

**要做的（运营侧，不是开发）**：
1. **先确认这个号是「服务号」还是「订阅号」** ← **这一步就能定生死**
2. 后台「系统管理 → 社交平台 → 新增」：类型选 **微信公众号**、**用户类型选「会员」**、
   填**真实公众号**的 appid / secret（表单里本来就有"用户类型"单选框，能选）
3. 公众号后台配好 **网页授权域名**（= 本站域名）
4. 配完立刻用真机扫一次验证，确认服务端日志里**能拿到 openid** —— 这一步过不了，后面全是白干

**⚠️ 为什么第 1 步是定生死的**：**网页授权是「服务号」才有的能力，订阅号没有。**
若这个号是订阅号，那么"PC 出二维码 → 手机微信授权 → 回跳拿 code"这条链**根本走不通**，
整套设计（登录票据 + 轮询 + 延后建号）都要换形态 —— 不是改个参数能救的。
这也是为什么 `tasks.md` 把 T337 列成**硬阻塞**而不是"最好先做"：
它挡的不是流程，是**整套设计是否成立**。

**2026-10-14 复核**：T337 **仍未完成** —— `system_social_client` 里 `type=31` 的两条
`user_type` 依旧都是 1（管理员），仍无会员那条。故 `/speckit-implement` 在此**停下**，
未开始写任何代码（所有者 2026-10-14 决定：先做 T337，别提前写）。

---

## Technical Context

**Language/Version**: Java 17/25（yudao-cloud 服务端）+ TypeScript 5 / Vue 3（storefront）
**Primary Dependencies**: **无新增**（微信侧走既有的 `justauth` / `WxJava`；二维码由前端生成或后端出串）
**Storage**: MySQL（`member_user` **加一列**承载公司名称）+ Redis（登录票据）
**Testing**: 服务端 JUnit（票据状态机、建号/绑定的分支）；storefront Vitest（轮询、补全页、错误态）
**Target Platform**: C 端 Web 前台（PC 扫码 + 手机微信内授权）
**Project Type**: 多端（服务端 + C 端 Web；管理端只多一列展示）
**Constraints**:
- 行政区划那类"前端自算"不适用 —— 授权、openid、建号、绑定都在服务端
- 补全页出现时**客户尚未登录**，所以**不能**调用要求登录的接口（头像上传见 FR-151）
- 票据在"已扫码待补全"期间**等价于凭据**，必须不可猜测（FR-149）
**Scale/Scope**: 新增 1 个 Redis 票据域 + 3~4 个 app 接口 + 1 个二维码组件 + 1 个补全页；`member_user` 加 1 列

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 本特性如何满足 |
|---|---|
| **I 测试先行** | 两条主链路都能**不依赖微信**地测：①**票据状态机**（签发→已扫→待补全→已完成/过期，含一次性与过期）是纯服务端逻辑，用单测覆盖；②**建号 / 合并绑定 / 拒绝重复绑定**三岔口，用「已存在的手机号」「已绑定过别的微信」等夹具直接调服务层。**微信授权本身不测**（外部系统），见 quickstart 的显式不验证项 |
| **II 先澄清，不猜测** | Q19~Q26 共 8 项决策已定；本轮**没有新冒出**需要所有者表态的分叉。规格里唯一"自行补的默认值"（票据 5 分钟，FR-152）已显式标注 |
| **III 复用既有能力** | 复用：`getSocialUserByCode`（code 换 openid）、`MemberUserService.createUser`、会员 `update` / `update-mobile`、文件上传、`QrPayDialog` 的**轮询骨架**。<br>⚠️ **但两处既有行为必须绕开或新增**（见 research.md R16/R17）：绑定目前**只认 code**（要补一条"按 openid 绑定"）；`bindSocialUser` 内部有**两次无条件解绑**，其中一次正是 Q21=C 明令禁止的 |
| **IV 最小改动** | 服务端只碰会员模块 + 一列；前端只碰登录弹层与两个新组件；**不动**既有的短信/密码登录、不动支付相关的 openid 绑定链路 |
| **V 增量可验证** | US16（扫码登录）可独立演示；US17（补全）可独立演示；US18（合并到原账号）可独立演示。SC-049/053/055 都是"断言某个增量恰好为 0 / 恰好发生一次"的可数事实 |

**Gate 结论：通过，无 MUST 违规。** 但有一条**不是违规、却会决定成败的前置条件**（上文那个"测试号"），
它写在最前面而不是藏在 Notes 里。

## Project Structure

### Documentation (this feature)

```text
specs/005-wechat-scan-login/
├── plan.md              # 本文件
├── spec.md              # 功能规格（含 Q19~Q26）
├── research.md          # Phase 0 输出：R15~R24
├── data-model.md        # Phase 1 输出：票据状态机、会员字段、绑定关系
├── contracts/
│   ├── app-api.md       # C 端新增/改动的接口契约
│   └── admin-api.md     # 管理端：公司名称的展示
├── quickstart.md        # 手工验证清单与显式不验证项
└── checklists/requirements.md
```

### Source Code (repository root)

```text
storefront/src/
├── api/
│   ├── social.ts                  # 改：补上"扫码登录"用的几个调用
│   └── wechatLogin.ts             # 新增：票据签发 / 轮询 / 提交补全
├── components/
│   ├── LoginDialog.vue            # 改：加「微信登录」入口与二维码视图
│   ├── WechatQrPanel.vue          # 新增：二维码 + 轮询 + 过期重取（骨架照 QrPayDialog）
│   └── ProfileCompletionDialog.vue# 新增：补全页（手机号+验证码 / 公司名称 / 头像）
└── views/AccountView.vue          # 改：个人资料里露出「公司名称」（可看可改，Q24）

yudao-cloud/yudao-module-member/yudao-module-member-server/src/main/java/cn/iocoder/yudao/module/member/
├── controller/app/auth/           # 新增：票据签发 / 查询 / 提交补全（app-api）
├── service/auth/                  # 新增：扫码登录的编排（建号 / 合并 / 拒绝重复绑定）
└── dal/dataobject/user/           # 改：MemberUserDO 加 company 字段

yudao-cloud/yudao-module-system/.../service/social/   # 改：补一条"按 openid 绑定"的能力
yudao-cloud/sql/mysql/006-wechat-login-company.sql    # 新增：member_user 加列

specs/005-wechat-scan-login/                           # 本目录
```

**Structure Decision**：沿用既有分层。前端把二维码与补全页拆成两个独立组件，
是因为它们的**生命周期不同**（二维码是一次登录会话，补全页是一次表单），
混在一个组件里会让"关掉二维码"和"提交补全"两套状态互相纠缠。

## 实施分期

### Phase 0 —— **先验证前置条件**（不做完不写代码）

1. 按上文"前置阻塞"把 `type=31, user_type=2` 的真实公众号配置配好
2. **真机扫一次**走通微信授权，确认能拿到 openid（能被服务端换成 `SocialUserRespDTO`）
3. **若拿不到** → 停下来回到规格，不要硬做

### Phase 1 —— 票据与扫码链路（US16 的骨架）

4. 服务端：票据的签发 / 查询 / 过期；不可猜测、一次性（FR-149）
5. 服务端：`social-auth-redirect` 的产物 + 回跳页处理，把 openid 写进票据（**不建号**，FR-132/150）
6. 前端：`WechatQrPanel` 二维码 + 轮询；过期提示与重取（FR-140）

### Phase 2 —— 补全页与建号（US17）

7. 服务端：提交补全 → 验码 → 建号（或合并）→ 绑定 → **此刻才签发 token**（FR-134/142/150）
8. 前端：`ProfileCompletionDialog`；关闭 = 作废这次扫码（FR-148）
9. `member_user` 加 `company` 列 + 后台/前台露出（FR-138 / Q24）

### Phase 3 —— 合并到原账号与拒绝重复绑定（US18）

10. 手机号命中既有会员 → 校验通过后绑到那个会员（FR-136 / FR-145）
11. **绕开 `bindSocialUser` 的两次无条件解绑**，改为"已绑过别的微信就拒绝"（FR-146 / Q21=C）

### Phase 4 —— 收尾

12. 头像：默认微信头像，自选头像**提交后**再传（FR-151）
13. 回归：既有短信 / 密码登录、JSAPI 支付的 openid 绑定链路逐条不回归（SC-052）

## 测试策略

| 层 | 内容 |
|---|---|
| **服务端单测（主战场）** | 票据状态机（签发/查询/一次性/过期）；**提交补全的三个岔口**：新号→建号、老号→合并、老号已绑别的微信→拒绝；「验证不通过则什么都不建」；`bindSocialUser` 的解绑副作用**不被触发** |
| **storefront 单测** | 轮询的停止条件（完成/过期/卸载）；补全页的三条提示与"验证不过不写入"；关闭即作废 |
| **e2e** | **不做** —— 见 quickstart 的显式不验证项 |
| **显式不验证** | 微信授权本身（外部系统）；真机扫码（人工，见 quickstart） |

## 主要风险与对策

| 风险 | 对策 |
|---|---|
| **公众号配置不对（测试号 / 类型选错）** | Phase 0 先验证；这是**唯一会让整个特性白做**的风险 |
| **微信授权码短命**（提交补全时已失效） | 票据里存**openid**而不是 code；绑定走新增的"按 openid 绑定"（R16） |
| **静默顶掉先绑的微信** | 绝不复用 `bindSocialUser`；见 R17 与 SC-053 |
| **补全页停留过久** | 票据过期 → 明确提示重新扫码（FR-152） |
| **主页面的二维码被他人扫走** | **显式接受**（Q25=B），已记入规格 Edge Cases |
| 误把"社交身份"当成"社交绑定" | SC-055 已把两者分开断言 |

## Post-Design Re-check

Phase 1 产出（[research.md](./research.md) R15~R24、[data-model.md](./data-model.md)、
[contracts/](./contracts/)、[quickstart.md](./quickstart.md)）之后，重新逐条对照宪法：

| 原则 | 设计后复核 | 结论 |
|---|---|---|
| I 测试先行 | 设计刻意把整条链路切成"**不带微信也能测**"（R24）：票据的输入就是一个 openid 字符串。于是扫码之后的所有逻辑都能先写测试见红；唯一测不了的只剩"微信给不给这个 openid" | 通过 |
| II 先澄清不猜测 | 设计中**没有**新冒出需要所有者表态的分叉。规格里唯一"自行补的默认值"（票据 5 分钟）已在 FR-152 显式标注 | 通过 |
| III 复用既有能力 | 复用清单见 Constitution Check；**两处不能复用**（只认 code 的绑定、会静默解绑的 `bindSocialUser`）都已写成 R16/R17 并给了替代做法 | 通过 |
| IV 最小改动 | 表结构只加一列；管理端只多一列展示；前端只碰登录弹层 + 两个新组件。契约里显式列出"不动"的东西（既有的两种登录、支付 openid 链路、社交平台那两条管理员配置） | 通过 |
| V 增量可验证 | US16 / US17 / US18 三个故事各有独立的手工清单段落（quickstart §一/§二/§三）；SC-050/053/054/055 都是"断言某个增量恰好为 0 或恰好发生一次"的可数事实 | 通过 |

**无新增违规，Complexity Tracking 仍为空。**

**但有一条比违规更该先处理的东西**：plan.md 开头那个「前置阻塞」—— C 端现在指向微信**测试号**。
它不是宪法问题，是**前提问题**：配置不修，代码写得再对，真实客户也用不了。
实施的第一件事就是把 quickstart §零 那四步走完。

