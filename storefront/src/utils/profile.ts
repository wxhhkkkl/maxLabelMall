import type { ProfileUpdateReq } from '@/types'

/**
 * 「编辑资料」的校验与请求体构造。
 *
 * 三条口径全部来自后端 `AppMemberUserUpdateReqVO` 与本轮的澄清，**不要凭直觉改**：
 *
 * 1. **只提交改动过的字段**。后端四个字段**都不是必填**（该 VO 上没有任何 `@NotNull`），
 *    而更新走 MyBatis-Plus 默认的 `NOT_NULL` 策略 —— 所以「省略 = 保留原值」。
 *    把没改的字段也发回去，反而会用**陈旧值覆盖**别处的改动（多标签页时尤其）。
 * 2. **空串 ≠ 省略**：要清空字段必须提交**空串**（`@URL`/`@Email` 都对空串放行，已核实
 *    hibernate-validator 9.1.3 的字节码）。所以邮箱清空要发 `''`。
 * 3. **昵称必填、邮箱可清空**：昵称是展示名，清空会让顶栏回落成脱敏手机号（FR-016）；
 *    邮箱是可选信息，用户有权删掉。头像**不提供移除**（无需求来源）。
 *
 * ⚠️ 历史教训：早期规格写的是「四项必须同时提交」，那来自把 `@Schema(requiredMode = REQUIRED)`
 * （**Swagger 文档注解**）误读成了校验注解 —— 见 spec 的 Clarifications 订正条。
 */

/** 表单里可编辑的那四个字段 */
export interface ProfileForm {
  nickname: string
  avatar: string
  email: string
  sex: number | undefined
}

/** 邮箱长度上限 —— 与后端 `@Size(max = 50)` 对齐 */
const EMAIL_MAX = 50

/** 刻意放宽：只要求「有 @ 且 @ 后有 .」这类最低限度，真正的把关交给后端 `@Email` */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/** 性别可选项 —— 与后端共享枚举 `SexEnum` 一致（`0 未知 / 1 男 / 2 女`） */
export const SEX_LABELS: Array<{ value: number; label: string }> = [
  { value: 0, label: '未知' },
  { value: 1, label: '男' },
  { value: 2, label: '女' },
]

/**
 * 性别的中文名。**未设置与未知取值都回落空串** —— 后端该字段没有枚举校验，
 * 遇到不认识的值不该猜一个中文出来。
 */
export function sexLabel(sex?: number | null): string {
  return SEX_LABELS.find((s) => s.value === sex)?.label ?? ''
}

/** 校验表单。`ok=false` 时 `message` 是给用户看的文案 */
export function validateProfile(form: { nickname?: string; email?: string }): {
  ok: boolean
  message: string
} {
  if (!form.nickname?.trim()) {
    return { ok: false, message: '请填写昵称' }
  }
  const email = (form.email ?? '').trim()
  // 空邮箱是合法的（= 清空）
  if (email && !EMAIL_RE.test(email)) {
    return { ok: false, message: '邮箱格式不正确' }
  }
  if (email.length > EMAIL_MAX) {
    return { ok: false, message: `邮箱长度不能超过 ${EMAIL_MAX} 个字符` }
  }
  return { ok: true, message: '' }
}

/**
 * 比对「当前值」与「表单值」，**只返回改动过的字段**。
 *
 * - 没改动 → `{}`（调用方据此可以不发请求）
 * - 邮箱被清空 → `{ email: '' }`（**空串**，不是省略）
 * - 文本字段比对前先去首尾空格，避免"只多了个空格"就发请求
 */
export function buildProfileUpdate(
  current: Partial<ProfileForm>,
  next: ProfileForm,
): ProfileUpdateReq {
  const body: ProfileUpdateReq = {}

  const nickname = next.nickname.trim()
  if (nickname !== (current.nickname ?? '').trim()) {
    body.nickname = nickname
  }

  const avatar = next.avatar.trim()
  if (avatar !== (current.avatar ?? '').trim()) {
    body.avatar = avatar
  }

  const email = next.email.trim()
  if (email !== (current.email ?? '').trim()) {
    body.email = email
  }

  if (next.sex !== current.sex) {
    body.sex = next.sex
  }

  return body
}
