import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/subscription', () => ({ getSubscriptionPage: vi.fn() }))

import { getSubscriptionPage } from '@/api/member/subscription'
import UserSubscriptionList from './UserSubscriptionList.vue'

const getPage = vi.mocked(getSubscriptionPage)

beforeEach(() => {
  getPage.mockReset()
})

describe('会员订阅页签', () => {
  it('展示周期、到期日、剩余天数、来源订单与退款标记', async () => {
    getPage.mockResolvedValue({
      total: 2,
      list: [
        {
          id: 1, memberId: 1001, periodMonths: 12,
          startDate: '2026-10-20', endDate: '2027-10-20',
          startTime: '2026-10-20T00:00:00', endTime: '2027-10-21T00:00:00',
          active: true, remainingDays: 365, sourceOrderId: 1,
          sourceOrderNo: 'o1', refunded: false, createTime: '2026-10-20T10:00:00'
        },
        {
          id: 2, memberId: 1001, periodMonths: 1,
          startDate: '2026-09-01', endDate: '2026-10-01',
          startTime: '2026-09-01T00:00:00', endTime: '2026-10-02T00:00:00',
          active: false, remainingDays: 0, sourceOrderId: 2,
          sourceOrderNo: 'o2', refunded: true, createTime: '2026-09-01T10:00:00'
        }
      ]
    })

    const wrapper = mount(UserSubscriptionList, { props: { userId: 1001 }, global: { plugins: [createPinia()] } })
    await flushPromises()

    expect(wrapper.text()).toContain('有效')
    expect(wrapper.text()).toContain('已过期')
    expect(wrapper.text()).toContain('来源已退款')
    expect(wrapper.text()).toContain('2027-10-20')
    expect(getPage).toHaveBeenCalledWith({ pageNo: 1, pageSize: 10, userId: 1001 })
  })
})
