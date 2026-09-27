import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const getCartCount = vi.fn()
const addToCart = vi.fn()
vi.mock('@/api/cart', () => ({
  getCartCount: (...a: unknown[]) => getCartCount(...a),
  addToCart: (...a: unknown[]) => addToCart(...a),
  listCart: vi.fn(),
  updateCartCount: vi.fn(),
  updateCartSelected: vi.fn(),
  deleteCartItems: vi.fn(),
}))

const getMemberUser = vi.fn()
vi.mock('@/api/member', () => ({
  getMemberUser: (...a: unknown[]) => getMemberUser(...a),
  logout: vi.fn().mockResolvedValue(true),
}))

const { useCartStore } = await import('./cart')
const { useUserStore } = await import('./user')

beforeEach(() => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  getCartCount.mockReset()
  getCartCount.mockResolvedValue(0)
  addToCart.mockReset()
  addToCart.mockResolvedValue(1)
  getMemberUser.mockReset()
  getMemberUser.mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' })
})

describe('顶栏角标数量', () => {
  it('未登录时不请求，数量为 0', async () => {
    const c = useCartStore()
    await c.refreshCount()
    expect(getCartCount).not.toHaveBeenCalled()
    expect(c.count).toBe(0)
  })

  it('**数量来自 `get-count`，不是本地累加**', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    getCartCount.mockResolvedValue(7)
    const c = useCartStore()
    await c.refreshCount()
    expect(getCartCount).toHaveBeenCalled()
    expect(c.count).toBe(7)
  })

  it('请求失败时角标不炸，回落到 0', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    getCartCount.mockRejectedValue(new Error('Network Error'))
    const c = useCartStore()
    await c.refreshCount()
    expect(c.count).toBe(0)
  })
})

describe('登录后重新拉取（FR-022）', () => {
  it('登录成功后角标会被重新拉取 —— 购物车绑在账号上，换设备/重登都能看到', async () => {
    const { setTokens } = await import('@/utils/auth')
    const u = useUserStore()
    const c = useCartStore()
    c.$patch({ count: 0 })

    // 模拟登录成功：令牌写入 → 同步登录态
    setTokens('at-1', 'rt-1')
    getCartCount.mockResolvedValue(3)
    await u.syncAfterLogin()
    await c.refreshCount()

    expect(u.isLogin).toBe(true)
    expect(c.count).toBe(3)
  })

  it('退出登录后角标归零', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    getCartCount.mockResolvedValue(5)
    const c = useCartStore()
    await c.refreshCount()
    expect(c.count).toBe(5)

    await useUserStore().logout()
    c.reset()
    expect(c.count).toBe(0)
  })
})

describe('加购与角标刷新', () => {
  it('加购成功后刷新角标', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    const c = useCartStore()
    getCartCount.mockResolvedValue(1)
    await c.add(101, 1)
    expect(addToCart).toHaveBeenCalledWith(101, 1)
    expect(c.count).toBe(1)
  })
})

describe('下单后的角标刷新（FR-031 / FR-019）', () => {
  it('**下单成功后角标重新拉取**，拿到的是后端删完已结算条目后的数量', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    const c = useCartStore()
    getCartCount.mockResolvedValue(3)
    await c.refreshCount()
    expect(c.count).toBe(3)

    // 下单成功：后端已把被结算的两件从购物车删掉，数量变 1
    getCartCount.mockResolvedValue(1)
    await c.onOrderPlaced()
    expect(c.count).toBe(1)
  })

  it('**前端不重复调用删除接口** —— 删除是后端 `createOrder` 的副作用', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    const { deleteCartItems } = await import('@/api/cart')
    const c = useCartStore()
    await c.onOrderPlaced()
    // see TradeOrderUpdateServiceImpl#createOrder：cartId 非空时服务端已 deleteCart。
    // 前端再删一次是重复删除，会把用户下单后新加的同名条目也带走。
    expect(deleteCartItems).not.toHaveBeenCalled()
  })
})

describe('未登录加购的意图暂存（FR-015）', () => {
  it('未登录时把加购意图存下来，登录成功后自动执行', async () => {
    const c = useCartStore()
    getCartCount.mockResolvedValue(1)
    await c.add(101, 2)

    // 未登录 → 没发请求，而是暂存意图
    expect(addToCart).not.toHaveBeenCalled()
    expect(c.pendingIntent).toEqual({ skuId: 101, count: 2 })

    // 登录成功后自动补上，不要求用户重新点击
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    await c.flushPendingIntent()
    expect(addToCart).toHaveBeenCalledWith(101, 2)
    expect(c.pendingIntent).toBeNull()
  })

  it('没有待执行意图时 flush 不发请求', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    await useCartStore().flushPendingIntent()
    expect(addToCart).not.toHaveBeenCalled()
  })
})
