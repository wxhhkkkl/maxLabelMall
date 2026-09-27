/** 后端统一响应体。**成功码是 0**（app 端），不是管理端的 200。 */
export interface CommonResult<T = unknown> {
  code: number
  data: T
  msg: string
}

/** 分页结果。`total` 用于列表头部的「共 N 件商品」 */
export interface PageResult<T> {
  list: T[]
  total: number
}

/** 分页请求参数 */
export interface PageParam {
  pageNo?: number
  pageSize?: number
}
