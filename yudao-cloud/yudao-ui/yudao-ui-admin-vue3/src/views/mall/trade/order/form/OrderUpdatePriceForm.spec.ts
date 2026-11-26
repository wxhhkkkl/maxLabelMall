import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 订单调价弹窗（2026-11-25）
 *
 * 口径：输入框是**累计调价总额**，同一未支付订单可以多次改价。
 * · 打开已调过价的订单：输入框回填当前累计值，「调价后」= 未调价前应付 + 累计调价（不能重复扣一次）
 * · 提交的是累计值（分），不是本次差额
 */

const updateOrderPrice = vi.fn()
vi.mock('@/api/mall/trade/order', () => ({
  updateOrderPrice: (...args: unknown[]) => updateOrderPrice(...args)
}))

const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))

const OrderUpdatePriceForm = (await import('./OrderUpdatePriceForm.vue')).default

/** 一笔当前应付 98 元、累计已减 10 元的订单（未改价前应付 108 元） */
function adjustedOrder() {
  return { id: 1, payPrice: 9800, adjustPrice: -1000 } as any
}

async function openForm(row = adjustedOrder()) {
  const wrapper = mount(OrderUpdatePriceForm)
  ;(wrapper.vm as any).open(row)
  await flushPromises()
  return wrapper
}

function inputsOf(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('input')
}

async function submit(wrapper: ReturnType<typeof mount>) {
  const button = wrapper.findAll('button').find((item) => item.text().replace(/\s/g, '') === '确定')
  expect(button, '找不到「确定」按钮').toBeTruthy()
  await button!.trigger('click')
  await flushPromises()
}

beforeEach(() => {
  updateOrderPrice.mockReset()
  updateOrderPrice.mockResolvedValue(true)
  message.success.mockReset()
})

describe('订单调价弹窗（累计调价）', () => {
  it('打开已调过价的订单：回填累计调价，调价后金额不会重复扣一次', async () => {
    const wrapper = await openForm()
    const inputs = inputsOf(wrapper)

    expect((inputs[0].element as HTMLInputElement).value).toContain('98.00')
    // 累计调价输入框：-10 元
    expect((inputs[1].element as HTMLInputElement).value).toContain('-10')
    // 未改价 108 元 + 累计调价 -10 元 = 98 元（旧实现会显示 88 元）
    expect((inputs[2].element as HTMLInputElement).value).toContain('98.00')
  })

  it('不改金额直接确定：提交当前累计值（分），后端按无变化处理', async () => {
    const wrapper = await openForm()

    await submit(wrapper)

    expect(updateOrderPrice).toHaveBeenCalledWith({ id: 1, adjustPrice: -1000 })
    expect(message.success).toHaveBeenCalled()
  })

  it('把累计调价从 -10 改成 -15：提交 -1500 分（累计值，不是 -500 差额）', async () => {
    const wrapper = await openForm()
    const adjustInput = inputsOf(wrapper)[1]

    await adjustInput.setValue('-15')
    await adjustInput.trigger('change')
    await flushPromises()
    // 调价后 = 108 - 15 = 93 元
    expect((inputsOf(wrapper)[2].element as HTMLInputElement).value).toContain('93.00')

    await submit(wrapper)

    expect(updateOrderPrice).toHaveBeenCalledWith({ id: 1, adjustPrice: -1500 })
  })
})
