import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { Banner } from '@/types'

import MallBanner from './MallBanner.vue'

/**
 * 商城页顶部横幅（FR-080~083）—— **组合式**：后台配的图 + 后台配的文案，前端负责排版。
 *
 * 为什么不是"上传一张拍平的图"：设计稿的横幅是一张产品图 + 一层渐变 + 几段排版文字。
 * 拍平成一张 PNG 后，窄屏要么被裁掉左边的字、要么缩得很小。组合式则怎么缩放都不丢内容。
 *
 * 文案的来源见 `utils/banner.ts` 的表格（`标题` 分行、`描述` 拆副标题 / 胶囊）。
 *
 * ⚠️ 组件**不发请求** —— 数据由 `MallView` 拉好传进来，这样"拉失败"由页面决定怎么降级。
 */
function banner(over: Partial<Banner> = {}): Banner {
  return {
    id: 1,
    title: '从设计到打印\n每一步，都有好搭档',
    memo: '标签软件 · 打印设备 · 标签耗材\nMaxLabel 软件开发中',
    picUrl: 'http://x/product.webp',
    url: 'http://x/act',
    ...over,
  }
}

function mountBanner(banners: Banner[]) {
  return mount(MallBanner, { props: { banners } })
}

describe('MallBanner —— 三态（0 条 / 1 条 / 多条）', () => {
  it('**0 条时整块不渲染**（不留空白占位，商品列表从顶部正常开始）', () => {
    expect(mountBanner([]).find('.mall-banner-wrap').exists()).toBe(false)
  })

  it('1 条时渲染，且**没有任何轮播控件**', () => {
    const w = mountBanner([banner()])
    expect(w.find('.mall-banner-wrap').exists()).toBe(true)
    expect(w.find('.car-dot').exists()).toBe(false)
    expect(w.find('.car-arrow').exists()).toBe(false)
  })

  it('多条时走轮播，圆点数量与横幅数一致', () => {
    const w = mountBanner([banner(), banner({ id: 2, title: '第二张' })])
    expect(w.findAll('.car-dot')).toHaveLength(2)
  })
})

describe('MallBanner —— 文案排版（后台配文，前端排版）', () => {
  it('主标题按换行分成多行渲染', () => {
    const w = mountBanner([banner()])
    const lines = w.findAll('.mbn-title-line').map((n) => n.text())
    expect(lines).toEqual(['从设计到打印', '每一步，都有好搭档'])
  })

  it('副标题来自「描述」第一行', () => {
    expect(mountBanner([banner()]).get('.mbn-subtitle').text()).toBe('标签软件 · 打印设备 · 标签耗材')
  })

  it('胶囊来自「描述」第二行', () => {
    expect(mountBanner([banner()]).get('.mbn-badge').text()).toBe('MaxLabel 软件开发中')
  })

  it('**「描述」只有一行时不渲染胶囊**', () => {
    const w = mountBanner([banner({ memo: '只有副标题' })])
    expect(w.get('.mbn-subtitle').text()).toBe('只有副标题')
    expect(w.find('.mbn-badge').exists()).toBe(false)
  })

  it('**没有「描述」时副标题与胶囊都不渲染**（不留空壳）', () => {
    const w = mountBanner([banner({ memo: undefined })])
    expect(w.find('.mbn-subtitle').exists()).toBe(false)
    expect(w.find('.mbn-badge').exists()).toBe(false)
    // 标题还在
    expect(w.findAll('.mbn-title-line')).toHaveLength(2)
  })

  it('品牌行是前端固定的（站点自己的名字，页头页脚本来就写着）', () => {
    expect(mountBanner([banner()]).get('.mbn-brand').text()).toContain('MaxLabel')
  })

  it('图片用 title 当 alt（可访问性，也便于排查是哪张图挂了）', () => {
    expect(mountBanner([banner()]).get('.mbn-photo').attributes('alt')).toContain('从设计到打印')
  })
})

describe('MallBanner —— 跳转（FR-083）', () => {
  it('配了 url → 整块是链接，且新开标签页（不把用户带离本站）', () => {
    const a = mountBanner([banner({ url: 'http://x/act' })]).get('.mbn-link')
    expect(a.attributes('href')).toBe('http://x/act')
    expect(a.attributes('target')).toBe('_blank')
    expect(a.attributes('rel')).toContain('noopener')
  })

  it('**没配 url → 不是链接**（点了不跳转、也不报错），但内容照常渲染', () => {
    const w = mountBanner([banner({ url: undefined })])
    expect(w.find('.mbn-link').exists()).toBe(false)
    expect(w.find('.mbn-photo').exists()).toBe(true)
    expect(w.findAll('.mbn-title-line')).toHaveLength(2)
  })

  it('多条时逐张判 url（一条有、一条没有）', () => {
    const w = mountBanner([banner({ url: 'http://x/a' }), banner({ id: 2, url: undefined })])
    expect(w.findAll('.mbn-link')).toHaveLength(1)
  })
})

/**
 * 图片的尺寸契约。
 *
 * 所有者定的口径是**高度固定 220px**，同时**不许裁、不许拉伸** ——
 * 三条同时满足只有 `object-fit: contain`（图片完整放进盒子、保持自身比例，装不满处留白）。
 *
 * ⚠️ **不要写回 `cover`**：`cover` 是"放大到填满、溢出裁掉"，窄屏会切掉图片左右。
 * 组合式横幅把这个风险降到了最低（文字在 HTML 里、图只是配景），但「不许裁」这条依然有效。
 *
 * jsdom 没有排版引擎，测不了"到底裁没裁"，所以钉的是**样式契约**本身 ——
 * 与 `styles/design-consistency.spec.ts` 读样式文本断言是同一套做法。
 */
describe('MallBanner —— 尺寸契约', () => {
  /** 取某个组件样式块并**剥掉注释** —— 注释里会引用反例，不该把断言绊倒 */
  async function styleOf(file: string): Promise<string> {
    const { readFileSync } = await import('node:fs')
    const { join } = await import('node:path')
    const src = readFileSync(join(process.cwd(), 'src', 'components', file), 'utf8')
    return src.slice(src.indexOf('<style')).replace(/\/\*[\s\S]*?\*\//g, '')
  }

  it('**容器高度固定 220px** —— 要压过 design.css 在窄屏把它设成 auto 的那条规则', async () => {
    const style = await styleOf('MallBanner.vue')
    expect(style).toMatch(/\.mall-banner[^}]*height:\s*220px/)
  })

  it('**产品图用 contain 而不是 cover** —— cover 会裁掉图', async () => {
    const style = await styleOf('BannerSlide.vue')
    expect(style).toMatch(/object-fit:\s*contain/)
    expect(style).not.toMatch(/object-fit:\s*cover/)
  })
})
