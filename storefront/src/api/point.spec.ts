import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

const { pagePointRecords } = await import('./point')

beforeEach(() => {
  get.mockReset()
})

/**
 * 积分明细（`GET /app-api/member/point/record/page`）。
 *
 * ⚠️ `point` 是**变动值**（正负即增减），不是余额；余额在 `/member/user/get` 的 `point` 上。
 */
describe('积分记录', () => {
  it('按分页参数查，走 GET /member/point/record/page', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pagePointRecords({ pageNo: 1, pageSize: 10 })
    expect(get).toHaveBeenCalledWith('/member/point/record/page', { pageNo: 1, pageSize: 10 })
  })

  it('不传参数时也能查（后端有默认值）', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pagePointRecords()
    expect(get).toHaveBeenCalledWith('/member/point/record/page', {})
  })
})
