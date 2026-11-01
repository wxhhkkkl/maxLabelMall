import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const listCustomizationMaterials = vi.fn()
const quoteCustomization = vi.fn()
const createCustomizationOrder = vi.fn()
const listAddress = vi.fn()
const uploadFile = vi.fn()

vi.mock('@/api/customization', () => ({
  listCustomizationMaterials: (...a: unknown[]) => listCustomizationMaterials(...a),
  quoteCustomization: (...a: unknown[]) => quoteCustomization(...a),
  createCustomizationOrder: (...a: unknown[]) => createCustomizationOrder(...a),
}))
vi.mock('@/api/address', () => ({
  listAddress: (...a: unknown[]) => listAddress(...a),
}))
vi.mock('@/api/upload', () => ({
  uploadFile: (...a: unknown[]) => uploadFile(...a),
}))
vi.mock('@/utils/auth', () => ({
  getAccessToken: () => 'token',
}))

const View = (await import('./CustomizationView.vue')).default

const material = {
  id: 3, name: '可移除铜版',
  minQuantity: 1, maxQuantity: 100000,
  minWidth: 10, maxWidth: 2000, minHeight: 10, maxHeight: 2000,
}

const quote = {
  materialId: 3, materialName: '可移除铜版',
  unitArea: 0.005, totalArea: 5,
  breakdown: { print: 45520, printing: 0, dieCut: 0, urgent: 0, subtotal: 45520, total: 45520 },
  freight: 800, payable: 46320, quoteTime: '2026-11-01 10:00:00',
}

function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/customization', component: View },
      { path: '/order/:id', component: { template: '<div />' } },
    ],
  })
  return mount(View, {
    global: {
      plugins: [router],
      stubs: { CheckoutInvoiceCard: true },
    },
  })
}

beforeEach(() => {
  listCustomizationMaterials.mockReset().mockResolvedValue([material])
  quoteCustomization.mockReset().mockResolvedValue(quote)
  createCustomizationOrder.mockReset()
  listAddress.mockReset().mockResolvedValue([{ id: 7, name: '张三', mobile: '13800008888', areaName: '北京', detailAddress: 'xx 路', defaultStatus: true }])
  uploadFile.mockReset().mockResolvedValue('https://img.yuwangchenfa.com/a.pdf')
})

async function pickAll(w: ReturnType<typeof mountView>): Promise<void> {
  // 单页渐进：依次点选第一步 / 第二步 / 第三步
  await w.get('#customizationMode-2').trigger('click')
  await w.get('#customizationMaterial-3').trigger('click')
  await w.get('#customizationForm-2').trigger('click')
  await flushPromises()
}

describe('CustomizationView（014 / US48）', () => {
  it('按顺序选择后出现后一步，并按默认尺寸报价', async () => {
    vi.useFakeTimers()
    const w = mountView()
    await flushPromises()
    // 未选第一步时，第二步不出现
    expect(w.find('#customizationMaterial-3').exists()).toBe(false)

    await pickAll(w)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(quoteCustomization).toHaveBeenCalledWith(expect.objectContaining({
      materialId: 3, width: 10, height: 10, quantity: 1,
      addressId: 7, deliveryType: 1, mode: 2, form: 2,
    }))
    expect(w.get('#customizationQuoteTotal').text()).toContain('463.20')
    w.unmount()
    vi.useRealTimers()
  })

  it('超过 16MB 的文件在上传前被拦下', async () => {
    const w = mountView()
    await flushPromises()
    await pickAll(w)
    const input = w.get('#customizationFile')
    const big = new File(['x'], 'big.pdf', { type: 'application/pdf' })
    Object.defineProperty(big, 'size', { value: 17 * 1024 * 1024 })
    Object.defineProperty(input.element, 'files', { value: [big], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(uploadFile).not.toHaveBeenCalled()
    expect(w.text()).toContain('16MB')
    w.unmount()
  })

  it('价格已变时展示确认弹层，不直接跳转', async () => {
    vi.useFakeTimers()
    createCustomizationOrder.mockResolvedValue({ changed: true, quote: { ...quote, payable: 47320 } })
    const w = mountView()
    await flushPromises()
    await pickAll(w)
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    uploadFile.mockResolvedValue('https://img.yuwangchenfa.com/a.pdf')
    const input = w.get('#customizationFile')
    const file = new File(['x'], 'a.pdf', { type: 'application/pdf' })
    Object.defineProperty(file, 'size', { value: 1024 })
    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()

    await w.get('#customizationSubmit').trigger('click')
    await flushPromises()

    expect(createCustomizationOrder).toHaveBeenCalledWith(expect.objectContaining({ expectedAmount: 46320 }))
    expect(w.text()).toContain('价格已更新')
    w.unmount()
    vi.useRealTimers()
  })
})
