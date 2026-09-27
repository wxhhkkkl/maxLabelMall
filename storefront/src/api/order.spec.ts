import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const del = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: vi.fn(),
  del: (...a: unknown[]) => del(...a),
}))

const {
  buildSettlementQuery,
  cancelOrder,
  createOrder,
  getOrderDetail,
  pageOrders,
  settlement,
} = await import('./order')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
  del.mockReset()
})

/**
 * 结算的**数组参数必须手工拼 query**：SpringMVC 对带下标的参数名要的是
 * `items[0].skuId=..&items[0].count=..` 这种形式（默认的对象序列化拼不出来）。
 * 注意**下标要百分号编码**，见下面那条用例。
 */
describe('buildSettlementQuery —— 手工拼数组参数', () => {
  it('items 按下标展开成 items[0].skuId / items[0].count', () => {
    const q = buildSettlementQuery({ items: [{ skuId: 11, count: 2 }] })
    expect(q).toContain('items%5B0%5D.skuId=11')
    expect(q).toContain('items%5B0%5D.count=2')
  })

  it('多个 item 的下标递增', () => {
    const q = buildSettlementQuery({
      items: [
        { skuId: 11, count: 1 },
        { skuId: 22, count: 3 },
      ],
    })
    expect(q).toContain('items%5B0%5D.skuId=11')
    expect(q).toContain('items%5B1%5D.skuId=22')
    expect(q).toContain('items%5B1%5D.count=3')
  })

  /**
   * ⚠️ **方括号必须百分号编码。**
   *
   * `[` `]` 不是 RFC 7230/3986 的合法 query 字符，而 yudao 后端**没有**配
   * `server.tomcat.relaxed-query-chars`（也不许我们改后端）。字面写 `items[0].skuId`
   * 会被 Tomcat 直接拒收 —— 返回一张 HTML 的 400 页面，前端只会看到"结算失败"，
   * 排查方向很容易被带到业务错误上去。实测 `items%5B0%5D.skuId` 能被 SpringMVC
   * 正确解码成 `items[0].skuId` 并绑定。
   */
  it('**方括号写成 %5B / %5D**，否则 Tomcat 会以 400 拒收整个请求', () => {
    const q = buildSettlementQuery({ items: [{ skuId: 11, count: 2 }] })
    expect(q).toContain('items%5B0%5D.skuId=11')
    expect(q).toContain('items%5B0%5D.count=2')
    expect(q).not.toContain('items[0]')
  })

  it('从购物车结算时带 cartId', () => {
    const q = buildSettlementQuery({ items: [{ skuId: 11, count: 1, cartId: 50 }] })
    expect(q).toContain('items%5B0%5D.cartId=50')
  })

  it('**pointStatus 恒为 false 且必须传** —— 后端标了 @NotNull，漏传会报错', () => {
    const q = buildSettlementQuery({ items: [{ skuId: 11, count: 1 }] })
    expect(q).toContain('pointStatus=false')
  })

  it('本期固定按快递：deliveryType 传快递值', () => {
    const q = buildSettlementQuery({ items: [{ skuId: 11, count: 1 }] })
    expect(q).toMatch(/deliveryType=\d+/)
  })

  it('选了券时带 couponId，没选则不带', () => {
    expect(buildSettlementQuery({ items: [{ skuId: 11, count: 1 }], couponId: 1024 })).toContain(
      'couponId=1024',
    )
    expect(buildSettlementQuery({ items: [{ skuId: 11, count: 1 }] })).not.toContain('couponId')
  })

  it('有地址时带 addressId', () => {
    const q = buildSettlementQuery({ items: [{ skuId: 11, count: 1 }], addressId: 7 })
    expect(q).toContain('addressId=7')
  })
})

describe('结算请求', () => {
  /**
   * ⚠️ 直购与购物车结算**走同一个端点** `/trade/order/settlement`。
   *
   * 早期把它拆成了两条：直购走 `/trade/order/settlement-product`。那是**契约误读** ——
   * `settlement-product` 只收 `spuIds: List<Long>`、标着 `@PermitAll`、返回的是
   * 「商品列表 / 商品详情用的活动价格信息」（`List<AppTradeProductSettlementRespVO>`），
   * 跟结算下单毫无关系。真正的直购结算是后端 `calculatePrice` 的「情况一：skuId + count
   * 不需要 cartId」。实测该请求返回 code=0 的完整结算响应。
   */
  it('**商品页直购也走 settlement**，且不带 cartId', async () => {
    get.mockResolvedValue({ price: {} })
    await settlement({ items: [{ skuId: 11, count: 1 }] })
    const [url] = get.mock.calls[0] as [string]
    expect(url.startsWith('/trade/order/settlement?')).toBe(true)
    expect(url).toContain('items%5B0%5D.skuId=11')
    expect(url).not.toContain('cartId')
  })

  it('购物车结算走 settlement，带 cartId', async () => {
    get.mockResolvedValue({ price: {} })
    await settlement({ items: [{ skuId: 11, count: 1, cartId: 50 }] })
    const [url] = get.mock.calls[0] as [string]
    expect(url.startsWith('/trade/order/settlement?')).toBe(true)
    expect(url).toContain('items%5B0%5D.cartId=50')
  })
})

describe('下单', () => {
  it('POST /trade/order/create，body 里同样带 pointStatus=false', async () => {
    post.mockResolvedValue({ id: 1, payOrderId: 2 })
    await createOrder({ items: [{ skuId: 11, count: 1 }], addressId: 7 })
    expect(post.mock.calls[0]?.[0]).toBe('/trade/order/create')
    const body = post.mock.calls[0]?.[1] as Record<string, unknown>
    expect(body.pointStatus).toBe(false)
    expect(body.addressId).toBe(7)
  })

  it('下单响应里的 payOrderId 可能是 null（全额券抵扣的订单没有支付单）', async () => {
    post.mockResolvedValue({ id: 1, payOrderId: null })
    const res = await createOrder({ items: [{ skuId: 11, count: 1 }] })
    expect(res.payOrderId).toBeNull()
  })
})

describe('订单查询与取消', () => {
  it('订单列表带状态筛选与分页', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageOrders({ pageNo: 1, pageSize: 10, status: 0 })
    expect(get.mock.calls[0]?.[0]).toBe('/trade/order/page')
    expect((get.mock.calls[0]?.[1] as Record<string, unknown>).status).toBe(0)
  })

  it('订单详情带 id', async () => {
    get.mockResolvedValue({})
    await getOrderDetail(9)
    expect(get.mock.calls[0]?.[1]).toEqual({ id: 9 })
  })

  // 取消是 DELETE 而且 id 走 query（后端 @DeleteMapping + @RequestParam）
  it('**取消订单用 DELETE**，id 作为 query 参数', async () => {
    del.mockResolvedValue(true)
    await cancelOrder(9)
    expect(del.mock.calls[0]?.[0]).toBe('/trade/order/cancel')
    expect(del.mock.calls[0]?.[1]).toEqual({ id: 9 })
  })
})
