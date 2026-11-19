import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 赠送对象选择（020 / Q162）
 *
 * 钉两件事：
 * · **没有授权的新客户也出现在列表里**（赠送是独立入口，不再只能从客户明细点进去）
 * · 筛选参数（客户类型 / 关键字）原样传给后端；选中后抛出 select 事件
 */
const getLicenseGiftTargetPage = vi.fn()
vi.mock('@/api/member/license', () => ({
  getLicenseGiftTargetPage: (...a: unknown[]) => getLicenseGiftTargetPage(...a),
  getLicenseSummary: vi.fn(),
  getLicenseCustomerPage: vi.fn(),
  getLicenseGrantPage: vi.fn(),
  grantLicense: vi.fn()
}))

const GiftTargetPicker = (await import('./GiftTargetPicker.vue')).default

const ROWS = [
  {
    targetType: 1,
    targetId: 88,
    targetName: '新会员',
    targetMobile: '13700008888',
    status: 0,
    endTime: undefined,
    remainingDays: 0,
    active: false,
    maxGiftMonths: 12
  },
  {
    targetType: 2,
    targetId: 9,
    targetName: '停用企业',
    status: 1,
    endTime: new Date(2027, 0, 1).getTime(),
    remainingDays: 200,
    active: true,
    maxGiftMonths: 5
  }
]

const mountPicker = async () => {
  const w = mount(GiftTargetPicker, { global: { plugins: [createPinia()] } })
  ;(w.vm as unknown as { open: () => void }).open()
  await flushPromises()
  return w
}

beforeEach(() => {
  getLicenseGiftTargetPage.mockReset()
  getLicenseGiftTargetPage.mockResolvedValue({ list: ROWS, total: ROWS.length })
})

describe('赠送对象选择弹窗', () => {
  it('从未有过授权的个人也出现在列表里，并标注「无授权」', async () => {
    const w = await mountPicker()

    expect(w.text()).toContain('新会员')
    expect(w.text()).toContain('无有效授权')
    expect(w.text()).toContain('无授权')
  })

  it('表格限高、内部滚动（2026-11-18）：弹窗不能被 10 行撑到视口外、分页被裁', async () => {
    const w = await mountPicker()

    const table = w.findComponent({ name: 'ElTable' })
    expect(table.exists()).toBe(true)
    expect(table.props('maxHeight')).toBe('calc(100vh - 320px)')
  })

  it('停用企业照常列出并标注「已停用」（赠送时由服务端提示先启用）', async () => {
    const w = await mountPicker()

    expect(w.text()).toContain('停用企业')
    expect(w.text()).toContain('已停用')
  })

  it('点「赠送」抛出选中对象，交给赠送弹窗', async () => {
    const w = await mountPicker()

    const button = w.findAll('button').find((b) => b.text() === '赠送')!
    expect(button).toBeTruthy()
    await button.trigger('click')

    expect(w.emitted('select')).toHaveLength(1)
    expect(w.emitted('select')![0][0]).toEqual(
      expect.objectContaining({ targetId: 88, maxGiftMonths: 12 })
    )
  })

  it('关键字筛选时参数原样传给后端，并从第 1 页开始查', async () => {
    const w = await mountPicker()
    getLicenseGiftTargetPage.mockClear()

    const keywordInput = w.get('input[placeholder="昵称 / 手机号 / 企业名"]')
    await keywordInput.setValue('南京')
    await keywordInput.trigger('keyup.enter')
    await flushPromises()

    expect(getLicenseGiftTargetPage).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: '南京', pageNo: 1 })
    )
  })
})
