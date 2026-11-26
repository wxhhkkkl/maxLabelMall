import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import type { Sku, Spu } from '@/api/mall/product/spu'
import SkuList from './SkuList.vue'

/**
 * SKU 价格填写（2026-11-25）
 *
 * 运营只填销售价时，市场价/成本价未填（空或 0）应自动补齐为同价；
 * 已经单独填过的值不得被覆盖。
 */

vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ warning: vi.fn(), error: vi.fn() })
}))

async function mountSkuList(skus: Sku[]) {
  const formData = { specType: false, subCommissionType: false, skus } as Spu
  const wrapper = mount(SkuList, { props: { propFormData: formData } })
  await flushPromises()
  await nextTick()
  return { wrapper, formData }
}

function salesPriceInput(wrapper: ReturnType<typeof mount>) {
  const numbers = wrapper.findAllComponents({ name: 'ElInputNumber' })
  expect(numbers.length, '找不到销售价/市场价/成本价输入框').toBeGreaterThanOrEqual(3)
  return numbers[0] // 编辑表格列顺序：销售价 → 市场价 → 成本价
}

async function changeSalesPrice(wrapper: ReturnType<typeof mount>, value?: number) {
  const input = salesPriceInput(wrapper)
  if (value !== undefined) input.vm.$emit('update:modelValue', value)
  input.vm.$emit('change', value)
  await nextTick()
}

describe('SkuList —— 填写销售价自动补齐市场价/成本价', () => {
  it('两项都未填时：市场价、成本价都补成销售价', async () => {
    const { wrapper, formData } = await mountSkuList([{ price: 0, marketPrice: 0, costPrice: 0 }])

    await changeSalesPrice(wrapper, 12.9)

    expect(formData.skus![0]).toMatchObject({ price: 12.9, marketPrice: 12.9, costPrice: 12.9 })
  })

  it('已单独填过市场价：只补成本价，不覆盖市场价', async () => {
    const { wrapper, formData } = await mountSkuList([{ price: 0, marketPrice: 199, costPrice: 0 }])

    await changeSalesPrice(wrapper, 50)

    expect(formData.skus![0]).toMatchObject({ price: 50, marketPrice: 199, costPrice: 50 })
  })

  it('清空销售价（change 收到 undefined）：不改动市场价/成本价', async () => {
    const { wrapper, formData } = await mountSkuList([{ price: 0, marketPrice: 0, costPrice: 0 }])

    await changeSalesPrice(wrapper, undefined)

    expect(formData.skus![0]).toMatchObject({ marketPrice: 0, costPrice: 0 })
  })
})
