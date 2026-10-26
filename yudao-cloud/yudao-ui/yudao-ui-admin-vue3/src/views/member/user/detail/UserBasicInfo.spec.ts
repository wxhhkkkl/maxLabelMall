import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/org', () => ({ getOrgByMember: vi.fn() }))

import { getOrgByMember } from '@/api/member/org'
import UserBasicInfo from './UserBasicInfo.vue'

describe('会员详情 - 所属企业', () => {
  it('展示企业、角色与席位状态', async () => {
    vi.mocked(getOrgByMember).mockResolvedValue({
      orgId: 88,
      orgName: '测试企业',
      role: 3,
      seatActive: true,
      seatEndDate: '2027-10-26'
    })

    const wrapper = mount(UserBasicInfo, {
      props: { user: { id: 1002, nickname: '张三', mobile: '13800138000' } as any },
      global: { stubs: { dictTag: true } }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('所属企业')
    expect(wrapper.text()).toContain('测试企业')
    expect(wrapper.text()).toContain('成员')
    expect(wrapper.text()).toContain('席位有效至 2027-10-26')
  })

  it('未加入企业时显示未加入', async () => {
    vi.mocked(getOrgByMember).mockResolvedValue({ orgId: undefined })

    const wrapper = mount(UserBasicInfo, {
      props: { user: { id: 1002, nickname: '张三', mobile: '13800138000' } as any },
      global: { stubs: { dictTag: true } }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('未加入')
  })
})
