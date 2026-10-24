import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getLastSearchKeyword, LAST_SEARCH_KEY, saveLastSearchKeyword } from './last-search'

/**
 * 「上次搜索的关键词」的本地存取。
 *
 * 关键点不是读写本身，而是两条**降级**：
 *   · 没搜过 / 值损坏 → 返回空串（调用方据此退回默认提示）
 *   · localStorage 不可用（隐私模式、被禁用）→ 静默失败，**不能影响搜索本身**
 */

describe('last-search —— 上次搜索关键词的本地存取', () => {
  beforeEach(() => window.localStorage.clear())

  it('存进去能原样读出来', () => {
    saveLastSearchKeyword('碳带')
    expect(getLastSearchKeyword()).toBe('碳带')
    expect(window.localStorage.getItem(LAST_SEARCH_KEY)).toBe('碳带')
  })

  it('首尾空格会被裁掉', () => {
    saveLastSearchKeyword('  热敏纸  ')
    expect(getLastSearchKeyword()).toBe('热敏纸')
  })

  it('空串 / 纯空格不覆盖已经记住的词', () => {
    saveLastSearchKeyword('碳带')
    saveLastSearchKeyword('')
    saveLastSearchKeyword('   ')
    expect(getLastSearchKeyword()).toBe('碳带')
  })

  it('从没搜过时返回空串（调用方据此显示默认提示）', () => {
    expect(getLastSearchKeyword()).toBe('')
  })

  it('localStorage 抛错时静默降级，不把异常抛给搜索流程', () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError')
    })
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError')
    })
    try {
      expect(() => saveLastSearchKeyword('碳带')).not.toThrow()
      expect(getLastSearchKeyword()).toBe('')
    } finally {
      getItem.mockRestore()
      setItem.mockRestore()
    }
  })
})
