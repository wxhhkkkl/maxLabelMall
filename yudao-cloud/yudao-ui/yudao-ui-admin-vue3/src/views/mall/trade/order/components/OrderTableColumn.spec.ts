import { flushPromises, mount } from '@vue/test-utils'
import { ElTable } from 'element-plus'
import { defineComponent, h } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { INVOICE_STATUS } from '@/utils/invoice'

/**
 * 后台订单列表的「一行一单」展示（2026-10-23 按旧后台版式重排）。
 *
 * 钉住的是**信息落位**，不是像素：
 * · 订单信息条 = 订单编号 + 完成时间（没有完成时间就看下单时间），有单号时给「查看物流」
 * · 金额 = 实付（红）+ 商品原价
 * · 会员 = 昵称 + 用户编号；推荐人没有就是「无」
 * · 收件人信息按配送方式分支：快递看收件人、虚拟商品只看买家、自提看门店
 * · 售后状态不再独占一列，但**不能消失**（退款中必须仍能在列表上看见）
 */

const push = vi.fn()
// 只替换 useRouter / useRoute，其余导出必须保留 —— 组件链会经 axios 引入真实的
// `src/router/index.ts`，它要用 createRouter 建实例
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return { ...actual, useRouter: () => ({ push }), useRoute: () => ({}) }
})

const OrderTableColumn = (await import('./OrderTableColumn.vue')).default

// 字典标签会去后端拉 `simple-list`；单测里别真发请求。
// 注意：这里只关心"有没有渲染出标签"，不校验字典文案（那是字典模块的职责）。
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

/** Element Plus 的表格在 jsdom 里要自己补 ResizeObserver */
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
;(globalThis as any).ResizeObserver = ResizeObserverStub

type Row = import('@/api/mall/trade/order').OrderVO

function order(over: Partial<Row> = {}): Row {
  return {
    id: 1,
    no: 'NO-20261023-0001',
    createTime: '2026-10-23 10:00:00' as any,
    status: 30,
    payPrice: 29000,
    totalPrice: 30000,
    userId: 5595975,
    deliveryType: 1,
    receiverName: '杨锋',
    receiverMobile: '15755652888',
    items: [
      {
        id: 11,
        spuName: '多规格三防热敏纸',
        picUrl: '',
        count: 60,
        properties: [{ propertyId: 1, propertyName: '规格', valueName: '100*100' }]
      }
    ],
    ...over
  } as Row
}

function mountTable(list: Row[]) {
  const Wrapper = defineComponent({
    setup() {
      return () =>
        h(
          ElTable as any,
          { data: list, rowKey: 'id' },
          {
            default: () => [h(OrderTableColumn as any, { list, pickUpStoreList: [] })]
          }
        )
    }
  })
  return mount(Wrapper)
}

beforeEach(() => {
  push.mockReset()
})

describe('后台订单列表 —— 订单信息条', () => {
  it('左：订单编号 + 完成时间；右：快递编号 + 查看物流', async () => {
    const w = mountTable([
      order({ logisticsNo: 'YT7648787406662', finishTime: '2026-10-22 21:15:33' as any })
    ])
    await flushPromises()

    const text = w.text()
    expect(text).toContain('订单编号：NO-20261023-0001')
    expect(text).toContain('完成时间：')
    expect(text).toContain('快递编号：YT7648787406662')
    expect(text).toContain('查看物流')
  })

  it('未完成的单没有「完成时间」，退回显示下单时间', async () => {
    const w = mountTable([order({ finishTime: null })])
    await flushPromises()

    const text = w.text()
    expect(text).toContain('下单时间：')
    expect(text).not.toContain('完成时间：')
  })

  it('没有快递单号就不显示「快递编号 / 查看物流」（不给死链）', async () => {
    const w = mountTable([order({ logisticsNo: undefined })])
    await flushPromises()

    const text = w.text()
    expect(text).not.toContain('快递编号')
    expect(text).not.toContain('查看物流')
  })

  it('点「查看物流」把运营送到订单详情（物流轨迹只有那一处实现）', async () => {
    const w = mountTable([order({ logisticsNo: 'YT1' })])
    await flushPromises()

    await w.get('.order-head-link').trigger('click')
    expect(push).toHaveBeenCalledWith({ name: 'TradeOrderDetail', params: { id: 1 } })
  })
})

describe('后台订单列表 —— 各列的信息落位', () => {
  it('金额：实付 + 商品原价（原价带「元」）', async () => {
    const w = mountTable([order()])
    await flushPromises()

    const amounts = w.findAll('.order-amount')
    expect(amounts[0].text()).toBe('290.00')
    expect(w.findAll('.order-amount-origin')[0].text()).toBe('300.00元')
  })

  it('会员：昵称 + 用户编号', async () => {
    const w = mountTable([order({ user: { id: 5595975, nickname: '杨锋' } })])
    await flushPromises()

    const text = w.text()
    expect(text).toContain('杨锋')
    expect(text).toContain('(ID:5595975)')
  })

  it('没申请发票 → 「否」（不是空着让人以为是漏了数据）', async () => {
    const w = mountTable([order({ invoiceStatus: null })])
    await flushPromises()

    expect(w.text()).toContain('否')
    expect(w.find('.el-tag--warning').exists()).toBe(false)
  })

  it('发票状态按 待开票 / 已开票 / 已作废 显示', async () => {
    const waiting = mountTable([order({ invoiceStatus: INVOICE_STATUS.WAITING })])
    await flushPromises()
    expect(waiting.text()).toContain('待开票')

    const issued = mountTable([order({ invoiceStatus: INVOICE_STATUS.ISSUED })])
    await flushPromises()
    expect(issued.text()).toContain('已开票')

    const canceled = mountTable([order({ invoiceStatus: INVOICE_STATUS.CANCELED })])
    await flushPromises()
    expect(canceled.text()).toContain('已作废')
  })

  it('快递单：收件人信息显示姓名与手机号', async () => {
    const w = mountTable([order()])
    await flushPromises()

    const text = w.text()
    expect(text).toContain('杨锋')
    expect(text).toContain('15755652888')
  })

  it('虚拟商品单：没有收件人，显示买家而不是两行空白', async () => {
    const w = mountTable([
      order({
        deliveryType: 3,
        receiverName: '',
        receiverMobile: '',
        user: { id: 1, nickname: '软件客户' }
      })
    ])
    await flushPromises()

    const text = w.text()
    expect(text).toContain('买家：软件客户')
    expect(text).not.toContain('15755652888')
  })

  it('售后状态收进「状态」列但仍然可见（退款中不能从列表上消失）', async () => {
    const w = mountTable([order({ afterSaleStatus: 10 })])
    await flushPromises()

    // 具体文案来自字典（测试里不拉字典），这里只钉"这一格被渲染出来了"
    expect(w.find('.order-after-sale').exists()).toBe(true)
  })

  it('没有售后的单不渲染售后那一格', async () => {
    const w = mountTable([order({ afterSaleStatus: 0 })])
    await flushPromises()

    expect(w.find('.order-after-sale').exists()).toBe(false)
  })
})
