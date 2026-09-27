import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios'

import { getAccessToken, getRefreshToken, setTokens, clearTokens } from '@/utils/auth'

import { BASE_URL, TIMEOUT } from './config'
import { buildHeaders, errorMessageOf, isSuccess, isUnauthorized } from './helpers'
import { createRefreshQueue } from './refreshQueue'

/** 登录态彻底失效时的回调（由应用注册，用于引导重新登录） */
let authExpiredHandler: (() => void) | null = null
export function onAuthExpired(fn: () => void): void {
  authExpiredHandler = fn
}

/** 带业务错误码的异常，便于调用方按 code 分支处理 */
export class HttpError extends Error {
  readonly code: number
  constructor(code: number, message: string) {
    super(message)
    this.name = 'HttpError'
    this.code = code
  }
}

/**
 * 用 refreshToken 换新令牌。
 *
 * ⚠️ `refreshToken` 是 **query 参数**（后端是 `@RequestParam`），写成 body 会
 * 拿到参数校验失败。这里刻意用裸 axios，避免走进本实例的拦截器造成递归。
 */
async function doRefresh(): Promise<void> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new HttpError(401, '登录已过期，请重新登录')
  }
  const resp = await axios.post(
    `${BASE_URL}/member/auth/refresh-token`,
    null,
    { params: { refreshToken }, timeout: TIMEOUT, headers: buildHeaders() },
  )
  const body = resp.data
  if (!isSuccess(body)) {
    throw new HttpError(401, errorMessageOf(body, '登录已过期，请重新登录'))
  }
  const data = body.data as { accessToken: string; refreshToken: string }
  setTokens(data.accessToken, data.refreshToken)
}

export function createHttpService(): AxiosInstance {
  const instance = axios.create({ baseURL: BASE_URL, timeout: TIMEOUT })
  const queue = createRefreshQueue(doRefresh)

  // ── 请求拦截器：注入 tenant-id / terminal / Bearer ──────────────────
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const headers = buildHeaders(getAccessToken())
    for (const [k, v] of Object.entries(headers)) {
      config.headers.set(k, v)
    }
    return config
  })

  // ── 响应拦截器：按 body.code 判定；401 走刷新队列后重放 ─────────────
  //
  // 成功时**原样返回 response**（不解包）—— 解包交给 index.ts 做，这样 axios
  // 的类型是自洽的（拦截器必须返回 AxiosResponse）。调用方在 index.ts 里
  // 拿到 `CommonResult<T>`，再取 `.data.data`。
  instance.interceptors.response.use(
    (response) => {
      const body: unknown = response.data
      if (isSuccess(body)) {
        return response
      }
      if (isUnauthorized(body)) {
        return queue.submit(() => instance.request(response.config))
      }
      return Promise.reject(
        new HttpError(
          Number((body as { code?: unknown })?.code ?? -1),
          errorMessageOf(body),
        ),
      )
    },
    async (error: AxiosError) => {
      const status = error.response?.status
      const body = error.response?.data

      if (status === 401 || isUnauthorized(body)) {
        try {
          return (await queue.submit(() =>
            instance.request(error.config as AxiosRequestConfig),
          )) as never
        } catch (e) {
          // 刷新失败：清登录态并通知应用引导重登
          clearTokens()
          authExpiredHandler?.()
          throw e instanceof Error ? e : new HttpError(401, '登录已过期，请重新登录')
        }
      }

      throw new HttpError(status ?? -1, errorMessageOf(body, error.message))
    },
  )

  return instance
}

export const http: AxiosInstance = createHttpService()
