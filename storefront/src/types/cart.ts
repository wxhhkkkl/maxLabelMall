import type { ProductSku } from './product'

interface CartSpu {
  id: number
  name: string
  picUrl: string
  price: number
  stock: number
}

/** 购物车条目（AppCartListRespVO.Cart） */
export interface CartItem {
  id: number
  count: number
  /** 勾选状态由**后端持久化**（/trade/cart/update-selected） */
  selected: boolean
  spu: CartSpu
  sku: ProductSku
}

/**
 * 购物车列表。**有效/失效已由后端分好** —— 前端不得自行判断下架与售罄（FR-023）。
 */
export interface CartListResp {
  validList: CartItem[]
  invalidList: CartItem[]
}
