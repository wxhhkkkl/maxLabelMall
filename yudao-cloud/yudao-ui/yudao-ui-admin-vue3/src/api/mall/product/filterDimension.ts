import request from '@/config/axios'

/**
 * 商品筛选维度（特性 003）。
 *
 * 契约见 `specs/003-product-filter-dimensions/contracts/admin-api.md` §1。
 *
 * ⚠️ 「向下继承」不是存储结构：维度只存一个 `categoryId`，子分类页能看到父分类的维度，
 * 是服务端按**分类祖先链**实时算出来的（决策 Q10=B）。所以 `getDimensionList` 返回的是
 * **两段** —— `own`（本分类自有，可改）与 `inherited`（继承来的，只读且带来源）。见 FR-110。
 */
export interface FilterDimensionVO {
  id?: number
  /** 归属的商品分类编号 */
  categoryId?: number
  /** 维度名称，如「宽度」 */
  name: string
  /** 展示顺序 */
  sort?: number
  remark?: string
  /** 仅「继承来的维度」有值：它挂在哪个祖先分类上 */
  inheritedFromCategoryName?: string
}

export interface FilterDimensionListVO {
  own: FilterDimensionVO[]
  inherited: FilterDimensionVO[]
}

/** 删除前的影响告知 —— 删维度会连带删掉它的可选值与商品关联，不可撤销 */
export interface FilterDeleteImpactVO {
  valueCount: number
  spuCount: number
}

// 获得某个分类的筛选维度（自有 + 继承）
export const getDimensionList = (categoryId: number) => {
  return request.get({ url: '/product/filter-dimension/list', params: { categoryId } })
}

// 创建筛选维度
export const createDimension = (data: FilterDimensionVO) => {
  return request.post({ url: '/product/filter-dimension/create', data })
}

// 更新筛选维度（**不接受改分类** —— 挪分类等于换掉整条继承关系，本期不支持）
export const updateDimension = (data: FilterDimensionVO) => {
  return request.put({ url: '/product/filter-dimension/update', data })
}

// 删除筛选维度
export const deleteDimension = (id: number) => {
  return request.delete({ url: `/product/filter-dimension/delete?id=${id}` })
}

// 删除前的影响告知
export const getDimensionDeleteImpact = (id: number) => {
  return request.get({ url: '/product/filter-dimension/delete-impact', params: { id } })
}
