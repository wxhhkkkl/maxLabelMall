import { defineStore } from 'pinia'
import { ref } from 'vue'

import { addToCart, getCartCount } from '@/api/cart'
import { getAccessToken } from '@/utils/auth'

/**
 * 购物车角标与加购入口。
 *
 * 三条约束：
 *
 * 1. **角标数量来自后端 `get-count`，不是本地累加**（FR-019）。本地累加在换设备、
 *    多标签页、后台改数量时都会失真；后端才是权威。
 *
 * 2. **购物车绑在账号上**（FR-022）。所以登录成功后必须**重新拉取** —— 否则用户
 *    退出再登录会看到别人（或空）的角标。刷新时机：登录后、加购后、改量后、删除后、
 *    下单后。
 *
 * 3. **未登录时加购要暂存意图**（FR-015）：把这次加购记下来，登录成功后自动补上，
 *    不要求用户重新点击一遍。这是"登录中断"最容易被做丢的一环。
 */
export const useCartStore = defineStore('cart', () => {
  const count = ref(0)

  /** 未登录时暂存的加购意图；登录成功后由 flushPendingIntent 消费 */
  const pendingIntent = ref<{ skuId: number; count: number } | null>(null)

  /** 从后端拉取角标数量。未登录时不请求（后端会返回 401），直接归零。 */
  async function refreshCount(): Promise<void> {
    if (!getAccessToken()) {
      count.value = 0
      return
    }
    try {
      count.value = await getCartCount()
    } catch {
      // 角标拉取失败不该影响页面其余部分；保持在旧值之外只做兜底归零
      count.value = 0
    }
  }

  /**
   * 加购。
   *
   * 已登录 → 直接加购并刷新角标；
   * 未登录 → **不发请求**，把意图暂存下来（由调用方引导登录，登录后自动执行）。
   */
  async function add(skuId: number, num = 1): Promise<void> {
    if (!getAccessToken()) {
      pendingIntent.value = { skuId, count: num }
      return
    }
    await addToCart(skuId, num)
    await refreshCount()
  }

  /** 登录成功后调用：把暂存的加购意图补上，然后清空 */
  async function flushPendingIntent(): Promise<void> {
    const intent = pendingIntent.value
    if (!intent) {
      await refreshCount()
      return
    }
    pendingIntent.value = null
    try {
      await addToCart(intent.skuId, intent.count)
    } finally {
      await refreshCount()
    }
  }

  /**
   * 下单成功后调用（FR-031）。
   *
   * **只同步角标，不在这里删条目** —— 后端 `TradeOrderUpdateServiceImpl#createOrder`
   * 在请求带 `cartId` 时已经 `cartService.deleteCart(...)` 了。前端再删一次是重复删除。
   *
   * 也不能复用 `flushPendingIntent()` 来达到"顺手刷新"的效果：那个方法在存在待执行
   * 加购意图时**会真的往购物车加一件商品**，在结算页触发它等于凭空多一件。
   */
  async function onOrderPlaced(): Promise<void> {
    await refreshCount()
  }

  /** 退出登录后调用 */
  function reset(): void {
    count.value = 0
    pendingIntent.value = null
  }

  return { count, pendingIntent, refreshCount, add, flushPendingIntent, onOrderPlaced, reset }
})
