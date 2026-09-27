/**
 * 时间格式化 —— 全站**唯一**的实现处。
 *
 * ⚠️ 为什么必须有这个模块：yudao 在 `YudaoJacksonAutoConfiguration` 里给
 * `LocalDateTime` **全局**注册了 `TimestampLocalDateTimeSerializer`，所以后端返回的
 * 时间**一律是 epoch 毫秒数**（`1790486340000`），不是字符串。
 *
 * 直接插值 `{{ order.payExpireTime }}` 的后果是把这串数字原样显示给用户
 * （实测：订单页显示「支付截止 1790486340000」）。而这个缺陷躲过了全部单元测试，
 * 因为它们的 fixture 写的是 `'2026-09-24 12:00:00'` —— **fixture 谎报了线上格式**。
 *
 * 因此：视图里**不要**再直接插值时间字段，一律过这两个函数。
 */

type TimeInput = number | string | null | undefined

const pad = (n: number) => String(n).padStart(2, '0')

/** 把 `Date` 按**本地时间**拼成 `YYYY-MM-DD HH:mm:ss` */
function fromDate(d: Date): string {
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    ` ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}

/**
 * 已是可读文本的时间原样留下，不做二次解析。
 * 后端有少数字段（以及测试 fixture）给的是这种形式，不该被当成非法值。
 */
const READABLE = /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?$/

/**
 * 格式化为 `YYYY-MM-DD HH:mm:ss`。
 *
 * 认三种输入：epoch 毫秒数、已是可读形式的字符串、ISO 字符串。
 * 空值给空串；无法识别的字符串**原样返回**（宁可显示原文，也不要显示
 * `Invalid Date` / `NaN`）。
 */
export function formatDateTime(v: TimeInput): string {
  if (v === null || v === undefined || v === '') return ''
  if (typeof v === 'number') return Number.isFinite(v) ? fromDate(new Date(v)) : ''
  if (READABLE.test(v)) return v.replace('T', ' ')
  const d = new Date(v)
  return Number.isNaN(d.getTime()) ? v : fromDate(d)
}

/** 只要日期部分 `YYYY-MM-DD`。用于「有效期」这类不需要精确到秒的地方 */
export function formatDate(v: TimeInput): string {
  const full = formatDateTime(v)
  return full ? full.slice(0, 10) : ''
}
