import { describe, expect, it } from 'vitest'

import {
  COMMENT_FOLD_CHARS,
  COMMENT_CONTENT_MAX,
  COMMENT_PIC_MAX,
  formatCommentTime,
  shouldFoldComment,
  validateComment,
} from './comment'

/**
 * 评价的纯函数层（FR-072 折叠 / FR-074 约束 / FR-075 提交前校验）。
 *
 * ⚠️ **这些约束后端一个都不校验**（评分没有 `@Min/@Max`、内容没有 `@Size`），
 * 真正兜底的只有数据库列宽。所以前端这一层不是"锦上添花的提前反馈"，
 * 而是**唯一**的校验 —— 漏了就会变成"用户点了提交，收到一条数据库层的报错"。
 * 见 contracts/app-api.md §3.2。
 */
describe('shouldFoldComment —— 折叠阈值（FR-072）', () => {
  it('恰好 120 字符不折叠', () => {
    expect(shouldFoldComment('好'.repeat(COMMENT_FOLD_CHARS))).toBe(false)
  })

  it('121 字符折叠', () => {
    expect(shouldFoldComment('好'.repeat(COMMENT_FOLD_CHARS + 1))).toBe(true)
  })

  it('空内容不折叠', () => {
    expect(shouldFoldComment('')).toBe(false)
  })
})

describe('formatCommentTime', () => {
  it('epoch 毫秒转成可读格式', () => {
    expect(formatCommentTime(new Date(2026, 9, 10, 12, 34, 5).getTime())).toBe('2026-10-10 12:34:05')
  })

  it('认不出的原样返回，不显示 Invalid Date', () => {
    expect(formatCommentTime('待确认')).toBe('待确认')
  })
})

describe('validateComment —— 表单校验（FR-074 / FR-075）', () => {
  const ok = {
    descriptionScores: 5,
    benefitScores: 5,
    content: '很好用',
    picUrls: [] as string[],
  }

  it('合法表单通过', () => {
    expect(validateComment(ok)).toEqual({ ok: true, message: '' })
  })

  // ── 评分：后端不校验范围，这里必须拦 ──────────────────────────────
  it.each([0, 6, -1, 99])('商品质量评分 %i 越界被拒', (v) => {
    const r = validateComment({ ...ok, descriptionScores: v })
    expect(r.ok).toBe(false)
    expect(r.message).toContain('评分')
  })

  it.each([0, 6])('服务态度评分 %i 越界被拒', (v) => {
    expect(validateComment({ ...ok, benefitScores: v }).ok).toBe(false)
  })

  it('边界值 1 与 5 都通过', () => {
    expect(validateComment({ ...ok, descriptionScores: 1, benefitScores: 1 }).ok).toBe(true)
    expect(validateComment({ ...ok, descriptionScores: 5, benefitScores: 5 }).ok).toBe(true)
  })

  // ── 内容 ────────────────────────────────────────────────────────
  it('内容为空被拒', () => {
    const r = validateComment({ ...ok, content: '' })
    expect(r.ok).toBe(false)
    expect(r.message).toContain('评价内容')
  })

  it('内容只有空白也算空', () => {
    expect(validateComment({ ...ok, content: '   \n  ' }).ok).toBe(false)
  })

  it(`恰好 ${COMMENT_CONTENT_MAX} 字符通过`, () => {
    expect(validateComment({ ...ok, content: '好'.repeat(COMMENT_CONTENT_MAX) }).ok).toBe(true)
  })

  it(`超过 ${COMMENT_CONTENT_MAX} 字符被拒（与数据库列宽一致）`, () => {
    const r = validateComment({ ...ok, content: '好'.repeat(COMMENT_CONTENT_MAX + 1) })
    expect(r.ok).toBe(false)
    expect(r.message).toContain(`${COMMENT_CONTENT_MAX}`)
  })

  // ── 图片 ────────────────────────────────────────────────────────
  it(`最多 ${COMMENT_PIC_MAX} 张`, () => {
    const urls = (n: number) => Array.from({ length: n }, (_, i) => `u${i}.png`)
    expect(validateComment({ ...ok, picUrls: urls(COMMENT_PIC_MAX) }).ok).toBe(true)
    expect(validateComment({ ...ok, picUrls: urls(COMMENT_PIC_MAX + 1) }).ok).toBe(false)
  })

  it('图片不是必填', () => {
    expect(validateComment({ ...ok, picUrls: [] }).ok).toBe(true)
  })
})
