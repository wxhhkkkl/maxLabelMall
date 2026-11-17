import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 维修开单弹窗（021 / US72）
 *
 * 钉两件事：费用负数 / 说明为空不提交；合法输入按「元 → 分」提交。
 */
const createCustomerServiceRepairOrder = vi.fn()
vi.mock('@/api/trade/customerService', () => ({
  createCustomerServiceRepairOrder: (...a: unknown[]) => createCustomerServiceRepairOrder(...a),
  getCustomerServiceTicketPage: vi.fn(),
  getCustomerServiceTicket: vi.fn(),
  handleCustomerServiceTicket: vi.fn(),
  getCustomerServiceRepairPage: vi.fn(),
  getCustomerServiceRepair: vi.fn(),
  recordCustomerServiceRepairContact: vi.fn(),
  updateCustomerServiceRepairStatus: vi.fn(),
  cancelCustomerServiceRepair: vi.fn()
}))

const OrderCreateDialog = (await import('./OrderCreateDialog.vue')).default

const ROW = {
  id: 601,
  memberId: 88,
  memberNickname: '张三',
  deviceModel: 'Zebra ZT411',
  faultDesc: '走纸不停',
  contactMobile: '13800008888',
  receiverName: '张三',
  receiverMobile: '13800008888',
  receiverArea: '江苏省 南京市',
  receiverDetail: 'xx 路 1 号',
  status: 10,
  statusName: '已联系',
  source: 1,
  sourceName: '客户自助',
  createTime: new Date(2026, 10, 17).getTime()
}

const mountDialog = async () => {
  const w = mount(OrderCreateDialog)
  ;(w.vm as unknown as { open: (r: typeof ROW) => void }).open(ROW)
  await flushPromises()
  return w
}

beforeEach(() => {
  createCustomerServiceRepairOrder.mockReset()
  createCustomerServiceRepairOrder.mockResolvedValue({ repairOrderId: 701, payableAmount: 12000, payStatus: 0 })
})

describe('维修开单弹窗', () => {
  it('费用为负数时不提交', async () => {
    const w = await mountDialog()
    await w.get('#orderRepairFee').setValue('-1')
    await w.get('#orderReturnFreight').setValue('20')
    await w.get('#orderFeeRemark').setValue('维修费')
    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(createCustomerServiceRepairOrder).not.toHaveBeenCalled()
  })

  it('费用说明为空时不提交', async () => {
    const w = await mountDialog()
    await w.get('#orderRepairFee').setValue('100')
    await w.get('#orderReturnFreight').setValue('20')
    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(createCustomerServiceRepairOrder).not.toHaveBeenCalled()
  })

  it('合法输入按元转分提交', async () => {
    const w = await mountDialog()
    await w.get('#orderRepairFee').setValue('100')
    await w.get('#orderReturnFreight').setValue('20')
    await w.get('#orderFeeRemark').setValue('更换打印头；含寄回运费')
    await w.get('.el-dialog__footer .el-button--primary').trigger('click')
    await flushPromises()

    expect(createCustomerServiceRepairOrder).toHaveBeenCalledWith({
      repairRequestId: 601,
      repairFee: 10000,
      returnFreight: 2000,
      feeRemark: '更换打印头；含寄回运费'
    })
    expect(w.emitted('success')).toHaveLength(1)
  })
})
