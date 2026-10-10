import { describe, expect, it } from 'vitest'

import { formatDate, formatDateTime, toMillis } from './time'

/**
 * ⚠️ 这些用例存在的理由是一个**真实缺陷**（2026-09-27 实测发现）：
 *
 * yudao 在 `YudaoJacksonAutoConfiguration` 里给 `LocalDateTime` 全局注册了
 * `TimestampLocalDateTimeSerializer`，所以**所有时间字段回来都是 epoch 毫秒数**，
 * 不是字符串。而订单页当时直接插值 `{{ order.payExpireTime }}`，页面上显示的是
 * `支付截止 1790486340000` —— **单测没抓到**，因为它们的 fixture 用的是
 * `'2026-09-24 12:00:00'` 这种字符串，**谎报了线上的格式**。
 *
 * 所以下面既有格式化用例，也有一条专门盯"数字不许被原样渲染"的用例。
 */
describe('formatDateTime', () => {
  it('把 epoch 毫秒数格式化成可读时间（用本地时间构造，避免依赖运行机器的时区）', () => {
    const ms = new Date(2026, 8, 27, 13, 19, 0).getTime() // 本地 2026-09-27 13:19:00
    expect(formatDateTime(ms)).toBe('2026-09-27 13:19:00')
  })

  it('**数字绝不会被原样渲染出来** —— 这正是线上那个缺陷', () => {
    const out = formatDateTime(1790486340000)
    expect(out).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(out).not.toContain('1790486340000')
  })

  it('已经是可读字符串的原样返回（后端某些字段仍可能给字符串）', () => {
    expect(formatDateTime('2026-09-24 12:00:00')).toBe('2026-09-24 12:00:00')
  })

  it('ISO 字符串也能认', () => {
    expect(formatDateTime('2026-09-24T12:00:00')).toBe('2026-09-24 12:00:00')
  })

  it('空值给空串，不渲染成 Invalid Date 或 NaN', () => {
    expect(formatDateTime(null)).toBe('')
    expect(formatDateTime(undefined)).toBe('')
    expect(formatDateTime('')).toBe('')
  })

  it('无法识别的字符串原样返回（宁可显示原文，也不要显示 Invalid Date）', () => {
    expect(formatDateTime('待确认')).toBe('待确认')
  })
})

describe('toMillis', () => {
  it('epoch 毫秒数原样返回', () => {
    expect(toMillis(1790486340000)).toBe(1790486340000)
  })

  it('可读字符串与 ISO 字符串都转成毫秒数', () => {
    const ms = new Date(2026, 8, 24, 12, 0, 0).getTime()
    expect(toMillis('2026-09-24 12:00:00')).toBe(ms)
    expect(toMillis('2026-09-24T12:00:00')).toBe(ms)
  })

  it('认不出的一律给 undefined —— 让调用方按「没有截止时间」处理，而不是拿 NaN 去比大小', () => {
    expect(toMillis(null)).toBeUndefined()
    expect(toMillis(undefined)).toBeUndefined()
    expect(toMillis('')).toBeUndefined()
    expect(toMillis('待确认')).toBeUndefined()
    expect(toMillis(Number.NaN)).toBeUndefined()
  })
})

describe('formatDate', () => {
  it('只取日期部分', () => {
    const ms = new Date(2026, 8, 27, 13, 19, 0).getTime()
    expect(formatDate(ms)).toBe('2026-09-27')
  })

  it('带时间的字符串截到日期', () => {
    expect(formatDate('2026-09-24 12:00:00')).toBe('2026-09-24')
  })

  it('空值给空串', () => {
    expect(formatDate(null)).toBe('')
  })
})
