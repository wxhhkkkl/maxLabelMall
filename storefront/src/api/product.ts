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
