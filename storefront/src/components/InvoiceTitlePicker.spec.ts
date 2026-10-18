import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { InvoiceTitleKind, InvoiceType, type InvoiceTitle, type InvoiceTypeValue } from '@/types'

const listInvoiceTitles = vi.fn()
const createInvoiceTitle = vi.fn()
vi.mock('@/api/invoice', () => ({
  listInvoiceTitles: (...a: unknown[]) => listInvoiceTitles(...a),
  createInvoiceTitle: (...a: unknown[]) => createInvoiceTitle(...a),
  updateInvoiceTitle: vi.fn(),
  deleteInvoiceTitle: vi.fn(),
  createOrderInvoice: vi.fn(),
}))

const InvoiceTitlePicker = (await import('./InvoiceTitlePicker.vue')).default

/**
 * 「选一条已保存的抬头 / 没有就当场新增」的选择器（Q36）。
 *
 * 三条是本轮新增或纠正的：
 * · **只列与当前票种相符**的抬头 —— 选专票时列出普票抬头，客户点了会得到一条填不全的专票信息
 * · **没有抬头时也要有「+ 新增抬头」** —— 否则第一次来的客户无路可走
 * · **就地新增后自动选中它** —— 不用先跳去个人中心再回来选
 */
const NORMAL_TITLE: InvoiceTitle = {
  id: 3, type: InvoiceType.NORMAL, titleKind: InvoiceTitleKind.COMPANY,
  titleName: '普票公司', taxNo: '91320104660690975E', defaultStatus: true,
}
const SPECIAL_TITLE: InvoiceTitle = {
  id: 4, type: InvoiceType.SPECIAL, titleKind: InvoiceTitleKind.COMPANY,
  titleName: '专票公司', taxNo: '91320104660690975E',
  titleAddress: '南京市玄武区', titlePhone: '18913984596', bankName: '工商银行', defaultStatus: true,
}
/** 个人抬头只可能落在普票那一段里（个人开不了专票） */
const PERSONAL_TITLE: InvoiceTitle = {
  id: 5, type: InvoiceType.NORMAL, titleKind: InvoiceTitleKind.PERSONAL,
  titleName: '张三', taxNo: null, defaultStatus: false,
}

function mountPicker(type: InvoiceTypeValue = InvoiceType.NORMAL, titles: InvoiceTitle[] = []) {
  listInvoiceTitles.mockResolvedValue(titles)
  return mount(InvoiceTitlePicker, { props: { type } })
}

beforeEach(() => {
  listInvoiceTitles.mockReset()
  createInvoiceTitle.mockReset()
  createInvoiceTitle.mockResolvedValue(7)
})

describe('发票抬头选择器', () => {
  it('**只列当前票种**的抬头（普票时不出现专票抬头）', async () => {
    const w = mountPicker(InvoiceType.NORMAL, [NORMAL_TITLE, SPECIAL_TITLE])
    await flushPromises()

    expect(w.text()).toContain('普票公司')
    expect(w.text()).not.toContain('专票公司')
  })

  it('切到专票 → 换成专票那批，并把选中态清掉', async () => {
    const w = mountPicker(InvoiceType.NORMAL, [NORMAL_TITLE, SPECIAL_TITLE])
    await flushPromises()
    await w.setProps({ type: InvoiceType.SPECIAL })
    await flushPromises()

    expect(w.text()).toContain('专票公司')
    expect(w.text()).not.toContain('普票公司')
  })

  it('**该票种的默认抬头被预先选中**，并把那条抛给调用方', async () => {
    const w = mountPicker(InvoiceType.NORMAL, [NORMAL_TITLE])
    await flushPromises()

    expect(w.find('.invoice-picker-item.is-active').text()).toContain('普票公司')
    expect(w.emitted('picked')?.[0]).toEqual([NORMAL_TITLE])
  })

  it('换选另一条 → 抛出那一条', async () => {
    const other: InvoiceTitle = { id: 9, type: InvoiceType.NORMAL, titleKind: InvoiceTitleKind.COMPANY, titleName: '另一家', defaultStatus: false }
    const w = mountPicker(InvoiceType.NORMAL, [NORMAL_TITLE, other])
    await flushPromises()

    await w.get('[data-invoice-title="9"]').trigger('click')

    expect(w.emitted('picked')?.at(-1)).toEqual([other])
  })

  it('**一条都没有时也有「+ 新增抬头」**（不给无路可走的空态）', async () => {
    const w = mountPicker(InvoiceType.NORMAL, [])
    await flushPromises()

    expect(w.find('.invoice-picker-item').exists()).toBe(false)
    expect(w.find('[data-add-invoice-title]').exists()).toBe(true)
  })

  it('**就地新增：存完自动选中它**（不必先跳个人中心）', async () => {
    const w = mountPicker(InvoiceType.SPECIAL, [])
    await flushPromises()
    const created: InvoiceTitle = { id: 7, type: InvoiceType.SPECIAL, titleKind: InvoiceTitleKind.COMPANY, titleName: '新公司', taxNo: '91320104660690975E', titleAddress: 'x', titlePhone: '1', bankName: 'b', defaultStatus: false }

    // 打开弹层 → 弹层提交成功（真实链路里是弹层自己调的接口）
    await w.get('[data-add-invoice-title]').trigger('click')
    listInvoiceTitles.mockResolvedValue([created])
    w.findComponent({ name: 'InvoiceTitleDialog' }).vm.$emit('submitted')
    await flushPromises()

    expect(w.get('.invoice-picker-item.is-active').text()).toContain('新公司')
    expect(w.emitted('picked')?.at(-1)).toEqual([created])
  })

  it('把**整条抬头**抛给调用方（含 `titleKind`）—— 调用方要靠它把抬头类型一起填上（Q38）', async () => {
    const w = mountPicker(InvoiceType.NORMAL, [PERSONAL_TITLE])
    await flushPromises()

    await w.get('[data-invoice-title="5"]').trigger('click')

    expect(w.emitted('picked')?.at(-1)).toEqual([PERSONAL_TITLE])
    expect(w.get('.invoice-picker-item').text()).toContain('个人') // 与「公司抬头」区分开
  })

  it('抬头列表拉不到 → 不显示条目，但**照常能手填**（不阻塞开票）', async () => {
    listInvoiceTitles.mockRejectedValue(new Error('offline'))
    const w = mount(InvoiceTitlePicker, { props: { type: InvoiceType.NORMAL } })
    await flushPromises()

    expect(w.find('.invoice-picker-item').exists()).toBe(false)
    expect(w.find('[data-add-invoice-title]').exists()).toBe(true)
  })
})
