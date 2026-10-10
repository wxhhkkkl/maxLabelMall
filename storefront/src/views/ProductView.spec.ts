import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { ProductSpu, ProductSku } from '@/types'

const getProductDetail = vi.fn()
vi.mock('@/api/product', () => ({
  getProductDetail: (...a: unknown[]) => getProductDetail(...a),
}))

const addToCart = vi.fn()
const getCartCount = vi.fn()
// T214 的评价区。默认给空列表 —— 绝大多数既有用例不关心评价，不该被它影响
const pageComments = vi.fn()
vi.mock('@/api/comment', () => ({
  COMMENT_TYPE: { ALL: 0, GOOD: 1, MEDIOCRE: 2, NEGATIVE: 3 },
  pageComments: (...a: unknown[]) => pageComments(...a),
}))
vi.mock('@/api/cart', () => ({
  addToCart: (...a: unknown[]) => addToCart(...a),
  getCartCount: (...a: unknown[]) => getCartCount(...a),
  listCart: vi.fn(),
  updateCartCount: vi.fn(),
  updateCartSelected: vi.fn(),
  deleteCartItems: vi.fn(),
}))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' }),
  logout: vi.fn(),
}))

const ProductView = (await import('./ProductView.vue')).default
const { useToasts } = await import('@/components/base/useToasts')

function sku(id: number, over: Partial<ProductSku> = {}): ProductSku {
  return {
    id,
    properties: [{ propertyId: 1, propertyName: '版本', valueId: id, valueName: `规格 ${id}` }],
    price: 89900,
    marketPrice: 109900,
    stock: 10,
    ...over,
  }
}

function spu(over: Partial<ProductSpu> = {}): ProductSpu {
  return {
    id: 7,
    name: '赋签 M3 Pro 商用高速标签打印机',
    introduction: '300dpi 高清打印 · 双模蓝牙+USB · 自动切刀',
    categoryId: 20,
    picUrl: 'http://x/main.png',
    sliderPicUrls: ['http://x/1.png', 'http://x/2.png'],
    specType: true,
    price: 89900,
    marketPrice: 109900,
    stock: 10,
    salesCount: 3,
    deliveryTypes: [1],
    description: '<p>产品详情正文</p>',
    skus: [sku(1), sku(2, { price: 94900 }), sku(3, { price: 99900, stock: 0 })],
    ...over,
  }
}

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/product/:id', component: { template: '<div/>' } },
    { path: '/product/:id/comments', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
    { path: '/cart', component: { template: '<div/>' } },
    { path: '/checkout', component: { template: '<div/>' } },
  ],
})

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  addToCart.mockReset()
  addToCart.mockResolvedValue(1)
  getCartCount.mockReset()
  getCartCount.mockResolvedValue(0)
  // 评价默认空列表。**放在 beforeEach 而不是 mountDetail 里** —— mountDetail 会先被调用、
  // 再挂载，若在那里 reset 就会把用例自己设好的 mock 冲掉
  pageComments.mockReset()
  pageComments.mockResolvedValue({ list: [], total: 0 })
  useToasts().reset()
  // router 是模块级单例，跨用例共享。不复位的话，上一个用例导航到的 /checkout
  // 会被下一个用例当成自己的结果（「未选规格不结算」就是这么挂的）。
  await router.push('/product/7')
  await router.isReady()
})

async function mountDetail(p: ProductSpu | null) {
  getProductDetail.mockReset()
  if (p === null) getProductDetail.mockRejectedValue(new Error('SPU_NOT_ENABLE'))
  else getProductDetail.mockResolvedValue(p)
  const w = mount(ProductView, {
    props: { id: 7 },
    global: { plugins: [router, createPinia()] },
  })
  await flushPromises()
  // 评价是**第二条**异步链（详情好了才去拉），一拍 flushPromises 盖不住
  await flushPromises()
  return w
}

/** 登录态：写入令牌即可（store 的 isLogin 以令牌为准） */
async function login() {
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
}

