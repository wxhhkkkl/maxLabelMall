import { InvoiceTitleKind, type InvoiceTitle } from '@/types'
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
 * 发票抬头的**新增 / 编辑**弹层（US23 / T419 / Q38）
 *
 * 四条：
 * · 抬头簿与开票申请**同一套必填**（企业要名称 + 税号；**个人只要名称**）
 * · 姓名是**所有**抬头唯一的必填项
 * · 新增与编辑走不同接口，编辑必须带上 `id`
 * · 「设为默认」是随表单一起提交的 `defaultStatus`；**`titleKind` 必须跟着一起提交**（Q38）
 */
const TITLE: InvoiceTitle = {
  id: 3,
  type: 2, // 专票（Q36 起抬头带票种）
  titleKind: InvoiceTitleKind.COMPANY, // 专票只可能是企业抬头（Q38）
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

  it('**名称 + 税号就能存**（地址/电话/开户行都选填）', async () => {
    const w = mountDialog()
    await w.get('#invoiceTitleName').setValue('南京路桥机械有限公司')
    await w.get('#invoiceTaxNo').setValue('91320104660690975E')
    await w.get('#saveInvoiceTitle').trigger('click')
    await flushPromises()

    expect(createInvoiceTitle).toHaveBeenCalledWith({
      type: 1, // 默认普票
      titleKind: 1, // 默认企业（Q38）
      titleName: '南京路桥机械有限公司',
      taxNo: '91320104660690975E',
      titleAddress: '',
      titlePhone: '',
      bankName: '',
      email: '',
      defaultStatus: false,
    })
    expect(w.emitted('submitted')).toHaveLength(1)
  })

  it('**企业**抬头缺税号存不了（2026-10-15 口径）', async () => {
    const w = mountDialog()
    await w.get('#invoiceTitleName').setValue('南京路桥机械有限公司')
    await w.get('#saveInvoiceTitle').trigger('click')

    expect(createInvoiceTitle).not.toHaveBeenCalled()
    expect(w.text()).toContain('纳税人识别号')
  })

  it('**个人抬头只填姓名就能存**（Q38：个人没有纳税人识别号），且抬头类型跟着提交', async () => {
    const w = mountDialog()
    await w.findAll('.invoice-kind')[1]!.trigger('click') // 选「个人」

    await w.get('#invoiceTitleName').setValue('张三')
    await w.get('#saveInvoiceTitle').trigger('click')
    await flushPromises()

    expect(createInvoiceTitle).toHaveBeenCalledWith({
      type: 1,
      titleKind: InvoiceTitleKind.PERSONAL,
      titleName: '张三',
      taxNo: '',
      titleAddress: '',
      titlePhone: '',
      bankName: '',
      email: '',
      defaultStatus: false,
    })
  })

  it('**专票的头**：名称 + 税号即可存，地址/电话/开户行不必填（2026-10-15 口径）', async () => {
    const w = mount(InvoiceTitleDialog, { props: { open: true, title: null, type: 2 } })

    await w.get('#invoiceTitleName').setValue('南京路桥机械有限公司')
    await w.get('#invoiceTaxNo').setValue('91320104660690975E')
    await w.get('#saveInvoiceTitle').trigger('click')
    await flushPromises()

    expect(createInvoiceTitle).toHaveBeenCalledWith(expect.objectContaining({
      type: 2,
      titleName: '南京路桥机械有限公司',
      titleAddress: '',
      titlePhone: '',
      bankName: '',
    }))
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

  it('编辑一条**个人**抬头：预填「个人」，提交时也带着它', async () => {
    // 逐项列字段提交的地方最容易漏新字段 —— 漏了 `titleKind` 后端会直接拒
    const personal: InvoiceTitle = {
      id: 9,
      type: 1,
      titleKind: InvoiceTitleKind.PERSONAL,
      titleName: '张三',
      taxNo: null,
      defaultStatus: false,
    }
    const w = mountDialog(personal)
    expect(w.findAll('.invoice-kind')[1]!.attributes('aria-checked')).toBe('true')

    await w.get('#saveInvoiceTitle').trigger('click')
    await flushPromises()

    expect(updateInvoiceTitle).toHaveBeenCalledWith(9, expect.objectContaining({
      titleKind: InvoiceTitleKind.PERSONAL,
      titleName: '张三',
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
    await w.get('#invoiceTaxNo').setValue('91320104660690975E')
    await w.get('#saveInvoiceTitle').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('发票抬头不存在')
    expect(w.emitted('submitted')).toBeUndefined()
  })
})
