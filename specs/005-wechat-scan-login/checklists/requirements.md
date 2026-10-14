# Specification Quality Checklist: 微信扫码登录与首次登录补全资料

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-12
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

**第六轮校验（2026-10-12，`/speckit-analyze` 之后）：全部通过。**

`/speckit-analyze` 查出 1 条 CRITICAL + 3 条中危，已全部修掉：

| 问题 | 修法 |
|---|---|
| **SC-049 永远不可能通过**（要求"系统里没有手机号的会员为 0"，而线上**本来就有 20 个** demo/测试会员没有手机号） | 口径收窄成「**本次运行产生的会员增量**里没有手机号的为 0」；`tasks.md` T351 同步加警告 |
| FR-145 与 FR-136 **近乎逐字重复** | **改造** FR-145（保编号避免断号）成一条真正缺的要求：合并后客户看到的是**原账号**的全部数据（US18 场景 2 一直缺这条 FR） |
| 票据状态集**三处不一致**（4 / 6 / 5 个） | 统一为 **5 个**：`待扫 / 待补全 / 已完成 / 已过期 / 已作废`。顺带发现 **`已扫待确认` 根本观测不到** —— 公众号网页授权不回调"已扫"事件，故去掉 |
| 事实表把 `social-login` 写得像"正在被使用" | 实查：**前台全仓零命中**；真正在用的是 `bindSocialUser`（JSAPI 支付）。措辞已改，并补一行"本特性 MUST NOT 破坏它" |
| FR-137 被 FR-133 完全包含 | 保留编号，降为对 FR-133 的引用（"一律以 FR-133 为准"） |

**第五轮校验（2026-10-12，第二次 `/speckit-clarify`）：未提问，全部通过。**

这一轮**没有找到值得正式澄清的用户侧歧义**，因此一个问题都没问。但按分类法扫的时候在**源码**里
挖到两处会直接决定计划怎么写的硬事实，已写进「已核实的事实」：

1. **绑定目前只能按授权码（code）** —— `bindSocialUser` 用 code 换 openid，**没有"按 openid 绑定"的入口**
   （`SocialUserServiceImpl.java:62-66`）。而 Q26 把绑定推迟到"提交补全页"那一刻，
   那时微信授权码（一次性、短命）很可能已失效 → **计划阶段必须补一条"按 openid 绑定"的能力**。
2. ⚠️⚠️ **`bindSocialUser` 内部有两次无条件解绑**（`SocialUserServiceImpl.java:71-76`），
   其中「该会员原先绑过别的社交账号 → 解绑」**正是 Q21=C 明令禁止的行为**。
   → FR-146 / SC-053 **绝不能复用这个方法**；复用会被**静默**违反（数据看着对，只是先绑的微信登不进去了）。

另外补了三处，其中一处是**规格自行补的默认值**（已显式标注、不是所有者表态）：

- **FR-152**：补全页停留过久、凭据失效后的提示与出路（默认有效期 **5 分钟**，计划阶段按微信授权码实际寿命确认）
- **SC-055** 收紧：把"社交身份"与"社交绑定"分开算 —— 扫码可能落一条"只记 openid、没绑任何会员"的
  身份记录，那是正常产物，不算绑定增量
- 事实表里那条 `social.ts` 注释层面的怀疑，换成了服务端源码位置的直接证据

**第四轮校验（2026-10-12）：全部通过。**

`/speckit-clarify` 问了 4 个问题，其后所有者又提出一处设计改进（**Q26：延后建号**）——
它牵动 FR-132/134/135/141/148 与 SC-049/055，已全部同步、无遗留矛盾。
连同 `/speckit-specify` 那一轮的 3 个，本规格共 **8 项已确认决策（Q19~Q26）**：

| 决策 | 结论 | 落点 |
|---|---|---|
| Q19 | 电脑端扫码后**自动**登录（服务端要有票据机制；参照既有 `QrPayDialog` 范式） | FR-130 / FR-144 |
| Q20 | 补手机号命中既有会员时**必须短信验证**才允许绑定 | FR-136 / FR-145 / SC-050 |
| Q21 | 一个会员只允许绑一个微信；后绑的被**拒绝并告知**，不静默顶掉 | FR-146 / SC-053 |
| Q22 | 补全的手机号**一律**短信验证，不分新号 / 老号 | FR-147 / SC-054 |
| Q23 | 没手机号时**拦住一切会员功能**；关掉补全页 = 回到匿名 | FR-134 / FR-148 / SC-049 / SC-055 |
| Q24 | 「公司名称」是**会员档案的一项**，前台可见可改、后台可见 | FR-138 |
| Q25 | 二维码被他人扫走的风险**不加额外提示**（显式接受） | Edge Cases |
| Q26 | 扫码后**不先建会员**，填完补全页才建（或绑到原账号） | FR-132 / FR-134 / FR-148 / FR-150 / FR-151 / SC-049 / SC-055 |

**显式接受的风险（不是被忽略的漏洞）**：Q25=B 意味着自动登录的固有代价被接受 ——
有人诱导电脑前的人打开二维码、自己扫走并确认，电脑会登录成扫码者的账号。
记录在 Clarifications 与 Edge Cases 两处，供将来复盘。

**写规格时实查、可直接引用的事实**：

- **既有 `socialLogin` 的建号时机与 Q26 相反**：它对未绑定的 openid 会**立刻** `createUser` + 绑定
  （`MemberAuthServiceImpl.java:110-127`）。Q26 要求"填完再建"，所以这条路径**不能直接复用**；
  但它另一半（**已绑定的 openid 直接读用户**）可以复用。
- **既有逻辑也不会按手机号找原账号** —— 「手机号已注册过就绑到原用户」**是新增逻辑**，
  也是本特性最容易出数据事故的地方（US3 / FR-136 / SC-050）。
- ⚠️ **既有 `bindSocialUser` 有个"该 openid 原先绑过别的会员会被解绑"的副作用**
  （`storefront/src/api/social.ts:43-48` 注释），它与 **Q21=C 直接冲突** ——
  实现时 MUST NOT 直接复用那条路径的默认行为，否则 FR-146 / SC-053 会被静默违反。
- **`member_user` 没有"公司名称"列**（线上库实查）→ FR-138 是一次真实的数据模型改动。
- **`member_user.mobile` 可空** → "没有手机号的会员"是库里真实存在的状态，不是假想。
- 前台已有 `QrPayDialog.vue`（PC 二维码 + 定时轮询）可作范式参考（宪法原则 III）。
- 会员手机号+验证码登录已存在，本特性 MUST NOT 破坏它（SC-052）。

**第七轮（2026-10-14）：所有者取消了一条 MUST 要求。**

`FR-131`（手机在微信内打开时 MUST 直接授权、不出二维码）由所有者明确决定**本期不做**。
它本已实现了一半、又在发版后才被发现其实**一行都没写**；
所有者选择取消而不是补做。取消的留痕落在三处：`spec.md` 的 FR-131、`tasks.md` 的 Phase 7、
`quickstart.md` §五 的显式不验证项 —— **没有静默删除**，因为它是写在规格里的 MUST。

取消的代价也已写明：在微信里打开本站的客户点「微信登录」会看到二维码（需另找设备扫），
电脑端主场景不受影响。
