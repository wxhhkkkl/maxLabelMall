import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { CouponTemplate } from '@/types'

const listCouponTemplates = vi.fn()
const takeCoupon = vi.fn()
vi.mock('@/api/coupon', () => ({
  listCouponTemplates: (...a: unknown[]) => listCouponTemplates(...a),
  takeCoupon: (...a: unknown[]) => takeCoupon(...a),
  pageMyCoupons: vi.fn(),
  getUnusedCouponCount: vi.fn(),
}))

const CouponCenterView = (await import('./CouponCenterView.vue')).default
// toast 由 DefaultLayout 里的 MlToastHost 渲染，本测试只挂视图，
// 故按既有约定（ProductView.spec.ts）直接断言 toast store
const { useToasts } = await import('@/components/base/useToasts')
const toastText = () =>
  useToasts()
    .items.value.map((t) => t.text)
    .join()

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/coupon', component: { template: '<div/>' } },
    { path: '/coupon/mine', component: { template: '<div/>' } },
  ],
})

function template(over: Partial<CouponTemplate> = {}): CouponTemplate {
  return {
    id: 1024,
    name: '新人首单券',
    usePrice: 10000,
    discountType: 1,
    discountPercent: 0,
    discountPrice: 3000,
    validStartTime: '2026-09-01 00:00:00',
    validEndTime: '2026-12-31 23:59:59',
    canTake: true,
    ...over,
  }
}

async function mountView() {
  const w = mount(CouponCenterView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  useToasts().reset()
  listCouponTemplates.mockReset()
  takeCoupon.mockReset()
  listCouponTemplates.mockResolvedValue([template()])
  takeCoupon.mockResolvedValue(false) // 领完后不能再领
  await router.push('/coupon')
  await router.isReady()
})

describe('CouponCenterView —— 展示（design-new-pages §3.7）', () => {
  it('展示券名、面额与使用条件', async () => {
    const w = await mountView()
    expect(w.text()).toContain('新人首单券')
    expect(w.text()).toContain('¥30.00') // 满减面额
    expect(w.text()).toContain('满¥100.00可用') // 使用门槛
  })

  it('折扣券显示成「打几折」而不是金额', async () => {
    listCouponTemplates.mockResolvedValue([
      template({ discountType: 2, discountPercent: 85, discountPrice: 0 }),
    ])
    const w = await mountView()
    expect(w.text()).toContain('8.5折')
  })

  it('可领的券给出「立即领取」入口', async () => {
    const w = await mountView()
    expect(w.text()).toContain('立即领取')
  })

  it('**后端说不能领的券不给领取入口**（canTake 由后端判定）', async () => {
    listCouponTemplates.mockResolvedValue([template({ canTake: false })])
    const w = await mountView()
    expect(w.text()).toContain('已领取')
    expect(w.text()).not.toContain('立即领取')
  })

  it('没有可领的券时给空状态', async () => {
    listCouponTemplates.mockResolvedValue([])
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
  })

  it('加载失败给可重试的错误态，不白屏（FR-045）', async () => {
    listCouponTemplates.mockRejectedValue(new Error('Network Error'))
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('重新加载')
  })
})

describe('CouponCenterView —— 领取（FR-026c / SC-014）', () => {
  /** 领取要登录态。未登录的那条路径单独测（见本文件的最后一组） */
  async function mountLoggedIn() {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    return mountView()
  }

  it('领取时按**模板编号**调用领取接口', async () => {
    const w = await mountLoggedIn()
    await w.get('.cc-take').trigger('click')
    await flushPromises()
    expect(takeCoupon).toHaveBeenCalledWith(1024)
  })

  it('**重复领取不产生第二张凭证**：领过之后入口即变为「已领取」', async () => {
    const w = await mountLoggedIn()
    await w.get('.cc-take').trigger('click')
    await flushPromises()

    // 卡片只有一张，且不再是可领状态
    expect(w.findAll('.cc-card')).toHaveLength(1)
    expect(w.text()).toContain('已领取')
    expect(w.find('.cc-take').exists()).toBe(false)
  })

  it('**已领取后不再发第二次领取请求**（重复点击不该重复领）', async () => {
    const w = await mountLoggedIn()
    await w.get('.cc-take').trigger('click')
    await flushPromises()
    expect(takeCoupon).toHaveBeenCalledTimes(1)

    // 再点一次（此时入口已变成「已领取」，即使误点也不该再发请求）
    const again = w.find('.cc-take')
    if (again.exists()) await again.trigger('click')
    await flushPromises()
    expect(takeCoupon).toHaveBeenCalledTimes(1)
  })

  it('**可以连着领不同的券** —— 领完第一张不该把后续领取也卡住', async () => {
    listCouponTemplates.mockResolvedValue([
      template({ id: 1024, name: '券甲' }),
      template({ id: 1025, name: '券乙' }),
    ])
    const w = await mountLoggedIn()

    await w.findAll('.cc-take')[0].trigger('click')
    await flushPromises()
    expect(takeCoupon).toHaveBeenNthCalledWith(1, 1024)

    await w.findAll('.cc-take')[0].trigger('click')
    await flushPromises()
    expect(takeCoupon).toHaveBeenNthCalledWith(2, 1025)

    // 两张都变成已领取
    expect(w.findAll('.cc-take')).toHaveLength(0)
    expect(w.text()).toContain('券甲')
    expect(w.text()).toContain('券乙')
  })

  it('领取失败时给出提示，且仍可再试', async () => {
    takeCoupon.mockRejectedValue({ message: '该券已抢光' })
    const w = await mountLoggedIn()
    await w.get('.cc-take').trigger('click')
    await flushPromises()
    expect(toastText()).toContain('该券已抢光')
    expect(w.find('.cc-take').exists()).toBe(true)
  })
})

describe('CouponCenterView —— 未登录点领取先引导登录（§3.7 状态）', () => {
  it('**不发领取请求**，而是挂上 ?login=1 走全站唯一的登录弹层', async () => {
    await router.push('/coupon')
    const w = await mountView()

    await w.get('.cc-take').trigger('click')
    await flushPromises()

    expect(takeCoupon).not.toHaveBeenCalled()
    expect(router.currentRoute.value.query.login).toBe('1')
    expect(router.currentRoute.value.query.redirect).toBe('/coupon')
  })
})
