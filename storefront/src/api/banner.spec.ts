import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

const { BANNER_POSITION, listBanners } = await import('./banner')

beforeEach(() => {
  get.mockReset()
})

/**
 * 横幅读取（`GET /app-api/promotion/banner/list`）。
 *
 * ⚠️ **`position` 是必传参数**（后端没有默认值），缺了直接 400 —— 所以类型上就要求传。
 *
 * ⚠️ 该接口**不按 `status` 过滤**：后端只按 position 等值查询，**停用的 banner 也会被返回**。
 * 这是既有行为，前端**不做**状态判断（响应里根本没有 status 字段）——
 * 若将来要"停用即不展示"，那是后端的改动。见 contracts/app-api.md §5.1。
 */
describe('横幅读取', () => {
  it('走 GET /promotion/banner/list，并带上 position', async () => {
    get.mockResolvedValue([])
    await listBanners(BANNER_POSITION.MALL)
    expect(get).toHaveBeenCalledWith('/promotion/banner/list', { position: 6 })
  })

  it('首页位置是 1（既有取值，不要改）', async () => {
    get.mockResolvedValue([])
    await listBanners(BANNER_POSITION.HOME)
    expect(get).toHaveBeenCalledWith('/promotion/banner/list', { position: 1 })
  })

  it('商城页位置是 6（本项目新增，与后端枚举、字典三处必须一致）', () => {
    expect(BANNER_POSITION.MALL).toBe(6)
  })

  it('后端返回的不是数组时回落成空数组（不让页面崩）', async () => {
    get.mockResolvedValue(null)
    expect(await listBanners(6)).toEqual([])
  })
})
