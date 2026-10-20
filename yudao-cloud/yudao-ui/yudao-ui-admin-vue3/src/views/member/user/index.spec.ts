import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/user', () => ({
  getUserPage: vi.fn()
}))
vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => ({ push: vi.fn() })
}))

import { getUserPage } from '@/api/member/user'
import MemberUserIndex from './index.vue'

const getPage = vi.mocked(getUserPage)
const directive = { mounted: () => {} }
const stubs = {
  'doc-alert': true,
  MemberTagSelect: true,
  MemberLevelSelect: true,
  MemberGroupSelect: true,
  UserLevelUpdateForm: true,
  UserPointUpdateForm: true,
  UserBalanceUpdateForm: true,
  UserForm: true,
  CouponSendForm: true
}

beforeEach(() => {
  getPage.mockReset()
})

describe('会员列表订阅列', () => {
  it('展示有效 / 已过期 / 未订阅三种状态与到期日', async () => {
    getPage.mockResolvedValue({
      total: 3,
      list: [
        { id: 1, mobile: '13800000001', subscriptionActive: true, subscriptionEndDate: '2027-10-20' },
        { id: 2, mobile: '13800000002', subscriptionActive: false, subscriptionEndDate: '2026-09-01' },
        { id: 3, mobile: '13800000003', subscriptionActive: false }
      ] as never
    })

    const wrapper = mount(MemberUserIndex, {
      global: {
        plugins: [createPinia()],
        directives: { hasPermi: directive },
        stubs
      }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('有效')
    expect(wrapper.text()).toContain('已过期')
    expect(wrapper.text()).toContain('未订阅')
    expect(wrapper.text()).toContain('2027-10-20')
  })
})
