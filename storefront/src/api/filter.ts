import { get } from '@/config/http'
import type { FilterDimension } from '@/types/filter'

/**
 * 商城页筛选维度（`/app-api/product/filter/**`，特性 003）。
 *
 * ⚠️ 这个接口**只依赖 `categoryId`**，不接受关键词与已选值 —— 选项集合是**静态**的，
 * 所以同一个分类内**只需请求一次**（切分类才重取）。见 contracts/app-api.md §2。
 *
 * ⚠️ 它失败时 **MUST NOT** 影响商品列表：调用方应让筛选条整块不渲染并允许重试（FR-106）。
 */
export function listFilterDimensions(categoryId: number): Promise<FilterDimension[]> {
  return get<FilterDimension[]>('/product/filter/dimension-list', { categoryId })
}
