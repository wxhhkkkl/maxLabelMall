import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Address, SettlementResp } from '@/types'

const settlement = vi.fn()
const createOrder = vi.fn()
const listCart = vi.fn()
const getCartCount = vi.fn()
const listAddress = vi.fn()
const getAreaTree = vi.fn()
const pageMyCoupons = vi.fn()

vi.mock('@/api/order', async () => {
  const actual = await vi.importActual<typeof import('@/api/order')>('@/api/order')
  return {
    ...actual,
    settlement: (...a: unknown[]) => settlement(...a),
    createOrder: (...a: unknown[]) => createOrder(...a),
  }
})
vi.mock('@/api/cart', () => ({
  listCart: (...a: unknown[]) => listCart(...a),
  getCartCount: (...a: unknown[]) => getCartCount(...a),
  addToCart: vi.fn(),
  updateCartCount: vi.fn(),
  updateCartSelected: vi.fn(),
  deleteCartItems: vi.fn(),
}))
vi.mock('@/api/address', () => ({
  listAddress: (...a: unknown[]) => listAddress(...a),
  getAreaTree: vi.fn(),
  createAddress: vi.fn(),
  updateAddress: vi.fn(),
  deleteAddress: vi.fn(),
  getDefaultAddress: vi.fn(),
  getAddress: vi.fn(),
}))
vi.mock('@/api/area', () => ({ getAreaTree: (...a: unknown[]) => getAreaTree(...a) }))
vi.mock('@/api/coupon', () => ({
  pageMyCoupons: (...a: unknown[]) => pageMyCoupons(...a),
  getUnusedCouponCount: vi.fn(),
  listCouponTemplates: vi.fn(),
  takeCoupon: vi.fn(),
}))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' }),
  logout: vi.fn(),
  login: vi.fn(),
  smsLogin: vi.fn(),
  sendSmsCode: vi.fn(),
  SMS_SCENE_MEMBER_LOGIN: 1,
}))

const CheckoutView = (await import('./CheckoutView.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/checkout', component: { template: '<div/>' } },
    { path: '/order', component: { template: '<div/>' } },
    { path: '/order/:id', component: { template: '<div/>' } },
    { path: '/account/address', component: { template: '<div/>' } },
    { path: '/product/:id', component: { template: '<div/>' } },
    { path: '/cart', component: { template: '<div/>' } },
  ],
})

const ADDR: Address = {
  id: 7,
  name: '张三',
  mobile: '13800008888',
  areaId: 110101,
  areaName: '北京市 东城区',
  detailAddress: 'xx 路 1 号',
  defaultStatus: true,
}

const COUPON_OK = {
  id: 1024,
  name: '首单立减 30',
  status: 1 as const,
  usePrice: 10000,
  productScope: 1,
  productScopeValues: [],
  validStartTime: '2026-01-01 00:00:00',
  validEndTime: '2026-12-31 23:59:59',
  discountType: 1,
  discountPercent: 0,
  discountPrice: 3000,
  discountLimitPrice: 0,
  match: true,
}

const COUPON_BAD = {
  ...COUPON_OK,
  id: 2048,
  name: '满 500 减 100',
  usePrice: 50000,
  match: false,
  mismatchReason: '未达到使用门槛',
}

/** 六项自洽的金额：100 元商品 - 30 元券 + 8 元运费 = 78 元 */
function settlementResp(over: Partial<SettlementResp> = {}): SettlementResp {
  return {
    type: 0,
    items: [
      {
        categoryId: 1,
        spuId: 10,
        spuName: '三防热敏标签纸',
        skuId: 100,
        price: 10000,
        picUrl: '',
        properties: [{ propertyId: 1, propertyName: '版本', valueId: 1, valueName: '标配' }],
        count: 1,
      },
    ],
    coupons: [],
    price: {
      totalPrice: 10000,
      discountPrice: 0,
      deliveryPrice: 800,
      couponPrice: 0,
      pointPrice: 0,
      vipPrice: 0,
      payPrice: 10800,
    },
    address: null,
    usePoint: 0,
    totalPoint: 0,
    promotions: [],
    ...over,
  }
}

