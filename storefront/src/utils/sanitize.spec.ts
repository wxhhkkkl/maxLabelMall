import { describe, expect, it } from 'vitest'

import { hasVisibleContent, sanitizeRichText } from './sanitize'

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

/**
 * 「有没有可见内容」的判定（FR-064 / FR-065）。
 *
 * ⚠️ **这里是本次修复的核心**。原判定是"剥掉所有标签后看还剩不剩**文字**"，
 * 于是**纯图片的详情被判成空、整块不显示** —— 而纯图片详情在电商里是常态，
 * 存量数据里就有好几件。新判据把图片与文字**同等对待**。
 *
 * 判据故意收得紧：空壳标签（`<p></p>`、`<br>`）**不算**有内容。放宽它们会让
 * "详情区块渲染出来了但什么也看不见"——那是另一种空白块，同样要防。
 */
describe('hasVisibleContent —— 图片与文字同等计为"有内容"', () => {
  it('**纯图片算有内容**（本次修复的那一类）', () => {
    expect(hasVisibleContent('<img src="http://x/a.png">')).toBe(true)
    expect(hasVisibleContent('<p><img src="http://x/a.png"></p>')).toBe(true)
  })

  it('表格里嵌的图片也算（不能只看直接子节点）', () => {
    expect(hasVisibleContent('<table><tr><td><img src="x.png"></td></tr></table>')).toBe(true)
  })

  it('纯文本算有内容', () => {
    expect(hasVisibleContent('就是一段纯文本')).toBe(true)
    expect(hasVisibleContent('<p>三防热敏纸</p>')).toBe(true)
  })

  it('图文混排算有内容', () => {
    expect(hasVisibleContent('<img src="a.png"><p>说明文字</p>')).toBe(true)
  })

  it('**空壳标签不算**有内容 —— 否则会渲染出一个什么都看不见的空白块', () => {
    expect(hasVisibleContent('<p></p>')).toBe(false)
    expect(hasVisibleContent('<div></div><div></div>')).toBe(false)
    expect(hasVisibleContent('<br>')).toBe(false)
    expect(hasVisibleContent('<p><br></p><div><span>   </span></div>')).toBe(false)
  })

  it('空串 / 空白串不算有内容', () => {
    expect(hasVisibleContent('')).toBe(false)
    expect(hasVisibleContent('   \n  ')).toBe(false)
  })

  it('清洗后什么都不剩（例如只有 script）不算有内容', () => {
    expect(hasVisibleContent(sanitizeRichText('<script>alert(1)</script>'))).toBe(false)
  })

  it('只有表格文字也算有内容', () => {
    expect(hasVisibleContent('<table><tr><td>30dpi</td></tr></table>')).toBe(true)
  })
})