describe('ProductView —— 图片区与信息区（FR-005）', () => {
  it('展示名称、副标题、售价与划线价', async () => {
    const w = await mountDetail(spu())
    expect(w.text()).toContain('赋签 M3 Pro 商用高速标签打印机')
    expect(w.text()).toContain('300dpi 高清打印')
    expect(w.get('.pd-price').text()).toContain('¥899.00')
    expect(w.text()).toContain('¥1099.00')
  })

  it('渲染主图与图集缩略图', async () => {
    const w = await mountDetail(spu())
    expect(w.get('.g-main img').attributes('src')).toBe('http://x/main.png')
    // 3 张 = 封面 picUrl + 两张 sliderPicUrls（早期只渲染 sliderPicUrls，
    // 于是封面不在图集里、也无法被选中）
    expect(w.findAll('.g-thumb')).toHaveLength(3)
  })

  it('展示销量与库存状态', async () => {
    const w = await mountDetail(spu())
    expect(w.text()).toContain('3')
  })
})

describe('ProductView —— 多规格联动（FR-006）', () => {
  it('多规格商品渲染可选的规格项', async () => {
    const w = await mountDetail(spu())
    expect(w.findAll('.spec')).toHaveLength(3)
  })

  it('切换规格时价格联动', async () => {
    const w = await mountDetail(spu())
    await w.findAll('.spec')[1]?.trigger('click') // 第 2 个规格有货，价 949
    expect(w.get('.pd-price').text()).toContain('¥949.00')
  })

  it('**无货规格不可选**：点了也不改变已选规格与价格', async () => {
    const w = await mountDetail(spu())
    const outOfStock = w.findAll('.spec')[2]
    expect(outOfStock?.classes()).toContain('disabled')
    await outOfStock?.trigger('click')
    // 仍是第一个有货规格的价格，未被无货规格改掉
    expect(w.get('.pd-price').text()).toContain('¥899.00')
  })

  it('默认预选第一个**有货**的规格（不是盲选第一个）', async () => {
    const w = await mountDetail(spu({ skus: [sku(1, { stock: 0 }), sku(2, { price: 94900 })] }))
    expect(w.get('.pd-price').text()).toContain('¥949.00')
  })

  it('单规格商品**不渲染空的规格表**', async () => {
    const w = await mountDetail(
      spu({ specType: false, skus: [sku(1)], description: '<p>d</p>' }),
    )
    expect(w.findAll('.spec')).toHaveLength(0)
  })

  it('数量步进器最低为 1', async () => {
    const w = await mountDetail(spu())
    expect(w.get('.qty-num').text()).toBe('1')
  })
})

describe('ProductView —— 规格参数表改由 SKU 规格项承载（FR-005a）', () => {
  it('**没有独立的「规格参数」表**', async () => {
    const w = await mountDetail(spu())
    expect(w.text()).not.toContain('规格参数')
    expect(w.find('.param-list').exists()).toBe(false)
    expect(w.findAll('.param-row')).toHaveLength(0)
  })

  it('规格项展示规格名与规格值', async () => {
    const w = await mountDetail(spu())
    expect(w.text()).toContain('版本')
    expect(w.text()).toContain('规格 1')
  })
})

describe('ProductView —— 主图与图集（FR-005）', () => {
  it('默认显示第一张图（封面）', async () => {
    const w = await mountDetail(spu())
    expect(w.get('.g-main img').attributes('src')).toBe('http://x/main.png')
    expect(w.get('.g-thumb.active img').attributes('src')).toBe('http://x/main.png')
  })

  it('图集 = 封面 + 轮播图，逐一渲染出缩略图', async () => {
    const w = await mountDetail(spu())
    const srcs = w.findAll('.g-thumb img').map((i) => i.attributes('src'))
    expect(srcs).toEqual(['http://x/main.png', 'http://x/1.png', 'http://x/2.png'])
  })

  it('**点击缩略图切换主图，并标出当前项**', async () => {
    // 这条是补一个真实缺陷：缩略图原先渲染了，但 `active` 写死成 index 0、
    // 也没有点击处理 —— 点它什么都不发生，主图永远是封面。
    const w = await mountDetail(spu())
    await w.findAll('.g-thumb')[2].trigger('click')
    expect(w.get('.g-main img').attributes('src')).toBe('http://x/2.png')
    expect(w.findAll('.g-thumb')[2].classes()).toContain('active')
    expect(w.findAll('.g-thumb')[0].classes()).not.toContain('active')
  })

  it('**封面与轮播图重复时去重**，不出现两张一模一样的缩略图', async () => {
    const w = await mountDetail(spu({ picUrl: 'http://x/1.png' }))
    const srcs = w.findAll('.g-thumb img').map((i) => i.attributes('src'))
    expect(srcs).toEqual(['http://x/1.png', 'http://x/2.png'])
  })

  it('没有任何图时给占位块，不渲染空 img', async () => {
    const w = await mountDetail(spu({ picUrl: '', sliderPicUrls: [] }))
    expect(w.find('.g-main img').exists()).toBe(false)
    expect(w.find('.g-main-ph').exists()).toBe(true)
  })
})