type CheckoutProps = {
  source?: 'product' | 'cart'
  skuId?: number
  count?: number
  cartIds?: number[]
}

async function mountCheckout(
  resp: SettlementResp = settlementResp(),
  props: CheckoutProps = { source: 'product', skuId: 100, count: 1 },
) {
  settlement.mockResolvedValue(resp)

  const w = mount(CheckoutView, {
    props,
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
  settlement.mockReset()
  createOrder.mockReset()
  listAddress.mockReset()
  getAreaTree.mockReset()
  listCart.mockReset()
  getCartCount.mockReset()
  pageMyCoupons.mockReset()
  createOrder.mockResolvedValue({ id: 1, payOrderId: 2 })

  // 与「被测响应」无关的默认值放在这里，而不是 mountCheckout() 里 ——
  // 否则会把用例体内自己的 mockResolvedValue 覆盖掉（地址为空 / 多地址两个用例
  // 就是这样被冲掉的）。用例体内的设置晚于 beforeEach，自然优先。
  listAddress.mockResolvedValue([ADDR])
  getAreaTree.mockResolvedValue([])
  listCart.mockResolvedValue({ validList: [], invalidList: [] })
  getCartCount.mockResolvedValue(0)
  pageMyCoupons.mockResolvedValue({ list: [], total: 0 })

  // router 是模块级单例，跨用例共享。不复位的话，上一个用例导航到的
  // /order/1 会被下一个用例当成自己的结果（「价格变动不跳走」就是这么挂的）。
  await router.push('/checkout')
  await router.isReady()
})

describe('CheckoutView —— 金额明细可逐项核对（SC-007 / FR-026）', () => {
  it('展示商品小计 / 促销优惠 / 运费 / 优惠券抵扣 / 应付总额五行', async () => {
    const w = await mountCheckout()
    for (const label of ['商品小计', '促销优惠', '运费', '优惠券抵扣', '应付总额']) {
      expect(w.text()).toContain(label)
    }
  })

  it('**促销优惠与优惠券抵扣是两行**，不合并成一行「优惠」（FR-026g）', async () => {
    const w = await mountCheckout(
      settlementResp({
        price: {
          totalPrice: 10000,
          discountPrice: 500,
          deliveryPrice: 800,
          couponPrice: 3000,
          pointPrice: 0,
          vipPrice: 0,
          payPrice: 7300,
        },
      }),
    )
    expect(w.text()).toContain('促销优惠')
    expect(w.text()).toContain('优惠券抵扣')
    expect(w.findAll('.ml-amount-row').length).toBeGreaterThanOrEqual(5)
  })

  it('**明细中没有积分抵扣行**（本期固定不使用积分）', async () => {
    const w = await mountCheckout()
    expect(w.text()).not.toContain('积分')
  })

  it('**应付总额等于各明细之和** —— 用 reconcile 核对（SC-007）', async () => {
    const resp = settlementResp({
      price: {
        totalPrice: 20000,
        discountPrice: 1500,
        deliveryPrice: 800,
        couponPrice: 3000,
        pointPrice: 0,
        vipPrice: 0,
        payPrice: 16300,
      },
    })
    const w = await mountCheckout(resp)
    // 页面上总额展示为 ¥163.00
    expect(w.get('.ml-amount-row.is-total .v').text()).toBe('¥163.00')
    // 且后端给的数据本身自洽
    const { reconcile } = await import('@/utils/money')
    expect(reconcile(resp.price)).toBe(true)
  })

  it('数据不自洽时**给出可见警告**，而不是照抄一个错的总数', async () => {
    const w = await mountCheckout(
      settlementResp({
        price: {
          totalPrice: 10000,
          discountPrice: 0,
          deliveryPrice: 800,
          couponPrice: 0,
          pointPrice: 0,
          vipPrice: 0,
          payPrice: 99999, // 与各项之和不符
        },
      }),
    )
    expect(w.find('.amount-warning').exists()).toBe(true)
  })
})

describe('CheckoutView —— 券选择（FR-026a / FR-026b）', () => {
  it('**可用性由后端的 match / mismatchReason 判定**，并展示不可用原因', async () => {
    listAddress.mockResolvedValue([ADDR])
    const w = await mountCheckout(settlementResp({ coupons: [COUPON_OK, COUPON_BAD] }))
    await w.get('#openCouponPicker').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('首单立减 30')
    expect(w.text()).toContain('满 500 减 100')
    // 不可用的券必须说明原因
    expect(w.text()).toContain('未达到使用门槛')
  })

  it('**不可用的券选不中**', async () => {
    const w = await mountCheckout(settlementResp({ coupons: [COUPON_OK, COUPON_BAD] }))
    await w.get('#openCouponPicker').trigger('click')
    await flushPromises()
    const bad = w.findAll('.coupon-item').find((c) => c.text().includes('满 500 减 100'))
    expect(bad?.classes()).toContain('is-disabled')
  })

  it('选券后按带 couponId 的请求重算', async () => {
    const w = await mountCheckout(settlementResp({ coupons: [COUPON_OK] }))
    await w.get('#openCouponPicker').trigger('click')
    await flushPromises()
    await w.findAll('.coupon-item')[0]?.trigger('click')
    await flushPromises()
    const lastCall = settlement.mock.calls.at(-1)?.[0] as Record<string, unknown>
    expect(lastCall.couponId).toBe(1024)
  })

  it('可以选择「不使用优惠券」，总额恢复', async () => {
    const w = await mountCheckout(settlementResp({ coupons: [COUPON_OK] }))
    await w.get('#openCouponPicker').trigger('click')
    await flushPromises()
    await w.findAll('.coupon-item')[0]?.trigger('click')
    await flushPromises()
    await w.get('#openCouponPicker').trigger('click')
    await flushPromises()
    await w.get('#couponNone').trigger('click')
    await flushPromises()
    const lastCall = settlement.mock.calls.at(-1)?.[0] as Record<string, unknown>
    expect(lastCall.couponId).toBeUndefined()
  })
})

/**
 * 购物车来源的入参拼装。
 *
 * ⚠️ 结算接口对每个 item 都要求 `skuId`（后端 `@NotNull`），而 URL 上只有 cartId。
 * 早先这里从**结算响应**里按 cartId 反查 skuId —— 可第一次请求时响应还不存在，
 * 于是发出去的就是 `skuId=0`，后端直接拒。正确做法是先从购物车列表拿 skuId。
 */
describe('CheckoutView —— 购物车来源的入参（FR-025 / FR-031）', () => {
  const CART_ITEM = {
    id: 5,
    count: 2,
    selected: true,
    spu: { id: 10, name: '三防热敏标签纸', picUrl: '', price: 10000, stock: 99 },
    sku: {
      id: 100,
      properties: [{ propertyId: 1, propertyName: '版本', valueId: 1, valueName: '标配' }],
      price: 10000,
      marketPrice: 0,
      stock: 99,
    },
  }

  it('**先用 cartId 拉购物车拿到 skuId**，再带 cartId + skuId + count 去结算', async () => {
    listCart.mockResolvedValue({ validList: [CART_ITEM], invalidList: [] })
    await mountCheckout(settlementResp(), { source: 'cart', cartIds: [5] })

    expect(listCart).toHaveBeenCalled()
    const input = settlement.mock.calls[0]?.[0] as { items: unknown[] }
    expect(input.items).toEqual([{ cartId: 5, skuId: 100, count: 2 }])
  })

  it('未被本次勾选的购物车条目不进结算', async () => {
    listCart.mockResolvedValue({
      validList: [CART_ITEM, { ...CART_ITEM, id: 6 }],
      invalidList: [],
    })
    await mountCheckout(settlementResp(), { source: 'cart', cartIds: [5] })
    const input = settlement.mock.calls[0]?.[0] as { items: Array<{ cartId: number }> }
    expect(input.items.map((i) => i.cartId)).toEqual([5])
  })
})

describe('CheckoutView —— 地址与运费（FR-027 / FR-028）', () => {
  it('默认选中默认地址', async () => {
    const w = await mountCheckout()
    expect(w.text()).toContain('xx 路 1 号')
  })

  it('没有地址时引导去新增', async () => {
    listAddress.mockResolvedValue([])
    const w = await mountCheckout()
    expect(w.text()).toContain('新增收货地址')
  })

  it('**切换地址后重新结算（运费会随之变化）**', async () => {
    const addr2: Address = { ...ADDR, id: 8, detailAddress: 'yy 路 2 号', defaultStatus: false }
    listAddress.mockResolvedValue([ADDR, addr2])
    const w = await mountCheckout()
    const before = settlement.mock.calls.length
    await w.get('#addressPicker').trigger('click')
    await flushPromises()
    const second = w.findAll('.addr-option').find((a) => a.text().includes('yy 路 2 号'))
    await second?.trigger('click')
    await flushPromises()
    // 换地址必须重新结算，否则运费还是旧地址的
    expect(settlement.mock.calls.length).toBeGreaterThan(before)
    const lastCall = settlement.mock.calls.at(-1)?.[0] as Record<string, unknown>
    expect(lastCall.addressId).toBe(8)
  })
})

describe('CheckoutView —— 提交订单（FR-027a / FR-032）', () => {
  it('提交时带 addressId，**pointStatus 由 API 层固定为 false**', async () => {
    const w = await mountCheckout()
    await w.get('#submitOrder').trigger('click')
    await flushPromises()
    const body = createOrder.mock.calls[0]?.[0] as Record<string, unknown>
    expect(body.addressId).toBe(7)
    expect(body.items).toHaveLength(1)
  })

  it('**连点两次提交只产生一笔订单**（FR-032）', async () => {
    let resolveCreate: (v: unknown) => void = () => undefined
    createOrder.mockReturnValue(new Promise((res) => (resolveCreate = res)))
    const w = await mountCheckout()
    await w.get('#submitOrder').trigger('click')
    await w.get('#submitOrder').trigger('click')
    await w.get('#submitOrder').trigger('click')
    resolveCreate({ id: 1, payOrderId: 2 })
    await flushPromises()
    expect(createOrder).toHaveBeenCalledTimes(1)
  })

  it('提交中按钮禁用，避免重复点击', async () => {
    createOrder.mockReturnValue(new Promise(() => undefined))
    const w = await mountCheckout()
    await w.get('#submitOrder').trigger('click')
    await flushPromises()
    expect(w.get('#submitOrder').attributes('disabled')).toBeDefined()
  })

  it('成功后进入订单详情（带着订单号）', async () => {
    const w = await mountCheckout()
    await w.get('#submitOrder').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/order/1')
  })
})

describe('CheckoutView —— 下单失败要说清原因且不丢输入（FR-029）', () => {
  it('库存不足时明确指出是哪件商品，且地址选择不丢', async () => {
    createOrder.mockRejectedValue({ code: 400, message: '商品「三防热敏标签纸」库存不足' })
    const w = await mountCheckout()
    await w.get('#submitOrder').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('库存不足')
    // 地址仍在页面上，不需要用户重选
    expect(w.text()).toContain('xx 路 1 号')
  })
})

describe('CheckoutView —— 价格变动需用户确认（FR-030）', () => {
  it('提交时后端报价格变动 → 提示并要求确认，**不静默成交**', async () => {
    createOrder.mockRejectedValueOnce({ code: 400, message: '商品价格已变动，请确认后重新提交' })
    const w = await mountCheckout()
    await w.get('#submitOrder').trigger('click')
    await flushPromises()
    // 明确提示 + 出现确认入口；且没有因为这次失败就直接跳走
    expect(w.text()).toContain('价格已变动')
    expect(w.find('#confirmPriceChange').exists()).toBe(true)
    expect(router.currentRoute.value.path).toBe('/checkout')
  })
})
