import { readFileSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

/**
 * 首页商品网格的**列数分档**，以及它与取数个数的互补关系。
 *
 * 为什么给纯 CSS 写测试：列数在 `design.css`、取几个在 `HomeView.vue`，两处靠
 * 「断点互补」（1101 / 1100）耦合。**只改一边就出错** ——
 *   · 取 6 个却排 4 列 → 第三张卡孤零零占一行；
 *   · 取 4 个却排 6 列 → 右侧空两格，看着像没加载完（首页最初那个缺陷的成因）。
 * 这类错位在浏览器里一眼能看出来，但代码评审时看不出来，所以用断言钉住。
 *
 * ⚠️ `design-consistency.spec.ts` 覆盖的是 16 个**新页面**的 scoped 样式，
 * 管不到 `design.css`，所以这个文件是必要的，不是重复。
 */

const css = readFileSync(join(process.cwd(), 'src', 'styles', 'design.css'), 'utf8')
const home = readFileSync(join(process.cwd(), 'src', 'views', 'HomeView.vue'), 'utf8')

/** 去掉 CSS 注释 —— 否则紧贴在规则前的那段注释会被当成选择器的一部分 */
const stripComments = (s: string) => s.replace(/\/\*[\s\S]*?\*\//g, '')

/** 取出某个 `@media (max-width: Npx)` 块的正文（这些块没有嵌套） */
function mediaBlock(maxWidth: number): string {
  const start = css.indexOf(`@media (max-width: ${maxWidth}px) {`)
  expect(start, `design.css 里找不到 @media (max-width: ${maxWidth}px)`).toBeGreaterThan(-1)
  const end = css.indexOf('\n}', start)
  expect(end, `@media (max-width: ${maxWidth}px) 块没有正常闭合`).toBeGreaterThan(start)
  return stripComments(css.slice(start, end))
}

/**
 * 块内**作用于 `.product-grid`** 的 `grid-template-columns` 值。
 *
 * 必须按选择器找，不能直接搜整个块 —— 这些块里挤了一条共享规则
 * （`.mall-grid, .grid-3, …`），改动必须落在 `.product-grid` 自己那条上，
 * 否则会把商城页和其他网格一起带跑。
 */
function productGridColumns(blockText: string): string | null {
  // ⚠️ 声明部分必须是 `[^{}]*` 而不是 `[^}]*`：块以 `@media (…) {` 开头，
  // 只排除 `}` 的话，贪婪匹配会**跨过媒体查询自己的 `{`**，把 `.product-grid`
  // 整个吞进上一条“规则”的声明里，于是 `.product-grid` 单独成条时永远匹配不到。
  for (const rule of blockText.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
    const selectors = rule[1]!.split(',').map((s) => s.trim())
    if (!selectors.includes('.product-grid')) continue
    const cols = /grid-template-columns:\s*([^;]+)/.exec(rule[2]!)
    return cols ? cols[1]!.trim() : null
  }
  return null
}

describe('首页商品网格 —— 列数分档', () => {
  it('基线（超宽屏）是 6 列，与「超宽屏取 6 个」对齐', () => {
    const m = /(?:^|\n)\.product-grid\s*\{\s*grid-template-columns:\s*([^;]+)/.exec(css)
    expect(m?.[1]?.trim()).toBe('repeat(6, 1fr)')
  })

  it('≤1600px 是 4 列', () => {
    // 门槛之所以不放在 1100px：6 列在 1440px 上每张卡只剩约 193px，
    // 比设计稿的 240px 卡片下限窄不少。抬到 1600 后 6 列最小约 220px。
    expect(productGridColumns(mediaBlock(1600))).toBe('repeat(4, 1fr)')
  })

  it('≤1100px 不再单独规定 .product-grid 的列数（已由 ≤1600px 那条覆盖）', () => {
    expect(productGridColumns(mediaBlock(1100))).toBeNull()
  })

  it('≤768px 收成 1 列（此时卡片改横版，两列会挤坏）', () => {
    expect(productGridColumns(mediaBlock(768))).toBe('1fr')
  })

  it('≤1600px 里 .product-grid 必须单独成条，不得并进共享规则', () => {
    // 并进去的话它会跟着共享规则变成 2 列 —— 取回 4 个就成了 2×2，一行只有 2 个
    const rule = [...mediaBlock(1600).matchAll(/([^{}]+)\{([^{}]*)\}/g)].find((m) =>
      (m[1] ?? '')
        .split(',')
        .map((s) => s.trim())
        .includes('.product-grid'),
    )
    expect(rule, '≤1600px 块里找不到 .product-grid 的规则').toBeDefined()
    expect(
      (rule![1] ?? '')
        .split(',')
        .map((s) => s.trim()),
    ).toEqual(['.product-grid'])
  })

  it('≤768px 的共享规则保持原样 —— 那里两者同为 1 列，共用是对的', () => {
    expect(mediaBlock(768)).toContain('.product-grid, .mall-grid')
  })

  it('取数断点是 CSS 断点的互补值：1601 / 1600', () => {
    expect(home).toContain("'(min-width: 1601px)'")
    expect(mediaBlock(1600)).toContain('@media (max-width: 1600px)')
  })
})
