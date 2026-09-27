/**
 * 令牌存取 —— 本项目**唯一**的令牌读写处。
 *
 * ⚠️ 存储 key 固定为 `ACCESS_TOKEN` / `REFRESH_TOKEN`，**不得与
 * `yudao-ui-mall-uniapp` 的 `token` / `refresh-token` 混用**。
 * 仓库里两套既有前端的 key 恰好不一致，混用会导致「登录了但请求不带令牌」。
 */

const ACCESS_TOKEN_KEY = 'ACCESS_TOKEN'
const REFRESH_TOKEN_KEY = 'REFRESH_TOKEN'

function store(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

export function getAccessToken(): string {
  return store()?.getItem(ACCESS_TOKEN_KEY) ?? ''
}

export function getRefreshToken(): string {
  return store()?.getItem(REFRESH_TOKEN_KEY) ?? ''
}

export function setTokens(accessToken: string, refreshToken: string): void {
  store()?.setItem(ACCESS_TOKEN_KEY, accessToken)
  store()?.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

/** 退出登录或刷新失败时调用：清空两枚令牌 */
export function clearTokens(): void {
  store()?.removeItem(ACCESS_TOKEN_KEY)
  store()?.removeItem(REFRESH_TOKEN_KEY)
}

export const AUTH_KEYS = { ACCESS_TOKEN_KEY, REFRESH_TOKEN_KEY } as const
