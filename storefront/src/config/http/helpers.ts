import { SUCCESS_CODE, TENANT_ID, TERMINAL } from './config'

/**
 * 构建请求头。
 *
 * **`tenant-id` 是无条件注入的**，不能挂在任何开关后面：后端在缺该头时返回
 * `HTTP 200 + body.code=400`（「请求的租户标识未传递」），表现为「页面空数据却
 * 不报错」，是本站最难排查的故障。免登录接口（商品浏览、分类、登录）同样需要。
 */
export function buildHeaders(token?: string): Record<string, string> {
  const headers: Record<string, string> = {
    'tenant-id': TENANT_ID,
    terminal: String(TERMINAL),
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

function asRecord(body: unknown): Record<string, unknown> | null {
  return typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : null
}

/**
 * 成功判定 —— **按 `body.code`，不看 HTTP 状态**。
 *
 * app 端的成功码是 `0`；管理端的 `200` 不算成功。租户缺失等错误是
 * `HTTP 200 + body.code=400`，只看 HTTP 状态会把它们当成成功。
 */
export function isSuccess(body: unknown): boolean {
  return asRecord(body)?.code === SUCCESS_CODE
}

/** 取后端错误文案；缺失时用调用方给的回落文案，都没有则给通用文案（不返回空串） */
export function errorMessageOf(body: unknown, fallback?: string): string {
  const msg = asRecord(body)?.msg
  if (typeof msg === 'string' && msg.length > 0) return msg
  if (fallback && fallback.length > 0) return fallback
  return '请求失败，请稍后重试'
}

/** 401 —— 需要刷新令牌后重放 */
export function isUnauthorized(body: unknown): boolean {
  return asRecord(body)?.code === 401
}

export { SUCCESS_CODE, TENANT_ID, TERMINAL }
