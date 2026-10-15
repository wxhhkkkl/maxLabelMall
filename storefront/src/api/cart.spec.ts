import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const del = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: (...a: unknown[]) => put(...a),
  del: (...a: unknown[]) => del(...a),
}))

const { addToCart, listCart, updateCartCount, updateCartSelected, deleteCartItems, getCartCount } =
  await import('./cart')

/**
 * 购物车接口的**请求形状**（`/app-api/trade/cart/**`）。
 *
 * ⚠️ 本文件是 2026-10-15 补的 —— 在此之前**购物车的接口形状一个测试都没有**：
 * `CartView.spec.ts` 把整个 api 模块 mock 掉了，只断言到"函数被调用"这一层，
 * 于是**发出去的报文长什么样**完全没被验证。
 *
 * 结果就真的漏了一个：`update-selected` 把 `ids` 发成了 **`"1,2"` 这样的字符串**，
 * 而后端 `AppCartUpdateSelectedReqVO.ids` 是 `Collection<Long>` ——
 * Jackson 反序列化不了，接口 400；前端又"失败即回退"，所以表现是
 * **「勾选取消不掉」**（2026-10-15 所有者实测反馈）。
 *
 * 这里把三个**写法不同**的接口都钉住，因为它们靠的是同一件事的两面：
 *   · `put` 发的是 **JSON body** → 集合字段必须是**数组**
 *   · `del` 发的是 **query** → Spring 会把逗号串按逗号拆成集合，**必须**是逗号串
 * 「统一改成数组」或「统一改成字符串」都会打断另一边。
 */
beforeEach(() => {
  get.mockReset()
  post.mockReset()
  put.mockReset()
  del.mockReset()
})

describe('购物车接口的请求形状', () => {
  it('**勾选**：`ids` 必须是数组（后端是 `Collection<Long>`，字符串会被 Jackson 拒成 400）', async () => {
    put.mockResolvedValue(true)
    await updateCartSelected([1, 2], false)

    expect(put).toHaveBeenCalledWith('/trade/cart/update-selected', { ids: [1, 2], selected: false })
  })

  it('取消勾选和勾选走同一条路（`selected: false` 也要能发出去）', async () => {
    put.mockResolvedValue(true)
    await updateCartSelected([7], false)

    expect(put).toHaveBeenCalledWith('/trade/cart/update-selected', { ids: [7], selected: false })
  })

  it('**删除**：`ids` 反过来必须是逗号串 —— 它走 query，Spring 按逗号拆成集合', async () => {
    del.mockResolvedValue(true)
    await deleteCartItems([3, 4])

    expect(del).toHaveBeenCalledWith('/trade/cart/delete', { ids: '3,4' })
  })

  it('改数量按 `{ id, count }` 发 body', async () => {
    put.mockResolvedValue(true)
    await updateCartCount(5, 3)

    expect(put).toHaveBeenCalledWith('/trade/cart/update-count', { id: 5, count: 3 })
  })

  it('加购 / 列表 / 角标各自的路径与参数', async () => {
    post.mockResolvedValue(1)
    get.mockResolvedValue({ validList: [], invalidList: [] })
    await addToCart(9, 2)
    await listCart()
    await getCartCount()

    expect(post).toHaveBeenCalledWith('/trade/cart/add', { skuId: 9, count: 2 })
    expect(get).toHaveBeenCalledWith('/trade/cart/list')
    expect(get).toHaveBeenCalledWith('/trade/cart/get-count')
  })
})
