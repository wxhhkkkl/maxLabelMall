import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import OrderDeliveryFields from './OrderDeliveryFields.vue'
import OrderDeliveryPanel from './OrderDeliveryPanel.vue'

/**
 * 订单详情内联发货表单（2026-11-18）
 *
 * 钉住提交口径（与弹窗 OrderDeliveryForm 一致）：
 * · 快递发货必须有发货地址；提交的是面板里当前填的内容
 * · 「无需发货」把物流公司置 0、单号清空
 */

const getSimpleDeliveryExpressList = vi.fn()
vi.mock('@/api/mall/trade/delivery/express', () => ({
  getSimpleDeliveryExpressList: (...a: unknown[]) => getSimpleDeliveryExpressList(...a)
}))
vi.mock('@/api/system/area', () => ({
  getAreaTree: vi.fn().mockResolvedValue([])
}))
const getDeliveryDefaultAddress = vi.fn()
const deliveryOrder = vi.fn()
vi.mock('@/api/mall/trade/order', () => ({
  getDeliveryDefaultAddress: (...a: unknown[]) => getDeliveryDefaultAddress(...a),
  deliveryOrder: (...a: unknown[]) => deliveryOrder(...a)
}))

const message = vi.hoisted(() => ({ success: vi.fn(), warning: vi.fn(), error: vi.fn() }))
vi.mock('@/hooks/web/useMessage', () => ({ useMessage: () => message }))

function order(over: Record<string, unknown> = {}) {
  return {
    id: 1,
    status: 10,
    deliveryType: 1,
    logisticsId: null,
    logisticsNo: null,
    ...over
  } as any
}

async function mountPanel(o = order()) {
  getSimpleDeliveryExpressList.mockResolvedValue([{ id: 10, name: '顺丰速运' }])
  getDeliveryDefaultAddress.mockResolvedValue({ areaId: 110101, address: '北京市朝阳区 xx 路' })
  deliveryOrder.mockResolvedValue(true)
  message.warning.mockReset()

  const w = mount(OrderDeliveryPanel, { props: { order: o } })
  await flushPromises()
  return w
}

const fieldsOf = (w: any) => w.findComponent(OrderDeliveryFields)

describe('订单详情内联发货表单', () => {
  it('初始化：默认发货地址取后台配置，物流公司为空', async () => {
    const w = await mountPanel()

    const formData = fieldsOf(w).props('formData') as any
    expect(getDeliveryDefaultAddress).toHaveBeenCalled()
    expect(formData.deliveryFromAreaId).toBe(110101)
    expect(formData.deliveryFromAddress).toBe('北京市朝阳区 xx 路')
    expect(formData.logisticsId).toBeNull()
  })

  it('填好物流后提交：带上订单号、物流与发货地址', async () => {
    const w = await mountPanel()
    const formData = fieldsOf(w).props('formData') as any
    formData.logisticsId = 10
    formData.logisticsNo = 'SF1234567890'

    const submit = w.findAll('button').find((b) => b.text().includes('确认发货'))
    expect(submit).toBeTruthy()
    await submit!.trigger('click')
    await flushPromises()

    expect(deliveryOrder).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 1,
        logisticsId: 10,
        logisticsNo: 'SF1234567890',
        deliveryFromAreaId: 110101,
        deliveryFromAddress: '北京市朝阳区 xx 路'
      })
    )
    expect(w.emitted('success')).toBeTruthy()
  })

  it('快递发货但没有发货地址 → 提示且不发请求', async () => {
    const w = await mountPanel()
    const formData = fieldsOf(w).props('formData') as any
    formData.deliveryFromAreaId = null
    formData.deliveryFromAddress = ''

    const submit = w.findAll('button').find((b) => b.text().includes('确认发货'))
    await submit!.trigger('click')
    await flushPromises()

    expect(message.warning).toHaveBeenCalledWith('请填写发货地址')
    expect(deliveryOrder).not.toHaveBeenCalled()
  })

  it('选「无需发货」→ 提交时物流公司置 0、单号清空', async () => {
    const w = await mountPanel()
    const fields = fieldsOf(w)
    // 发货方式由 OrderDeliveryFields 的 defineModel 抛出
    fields.vm.$emit('update:expressType', 'none')
    await flushPromises()
    const formData = fields.props('formData') as any
    formData.logisticsNo = 'LEFT-OVER'

    const submit = w.findAll('button').find((b) => b.text().includes('确认发货'))
    await submit!.trigger('click')
    await flushPromises()

    expect(deliveryOrder).toHaveBeenCalledWith(
      expect.objectContaining({ logisticsId: 0, logisticsNo: '' })
    )
  })
})
