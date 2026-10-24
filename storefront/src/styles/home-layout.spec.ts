import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

/**
 * 首页每一楼的**内容宽度上限**（2026-10-24 所有者反馈：大屏上各楼铺满整屏、
 * 与已居中的 Hero / 行业区对不齐）。
 *
 * 这件事被两处代码共同决定，任何一处漏掉都会让某一楼重新铺满整屏：
 *   · `HomeView.vue` 的楼层有没有 `home-floor` 类；
 *   · `design.css` 的 `.home-floor` 规则是否在**各楼自己的 padding 简写之后**
 *     （同优先级下靠书写顺序取胜，放前面会被 `padding: 80px 100px` 盖掉）。
 *
 * 上限 1600px 与商品区 6 列相互制约（见 `product-grid.spec.ts`）：6 列要保住
 * 设计稿 240px 的卡片下限，内容至少 1600px。
 */

const css = readFileSync(join(process.cwd(), 'src', 'styles', 'design.css'), 'utf8')
const home = readFileSync(join(process.cwd(), 'src', 'views', 'HomeView.vue'), 'utf8')
const hero = readFileSync(join(process.cwd(), 'src', 'components', 'home', 'HomeHero.vue'), 'utf8')
const industry = readFileSync(join(process.cwd(), 'src', 'components', 'home', 'HomeIndustry.vue'), 'utf8')

/** 去掉 CSS 注释，避免注释里的示例被当成规则 */
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, '')

describe('首页楼层 —— 内容宽度上限', () => {
  it('每一楼都带 home-floor（新增楼层漏了就会重新铺满整屏）', () => {
    const sections = [...home.matchAll(/<section class="([^"]+)"/g)].map((m) => m[1]!)

    expect(sections).toHaveLength(6)
    for (const classes of sections) {
      expect(classes.split(/\s+/)).toContain('home-floor')
    }
  })

  it('.home-floor 收窄到 1600px 居中，窄屏两侧仍留 48px', () => {
    const rule = /(?:^|\n)\.home-floor\s*\{\s*([^}]+)\}/.exec(stripComments(css))
    expect(rule?.[1]).toContain('max(48px, calc((100% - 1600px) / 2))')
  })

  it('.home-floor 写在各楼 padding 简写之后（否则会被覆盖）', () => {
    const ruleAt = stripComments(css).indexOf('.home-floor {')
    expect(ruleAt).toBeGreaterThan(-1)
    for (const selector of ['.mall {', '.features {', '.steps {', '.trust {', '.support-home {', '.final-cta {']) {
      expect(stripComments(css).indexOf(selector)).toBeLessThan(ruleAt)
    }
  })

  it('Hero 与行业区用同一个 1600px 上限', () => {
    const width = 'width: min(1600px, calc(100% - 96px));'
    expect(hero).toContain(`.home-banner-container { display: flex; align-items: center; min-height: 570px; ${width}`)
    expect(industry).toContain(`.home-industry-container { ${width}`)
  })
})
