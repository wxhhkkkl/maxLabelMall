import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * 详情页图片列**跟随滚动**（2026-11-24 所有者要求）。
 *
 * 规格多的商品右列很长，选规格时要往下滚；图片列若跟着整页滚走，客户就看不到
 * 当前规格的示意图了。做法是让 `.gallery` 在 `.pd-main` 内 `position: sticky`。
 *
 * ⚠️ 与 `product-detail-layout.spec.ts` 同理：jsdom 没有排版引擎，粘住与否测不出来，
 * 这里钉的是**样式文本里的不变量**（顶部偏移要避开吸顶页头、窄屏单列时必须取消）。
 */
const CSS = readFileSync(join(process.cwd(), 'src', 'styles', 'product-detail.css'), 'utf8')

function block(selector: string): string {
  const escaped = selector.replace(/\./g, '\\.')
  const m = CSS.match(new RegExp(`(?:^|\\n)${escaped}\\s*\\{([^}]*)\\}`))
  if (!m) throw new Error(`product-detail.css 里找不到 ${selector} 的主干规则`)
  return m![1]!
}

describe('详情页：图片列跟随滚动', () => {
  it('`.gallery` 在桌面端粘住，且顶部偏移避开 76px 的吸顶页头', () => {
    const gallery = block('.product-page .gallery')
    expect(gallery).toMatch(/position:\s*sticky/)
    expect(gallery).toMatch(/align-self:\s*start/)

    const top = /(?:^|;)\s*top\s*:\s*(\d+(?:\.\d+)?)px/.exec(gallery)
    expect(top, '`.gallery` 必须给 sticky 一个 px 顶部偏移').not.toBeNull()
    expect(Number(top![1])).toBeGreaterThanOrEqual(76)
  })

  it('≤768px 单列布局时取消粘住 —— 那时图片在上、信息在下，粘住只会挡住内容', () => {
    const narrow = CSS.slice(CSS.indexOf('@media (max-width: 768px)'))
    const rule = /\.product-page \.gallery\s*\{([^}]*)\}/.exec(narrow)
    expect(rule, '768px 媒体查询里必须有 `.product-page .gallery` 的覆盖').not.toBeNull()
    expect(rule![1]).toMatch(/position:\s*static/)
  })
})
