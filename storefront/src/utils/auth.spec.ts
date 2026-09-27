import { beforeEach, describe, expect, it } from 'vitest'

import { AUTH_KEYS, clearTokens, getAccessToken, getRefreshToken, setTokens } from './auth'

describe('令牌存取', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('未登录时返回空串（而不是 null/undefined，调用方无需判空）', () => {
    expect(getAccessToken()).toBe('')
    expect(getRefreshToken()).toBe('')
  })

  it('写入后可按原值读回', () => {
    setTokens('at-123', 'rt-456')
    expect(getAccessToken()).toBe('at-123')
    expect(getRefreshToken()).toBe('rt-456')
  })

  it('清空后两枚令牌都没了', () => {
    setTokens('at-123', 'rt-456')
    clearTokens()
    expect(getAccessToken()).toBe('')
    expect(getRefreshToken()).toBe('')
  })

  // 这条是本模块存在的主要风险：仓库里另一套前端的 key 拼写不同，混用会
  // 导致「登录了但请求不带令牌」。锁死 key 值以防无意改动。
  it('存储 key 固定为 ACCESS_TOKEN / REFRESH_TOKEN，不与 uniapp 的 token 混用', () => {
    expect(AUTH_KEYS.ACCESS_TOKEN_KEY).toBe('ACCESS_TOKEN')
    expect(AUTH_KEYS.REFRESH_TOKEN_KEY).toBe('REFRESH_TOKEN')

    setTokens('at-123', 'rt-456')
    expect(window.localStorage.getItem('ACCESS_TOKEN')).toBe('at-123')
    expect(window.localStorage.getItem('REFRESH_TOKEN')).toBe('rt-456')
    // uniapp 的 key 不应被本模块写入
    expect(window.localStorage.getItem('token')).toBeNull()
    expect(window.localStorage.getItem('refresh-token')).toBeNull()
  })
})
