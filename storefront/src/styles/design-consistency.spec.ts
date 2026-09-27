import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

import { describe, expect, it } from 'vitest'

/**
 * 新页面的**视觉一致性结构核对** —— design-new-pages.md §5 里能机械判定的那几条。
 *
 * §5 是给「与设计稿并排比对」用的，其中三条可以变成断言（也就不会随人眼疲劳而失效）：
 *   ① 颜色只许用 §1.1 表内色（`#FFFFFF` 作为中性底色不受限）
 *   ② 断点只许用 1100 / 768 / 480
 *   ③ 顶栏与页脚由 `DefaultLayout` 统一提供，新页面**不得自行渲染**
 *
 * **不能机械判定的部分（留人工核对）**：与设计稿的逐像素比对、字号区间、
 * 「是否出现第二套按钮/卡片视觉」。这些在 tasks.md 的 T138 里如实标注为人工项，
 * 不在这里假装已覆盖（宪法原则 V）。
 */

/** §1.1 的色值表，全部小写、6 位。`#FFFFFF` 作为中性底色不限用，但仍在表内 */
const PALETTE = new Set([
  '2e7cd6', // 品牌主色
  '35a5ff', // 点缀亮蓝
  '0c2148', // 深蓝底
  '0a1a38', // 最深蓝
  '16233f', // 正文主色
  '5b6c8f', // 次级文字
  '8a97b5', // 占位文字
  'b4c0d8', // 弱文字
  'eaf1ff', // 浅蓝背景
  'f0f4fb',
  'f4f7fd',
  'f5f8ff', // 卡片浅底
  'eef2fa',
  'e3e9f4', // 边框
  'ff5c22', // 强调橙
  'ffffff', // 白
])

/** §1.2：新增页面 MUST 沿用这三个断点，不得新增 */
const BREAKPOINTS = new Set([1100, 768, 480])

/** §5 点名的 17 条无设计稿路由，对应 16 个视图文件 */
const NEW_PAGE_VIEWS = [
  'CartView',
  'CheckoutView',
  'OrderListView',
  'OrderDetailView',
  'AccountView',
  'AddressView',
  'CouponCenterView',
  'MyCouponView',
  'AgreementView',
  'EnterpriseView',
  'TemplateCenterView',
  'ChangelogView',
  'AboutView',
  'NewsView',
  'ContactView',
  'JobsView',
]

const VIEWS_DIR = join(process.cwd(), 'src', 'views')
const read = (view: string) => readFileSync(join(VIEWS_DIR, `${view}.vue`), 'utf8')

/** 只取 `<style scoped>` 里的内容 —— `<script>` 里的颜色字符串不算样式 */
function styles(src: string): string {
  return src.slice(src.indexOf('<style'))
}

/** `#AbC` → `aabbcc`（去 `#`、补全 3 位写法），便于与表统一比较 */
function expand(hex: string): string {
  const h = hex.replace('#', '').toLowerCase()
  return h.length === 3 ? `${h[0]}${h[0]}${h[1]}${h[1]}${h[2]}${h[2]}` : h
}

describe('新页面视觉一致性 —— 颜色（§5 第 1 条 / §1.1）', () => {
  it.each(NEW_PAGE_VIEWS)('%s 的样式里只出现 §1.1 表内色', (view) => {
    const css = styles(read(view))
    const used = [...css.matchAll(/#[0-9a-fA-F]{3,8}\b/g)].map((m) => expand(m[0]))
    const offPalette = [...new Set(used)].filter((c) => !PALETTE.has(c))
    expect(offPalette, `${view} 用了表外色：${offPalette.join(', ')}`).toEqual([])
  })
})

describe('新页面视觉一致性 —— 断点（§5 第 4 条 / §1.2）', () => {
  it.each(NEW_PAGE_VIEWS)('%s 只用 1100 / 768 / 480 三个断点', (view) => {
    const css = styles(read(view))
    // 只取 `@media (...)` 里的 —— 普通 CSS 属性 `max-width: 760px`（正文栏宽）不是断点
    const used = [...css.matchAll(/@media[^{]*max-width:\s*(\d+)px/g)].map((m) => Number(m[1]))
    const extra = [...new Set(used)].filter((n) => !BREAKPOINTS.has(n))
    expect(extra, `${view} 用了表外断点：${extra.join(', ')}`).toEqual([])
  })
})

describe('新页面视觉一致性 —— 不自带顶栏与页脚（§5 第 6 条 / FR-042）', () => {
  it.each(NEW_PAGE_VIEWS)('%s 不渲染 Header / Footer', (view) => {
    const src = read(view)
    expect(src).not.toMatch(/SiteHeader|SiteFooter/)
    expect(src).not.toMatch(/class="header"/)
    expect(src).not.toMatch(/class="footer"/)
    expect(src).not.toMatch(/<footer/)
  })

  it('17 条无设计稿路由都落在 DefaultLayout 之下（顶栏唯一且状态同源）', async () => {
    const { default: realRouter } = await import('@/router')
    const options = realRouter as unknown as {
      options: { routes: Array<{ path: string; component?: unknown; children?: unknown[] }> }
    }
    // 顶层只应有一个承载外壳的父路由（path 为 '/'），其余页面都是它的 children
    const layoutRoutes = options.options.routes.filter((r) => r.children?.length)
    expect(layoutRoutes).toHaveLength(1)
    expect(layoutRoutes[0]?.path).toBe('/')
  })
})

// 这里原本有一条「`design.css` 必须与 `www/css/style.css` 字节相同」的断言。
// 2026-09-27 随规格修订**一并删除**：设计稿基线（FR-046 / SC-012）已放开，
// `www/` 目录也已删除，`design.css` 现在是本项目自己的样式基线，可以演进。
// 见 docs: 宪法 2.1.0 与 spec.md 的 FR-046 / SC-012 改动说明。

describe('新页面视觉一致性 —— 不留写死内容（FR-043）', () => {
  it('全站不再出现设计稿写死的「购物车 (2)」', () => {
    const files: string[] = []
    const walk = (dir: string) => {
      for (const name of readdirSync(dir)) {
        const full = join(dir, name)
        if (statSync(full).isDirectory()) walk(full)
        else if (full.endsWith('.vue')) files.push(full)
      }
    }
    walk(VIEWS_DIR)
    walk(join(process.cwd(), 'src', 'components'))

    // 先剥掉注释：注释里**提到**这个字符串是正常的（例如 SiteHeader 说明它已被移除），
    // 要抓的是它出现在**会被渲染的代码**里
    const stripComments = (src: string) =>
      src
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .split('\n')
        .filter((l) => !l.trim().startsWith('//'))
        .join('\n')

    const hits = files.filter((f) => stripComments(readFileSync(f, 'utf8')).includes('购物车 (2)'))
    expect(hits).toEqual([])
  })
})
