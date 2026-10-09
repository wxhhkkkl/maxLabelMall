import { flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AUTH_KEYS, setTokens } from '@/utils/auth'

const getMemberUser = vi.fn()
vi.mock('@/api/member', () => ({
  getMemberUser: (...a: unknown[]) => getMemberUser(...a),
  logout: vi.fn().mockResolvedValue(true),
}))

const { useUserStore } = await import('./user')

beforeEach(() => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  getMemberUser.mockReset()
})

describe('登录态', () => {
  it('未登录时 isLogin 为 false', () => {
    const s = useUserStore()
    expect(s.isLogin).toBe(false)
  })

  it('令牌写入后（冷启动场景）**从本地存储恢复登录态**（FR-013）', () => {
    setTokens('at-1', 'rt-1')
    const s = useUserStore()
    // 关掉浏览器再打开 = 重新建 store，但 localStorage 还在
    expect(s.isLogin).toBe(true)
  })

  it('冷启动后能拉到会员信息', async () => {
    setTokens('at-1', 'rt-1')
    getMemberUser.mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800000000' })
    const s = useUserStore()
    await s.loadMember()
    expect(s.member?.nickname).toBe('张三')
  })

  it('**冷启动自动恢复会员信息**，不需要调用方显式触发（FR-013）', async () => {
    setTokens('at-1', 'rt-1')
    getMemberUser.mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800000000' })
    // 关掉浏览器再打开 = 重新建 store，但 localStorage 里的令牌还在。
    // 这里**故意不调 loadMember()**：顶栏的昵称就靠这一步自动补上，
    // 只恢复令牌而不恢复会员信息的话，顶栏会渲染成空文本。
    const s = useUserStore()
    await flushPromises()
    expect(s.member?.nickname).toBe('张三')
  })

  it('**令牌被后端判为越权（403）时必须清掉登录态**，不能卡在"有令牌没会员"', async () => {
    // 场景：换租户后，浏览器里还留着旧租户签发的令牌。前端发 tenant-id=162 +
    // 那个旧令牌 → 后端 TenantSecurityWebFilter 回 403「您无权访问该租户的数据」。
    // 而拦截器**只在 401 时**刷新并清空，403 不理 —— 于是旧令牌永远留着，
    // isLogin 恒为真、member 恒为 null，顶栏渲染成一个看不见的空链接，刷新也没用。
    setTokens('at-old-tenant', 'rt-old-tenant')
    getMemberUser.mockRejectedValue({ code: 403, message: '您无权访问该租户的数据' })
    await useUserStore().loadMember()
    expect(useUserStore().isLogin).toBe(false)
    expect(window.localStorage.getItem(AUTH_KEYS.ACCESS_TOKEN_KEY)).toBeNull()
  })

  it('**但临时故障（网络异常）不能把用户踢下线**（FR-013）', async () => {
    setTokens('at-1', 'rt-1')
    getMemberUser.mockRejectedValue(new Error('Network Error'))
    await useUserStore().loadMember()
    // 令牌还在：可能只是偶发网络问题，真正的失效由 401 分支处理
    expect(useUserStore().isLogin).toBe(true)
    expect(window.localStorage.getItem(AUTH_KEYS.ACCESS_TOKEN_KEY)).toBe('at-1')
  })

  it('退出登录清空令牌与会员信息', async () => {
    setTokens('at-1', 'rt-1')
    getMemberUser.mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800000000' })
    const s = useUserStore()
    await s.loadMember()
    await s.logout()
    expect(s.isLogin).toBe(false)
    expect(s.member).toBeNull()
    expect(window.localStorage.getItem(AUTH_KEYS.ACCESS_TOKEN_KEY)).toBeNull()
  })
})

describe('顶栏展示用', () => {
  it('有昵称时用它', async () => {
    setTokens('at-1', 'rt-1')
    getMemberUser.mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800000000' })
    const s = useUserStore()
    await s.loadMember()
    expect(s.displayName).toBe('张三')
  })

  it('**没有昵称时回落为脱敏手机号**（FR-016）', async () => {
    setTokens('at-1', 'rt-1')
    getMemberUser.mockResolvedValue({ id: 1, nickname: '', mobile: '13800008888' })
    const s = useUserStore()
    await s.loadMember()
    expect(s.displayName).toBe('138****8888')
  })

  it('未登录时 displayName 为空串，不显示占位文案', () => {
    expect(useUserStore().displayName).toBe('')
  })
})

describe('协议同意 —— **仅内存，刷新后不保留**（FR-050）', () => {
  it('默认未同意', () => {
    expect(useUserStore().agreedToTerms).toBe(false)
  })

  it('同意后在本会话内为 true', () => {
    const s = useUserStore()
    s.setAgreed(true)
    expect(s.agreedToTerms).toBe(true)
  })

  it('**同意状态不写入本地存储** —— 刷新/重开页面后必须重新勾选', () => {
    const s = useUserStore()
    s.setAgreed(true)
    // 页面上不该留下任何与同意有关的持久化痕迹
    const dumped = JSON.stringify(window.localStorage)
    expect(dumped).not.toContain('agree')
    expect(dumped).not.toContain('consent')
    expect(dumped).not.toContain('terms')
    // 重新建 store（≈ 刷新）后应回到未同意
    setActivePinia(createPinia())
    expect(useUserStore().agreedToTerms).toBe(false)
  })
})
