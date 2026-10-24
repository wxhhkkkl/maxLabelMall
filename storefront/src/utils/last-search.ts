/**
 * 「上一次搜索的关键词」的本地存取（商城列表页的搜索框用）。
 *
 * 目的只有一个：客户想**再搜一次同一个词**时，不必重新打一遍 ——
 * 输入框为空时提示文字会显示它，直接点「搜索」就按它再搜。
 *
 * 两条约束：
 *   · **key 必须带项目前缀**：管理端（同域名 `/admin/`）与本前台同源共享
 *     localStorage，裸 key 有撞车风险（有 `utils/auth.ts` 里那起事故的教训）。
 *   · **localStorage 不可用不能影响搜索**：隐私模式、被禁用时读写一律静默降级，
 *     搜索本身照常可用，只是记不住。
 */

export const LAST_SEARCH_KEY = 'MAXLABEL_LAST_SEARCH_KEYWORD'

function store(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

/** 上次搜索的关键词；没搜过 / 读不出来时返回空串（调用方据此退回默认提示） */
export function getLastSearchKeyword(): string {
  try {
    return store()?.getItem(LAST_SEARCH_KEY)?.trim() ?? ''
  } catch {
    return ''
  }
}

/** 记住这次搜索的词。空串不写入 —— 否则「清空后点清除」会把上次的词抹掉 */
export function saveLastSearchKeyword(keyword: string): void {
  const term = keyword.trim()
  if (!term) return
  try {
    store()?.setItem(LAST_SEARCH_KEY, term)
  } catch {
    // 隐私模式 / 配额满：记不住就不记，搜索本身不受影响
  }
}
