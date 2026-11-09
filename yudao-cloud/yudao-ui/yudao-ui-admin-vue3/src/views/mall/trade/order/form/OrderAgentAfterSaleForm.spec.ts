import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const agentCreate = vi.fn()
vi.mock('@/api/mall/trade/afterSale', () => ({
  agentCreate: (...args: unknown[]) => agentCreate(...args)
}))

// 上传组件不在本用例真正上传；避免 axios 初始化去拉配置
vi.mock('@/config/axios', () => ({
  default: {
    get: async () => [],
    post: async () => null,
    postOriginal: async () => null,
    delete: async () => null,
    put: async () => null,
    download: async () => null
  }
}))

const Form = (await import('./OrderAgentAfterSaleForm.vue')).default

describe('OrderAgentAfterSaleForm（017）', () => {
  beforeEach(() => {
    agentCreate.mockReset()
    agentCreate.mockResolvedValue(9001)
  })

  it('打开时默认选中第一个未售后的订单项，并按实付金额作为退款上限', async () => {
    const wrapper = mount(Form, {
      global: {
        stubs: {
          Dialog: { template: '<div><slot /><slot name="footer" /></div>' }
        }
      }
    })
    ;(wrapper.vm as any).open({
      id: 1,
      items: [
        { id: 11, spuName: '已退款项', payPrice: 5000, afterSaleStatus: 20 },
        { id: 12, spuName: '可退项', payPrice: 7000, afterSaleStatus: 0 }
      ]
    })
    await flushPromises()

    // 订单项下拉默认选中 id=12（第一个 afterSaleStatus=0）
    expect(wrapper.text()).toContain('可退项')
    expect(wrapper.text()).toContain('最多 70.00 元')
  })

  it('提交时按分转换金额，并带上原因与凭证', async () => {
    const wrapper = mount(Form, {
      global: {
        stubs: {
          Dialog: { template: '<div><slot /><slot name="footer" /></div>' }
        }
      }
    })
    ;(wrapper.vm as any).open({
      id: 1,
      items: [{ id: 12, spuName: '可退项', payPrice: 7000, afterSaleStatus: 0 }]
    })
    await flushPromises()

    const reasonItem = wrapper
      .findAll('.el-form-item')
      .find((item) => item.text().includes('退款原因'))
    expect(reasonItem).toBeTruthy()
    await reasonItem!.find('input').setValue('不想要了')

    const submit = wrapper.findAll('button').find((button) => button.text().includes('提交'))
    expect(submit).toBeTruthy()
    await submit!.trigger('click')
    await flushPromises()

    expect(agentCreate).toHaveBeenCalledTimes(1)
    expect(agentCreate).toHaveBeenCalledWith({
      orderItemId: 12,
      way: 1,
      refundPrice: 7000,
      applyReason: '不想要了',
      applyDescription: undefined,
      applyPicUrls: undefined
    })
  })
})
