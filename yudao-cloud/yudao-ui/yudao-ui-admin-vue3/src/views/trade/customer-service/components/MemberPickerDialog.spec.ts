import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 客服代建（021 / US75）
 *
 * 钉两件事：会员检索可选择；未选会员时代建弹窗不提交。
 */
const searchCustomerServiceMembers = vi.fn()
const createCustomerServiceTicket = vi.fn()
vi.mock('@/api/trade/customerService', () => ({
  searchCustomerServiceMembers: (...a: unknown[]) => searchCustomerServiceMembers(...a),
  createCustomerServiceTicket: (...a: unknown[]) => createCustomerServiceTicket(...a),
  createCustomerServiceRepair: vi.fn(),
  getCustomerServiceTicketPage: vi.fn(),
  getCustomerServiceTicket: vi.fn(),
  handleCustomerServiceTicket: vi.fn(),
  getCustomerServiceRepairPage: vi.fn(),
  getCustomerServiceRepair: vi.fn(),
  recordCustomerServiceRepairContact: vi.fn(),
  createCustomerServiceRepairOrder: vi.fn(),
  updateCustomerServiceRepairStatus: vi.fn(),
  cancelCustomerServiceRepair: vi.fn(),
  confirmCustomerServiceRepairOffline: vi.fn(),
  getCustomerServiceAddress: vi.fn(),
  updateCustomerServiceAddress: vi.fn()
}))

const MemberPickerDialog = (await import('./MemberPickerDialog.vue')).default
const TicketCreateDialog = (await import('./TicketCreateDialog.vue')).default

const MEMBER = { id: 88, nickname: '张三', mobile: '13800008888' }

const mountOptions = { global: { plugins: [createPinia()], stubs: { UploadImgs: true } } }

beforeEach(() => {
  searchCustomerServiceMembers.mockReset()
  searchCustomerServiceMembers.mockResolvedValue([MEMBER])
  createCustomerServiceTicket.mockReset()
  createCustomerServiceTicket.mockResolvedValue(501)
})

describe('客服代建', () => {
  it('检索会员并选择', async () => {
    const w = mount(MemberPickerDialog, mountOptions)
    ;(w.vm as unknown as { open: () => void }).open()
    await flushPromises()
    await w.get('input').setValue('13800008888')
    await w.findAll('button').find((b) => b.text() === '搜索')!.trigger('click')
    await flushPromises()

    expect(searchCustomerServiceMembers).toHaveBeenCalledWith('13800008888')
    await w.findAll('button').find((b) => b.text() === '选择')!.trigger('click')
    expect(w.emitted('select')).toHaveLength(1)
  })

  it('未选会员时代建投诉咨询不提交', async () => {
    const w = mount(TicketCreateDialog, mountOptions)
    ;(w.vm as unknown as { open: () => void }).open()
    await flushPromises()
    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(createCustomerServiceTicket).not.toHaveBeenCalled()
  })

  it('选择会员并填写后按 memberId 提交', async () => {
    const w = mount(TicketCreateDialog, mountOptions)
    ;(w.vm as unknown as { open: () => void }).open()
    await flushPromises()
    w.findComponent(MemberPickerDialog).vm.$emit('select', MEMBER)
    await flushPromises()
    await w.get('textarea').setValue('客户来电咨询打印偏移')
    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(createCustomerServiceTicket).toHaveBeenCalledWith(
      expect.objectContaining({ memberId: 88, type: 1, contactMobile: '13800008888' })
    )
  })
})
