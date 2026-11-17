import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

/**
 * 客户服务（021 / US70 + US71）
 *
 * 钉五件事：登录态、内容必填、提交后列表出现、处理说明可见、维修申请提交与状态展示。
 */
const createCustomerServiceTicket = vi.fn()
const getCustomerServiceTicketPage = vi.fn()
const createCustomerServiceRepair = vi.fn()
const getCustomerServiceRepairPage = vi.fn()
const getCustomerServiceRepair = vi.fn()
const payRepairOrderOnline = vi.fn()
const chooseRepairOrderOffline = vi.fn()
const markRepairShipped = vi.fn()
vi.mock('@/api/customerService', () => ({
  createCustomerServiceTicket: (...a: unknown[]) => createCustomerServiceTicket(...a),
  getCustomerServiceTicketPage: (...a: unknown[]) => getCustomerServiceTicketPage(...a),
  getCustomerServiceTicket: vi.fn(),
  createCustomerServiceRepair: (...a: unknown[]) => createCustomerServiceRepair(...a),
  getCustomerServiceRepairPage: (...a: unknown[]) => getCustomerServiceRepairPage(...a),
  getCustomerServiceRepair: (...a: unknown[]) => getCustomerServiceRepair(...a),
  payRepairOrderOnline: (...a: unknown[]) => payRepairOrderOnline(...a),
  chooseRepairOrderOffline: (...a: unknown[]) => chooseRepairOrderOffline(...a),
  markRepairShipped: (...a: unknown[]) => markRepairShipped(...a)
}))
vi.mock('@/api/pay', () => ({
  getPayOrder: vi.fn().mockResolvedValue({ appId: 1, status: 10 }),
  listEnabledChannelCodes: vi.fn().mockResolvedValue(['alipay_pc']),
  submitPay: vi.fn()
}))
vi.mock('@/api/upload', () => ({ uploadFile: vi.fn().mockResolvedValue('https://img.yuwangchenfa.com/a.jpg') }))
vi.mock('@/api/address', () => ({ listAddress: vi.fn().mockResolvedValue([]) }))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' }),
  logout: vi.fn()
}))

const View = (await import('./CustomerServiceView.vue')).default

const TICKET = {
  id: 501,
  type: 1,
  typeName: '投诉',
  content: '打印偏移',
  contactMobile: '13800008888',
  status: 10,
  statusName: '已处理',
  handleRemark: '已电话回访并指导恢复',
  handleTime: new Date(2026, 10, 17, 10, 0).getTime(),
  images: [],
  createTime: new Date(2026, 10, 17, 9, 0).getTime()
}

const REPAIR = {
  id: 601,
  deviceModel: 'Zebra ZT411',
  faultDesc: '走纸不停',
  contactMobile: '13800008888',
  status: 10,
  statusName: '已联系',
  contactResult: '已电话联系，确认可寄修',
  repairOrderId: 701,
  payStatusName: '待支付',
  refundStatus: 0,
  refundStatusName: '未退款',
  images: [],
  createTime: new Date(2026, 10, 17, 9, 0).getTime()
}

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/account/customer-service', component: View },
    { path: '/account', component: { template: '<div/>' } },
    { path: '/', component: { template: '<div/>' } }
  ]
})

