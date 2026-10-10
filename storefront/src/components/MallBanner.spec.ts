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
 * 横幅图片的尺寸契约（缺陷修复，2026-10-10）。
 *
 * ⚠️ **绝对不要给横幅图片钉死高度**。第一版写的是 `height: 220px; object-fit: cover`
 * —— `cover` 的意思是"等比放大到填满容器、溢出的裁掉"，于是：
 *   · 窄屏时左右被切掉 → **图片最左边的文字看不到**；
 *   · 源图比容器小时被放大 → **模糊**（运营传了张 logo 就特别明显）。
 *
 * 正确做法是 `width: 100%; height: auto`：高度由**图片自己的比例**决定，
 * 既不会裁、也不会为了填满而拉伸。
 *
 * jsdom 没有排版引擎，测不了"到底裁没裁"；所以这里钉的是**样式契约**本身 ——
 * 与 `styles/design-consistency.spec.ts` 读样式文本做断言是同一套做法。
 */
describe('MallBanner —— 图片尺寸不得写死', () => {
  it('`.mbn-img` 的 height 必须是 auto，不能是固定像素', async () => {
    const { readFileSync } = await import('node:fs')
    const { join } = await import('node:path')
    const src = readFileSync(join(process.cwd(), 'src', 'components', 'MallBanner.vue'), 'utf8')
    const style = src.slice(src.indexOf('<style'))

    // `height: 220px` / `height: 150px` 都会命中；`height: auto` 不会
    expect(style).not.toMatch(/\.mbn-img[^}]*height:\s*\d+px/)
    // 反面断言：确实声明了 height: auto（防止把整条规则删掉也算"通过"）
    expect(style).toMatch(/\.mbn-img[^}]*height:\s*auto/)
  })
})
