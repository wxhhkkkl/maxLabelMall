import { describe, expect, it } from 'vitest'

import { sanitizeRichText } from './sanitize'

/**
 * 富文本安全过滤（FR-005c / SC-020）。
 *
 * 商品详情富文本由运营在后台富文本编辑器里撰写，最终以 HTML 存储并由前台渲染。
 * **直接 `v-html` 注入等于把 XSS 面交给运营账号**：账号被盗或后台被越权写入，
 * 攻击脚本就会在所有访客的浏览器里执行。
 *
 * 本文件是这条安全契约的可执行断言。计划里此前**没有指定任何过滤机制**
 * （多份规划产物中 `sanitize` / `DOMPurify` 零命中），这里补上并锁定行为。
 */
describe('sanitizeRichText —— 保留排版、剔除可执行内容', () => {
  it('保留常规排版标签', () => {
    const html = '<p>三防热敏纸 <strong>40×30mm</strong></p><ul><li>防水</li></ul>'
    const out = sanitizeRichText(html)
    expect(out).toContain('<p>')
    expect(out).toContain('<strong>')
    expect(out).toContain('<ul>')
    expect(out).toContain('<li>')
    expect(out).toContain('三防热敏纸')
  })

  it('保留图片与表格（详情页常用）', () => {
    const out = sanitizeRichText('<img src="http://x/a.png"><table><tr><td>30dpi</td></tr></table>')
    expect(out).toContain('<img')
    expect(out).toContain('<table')
  })

  // ── 以下是必须剔除的 ─────────────────────────────────────────────
  it('剔除 <script>（连同其内容）', () => {
    const out = sanitizeRichText('<p>正常</p><script>alert(1)</script>')
    expect(out).toContain('正常')
    expect(out).not.toContain('<script')
    expect(out).not.toContain('alert(1)')
  })

  it('剔除事件属性（onerror / onclick / onload …）', () => {
    const out = sanitizeRichText('<img src=x onerror="alert(1)"><p onclick="alert(2)">点我</p>')
    expect(out).not.toContain('onerror')
    expect(out).not.toContain('onclick')
    expect(out).not.toContain('alert')
    // 内容本身保留
    expect(out).toContain('点我')
  })

  it('剔除 javascript: 与 vbscript: 协议', () => {
    const a = sanitizeRichText('<a href="javascript:alert(1)">链接</a>')
    expect(a).not.toContain('javascript:')
    expect(a).toContain('链接')

    const b = sanitizeRichText('<a href="vbscript:msgbox(1)">x</a>')
    expect(b).not.toContain('vbscript:')
  })

  it('剔除 <iframe> / <object> / <embed> 等可嵌入执行内容的标签', () => {
    for (const bad of ['<iframe src="http://evil"></iframe>', '<object data="x"></object>', '<embed src="x">']) {
      const out = sanitizeRichText(bad)
      expect(out).not.toMatch(/<iframe|<object|<embed/)
    }
  })

  it('剔除 <style> 与 style 属性（可用于视觉钓鱼/覆盖）', () => {
    const out = sanitizeRichText('<style>body{display:none}</style><p style="position:fixed">x</p>')
    expect(out).not.toContain('<style')
    expect(out).not.toContain('position:fixed')
  })

  it('剔除 svg 内嵌脚本（<svg><script>）', () => {
    const out = sanitizeRichText('<svg><script>alert(1)</script></svg>')
    expect(out).not.toContain('<script')
    expect(out).not.toContain('alert')
  })

  it('大小写混合也拦得住：<ScRiPt> 与 OnErRoR', () => {
    const out = sanitizeRichText('<ScRiPt>alert(1)</ScRiPt><img src=x OnErRoR="alert(2)">')
    expect(out.toLowerCase()).not.toContain('<script')
    expect(out.toLowerCase()).not.toContain('onerror')
  })

  it('空输入返回空串，不抛错', () => {
    expect(sanitizeRichText('')).toBe('')
    expect(sanitizeRichText(undefined as unknown as string)).toBe('')
    expect(sanitizeRichText(null as unknown as string)).toBe('')
  })

  it('纯文本原样保留', () => {
    expect(sanitizeRichText('就是一段纯文本')).toBe('就是一段纯文本')
  })
})
