import { describe, expect, it, vi } from 'vitest'

vi.mock('@/config/http', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))

import { get, post, put } from '@/config/http'
import { assignOrgSeat, getMyOrg, listOrgMembers } from './org'

const requestGet = vi.mocked(get)
const requestPost = vi.mocked(post)
const requestPut = vi.mocked(put)

describe('企业中心 API', () => {
  it('读取我的企业与成员分页', async () => {
    requestGet.mockResolvedValueOnce({ orgId: 88, orgName: '测试企业', role: 1, seatAvailable: 2 })
    const org = await getMyOrg()
    expect(requestGet).toHaveBeenCalledWith('/member/org/get')
    expect(org.orgName).toBe('测试企业')

    requestGet.mockResolvedValueOnce({ list: [{ memberId: 1002 }], total: 1 })
    const members = await listOrgMembers({ pageNo: 1, pageSize: 10 })
    expect(requestGet).toHaveBeenLastCalledWith('/member/org/member/page', { pageNo: 1, pageSize: 10 })
    expect(members.total).toBe(1)
  })

  it('席位分配走 POST 且带 memberId', async () => {
    requestPost.mockResolvedValue(true)
    await assignOrgSeat(1002)
    expect(requestPost).toHaveBeenCalledWith('/member/org/seat/assign', { memberId: 1002 })
  })

  it('调整角色走 PUT', async () => {
    requestPut.mockResolvedValue(true)
    const { updateOrgMemberRole } = await import('./org')
    await updateOrgMemberRole(1002, 2)
    expect(requestPut).toHaveBeenCalledWith('/member/org/member/update-role', { memberId: 1002, role: 2 })
  })
})
