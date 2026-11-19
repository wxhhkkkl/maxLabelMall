import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TradeOrderStatusEnum } from '@/utils/constants'

/**
 * 后台订单详情 —— 物流信息（010 / US36 / US37）。
 *
 * 钉四件事：
 * · 有轨迹 → 渲染时间轴
 * · 空轨迹 → 显示「暂无轨迹信息」，不是空白
 * · 查询失败 → 显示降级提示，页面其余内容仍可用
 * · 快递公司没有 `kd100Code` → 直接「暂不支持查询」，**不发**注定失败的请求
 */

const push = vi.fn()
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push, currentRoute: { value: {} } }),
    useRoute: () => ({ params: {} })
  }
})

const getOrder = vi.fn()
const getExpressTrackList = vi.fn()
const getExpressMapUrl = vi.fn()
const updateOrderRemark = vi.fn()
const getDeliveryDefaultAddress = vi.fn()
const deliveryOrder = vi.fn()
vi.mock('@/api/mall/trade/order', () => ({
  EXPRESS_QUERY_NOT_SUPPORTED: 1_011_004_103,
  EXPRESS_MAP_NOT_CONFIGURED: 1_011_004_104,
  EXPRESS_MAP_NOT_AVAILABLE: 1_011_004_105,
  getOrder: (...a: unknown[]) => getOrder(...a),
  getExpressTrackList: (...a: unknown[]) => getExpressTrackList(...a),
  getExpressMapUrl: (...a: unknown[]) => getExpressMapUrl(...a),
  updateOrderRemark: (...a: unknown[]) => updateOrderRemark(...a),
  getDeliveryDefaultAddress: (...a: unknown[]) => getDeliveryDefaultAddress(...a),
  deliveryOrder: (...a: unknown[]) => deliveryOrder(...a),
  cancelOrder: vi.fn(),
  pickUpOrder: vi.fn(),
  updateOrderErp: vi.fn()
}))
const getSimpleDeliveryExpressList = vi.fn()
vi.mock('@/api/mall/trade/delivery/express', () => ({
  getSimpleDeliveryExpressList: (...a: unknown[]) => getSimpleDeliveryExpressList(...a)
}))
vi.mock('@/api/mall/trade/delivery/pickUpStore', () => ({
  getDeliveryPickUpStore: vi.fn()
}))
// 发货表单内联在详情页（2026-11-18），会拉地区树；单测里给空树即可
vi.mock('@/api/system/area', () => ({
  getAreaTree: vi.fn().mockResolvedValue([])
}))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: {},
  getDictLabel: () => '',
  getDictObj: () => undefined
}))
const checkPermi = vi.fn()
vi.mock('@/utils/permission', () => ({ checkPermi: (...a: unknown[]) => checkPermi(...a) }))
// 字典标签会去后端拉字典；单测里不要真发请求
vi.mock('@/config/axios', () => ({
  default: {
    get: async () => [],
    post: async () => null,
    postOriginal: async () => null,
    delete: async () => null,
    put: async () => null,
    download: async () => null
  }
}))

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
;(globalThis as any).ResizeObserver = ResizeObserverStub

const OrderDetail = (await import('./index.vue')).default
const OrderDeliveryPanel = (await import('@/views/mall/trade/order/form/OrderDeliveryPanel.vue')).default

const TRACK_AT = new Date(2026, 9, 22, 18, 30, 0).getTime() // 2026-10-22 18:30:00

function order(over: Record<string, unknown> = {}) {
  return {
    id: 1,
    no: 'NO-1',
    status: 20,
    deliveryType: 1, // EXPRESS
    logisticsId: 10,
    logisticsNo: 'SF1234567890',
    deliveryTime: new Date(2026, 9, 20, 10, 0, 0).getTime(),
    receiverName: '张三',
    receiverMobile: '13800008888',
    receiverDetailAddress: 'xx 路 1 号',
    payPrice: 7800,
    createTime: new Date(2026, 9, 20, 9, 0, 0).getTime(),
    items: [],
    logs: [],
    ...over
  }
}

async function mountDetail(o = order()) {
  getOrder.mockResolvedValue(o)
  const w = mount(OrderDetail, {
    props: { id: 1 },
    global: { plugins: [createPinia()] }
  })
  await flushPromises()
  return w
}

