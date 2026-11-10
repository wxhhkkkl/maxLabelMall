import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { defineComponent, h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 后台订单列表的**状态标签页**（2026-10-23）。
 *
 * 钉三件事：
 * · 进页面默认按「待发货/已发货」筛（两个状态一起，不是只筛待发货）
 * · 切标签会清掉下拉里的单选状态，两者不会同时生效 —— 否则界面自相矛盾
 * · 「重置」只清搜索表单，**不动当前标签**
 */

const push = vi.fn()
const currentRoute = ref({})
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return { ...actual, useRouter: () => ({ push, currentRoute }), useRoute: () => ({}) }
})

const getOrderPage = vi.fn()
vi.mock('@/api/mall/trade/order', () => ({
  getOrderPage: (...a: unknown[]) => getOrderPage(...a),
  cancelOrder: vi.fn(),
  getOrderSummary: vi.fn(),
  exportOrderExcel: vi.fn()
}))

// 订单列表「更多 → 开票」：点开先按订单号查申请，再把申请交给上传弹层
const getInvoicePage = vi.fn()
vi.mock('@/api/mall/trade/invoice/index', () => ({
  getInvoicePage: (...a: unknown[]) => getInvoicePage(...a),
  issueInvoice: vi.fn()
}))

// 上传弹层只验证"被打开、拿到的是哪条申请"，不重复渲染它自己的表单（那有单独的 spec）
const invoiceOpen = vi.fn()
const InvoiceIssueFormStub = defineComponent({
  setup(_, { expose }) {
    expose({ open: invoiceOpen })
    return () => h('div')
  }
})

// 导出弹窗只验证"被打开"，它自己的表单逻辑有单独的 spec
const exportOpen = vi.fn()
const OrderExportFormStub = defineComponent({
  setup(_, { expose }) {
    expose({ open: exportOpen })
    return () => h('div')
  }
})
vi.mock('@/api/mall/trade/delivery/pickUpStore', () => ({
  getSimpleDeliveryPickUpStoreList: vi.fn().mockResolvedValue([])
}))
vi.mock('@/api/mall/trade/delivery/express', () => ({
  getSimpleDeliveryExpressList: vi.fn().mockResolvedValue([])
}))
vi.mock('@/utils/dict', () => ({
  DICT_TYPE: {},
  getIntDictOptions: () => [],
  getStrDictOptions: () => []
}))
vi.mock('@/utils/permission', () => ({ checkPermi: () => true }))
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

const OrderIndex = (await import('./index.vue')).default

/** 取最后一次分页请求的参数 */
const lastParams = () => getOrderPage.mock.calls.at(-1)?.[0] as Record<string, any>

/** 点某个状态标签（按文案找） */
async function clickTab(w: any, label: string) {
  const tab = w.findAll('.el-tabs__item').find((t: any) => t.text() === label)
  expect(tab, `找不到标签「${label}」`).toBeTruthy()
  await tab!.trigger('click')
  await flushPromises()
}

type Row = import('@/api/mall/trade/order').OrderVO

/** 一行最小可渲染的订单（表格列会读 items / user / 收货字段） */
function order(over: Partial<Row> = {}): Row {
  return {
    id: 1,
    no: 'NO-20261105-0001',
    createTime: '2026-11-05 10:00:00' as any,
    status: 30,
    payPrice: 10000,
    totalPrice: 10000,
    userId: 1001,
    deliveryType: 1,
    receiverName: '张三',
    receiverMobile: '13800138000',
    items: [{ id: 11, spuName: '标签纸', picUrl: '', count: 1 }],
    ...over
  } as Row
}

beforeEach(() => {
  getOrderPage.mockReset()
  getOrderPage.mockResolvedValue({ list: [], total: 0 })
  getInvoicePage.mockReset()
  invoiceOpen.mockReset()
  exportOpen.mockReset()
  // el-dropdown 的菜单会 teleport 到 body；不清掉上一条用例的下拉会串味
  document.querySelectorAll('.el-popper').forEach((el) => el.remove())
})

