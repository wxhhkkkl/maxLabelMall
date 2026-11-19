/**
 * 商品列表项（AppProductSpuRespVO）。
 *
 * 注意：只有 `description` 与 `skus` 是**详情独有**；`introduction` 与
 * `sliderPicUrls` 在**列表接口同样返回**（AppProductSpuRespVO:19,28）。
 * 不要断言它们不存在。
 */
export interface ProductSpu {
  id: number
  name: string
  /** 商品内部编码（仅详情返回；为空则不展示） */
  internalCode?: string
  introduction: string
  categoryId: number
  picUrl: string
  sliderPicUrls: string[]
  specType: boolean
  /** 单位：分 */
  price: number
  /** 单位：分 */
  marketPrice: number
  stock: number
  salesCount: number
  deliveryTypes: number[]
  /** 仅详情返回 —— 富文本，需安全过滤（FR-005c） */
  description?: string
  /** 仅详情返回 */
  skus?: ProductSku[]
  /** 关联软件/驱动（2026-11-18，仅详情返回；无关联时为空） */
  driverSoftwareId?: number
  driverTitle?: string
  /** 1 桌面端软件 2 打印驱动 */
  driverType?: number
  driverVersion?: string
  /** 对象存储直链；有值才显示下载入口 */
  driverDownloadUrl?: string
}

/** 规格项（AppProductSpuDetailRespVO.skus[]） */
export interface ProductSku {
  id: number
  properties: Array<{
    propertyId: number
    propertyName: string
    valueId: number
    valueName: string
  }>
  /** 单位：分 */
  price: number
  /** 单位：分 */
  marketPrice: number
  vipPrice?: number
  picUrl?: string
  stock: number
  weight?: number
  volume?: number
}

/**
 * 商品检索的排序字段。**必须小驼峰**：后端白名单只认 price / salesCount，
 * 传大写或 createTime 会被 @AssertTrue 判为「排序字段不合法」。
 */
export type ProductSortField = 'price' | 'salesCount'
