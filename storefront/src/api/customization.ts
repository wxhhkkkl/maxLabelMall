import { get, post } from '@/config/http'
import type { InvoiceApplyForm } from '@/types'

/**
 * 印刷定制（`/app-api/trade/customization/**`，014）。
 *
 * ⚠️ 金额一律是**分**，面积一律是 **㎡**；前端只展示，永远以服务端报价为准
 * （宪法原则 III：价格、库存等权威值不得由前端裁决）。
 */

export interface CustomizationMaterial {
  id: number
  name: string
  /** 材质说明（展示给客户） */
  description?: string
  minQuantity: number
  maxQuantity: number
  minWidth: number
  maxWidth: number
  minHeight: number
  maxHeight: number
}

export interface CustomizationFile {
  name: string
  url: string
  size?: number
  type?: string
}

export interface CustomizationSpec {
  materialId: number
  width: number
  height: number
  quantity: number
  /** 业务模式：1 印刷（多色）/ 2 打印（黑色）/ 3 两者 / 4 空白标签 */
  mode: number
  /** 成品形态：1 卷筒 / 2 平张 */
  form?: number
  sheetSpec?: string
  /** 成品处理：1 圆角 / 2 撕裂线 */
  finishing?: number
  tearLine?: boolean
  rowCount?: number
  columnCount?: number
  rowGap?: number
  columnGap?: number
  axisDiameter?: string
  dieCut: boolean
  urgent: boolean
  files: CustomizationFile[]
  remark?: string
}

export interface CustomizationBreakdown {
  print: number
  printing: number
  dieCut: number
  urgent: number
  subtotal: number
  total: number
}

export interface CustomizationQuote {
  materialId: number
  materialName: string
  unitArea: number
  totalArea: number
  breakdown: CustomizationBreakdown
  freight: number
  payable: number
  quoteTime: string
}

export interface CustomizationQuoteReq extends Omit<CustomizationSpec, 'files' | 'remark'> {
  /** 传了才计算运费（需登录） */
  addressId?: number
  deliveryType?: number
}

export interface CustomizationCreateReq extends CustomizationSpec {
  /** 客户在页面上看到的应付金额（含运费）；与最新报价不一致时不建单（Q101） */
  expectedAmount: number
  addressId?: number
  deliveryType?: number
  invoice?: InvoiceApplyForm
}

export interface CustomizationCreateResp {
  /** 价格已变：未建单，quote 是新报价（Q101） */
  changed: boolean
  quote?: CustomizationQuote
  id?: number
  payOrderId?: number | null
  customizationOrderId?: number
}

/** 可选材质（公开） */
export function listCustomizationMaterials(): Promise<CustomizationMaterial[]> {
  return get<CustomizationMaterial[]>('/trade/customization/material/list')
}

/** 报价（公开；传 addressId 时返回运费） */
export function quoteCustomization(req: CustomizationQuoteReq): Promise<CustomizationQuote> {
  return post<CustomizationQuote>('/trade/customization/quote', req)
}

/** 创建定制订单（需登录） */
export function createCustomizationOrder(req: CustomizationCreateReq): Promise<CustomizationCreateResp> {
  return post<CustomizationCreateResp>('/trade/customization/order/create', req)
}