beforeEach(() => {
  getOrder.mockReset()
  getExpressTrackList.mockReset()
  getSimpleDeliveryExpressList.mockReset()
  getSimpleDeliveryExpressList.mockResolvedValue([
    { id: 10, name: '顺丰速运', kd100Code: 'shunfeng' }
  ])
  getDeliveryDefaultAddress.mockReset()
  getDeliveryDefaultAddress.mockResolvedValue({ areaId: null, address: '' })
  deliveryOrder.mockReset()
  deliveryOrder.mockResolvedValue(true)
  getExpressTrackList.mockResolvedValue([])
  getExpressMapUrl.mockReset()
  getExpressMapUrl.mockResolvedValue('')
  updateOrderRemark.mockReset()
  updateOrderRemark.mockResolvedValue(true)
  checkPermi.mockReset()
  checkPermi.mockReturnValue(true)
})

describe('后台订单详情 —— 物流信息', () => {
  it('有轨迹时渲染时间轴', async () => {
    getExpressTrackList.mockResolvedValue([{ time: TRACK_AT, content: '已签收' }])

    const w = await mountDetail()

    expect(getExpressTrackList).toHaveBeenCalledWith(1)
    expect(w.text()).toContain('物流详情')
    expect(w.text()).toContain('已签收')
    // 时间格式化过，不是原始毫秒数
    expect(w.text()).toContain('2026-10-22 18:30:00')
    expect(w.text()).not.toContain(String(TRACK_AT))
  })

  it('空轨迹显示「暂无轨迹信息」', async () => {
    getExpressTrackList.mockResolvedValue([])

    const w = await mountDetail()

    expect(w.text()).toContain('暂无轨迹信息')
  })

  it('查询失败显示降级提示，页面其余内容仍可用', async () => {
    getExpressTrackList.mockRejectedValue(new Error('boom'))

    const w = await mountDetail()

    expect(w.text()).toContain('暂时无法获取物流信息')
    // 订单本身照常渲染
    expect(w.text()).toContain('NO-1')
  })

  it('快递公司没有 kd100Code → 直接「暂不支持查询」，不发请求', async () => {
    getSimpleDeliveryExpressList.mockResolvedValue([
      { id: 10, name: '安能', kd100Code: '' }
    ])

    const w = await mountDetail()

    expect(w.text()).toContain('该快递公司暂不支持查询')
    expect(getExpressTrackList).not.toHaveBeenCalled()
  })

  it('非空但无法识别的编码 → 内联「暂不支持查询」（T043）', async () => {
    getExpressTrackList.mockRejectedValue({ code: 1_011_004_103 })

    const w = await mountDetail()

    expect(w.text()).toContain('该快递公司暂不支持查询')
  })

  it('精简快递列表失败 → 降级提示，订单其余内容照常（T037）', async () => {
    getSimpleDeliveryExpressList.mockRejectedValue(new Error('boom'))

    const w = await mountDetail()

    expect(w.text()).toContain('暂时无法获取物流信息')
    expect(w.text()).toContain('NO-1')
    expect(getExpressTrackList).not.toHaveBeenCalled()
  })

  it('订单不存在时关闭页面并返回，不因物流加载抛 NPE（T041）', async () => {
    getOrder.mockResolvedValue(null)

    mount(OrderDetail, {
      props: { id: 1 },
      global: { plugins: [createPinia()] }
    })
    await flushPromises()

    expect(push).toHaveBeenCalledWith({ name: 'TradeOrder' })
    expect(getExpressTrackList).not.toHaveBeenCalled()
  })

  it('进入详情自动加载地图并内嵌 iframe（不需要点击，010 追加）', async () => {
    getExpressMapUrl.mockResolvedValue('https://api.kuaidi100.com/tools/map/x')

    const w = await mountDetail()

    expect(getExpressMapUrl).toHaveBeenCalledWith(1)
    expect(w.find('iframe.express-map-frame').attributes('src')).toBe(
      'https://api.kuaidi100.com/tools/map/x'
    )
  })

  it('地图未配置发货地址 → 站内提示，不渲染 iframe（010 追加）', async () => {
    getExpressMapUrl.mockRejectedValue({ code: 1_011_004_104 })

    const w = await mountDetail()

    expect(w.text()).toContain('物流地图暂未配置发货地址')
    expect(w.find('iframe').exists()).toBe(false)
  })

  it('发货后 getDetail 重新加载物流，不再停在「加载中…」（T038）', async () => {
    getOrder
      .mockResolvedValueOnce(order({ status: 10, logisticsId: null, logisticsNo: null }))
      .mockResolvedValueOnce(order({ status: 20, logisticsId: 10, logisticsNo: 'SF1234567890' }))
    getExpressTrackList.mockResolvedValue([{ time: TRACK_AT, content: '已签收' }])

    const w = mount(OrderDetail, {
      props: { id: 1 },
      global: { plugins: [createPinia()] }
    })
    await flushPromises()
    expect(getExpressTrackList).not.toHaveBeenCalled()

    // 模拟内联发货表单成功 → 模板里的 @success="getDetail"
    w.findComponent(OrderDeliveryPanel).vm.$emit('success')
    await flushPromises()

    expect(getExpressTrackList).toHaveBeenCalledWith(1)
    expect(w.text()).toContain('已签收')
  })
})

