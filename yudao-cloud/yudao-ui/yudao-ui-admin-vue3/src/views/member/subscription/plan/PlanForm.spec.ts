import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/mall/product/spu', () => ({
  getSpuSimpleList: vi.fn(async () => []),
  getSpu: vi.fn()
}))
vi.mock('@/api/mall/product/subscriptionPlan', () => ({
  createSubscriptionPlan: vi.fn(),
  updateSubscriptionPlan: vi.fn()
}))

import { getSpu } from '@/api/mall/product/spu'
import PlanForm from './PlanForm.vue'

const getSpuMock = vi.mocked(getSpu)

beforeEach(() => {
  getSpuMock.mockReset()
})

/**
 * 表单整体装在 Dialog 里，未打开时 DOM 是空的，所以这里断言的是驱动提示的那条规则。
 */
type PlanFormVm = {
  formData: { scope: number; spuId: number }
  loadSkuOptions: (spuId?: number) => Promise<void>
  scopeConflictHint: string
}

async function drive(scope: number, enterpriseOnly: boolean): Promise<PlanFormVm> {
  getSpuMock.mockResolvedValue({
    id: 300,
    name: '商品',
    enterpriseOnly,
    skus: [{ id: 301, price: 99000, stock: 99 }]
  })
  const wrapper = mount(PlanForm, { global: { plugins: [] } })
  const vm = wrapper.vm as unknown as PlanFormVm
  vm.formData.scope = scope
  await vm.loadSkuOptions(300)
  await flushPromises()
  return vm
}

describe('会员授权计划表单 · 类别与商品一致性', () => {
  it('企业类别选到未勾「企业专享」的商品时提示', async () => {
    const vm = await drive(2, false)
    expect(vm.scopeConflictHint).toBe('企业类别必须选择勾选了「企业专享」的商品')
  })

  it('个人类别选到企业专享商品时提示', async () => {
    const vm = await drive(1, true)
    expect(vm.scopeConflictHint).toBe('个人类别不能选择勾选了「企业专享」的商品')
  })

  it('类别与商品一致时没有提示', async () => {
    const vm = await drive(2, true)
    expect(vm.scopeConflictHint).toBe('')
  })
})
