import type { AxiosRequestConfig } from 'axios'

import type { CommonResult } from '@/types'

import { http } from './service'

/**
 * 统一请求入口。
 *
 * 分工：响应拦截器按 `body.code` 判定成功/失败（并在 401 时刷新令牌后重放），
 * **解包留给本文件** —— 这样 axios 拦截器的返回值类型是自洽的。
 * 调用方拿到的是后端 `data` 字段本身。
 *
 * 失败一律以 `HttpError`（带 `code` 与文案）抛出，调用方按需分支。
 */

export async function get<T>(url: string, params?: unknown): Promise<T> {
  const res = await http.get<CommonResult<T>>(url, { params })
  return res.data.data
}

export async function post<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  const res = await http.post<CommonResult<T>>(url, data, config)
  return res.data.data
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  const res = await http.put<CommonResult<T>>(url, data)
  return res.data.data
}

/** 后端取消订单是 DELETE（`@DeleteMapping`），参数走 query */
export async function del<T>(url: string, params?: unknown): Promise<T> {
  const res = await http.delete<CommonResult<T>>(url, { params })
  return res.data.data
}

export { HttpError, onAuthExpired, http } from './service'
export { SUCCESS_CODE, BASE_URL, TENANT_ID, TERMINAL } from './config'
export { buildHeaders, errorMessageOf, isSuccess, isUnauthorized } from './helpers'
