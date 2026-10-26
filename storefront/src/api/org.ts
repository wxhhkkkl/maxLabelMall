import { get, post, put } from '@/config/http'

/** 我的企业概览（`/app-api/member/org/get`），需登录。 */
export interface MemberOrg {
  orgId: number | null
  orgName?: string
  role?: number
  status?: number
  seatActive?: boolean
  seatEndDate?: string | null
  memberCount?: number
  seatTotal?: number
  seatUsed?: number
  seatAvailable?: number
  licenseEndDate?: string | null
}

export interface OrgMember {
  memberId: number
  nickname?: string
  mobileMasked?: string
  role: number
  seatActive: boolean
  seatEndDate?: string | null
  joinedTime?: string
}

export interface OrgLicense {
  id: number
  skuId: number
  periodMonths: number
  seatsTotal: number
  startDate: string
  endDate: string
  remainingDays: number
  sourceOrderNo: string
  refunded: boolean
  active: boolean
}

export function getMyOrg(): Promise<MemberOrg> {
  return get<MemberOrg>('/member/org/get')
}

export function listOrgMembers(params: {
  pageNo: number
  pageSize: number
  keyword?: string
}): Promise<{ list: OrgMember[]; total: number }> {
  return get<{ list: OrgMember[]; total: number }>('/member/org/member/page', params)
}

export function addOrgMember(mobile: string): Promise<boolean> {
  return post<boolean>('/member/org/member/add', { mobile })
}

export function removeOrgMember(memberId: number): Promise<boolean> {
  return post<boolean>('/member/org/member/remove', { memberId })
}

export function updateOrgMemberRole(memberId: number, role: number): Promise<boolean> {
  return put<boolean>('/member/org/member/update-role', { memberId, role })
}

export function transferOrgOwner(memberId: number): Promise<boolean> {
  return post<boolean>('/member/org/owner/transfer', { memberId })
}

export function assignOrgSeat(memberId: number): Promise<boolean> {
  return post<boolean>('/member/org/seat/assign', { memberId })
}

export function revokeOrgSeat(memberId: number): Promise<boolean> {
  return post<boolean>('/member/org/seat/revoke', { memberId })
}

export function listOrgLicenses(params: {
  pageNo: number
  pageSize: number
}): Promise<{ list: OrgLicense[]; total: number }> {
  return get<{ list: OrgLicense[]; total: number }>('/member/org/license/page', params)
}

export function leaveOrg(): Promise<boolean> {
  return post<boolean>('/member/org/leave')
}
