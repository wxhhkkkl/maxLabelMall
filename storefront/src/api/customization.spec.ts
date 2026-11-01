import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: vi.fn(),
  del: vi.fn(),
}))

const { createCustomizationOrder, listCustomizationMaterials, quoteCustomization } =
  await import('./customization')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

describe('印刷定制接口（014）', () => {
  it('材质列表走 GET /trade/customization/material/list', async () => {
    get.mockResolvedValue([{ id: 1, name: '透明PET' }])
    const list = await listCustomizationMaterials()
    expect(get).toHaveBeenCalledWith('/trade/customization/material/list')
    expect(list[0].name).toBe('透明PET')
  })

  it('报价走 POST /trade/customization/quote，body 是规格', async () => {
    post.mockResolvedValue({ payable: 100 })
    await quoteCustomization({
      materialId: 1, width: 50, height: 100, quantity: 1000,
      mode: 2, dieCut: false, urgent: false,
    })
    expect(post).toHaveBeenCalledWith('/trade/customization/quote', {
      materialId: 1, width: 50, height: 100, quantity: 1000,
      mode: 2, dieCut: false, urgent: false,
    })
  })

  it('建单走 POST /trade/customization/order/create，带 expectedAmount 与文件', async () => {
    post.mockResolvedValue({ changed: false, id: 9, payOrderId: 10 })
    const resp = await createCustomizationOrder({
      materialId: 1, width: 50, height: 100, quantity: 1000,
      mode: 2, dieCut: false, urgent: false,
      files: [{ name: 'a.pdf', url: 'https://img.yuwangchenfa.com/a.pdf' }],
      expectedAmount: 34900,
      addressId: 7,
      deliveryType: 1,
    })
    expect(post).toHaveBeenCalledWith('/trade/customization/order/create', expect.objectContaining({
      expectedAmount: 34900,
      addressId: 7,
      files: [{ name: 'a.pdf', url: 'https://img.yuwangchenfa.com/a.pdf' }],
    }))
    expect(resp.id).toBe(9)
  })

  it('改价响应原样透传 changed 与新报价（Q101）', async () => {
    post.mockResolvedValue({ changed: true, quote: { payable: 35900 } })
    const resp = await createCustomizationOrder({
      materialId: 1, width: 50, height: 100, quantity: 1000,
      mode: 2, dieCut: false, urgent: false,
      files: [], expectedAmount: 34900,
    })
    expect(resp.changed).toBe(true)
    expect(resp.quote?.payable).toBe(35900)
  })
})
