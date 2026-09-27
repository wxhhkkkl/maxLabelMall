import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { OrderStatus, type OrderPageItem } from '@/types'

const pageOrders = vi.fn()
const cancelOrder = vi.fn()
vi.mock('@/api/order', () => ({
  pageOrders: (...a: unknown[]) => pageOrders(...a),
  cancelOrder: (...a: unknown[]) => cancelOrder(...a),
  getOrderDetail: vi.fn(),
  getOrderCount: vi.fn(),
  createOrder: vi.fn(),
  settlement: vi.fn(),
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

const OrderListView = (await import('./OrderListView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/order', component: { template: '<div/>' } },
    { path: '/order/:id', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
  ],
})

function order(id: number, over: Partial<OrderPageItem> = {}): OrderPageItem {
  return {
    id,
    no: `NO-${id}`,
    status: OrderStatus.UNPAID,
    createTime: '2026-09-24 10:00:00',
    payPrice: 10800,
    items: [{ id: id * 10, spuName: '三防热敏标签纸', picUrl: '', count: 1, price: 10000 }],
    ...over,
  }
}

function page(list: OrderPageItem[], total = list.length) {
  return { list, total }
}

async function mountList(list: OrderPageItem[] = [order(1)], total?: number) {
  pageOrders.mockResolvedValue(page(list, total))
  const w = mount(OrderListView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
  pageOrders.mockReset()
  cancelOrder.mockReset()
  cancelOrder.mockResolvedValue(true)
  await router.push('/order')
  await router.isReady()
})

describe('OrderListView —— 列表与状态筛选（FR-034 / SC-017）', () => {
  it('列出订单号、下单时间、应付金额与状态', async () => {
    const w = await mountList([order(1)])
    expect(w.text()).toContain('NO-1')
    expect(w.text()).toContain('2026-09-24 10:00:00')
    expect(w.text()).toContain('¥108.00')
    expect(w.text()).toContain('待支付')
  })

  it('默认「全部」时**不传 status**（后端标了 @InEnum，空串会被拒）', async () => {
    await mountList()
    const params = pageOrders.mock.calls[0]?.[0] as Record<string, unknown>
    expect(params.status).toBeUndefined()
  })

  it('选「待发货」按 status=10 重新查询', async () => {
    const w = await mountList()
    const tab = w.findAll('.cat-pills .tab').find((t) => t.text() === '待发货')
    await tab?.trigger('click')
    await flushPromises()
    const params = pageOrders.mock.calls.at(-1)?.[0] as Record<string, unknown>
    expect(params.status).toBe(10)
  })

  it('筛选胶囊按后端 5 态给出，不自定义命名', async () => {
    const w = await mountList()
    const labels = w.findAll('.cat-pills .tab').map((t) => t.text())
    expect(labels).toEqual(['全部', '待支付', '待发货', '已发货', '已完成', '已取消'])
  })

  it('**待发货与已发货的视觉可区分**（FR-041c），不是两个不同字符串而已', async () => {
    const w = await mountList([
      order(1, { status: OrderStatus.UNDELIVERED }),
      order(2, { status: OrderStatus.DELIVERED }),
    ])
    const pills = w.findAll('.ml-pill')
    expect(pills[0]?.classes()).toContain('is-awaiting-shipment')
    expect(pills[1]?.classes()).toContain('is-shipped')
    expect(pills[0]?.classes()).not.toEqual(pills[1]?.classes())
  })

  it('分页：总数超过一页时渲染分页器，翻页带上 pageNo', async () => {
    const w = await mountList([order(1)], 30)
    expect(w.find('.pager').exists()).toBe(true)
    await w.findAll('.pg')[1]?.trigger('click')
    await flushPromises()
    const params = pageOrders.mock.calls.at(-1)?.[0] as Record<string, unknown>
    expect(params.pageNo).toBe(2)
  })
})

describe('OrderListView —— 取消订单（FR-036 / T112）', () => {
  it('**只有待支付订单**给出取消入口，其余订单没有', async () => {
    const w = await mountList([
      order(1, { status: OrderStatus.UNPAID }),
      order(2, { status: OrderStatus.UNDELIVERED }),
    ])
    const cards = w.findAll('.order-card')
    expect(cards[0]?.find('.cancel-order').exists()).toBe(true)
    expect(cards[1]?.find('.cancel-order').exists()).toBe(false)
  })

  it('取消前二次确认；确认后调后端并重新拉列表', async () => {
    const w = await mountList([order(1)])
    await w.get('.cancel-order').trigger('click')
    await flushPromises()
    // 二次确认：不可撤销的操作不能点一下就没了
    expect(w.find('.ml-modal').exists()).toBe(true)
    expect(cancelOrder).not.toHaveBeenCalled()

    await w.get('#confirmCancel').trigger('click')
    await flushPromises()
    expect(cancelOrder).toHaveBeenCalledWith(1)
    // 状态由后端改，前端重新拉一次，不自行把订单标成已取消
    expect(pageOrders.mock.calls.length).toBeGreaterThan(1)
  })

  it('确认框里点「再想想」不发请求', async () => {
    const w = await mountList([order(1)])
    await w.get('.cancel-order').trigger('click')
    await flushPromises()
    await w.get('#dismissCancel').trigger('click')
    await flushPromises()
    expect(cancelOrder).not.toHaveBeenCalled()
  })

  it('取消失败时给出提示，且不把订单显示成已取消', async () => {
    cancelOrder.mockRejectedValue({ code: 400, message: '订单已发货，不能取消' })
    const w = await mountList([order(1)])
    await w.get('.cancel-order').trigger('click')
    await flushPromises()
    await w.get('#confirmCancel').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('订单已发货，不能取消')
  })
})

describe('OrderListView —— 空态与错误态（FR-044 / FR-045）', () => {
  it('没有订单时展示空状态并引导去商城', async () => {
    const w = await mountList([])
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('去商城逛逛')
  })

  it('请求失败时给可重试的错误态，不白屏', async () => {
    pageOrders.mockRejectedValue(new Error('Network Error'))
    const w = mount(OrderListView, { global: { plugins: [router, createPinia()] } })
    await flushPromises()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('重新加载')
  })
})
