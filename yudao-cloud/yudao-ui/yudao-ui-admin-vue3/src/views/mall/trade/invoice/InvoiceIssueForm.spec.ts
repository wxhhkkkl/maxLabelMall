import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { INVOICE_STATUS } from '@/utils/invoice'

/**
 * 开具发票弹窗（006 / T401）
 *
 * 钉三件事：
 * · **没选文件不许提交**（提交了个空 URL，后端会拒；但更糟的是"看起来成功了"）
 * · **没有 `trade:order-invoice:issue` 权限时，上传控件不可用** —— 这是"接口能调、按钮不显示"
 *   那道坑的**前端**一半（后端那一半是 `@PreAuthorize`，见服务端测试）
 * · 提交成功**通知父组件刷新**（列表以服务端为准）
 *
 * ⚠️ **故意不 mock `useMessage`**（与 003 的 FilterDimensionDrawer.spec.ts 同一理由：
 * 按自己假设的形状去 mock，测的是假设而不是现实 —— 那边真踩过，组件拿到 undefined 而测试全绿）。
 */
const issueInvoice = vi.fn()
vi.mock('@/api/mall/trade/invoice/index', () => ({
  issueInvoice: (...a: unknown[]) => issueInvoice(...a),
  getInvoicePage: vi.fn()
}))

const checkPermi = vi.fn()
vi.mock('@/utils/permission', () => ({
  checkPermi: (...a: unknown[]) => checkPermi(...a)
}))

const InvoiceIssueForm = (await import('./InvoiceIssueForm.vue')).default
const UploadFile = (await import('@/components/UploadFile/src/UploadFile.vue')).default

const ROW = {
  id: 77,
  orderNo: 'NO-20261015-0001',
  titleName: '南京路桥机械有限公司',
  amount: 7800,
  status: INVOICE_STATUS.WAITING
}

async function mountForm() {
  const w = mount(InvoiceIssueForm)
  // 走组件自己的 open()，与列表页的用法一致
  ;(w.vm as unknown as { open: (row: typeof ROW) => void }).open(ROW)
  await flushPromises()
  return w
}

beforeEach(() => {
  issueInvoice.mockReset()
  issueInvoice.mockResolvedValue(true)
  checkPermi.mockReset()
  checkPermi.mockReturnValue(true)
})

describe('开具发票弹窗', () => {
  it('没选文件时提交 → 不发请求，且弹窗不关（不谎报成功）', async () => {
    const w = await mountForm()
    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(issueInvoice).not.toHaveBeenCalled()
    expect(w.emitted('success')).toBeUndefined()
  })

  it('选了文件后提交：带上申请编号与文件地址，并通知父组件刷新', async () => {
    const w = await mountForm()
    w.findComponent(UploadFile).vm.$emit('update:modelValue', 'https://img.yuwangchenfa.com/invoice/77.pdf')
    await w.vm.$nextTick()

    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(issueInvoice).toHaveBeenCalledWith(77, 'https://img.yuwangchenfa.com/invoice/77.pdf')
    expect(w.emitted('success')).toHaveLength(1)
  })

  it('**没有开具权限时，上传控件不可用**（后端还有一道 @PreAuthorize）', async () => {
    checkPermi.mockReturnValue(false)
    const w = await mountForm()

    expect(w.findComponent(UploadFile).props('disabled')).toBe(true)
  })

  it('有权限时上传控件可用', async () => {
    const w = await mountForm()

    expect(w.findComponent(UploadFile).props('disabled')).toBe(false)
  })

  it('已上传的文件**只显示时间戳短名**（原始名是 URL 编码的长文件名，看不懂也挤不下）', async () => {
    // 2026-10-15 所有者反馈：上传后列表里显示的是
    // `%E5%BE%AE%E4%BF%A1%E5%9B%BE%E7%89%87_20261012143256_109_114.png` 这一长串
    const w = await mountForm()
    w.findComponent(UploadFile).vm.$emit(
      'update:modelValue',
      'https://img.yuwangchenfa.com/20261012/%E5%BE%AE%E4%BF%A1%E5%9B%BE%E7%89%87_20261012143256_109_114.png'
    )
    await w.vm.$nextTick()
    await flushPromises()

    expect(w.text()).toContain('20261012143256')
    expect(w.text()).not.toContain('%E5%BE%AE')
  })

  it('每次打开都不保留上一次选中的文件（否则"传错了再打开"会以为已换好）', async () => {
    const w = await mountForm()
    ;(w.vm as unknown as { open: (row: typeof ROW & { invoiceFileUrl?: string }) => void }).open({
      ...ROW,
      invoiceFileUrl: 'https://img.yuwangchenfa.com/invoice/old.pdf'
    })
    await flushPromises()

    expect(w.findComponent(UploadFile).props('modelValue')).toBeUndefined()
  })
})