describe('后台订单详情 —— 用户已删除标记（假删除）', () => {
  it('买家删除后运营仍能打开详情，并看到「用户已删除」', async () => {
    const w = await mountDetail(order({ status: 30, userDeleted: true }))

    expect(w.text()).toContain('NO-1')
    expect(w.text()).toContain('用户已删除')
  })

  it('普通订单没有该标记', async () => {
    const w = await mountDetail()

    expect(w.text()).not.toContain('用户已删除')
  })
})

describe('后台订单详情 —— 买家信息', () => {
  it('买家之后展示用户手机号', async () => {
    const w = await mountDetail(
      order({ user: { id: 1, nickname: '张三', mobile: '13800008888' } })
    )
    expect(w.text()).toContain('用户手机号')
    expect(w.text()).toContain('13800008888')
  })
})

describe('后台订单详情 —— 付款方式与交易单号', () => {
  it('微信/支付宝在线支付显示第三方交易单号', async () => {
    const w = await mountDetail(
      order({ payChannelCode: 'wx_lite', payChannelOrderNo: '4200001234202601011234567890' })
    )

    expect(w.text()).toContain('交易单号')
    expect(w.text()).toContain('4200001234202601011234567890')
  })

  it('对公转账不显示交易单号（流水号在收款信息里）', async () => {
    const w = await mountDetail(
      order({ payChannelCode: 'offline', payChannelOrderNo: 'OFFLINE-1' })
    )

    expect(w.text()).not.toContain('交易单号')
  })
})

describe('后台订单详情 —— ERP开单（2026-11-10）', () => {
  it('未开单：显示「未开单」，给「标记ERP已开单」按钮', async () => {
    const w = await mountDetail(order({ erpCreated: false }))

    expect(w.text()).toContain('ERP系统开单')
    expect(w.text()).toContain('未开单')
    expect(w.text()).toContain('标记ERP已开单')
    expect(w.text()).not.toContain('撤销开单')
  })

  it('已开单：显示 ERP 单号，给「撤销开单」按钮', async () => {
    const w = await mountDetail(order({ erpCreated: true, erpNo: 'ERP-20261110-001' }))

    expect(w.text()).toContain('已开单')
    expect(w.text()).toContain('ERP-20261110-001')
    expect(w.text()).toContain('撤销开单')
    expect(w.text()).not.toContain('标记ERP已开单')
  })
})

