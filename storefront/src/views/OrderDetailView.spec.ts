import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import { OrderStatus, type OrderDetail } from '@/types'
import { clearWxPayPending, markWxPayPending, readWxPayPending } from '@/utils/weixin'

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

const getSocialUser = vi.fn()
const bindSocialUser = vi.fn()
const getSocialAuthRedirectUrl = vi.fn()
const createAfterSale = vi.fn()
const cancelAfterSale = vi.fn()
vi.mock('@/api/afterSale', () => ({
  createAfterSale: (...a: unknown[]) => createAfterSale(...a),
  cancelAfterSale: (...a: unknown[]) => cancelAfterSale(...a),
}))

vi.mock('@/api/social', () => ({
  SOCIAL_TYPE_WECHAT_MP: 31,
  getSocialUser: (...a: unknown[]) => getSocialUser(...a),
  bindSocialUser: (...a: unknown[]) => bindSocialUser(...a),
  getSocialAuthRedirectUrl: (...a: unknown[]) => getSocialAuthRedirectUrl(...a),
}))

// 只把「整页跳转」换掉（jsdom 里赋值 location.href 会报 not implemented），
// 其余走真实实现 —— 这样「到底有没有真的去唤起收银台」才是被真实验证过的。
const redirectTo = vi.fn()
vi.mock('@/utils/weixin', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/utils/weixin')>()),
  redirectTo: (...a: unknown[]) => redirectTo(...a),
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
  // ⚠️ 默认取「渠道已受理、**没有** displayMode」那条路 —— 即提交后重拉详情确认状态。
  //    这个值**不能随便写**：`displayMode: 'url'` 意味着「跳收银台」，组件会直接
  //    `redirectTo` 并返回，**不会重拉详情** —— 于是所有断言「提交后重拉一次详情」的
  //    用例都会因为压根没重拉而失败。凡是测 url 跳转的用例，各自显式设置该字段。
  submitPay.mockResolvedValue({ status: 10, displayMode: '', displayContent: '' })
  // 支付单带 appId —— 界面靠它查该应用启用了哪些渠道（前端不记死应用编号）
  getPayOrder.mockReset()
  getPayOrder.mockResolvedValue({ id: 8899, appId: 10, status: 0, channelCode: '' })
  // 默认：只开了支付宝（线上 162 就是这状态，微信等商户号）
  listEnabledChannelCodes.mockReset()
  listEnabledChannelCodes.mockResolvedValue(['alipay_pc'])
  createAfterSale.mockReset()
  createAfterSale.mockResolvedValue(2048)
  cancelAfterSale.mockReset()
  cancelAfterSale.mockResolvedValue(true)
  getSocialUser.mockReset()
  getSocialUser.mockResolvedValue(null)
  bindSocialUser.mockReset()
  getSocialAuthRedirectUrl.mockReset()
  redirectTo.mockReset()
  clearWxPayPending()
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
    expect(submitPay).toHaveBeenCalledWith(8899, 'alipay_pc', undefined, expect.any(String))
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

    expect(submitPay).toHaveBeenCalledWith(8899, 'alipay_pc', undefined, expect.any(String))
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
    expect(submitPay).toHaveBeenCalledWith(8899, 'wx_native', undefined, expect.any(String))
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

/**
 * 微信公众号 JSAPI 支付（渠道码 `wx_pub`）。
 *
 * ⚠️ 这一组**刻意断言「请求体」与「收银台入参」**，不只断言界面文案 ——
 * 本项目吃过「造假登录态、只断言 UI 不断言请求体」的亏：那样写出来的绿灯
 * 证明不了链路接通。
 */
describe('OrderDetailView —— 微信公众号 JSAPI 支付（wx_pub）', () => {
  const UA_WECHAT =
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 MicroMessenger/8.0.44 NetType/WIFI'

  /** JSAPI 下单结果 —— 字段名照后端 `WxPubPayClient` 返回的样子写 */
  const JSAPI_DISPLAY = JSON.stringify({
    appId: 'wx1234567890',
    timeStamp: '1730000000',
    nonceStr: 'nonce-abc',
    packageValue: 'prepay_id=abc',
    signType: 'MD5',
    paySign: 'SIGN-xyz',
  })

  let uaSpy: { mockRestore: () => void } | null = null

  function setUa(ua: string) {
    uaSpy?.mockRestore()
    uaSpy = vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue(ua)
  }

  /** 装上假的微信内置 bridge，返回 `invoke` 以便断言入参 */
  function installBridge(errMsg = 'getBrandWCPayRequest:ok') {
    const invoke = vi.fn(
      (_api: string, _params: Record<string, string>, cb: (r: { err_msg: string }) => void) =>
        cb({ err_msg: errMsg }),
    )
    ;(globalThis as { WeixinJSBridge?: unknown }).WeixinJSBridge = { invoke }
    return invoke
  }

  /** 链路上有好几段串行的 await，一次 flush 不够 */
  async function settle() {
    for (let i = 0; i < 4; i++) await flushPromises()
  }

  afterEach(() => {
    uaSpy?.mockRestore()
    uaSpy = null
    delete (globalThis as { WeixinJSBridge?: unknown }).WeixinJSBridge
  })

  it('微信外不渲染 wx_pub —— 后端就算启用了也不给这个入口', async () => {
    listEnabledChannelCodes.mockResolvedValue(['alipay_pc', 'wx_pub'])
    const w = await mountDetail()
    expect(w.find('[data-channel="wx_pub"]').exists()).toBe(false)
  })

  it('微信内渲染 wx_pub，且文案与扫码渠道区分开', async () => {
    setUa(UA_WECHAT)
    listEnabledChannelCodes.mockResolvedValue(['wx_pub'])
    const w = await mountDetail()
    const btn = w.find('[data-channel="wx_pub"]')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toContain('微信支付（公众号）')
    expect(btn.classes()).not.toContain('is-disabled')
  })

  it('**提交支付时真的带上了 openid**，唤起收银台用的也是后端给的参数', async () => {
    setUa(UA_WECHAT)
    const invoke = installBridge()
    listEnabledChannelCodes.mockResolvedValue(['wx_pub'])
    getSocialUser.mockResolvedValue({ openid: 'o-1' })
    submitPay.mockResolvedValue({ status: 0, displayMode: 'app', displayContent: JSAPI_DISPLAY })
    const w = await mountDetail()

    await w.get('#payOrder').trigger('click')
    await settle()

    // ① 请求体：没有 openid 后端一定拒（WxPubPayClient 直接抛错）
    expect(submitPay).toHaveBeenCalledWith(8899, 'wx_pub', { openid: 'o-1' })
    // ② 收银台：真要唤起，且 packageValue 必须改名成 package
    expect(invoke).toHaveBeenCalledTimes(1)
    const [api, params] = invoke.mock.calls[0]
    expect(api).toBe('getBrandWCPayRequest')
    expect(params).toEqual({
      appId: 'wx1234567890',
      timeStamp: '1730000000',
      nonceStr: 'nonce-abc',
      package: 'prepay_id=abc',
      signType: 'MD5',
      paySign: 'SIGN-xyz',
    })
  })

  it('**收银台走完之后仍以后端状态为准**（FR-039），不自行标记已付款', async () => {
    setUa(UA_WECHAT)
    installBridge('getBrandWCPayRequest:ok')
    listEnabledChannelCodes.mockResolvedValue(['wx_pub'])
    getSocialUser.mockResolvedValue({ openid: 'o-1' })
    submitPay.mockResolvedValue({ status: 0, displayMode: 'app', displayContent: JSAPI_DISPLAY })
    const w = await mountDetail()

    await w.get('#payOrder').trigger('click')
    await settle()

    // 重新拉了一次详情，且带 sync=true（让后端顺便同步渠道状态）
    expect(getOrderDetail).toHaveBeenCalledWith(1, true)
    // 后端仍说待支付 → 页面就得是待支付（这里 mock 没推进状态）
    expect(w.get('.ml-pill').text()).toBe('待支付')
  })

  it('收银台取消 → 明确提示，订单仍「待支付」且入口还在', async () => {
    setUa(UA_WECHAT)
    installBridge('getBrandWCPayRequest:cancel')
    listEnabledChannelCodes.mockResolvedValue(['wx_pub'])
    getSocialUser.mockResolvedValue({ openid: 'o-1' })
    submitPay.mockResolvedValue({ status: 0, displayMode: 'app', displayContent: JSAPI_DISPLAY })
    const w = await mountDetail()

    await w.get('#payOrder').trigger('click')
    await settle()

    expect(w.text()).toContain('支付已取消')
    expect(w.get('.ml-pill').text()).toBe('待支付')
    expect(w.find('#payOrder').exists()).toBe(true)
  })

  it('没有 openid 时**不发支付请求**，先整页跳授权，并记下在付哪一笔', async () => {
    setUa(UA_WECHAT)
    listEnabledChannelCodes.mockResolvedValue(['wx_pub'])
    getSocialUser.mockResolvedValue(null)
    getSocialAuthRedirectUrl.mockResolvedValue('https://open.weixin.qq.com/connect/oauth2/authorize?...')
    const w = await mountDetail()

    await w.get('#payOrder').trigger('click')
    await settle()

    // 回跳地址是当前订单页，且**不带 query**（免得把 ?login=1 之类带进回调）
    expect(getSocialAuthRedirectUrl).toHaveBeenCalledWith('http://localhost:3000/order/1')
    expect(redirectTo).toHaveBeenCalledWith('https://open.weixin.qq.com/connect/oauth2/authorize?...')
    // 授权往返之间要知道回来接着付哪一笔
    expect(readWxPayPending()).toBe(8899)
    // 没有 openid 就提交，必然被后端拒 —— 不该发这一枪
    expect(submitPay).not.toHaveBeenCalled()
  })

  it('授权回跳后自动换 openid 并**接着把这一笔付掉**', async () => {
    setUa(UA_WECHAT)
    const invoke = installBridge()
    // 两个渠道都开着，且支付宝排在前面 —— 用来验证续跑时会切回微信渠道
    listEnabledChannelCodes.mockResolvedValue(['alipay_pc', 'wx_pub'])
    bindSocialUser.mockResolvedValue('o-9')
    submitPay.mockResolvedValue({ status: 0, displayMode: 'app', displayContent: JSAPI_DISPLAY })
    markWxPayPending(8899)

    await router.push('/order/1?code=C-1&state=S-1')
    await mountDetail()
    await settle()

    expect(bindSocialUser).toHaveBeenCalledWith('C-1', 'S-1')
    expect(submitPay).toHaveBeenCalledWith(8899, 'wx_pub', { openid: 'o-9' })
    expect(invoke).toHaveBeenCalledTimes(1)
    // 凭据不该留在地址栏上：这条 URL 事实上可重放（后端会先命中自己库里存的 code+state）
    expect(router.currentRoute.value.query.code).toBeUndefined()
    expect(router.currentRoute.value.query.state).toBeUndefined()
    // 续跑是「读后即删」，不会因为再刷新一次而重复发单
    expect(readWxPayPending()).toBeNull()
  })

  it('续跑只发生一次 —— 刷新页面不会再自动提交一次', async () => {
    setUa(UA_WECHAT)
    installBridge()
    listEnabledChannelCodes.mockResolvedValue(['wx_pub'])
    bindSocialUser.mockResolvedValue('o-9')
    submitPay.mockResolvedValue({ status: 0, displayMode: 'app', displayContent: JSAPI_DISPLAY })
    markWxPayPending(8899)

    await router.push('/order/1?code=C-1&state=S-1')
    await mountDetail()
    await settle()
    expect(submitPay).toHaveBeenCalledTimes(1)

    // 用户手动刷新（同一 URL，但续跑标记已消费；这里重挂一次组件模拟）
    await mountDetail()
    await settle()
    expect(submitPay).toHaveBeenCalledTimes(1)
  })

  it('授权换 openid 失败时，把后端文案摊开来说，且不偷偷续跑', async () => {
    setUa(UA_WECHAT)
    listEnabledChannelCodes.mockResolvedValue(['wx_pub'])
    bindSocialUser.mockRejectedValue({ message: '社交授权失败，原因是：invalid code' })
    markWxPayPending(8899)

    await router.push('/order/1?code=C-bad&state=S-1')
    const w = await mountDetail()
    await settle()

    expect(w.text()).toContain('社交授权失败')
    expect(submitPay).not.toHaveBeenCalled()
    expect(readWxPayPending()).toBeNull()
  })

  it('微信外误点到 wx_pub 时兜底提示，不发出注定失败的支付请求', async () => {
    // 渠道选择器已经按环境过滤，这里直接构造出「选中了 wx_pub」的状态来验兜底
    setUa(UA_WECHAT)
    listEnabledChannelCodes.mockResolvedValue(['wx_pub'])
    const w = await mountDetail()
    // 切回非微信环境后再点
    uaSpy?.mockRestore()
    uaSpy = vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 Chrome/126')

    await w.get('#payOrder').trigger('click')
    await settle()

    expect(submitPay).not.toHaveBeenCalled()
    expect(w.text()).toContain('请在微信中打开')
  })
})

/**
 * 按后端给的 `displayMode` 把支付**接下去** —— 这是「点了立即支付没反应」的根因所在。
 *
 * 后端用 `displayMode` 告诉前端「这次支付该怎么继续」：
 *   · `url`   —— 跳收银台（支付宝电脑网站支付）。**拿到地址却不跳 = 什么都没发生**
 *   · `app`   —— 唤起 App/微信公众号内的收银台（本项目由 `wx_pub` 分支自己处理）
 *   · `qr_code` / `qr_code_url` / `form` —— 前端尚未实现，必须**明说**而不是静默
 *   · 不设（null）—— 例如 `mock`：渠道自己受理了，重拉详情确认状态即可
 */
describe('OrderDetailView —— 按 displayMode 继续支付', () => {
  it('**displayMode=url 时真的跳转到收银台地址**（这就是「没反应」的根因）', async () => {
    submitPay.mockResolvedValue({
      status: 0,
      displayMode: 'url',
      displayContent: 'https://openapi.alipay.com/gateway.do?charset=UTF-8&...',
    })
    const w = await mountDetail()

    await w.get('#payOrder').trigger('click')
    await flushPromises()

    expect(redirectTo).toHaveBeenCalledWith('https://openapi.alipay.com/gateway.do?charset=UTF-8&...')
  })

  it('跳走之后**不再重拉详情** —— 页面已经离开了，请求没意义', async () => {
    submitPay.mockResolvedValue({ status: 0, displayMode: 'url', displayContent: 'https://x' })
    const w = await mountDetail()
    const before = getOrderDetail.mock.calls.length

    await w.get('#payOrder').trigger('click')
    await flushPromises()

    expect(getOrderDetail.mock.calls.length).toBe(before)
  })

  it('提交时带上 **returnUrl = 当前页地址**，付完能回到订单页', async () => {
    submitPay.mockResolvedValue({ status: 0, displayMode: 'url', displayContent: 'https://x' })
    const w = await mountDetail()

    await w.get('#payOrder').trigger('click')
    await flushPromises()

    const args = submitPay.mock.calls[0]
    expect(args[0]).toBe(8899)
    expect(args[1]).toBe('alipay_pc')
    // 就是「当前页地址」；且必须是**绝对 URL** —— 后端 returnUrl 上有 @URL 校验，
    // 传相对路径会被判「回跳地址的格式必须是 URL」。
    // （组件测试跑在 memory history 下，jsdom 的地址就是根路径，不会有路由前缀）
    expect(args[3]).toBe(window.location.href)
    expect(String(args[3])).toMatch(/^https?:\/\//)
  })

  it('前端接不住的 displayMode（二维码/表单）→ **明确提示**，不静默什么都不做', async () => {
    submitPay.mockResolvedValue({ status: 0, displayMode: 'qr_code', displayContent: 'weixin://wxpay/...' })
    const w = await mountDetail()

    await w.get('#payOrder').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('扫码')
    expect(redirectTo).not.toHaveBeenCalled()
    // 订单仍待支付，入口留着让用户换渠道再试
    expect(w.find('#payOrder').exists()).toBe(true)
  })

  it('没有 displayMode 时（mock 那种）保持原行为：重拉详情确认状态', async () => {
    submitPay.mockResolvedValue({ status: 10, displayMode: '', displayContent: '' })
    getOrderDetail
      .mockResolvedValueOnce(detail())
      .mockResolvedValue(detail({ status: OrderStatus.UNDELIVERED }))
    const w = mount(OrderDetailView, { props: { id: 1 }, global: { plugins: [router, createPinia()] } })
    await flushPromises()

    await w.get('#payOrder').trigger('click')
    await flushPromises()

    expect(redirectTo).not.toHaveBeenCalled()
    expect(w.get('.ml-pill').text()).toBe('待发货')
  })
})

/**
 * 申请退款（按单个商品）。
 *
 * 判定口径全部来自后端 `AfterSaleServiceImpl`：订单必须已支付且未取消、
 * 该**订单项**未被申请过、退款金额不超过该项实付（所以实付为 0 不给入口）。
 * 这里断言的重点同样是**请求体**与**是否重拉详情**，不是按钮长什么样。
 */
describe('OrderDetailView —— 申请退款', () => {
  /** 造一笔「已支付」的订单（默认 fixture 是待支付） */
  function paid(over: Partial<OrderDetail> = {}) {
    return detail({ status: OrderStatus.UNDELIVERED, ...over })
  }

  /** 两项商品，各自给不同的售后状态 */
  function twoItems(first: Record<string, unknown>, second: Record<string, unknown>) {
    const d = paid()
    return detail({
      status: OrderStatus.UNDELIVERED,
      items: [{ ...d.items[0], ...first }, { ...d.items[0], id: 12, spuName: '另一件商品', ...second }],
    })
  }

  it('待支付的订单没有退款入口（后端对未支付直接拒）', async () => {
    const w = await mountDetail(detail({ status: OrderStatus.UNPAID }))
    expect(w.find('.od-refund').exists()).toBe(false)
  })

  it('已取消的订单没有退款入口', async () => {
    const w = await mountDetail(detail({ status: OrderStatus.CANCELED }))
    expect(w.find('.od-refund').exists()).toBe(false)
  })

  it.each([OrderStatus.UNDELIVERED, OrderStatus.DELIVERED, OrderStatus.COMPLETED])(
    '订单状态 %s 时有退款入口',
    async (status) => {
      const w = await mountDetail(detail({ status }))
      expect(w.find('.od-refund').exists()).toBe(true)
    },
  )

  it('**已申请过的项显示状态标签、不再有按钮**（售后中 / 已退款）', async () => {
    const applying = paid()
    const w = await mountDetail(
      detail({ status: OrderStatus.UNDELIVERED, items: [{ ...applying.items[0], afterSaleStatus: 10 }] }),
    )
    expect(w.find('.od-refund').exists()).toBe(false)
    expect(w.text()).toContain('退款处理中')

    const refunded = paid()
    const w2 = await mountDetail(
      detail({ status: OrderStatus.DELIVERED, items: [{ ...refunded.items[0], afterSaleStatus: 20 }] }),
    )
    expect(w2.find('.od-refund').exists()).toBe(false)
    expect(w2.text()).toContain('已退款')
  })

  it('**一项在售后中不影响另一项申请**（后端查重只看单项）', async () => {
    const w = await mountDetail(twoItems({ afterSaleStatus: 10 }, { afterSaleStatus: 0 }))
    expect(w.findAll('.od-refund')).toHaveLength(1)
    // 按项定位：可申请的是第二项（id=12）
    expect(w.get('.od-refund').attributes('data-item')).toBe('12')
  })

  it('实付为 0 的项不给入口（后端要求退款金额 > 0）', async () => {
    const d = paid()
    const w = await mountDetail(
      detail({ status: OrderStatus.UNDELIVERED, items: [{ ...d.items[0], payPrice: 0 }] }),
    )
    expect(w.find('.od-refund').exists()).toBe(false)
  })

  it('点「申请退款」打开弹层', async () => {
    const w = await mountDetail(paid())
    await w.get('.od-refund').trigger('click')
    await flushPromises()
    expect(w.find('#refundSubmit').exists()).toBe(true)
  })

  it('**提交成功后重新拉详情**确认售后状态（不本地乐观更新）', async () => {
    getOrderDetail.mockResolvedValue(paid())
    const w = mount(OrderDetailView, { props: { id: 1 }, global: { plugins: [router, createPinia()] } })
    await flushPromises()
    const before = getOrderDetail.mock.calls.length

    await w.get('.od-refund').trigger('click')
    await flushPromises()
    await w.get('#refundReason').setValue('不想要了')
    await w.get('#refundSubmit').trigger('click')
    await flushPromises()

    expect(createAfterSale).toHaveBeenCalledWith({
      orderItemId: 11,
      way: 10,
      refundPrice: 7000,
      applyReason: '不想要了',
    })
    expect(getOrderDetail.mock.calls.length).toBeGreaterThan(before)
  })

  it('全部项退款成功后订单被取消，页面仍能正常渲染', async () => {
    const d = detail({ status: OrderStatus.CANCELED })
    const w = await mountDetail(
      detail({ status: OrderStatus.CANCELED, items: [{ ...d.items[0], afterSaleStatus: 20 }] }),
    )
    expect(w.get('.ml-pill').text()).toBe('已取消')
    expect(w.find('.od-refund').exists()).toBe(false)
    expect(w.find('.empty-state').exists()).toBe(false)
  })
})

/**
 * 撤销退款申请（买家自己撤）。
 *
 * ⚠️ 撤销用的一定是**售后单编号**（订单项上的 `afterSaleId`），不是订单项/订单编号。
 * 撤销成功后订单项回到「未售后」，所以「申请退款」入口要重现。
 */
describe('OrderDetailView —— 撤销退款申请', () => {
  /** 一笔已付款订单，某个商品在售后中 */
  function applying(over: Record<string, unknown> = {}) {
    const d = detail({ status: OrderStatus.UNDELIVERED })
    return detail({
      status: OrderStatus.UNDELIVERED,
      items: [{ ...d.items[0], afterSaleStatus: 10, afterSaleId: 2048, ...over }],
    })
  }

  it('售后中的商品显示「撤销申请」，不再显示「申请退款」', async () => {
    const w = await mountDetail(applying())
    expect(w.find('.od-cancel-refund').exists()).toBe(true)
    expect(w.find('.od-refund').exists()).toBe(false)
    expect(w.text()).toContain('退款处理中')
  })

  it('未售后的商品只有「申请退款」，没有撤销', async () => {
    const w = await mountDetail(detail({ status: OrderStatus.UNDELIVERED }))
    expect(w.find('.od-refund').exists()).toBe(true)
    expect(w.find('.od-cancel-refund').exists()).toBe(false)
  })

  it('已退款（售后成功）两个入口都没有', async () => {
    const d = detail({ status: OrderStatus.UNDELIVERED })
    const w = await mountDetail(
      detail({ status: OrderStatus.UNDELIVERED, items: [{ ...d.items[0], afterSaleStatus: 20 }] }),
    )
    expect(w.find('.od-refund').exists()).toBe(false)
    expect(w.find('.od-cancel-refund').exists()).toBe(false)
  })

  it('**缺售后单编号时不给撤销入口** —— 没有 id 根本发不出请求', async () => {
    const w = await mountDetail(applying({ afterSaleId: undefined }))
    expect(w.find('.od-cancel-refund').exists()).toBe(false)
    // 但状态标签还在
    expect(w.text()).toContain('退款处理中')
  })

  it('点「撤销申请」先弹确认，**确认后才发请求**，并按售后单编号撤', async () => {
    const w = await mountDetail(applying())
    const before = getOrderDetail.mock.calls.length

    await w.get('.od-cancel-refund').trigger('click')
    await flushPromises()
    expect(cancelAfterSale).not.toHaveBeenCalled()
    expect(w.find('#confirmCancelRefund').exists()).toBe(true)

    await w.get('#confirmCancelRefund').trigger('click')
    await flushPromises()

    expect(cancelAfterSale).toHaveBeenCalledWith(2048)
    // 状态以后端为准：撤销后重拉详情
    expect(getOrderDetail.mock.calls.length).toBeGreaterThan(before)
  })

  it('点「再想想」不发请求', async () => {
    const w = await mountDetail(applying())
    await w.get('.od-cancel-refund').trigger('click')
    await flushPromises()
    await w.get('#dismissCancelRefund').trigger('click')
    await flushPromises()

    expect(cancelAfterSale).not.toHaveBeenCalled()
    expect(w.find('#confirmCancelRefund').exists()).toBe(false)
  })

  it('撤销失败（例如后端说状态不允许）→ 原样透出后端文案', async () => {
    cancelAfterSale.mockRejectedValue({ message: '售后单状态不允许取消' })
    const w = await mountDetail(applying())
    await w.get('.od-cancel-refund').trigger('click')
    await flushPromises()
    await w.get('#confirmCancelRefund').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('售后单状态不允许取消')
  })
})
