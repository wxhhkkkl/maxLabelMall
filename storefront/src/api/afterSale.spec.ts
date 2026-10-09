import { beforeEach, describe, expect, it, vi } from 'vitest'

const post = vi.fn()
vi.mock('@/config/http', () => ({
  get: vi.fn(),
  post: (...a: unknown[]) => post(...a),
  put: vi.fn(),
  del: vi.fn(),
}))

const { createAfterSale } = await import('./afterSale')

beforeEach(() => {
  post.mockReset()
})

/**
 * 售后接口（`/app-api/trade/after-sale/**`）。
 *
 * ⚠️ 后端 `AppAfterSaleCreateReqVO` 的必填字段是
 * `orderItemId`(**订单项**编号) / `way` / `refundPrice` / `applyReason`。
 * `orderItemId` 传成**订单**编号会「订单项不存在」—— 这是最容易混的一处。
 */
describe('申请售后', () => {
  it('body 是 { orderItemId, way, refundPrice, applyReason }', async () => {
    post.mockResolvedValue(1024)
    await createAfterSale({
      orderItemId: 11,
      way: 10,
      refundPrice: 7000,
      applyReason: '不想要了',
    })
    expect(post).toHaveBeenCalledWith('/trade/after-sale/create', {
      orderItemId: 11,
      way: 10,
      refundPrice: 7000,
      applyReason: '不想要了',
    })
  })

  it('**没传的可选键不出现在 body**（别给后端塞一个 undefined）', async () => {
    post.mockResolvedValue(1024)
    await createAfterSale({ orderItemId: 11, way: 10, refundPrice: 7000, applyReason: '拍错了' })
    const body = post.mock.calls[0][1] as Record<string, unknown>
    expect(Object.keys(body)).toEqual(['orderItemId', 'way', 'refundPrice', 'applyReason'])
    expect(body).not.toHaveProperty('applyDescription')
    expect(body).not.toHaveProperty('applyPicUrls')
  })

  it('传了补充描述时带上', async () => {
    post.mockResolvedValue(1024)
    await createAfterSale({
      orderItemId: 11,
      way: 20,
      refundPrice: 7000,
      applyReason: '质量问题',
      applyDescription: '外包装破损',
    })
    expect(post).toHaveBeenCalledWith('/trade/after-sale/create', {
      orderItemId: 11,
      way: 20,
      refundPrice: 7000,
      applyReason: '质量问题',
      applyDescription: '外包装破损',
    })
  })

  it('applyPicUrls 为空数组时也不带该键（本期不做图片，但接口要经得起以后用）', async () => {
    post.mockResolvedValue(1024)
    await createAfterSale({
      orderItemId: 11,
      way: 10,
      refundPrice: 7000,
      applyReason: '拍错了',
      applyPicUrls: [],
    })
    const body = post.mock.calls[0][1] as Record<string, unknown>
    expect(body).not.toHaveProperty('applyPicUrls')
  })

  it('返回售后单编号', async () => {
    post.mockResolvedValue(2048)
    expect(await createAfterSale({ orderItemId: 11, way: 10, refundPrice: 1, applyReason: 'x' })).toBe(2048)
  })

  it('失败要向上抛 —— 界面靠后端文案区分「已申请过」「未发货」等原因', async () => {
    post.mockRejectedValue({ message: '订单项已申请售后，无法重复申请' })
    await expect(
      createAfterSale({ orderItemId: 11, way: 10, refundPrice: 7000, applyReason: 'x' }),
    ).rejects.toMatchObject({ message: '订单项已申请售后，无法重复申请' })
  })
})
