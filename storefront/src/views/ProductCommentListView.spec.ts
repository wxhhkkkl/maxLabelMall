import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { ProductComment } from '@/types'

const pageComments = vi.fn()
vi.mock('@/api/comment', () => ({
  COMMENT_TYPE: { ALL: 0, GOOD: 1, MEDIOCRE: 2, NEGATIVE: 3 },
  pageComments: (...a: unknown[]) => pageComments(...a),
}))
vi.mock('@/api/cart', () => ({
  getCartCount: vi.fn().mockResolvedValue(0),
  addToCart: vi.fn(),
  listCart: vi.fn(),
  updateCartCount: vi.fn(),
  updateCartSelected: vi.fn(),
  deleteCartItems: vi.fn(),
}))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' }),
  logout: vi.fn(),
}))

const ProductCommentListView = (await import('./ProductCommentListView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
    { path: '/product/:id', component: { template: '<div/>' } },
    { path: '/product/:id/comments', component: { template: '<div/>' } },
  ],
})

function comment(over: Partial<ProductComment> = {}): ProductComment {
  return {
    id: 1,
    userNickname: '张三',
    scores: 5,
    content: '很好用',
    createTime: new Date(2026, 9, 10, 12, 0, 0).getTime(),
    ...over,
  }
}

function page(list: ProductComment[], total = list.length) {
  return { list, total }
}

async function mountView(id: number | string = 29502) {
  const w = mount(ProductCommentListView, {
    props: { id },
    global: { plugins: [router, createPinia()] },
  })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  pageComments.mockReset()
  pageComments.mockResolvedValue(page([comment()]))
  await router.push('/product/29502/comments')
  await router.isReady()
})

describe('ProductCommentListView —— 加载与渲染', () => {
  it('按路由里的商品编号拉评价', async () => {
    await mountView(29502)
    expect(pageComments).toHaveBeenCalledWith(
      expect.objectContaining({ spuId: 29502, pageNo: 1 }),
    )
  })

  it('渲染评价列表', async () => {
    const w = await mountView()
    expect(w.findAll('.cm-item')).toHaveLength(1)
    expect(w.text()).toContain('张三')
  })

  it('**长评价在这里不折叠**（与详情页相反，FR-072）', async () => {
    pageComments.mockResolvedValue(page([comment({ content: '好'.repeat(300) })]))
    const w = await mountView()
    expect(w.get('.cm-content').classes()).not.toContain('is-folded')
    expect(w.find('.cm-toggle').exists()).toBe(false)
  })

  it('**本页不给"查看全部"入口**（已经在全部评价页里了）', async () => {
    const w = await mountView()
    expect(w.find('.cm-view-all').exists()).toBe(false)
  })
})

describe('ProductCommentListView —— 空态与错误', () => {
  it('没有评价时明确说明，不白屏', async () => {
    pageComments.mockResolvedValue(page([]))
    const w = await mountView()
    expect(w.text()).toContain('暂无评价')
  })

  it('接口失败时给可重试的错误态，不白屏', async () => {
    pageComments.mockRejectedValue(new Error('boom'))
    const w = await mountView()
    expect(w.text()).toContain('加载失败')
    // 可重试
    pageComments.mockResolvedValue(page([comment()]))
    await w.get('.es-reset').trigger('click')
    await flushPromises()
    expect(w.findAll('.cm-item')).toHaveLength(1)
  })
})

describe('ProductCommentListView —— 分页', () => {
  it('超过一页时渲染分页条，切页会按新页号重新拉', async () => {
    pageComments.mockResolvedValue(page([comment()], 25))
    const w = await mountView()
    expect(w.find('.pager').exists()).toBe(true)

    const page2 = w.findAll('.pg').find((p) => p.text() === '2')
    await page2?.trigger('click')
    await flushPromises()
    expect(pageComments).toHaveBeenLastCalledWith(
      expect.objectContaining({ spuId: 29502, pageNo: 2 }),
    )
  })

  it('只有一页时不渲染分页条', async () => {
    pageComments.mockResolvedValue(page([comment()], 1))
    const w = await mountView()
    expect(w.find('.pager').exists()).toBe(false)
  })
})
