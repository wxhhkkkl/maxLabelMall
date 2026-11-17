import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import UserSubscriptionList from './UserSubscriptionList.vue'

/**
 * 会员详情 - 订阅页签（020 / T085）
 *
 * 钉住的是"赠送行不能显示成空白"：运营赠送的订阅**没有订单号**（source_* 全为 NULL），
 * 若沿用原来的 `sourceOrderNo` 列，客户看到的那一行来源就是空的。
 */
const getSubscriptionPage = vi.fn()
vi.mock('@/api/member/subscription', () => ({
  getSubscriptionPage: (...a: unknown[]) => getSubscriptionPage(...a)
}))

const ROWS = [
  {
    id: 1,
    memberId: 88,
    periodMonths: 12,
    startDate: '2026-01-01',
    endDate: '2027-01-01',
    startTime: '2026-01-01 00:00:00',
    endTime: '2027-01-02 00:00:00',
    active: true,
    remainingDays: 100,
    sourceOrderId: null,
    sourceOrderNo: null,
    licenseGrantId: 501,
    refunded: false,
    createTime: '2026-01-01 00:00:00'
  },
  {
    id: 2,
    memberId: 88,
    periodMonths: 3,
    startDate: '2025-10-01',
    endDate: '2026-01-01',
    startTime: '2025-10-01 00:00:00',
    endTime: '2026-01-02 00:00:00',
    active: false,
    remainingDays: 0,
    sourceOrderId: 1001,
    sourceOrderNo: 'NO-20251001-0001',
    licenseGrantId: null,
    refunded: false,
    createTime: '2025-10-01 00:00:00'
  }
]

const mountList = () =>
  mount(UserSubscriptionList, {
    props: { userId: 88 },
    global: { plugins: [createPinia()] }
  })

beforeEach(() => {
  getSubscriptionPage.mockReset()
  getSubscriptionPage.mockResolvedValue({ list: ROWS, total: 2 })
})

describe('会员详情 - 订阅页签', () => {
  it('赠送行显示「运营赠送」，不是空白', async () => {
    const w = mountList()
    await flushPromises()

    expect(w.text()).toContain('运营赠送')
  })

  it('订单行仍显示订单号', async () => {
    const w = mountList()
    await flushPromises()

    expect(w.text()).toContain('NO-20251001-0001')
  })
})