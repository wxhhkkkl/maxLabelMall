import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/user', () => ({
  getUserPage: vi.fn(async () => ({ list: [], total: 0 }))
}))
vi.mock('@/api/member/org', () => ({
  getOrgPage: vi.fn(),
  getOrg: vi.fn(),
  createOrg: vi.fn(),
  updateOrg: vi.fn(),
  updateOrgStatus: vi.fn(),
  getOrgMemberPage: vi.fn(),
  getOrgLicensePage: vi.fn(),
  addOrgMember: vi.fn(),
  removeOrgMember: vi.fn(),
  updateOrgMemberRole: vi.fn(),
  transferOrgOwner: vi.fn(),
  assignOrgSeat: vi.fn(),
  revokeOrgSeat: vi.fn()
}))

import { getOrgPage } from '@/api/member/org'
import OrgIndex from './index.vue'

const getPage = vi.mocked(getOrgPage)
const directive = { mounted: () => {} }

beforeEach(() => {
  getPage.mockReset()
})

describe('企业管理页', () => {
  it('展示企业、席位与状态', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [
        {
          id: 88,
          name: '测试企业',
          ownerMemberId: 1001,
          ownerNickname: '张三',
          ownerMobile: '138****8000',
          status: 0,
          memberCount: 5,
          seatTotal: 20,
          seatUsed: 3,
          licenseEndDate: '2027-10-26'
        }
      ]
    })
    const wrapper = mount(OrgIndex, {
      global: { plugins: [createPinia()], directives: { hasPermi: directive } }
    })
    await flushPromises()
    expect(wrapper.text()).toContain('测试企业')
    expect(wrapper.text()).toContain('3 / 20')
    expect(wrapper.text()).toContain('2027-10-26')
  })
})
