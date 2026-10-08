import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { OrderStatus, type OrderDetail } from '@/types'

const getOrderDetail = vi.fn()
const cancelOrder = vi.fn()
const submitPay = vi.fn()
const getPayOrder = vi.fn()
const listEnabledChannelCodes = vi.fn()
vi.mock('@/api/order', () => ({
  getOrderDetail: (...a: unknown[]) => getOrderDetail(...a),
  cancelOrder: (...a: unknown[]) => cancelOrder(...a),
  pageOrders: vi.fn(),
  getOrderCount: vi.fn(),
  createOrder: vi.fn(),
  settlement: vi.fn(),
}))
vi.mock('@/api/pay', () => ({
  submitPay: (...a: unknown[]) => submitPay(...a),
  getPayOrder: (...a: unknown[]) => getPayOrder(...a),
  listEnabledChannelCodes: (...a: unknown[]) => listEnabledChannelCodes(...a),
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

const OrderDetailView = (await import('./OrderDetailView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/order', component: { template: '<div/>' } },
    { path: '/order/:id', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
  ],
})

/**
 * ⚠️ 时间字段用 **epoch 毫秒数**，与线上一致 —— yudao 全局给 `LocalDateTime` 注册了
 * `TimestampLocalDateTimeSerializer`。**这里一度用的是 `'2026-09-24 10:00:00'` 这种
 * 字符串，谎报了线上格式**，于是「订单页把时间显示成 1790486340000」这个真实缺陷
 * 在单测里完全看不见。改测试 fixture 前请先想清楚线上到底长什么样。
 *
 * 用 `new Date(年,月,日,…)`（本地）构造，避免依赖运行机器的时区。
 */
const CREATE_AT = new Date(2026, 8, 24, 10, 0, 0).getTime() // 2026-09-24 10:00:00
const EXPIRE_AT = new Date(2026, 8, 24, 12, 0, 0).getTime() // 2026-09-24 12:00:00
const PAID_AT = new Date(2026, 8, 24, 10, 5, 0).getTime() // 2026-09-24 10:05:00

/**
 * 六项自洽：100 元商品 - 30 元券 + 8 元运费 = 78 元。
 * **金额是平铺字段**，与后端 `AppTradeOrderDetailRespVO` 一致（没有嵌套 price）。
 */
function detail(over: Partial<OrderDetail> = {}): OrderDetail {
  return {
    id: 1,
    no: 'NO-1',
    status: OrderStatus.UNPAID,
    createTime: CREATE_AT,
    payPrice: 7800,
    items: [
      {
        id: 11,
        spuName: '三防热敏标签纸',
        picUrl: '',
        properties: [{ propertyName: '版本', valueName: '标配' }],
        count: 1,
        price: 10000,
        payPrice: 7000,
      },
    ],
    totalPrice: 10000,
    discountPrice: 0,
    deliveryPrice: 800,
    couponPrice: 3000,
    pointPrice: 0,
    vipPrice: 0,
    payExpireTime: EXPIRE_AT,
    receiverName: '张三',
    receiverMobile: '13800008888',
    receiverAreaName: '北京市 东城区',
    receiverDetailAddress: 'xx 路 1 号',
    couponId: 1024,
    // 支付单号与交易订单号是**两个不同的 id**，故意取不同的值以免混淆
    payOrderId: 8899,
    ...over,
  }
}

async function mountDetail(d: OrderDetail = detail()) {
  getOrderDetail.mockResolvedValue(d)
  const w = mount(OrderDetailView, {
    props: { id: 1 },
    global: { plugins: [router, createPinia()] },
  })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
  getOrderDetail.mockReset()
  cancelOrder.mockReset()
  cancelOrder.mockResolvedValue(true)
  submitPay.mockReset()
  submitPay.mockResolvedValue({ status: 10, displayMode: 'url', displayContent: '' })
  // 支付单带 appId —— 界面靠它查该应用启用了哪些渠道（前端不记死应用编号）
  getPayOrder.mockReset()
  getPayOrder.mockResolvedValue({ id: 8899, appId: 10, status: 0, channelCode: '' })
  // 默认：只开了支付宝（线上 162 就是这状态，微信等商户号）
  listEnabledChannelCodes.mockReset()
  listEnabledChannelCodes.mockResolvedValue(['alipay_pc'])
  await router.push('/order/1')
  await router.isReady()
})

describe('OrderDetailView —— 状态区（FR-035 / FR-041a / FR-041c）', () => {
  it('**支付成功的订单显示「待发货」，不是「已支付」**（后端 status=10）', async () => {
    const w = await mountDetail(detail({ status: OrderStatus.UNDELIVERED }))
    expect(w.text()).toContain('待发货')
    expect(w.text()).not.toContain('已支付')
  })

  it.each([
    [OrderStatus.UNPAID, '待支付'],
    [OrderStatus.UNDELIVERED, '待发货'],
    [OrderStatus.DELIVERED, '已发货'],
    [OrderStatus.COMPLETED, '已完成'],
    [OrderStatus.CANCELED, '已取消'],
  ])('status=%s 显示为「%s」', async (status, label) => {
    const w = await mountDetail(detail({ status }))
    expect(w.get('.ml-pill').text()).toBe(label)
  })

  it('**待发货与已发货在视觉上可区分**（FR-041c）', async () => {
    const a = await mountDetail(detail({ status: OrderStatus.UNDELIVERED }))
    expect(a.get('.ml-pill').classes()).toContain('is-awaiting-shipment')
    const b = await mountDetail(detail({ status: OrderStatus.DELIVERED }))
    expect(b.get('.ml-pill').classes()).toContain('is-shipped')
  })

  it('**状态以后端返回为准**，前端不按支付时间等自行推断', async () => {
    // 后端说已完成，即便 payTime 为空也照显「已完成」
    const w = await mountDetail(detail({ status: OrderStatus.COMPLETED, payTime: undefined }))
    expect(w.get('.ml-pill').text()).toBe('已完成')
  })

  it('**不存在用户侧「确认收货」控件**（FR-041b）', async () => {
    const w = await mountDetail(detail({ status: OrderStatus.DELIVERED }))
    expect(w.text()).not.toContain('确认收货')
  })
})

describe('OrderDetailView —— 支付截止时间与支付时间（FR-041 / FR-039）', () => {
  it('待支付展示支付截止时间', async () => {
    const w = await mountDetail()
    expect(w.text()).toContain('支付截止')
    expect(w.text()).toContain('2026-09-24 12:00:00')
  })

  it('**时间是格式化过的，不是原始毫秒数**', async () => {
    // 回归断言：线上 yudao 把 LocalDateTime 序列化成 epoch 毫秒，
    // 直接插值会给用户看「支付截止 1790486340000」
    const w = await mountDetail()
    expect(w.text()).not.toContain(String(EXPIRE_AT))
    expect(w.text()).not.toContain(String(CREATE_AT))
    expect(w.text()).toContain('下单时间')
  })

  it('非待支付**不展示**支付截止时间（已无支付可言）', async () => {
    const w = await mountDetail(detail({ status: OrderStatus.UNDELIVERED }))
    expect(w.text()).not.toContain('支付截止')
  })

  it('已付款展示支付时间', async () => {
    const w = await mountDetail(detail({ status: OrderStatus.UNDELIVERED, payTime: PAID_AT }))
    expect(w.text()).toContain('2026-09-24 10:05:00')
  })

  it('未支付时支付时间位置显示「尚未支付」，不是空白也不是数字', async () => {
    const w = await mountDetail()
    expect(w.text()).toContain('尚未支付')
  })
})

describe('OrderDetailView —— 商品明细与收货信息（FR-035）', () => {
  it('商品明细含**下单时的名称与规格快照**、单价、数量', async () => {
    const w = await mountDetail()
    expect(w.text()).toContain('三防热敏标签纸')
    expect(w.text()).toContain('版本')
    expect(w.text()).toContain('标配')
    expect(w.text()).toContain('¥100.00')
    expect(w.text()).toContain('×1')
  })

  it('收货信息含收件人、电话与完整地址（含行政区划）', async () => {
    const w = await mountDetail()
    expect(w.text()).toContain('张三')
    expect(w.text()).toContain('13800008888')
    expect(w.text()).toContain('北京市 东城区')
    expect(w.text()).toContain('xx 路 1 号')
  })
})

describe('OrderDetailView —— 金额构成与所用券（FR-035 / FR-026f / SC-007）', () => {
  it('逐项展示商品小计 / 促销优惠 / 运费 / 优惠券抵扣 / 应付总额', async () => {
    const w = await mountDetail()
    for (const label of ['商品小计', '促销优惠', '运费', '优惠券抵扣', '应付总额']) {
      expect(w.text()).toContain(label)
    }
    expect(w.get('.ml-amount-row.is-total .v').text()).toBe('¥78.00')
  })

  it('**体现该订单所用的券及其抵扣金额**（FR-026f）', async () => {
    const w = await mountDetail()
    expect(w.text()).toContain('优惠券抵扣')
    expect(w.text()).toContain('-¥30.00')
    // 券的**名称**后端没有返回（AppTradeOrderDetailRespVO 只有 couponId/couponPrice），
    // 因此这里只展示能拿到的事实：金额 + 券编号。不编造券名。
    expect(w.text()).toContain('1024')
  })

  it('没有用券的订单不显示券编号提示', async () => {
    const w = await mountDetail(
      detail({
        couponId: undefined,
        couponPrice: 0,
        payPrice: 10800,
      }),
    )
    expect(w.text()).not.toContain('券编号')
  })
})

describe('OrderDetailView —— 取消订单（FR-036 / T112）', () => {
  it('**仅待支付**给出取消入口', async () => {
    const a = await mountDetail()
    expect(a.find('.cancel-order').exists()).toBe(true)
    const b = await mountDetail(detail({ status: OrderStatus.UNDELIVERED }))
    expect(b.find('.cancel-order').exists()).toBe(false)
  })

  it('二次确认后取消，并以后端状态为准重新拉详情', async () => {
    const w = await mountDetail()
    await w.get('.cancel-order').trigger('click')
    await flushPromises()
    expect(cancelOrder).not.toHaveBeenCalled()

    await w.get('#confirmCancel').trigger('click')
    await flushPromises()
    expect(cancelOrder).toHaveBeenCalledWith(1)
    // 重新拉详情：取消后的状态由后端给，前端不自行改成「已取消」
    expect(getOrderDetail.mock.calls.length).toBeGreaterThan(1)
  })
})

describe('OrderDetailView —— 支付入口（FR-037 / FR-039 / FR-040，T116）', () => {
  it('待支付**展示**支付入口', async () => {
    const w = await mountDetail()
    expect(w.find('#payOrder').exists()).toBe(true)
  })

  it('**已付款不展示**支付入口（FR-040）', async () => {
    for (const status of [
      OrderStatus.UNDELIVERED,
      OrderStatus.DELIVERED,
      OrderStatus.COMPLETED,
    ]) {
      const w = await mountDetail(detail({ status }))
      expect(w.find('#payOrder').exists()).toBe(false)
    }
  })

  it('已取消的订单也不展示支付入口', async () => {
    const w = await mountDetail(detail({ status: OrderStatus.CANCELED }))
    expect(w.find('#payOrder').exists()).toBe(false)
  })

  it('提交的是**支付单号**（不是交易订单号），且随后重拉详情确认状态（FR-039）', async () => {
    // 首次加载给「待支付」，之后再拉都给「待发货」（模拟后端已按回调推进）。
    // 这里不走 mountDetail()：它内部会 mockResolvedValue() 覆盖掉这个默认值。
    getOrderDetail
      .mockResolvedValueOnce(detail()) // 首次加载：待支付
      .mockResolvedValue(detail({ status: OrderStatus.UNDELIVERED })) // 支付后：后端已推进
    const w = mount(OrderDetailView, {
      props: { id: 1 },
      global: { plugins: [router, createPinia()] },
    })
    await flushPromises()

    await w.get('#payOrder').trigger('click')
    await flushPromises()

    // 交易订单号是 1，支付单号是 8899 —— 必须传后者，传错会「找不到支付单」
    expect(submitPay).toHaveBeenCalledWith(8899, 'alipay_pc')
    // 状态以后端返回为准（不是前端自己改的）
    expect(w.get('.ml-pill').text()).toBe('待发货')
    expect(w.find('#payOrder').exists()).toBe(false)
  })

  it('**提交成功后也不自行标记为已支付** —— 状态只认后端（FR-039）', async () => {
    // 提交成功，但重新拉到的仍是「待支付」（回调还没落地）
    getOrderDetail.mockResolvedValue(detail())
    const w = await mountDetail()

    await w.get('#payOrder').trigger('click')
    await flushPromises()

    expect(submitPay).toHaveBeenCalledWith(8899, 'alipay_pc')
    // 不得乐观地翻成「待发货」或「已支付」
    expect(w.get('.ml-pill').text()).toBe('待支付')
    expect(w.text()).not.toContain('待发货')
  })

  it('**支付单号为空时不给支付入口**，并说明本单无需支付（payPrice=0 的订单）', async () => {
    // payPrice 为 0 的订单后端不创建支付单 —— 没有任何 id 可提交。
    // 金额本身保持六项自洽（100 - 100 券 + 0 运费 = 0），免得同时触发金额告警。
    const w = await mountDetail(
      detail({ payOrderId: null, payPrice: 0, couponPrice: 10000, deliveryPrice: 0 }),
    )

    expect(w.find('#payOrder').exists()).toBe(false)
    expect(w.text()).toContain('无需支付')
    // 绝不能把 null 提交给支付接口
    expect(submitPay).not.toHaveBeenCalled()
  })

  it('**渠道码由后端给的启用列表决定**，不再是写死的 mock', async () => {
    await mountDetail()
    expect(listEnabledChannelCodes).toHaveBeenCalledWith(10)
    expect(submitPay).not.toHaveBeenCalled() // 只是加载，不该自动提交
  })

  it('待支付时展示渠道选择器：支付宝可选、微信置灰占位', async () => {
    const w = await mountDetail()
    const opts = w.findAll('.pay-channel')
    // 共两个入口、顺序固定：支付宝在前、微信在后
    expect(opts).toHaveLength(2)
    expect(opts[0].text()).toContain('支付宝')
    expect(opts[1].text()).toContain('微信支付')
    expect(opts[0].classes()).not.toContain('is-disabled')
    expect(opts[1].classes()).toContain('is-disabled')
    // 未开通的渠道要写明原因，不然用户会以为点了没反应
    expect(opts[1].text()).toContain('即将上线')
  })

  it('两个渠道都启用时，微信也可选', async () => {
    listEnabledChannelCodes.mockResolvedValue(['alipay_pc', 'wx_native'])
    const w = await mountDetail()
    const opts = w.findAll('.pay-channel')
    expect(opts.every((o) => !o.classes().includes('is-disabled'))).toBe(true)
    expect(w.text()).not.toContain('即将上线')
  })

  it('切换渠道后提交的是**选中的那个**渠道码', async () => {
    listEnabledChannelCodes.mockResolvedValue(['alipay_pc', 'wx_native'])
    const w = await mountDetail()
    await w.findAll('.pay-channel')[1].trigger('click')
    await w.get('#payOrder').trigger('click')
    await flushPromises()
    expect(submitPay).toHaveBeenCalledWith(8899, 'wx_native')
  })

  it('**已付款的订单不拉渠道列表** —— 没有支付可言，别白发请求', async () => {
    await mountDetail(detail({ status: OrderStatus.UNDELIVERED }))
    expect(listEnabledChannelCodes).not.toHaveBeenCalled()
  })

  it('提交失败时仍是「待支付」，给出提示**并可再次发起**（FR-038 / T118）', async () => {
    submitPay.mockRejectedValue({ message: '支付渠道暂时不可用' })
    const w = await mountDetail()

    await w.get('#payOrder').trigger('click')
    await flushPromises()

    expect(w.get('.ml-pill').text()).toBe('待支付')
    expect(w.text()).toContain('支付渠道暂时不可用')
    // 支付中断后订单仍待支付，所以入口要留着让用户再试一次
    expect(w.find('#payOrder').exists()).toBe(true)
  })
})

describe('OrderDetailView —— 错误态（FR-045）', () => {
  it('加载失败给可重试的错误态，不白屏', async () => {
    getOrderDetail.mockRejectedValue(new Error('Network Error'))
    const w = mount(OrderDetailView, {
      props: { id: 1 },
      global: { plugins: [router, createPinia()] },
    })
    await flushPromises()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('重新加载')
  })
})
