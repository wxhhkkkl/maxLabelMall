import request from '@/config/axios'

export interface PlatformContentVO {
  id?: number
  contentType: number
  name: string
  sizeBytes?: number
  widthMm?: number
  heightMm?: number
  category?: string
  keywords?: string
  status?: number
  publisherUserId?: number
  publishedTime?: Date
  createTime?: Date
  thumb?: string
  storageType?: number
  content?: string
  downloadUrl?: string
  mimeType?: string
}

export interface PlatformContentPageReqVO extends PageParam {
  keyword?: string
  contentType?: number
  category?: string
  status?: number
}

export interface PlatformContentCategoryVO {
  id?: number
  name: string
  sort?: number
}

export const PlatformContentApi = {
  getPage: async (params: PlatformContentPageReqVO) => {
    return await request.get<PageResult<PlatformContentVO[]>>({
      url: '/member/platform-content/page',
      params
    })
  },
  get: async (id: number) => {
    return await request.get<PlatformContentVO>({ url: '/member/platform-content/get', params: { id } })
  },
  create: async (data: PlatformContentVO) => {
    return await request.post<number>({ url: '/member/platform-content/create', data })
  },
  update: async (data: PlatformContentVO) => {
    return await request.put<boolean>({ url: '/member/platform-content/update', data })
  },
  upload: async (data: FormData) => {
    return await request.upload<number>({ url: '/member/platform-content/upload', data })
  },
  updateStatus: async (id: number, status: number) => {
    return await request.put<boolean>({ url: '/member/platform-content/update-status', data: { id, status } })
  },
  delete: async (id: number) => {
    return await request.delete<boolean>({ url: '/member/platform-content/delete', params: { id } })
  },
  getCategories: async () => {
    return await request.get<PlatformContentCategoryVO[]>({ url: '/member/platform-content/category/list' })
  },
  createCategory: async (name: string, sort?: number) => {
    return await request.post<number>({ url: '/member/platform-content/category/create', data: { name, sort } })
  },
  deleteCategory: async (id: number) => {
    return await request.delete<boolean>({ url: '/member/platform-content/category/delete', params: { id } })
  }
}
