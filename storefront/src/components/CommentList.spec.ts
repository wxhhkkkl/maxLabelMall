import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { ProductComment } from '@/types'

import CommentList from './CommentList.vue'

/**
 * 评价列表 —— 详情页与「全部评价」页共用（FR-067 / FR-068 / FR-069 / FR-071 / FR-072）。
 *
 * 两处最容易写错的地方，都在这里钉死：
 *
 * 1. **时间必须过 `formatDateTime`**。后端给的是 epoch 毫秒数，直接插值会把
 *    `1790486340000` 原样显示给用户 —— 本项目真出过这个缺陷，且当时单测是绿的，
 *    因为 fixture 谎报了线上格式。所以这里**故意用数字**做 fixture。
 * 2. **商家回复要显示在同一条评价之内**，不能渲染成并列的另一条（FR-068）。
 */
function comment(over: Partial<ProductComment> = {}): ProductComment {
  return {
    id: 1,
    userNickname: '张三',
    scores: 5,
    content: '很好用',
    createTime: new Date(2026, 9, 10, 12, 34, 5).getTime(),
    ...over,
  }
}

function mountList(props: Record<string, unknown> = {}) {
  return mount(CommentList, { props: { comments: [comment()], ...props } })
}

describe('CommentList —— 渲染', () => {
  it('展示昵称、评分、内容与时间', () => {
    const w = mountList()
    expect(w.text()).toContain('张三')
    expect(w.text()).toContain('很好用')
    expect(w.findAll('.ml-star.is-on')).toHaveLength(5)
  })

  it('**时间渲染成可读格式，不是那串毫秒数**', () => {
    const w = mountList()
    expect(w.text()).toContain('2026-10-10 12:34:05')
    expect(w.text()).not.toContain('1790486340000')
  })

  it('有图片时展示图片', () => {
    const w = mountList({ comments: [comment({ picUrls: ['http://x/a.png'] })] })
    const imgs = w.findAll('.cm-pics img')
    expect(imgs).toHaveLength(1)
    expect(imgs[0]?.attributes('src')).toBe('http://x/a.png')
  })

  it('**商家回复显示在同一条评价之内**，不是并列的另一条', () => {
    const w = mountList({ comments: [comment({ replyContent: '感谢支持' })] })
    expect(w.findAll('.cm-item')).toHaveLength(1)
    expect(w.get('.cm-item').text()).toContain('感谢支持')
  })

  it('没有商家回复时不渲染回复块', () => {
    const w = mountList()
    expect(w.find('.cm-reply').exists()).toBe(false)
  })
})

describe('CommentList —— 空态', () => {
  it('**没有评价时明说"暂无评价"**，不渲染空白列表', () => {
    const w = mountList({ comments: [] })
    expect(w.findAll('.cm-item')).toHaveLength(0)
    expect(w.get('.cm-empty').text()).toContain('暂无评价')
  })
})

describe('CommentList —— 长内容折叠（FR-072）', () => {
  const LONG = '好'.repeat(200)

  it('超过 120 字符时默认折叠，且有展开入口', () => {
    const w = mountList({ comments: [comment({ content: LONG })] })
    expect(w.get('.cm-content').classes()).toContain('is-folded')
    expect(w.get('.cm-toggle').text()).toContain('展开')
  })

  it('点展开后不再折叠', async () => {
    const w = mountList({ comments: [comment({ content: LONG })] })
    await w.get('.cm-toggle').trigger('click')
    expect(w.get('.cm-content').classes()).not.toContain('is-folded')
    expect(w.get('.cm-toggle').text()).toContain('收起')
  })

  it('120 字符以内不折叠、也不出现展开按钮', () => {
    const w = mountList({ comments: [comment({ content: '短'.repeat(120) })] })
    expect(w.get('.cm-content').classes()).not.toContain('is-folded')
    expect(w.find('.cm-toggle').exists()).toBe(false)
  })

  it('**传 collapsible=false 时一律不折叠**（「全部评价」页要看到完整内容）', () => {
    const w = mountList({ comments: [comment({ content: LONG })], collapsible: false })
    expect(w.get('.cm-content').classes()).not.toContain('is-folded')
    expect(w.find('.cm-toggle').exists()).toBe(false)
  })
})

describe('CommentList —— 查看全部入口（FR-071）', () => {
  it('showViewAll 为真时出现入口，点击 emit view-all', async () => {
    const w = mountList({ showViewAll: true })
    await w.get('.cm-view-all').trigger('click')
    expect(w.emitted('view-all')).toHaveLength(1)
  })

  it('默认不出现（「全部评价」页自己不需要这个入口）', () => {
    const w = mountList()
    expect(w.find('.cm-view-all').exists()).toBe(false)
  })
})
