import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 投诉咨询列表（021 / US70）
 *
 * 钉三件事：筛选区存在、待处理行有「处理」、无处理权限时不显示「处理」。
 */
const getCustomerServiceTicketPage = vi.fn()
vi.mock('@/api/trade/customerService', () => ({
  getCustomerServiceTicketPage: (...a: unknown[]) => getCustomerServiceTicketPage(...a),
  getCustomerServiceTicket: vi.fn(),
  handleCustomerServiceTicket: vi.fn()
}))

const TicketTable = (await import('./TicketTable.vue')).default

const ROW = {
  id: 501,
  memberId: 88,
  memberNickname: '张三',
  memberMobile: '13800008888',
  type: 1,
  typeName: '投诉',
  content: '打印偏移',
  contactMobile: '13800008888',
  status: 0,
  statusName: '待处理',
  images: [],
  source: 1,
  sourceName: '客户自助',
  createTime: new Date(2026, 10, 17).getTime()
}

const mountTable = (props: Record<string, unknown> = {}) =>
  mount(TicketTable, { props, global: { plugins: [createPinia()] } })

beforeEach(() => {
  getCustomerServiceTicketPage.mockReset()
  getCustomerServiceTicketPage.mockResolvedValue({ list: [ROW], total: 1 })
})

describe('投诉咨询列表', () => {
  it('渲染筛选区与列表', async () => {
    const w = mountTable({ canHandle: true })
    await flushPromises()

    expect(w.text()).toContain('诉求类型')
    expect(w.text()).toContain('状态')
    expect(w.text()).toContain('来源')
    expect(w.text()).toContain('张三')
    expect(w.text()).toContain('打印偏移')
  })

  it('有处理权限时待处理行出现「处理」，点击抛出事件', async () => {
    const w = mountTable({ canHandle: true })
    await flushPromises()

    const button = w.findAll('button').find((b) => b.text() === '处理')!
    expect(button).toBeTruthy()
    await button.trigger('click')
    expect(w.emitted('handle')).toHaveLength(1)
  })

  it('无处理权限时不显示「处理」', async () => {
    const w = mountTable({ canHandle: false })
    await flushPromises()

    expect(w.findAll('button').some((b) => b.text() === '处理')).toBe(false)
  })
})
