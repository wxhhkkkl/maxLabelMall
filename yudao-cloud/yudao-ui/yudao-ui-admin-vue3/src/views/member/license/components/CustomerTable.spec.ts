import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import CustomerTable from './CustomerTable.vue'

// Pagination / ContentWrap 内部会用到 store，测试里必须接上 Pinia
const mountTable = (props: Record<string, unknown> = {}) =>
  mount(CustomerTable, { props, global: { plugins: [createPinia()] } })

/**
 * 客户授权明细（020 / US68）
 *
 * 钉三件事：
 * · 双来源会员同时显示两种来源（不把"只有企业席位"的人显示成未订阅）
 * · 已过期行显示「已过期 N 天」，剩余天数不塞负数
 * · **明细行不再有操作列**（2026-11-18）：赠送只保留看板右上角一个入口
 */
const getLicenseCustomerPage = vi.fn()
vi.mock('@/api/member/license', () => ({
  getLicenseCustomerPage: (...a: unknown[]) => getLicenseCustomerPage(...a),
  getLicenseSummary: vi.fn(),
  grantLicense: vi.fn(),
  getLicenseGrantPage: vi.fn()
}))

const ROWS = [
  {
    targetType: 1,
    targetId: 88,
    targetName: '小王',
    targetMobile: '13800008888',
    sources: ['ORDER', 'ORG_SEAT'],
    startTime: new Date(2026, 0, 1).getTime(),
    endTime: new Date(2027, 0, 1).getTime(),
    remainingDays: 210,
    expiredDays: 0,
    active: true,
    personalEndTime: new Date(2026, 11, 1).getTime(),
    orgEndTime: new Date(2027, 0, 1).getTime(),
    maxGiftMonths: 2
  },
  {
    targetType: 2,
    targetId: 9,
    targetName: '某企业',
    sources: ['ORDER'],
    startTime: new Date(2025, 0, 1).getTime(),
    endTime: new Date(2026, 0, 1).getTime(),
    remainingDays: 0,
    expiredDays: 3,
    active: false,
    maxGiftMonths: 12
  }
]

beforeEach(() => {
  getLicenseCustomerPage.mockReset()
  getLicenseCustomerPage.mockResolvedValue({ list: ROWS, total: 2 })
})

describe('客户授权明细', () => {
  it('双来源行显示两种来源，不把企业席位显示成未订阅', async () => {
    const w = mountTable()
    await flushPromises()

    expect(w.text()).toContain('小王')
    expect(w.text()).toContain('订单购买')
    expect(w.text()).toContain('企业席位')
    expect(w.text()).not.toContain('未订阅')
  })

  it('已过期行：显示「已过期 N 天」而不是负数剩余', async () => {
    const w = mountTable()
    await flushPromises()

    expect(w.text()).toContain('已过期 3 天')
  })

  it('明细行没有「操作」列，也没有行内赠送按钮（2026-11-18）', async () => {
    const w = mountTable()
    await flushPromises()

    const headers = w
      .findAll('.el-table__header th')
      .map((th) => th.text())
      .filter(Boolean)
    expect(headers).not.toContain('操作')
    expect(w.findAll('button').some((b) => b.text().includes('赠送'))).toBe(false)
  })
})
