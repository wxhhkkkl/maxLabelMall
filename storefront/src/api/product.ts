import { get } from '@/config/http'
import type { PageResult, ProductSortField, ProductSpu } from '@/types'

/**
 * 商品与分类接口（`/app-api/product/**`）。
 *
 * ⚠️ 排序字段**必须小驼峰**：后端 `AppProductSpuPageReqVO` 有
 * `@AssertTrue(message = "排序字段不合法")`，白名单只认 `price` / `salesCount`。
 * 传 `PRICE` / `SALES_COUNT` 会被判为不合法；**`createTime` 虽有常量但不在白名单里**，
 * 同样会被拒。故本模块只暴露这两个值。
 */
export const SORT_FIELD = {
  sales: 'salesCount',
  price: 'price',
} as const satisfies Record<string, ProductSortField>

export interface ProductPageParams {
  pageNo?: number
  pageSize?: number
  categoryId?: number
  categoryIds?: number[]
  keyword?: string
  sortField?: ProductSortField
  sortAsc?: boolean
  /**
   * 筛选维度的可选值（特性 003）。
   *
   * ⚠️ **每个维度至多一个值**（前台同维度单选，Q9=B）。同一维度传多个值，
   * 后端会**明确拒绝**（非法请求）而不是静默取一个。
   */
  filterValueIds?: number[]
}

/** 设计稿的三种排序 → 后端参数。综合排序不传 sortField，由后端默认排序。 */
export type ProductSortKey = 'default' | 'sales' | 'priceAsc'

export function toPageParams(params: ProductPageParams): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  if (params.pageNo !== undefined) out.pageNo = params.pageNo
  if (params.pageSize !== undefined) out.pageSize = params.pageSize
  if (params.categoryId !== undefined) out.categoryId = params.categoryId
  if (params.categoryIds?.length) out.categoryIds = params.categoryIds.join(',')
  if (params.keyword) out.keyword = params.keyword
  // 综合排序刻意不传 sortField（undefined 的键不会出现在 query 里）
  if (params.sortField) out.sortField = params.sortField
  if (params.sortAsc !== undefined) out.sortAsc = params.sortAsc
  // 空数组 = 不施加条件（与既有 categoryIds / keyword 的处理一致）
  if (params.filterValueIds?.length) out.filterValueIds = params.filterValueIds.join(',')
  return out
}

export function pageProducts(params: ProductPageParams = {}): Promise<PageResult<ProductSpu>> {
  return get<PageResult<ProductSpu>>('/product/spu/page', toPageParams(params))
}

export function getProductDetail(id: number): Promise<ProductSpu> {
  return get<ProductSpu>('/product/spu/get-detail', { id })
}

export function listProductsByIds(ids: number[]): Promise<ProductSpu[]> {
  return get<ProductSpu[]>('/product/spu/list-by-ids', { ids: ids.join(',') })
}
