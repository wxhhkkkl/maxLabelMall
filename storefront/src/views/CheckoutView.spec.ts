import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Address, SettlementResp } from '@/types'
import { DELIVERY_TYPE_EXPRESS, DELIVERY_TYPE_VIRTUAL } from '@/api/order'

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
    // 默认按**快递**（有运费、要地址）—— 与改前的行为一致，绝大多数既有用例的前提。
    // 虚拟商品单用 `settlementResp({ deliveryType: DELIVERY_TYPE_VIRTUAL })` 单独构造。
    deliveryType: DELIVERY_TYPE_EXPRESS,
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
    // 默认**不开放对公转账** —— 与改前的行为一致（大多数既有用例的前提）。
    // 对公转账的用例各自显式打开（008）。
    offlinePayEnabled: false,
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

  it('**没有地址时「新增收货地址」就地弹层，不跳转** —— 跳走会丢掉本页已填内容', async () => {
    // 这里原本放的是指向 `/account/address` 的 RouterLink：一点就离开结算页，
    // 已选的券、地址、备注**全丢**。改成就地弹层（与地址管理页共用同一个组件）。
    listAddress.mockResolvedValue([])
    await router.push('/checkout')
    const w = await mountCheckout()

    await w.get('#coAddAddress').trigger('click')
    await flushPromises()

    expect(w.find('.ml-modal').exists()).toBe(true)
    expect(router.currentRoute.value.path).toBe('/checkout')
  })

  it('地址选择弹层里的「新增收货地址」同样不跳转', async () => {
    await router.push('/checkout')
    const w = await mountCheckout()
    await w.get('#addressPicker').trigger('click')
    await flushPromises()
    // 弹层里有多个 .ml-modal（选择器 + 表单），取新增按钮所在的那个
    const addBtn = w.findAll('button').find((b) => b.text() === '新增收货地址')
    expect(addBtn).toBeTruthy()
    await addBtn!.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/checkout')
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

describe('CheckoutView —— 下单时申请开票（006 / FR-153 / FR-156 / FR-159）', () => {
  /** 最后一次下单请求的**入参**（`createOrder` 被 mock 掉了，看到的是页面交给 api 层的东西） */
  const lastInput = () => createOrder.mock.calls.at(-1)?.[0] as Record<string, unknown>
  const invoiceBox = (w: Awaited<ReturnType<typeof mountCheckout>>) =>
    w.get('.invoice-card input[type="checkbox"]')

  async function submit(w: Awaited<ReturnType<typeof mountCheckout>>) {
    await w.get('#submitOrder').trigger('click')
    await flushPromises()
  }

  it('默认不开票：请求里没有 invoice（不是 null、也不是空对象）', async () => {
    const w = await mountCheckout()
    await submit(w)

    expect(createOrder).toHaveBeenCalled()
    expect(lastInput().invoice).toBeUndefined()
  })

  it('勾选并填好抬头后，invoice **逐字段**进请求体', async () => {
    // 本项目吃过"表单填了但没提交出去"的亏（只断言 UI、不断言请求体）——这条专门钉它
    const w = await mountCheckout()
    await invoiceBox(w).setValue(true)
    await w.get('#invoiceTitleName').setValue('南京路桥机械有限公司')
    await w.get('#invoiceTaxNo').setValue('91320104660690975E')
    await submit(w)

    expect(lastInput().invoice).toEqual({
      type: 1,
      titleKind: 1,
      titleName: '南京路桥机械有限公司',
      taxNo: '91320104660690975E',
      titleAddress: '',
      titlePhone: '',
      bankName: '',
    })
  })

  it('**个人抬头**：只填姓名就能下单，`titleKind` 确实发到了请求体里（Q38）', async () => {
    const w = await mountCheckout()
    await invoiceBox(w).setValue(true)
    await w.findAll('.invoice-kind')[1]!.trigger('click') // 选「个人」
    await w.get('#invoiceTitleName').setValue('张三')
    await submit(w)

    expect(lastInput().invoice).toEqual(expect.objectContaining({
      type: 1,
      titleKind: 2,
      titleName: '张三',
    }))
  })

  it('勾了开票但抬头没填全 → **不提交**，并指明缺哪一项（FR-156）', async () => {
    const w = await mountCheckout()
    await invoiceBox(w).setValue(true)
    await submit(w)

    expect(createOrder).not.toHaveBeenCalled()
    expect(w.text()).toContain('请填写抬头名称')
  })

  it('实付 0 元的订单：整张发票卡片不渲染（全额券抵扣没有可开票金额）', async () => {
    const w = await mountCheckout(
      settlementResp({
        price: {
          totalPrice: 10000,
          discountPrice: 0,
          deliveryPrice: 0,
          couponPrice: 10000,
          pointPrice: 0,
          vipPrice: 0,
          payPrice: 0,
        },
      }),
    )

    expect(w.find('.invoice-card').exists()).toBe(false)
  })
})

describe('CheckoutView —— 买家留言', () => {
  const lastInput = () => createOrder.mock.calls.at(-1)?.[0] as Record<string, unknown>
  const remarkBox = (w: Awaited<ReturnType<typeof mountCheckout>>) => w.get('#orderRemark')

  async function submit(w: Awaited<ReturnType<typeof mountCheckout>>) {
    await w.get('#submitOrder').trigger('click')
    await flushPromises()
  }

  it('不填时请求体里**没有** remark（不是空串、也不是 null）', async () => {
    const w = await mountCheckout()
    await submit(w)

    expect(createOrder).toHaveBeenCalled()
    expect(lastInput().remark).toBeUndefined()
  })

  it('填了：**逐字段进请求体**（接线断言 —— 本项目吃过"只断言 UI、不断言请求体"的亏）', async () => {
    const w = await mountCheckout()
    await remarkBox(w).setValue('请在工作日送')

    await submit(w)

    expect(lastInput().remark).toBe('请在工作日送')
  })

  it('只敲了空格：当成没填（别让库里留下 `"   "` 这种值）', async () => {
    const w = await mountCheckout()
    await remarkBox(w).setValue('   ')

    await submit(w)

    expect(lastInput().remark).toBeUndefined()
  })

  it('输入框上限 200 字 —— 与 `trade_order.user_remark` 的列宽一致', async () => {
    const w = await mountCheckout()

    expect(remarkBox(w).attributes('maxlength')).toBe('200')
  })
})

describe('CheckoutView —— 虚拟商品：软件不需要收货地址（007）', () => {
  const lastInput = () => createOrder.mock.calls.at(-1)?.[0] as Record<string, unknown>
  /** 卡片标题列表 —— 比 `w.text()` 精确：弹层里也有「收货地址」字样 */
  const cardTitles = (w: Awaited<ReturnType<typeof mountCheckout>>) =>
    w.findAll('.ml-card-title').map((n) => n.text())

  it('整单虚拟：**整块不渲染收货地址卡片**，没有地址也能提交', async () => {
    const w = await mountCheckout(settlementResp({ deliveryType: DELIVERY_TYPE_VIRTUAL }))

    expect(cardTitles(w)).not.toContain('收货地址')
    expect(w.find('#addressPicker').exists()).toBe(false)
    expect(w.find('#coAddAddress').exists()).toBe(false)

    await w.get('#submitOrder').trigger('click')
    await flushPromises()

    expect(createOrder).toHaveBeenCalled()
    // 别把「请先选择收货地址」甩给买软件的人
    expect(w.text()).not.toContain('请先选择收货地址')
  })

  it('整单虚拟：下单请求带上**结算判定的**配送方式，且不带 addressId', async () => {
    const w = await mountCheckout(settlementResp({ deliveryType: DELIVERY_TYPE_VIRTUAL }))

    await w.get('#submitOrder').trigger('click')
    await flushPromises()

    const body = lastInput()
    expect(body.deliveryType).toBe(DELIVERY_TYPE_VIRTUAL)
    expect(body.addressId).toBeUndefined()
  })

  it('整单虚拟：**不去挑默认地址**（否则卡片没了、地址却已被选中）', async () => {
    // 账号有默认地址（beforeEach 里塞了 ADDR），但本单是虚拟的 —— 不该自动选中它。
    // 非虚拟单会用默认地址**再结算一次**，所以这里断言只结算了一次。
    await mountCheckout(settlementResp({ deliveryType: DELIVERY_TYPE_VIRTUAL }))

    expect(settlement).toHaveBeenCalledTimes(1)
  })

  it('含实物的混买单（服务端判成快递）：照旧渲染地址卡片、照旧带 addressId', async () => {
    const w = await mountCheckout(settlementResp({ deliveryType: DELIVERY_TYPE_EXPRESS }))

    expect(cardTitles(w)).toContain('收货地址')

    await w.get('#submitOrder').trigger('click')
    await flushPromises()

    const body = lastInput()
    expect(body.deliveryType).toBe(DELIVERY_TYPE_EXPRESS)
    expect(body.addressId).toBe(ADDR.id)
  })

  it('非虚拟单没选地址：仍然拦住并提示（这条**不能**被虚拟商品改松）', async () => {
    listAddress.mockResolvedValue([]) // 一个地址都没有
    const w = await mountCheckout(settlementResp({ deliveryType: DELIVERY_TYPE_EXPRESS }))

    await w.get('#submitOrder').trigger('click')
    await flushPromises()

    expect(createOrder).not.toHaveBeenCalled()
    expect(w.text()).toContain('请先选择收货地址')
  })
})

/**
 * 对公转账（008 / FR-199 / FR-211）。
 *
 * ⚠️ 这里的断言**盯的是下单请求体**，不是页面上有没有那个按钮 ——
 * 「选了但没发出去」是本项目踩过的坑（只断言 UI、不断言请求体）。
 */
describe('CheckoutView —— 对公转账（008 / FR-199 / FR-211）', () => {
  const lastInput = () => createOrder.mock.calls.at(-1)?.[0] as Record<string, unknown>

  async function submit(w: Awaited<ReturnType<typeof mountCheckout>>) {
    await w.get('#submitOrder').trigger('click')
    await flushPromises()
  }

  it('后台**没开**对公转账时：结算页根本不给这个选项（FR-210 的"可关掉"）', async () => {
    const w = await mountCheckout(settlementResp({ offlinePayEnabled: false }))

    expect(w.find('#payMethodOffline').exists()).toBe(false)
    expect(w.text()).not.toContain('对公转账')
  })

  it('开了对公转账：与「在线支付」**并列**给出两种方式，并带上后台配的提示语', async () => {
    const w = await mountCheckout(
      settlementResp({ offlinePayEnabled: true, offlinePayTip: '请在转账附言里写订单号' }),
    )

    expect(w.find('#payMethodOffline').exists()).toBe(true)
    expect(w.find('#payMethodOnline').exists()).toBe(true)
    expect(w.text()).toContain('请在转账附言里写订单号')
  })

  it('卡片顺序：商品清单 → 支付方式 → 发票 → 优惠券（所有者 2026-10-16 指定）', async () => {
    // 先说清为什么值得钉：这几张卡的顺序被所有者连着调过两次，
    // 而 `.ml-card-title` 是页面左列**唯一**稳定的定位锚（卡片之间没有 id/class 区分）
    const titles = (await mountCheckout(settlementResp({ offlinePayEnabled: true })))
      .findAll('.ml-card-title')
      .map((n) => n.text())
    expect(titles.indexOf('商品清单（1 件）')).toBeLessThan(titles.indexOf('支付方式'))
    expect(titles.indexOf('支付方式')).toBeLessThan(titles.indexOf('发票'))
    expect(titles.indexOf('发票')).toBeLessThan(titles.indexOf('优惠券'))
    expect(titles.indexOf('优惠券')).toBeLessThan(titles.indexOf('买家留言'))
  })

  it('⚠️ 结算响应里**没有**收款账户明细 —— 客户还没下单时不该看到完整账号（契约 §2）', async () => {
    const w = await mountCheckout(settlementResp({ offlinePayEnabled: true }))

    // 结算响应本身就没有这些字段；页面上也不能凭空出现
    expect(w.text()).not.toContain('收款账号')
  })

  it('选「对公转账」→ 请求体带 `payChannelCode: "offline"`', async () => {
    const w = await mountCheckout(settlementResp({ offlinePayEnabled: true }))

    await w.get('#payMethodOffline').trigger('click')
    await flushPromises()
    await submit(w)

    expect(lastInput().payChannelCode).toBe('offline')
  })

  it('**默认（在线支付）请求体里没有 payChannelCode** —— 与改动前逐字一致（FR-211）', async () => {
    const w = await mountCheckout(settlementResp({ offlinePayEnabled: true }))

    await submit(w)

    expect(createOrder).toHaveBeenCalled()
    expect(lastInput().payChannelCode).toBeUndefined()
  })

  it('选了之后**又改回**在线支付：请求体里不留下 offline（不能按键序残留）', async () => {
    const w = await mountCheckout(settlementResp({ offlinePayEnabled: true }))

    await w.get('#payMethodOffline').trigger('click')
    await flushPromises()
    await w.get('#payMethodOnline').trigger('click')
    await flushPromises()
    await submit(w)

    expect(lastInput().payChannelCode).toBeUndefined()
  })

  it('地址与对公转账**互不影响**：对公单照旧要收货地址', async () => {
    listAddress.mockResolvedValue([]) // 一个地址都没有
    const w = await mountCheckout(settlementResp({ offlinePayEnabled: true }))

    await w.get('#payMethodOffline').trigger('click')
    await flushPromises()
    await submit(w)

    expect(createOrder).not.toHaveBeenCalled()
    expect(w.text()).toContain('请先选择收货地址')
  })
})
