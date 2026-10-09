/**
 * 令牌存取 —— 本项目**唯一**的令牌读写处。
 *
 * ⚠️ **key 必须带项目前缀**（2026-10-09 踩过线上事故才加的）：
 * 管理端（同一个域名的 `/admin/`）用的 `web-storage-cache` 也把令牌写在
 * localStorage 的 `ACCESS_TOKEN` / `REFRESH_TOKEN` 上，且值是
 * `{"c":…,"e":…,"v":…}` 这种信封。两边同 key、又同源 ⇒ **谁后登录就覆盖谁**：
 * 管理端一登，前台下次 401 刷新时会把整个信封当成 refreshToken 发出去，
 * 后端查不到 → 报「无效的刷新令牌」，而用户看着自己"明明是登录着的"。
 *
 * 早先的注释只提防了 uniapp 的 `token` / `refresh-token`，**漏掉了同源的这一个** ——
 * 那是更危险的一类：不同包名但同域名、同 key。
 *
 * 附带说明：管理端的信封里其实包着它的令牌明文（`v` 字段），那是**管理员令牌**，
 * 对前台接口无用，所以迁移时只认「看起来是裸令牌」的值。
 */

const ACCESS_TOKEN_KEY = 'MAXLABEL_ACCESS_TOKEN'
const REFRESH_TOKEN_KEY = 'MAXLABEL_REFRESH_TOKEN'

/** 撞过车的旧 key —— 只在迁移里读一次，之后必须删掉（留着会被管理端继续写） */
const LEGACY_ACCESS_KEY = 'ACCESS_TOKEN'
const LEGACY_REFRESH_KEY = 'REFRESH_TOKEN'

/** uniapp 用的 key —— 本模块**绝不**写入，仅用于测试断言 */
export const UNAPPLIKE_KEYS = ['token', 'refresh-token'] as const

/**
 * 迁移并清理旧 key。**在应用启动时调用一次**（`main.ts`）。
 *
 * 两件事，缺一不可：
 *   1. 旧 key 里若是前台自己的裸令牌 → 迁到新 key（避免老用户被静默登出）；
 *   2. 旧 key **无论如何都要删掉** —— 那是管理端在用的 key，留着会继续互相覆盖。
 *
 * 识别"信封"的办法很朴素：管理端的 `web-storage-cache` 值是 JSON，以 `{` 开头；
 * 前台的令牌是裸字符串。不做更复杂的解析 —— 认错也只是让用户重登一次。
 */
export function migrateLegacyTokens(): void {
  const s = store()
  if (!s) return
  const pairs: Array<[string, string]> = [
    [LEGACY_ACCESS_KEY, ACCESS_TOKEN_KEY],
    [LEGACY_REFRESH_KEY, REFRESH_TOKEN_KEY],
  ]
  for (const [legacyKey, newKey] of pairs) {
    const legacy = s.getItem(legacyKey)
    if (legacy === null) continue
    s.removeItem(legacyKey)
    const looksLikeEnvelope = legacy.trimStart().startsWith('{')
    if (looksLikeEnvelope) continue
    // 不覆盖已经存在的新值 —— 迁移只补空
    if (!s.getItem(newKey)) s.setItem(newKey, legacy)
  }
}

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
