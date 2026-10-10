import { beforeEach, describe, expect, it, vi } from 'vitest'

const post = vi.fn()
const del = vi.fn()
const get = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: vi.fn(),
  del: (...a: unknown[]) => del(...a),
}))

const { cancelAfterSale, createAfterSale, pageAfterSales } = await import('./afterSale')

beforeEach(() => {
  post.mockReset()
  del.mockReset()
  get.mockReset()
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

/**
 * 撤销退款申请。
 *
 * ⚠️ 参数是**售后单编号**（订单项上的 `afterSaleId`），不是订单项编号、更不是订单编号。
 * ⚠️ 走 **DELETE**，参数在 **query** 上（后端是 `@RequestParam`），不是 body。
 */
describe('撤销售后申请', () => {
  it('按售后单编号撤销，参数走 query', async () => {
    del.mockResolvedValue(true)
    await cancelAfterSale(2048)
    expect(del).toHaveBeenCalledWith('/trade/after-sale/cancel', { id: 2048 })
  })

  it('失败要向上抛 —— 状态不允许撤销时（如商家已收货待退款）后端会拒，文案要能透给用户', async () => {
    del.mockRejectedValue({ message: '售后单状态不允许取消' })
    await expect(cancelAfterSale(2048)).rejects.toMatchObject({ message: '售后单状态不允许取消' })
  })
})

/**
 * 「我的售后」列表。
 *
 * ⚠️ 它返回的是**售后单的精确状态**（10/20/30/40/50/61/62/63），
 * 这是列表页能**准确**判断能否撤销的前提（见 utils/afterSale 的说明）。
 */
describe('售后列表', () => {
  it('按分页参数查，走 GET /trade/after-sale/page', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageAfterSales({ pageNo: 1, pageSize: 10 })
    expect(get).toHaveBeenCalledWith('/trade/after-sale/page', { pageNo: 1, pageSize: 10 })
  })

  it('不传参数时也能查（后端有默认值）', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageAfterSales()
    expect(get).toHaveBeenCalledWith('/trade/after-sale/page', {})
  })
})
