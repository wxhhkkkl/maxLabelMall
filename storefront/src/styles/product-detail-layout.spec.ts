import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * 详情页的**宽度耦合**：宽栏区块（商品详情 / 用户评价）的左边缘必须与**标题列**对齐。
 *
 * 桌面端 `.pd-main` 是「图片 500px + 间隔 44px + 信息列」的两栏布局，标题在
 * `页面边距 + 500 + 44` 处；而 `.params` 若只退到页面边距，就会和标题错开、
 * 看起来"挂"在最左边（2026-10-10 所有者报的就是这个）。
 *
 * ⚠️ **为什么用样式文本断言而不是渲染断言**：jsdom 没有排版引擎，量不出元素坐标，
 * "对齐了没有"根本没法在单测里跑。但这件事背后有一个**可测的不变量** ——
 * 三个规则里的数字是**互相算出来的**：改了 `.gallery` 的宽度却不改 `.params`，
 * 错位就会悄悄回来。所以这里钉的是这个不变量（与 `design-consistency.spec.ts`
 * 读样式文本做断言是同一套做法）。
 *
 * 真机观感仍只能人工看，见 quickstart §1。
 */
const CSS = readFileSync(join(process.cwd(), 'src', 'styles', 'design.css'), 'utf8')

/**
 * 取某选择器**主干**规则（不含缩进的媒体查询副本）的内容。
 *
 * 用"行首紧贴选择器"来区分：design.css 里媒体查询内的规则都缩进了两格，
 * 所以 `\n.xxx {` 只会命中主干那条。
 */
function block(selector: string): string {
  const escaped = selector.replace(/\./g, '\\.')
  const m = CSS.match(new RegExp(`(?:^|\\n)${escaped}\\s*\\{([^}]*)\\}`))
  if (!m) throw new Error(`design.css 里找不到 ${selector} 的主干规则`)
  return m![1]!
}

/** 从声明块里抠出某个属性的值 */
function value(block: string, prop: string): string {
  const m = block.match(new RegExp(`(?:^|;)\\s*${prop}\\s*:([^;]+)`))
  if (!m) throw new Error(`找不到属性 ${prop}，块内容：${block}`)
  return m![1]!.trim()
}

/** 把 px 数值抠出来 */
function px(text: string): number {
  const m = text.match(/(-?\d+(?:\.\d+)?)px/)
  if (!m) throw new Error(`不是 px 值：${text}`)
  return Number(m![1])
}

describe('详情页：宽栏区块的左边缘与标题列对齐', () => {
  const galleryWidth = px(value(block('.gallery'), 'width'))
  const mainPadding = value(block('.pd-main'), 'padding')
  const gap = px(value(block('.pd-main'), 'gap'))

  it('前置：图片列与栏间距是已知的两个数（`.params` 的算法依赖它们）', () => {
    expect(galleryWidth).toBeGreaterThan(0)
    expect(gap).toBeGreaterThan(0)
  })

  it('`.params` 的左内边距 = 页面边距 + 图片列宽 + 栏间距', () => {
    const params = block('.params')
    // 左内边距是个 calc：`0 <上> <右> calc(...)`
    const calc = /calc\(([^)]*)\)/.exec(value(params, 'padding'))
    expect(calc, '`.params` 的左内边距必须写成 calc，把推导显式写出来').not.toBeNull()

    const numbers = (calc![1]!.match(/\d+(?:\.\d+)?/g) ?? []).map(Number)

    // `.pd-main` 的 padding 简写：`36px 60px 48px` → 左右都是第二个值
    const mainNumbers = (mainPadding.match(/\d+(?:\.\d+)?/g) ?? []).map(Number)
    const pagePadding = mainNumbers[1]!

    // 顺序与注释一致：页面边距 + 图片列 + 栏间距
    expect(numbers).toEqual([pagePadding, galleryWidth, gap])
  })

  it('**≤1100px 时退回页面边距** —— 那时两栏已变纵向，标题也回到左边，604px 的对齐不再成立', () => {
    const narrow = CSS.slice(CSS.indexOf('@media (max-width: 1100px)'))
    expect(narrow).toMatch(/\.params\s*\{[^}]*padding:\s*0\s+\d+px\s+\d+px/)
    // 且这一条里**不能**再出现 calc —— 纵向布局下它会把内容推到屏幕外
    const rule = /\.params\s*\{([^}]*)\}/.exec(narrow)
    expect(rule![1]).not.toContain('calc(')
  })
})
