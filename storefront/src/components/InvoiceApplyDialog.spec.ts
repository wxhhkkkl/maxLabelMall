import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const createOrderInvoice = vi.fn()
vi.mock('@/api/invoice', () => ({
  createOrderInvoice: (...a: unknown[]) => createOrderInvoice(...a),
  listInvoiceTitles: vi.fn(),
  createInvoiceTitle: vi.fn(),
  updateInvoiceTitle: vi.fn(),
  deleteInvoiceTitle: vi.fn(),
}))

const InvoiceApplyDialog = (await import('./InvoiceApplyDialog.vue')).default

/**
 * 订单详情**补开**弹层（US20 / T393）。
 *
 * 三条：
 * · 必填不全**不发请求**，并点名缺哪一项（FR-156）
 * · 填全后请求体带上 `orderId` 与抬头字段，成功后**通知父组件**（父组件负责重拉详情 —— 状态一律来自后端）
 * · 后端报错时**原样透出**它的原因（例如"订单已取消，无法申请开票"），且不谎报成功
 */
function mountDialog() {
  return mount(InvoiceApplyDialog, { props: { open: true, orderId: 1001 } })
}

beforeEach(() => {
  createOrderInvoice.mockReset()
  createOrderInvoice.mockResolvedValue(77)
})

describe('订单详情补开票弹层', () => {
  it('抬头必填不全 → 不发请求，并点名缺哪一项', async () => {
    const w = mountDialog()
    await w.get('#submitInvoice').trigger('click')

    expect(createOrderInvoice).not.toHaveBeenCalled()
    expect(w.text()).toContain('请填写抬头名称')
  })

  it('填全后提交：请求体带 orderId 与抬头字段，成功后通知父组件', async () => {
    const w = mountDialog()
    await w.get('#invoiceTitleName').setValue('南京路桥机械有限公司')
    await w.get('#invoiceTaxNo').setValue('91320104660690975E')
    await w.get('#submitInvoice').trigger('click')
    await flushPromises()

    expect(createOrderInvoice).toHaveBeenCalledWith({
      orderId: 1001,
      type: 1,
      titleName: '南京路桥机械有限公司',
      taxNo: '91320104660690975E',
      titleAddress: '',
      titlePhone: '',
      bankName: '',
    })
    expect(w.emitted('submitted')).toHaveLength(1)
    // 成功后自己关掉，父组件不必再管
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('后端拒绝时**原样透出**它的原因，且不谎报成功', async () => {
    createOrderInvoice.mockRejectedValue(new Error('订单已取消，无法申请开票'))
    const w = mountDialog()
    await w.get('#invoiceTitleName').setValue('张三')
    await w.get('#submitInvoice').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('订单已取消，无法申请开票')
    expect(w.emitted('submitted')).toBeUndefined()
  })

  it('提交中连点只发一次（幂等保护）', async () => {
    let resolve!: (v: number) => void
    createOrderInvoice.mockReturnValue(new Promise<number>((r) => { resolve = r }))
    const w = mountDialog()
    await w.get('#invoiceTitleName').setValue('张三')

    await w.get('#submitInvoice').trigger('click')
    await w.get('#submitInvoice').trigger('click')
    resolve(77)
    await flushPromises()

    expect(createOrderInvoice).toHaveBeenCalledTimes(1)
  })
})
