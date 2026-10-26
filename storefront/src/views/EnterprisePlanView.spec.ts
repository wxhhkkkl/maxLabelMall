import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.hoisted(() => vi.fn())
const route = vi.hoisted(() => ({ query: {} as Record<string, string> }))
const getMyOrg = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({ useRouter: () => ({ push }), useRoute: () => route }))
vi.mock('@/api/enterprisePlan', () => ({ getEnterprisePlan: vi.fn() }))
vi.mock('@/api/org', () => ({ getMyOrg: (...a: unknown[]) => getMyOrg(...a) }))

import { getEnterprisePlan } from '@/api/enterprisePlan'
import EnterprisePlanView from './EnterprisePlanView.vue'

const loadPlan = vi.mocked(getEnterprisePlan)

beforeEach(() => {
  loadPlan.mockReset()
  getMyOrg.mockReset()
  getMyOrg.mockResolvedValue({ orgId: 88, seatTotal: 0 })
  push.mockReset()
  route.query = {}
})

describe('企业商品详情', () => {
  it('展示价格与合计，并带数量进入结算', async () => {
    loadPlan.mockResolvedValue({
      spuId: 501,
      skuId: 601,
      name: 'MaxLabel 企业授权',
      periodMonths: 12,
      price: 99000,
      stock: 99,
      description: '<p>测试描述</p>',
    })
    const view = mount(EnterprisePlanView, { props: { skuId: '601' } })
    await flushPromises()

    expect(view.text()).toContain('MaxLabel 企业授权')
    expect(view.text()).toContain('1 年')
    expect(view.text()).toContain('¥990.00')
    expect(view.text()).toContain('企业授权订单若开票')
    // 富文本按 HTML 渲染，不出现字面标签
    expect(view.text()).toContain('测试描述')
    expect(view.text()).not.toContain('<p>')

    await view.find('button.ent-plan-buy').trigger('click')
    expect(push).toHaveBeenCalledWith({
      path: '/checkout',
      query: { source: 'product', skuId: '601', count: '1', enterprise: '1' },
    })
  })

  it('当前有席位时，数量默认与最小值都不低于当前席位数', async () => {
    loadPlan.mockResolvedValue({
      spuId: 501,
      skuId: 601,
      name: 'MaxLabel 企业授权',
      periodMonths: 12,
      price: 99000,
      stock: 99,
    })
    getMyOrg.mockResolvedValue({ orgId: 88, seatTotal: 2 })

    const view = mount(EnterprisePlanView, { props: { skuId: '601' } })
    await flushPromises()

    const input = view.find('input[type="number"]')
    expect((input.element as HTMLInputElement).value).toBe('2')
    expect(input.attributes('min')).toBe('2')
    expect(view.text()).toContain('当前 2 个席位')
  })

  it('续费入口显示「续费」', async () => {
    route.query = { renew: '1' }
    loadPlan.mockResolvedValue({
      spuId: 501,
      skuId: 601,
      name: 'MaxLabel 企业授权',
      periodMonths: 12,
      price: 99000,
      stock: 99,
    })

    const view = mount(EnterprisePlanView, { props: { skuId: '601' } })
    await flushPromises()
    expect(view.find('button.ent-plan-buy').text()).toBe('续费')
  })

  it('少于当前席位时不允许购买', async () => {
    loadPlan.mockResolvedValue({
      spuId: 501,
      skuId: 601,
      name: 'MaxLabel 企业授权',
      periodMonths: 12,
      price: 99000,
      stock: 99,
    })
    getMyOrg.mockResolvedValue({ orgId: 88, seatTotal: 2 })

    const view = mount(EnterprisePlanView, { props: { skuId: '601' } })
    await flushPromises()
    await view.find('input[type="number"]').setValue('1')
    await view.find('button.ent-plan-buy').trigger('click')

    expect(push).not.toHaveBeenCalled()
    expect(view.text()).toContain('购买席位数不能少于当前 2 个')
  })

  it('加载失败有明确提示', async () => {
    loadPlan.mockRejectedValue(new Error('网络错误'))
    const view = mount(EnterprisePlanView, { props: { skuId: '601' } })
    await flushPromises()
    expect(view.text()).toContain('网络错误')
  })
})
