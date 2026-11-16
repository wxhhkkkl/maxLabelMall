import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 修改发票信息弹窗（运营代客户改抬头）
 *
 * 钉三件事：
 * · **回填当前信息**（运营改的是"这一笔"，不是从空白开始重填）
 * · 校验与后端同一套矩阵：**企业开普票 / 专票缺税号不许提交**，个人抬头不给选专票
 * · 提交成功**通知父组件刷新**（详情页以服务端为准）
 *
 * ⚠️ 与 InvoiceIssueForm.spec.ts 一样**不 mock `useMessage`**：按自己假设的形状去 mock，
 * 测的是假设而不是现实。
 */
const updateInvoice = vi.fn()
vi.mock('@/api/mall/trade/invoice/index', () => ({
  updateInvoice: (...a: unknown[]) => updateInvoice(...a),
  getInvoicePage: vi.fn(),
  issueInvoice: vi.fn()
}))

const InvoiceEditForm = (await import('./InvoiceEditForm.vue')).default

const ROW = {
  id: 77,
  orderId: 1,
  orderNo: 'NO-20261015-0001',
  type: 1,
  titleKind: 1,
  titleName: '南京路桥机械有限公司',
  taxNo: '91320104660690975E',
  titleAddress: '南京市玄武区 xx 路 1 号',
  titlePhone: '025-88886666',
  bankName: '工商银行南京分行',
  bankAccount: '6222021000000000000',
  bankCode: '102301000123',
  email: 'finance@example.com',
  status: 0
}

async function mountForm(row: typeof ROW = ROW) {
  const w = mount(InvoiceEditForm)
  await (w.vm as unknown as { open: (r: typeof ROW) => Promise<void> }).open(row)
  await flushPromises()
  return w
}

const submit = async (w: Awaited<ReturnType<typeof mountForm>>) => {
  await w.get('.el-dialog__footer .el-button--primary').trigger('click')
  await flushPromises()
}

/**
 * 税号那一行是否被标红。
 *
 * ⚠️ 不断言红字文案：Element Plus 的 `shouldShowError` 走的是 **debounced** 状态
 * （`validateStateDebounced`），单测里要等定时器才出现；而 `.is-error` 是同步的，
 * 且它已经足以证明"这条规则拦住了提交"。
 */
const taxNoItem = (w: Awaited<ReturnType<typeof mountForm>>) =>
  w.findAll('.el-form-item').find((item) => item.text().includes('纳税人识别号'))!

beforeEach(() => {
  updateInvoice.mockReset()
  updateInvoice.mockResolvedValue(true)
})

describe('修改发票信息弹窗', () => {
  it('打开时回填当前的开票信息（运营改的是这一笔，不是重填）', async () => {
    const w = await mountForm()

    expect(w.text()).toContain('NO-20261015-0001')
    expect((w.get('input[placeholder="公司名或个人名"]').element as HTMLInputElement).value).toBe(
      '南京路桥机械有限公司'
    )
    expect(
      (w.get('input[placeholder*="15 / 18"]').element as HTMLInputElement).value
    ).toBe('91320104660690975E')
  })

  it('提交：带上申请编号与全部可改字段，并通知父组件刷新', async () => {
    const w = await mountForm()

    await submit(w)

    expect(updateInvoice).toHaveBeenCalledWith(
      expect.objectContaining({
        id: 77,
        type: 1,
        titleKind: 1,
        titleName: '南京路桥机械有限公司',
        taxNo: '91320104660690975E',
        email: 'finance@example.com',
        bankAccount: '6222021000000000000'
      })
    )
    expect(w.emitted('success')).toHaveLength(1)
  })

  it('企业开普票：税号被清空 → 不许提交（与后端 InvoiceValidator 同一套矩阵）', async () => {
    const w = await mountForm()

    await w.get('input[placeholder*="15 / 18"]').setValue('')
    await submit(w)

    expect(updateInvoice).not.toHaveBeenCalled()
    expect(taxNoItem(w).classes()).toContain('is-error')
  })

  it('税号格式不对 → 不许提交', async () => {
    const w = await mountForm()

    await w.get('input[placeholder*="15 / 18"]').setValue('abc')
    await submit(w)

    expect(updateInvoice).not.toHaveBeenCalled()
    expect(taxNoItem(w).classes()).toContain('is-error')
  })

  it('抬头改成「个人」：专票不可选，并自动回落到普票', async () => {
    const w = await mountForm({ ...ROW, type: 2 })

    // 5 个 radio：票据类型 3 个 + 抬头类型 2 个
    const radios = w.findAll('input[type="radio"]')
    const input = (node: (typeof radios)[number]) => node.element as HTMLInputElement
    expect(input(radios[1]).checked).toBe(true) // 进来是专票
    await radios[4].setValue() // 选「个人」

    expect(input(radios[0]).checked).toBe(true) // 自动回落成普票
    expect(input(radios[1]).disabled).toBe(true) // 专票灰掉
  })
})
