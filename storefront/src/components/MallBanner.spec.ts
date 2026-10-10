import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { Banner } from '@/types'

import MallBanner from './MallBanner.vue'

/**
 * 商城页顶部横幅（FR-080~083）。
 *
 * ⚠️ 组件**不发请求** —— 数据由 `MallView` 拉好传进来，这样"拉失败"由页面决定怎么降级，
 * 组件只管渲染三态（0 条 / 1 条 / 多条），也才好测。
 *
 * 三条容易写错的：
 * - **0 条必须整块不渲染** —— 渲染一个空容器会把商品列表顶下去、留一片空白；
 * - **1 条不得出现轮播控件** —— 一张图配圆点与箭头是无意义的交互噪音；
 * - **没有 url 时不得跳转** —— 后端该字段可为空，套个 `<a href="">` 会导致点一下刷页。
 */
function banner(over: Partial<Banner> = {}): Banner {
  return { id: 1, title: '标签耗材满减', picUrl: 'http://x/a.png', url: 'http://x/act', ...over }
}

function mountBanner(banners: Banner[]) {
  return mount(MallBanner, { props: { banners } })
}

describe('MallBanner —— 三态', () => {
  it('**0 条时整块不渲染**（不留空白占位，商品列表从顶部正常开始）', () => {
    const w = mountBanner([])
    expect(w.find('.mall-banner-wrap').exists()).toBe(false)
  })

  it('1 条时渲染图片，且**没有任何轮播控件**', () => {
    const w = mountBanner([banner()])
    expect(w.find('.mall-banner-wrap').exists()).toBe(true)
    expect(w.get('.mbn-img').attributes('src')).toBe('http://x/a.png')
    expect(w.find('.car-dot').exists()).toBe(false)
    expect(w.find('.car-arrow').exists()).toBe(false)
  })

  it('多条时走轮播，圆点数量与横幅数一致', () => {
    const w = mountBanner([banner(), banner({ id: 2, title: '第二张' })])
    expect(w.findAll('.car-dot')).toHaveLength(2)
    expect(w.findAll('.mbn-img')).toHaveLength(2)
  })

  it('图片的 alt 用后端给的 title（可访问性，也便于排查是哪张图挂了）', () => {
    const w = mountBanner([banner()])
    expect(w.get('.mbn-img').attributes('alt')).toBe('标签耗材满减')
  })

  it('多条时每张都渲染出来', () => {
    const w = mountBanner([banner(), banner({ id: 2, picUrl: 'http://x/b.png' })])
    const srcs = w.findAll('.mbn-img').map((i) => i.attributes('src'))
    expect(srcs).toEqual(['http://x/a.png', 'http://x/b.png'])
  })
})

describe('MallBanner —— 跳转（FR-083）', () => {
  it('配了 url → 是链接，且新开标签页（不把用户带离本站）', () => {
    const w = mountBanner([banner({ url: 'http://x/act' })])
    const a = w.get('.mbn-link')
    expect(a.attributes('href')).toBe('http://x/act')
    expect(a.attributes('target')).toBe('_blank')
    expect(a.attributes('rel')).toContain('noopener')
  })

  it('**没配 url → 不是链接**（点了不跳转、也不报错）', () => {
    const w = mountBanner([banner({ url: undefined })])
    expect(w.find('.mbn-link').exists()).toBe(false)
    // 图还在，只是不可点
    expect(w.find('.mbn-img').exists()).toBe(true)
  })

  it('多条时逐张判 url（一条有、一条没有）', () => {
    const w = mountBanner([banner({ url: 'http://x/a' }), banner({ id: 2, url: undefined })])
    expect(w.findAll('.mbn-link')).toHaveLength(1)
  })
})

/**
 * 横幅图片的尺寸契约（2026-10-10 一轮修两次，这里把结论钉死）。
 *
 * 所有者定的口径是**高度固定 220px**，同时**不许裁、不许拉伸**。
 * 三条同时满足只有一种做法：**`object-fit: contain`** —— 图片完整放进盒子里、
 * 保持自身比例，装不满的地方留白。
 *
 * ⚠️ **不要写回 `cover`**：`cover` 是"等比放大到填满、溢出部分裁掉"，后果两条 ——
 *   · 窄屏时左右被切 → **图片最左边的文字看不到**；
 *   · 源图比容器小时被放大到填满 → **模糊**。
 * 第一版就是 `cover`，被所有者当场发现了。
 *
 * jsdom 没有排版引擎，测不了"到底裁没裁"；所以这里钉的是**样式契约**本身 ——
 * 与 `styles/design-consistency.spec.ts` 读样式文本做断言是同一套做法。
 */
describe('MallBanner —— 图片尺寸契约', () => {
  /**
   * 取样式块，**并剥掉 `/* … *\/` 注释**。
   *
   * 注释里会引用反例（"不要写回 `object-fit: cover`"），不剥掉的话反面断言会被
   * 自己的散文绊倒 —— 断言的对象应该是**真实生效的 CSS**，不是解释文字。
   */
  async function styleBlock(): Promise<string> {
    const { readFileSync } = await import('node:fs')
    const { join } = await import('node:path')
    const src = readFileSync(join(process.cwd(), 'src', 'components', 'MallBanner.vue'), 'utf8')
    return src.slice(src.indexOf('<style')).replace(/\/\*[\s\S]*?\*\//g, '')
  }

  it('**用 contain 而不是 cover** —— cover 会裁掉图片左右（最左的文字就没了）', async () => {
    const style = await styleBlock()
    expect(style).toMatch(/object-fit:\s*contain/)
    // 反面断言：绝不能出现 cover（"删掉整条规则"不该算通过）
    expect(style).not.toMatch(/object-fit:\s*cover/)
  })

  it('高度是固定的 220px（所有者定的口径，不是 auto）', async () => {
    const style = await styleBlock()
    expect(style).toMatch(/\.mbn-img[^}]*height:\s*220px/)
    expect(style).not.toMatch(/\.mbn-img[^}]*height:\s*auto/)
  })
})