describe('后台订单列表 —— 状态标签页', () => {
  it('进页面默认只查「待发货 + 已发货」', async () => {
    const w = mount(OrderIndex, { global: { plugins: [createPinia()] } })
    await flushPromises()

    expect(lastParams().statuses).toEqual([10, 20])
    expect(lastParams().status).toBeUndefined()
    // 默认标签高亮在「待发货/已发货」
    expect(w.get('.el-tabs__item.is-active').text()).toBe('待发货/已发货')
  })

  it('切到「已完成」→ 只按 30 筛，且清掉下拉的单选状态', async () => {
    const w = mount(OrderIndex, { global: { plugins: [createPinia()] } })
    await flushPromises()

    await clickTab(w, '已完成')
    expect(lastParams().statuses).toEqual([30])
    expect(lastParams().status).toBeUndefined()
  })

  it('切到「全部订单」→ 不按状态筛', async () => {
    const w = mount(OrderIndex, { global: { plugins: [createPinia()] } })
    await flushPromises()

    await clickTab(w, '全部订单')
    expect(lastParams().statuses).toBeUndefined()
    expect(lastParams().status).toBeUndefined()
  })

  it('「下单方式」筛选把 agentCreated 传给后端（017）', async () => {
    const w = mount(OrderIndex, { global: { plugins: [createPinia()] } })
    await flushPromises()

    const formItem = w
      .findAll('.el-form-item')
      .find((item) => item.text().includes('下单方式'))
    expect(formItem).toBeTruthy()
    const select = formItem!.findComponent({ name: 'ElSelect' })
    expect(select.exists()).toBe(true)
    select.vm.$emit('update:modelValue', true)
    await flushPromises()

    const search = w.findAll('button').find((button) => button.text().includes('搜索'))
    expect(search).toBeTruthy()
    await search!.trigger('click')
    await flushPromises()

    expect(lastParams().agentCreated).toBe(true)
  })

  it('「重置」只清搜索表单，不动当前标签', async () => {
    const w = mount(OrderIndex, { global: { plugins: [createPinia()] } })
    await flushPromises()

    await clickTab(w, '已完成')
    const reset = w.findAll('button').find((b) => b.text().includes('重置'))
    expect(reset).toBeTruthy()
    await reset!.trigger('click')
    await flushPromises()

    expect(lastParams().statuses).toEqual([30])
    expect(w.get('.el-tabs__item.is-active').text()).toBe('已完成')
  })
})

/**
 * 订单列表的「更多 → 开票」（2026-11-05）。
 *
 * 客户在订单详情申请过开票后，运营扫列表时应该能当场开票：
 * · 只有**已申请**（待开票 / 已开票）的订单才出现入口，没申请与已作废都不出现
 * · 点开**弹上传框**，不跳走页面
 * · 上传成功后刷新列表（开票状态从「待开票」变「已开票」由后端返回）
 */
describe('后台订单列表 —— 更多里的开票入口', () => {
  /** 点开某一行的「更多」下拉，返回菜单项元素 */
  async function openMoreMenu(w: any) {
    const more = w.findAll('button').find((b: any) => b.text().includes('更多'))
    expect(more, '找不到「更多」按钮').toBeTruthy()
    await more!.trigger('click')
    await flushPromises()
    return Array.from(document.body.querySelectorAll('.el-dropdown-menu__item')) as HTMLElement[]
  }

  it('客户已申请（待开票）→ 出现「开票」，点开弹上传框且不跳页', async () => {
    getOrderPage.mockResolvedValue({ list: [order({ invoiceStatus: 0 })], total: 1 })
    getInvoicePage.mockResolvedValue({
      list: [{ id: 77, orderId: 1, orderNo: 'NO-20261105-0001', status: 0 }],
      total: 1
    })

    const w = mount(OrderIndex, {
      global: { plugins: [createPinia()], stubs: { InvoiceIssueForm: InvoiceIssueFormStub } }
    })
    await flushPromises()

    const items = await openMoreMenu(w)
    const invoiceItem = items.find((el) => el.textContent?.includes('开票'))
    expect(invoiceItem, '「更多」里应有开票入口').toBeTruthy()

    invoiceItem!.click()
    await flushPromises()

    // 按订单号查这条申请（一次一条），再把申请交给弹层
    expect(getInvoicePage).toHaveBeenCalledWith(
      expect.objectContaining({ orderNo: 'NO-20261105-0001' })
    )
    expect(invoiceOpen).toHaveBeenCalledWith(expect.objectContaining({ id: 77 }))
    // 「不离开列表」：没有发生路由跳转
    expect(push).not.toHaveBeenCalled()
  })

  it('没申请（invoiceStatus=null）→ 不出现开票入口', async () => {
    getOrderPage.mockResolvedValue({ list: [order({ invoiceStatus: null })], total: 1 })

    const w = mount(OrderIndex, {
      global: { plugins: [createPinia()], stubs: { InvoiceIssueForm: InvoiceIssueFormStub } }
    })
    await flushPromises()

    const items = await openMoreMenu(w)
    expect(items.some((el) => el.textContent?.includes('开票'))).toBe(false)
  })
})

/**
 * 导出入口（2026-11-10）：列表页只负责"打开时间范围弹窗"，
 * 真正的范围选择与请求在 OrderExportForm 里（见它自己的 spec）。
 */
describe('后台订单列表 —— 导出入口', () => {
  it('点「导出」打开时间范围弹窗', async () => {
    const w = mount(OrderIndex, {
      global: { plugins: [createPinia()], stubs: { OrderExportForm: OrderExportFormStub } }
    })
    await flushPromises()

    const exportButton = w.findAll('button').find((button) => button.text().includes('导出'))
    expect(exportButton, '找不到「导出」按钮').toBeTruthy()
    await exportButton!.trigger('click')
    await flushPromises()

    expect(exportOpen).toHaveBeenCalled()
  })
})
