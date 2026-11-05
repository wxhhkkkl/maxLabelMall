/**
 * 商城搜索的「最近搜索」本地存取。
 *
 * 除了保留原来的「上一次搜索」（输入框为空时作为提示、点搜索复用），
 * 现在额外保存一份**最近搜索列表**，在搜索框下方以可点的记忆词展示。
 *
 * 三条约束：
 *   · **key 必须带项目前缀**：管理端（同域名 `/admin/`）与本前台同源共享
 *     localStorage，裸 key 有撞车风险（有 `utils/auth.ts` 里那起事故的教训）。
 *   · **localStorage 不可用不能影响搜索**：隐私模式、被禁用时读写一律静默降级，
 *     搜索本身照常可用，只是记不住。
 *   · **旧 key 继续写**：保持「上次搜索」的既有行为与旧数据兼容。
 */

export const LAST_SEARCH_KEY = 'MAXLABEL_LAST_SEARCH_KEYWORD'
export const SEARCH_HISTORY_KEY = 'MAXLABEL_SEARCH_HISTORY'
export const SEARCH_HISTORY_LIMIT = 8

function store(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

function readLastKeyword(): string {
  try {
    return store()?.getItem(LAST_SEARCH_KEY)?.trim() ?? ''
  } catch {
    return ''
  }
}

function readHistoryRaw(): string[] {
  try {
    const raw = store()?.getItem(SEARCH_HISTORY_KEY)
    if (!raw) return []
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed)) return []
    return parsed
      .filter((item): item is string => typeof item === 'string')
      .map((item) => item.trim())
      .filter(Boolean)
  } catch {
    return []
  }
}

/** 上次搜索的关键词；没搜过 / 读不出来时返回空串（调用方据此退回默认提示） */
export function getLastSearchKeyword(): string {
  const last = readLastKeyword()
  if (last) return last
  return readHistoryRaw()[0] ?? ''
}

/** 最近搜索列表，最新的在前；没有新列表时兼容旧的单条记录 */
export function getSearchHistory(): string[] {
  const history = readHistoryRaw()
  if (history.length > 0) return dedupe(history).slice(0, SEARCH_HISTORY_LIMIT)
  const last = readLastKeyword()
  return last ? [last] : []
}

/** 记住这次搜索的词。空串不写入 —— 否则「清空后点清除」会把上次的词抹掉 */
export function saveLastSearchKeyword(keyword: string): void {
  const term = keyword.trim()
  if (!term) return
  const storage = store()
  if (!storage) return
  try {
    storage.setItem(LAST_SEARCH_KEY, term)
    const next = dedupe([term, ...getSearchHistory()]).slice(0, SEARCH_HISTORY_LIMIT)
    storage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(next))
  } catch {
    // 隐私模式 / 配额满：记不住就不记，搜索本身不受影响
  }
}

function dedupe(values: string[]): string[] {
  const seen = new Set<string>()
  return values.filter((value) => {
    if (seen.has(value)) return false
    seen.add(value)
    return true
  })
}
