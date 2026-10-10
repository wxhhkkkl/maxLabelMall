import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

const { pageComments } = await import('./comment')

beforeEach(() => {
  get.mockReset()
})

/**
 * 商品评价读取（`GET /app-api/product/comment/page`）。
 *
 * 两条契约要点（见 `contracts/app-api.md` §2.1）：
 * - **`type` 是必传参数**，后端没有默认值；本期只做"全部"，所以一律传 `0`；
 * - 后端**只返回可见的评价**，前端**不需要**再过滤一次 —— 前端自加一层过滤是多余逻辑。
 */
describe('商品评价读取', () => {
  it('走 GET /product/comment/page，带上 spuId 与 type=0', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageComments({ spuId: 29502 })
    expect(get).toHaveBeenCalledWith('/product/comment/page', {
      spuId: 29502,
      type: 0,
      pageNo: 1,
      pageSize: 10,
    })
  })

  it('分页参数能覆盖默认值', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageComments({ spuId: 1, pageNo: 3, pageSize: 20 })
    expect(get).toHaveBeenCalledWith('/product/comment/page', {
      spuId: 1,
      type: 0,
      pageNo: 3,
      pageSize: 20,
    })
  })
})
