import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({
  path: '/membership',
  fullPath: '/membership',
  query: {} as Record<string, string>,
}))
const auth = vi.hoisted(() => ({ token: '' }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
  useRoute: () => route,
  RouterLink: { template: '<a><slot /></a>' },
}))
vi.mock('@/api/subscription', () => ({ listSubscriptionPlans: vi.fn(), getMySubscription: vi.fn() }))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue({ id: 1, mobile: '13800000000', nickname: '测试会员' }),
  logout: vi.fn(),
}))
vi.mock('@/utils/auth', () => ({
  getAccessToken: () => auth.token,
  setTokens: vi.fn(),
  clearTokens: vi.fn(),
}))

import { getMySubscription, listSubscriptionPlans } from '@/api/subscription'
import MembershipView from './MembershipView.vue'

const listPlans = vi.mocked(listSubscriptionPlans)
const loadSubscription = vi.mocked(getMySubscription)

const PLANS = [
  { spuId: 929, skuId: 514, name: 'MaxLabel 会员授权（年）', edition: 'professional', periodMonths: 12, price: 100 },
  { spuId: 929, skuId: 515, name: 'MaxLabel 会员授权（月）', edition: 'professional', periodMonths: 1, price: 10 },
]

beforeEach(() => {
  auth.token = ''
  push.mockReset()
  route.query = {}
  listPlans.mockReset()
  listPlans.mockResolvedValue(PLANS)
  loadSubscription.mockReset()
})

function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(MembershipView, { global: { plugins: [pinia] } })
}

describe('个人续费页', () => {
  it('展示当前专业版状态与到期时间，周期按钮直达结算页', async () => {
    auth.token = 'token'
    loadSubscription.mockResolvedValue({
      active: true,
      edition: 'professional',
      endDate: '2027-10-26',
      remainingDays: 365,
      sourceType: 1,
    })

    const view = mountView()
    await flushPromises()

    expect(view.text()).toContain('专业版')
    expect(view.text()).toContain('2027-10-26')
    expect(view.text()).toContain('剩余 365 天')
    expect(view.text()).toContain('1 年')

    await view.find('[data-testid="membership-renew-514"]').trigger('click')
    expect(push).toHaveBeenCalledWith({
      path: '/checkout',
      query: { source: 'product', skuId: '514', count: '1' },
    })
  })

  it('未登录时提示登录查看，且不请求订阅状态', async () => {
    const view = mountView()
    await flushPromises()

    expect(view.text()).toContain('登录后查看当前订阅')
    expect(loadSubscription).not.toHaveBeenCalled()
    // 对比表仍然可见
    expect(view.text()).toContain('标准版')
    expect(view.text()).toContain('数据库数据源')

    await view.find('[data-testid="membership-login"]').trigger('click')
    expect(push).toHaveBeenCalledWith({
      path: '/membership',
      query: { login: '1', redirect: '/membership' },
    })
  })

  it('已登录但未订阅专业版时显示标准版与升级入口', async () => {
    auth.token = 'token'
    loadSubscription.mockResolvedValue({ active: false, edition: null, remainingDays: 0 })

    const view = mountView()
    await flushPromises()

    expect(view.text()).toContain('标准版')
    expect(view.text()).toContain('尚未开通专业版')
    expect(view.find('[data-testid="membership-renew-515"]').exists()).toBe(true)
    expect(view.text()).toContain('升级到专业版')
  })

  it('企业版提示联系管理员，个人续费按钮被禁用', async () => {
    auth.token = 'token'
    loadSubscription.mockResolvedValue({
      active: true,
      edition: 'enterprise',
      endDate: '2027-10-26',
      remainingDays: 365,
      sourceType: 2,
      orgName: '北京杏林康云',
    })

    const view = mountView()
    await flushPromises()

    expect(view.text()).toContain('企业版')
    expect(view.text()).toContain('请联系企业管理员')
    expect(view.find('[data-testid="membership-renew-514"]').attributes('disabled')).toBeDefined()
  })
})
