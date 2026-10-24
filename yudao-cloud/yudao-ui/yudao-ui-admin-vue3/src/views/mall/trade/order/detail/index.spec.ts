import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 后台订单详情 —— 物流信息（010 / US36 / US37）。
 *
 * 钉四件事：
 * · 有轨迹 → 渲染时间轴
 * · 空轨迹 → 显示「暂无轨迹信息」，不是空白
 * · 查询失败 → 显示降级提示，页面其余内容仍可用
 * · 快递公司没有 `kd100Code` → 直接「暂不支持查询」，**不发**注定失败的请求
 */

const push = vi.fn()
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push, currentRoute: { value: {} } }),
    useRoute: () => ({ params: {} })
  }
})

const getOrder = vi.fn()
const getExpressTrackList = vi.fn()
const getExpressMapUrl = vi.fn()
vi.mock('@/api/mall/trade/order', () => ({
  EXPRESS_QUERY_NOT_SUPPORTED: 1_011_004_103,
  EXPRESS_MAP_NOT_CONFIGURED: 1_011_004_104,
  EXPRESS_MAP_NOT_AVAILABLE: 1_011_004_105,
  getOrder: (...a: unknown[]) => getOrder(...a),
  getExpressTrackList: (...a: unknown[]) => getExpressTrackList(...a),
  getExpressMapUrl: (...a: unknown[]) => getExpressMapUrl(...a),
  cancelOrder: vi.fn(),
  pickUpOrder: vi.fn()
}))
const getSimpleDeliveryExpressList = vi.fn()
vi.mock('@/api/mall/trade/delivery/express', () => ({
  getSimpleDeliveryExpressList: (...a: unknown[]) => getSimpleDeliveryExpressList(...a)
}))
vi.mock('@/api/mall/trade/delivery/pickUpStore', () => ({
  getDeliveryPickUpStore: vi.fn()
}))
// 真实的发货表单在 onMounted 里也会拉精简快递列表；单测里换成桩，
// 避免它在 T037 的「列表失败」用例里制造第二个未捕获的 rejection。
vi.mock('@/views/mall/trade/order/form/OrderDeliveryForm.vue', () => ({
  default: {
    name: 'OrderDeliveryForm',
    emits: ['success'],
    template: '<div />'
  }
}))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: {},
  getDictLabel: () => '',
  getDictObj: () => undefined
}))
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

const OrderDetail = (await import('./index.vue')).default
const OrderDeliveryForm = (await import('@/views/mall/trade/order/form/OrderDeliveryForm.vue')).default

const TRACK_AT = new Date(2026, 9, 22, 18, 30, 0).getTime() // 2026-10-22 18:30:00

function order(over: Record<string, unknown> = {}) {
  return {
    id: 1,
    no: 'NO-1',
    status: 20,
    deliveryType: 1, // EXPRESS
    logisticsId: 10,
    logisticsNo: 'SF1234567890',
    deliveryTime: new Date(2026, 9, 20, 10, 0, 0).getTime(),
    receiverName: '张三',
    receiverMobile: '13800008888',
    receiverDetailAddress: 'xx 路 1 号',
    payPrice: 7800,
    createTime: new Date(2026, 9, 20, 9, 0, 0).getTime(),
    items: [],
    logs: [],
    ...over
  }
}

async function mountDetail(o = order()) {
  getOrder.mockResolvedValue(o)
  const w = mount(OrderDetail, {
    props: { id: 1 },
    global: { plugins: [createPinia()] }
  })
  await flushPromises()
  return w
}

beforeEach(() => {
  getOrder.mockReset()
  getExpressTrackList.mockReset()
  getSimpleDeliveryExpressList.mockReset()
  getSimpleDeliveryExpressList.mockResolvedValue([
    { id: 10, name: '顺丰速运', kd100Code: 'shunfeng' }
  ])
  getExpressTrackList.mockResolvedValue([])
  getExpressMapUrl.mockReset()
  getExpressMapUrl.mockResolvedValue('')
})

