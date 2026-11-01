import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const del = vi.fn()
vi.mock('@/config/axios', () => ({
  default: {
    get: (...a: unknown[]) => get(...a),
    post: (...a: unknown[]) => post(...a),
    put: (...a: unknown[]) => put(...a),
    delete: (...a: unknown[]) => del(...a)
  }
}))

const {
  createMaterial,
  deleteMaterial,
  getConfig,
  getMaterialPage,
  saveConfig,
  updateMaterialStatus
} = await import('./index')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
  put.mockReset()
  del.mockReset()
})

describe('后台印刷定制接口（014）', () => {
  it('材质分页走 /trade/customization/material/page', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await getMaterialPage({ pageNo: 1, pageSize: 10, status: 0 })
    expect(get).toHaveBeenCalledWith({
      url: '/trade/customization/material/page',
      params: { pageNo: 1, pageSize: 10, status: 0 }
    })
  })

  it('新增材质走 POST /trade/customization/material/create', async () => {
    post.mockResolvedValue(1)
    await createMaterial({ name: '透明PET' } as never)
    expect(post).toHaveBeenCalledWith({ url: '/trade/customization/material/create', data: { name: '透明PET' } })
  })

  it('停用材质走 PUT update-status', async () => {
    put.mockResolvedValue(true)
    await updateMaterialStatus(3, 1)
    expect(put).toHaveBeenCalledWith({
      url: '/trade/customization/material/update-status',
      params: { id: 3, status: 1 }
    })
  })

  it('删除材质走 DELETE delete?id=', async () => {
    del.mockResolvedValue(true)
    await deleteMaterial(3)
    expect(del).toHaveBeenCalledWith({ url: '/trade/customization/material/delete', params: { id: 3 } })
  })

  it('参数读取与保存走 config/get 与 config/save', async () => {
    get.mockResolvedValue({ printBasePrice: 20000 })
    await getConfig()
    expect(get).toHaveBeenCalledWith({ url: '/trade/customization/config/get' })

    put.mockResolvedValue(true)
    await saveConfig({ printBasePrice: 30000 } as never)
    expect(put).toHaveBeenCalledWith({ url: '/trade/customization/config/save', data: { printBasePrice: 30000 } })
  })
})
