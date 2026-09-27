/** 收货地址。五个字段全必填（FR-027）。 */
export interface Address {
  id: number
  name: string
  mobile: string
  /** 地区编号，由 /system/area/tree 三级联动选出 —— 必须来自后台，不得前端内置 */
  areaId: number
  areaName: string
  detailAddress: string
  defaultStatus: boolean
}

/** 地区树节点（/system/area/tree） */
export interface Area {
  id: number
  name: string
  children?: Area[]
}