describe('ProductView —— 富文本详情（FR-005b / FR-005c / SC-020）', () => {
  it('渲染后台配置的富文本', async () => {
    const w = await mountDetail(spu())
    expect(w.find('.pd-desc').exists()).toBe(true)
    expect(w.text()).toContain('产品详情正文')
  })

  it('**富文本里的 <script> 不进入 DOM，也不执行**', async () => {
    const w = await mountDetail(
      spu({ description: '<p>正常</p><script>window.__pwned = true</script>' }),
    )
    expect(w.find('.pd-desc script').exists()).toBe(false)
    expect(w.html()).not.toContain('<script>window')
    expect((window as unknown as { __pwned?: boolean }).__pwned).toBeUndefined()
    // 正常内容保留
    expect(w.text()).toContain('正常')
  })

  it('**富文本里的 onerror 事件属性被剔除**', async () => {
    const w = await mountDetail(
      spu({ description: '<img src=x onerror="window.__pwned2 = true"><p>正文</p>' }),
    )
    expect(w.html()).not.toContain('onerror')
    expect((window as unknown as { __pwned2?: boolean }).__pwned2).toBeUndefined()
    expect(w.text()).toContain('正文')
  })

  it('**富文本为空时整个详情区不渲染**（不留空白占位）', async () => {
    const w = await mountDetail(spu({ description: '' }))
    expect(w.find('.pd-desc').exists()).toBe(false)
  })

  it('富文本只有空白时也不渲染', async () => {
    const w = await mountDetail(spu({ description: '   \n  ' }))
    expect(w.find('.pd-desc').exists()).toBe(false)
  })

  /**
   * ⚠️ **下面这两条是本次修复的核心**（FR-063 / FR-064）。
   *
   * 上面那几条"空/纯空白不渲染"的用例一直都在，且一直是绿的 —— 但**纯图片详情从来没被断言过**，
   * 于是它被"剥掉标签看还剩不剩文字"的旧判据默默吞掉，谁也发现不了。这正是漏掉它的原因。
   */
  it('**富文本只有图片（没有文字）时照样渲染** —— 电商里长图详情是常态', async () => {
    const w = await mountDetail(spu({ description: '<p><img src="http://x/detail.png"></p>' }))
    expect(w.find('.pd-desc').exists()).toBe(true)
    expect(w.find('.pd-desc img').exists()).toBe(true)
  })

  it('**富文本只有空壳标签时不渲染**，且不留占位文案', async () => {
    const w = await mountDetail(spu({ description: '<p></p><div><br></div>' }))
    expect(w.find('.pd-desc').exists()).toBe(false)
    // 不能退化成"渲染一个空区块 + 一句'暂无详情'"——那也是空白块
    expect(w.text()).not.toContain('暂无详情')
    expect(w.text()).not.toContain('商品详情')
  })
})

/**
 * 商品详情页的评价区（FR-067 / FR-069 / FR-071）。
 *
 * ⚠️ **评价区的失败绝不能带崩详情页** —— 这一页在支付链路上，
 * 为了一个辅助区块白屏，代价远大于收益。所以专门有一条"接口挂了页面照样正常"。
 */
describe('ProductView —— 评价区', () => {
  function comment(over: Record<string, unknown> = {}) {
    return {
      id: 1,
      userNickname: '李四',
      scores: 4,
      content: '标签清晰不掉色',
      createTime: new Date(2026, 9, 10, 9, 0, 0).getTime(),
      ...over,
    }
  }

  it('有评价时渲染评价区', async () => {
    pageComments.mockResolvedValue({ list: [comment()], total: 1 })
    const w = await mountDetail(spu())
    expect(w.find('.pd-comments').exists()).toBe(true)
    expect(w.findAll('.cm-item')).toHaveLength(1)
    expect(w.text()).toContain('标签清晰不掉色')
  })

  it('按当前商品编号去拉评价', async () => {
    await mountDetail(spu())
    expect(pageComments).toHaveBeenCalledWith(expect.objectContaining({ spuId: 7 }))
  })

  it('**没有评价时明确说明**，不留空白（FR-069）', async () => {
    pageComments.mockResolvedValue({ list: [], total: 0 })
    const w = await mountDetail(spu())
    expect(w.find('.pd-comments').exists()).toBe(true)
    expect(w.text()).toContain('暂无评价')
  })

  it('**评价接口挂了，详情页照样正常**（只降级该区块，不整页报错）', async () => {
    pageComments.mockRejectedValue(new Error('boom'))
    const w = await mountDetail(spu())
    expect(w.find('.pd-comments').exists()).toBe(false)
    // 详情本身还在
    expect(w.find('.pd-desc').exists()).toBe(true)
    expect(w.text()).toContain('产品详情正文')
    expect(w.text()).not.toContain('加载失败')
  })

  it('点「查看全部评价」跳到该商品的全部评价页', async () => {
    pageComments.mockResolvedValue({ list: [comment()], total: 30 })
    const w = await mountDetail(spu())
    await w.get('.cm-view-all').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/product/7/comments')
  })
})

