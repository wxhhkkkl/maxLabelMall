import { beforeEach, describe, expect, it } from 'vitest'

import {
  AUTH_KEYS,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  migrateLegacyTokens,
  setTokens,
} from './auth'

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

  /**
   * ⚠️ 这条是本模块**最要紧**的约束，2026-10-09 因此踩过一次线上事故：
   *
   * 管理端（`/admin/`）与本前台**同属一个域名**，而管理端用的
   * `web-storage-cache` 也把令牌写在 localStorage 的 `ACCESS_TOKEN` /
   * `REFRESH_TOKEN` 上 —— 且值是 `{"c":…,"e":…,"v":…}` 这种信封。
   * 两边同 key，**谁后登录就覆盖谁**：管理端一登，前台再请求就会把整个信封
   * 当成 refreshToken 发出去，后端查不到 → 「无效的刷新令牌」。
   *
   * 所以前台的 key **必须带项目前缀**，且**绝不写裸 key**。
   */
  it('存储 key 带项目前缀 —— 与同源的管理端 key **不能撞车**', () => {
    expect(AUTH_KEYS.ACCESS_TOKEN_KEY).toBe('MAXLABEL_ACCESS_TOKEN')
    expect(AUTH_KEYS.REFRESH_TOKEN_KEY).toBe('MAXLABEL_REFRESH_TOKEN')

    setTokens('at-123', 'rt-456')
    expect(window.localStorage.getItem('MAXLABEL_ACCESS_TOKEN')).toBe('at-123')
    expect(window.localStorage.getItem('MAXLABEL_REFRESH_TOKEN')).toBe('rt-456')

    // ❗绝不写裸 key —— 那两个是管理端在用的
    expect(window.localStorage.getItem('ACCESS_TOKEN')).toBeNull()
    expect(window.localStorage.getItem('REFRESH_TOKEN')).toBeNull()
  })
})

describe('migrateLegacyTokens —— 清理撞车的旧 key', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('旧 key 里是前台自己的令牌 → 迁移到新 key，并删掉旧 key', () => {
    window.localStorage.setItem('ACCESS_TOKEN', 'at-old')
    window.localStorage.setItem('REFRESH_TOKEN', 'rt-old')

    migrateLegacyTokens()

    expect(getAccessToken()).toBe('at-old')
    expect(getRefreshToken()).toBe('rt-old')
    // 旧 key 必须消失，否则管理端下次登录还会往这里写、继续造成困惑
    expect(window.localStorage.getItem('ACCESS_TOKEN')).toBeNull()
    expect(window.localStorage.getItem('REFRESH_TOKEN')).toBeNull()
  })

  /**
   * ⚠️ **2026-10-16 事故**（用户报的："同时登录前后台，前台刷新一下后台就掉登录"）。
   *
   * 原来这条写的是"信封也要删掉"，理由是"留着会被管理端继续写" ——
   * 那是把"清理前台残留"和"删掉管理端的会话"混为一谈了：同源共享 localStorage，
   * **管理端此刻正在用这两个键**，删了它 = 把后台登出；而这个函数在 `main.ts`
   * 顶层，**每次刷新都会跑**。
   */
  it('旧 key 里是管理端的 wsCache 信封 → **原封不动**（那是管理端此刻的会话）', () => {
    const adminAt = '{"c":1791534862803,"e":253402300799000,"v":"x"}'
    const adminRt = '{"c":1791534862803,"e":253402300799000,"v":"y"}'
    window.localStorage.setItem('ACCESS_TOKEN', adminAt)
    window.localStorage.setItem('REFRESH_TOKEN', adminRt)

    migrateLegacyTokens()

    // 前台没捡到什么（信封不是令牌）
    expect(getAccessToken()).toBe('')
    expect(getRefreshToken()).toBe('')
    // **管理端的会话必须还在** —— 这条就是本次修复的核心
    expect(window.localStorage.getItem('ACCESS_TOKEN')).toBe(adminAt)
    expect(window.localStorage.getItem('REFRESH_TOKEN')).toBe(adminRt)
  })

  it('**反复跑也不会动管理端的会话**（这个函数每次页面加载都会跑，不是只跑一次）', () => {
    const adminAt = '{"c":1791534862803,"e":253402300799000,"v":"x"}'
    window.localStorage.setItem('ACCESS_TOKEN', adminAt)

    migrateLegacyTokens()
    migrateLegacyTokens()
    migrateLegacyTokens()

    expect(window.localStorage.getItem('ACCESS_TOKEN')).toBe(adminAt)
  })

  it('前台的令牌存取**不会**顺手清掉管理端的键', () => {
    const adminAt = '{"c":1791534862803,"e":253402300799000,"v":"x"}'
    window.localStorage.setItem('ACCESS_TOKEN', adminAt)

    setTokens('at-123', 'rt-456')
    clearTokens()
    migrateLegacyTokens()

    expect(window.localStorage.getItem('ACCESS_TOKEN')).toBe(adminAt)
  })

  it('不覆盖已存在的新 key（迁移只做一次，不影响当前会话）', () => {
    setTokens('at-new', 'rt-new')
    window.localStorage.setItem('ACCESS_TOKEN', 'at-old')

    migrateLegacyTokens()

    expect(getAccessToken()).toBe('at-new')
    expect(window.localStorage.getItem('ACCESS_TOKEN')).toBeNull()
  })
})
