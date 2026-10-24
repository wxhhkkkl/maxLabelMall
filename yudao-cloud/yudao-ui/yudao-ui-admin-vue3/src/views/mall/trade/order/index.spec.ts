import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 后台订单列表的**状态标签页**（2026-10-23）。
 *
 * 钉三件事：
 * · 进页面默认按「待发货/已发货」筛（两个状态一起，不是只筛待发货）
 * · 切标签会清掉下拉里的单选状态，两者不会同时生效 —— 否则界面自相矛盾
 * · 「重置」只清搜索表单，**不动当前标签**
 */

const push = vi.fn()
const currentRoute = ref({})
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return { ...actual, useRouter: () => ({ push, currentRoute }), useRoute: () => ({}) }
})

const getOrderPage = vi.fn()
vi.mock('@/api/mall/trade/order', () => ({
  getOrderPage: (...a: unknown[]) => getOrderPage(...a),
  cancelOrder: vi.fn(),
  getOrderSummary: vi.fn()
}))
vi.mock('@/api/mall/trade/delivery/pickUpStore', () => ({
  getSimpleDeliveryPickUpStoreList: vi.fn().mockResolvedValue([])
}))
vi.mock('@/api/mall/trade/delivery/express', () => ({
  getSimpleDeliveryExpressList: vi.fn().mockResolvedValue([])
}))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: {},
  getIntDictOptions: () => [],
  getStrDictOptions: () => []
}))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
// 字典标签会去后端拉字典；单测里不要真发请求
vi.mock('@/config/axios', () => ({
  default: {
    get: async () => [],
    post: async () => null,
    postOriginal: async () => null,
    delete: async () => null,
    put: async () => null,
    download: async () => null
  }
}))

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
;(globalThis as any).ResizeObserver = ResizeObserverStub

const OrderIndex = (await import('./index.vue')).default

/** 取最后一次分页请求的参数 */
const lastParams = () => getOrderPage.mock.calls.at(-1)?.[0] as Record<string, any>

/** 点某个状态标签（按文案找） */
async function clickTab(w: any, label: string) {
  const tab = w.findAll('.el-tabs__item').find((t: any) => t.text() === label)
  expect(tab, `找不到标签「${label}」`).toBeTruthy()
  await tab!.trigger('click')
  await flushPromises()
}

beforeEach(() => {
  getOrderPage.mockReset()
  getOrderPage.mockResolvedValue({ list: [], total: 0 })
})

describe('后台订单列表 —— 状态标签页', () => {
  it('进页面默认只查「待发货 + 已发货」', async () => {
    const w = mount(OrderIndex, { global: { plugins: [createPinia()] } })
    await flushPromises()

    expect(lastParams().statuses).toEqual([10, 20])
    expect(lastParams().status).toBeUndefined()
    // 默认标签高亮在「待发货/已发货」
    expect(w.get('.el-tabs__item.is-active').text()).toBe('待发货/已发货')
  })

  it('切到「已完成」→ 只按 30 筛，且清掉下拉的单选状态', async () => {
    const w = mount(OrderIndex, { global: { plugins: [createPinia()] } })
    await flushPromises()

    await clickTab(w, '已完成')
    expect(lastParams().statuses).toEqual([30])
    expect(lastParams().status).toBeUndefined()
  })

  it('切到「全部订单」→ 不按状态筛', async () => {
    const w = mount(OrderIndex, { global: { plugins: [createPinia()] } })
    await flushPromises()

    await clickTab(w, '全部订单')
    expect(lastParams().statuses).toBeUndefined()
    expect(lastParams().status).toBeUndefined()
  })

  it('「重置」只清搜索表单，不动当前标签', async () => {
    const w = mount(OrderIndex, { global: { plugins: [createPinia()] } })
    await flushPromises()

    await clickTab(w, '已完成')
    const reset = w.findAll('button').find((b) => b.text().includes('重置'))
    expect(reset).toBeTruthy()
    await reset!.trigger('click')
    await flushPromises()

    expect(lastParams().statuses).toEqual([30])
    expect(w.get('.el-tabs__item.is-active').text()).toBe('已完成')
  })
})
