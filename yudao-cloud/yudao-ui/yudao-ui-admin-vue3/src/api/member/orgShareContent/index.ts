import request from '@/config/axios'

export interface OrgShareOverviewVO {
  quotaBytes: number
  usedBytes: number
  remainingBytes: number
}

export interface OrgShareFolderVO {
  id: number
  name: string
  parentId?: number | null
  memberCount: number
  contentCount?: number
  usedBytes?: number
}

export interface OrgShareContentVO {
  id: number
  folderId?: number | null
  folderName?: string
  contentType: number
  name: string
  sizeBytes: number
  widthMm?: number
  heightMm?: number
  category?: string
  keywords?: string
  uploaderMemberId?: number
  uploaderNickname?: string
  createTime?: string
  storageType?: number
  content?: string | null
  downloadUrl?: string | null
  mimeType?: string
}

export const getOrgShareOverview = (orgId: number) =>
  request.get<OrgShareOverviewVO>({ url: '/member/share-content/org-overview', params: { orgId } })

export const getOrgShareFolders = (orgId: number) =>
  request.get<OrgShareFolderVO[]>({ url: '/member/share-content/org-folders', params: { orgId } })

export const getOrgShareContentPage = (params: {
  orgId: number
  folderId?: number
  contentType?: number
  keyword?: string
  pageNo: number
  pageSize: number
}) => request.get<{ list: OrgShareContentVO[]; total: number }>({
  url: '/member/share-content/org-page',
  params
})

export const getOrgShareContent = (id: number) =>
  request.get<OrgShareContentVO>({ url: '/member/share-content/get', params: { id } })

/** 管理端：会员个人空间容量概览 */
export const getMemberShareOverview = (memberId: number) =>
  request.get<OrgShareOverviewVO>({
    url: '/member/share-content/member-overview',
    params: { memberId }
  })

/** 管理端：会员个人空间文件夹（含父级，前端组树） */
export const getMemberShareFolders = (memberId: number) =>
  request.get<OrgShareFolderVO[]>({
    url: '/member/share-content/member-folders',
    params: { memberId }
  })

/** 管理端：会员个人空间内容分页 */
export const getMemberShareContentPage = (params: {
  memberId: number
  folderId?: number | null
  contentType?: number
  keyword?: string
  pageNo: number
  pageSize: number
}) => request.get<{ list: OrgShareContentVO[]; total: number }>({
  url: '/member/share-content/member-page',
  params
})

/** 管理端：会员个人空间内容详情 */
export const getMemberShareContent = (memberId: number, id: number) =>
  request.get<OrgShareContentVO>({
    url: '/member/share-content/member-get',
    params: { memberId, id }
  })
