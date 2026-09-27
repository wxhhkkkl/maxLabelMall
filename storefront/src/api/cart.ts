import { del, get, post, put } from '@/config/http'
import type { CartListResp } from '@/types'

/**
 * 购物车接口（`/app-api/trade/cart/**`），全部需登录。
 *
 * ⚠️ 两条约束：
 *
 * 1. **有效 / 失效条目由后端分好**（`validList` / `invalidList`），前端不得自行
 *    判断下架与售罄（FR-023）。后端已经把"能否购买"算好了，前端照渲染即可。
 *
 * 2. **勾选状态由后端持久化**（`update-selected`），不是纯前端状态 —— 所以刷新、
 *    换设备后勾选仍在。
 */

/** 加入购物车。**数量上限的判定方是后端**（FR-021），前端只做提示与回退展示。 */
export function addToCart(skuId: number, count: number): Promise<number> {
  return post<number>('/trade/cart/add', { skuId, count })
}

/** 购物车列表 —— 返回已分好的有效/失效两组 */
export function listCart(): Promise<CartListResp> {
  return get<CartListResp>('/trade/cart/list')
}

/** 修改数量。超库存时后端会拒绝，前端据此回退。 */
export function updateCartCount(id: number, count: number): Promise<boolean> {
  return put<boolean>('/trade/cart/update-count', { id, count })
}

/** 修改勾选（后端持久化） */
export function updateCartSelected(ids: number[], selected: boolean): Promise<boolean> {
  return put<boolean>('/trade/cart/update-selected', { ids: ids.join(','), selected })
}

/** 删除条目 */
export function deleteCartItems(ids: number[]): Promise<boolean> {
  return del<boolean>('/trade/cart/delete', { ids: ids.join(',') })
}

/** 顶栏角标数量 —— 与列表分开取，避免为了一个数字拉整个列表 */
export function getCartCount(): Promise<number> {
  return get<number>('/trade/cart/get-count')
}
