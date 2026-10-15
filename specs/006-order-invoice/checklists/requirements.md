# Specification Quality Checklist: 订单开票

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-15
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
      —— 提到的是既有能力的**边界事实**（文件服务、菜单权限点、订单快照），与 005 同一写法；
      FR 里没有出现类名 / 表名 / 接口路径
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders —— 四步闭环用业务语言写成 US19~US22
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain —— Q27 / Q28 已由所有者答复并回填（2026-10-15）
- [x] Requirements are testable and unambiguous —— 每条 FR 都能写成用例；
      两处待答项已标出而非默认
- [x] Success criteria are measurable —— SC-056~063 均为可断言的计数 / 步骤数 / 时长
- [x] Success criteria are technology-agnostic
- [x] All acceptance scenarios are defined —— 4 个故事共 17 条 Given/When/Then
- [x] Edge cases are identified —— 未支付、实付 0 元、退款后、账号合并、重复提交、上传异常、文件丢失
- [x] Scope is clearly bounded —— 本期不做清单 + 显式不做红冲 / 拆票 / 自动开票
- [x] Dependencies and assumptions identified —— 类型清单、金额口径、多条抬头、公网可读链接

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows —— 申请 / 处理 / 下载 / 抬头簿
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- **五处澄清已答复并回填**（2026-10-15，Q27~Q31）：
  1. **Q27** → 两种电子票（电子普通发票 / 电子增值税专用发票），**不支持纸质与邮寄**；
     字段（**后被 Q37 收敛**）：两种票都只必填「名称 + 税号」，地址/电话/开户行选填
  2. **Q28** → **能补开**（订单详情入口，限已支付且未开票），**申请提交后不可改**
  3. **Q29** → 后台权限**登记权限点、走现有角色体系**；接口 MUST 用同一权限点校验，不只靠前端藏按钮
  4. **Q30** → 订单退款 / 取消时，**尚未开具**的申请**作废**并移出待开票列表，客户端如实显示
  5. **Q31** → 重新上传**覆盖**，只留最新一份，不留历史
- **澄清带来的连锁改动**（已同步，别按旧版读）：新增 **US20**（原 US2~US4 顺移为 US21~US23）；
  `FR-176` 订单详情展示改成**四态**；新增 `FR-182`/`FR-183`/`FR-184`、`SC-064`/`SC-065`，
  `SC-059` 扩写；`FR-172`/`FR-174` 补全口径
- **三处显式接受的风险 / 自取默认值**（都已在规格里写明，不是遗漏）：
  - 发票文件沿用既有对象存储，链接**公网可读**（实测 200）—— 加鉴权要改下载链路，属另一次改动
  - 补开**对上线前的历史订单同样开放**（📌 尚未得到所有者明确确认），运营开具前须先核对线下是否已开过
  - 重新上传不留痕迹（Q31 已确认接受）—— 传错成别家发票时事后查不到换过什么
- 清单全部通过，可直接进入 `/speckit-plan`。
