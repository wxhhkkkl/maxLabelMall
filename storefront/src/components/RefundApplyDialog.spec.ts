import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const createAfterSale = vi.fn()
vi.mock('@/api/afterSale', () => ({
  createAfterSale: (...a: unknown[]) => createAfterSale(...a),
}))

const RefundApplyDialog = (await import('./RefundApplyDialog.vue')).default

const { OrderStatus } = await import('@/types')

/** 一笔订单里的某个商品（金额 70 元 = 7000 分） */
const ITEM = {
  id: 11,
  spuName: '三防热敏标签纸',
  properties: [{ propertyName: '版本', valueName: '标配' }],
  payPrice: 7000,
  afterSaleStatus: 0,
}

function mountDialog(orderStatus: number = OrderStatus.UNDELIVERED, item = ITEM) {
  return mount(RefundApplyDialog, { props: { open: true, item, orderStatus } })
}

beforeEach(() => {
  createAfterSale.mockReset()
  createAfterSale.mockResolvedValue(2048)
})

/**
 * 申请退款弹层。
 *
 * ⚠️ 断言刻意落在**请求体**上（`orderItemId` 是 11 不是 1、`refundPrice` 是实付金额），
 * 不能只看界面文案 —— 这是本项目反复强调的一条。
 */
describe('RefundApplyDialog —— 售后方式', () => {
  it('未发货时「退货退款」置灰并说明原因，「仅退款」默认选中', () => {
    const w = mountDialog(OrderStatus.UNDELIVERED)
    const ways = w.findAll('.rf-way')
    expect(ways[0].attributes('data-way')).toBe('10')
    expect(ways[1].attributes('data-way')).toBe('20')
    expect(ways[0].classes()).toContain('is-active')
    expect(ways[1].classes()).toContain('is-disabled')
    expect(ways[1].attributes('disabled')).toBeDefined()
    // 置灰而不是隐藏：要让用户知道选项存在、也知道为什么不可用
    expect(w.text()).toContain('未发货')
  })

  it('已发货时「退货退款」可选，并能以 way=20 提交', async () => {
    const w = mountDialog(OrderStatus.DELIVERED)
    const way20 = w.get('[data-way="20"]')
    expect(way20.classes()).not.toContain('is-disabled')

    await way20.trigger('click')
    await w.get('#refundReason').setValue('质量问题')
    await w.get('#refundSubmit').trigger('click')
    await flushPromises()

    expect(createAfterSale).toHaveBeenCalledWith(
      expect.objectContaining({ way: 20 }),
    )
  })

  it('**未发货时点不动 way=20**，提交出去的仍是 10（双保险的第一道）', async () => {
    const w = mountDialog(OrderStatus.UNDELIVERED)
    await w.get('[data-way="20"]').trigger('click')
    await w.get('#refundReason').setValue('不想要了')
    await w.get('#refundSubmit').trigger('click')
    await flushPromises()

    expect(createAfterSale).toHaveBeenCalledWith(
      expect.objectContaining({ way: 10 }),
    )
  })
})

describe('RefundApplyDialog —— 提交', () => {
  it('**请求体带对了 orderItemId / way / refundPrice**（11 不是订单号）', async () => {
    const w = mountDialog()
    await w.get('#refundReason').setValue('不想要了')
    await w.get('#refundSubmit').trigger('click')
    await flushPromises()

    expect(createAfterSale).toHaveBeenCalledWith({
      orderItemId: 11,
      way: 10,
      refundPrice: 7000,
      applyReason: '不想要了',
    })
  })

  it('**退款原因必填** —— 空原因不发请求，并给出提示', async () => {
    const w = mountDialog()
    await w.get('#refundSubmit').trigger('click')
    await flushPromises()

    expect(createAfterSale).not.toHaveBeenCalled()
    expect(w.text()).toContain('请填写退款原因')
  })

  it('只填空格也不算填了', async () => {
    const w = mountDialog()
    await w.get('#refundReason').setValue('   ')
    await w.get('#refundSubmit').trigger('click')
    await flushPromises()
    expect(createAfterSale).not.toHaveBeenCalled()
  })

  it('退款金额只读：显示该项实付，且没有可编辑的金额控件', () => {
    const w = mountDialog()
    expect(w.text()).toContain('¥70.00')
    expect(w.find('input[type="number"]').exists()).toBe(false)
  })

  it('成功：emit submitted 与 close（由调用方负责重拉详情）', async () => {
    const w = mountDialog()
    await w.get('#refundReason').setValue('拍错了')
    await w.get('#refundSubmit').trigger('click')
    await flushPromises()

    expect(w.emitted('submitted')).toBeTruthy()
    expect(w.emitted('close')).toBeTruthy()
  })

  it('**失败：原样展示后端文案、弹层不关**（「已申请过」这类要用户看得到）', async () => {
    createAfterSale.mockRejectedValue({ message: '订单项已申请售后，无法重复申请' })
    const w = mountDialog()
    await w.get('#refundReason').setValue('拍错了')
    await w.get('#refundSubmit').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('订单项已申请售后，无法重复申请')
    expect(w.emitted('close')).toBeFalsy()
    expect(w.emitted('submitted')).toBeFalsy()
  })

  it('实付为 0 的项不发请求（后端 refundPrice 要求 > 0）', async () => {
    const w = mountDialog(OrderStatus.UNDELIVERED, { ...ITEM, payPrice: 0 })
    await w.get('#refundReason').setValue('拍错了')
    await w.get('#refundSubmit').trigger('click')
    await flushPromises()

    expect(createAfterSale).not.toHaveBeenCalled()
    expect(w.text()).toContain('无可退金额')
  })
})

describe('RefundApplyDialog —— 打开时重置', () => {
  it('关掉再打开，上一次填的原因不残留', async () => {
    const w = mountDialog()
    await w.get('#refundReason').setValue('上一次的原因')
    await w.setProps({ open: false })
    await w.setProps({ open: true })
    await flushPromises()

    expect((w.get('#refundReason').element as HTMLTextAreaElement).value).toBe('')
  })
})
