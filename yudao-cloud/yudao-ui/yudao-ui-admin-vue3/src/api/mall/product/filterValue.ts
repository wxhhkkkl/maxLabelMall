import request from '@/config/axios'

import type { FilterDeleteImpactVO } from './filterDimension'

/**
 * 筛选维度的可选值（特性 003）。
 *
 * 契约见 `specs/003-product-filter-dimensions/contracts/admin-api.md` §2。
 *
 * ⚠️ 顺序**只认 `sort`**（FR-112）：前台怎么排完全听运营的，
 * 服务端不会按商品数或任何运行时数据重排。
 */
export interface FilterValueVO {
  id?: number
  /** 所属筛选维度编号 */
  dimensionId?: number
  /** 可选值名称，如「110」 */
  name: string
  /** 展示顺序 */
  sort?: number
  remark?: string
}

// 获得某个维度下的可选值（按运营设定的顺序）
export const getValueList = (dimensionId: number) => {
  return request.get({ url: '/product/filter-value/list', params: { dimensionId } })
}

// 创建可选值
export const createValue = (data: FilterValueVO) => {
  return request.post({ url: '/product/filter-value/create', data })
}

// 更新可选值
export const updateValue = (data: FilterValueVO) => {
  return request.put({ url: '/product/filter-value/update', data })
}

// 删除可选值（会一并清掉商品上对它的关联）
export const deleteValue = (id: number) => {
  return request.delete({ url: `/product/filter-value/delete?id=${id}` })
}

// 删除前的影响告知（有多少商品挂着它）
export const getValueDeleteImpact = (id: number) => {
  return request.get({ url: '/product/filter-value/delete-impact', params: { id } }) as Promise<FilterDeleteImpactVO>
}
