import { get } from '@/config/http'

/**
 * 软件与驱动（`/app-api/software/**`，013）。
 *
 * 服务支持页公开免登录；只返回「当前发布」版本，历史版本不对外（Q84）。
 */

export interface SoftwareCategory {
  id: number
  name: string
  sort: number
}

export interface Software {
  id: number
  categoryId: number
  title: string
  introduction?: string
  type: number
  latestVersion?: string
  platforms?: string[]
}

export interface SoftwareVersion {
  version: string
  platform: string
  arch: string
  fileName: string
  fileSize: number
  releaseTime?: string
  /** 对象存储的公开永久直链 */
  downloadUrl: string
}

export interface SoftwareDetail extends Software {
  versions: SoftwareVersion[]
}

export const listSoftwareCategories = () => get<SoftwareCategory[]>('/software/category/list')

export const listSoftwareByCategory = (categoryId: number) =>
  get<Software[]>('/software/list', { categoryId })

export const getSoftwareDetail = (id: number) => get<SoftwareDetail>('/software/get', { id })

/** 服务支持页平铺：所有可展示软件 + 各自的当前发布版本 */
export const listAllSoftware = () => get<SoftwareDetail[]>('/software/all')
