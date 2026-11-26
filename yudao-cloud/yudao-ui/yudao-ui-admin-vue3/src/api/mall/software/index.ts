import request from '@/config/axios'

// ==================== 软件分类 ====================

export interface SoftwareCategoryVO {
  id: number
  name: string
  sort: number
  status: number
}

export const getSoftwareCategoryPage = (params: { pageNo: number; pageSize: number; name?: string; status?: number }) =>
  request.get<{ list: SoftwareCategoryVO[]; total: number }>({ url: '/software/category/page', params })

export const getSoftwareCategoryList = () =>
  request.get<SoftwareCategoryVO[]>({ url: '/software/category/list' })

export const createSoftwareCategory = (data: Partial<SoftwareCategoryVO>) =>
  request.post<number>({ url: '/software/category/create', data })

export const updateSoftwareCategory = (data: Partial<SoftwareCategoryVO>) =>
  request.put<boolean>({ url: '/software/category/update', data })

export const deleteSoftwareCategory = (id: number) =>
  request.delete<boolean>({ url: '/software/category/delete', params: { id } })

// ==================== 软件 / 驱动 ====================

export interface SoftwareVO {
  id: number
  categoryId: number
  title: string
  introduction?: string
  type: number
  showInSupport: boolean
  status: number
  createTime?: string
}

export interface SoftwareDetailVO extends SoftwareVO {
  versions: SoftwareVersionVO[]
}

export const getSoftwarePage = (params: {
  pageNo: number
  pageSize: number
  categoryId?: number
  type?: number
  status?: number
  title?: string
}) => request.get<{ list: SoftwareVO[]; total: number }>({ url: '/software/info/page', params })

export const getSoftware = (id: number) =>
  request.get<SoftwareDetailVO>({ url: '/software/info/get', params: { id } })

export const createSoftware = (data: Partial<SoftwareVO>) =>
  request.post<number>({ url: '/software/info/create', data })

export const updateSoftware = (data: Partial<SoftwareVO>) =>
  request.put<boolean>({ url: '/software/info/update', data })

export const deleteSoftware = (id: number) =>
  request.delete<boolean>({ url: '/software/info/delete', params: { id } })

/**
 * 商品「其他设置 → 关联驱动下载」的选项（2026-11-18）
 *
 * 权限按 `product:spu:query` 校验（商品运营不一定有软件分发权限）。
 * 返回全部未删除的软件/驱动，含停用项（停用项用于回显已关联的商品）。
 */
export interface SoftwareSimpleVO {
  id: number
  title: string
  type: number // 1 桌面端软件 2 打印驱动
  status: number // 0 启用 1 停用
}

export const getSoftwareSimpleList = () =>
  request.get<SoftwareSimpleVO[]>({ url: '/software/info/simple-list' })

// ==================== 软件版本 ====================

export interface SoftwareVersionVO {
  id: number
  softwareId: number
  version: string
  platform: string
  arch: string
  fileName: string
  fileSize: number
  sha512: string
  fileUrl: string
  objectKey: string
  releaseNotes?: string
  /** ⚠️ epoch 毫秒数（后端全局按时间戳序列化/反序列化 LocalDateTime），不是 "yyyy-MM-dd" 字符串 */
  releaseTime?: number
  current: boolean
  status: number
  createTime?: string
}

/**
 * 预签名直传复用 infra 既有端点（见 013 contracts/admin-api §3.1）：
 * 传 `directory = software/{platform}-{arch}/{version}`，回来的是 uploadUrl + path + url。
 */
export interface PresignedUploadVO {
  configId: number
  path: string
  uploadUrl: string
  url: string
}

export const getPresignedUploadUrl = (params: { name: string; directory: string }) =>
  request.get<PresignedUploadVO>({ url: '/infra/file/presigned-url', params })

export const getSoftwareVersionPage = (params: { pageNo: number; pageSize: number; softwareId: number }) =>
  request.get<{ list: SoftwareVersionVO[]; total: number }>({ url: '/software/version/page', params })

export const registerSoftwareVersion = (data: {
  softwareId: number
  version: string
  platform: string
  arch: string
  fileName: string
  fileSize: number
  sha512: string
  objectKey: string
  fileUrl: string
  releaseNotes?: string
  /** ⚠️ epoch 毫秒数（后端全局按时间戳序列化/反序列化 LocalDateTime），不是 "yyyy-MM-dd" 字符串 */
  releaseTime?: number
}) => request.post<number>({ url: '/software/version/register', data })

export const publishSoftwareVersion = (id: number, current = false) =>
  request.post<boolean>({ url: '/software/version/publish', params: { id, current } })

export const unpublishSoftwareVersion = (id: number) =>
  request.put<boolean>({ url: '/software/version/unpublish', params: { id } })

export const deleteSoftwareVersion = (id: number) =>
  request.delete<boolean>({ url: '/software/version/delete', params: { id } })