describe('ProductView —— 商品下架（FR-007）', () => {
  it('接口报错时展示「已下架」提示并引导返回商城，而不是空白页', async () => {
    const w = await mountDetail(null)
    expect(w.text()).toContain('下架')
    expect(w.find('.empty-state').exists()).toBe(true)
    // 有回商城的入口：是 EmptyState 的动作按钮，点击后跳 /mall
    expect(w.get('.es-reset').text()).toBe('返回商城')
    await w.get('.es-reset').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/mall')
  })
})

describe('ProductView —— 不出现促销活动标签（FR-026h）', () => {
  it('详情页也不渲染促销活动标签', async () => {
    const w = await mountDetail(spu())
    for (const t of ['满减', '满 10 卷', '限时折扣', '秒杀']) {
      expect(w.text()).not.toContain(t)
    }
  })
})

describe('ProductView —— 加入购物车（T080 / FR-017 / FR-018）', () => {
  it('已登录点「加入购物车」用当前选中规格真正加购', async () => {
    await login()
    const w = await mountDetail(spu())
    await w.get('.btn-cart').trigger('click')
    await flushPromises()
    // 默认预选第一个有货规格（sku 1）
    expect(addToCart).toHaveBeenCalledWith(1, 1)
    expect(useToasts().items.value.map((t) => t.text).join()).toContain('已加入购物车')
  })

  it('数量按步进器的当前值一起提交', async () => {
    await login()
    const w = await mountDetail(spu())
    await w.get('.qty-btn.plus').trigger('click')
    await w.get('.btn-cart').trigger('click')
    await flushPromises()
    expect(addToCart).toHaveBeenCalledWith(1, 2)
  })

  it('**多规格但没有任何有货规格时不加购**，提示先选规格（FR-017 / FR-018）', async () => {
    await login()
    const w = await mountDetail(spu({ skus: [sku(1, { stock: 0 }), sku(2, { stock: 0 })] }))
    await w.get('.btn-cart').trigger('click')
    await flushPromises()
    expect(addToCart).not.toHaveBeenCalled()
    expect(useToasts().items.value.map((t) => t.text).join()).toContain('规格')
  })

  it('未登录时**不发加购请求**，暂存意图并引导登录（FR-015）', async () => {
    const w = await mountDetail(spu())
    await w.get('.btn-cart').trigger('click')
    await flushPromises()
    expect(addToCart).not.toHaveBeenCalled()
    expect(router.currentRoute.value.query.login).toBe('1')
  })
})

describe('ProductView —— 立即购买（T102 / FR-024）', () => {
  it('跳到结算页并带上来源与所选规格、数量，**不经过购物车**', async () => {
    await login()
    const w = await mountDetail(spu())
    await w.get('.btn-buy').trigger('click')
    await flushPromises()
    const r = router.currentRoute.value
    expect(r.path).toBe('/checkout')
    expect(r.query.source).toBe('product')
    expect(r.query.skuId).toBe('1')
    expect(r.query.count).toBe('1')
    // 直购不碰购物车
    expect(addToCart).not.toHaveBeenCalled()
  })

  it('数量取步进器的当前值', async () => {
    await login()
    const w = await mountDetail(spu())
    await w.get('.qty-btn.plus').trigger('click')
    await w.get('.btn-buy').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.count).toBe('2')
  })

  it('**多规格但有货规格未选中时不结算**，提示先选规格', async () => {
    await login()
    const w = await mountDetail(spu({ skus: [sku(1, { stock: 0 }), sku(2, { stock: 0 })] }))
    await w.get('.btn-buy').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).not.toBe('/checkout')
    expect(useToasts().items.value.map((t) => t.text).join()).toContain('规格')
  })
})