async function mountView() {
  const w = mount(View, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

/** 页内不再有页签，切到在线维修走侧栏用的 ?tab=repair */
async function showRepair() {
  await router.push('/account/customer-service?tab=repair')
  await flushPromises()
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  await router.push('/account/customer-service')
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
  getCustomerServiceTicketPage.mockReset()
  getCustomerServiceTicketPage.mockResolvedValue({ list: [TICKET], total: 1 })
  getCustomerServiceRepairPage.mockReset()
  getCustomerServiceRepairPage.mockResolvedValue({ list: [REPAIR], total: 1 })
  getCustomerServiceRepair.mockReset()
  getCustomerServiceRepair.mockResolvedValue({
    ...REPAIR,
    status: 20,
    statusName: '已开单',
    repairOrderId: 701,
    repairFee: 10000,
    returnFreight: 2000,
    payableAmount: 12000,
    feeRemark: '更换打印头；含寄回运费',
    payStatus: 0,
    payStatusName: '待支付',
    returnAddress: {
      receiverName: '李四',
      receiverMobile: '010-88613986',
      areaName: '北京市 海淀区',
      detailAddress: 'xx 路 8 号',
      shipRemark: '请注明维修单号'
    }
  })
  payRepairOrderOnline.mockReset()
  payRepairOrderOnline.mockResolvedValue({ payOrderId: 99001 })
  chooseRepairOrderOffline.mockReset()
  chooseRepairOrderOffline.mockResolvedValue({ payStatus: 10, offline: { accountName: '某某公司' } })
  markRepairShipped.mockReset()
  markRepairShipped.mockResolvedValue(true)
  createCustomerServiceTicket.mockReset()
  createCustomerServiceTicket.mockResolvedValue(502)
  createCustomerServiceRepair.mockReset()
  createCustomerServiceRepair.mockResolvedValue(602)
})

describe('客户服务 - 投诉咨询', () => {
  it('已处理记录展示客服处理说明', async () => {
    const w = await mountView()

    expect(w.text()).toContain('打印偏移')
    expect(w.text()).toContain('已电话回访并指导恢复')
    expect(w.text()).toContain('客服回复')
  })

  it('客服代建的记录对客户可见（来源不影响客户侧展示）', async () => {
    getCustomerServiceTicketPage.mockResolvedValue({
      list: [{ ...TICKET, id: 999, content: '客服代建的内容', status: 0, statusName: '待处理', handleRemark: null }],
      total: 1
    })
    const w = await mountView()

    expect(w.text()).toContain('客服代建的内容')
  })

  it('默认展示记录列表，提交表单在弹框中', async () => {
    const w = await mountView()

    expect(w.text()).toContain('打印偏移')
    expect(w.find('textarea').exists()).toBe(false)
    expect(w.find('#csTabTicket').exists()).toBe(false)
    expect(w.find('#csTabRepair').exists()).toBe(false)

    await w.get('#ticketCreateOpen').trigger('click')
    expect(w.find('textarea').exists()).toBe(true)
  })

  it('联系手机号默认带出账户手机号', async () => {
    const w = await mountView()
    await w.get('#ticketCreateOpen').trigger('click')

    expect((w.get('input.ml-input').element as HTMLInputElement).value).toBe('13800008888')
  })

  it('内容为空时不提交', async () => {
    const w = await mountView()
    await w.get('#ticketCreateOpen').trigger('click')

    await w.get('#ticketSubmit').trigger('click')
    await flushPromises()

    expect(createCustomerServiceTicket).not.toHaveBeenCalled()
  })

  it('填写后提交，带上类型 / 内容 / 手机号并刷新列表', async () => {
    getCustomerServiceTicketPage.mockClear()
    const w = await mountView()
    await w.get('#ticketCreateOpen').trigger('click')

    await w.get('textarea').setValue('打印时标签总是偏移')
    await w.get('#ticketSubmit').trigger('click')
    await flushPromises()

    expect(createCustomerServiceTicket).toHaveBeenCalledWith(
      expect.objectContaining({ type: 1, content: '打印时标签总是偏移', contactMobile: '13800008888' })
    )
    expect(getCustomerServiceTicketPage).toHaveBeenCalled()
  })
})

describe('客户服务 - 在线维修', () => {
  it('侧栏 ?tab=repair 直接打开在线维修并高亮', async () => {
    await router.push('/account/customer-service?tab=repair')
    const w = mount(View, { global: { plugins: [router, createPinia()] } })
    await flushPromises()

    expect(w.find('#repairCreateOpen').exists()).toBe(true)
    expect(w.findAll('.s-item.active').map((a) => a.text().trim())).toEqual(['在线维修'])
  })

  it('展示维修记录与客服联系结果', async () => {
    const w = await mountView()
    await showRepair()

    expect(w.text()).toContain('Zebra ZT411')
    expect(w.text()).toContain('已联系')
    expect(w.text()).toContain('待支付')
    expect(w.text()).toContain('已电话联系，确认可寄修')
  })

  it('详情默认展开在列表内，不弹窗', async () => {
    const w = await mountView()
    await showRepair()

    expect(w.text()).toContain('维修订单')
    expect(w.text()).not.toContain('查看详情')
    expect(w.find('.ml-modal').exists()).toBe(false)
  })

  it('查看详情展示维修订单卡片（费用与支付状态）', async () => {
    const w = await mountView()
    await showRepair()


    expect(w.text()).toContain('维修订单')
    expect(w.text()).toContain('¥120.00')
    expect(w.text()).toContain('待支付')
    expect(w.text()).toContain('更换打印头；含寄回运费')
  })

  it('待支付订单显示支付方式；点击在线支付调用接口', async () => {
    const w = await mountView()
    await showRepair()

    expect(w.text()).toContain('回收邮寄地址')
    expect(w.text()).toContain('请注明维修单号')
    const payButton = w.findAll('button').find((b) => b.text() === '在线支付')!
    expect(payButton).toBeTruthy()
    await payButton.trigger('click')
    await flushPromises()

    expect(payRepairOrderOnline).toHaveBeenCalledWith(701)
  })

  it('待确认收款时可再次查看收款信息', async () => {
    getCustomerServiceRepair.mockResolvedValue({
      ...REPAIR,
      status: 20,
      statusName: '已开单',
      repairOrderId: 701,
      repairFee: 10000,
      returnFreight: 2000,
      payableAmount: 12000,
      payStatus: 10,
      payStatusName: '待确认收款',
      returnAddress: null
    })
    const w = await mountView()
    await showRepair()

    await w.findAll('button').find((b) => b.text() === '查看收款信息')!.trigger('click')
    await flushPromises()

    expect(chooseRepairOrderOffline).toHaveBeenCalledWith(701)
    expect(w.text()).toContain('某某公司')
  })

  it('已完成维修订单显示已完成', async () => {
    const completedAt = Date.now()
    getCustomerServiceRepairPage.mockResolvedValue({
      list: [{ ...REPAIR, status: 50, statusName: '已完成', completeTime: completedAt }],
      total: 1
    })
    getCustomerServiceRepair.mockResolvedValue({
      ...REPAIR,
      status: 50,
      statusName: '已完成',
      repairOrderId: 701,
      payStatus: 20,
      payStatusName: '已支付',
      completeTime: completedAt
    })
    const w = await mountView()
    await showRepair()

    expect(w.text()).toContain('已完成')
  })

  it('已退款订单显示已取消，隐藏地址与寄出表单', async () => {
    getCustomerServiceRepairPage.mockResolvedValue({
      list: [{ ...REPAIR, refundStatus: 20, refundStatusName: '已退款' }],
      total: 1
    })
    getCustomerServiceRepair.mockResolvedValue({
      ...REPAIR,
      status: 20,
      statusName: '已开单',
      repairOrderId: 701,
      repairFee: 10000,
      returnFreight: 2000,
      payableAmount: 12000,
      payStatus: 20,
      payStatusName: '已支付',
      refundStatus: 20,
      refundStatusName: '已退款',
      returnAddress: {
        receiverName: '李四',
        receiverMobile: '010-88613986',
        areaName: '北京市 海淀区',
        detailAddress: 'xx 路 8 号',
        shipRemark: '请注明维修单号'
      }
    })
    const w = await mountView()
    await showRepair()

    expect(w.text()).toContain('已取消')
    expect(w.text()).not.toContain('回收邮寄地址')
    expect(w.text()).not.toContain('标记已寄出')
    expect(w.text()).not.toContain('在线支付')
  })

  it('未支付时不显示寄出表单', async () => {
    const w = await mountView()
    await showRepair()

    expect(w.text()).not.toContain('标记已寄出')
  })

  it('已支付时显示寄出表单并提交运单号', async () => {
    getCustomerServiceRepair.mockResolvedValueOnce({
      ...REPAIR,
      status: 20,
      statusName: '已开单',
      repairOrderId: 701,
      repairFee: 10000,
      returnFreight: 2000,
      payableAmount: 12000,
      payStatus: 20,
      payStatusName: '已支付',
      returnAddress: null
    })
    const w = await mountView()
    await showRepair()

    expect(w.text()).toContain('地址尚未配置')
    await w.get('#repairShipNo-601').setValue('SF1234567890')
    const shipButton = w.findAll('button').find((b) => b.text() === '标记已寄出')!
    await shipButton.trigger('click')
    await flushPromises()

    expect(markRepairShipped).toHaveBeenCalledWith(
      expect.objectContaining({ id: 601, shipNo: 'SF1234567890' })
    )
  })

  it('缺少设备型号时不提交', async () => {
    const w = await mountView()
    await showRepair()
    await w.get('#repairCreateOpen').trigger('click')

    await w.get('#repairFaultDesc').setValue('走纸不停')
    await w.get('#repairReceiverName').setValue('张三')
    await w.get('#repairReceiverMobile').setValue('13800008888')
    await w.get('#repairReceiverArea').setValue('江苏省 南京市')
    await w.get('#repairReceiverDetail').setValue('xx 路 1 号')
    await w.get('#repairSubmit').trigger('click')
    await flushPromises()

    expect(createCustomerServiceRepair).not.toHaveBeenCalled()
  })

  it('填写完整后提交维修申请', async () => {
    const w = await mountView()
    await showRepair()
    await w.get('#repairCreateOpen').trigger('click')

    await w.get('#repairDeviceModel').setValue('Zebra ZT411')
    await w.get('#repairFaultDesc').setValue('走纸不停')
    await w.get('#repairReceiverName').setValue('张三')
    await w.get('#repairReceiverMobile').setValue('13800008888')
    await w.get('#repairReceiverArea').setValue('江苏省 南京市')
    await w.get('#repairReceiverDetail').setValue('xx 路 1 号')
    await w.get('#repairSubmit').trigger('click')
    await flushPromises()

    expect(createCustomerServiceRepair).toHaveBeenCalledWith(
      expect.objectContaining({
        deviceModel: 'Zebra ZT411',
        faultDesc: '走纸不停',
        contactMobile: '13800008888',
        receiverName: '张三',
        receiverArea: '江苏省 南京市',
        receiverDetail: 'xx 路 1 号'
      })
    )
  })
})
