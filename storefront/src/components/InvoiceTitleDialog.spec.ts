import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const createInvoiceTitle = vi.fn()
const updateInvoiceTitle = vi.fn()
vi.mock('@/api/invoice', () => ({
  createInvoiceTitle: (...a: unknown[]) => createInvoiceTitle(...a),
  updateInvoiceTitle: (...a: unknown[]) => updateInvoiceTitle(...a),
  listInvoiceTitles: vi.fn(),
  deleteInvoiceTitle: vi.fn(),
  createOrderInvoice: vi.fn(),
}))

const InvoiceTitleDialog = (await import('./InvoiceTitleDialog.vue')).default

/**
 * 发票抬头的**新增 / 编辑**弹层（US23 / T419）
 *
 * 三条：
 * · 抬头簿**只要求名称**（抬头可以不完整，缺项在选专票时才暴露 —— 与后端同口径）
 * · 新增与编辑走不同接口，编辑必须带上 `id`
 * · 「设为默认」是随表单一起提交的 `defaultStatus`
 */
const TITLE = {
  id: 3,
  titleName: '南京路桥机械有限公司',
  taxNo: '91320104660690975E',
  titleAddress: '南京市玄武区某某路 1 号',
  titlePhone: '18913984596',
  bankName: '工商银行南京分行 6222',
  defaultStatus: true,
}

function mountDialog(title: typeof TITLE | null = null) {
  return mount(InvoiceTitleDialog, { props: { open: true, title } })
}

beforeEach(() => {
  createInvoiceTitle.mockReset()
  createInvoiceTitle.mockResolvedValue(7)
  updateInvoiceTitle.mockReset()
  updateInvoiceTitle.mockResolvedValue(true)
})

describe('发票抬头弹层', () => {
  it('名称为空 → 不保存，并提示', async () => {
    const w = mountDialog()
    await w.get('#saveInvoiceTitle').trigger('click')

    expect(createInvoiceTitle).not.toHaveBeenCalled()
    expect(w.text()).toContain('请填写抬头名称')
  })

  it('**只填名称也能存**（抬头可以不完整）', async () => {
    const w = mountDialog()
    await w.get('#invoiceTitleName').setValue('张三')
    await w.get('#saveInvoiceTitle').trigger('click')
    await flushPromises()

    expect(createInvoiceTitle).toHaveBeenCalledWith({
      titleName: '张三',
      taxNo: '',
      titleAddress: '',
      titlePhone: '',
      bankName: '',
      defaultStatus: false,
    })
    expect(w.emitted('submitted')).toHaveLength(1)
  })

  it('编辑时预填该条抬头，并用 update 带上 id', async () => {
    const w = mountDialog(TITLE)
    expect((w.get('#invoiceTitleName').element as HTMLInputElement).value).toBe('南京路桥机械有限公司')

    await w.get('#invoiceTitleName').setValue('南京路桥机械有限公司（新）')
    await w.get('#saveInvoiceTitle').trigger('click')
    await flushPromises()

    expect(createInvoiceTitle).not.toHaveBeenCalled()
    expect(updateInvoiceTitle).toHaveBeenCalledWith(3, expect.objectContaining({
      titleName: '南京路桥机械有限公司（新）',
      defaultStatus: true,
    }))
  })

  it('新增时不带上一次编辑的内容（每次打开都重置）', async () => {
    const w = mount(InvoiceTitleDialog, { props: { open: false, title: TITLE } })
    await w.setProps({ open: true, title: null })

    expect((w.get('#invoiceTitleName').element as HTMLInputElement).value).toBe('')
  })

  it('后端拒绝时原样透出原因，且不谎报成功', async () => {
    createInvoiceTitle.mockRejectedValue(new Error('发票抬头不存在'))
    const w = mountDialog()
    await w.get('#invoiceTitleName').setValue('张三')
    await w.get('#saveInvoiceTitle').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('发票抬头不存在')
    expect(w.emitted('submitted')).toBeUndefined()
  })
})
