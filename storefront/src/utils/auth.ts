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

/**
 * 撞过车的旧 key —— **它们现在是管理端在用的键**。
 *
 * ⚠️ 只清理"前台早期留下的裸令牌残留"，**绝不能碰管理端的信封**
 * （见 {@link migrateLegacyTokens} 里那段说明）。
 */
const LEGACY_ACCESS_KEY = 'ACCESS_TOKEN'
const LEGACY_REFRESH_KEY = 'REFRESH_TOKEN'

/** uniapp 用的 key —— 本模块**绝不**写入，仅用于测试断言 */
export const UNAPPLIKE_KEYS = ['token', 'refresh-token'] as const

/**
 * 清理前台早期留在撞车 key 上的**裸令牌残留**。在 `main.ts` 顶层调用
 * —— 注意那是**每次页面加载都会跑**，不是"只跑一次"。
 *
 * 判据只有一条：管理端的 `web-storage-cache` 值是 JSON 信封（以 `{` 开头），
 * 前台的令牌是裸字符串。所以：
 *   · 裸字符串 → 是前台早期的残留：迁到新 key（不覆盖已有值）、清掉旧 key；
 *   · **信封 → 那是管理端此刻的会话，一个字节都不能动。**
 *
 * ⚠️ **2026-10-16 事故**：这里原来是**先删、后判断**——
 *   `s.removeItem(legacyKey)` 写在信封判断之前，于是每次刷新前台都会把管理端的
 *   `ACCESS_TOKEN` / `REFRESH_TOKEN` 删掉，**后台就掉登录**（用户报的就是这个：
 *   "同时登录前后台，前台刷新一下，后台就登出了"）。
 *   当时的理由是"旧 key 留着会被管理端继续写"——那是把"清理残留"和
 *   "删掉对方的会话"混为一谈了：同源共享 localStorage，对方**正在用**这些键。
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
    // ⚠️ 判断必须在删之前 —— 信封属于管理端，跳过（不删、不迁）
    if (legacy.trimStart().startsWith('{')) continue
    // 走到这里的只可能是前台早期的裸令牌残留：迁走（不覆盖已有值）并清掉
    s.removeItem(legacyKey)
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