describe('后台订单详情 —— 商家备注就地编辑', () => {
  it('点备注文字出现输入框，保存后带上订单号与新备注', async () => {
    const w = await mountDetail(order({ remark: '已确认收货' }))

    await w.get('.remark-view').trigger('click')
    const textarea = w.get('.remark-edit textarea')
    expect((textarea.element as HTMLTextAreaElement).value).toBe('已确认收货')

    await textarea.setValue('客户要开专票，已电话确认')
    await w.get('.remark-edit-actions .el-button--primary').trigger('click')
    await flushPromises()

    expect(updateOrderRemark).toHaveBeenCalledWith({ id: 1, remark: '客户要开专票，已电话确认' })
    expect(w.find('.remark-edit').exists()).toBe(false) // 保存后退出编辑态
  })

  it('内容没改就保存：不发请求（避免无意义的写库）', async () => {
    const w = await mountDetail(order({ remark: '已确认收货' }))

    await w.get('.remark-view').trigger('click')
    await w.get('.remark-edit-actions .el-button--primary').trigger('click')
    await flushPromises()

    expect(updateOrderRemark).not.toHaveBeenCalled()
  })

  it('没有 trade:order:update 权限：点备注不进入编辑态', async () => {
    checkPermi.mockReturnValue(false)
    const w = await mountDetail(order({ remark: '已确认收货' }))

    await w.get('.remark-view').trigger('click')

    expect(w.find('.remark-edit').exists()).toBe(false)
  })

  it('原本没有备注：显示「点击填写备注」', async () => {
    const w = await mountDetail(order({ remark: '' }))

    expect(w.text()).toContain('点击填写备注')
  })
})

describe('后台订单详情 —— 发票详情', () => {
  const invoice = {
    id: 77,
    orderId: 1,
    orderNo: 'NO-1',
    type: 2,
    typeName: '电子增值税专用发票',
    titleKind: 1,
    titleKindName: '企业',
    status: 0,
    statusName: '待开票',
    amount: 7800,
    orderPayPrice: 7800,
    orderRefundPrice: 0,
    titleName: '南京路桥机械有限公司',
    taxNo: '91320100MA1XXXXXX',
    titleAddress: '南京市玄武区 xx 路 1 号',
    titlePhone: '025-88888888',
    bankName: '招商银行南京分行',
    bankAccount: '6214xxxxxxxx',
    bankCode: '308301000001',
    email: 'finance@example.com',
    createTime: new Date(2026, 9, 20, 9, 0, 0).getTime()
  }

  it('有开票申请：展示抬头/税号/银行/邮箱，开票按钮在「发票详情」块里', async () => {
    const w = await mountDetail(order({ invoiceStatus: 0, invoice }))

    expect(w.text()).toContain('发票详情')
    expect(w.text()).toContain('待开票')
    expect(w.text()).toContain('电子增值税专用发票')
    expect(w.text()).toContain('南京路桥机械有限公司')
    expect(w.text()).toContain('91320100MA1XXXXXX')
    expect(w.text()).toContain('招商银行南京分行')
    expect(w.text()).toContain('6214xxxxxxxx')
    expect(w.text()).toContain('finance@example.com')
    expect(w.text()).toContain('78.00')

    // 按钮必须落在「发票详情」区块内，而不是还留在「订单状态」里
    const invoiceSection = w.find('.invoice-block')
    expect(invoiceSection.exists(), '找不到「发票详情」区块').toBe(true)
    expect(invoiceSection.findAll('button').some((button) => button.text().includes('开票'))).toBe(
      true
    )
  })

  it('发票信息拆两列、共四列归总，按钮在最下方（2026-11-18）', async () => {
    const w = await mountDetail(order({ invoiceStatus: 0, invoice }))

    const columns = w.findAll('.invoice-column')
    expect(columns).toHaveLength(4)
    // 发票信息：票种 + 抬头主体
    expect(columns[0]!.text()).toContain('发票信息')
    expect(columns[0]!.text()).toContain('南京路桥机械有限公司')
    // 开票资料：注册与开户
    expect(columns[1]!.text()).toContain('开票资料')
    expect(columns[1]!.text()).toContain('南京市玄武区 xx 路 1 号')
    expect(columns[1]!.text()).toContain('招商银行南京分行')
    expect(columns[2]!.text()).toContain('支付信息')
    expect(columns[2]!.text()).toContain('订单已退款')
    expect(columns[3]!.text()).toContain('其他信息')
    expect(columns[3]!.text()).toContain('finance@example.com')

    // 操作按钮不在任何一列里，而是区块最下方
    const actions = w.find('.invoice-actions')
    expect(actions.exists()).toBe(true)
    expect(actions.findAll('button').some((b) => b.text().includes('开票'))).toBe(true)
  })

  it('待开票：「修改发票信息」按钮出现在「发票详情」块里', async () => {
    const w = await mountDetail(order({ invoiceStatus: 0, invoice }))

    const invoiceSection = w.find('.invoice-block')
    expect(invoiceSection.exists()).toBe(true)
    expect(
      invoiceSection.findAll('button').some((button) => button.text().includes('修改发票信息'))
    ).toBe(true)
  })

  it('已开票：不给「修改发票信息」入口（票面文件已经生成，后端也会拒）', async () => {
    const w = await mountDetail(
      order({ invoiceStatus: 10, invoice: { ...invoice, status: 10, statusName: '已开票' } })
    )

    expect(w.text()).not.toContain('修改发票信息')
  })

  it('区块顺序：发票详情、商品详情紧跟在收货信息之后（2026-11-16 所有者要求）', async () => {
    const w = await mountDetail(order({ invoiceStatus: 0, invoice }))

    // 发票详情已从 el-descriptions 换成三列区块（2026-11-18），顺序断言要把它也算进来
    const sections = w.findAll('.el-descriptions, .invoice-block').map((section) => section.text())
    const indexOf = (title: string) => sections.findIndex((text) => text.includes(title))
    expect(indexOf('收货信息')).toBeGreaterThanOrEqual(0)
    expect(indexOf('收货信息')).toBeLessThan(indexOf('发票详情'))
    expect(indexOf('发票详情')).toBeLessThan(indexOf('商品详情'))
    expect(indexOf('商品详情')).toBeLessThan(indexOf('订单状态'))
  })

  it('未申请开票：显示「未申请开票」，没有开票按钮', async () => {
    const w = await mountDetail(order({ invoiceStatus: null, invoice: null }))

    expect(w.text()).toContain('该订单未申请开票')
    const invoiceSection = w.find('.invoice-block')
    expect(invoiceSection.exists()).toBe(true)
    expect(invoiceSection.findAll('button')).toHaveLength(0)
  })
})

