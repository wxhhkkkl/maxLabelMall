/**
 * 电话号码 → `tel:` 链接。
 *
 * ⚠️ 存在的理由：**占位文案绝不能被拼成可拨号链接**。业务方提供号码之前，联系方式
 * 一律是 `[[企业采购热线待填写]]` 这类占位（FR-054）；照直拼会得到
 * `tel:[[企业采购热线待填写]]` —— 既打不通，又像是站点已经在提供该服务。
 *
 * 所以本函数只在**真的像一个号码**时才返回链接，否则返回空串，让调用方退回纯文本。
 */

/** 号码里允许出现的字符：数字、开头的 `+`、以及常见的分隔符 */
const SEPARATORS = /[\s\-()]/g

/** 少于这个位数就不当作电话号码（区号+号码最短也长于它） */
const MIN_DIGITS = 6

export function telHref(value: unknown): string {
  if (typeof value !== 'string') return ''
  const raw = value.trim()
  if (!raw) return ''

  // 含邮箱特征的一律不当号码
  if (raw.includes('@')) return ''

  const compact = raw.replace(SEPARATORS, '')
  // 只允许 `+` 开头 + 纯数字；`[[…]]`、中文、字母都会在这里被挡掉
  if (!/^\+?\d+$/.test(compact)) return ''

  const digits = compact.replace(/\D/g, '')
  if (digits.length < MIN_DIGITS) return ''

  return `tel:${compact}`
}
