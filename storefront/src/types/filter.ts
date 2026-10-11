/**
 * 商城页筛选维度（特性 003）。
 *
 * 契约见 `specs/003-product-filter-dimensions/contracts/app-api.md` §2。
 *
 * ⚠️ **没有计数字段**，也**不承诺数组顺序** —— 前端 MUST 按 `sort` 自行排序。
 * 选项集合只由后台配置决定，不随关键词或已选值变化（决策 Q11=A / FR-111）。
 */
export interface FilterValue {
  valueId: number
  name: string
  sort: number
}

export interface FilterDimension {
  dimensionId: number
  dimensionName: string
  sort: number
  values: FilterValue[]
}