describe('后台订单详情 —— 物流信息', () => {
  it('有轨迹时渲染时间轴', async () => {
    getExpressTrackList.mockResolvedValue([{ time: TRACK_AT, content: '已签收' }])

    const w = await mountDetail()

    expect(getExpressTrackList).toHaveBeenCalledWith(1)
    expect(w.text()).toContain('物流详情')
    expect(w.text()).toContain('已签收')
    // 时间格式化过，不是原始毫秒数
    expect(w.text()).toContain('2026-10-22 18:30:00')
    expect(w.text()).not.toContain(String(TRACK_AT))
  })

  it('空轨迹显示「暂无轨迹信息」', async () => {
    getExpressTrackList.mockResolvedValue([])

    const w = await mountDetail()

    expect(w.text()).toContain('暂无轨迹信息')
  })

  it('查询失败显示降级提示，页面其余内容仍可用', async () => {
    getExpressTrackList.mockRejectedValue(new Error('boom'))

    const w = await mountDetail()

    expect(w.text()).toContain('暂时无法获取物流信息')
    // 订单本身照常渲染
    expect(w.text()).toContain('NO-1')
  })

  it('快递公司没有 kd100Code → 直接「暂不支持查询」，不发请求', async () => {
    getSimpleDeliveryExpressList.mockResolvedValue([
      { id: 10, name: '安能', kd100Code: '' }
    ])

    const w = await mountDetail()

    expect(w.text()).toContain('该快递公司暂不支持查询')
    expect(getExpressTrackList).not.toHaveBeenCalled()
  })

  it('非空但无法识别的编码 → 内联「暂不支持查询」（T043）', async () => {
    getExpressTrackList.mockRejectedValue({ code: 1_011_004_103 })

    const w = await mountDetail()

    expect(w.text()).toContain('该快递公司暂不支持查询')
  })

  it('精简快递列表失败 → 降级提示，订单其余内容照常（T037）', async () => {
    getSimpleDeliveryExpressList.mockRejectedValue(new Error('boom'))

    const w = await mountDetail()

    expect(w.text()).toContain('暂时无法获取物流信息')
    expect(w.text()).toContain('NO-1')
    expect(getExpressTrackList).not.toHaveBeenCalled()
  })

  it('订单不存在时关闭页面并返回，不因物流加载抛 NPE（T041）', async () => {
    getOrder.mockResolvedValue(null)

    mount(OrderDetail, {
      props: { id: 1 },
      global: { plugins: [createPinia()] }
    })
    await flushPromises()

    expect(push).toHaveBeenCalledWith({ name: 'TradeOrder' })
    expect(getExpressTrackList).not.toHaveBeenCalled()
  })

  it('进入详情自动加载地图并内嵌 iframe（不需要点击，010 追加）', async () => {
    getExpressMapUrl.mockResolvedValue('https://api.kuaidi100.com/tools/map/x')

    const w = await mountDetail()

    expect(getExpressMapUrl).toHaveBeenCalledWith(1)
    expect(w.find('iframe.express-map-frame').attributes('src')).toBe(
      'https://api.kuaidi100.com/tools/map/x'
    )
  })

  it('地图未配置发货地址 → 站内提示，不渲染 iframe（010 追加）', async () => {
    getExpressMapUrl.mockRejectedValue({ code: 1_011_004_104 })

    const w = await mountDetail()

    expect(w.text()).toContain('物流地图暂未配置发货地址')
    expect(w.find('iframe').exists()).toBe(false)
  })

  it('发货后 getDetail 重新加载物流，不再停在「加载中…」（T038）', async () => {
    getOrder
      .mockResolvedValueOnce(order({ status: 10, logisticsId: null, logisticsNo: null }))
      .mockResolvedValueOnce(order({ status: 20, logisticsId: 10, logisticsNo: 'SF1234567890' }))
    getExpressTrackList.mockResolvedValue([{ time: TRACK_AT, content: '已签收' }])

    const w = mount(OrderDetail, {
      props: { id: 1 },
      global: { plugins: [createPinia()] }
    })
    await flushPromises()
    expect(getExpressTrackList).not.toHaveBeenCalled()

    // 模拟「发货」表单成功 → 模板里的 @success="getDetail"
    w.findComponent(OrderDeliveryForm).vm.$emit('success')
    await flushPromises()

    expect(getExpressTrackList).toHaveBeenCalledWith(1)
    expect(w.text()).toContain('已签收')
  })
})