describe('后台订单详情 —— 修改地址入口（2026-11-17 所有者要求）', () => {
  it('待发货快递单：修改地址在「收货信息」里、紧跟收货地址（不再放顶部操作区）', async () => {
    const w = await mountDetail(order({ status: TradeOrderStatusEnum.UNDELIVERED.status }))

    const receiverSection = w
      .findAll('.el-descriptions')
      .find((section) => section.text().includes('收货信息'))!
    expect(receiverSection.text()).toContain('收货地址')
    expect(receiverSection.text()).toContain('修改地址')
  })

  it('已发货订单不显示修改地址', async () => {
    const w = await mountDetail(order({ status: TradeOrderStatusEnum.DELIVERED.status }))

    expect(w.text()).not.toContain('修改地址')
  })
})

/**
 * 发货表单内联（2026-11-18）
 *
 * 待发货订单进详情就能直接填写并发货，不再点「发货」二次打开弹窗。
 */
describe('后台订单详情 —— 发货表单内联', () => {
  it('待发货快递单：页面上直接展示发货表单，且不再有「发货」按钮', async () => {
    const w = await mountDetail(order({ status: 10, logisticsId: null, logisticsNo: null }))

    expect(w.text()).toContain('订单发货')
    expect(w.text()).toContain('发货方式')
    expect(w.text()).toContain('确认发货')
    // 旧的二次打开入口已移除（页面底部还有「提醒」文案，按按钮精确匹配）
    expect(w.findAll('button').some((b) => b.text() === '发货')).toBe(false)
  })

  it('已发货订单：不渲染发货表单', async () => {
    const w = await mountDetail(order({ status: 20 }))

    expect(w.text()).not.toContain('确认发货')
  })

  it('待发货自提单：不渲染发货表单（走核销入口）', async () => {
    const w = await mountDetail(order({ status: 10, deliveryType: 2, logisticsId: null }))

    expect(w.text()).not.toContain('确认发货')
  })

  it('发货表单排在「商品详情」之后（2026-11-19 所有者要求）', async () => {
    const w = await mountDetail(order({ status: TradeOrderStatusEnum.UNDELIVERED.status }))

    const sections = w.findAll('.el-descriptions, .invoice-block').map((section) => section.text())
    const indexOf = (title: string) => sections.findIndex((text) => text.includes(title))
    expect(indexOf('商品详情')).toBeGreaterThanOrEqual(0)
    expect(indexOf('商品详情')).toBeLessThan(indexOf('订单发货'))
  })

})
