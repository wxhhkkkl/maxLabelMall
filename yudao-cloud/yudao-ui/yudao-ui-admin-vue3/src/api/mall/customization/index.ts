import request from '@/config/axios'

/**
 * 印刷定制（014）
 *
 * ⚠️ 金额一律按「分」与后端交互；页面上的输入/展示用「元」，换算在页面里做。
 */

export interface CustomizationAreaRuleVO {
  id?: number
  minArea: number
  maxArea: number
  printCoefficient: number
  dieCutCoefficient: number
}

export interface CustomizationMaterialVO {
  id?: number
  name: string
  description?: string
  basePrice: number
  printCoefficient: number
  printingCoefficient: number
  minQuantity: number
  maxQuantity: number
  minWidth: number
  maxWidth: number
  minHeight: number
  maxHeight: number
  status: number
  sort: number
  areaRules: CustomizationAreaRuleVO[]
}

export interface CustomizationConfigVO {
  printBasePrice: number
  printingBasePrice: number
  dieCutBasePrice: number
  urgentBasePrice: number
  urgentCoefficient: number
}

export const getMaterialPage = (params: {
  pageNo: number
  pageSize: number
  name?: string
  status?: number
}) => request.get<{ list: CustomizationMaterialVO[]; total: number }>({
  url: '/trade/customization/material/page',
  params,
})

export const getMaterial = (id: number) =>
  request.get<CustomizationMaterialVO>({ url: '/trade/customization/material/get', params: { id } })

export const createMaterial = (data: CustomizationMaterialVO) =>
  request.post<number>({ url: '/trade/customization/material/create', data })

export const updateMaterial = (data: CustomizationMaterialVO) =>
  request.put<boolean>({ url: '/trade/customization/material/update', data })

export const deleteMaterial = (id: number) =>
  request.delete<boolean>({ url: '/trade/customization/material/delete', params: { id } })

export const updateMaterialStatus = (id: number, status: number) =>
  request.put<boolean>({ url: '/trade/customization/material/update-status', params: { id, status } })

export const getConfig = () =>
  request.get<CustomizationConfigVO>({ url: '/trade/customization/config/get' })

export const saveConfig = (data: CustomizationConfigVO) =>
  request.put<boolean>({ url: '/trade/customization/config/save', data })
