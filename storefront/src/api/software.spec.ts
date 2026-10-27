import { describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/config/http', () => ({ get: (...args: unknown[]) => get(...args) }))

import { getSoftwareDetail, listSoftwareByCategory, listSoftwareCategories } from './software'

describe('软件与驱动 API（013）', () => {
  it('分类列表走公开接口', async () => {
    get.mockResolvedValue([])
    await listSoftwareCategories()
    expect(get).toHaveBeenCalledWith('/software/category/list')
  })

  it('按分类取软件时带上 categoryId', async () => {
    get.mockResolvedValue([])
    await listSoftwareByCategory(3)
    expect(get).toHaveBeenCalledWith('/software/list', { categoryId: 3 })
  })

  it('详情按 id 取', async () => {
    get.mockResolvedValue(null)
    await getSoftwareDetail(10)
    expect(get).toHaveBeenCalledWith('/software/get', { id: 10 })
  })
})
