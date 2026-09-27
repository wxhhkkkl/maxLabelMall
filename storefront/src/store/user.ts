import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { getMemberUser, logout as apiLogout } from '@/api/member'
import { clearTokens, getAccessToken } from '@/utils/auth'
import type { MemberUser } from '@/types'

/**
 * 登录态。
 *
 * 三条约束：
 *
 * 1. **冷启动（关闭浏览器再打开）要能恢复登录态**（FR-013）—— 令牌在本地存储里，
 *    故 store 初始化时即从存储读取，而不是等某个接口回来才认为已登录。
 *
 * 2. **协议同意状态只存内存，绝不持久化**（FR-050）。勾选是"这次收集手机号前取得
 *    同意"的凭证；若写进本地存储，刷新后会被误当作"已经同意过"。
 *
 * 3. 退出登录要**同时**清令牌与内存状态，否则会出现"顶栏显示已退出但请求仍带令牌"。
 */
export const useUserStore = defineStore('user', () => {
  const member = ref<MemberUser | null>(null)
  /** 与本地存储保持同步的令牌；用 ref 是为了让 isLogin 可响应地变化 */
  const token = ref<string>(getAccessToken())
  /** ⚠️ 仅内存。不要给这个 ref 加任何持久化插件。 */
  const agreedToTerms = ref(false)

  const isLogin = computed(() => !!token.value)

  /** 手机号脱敏：13800008888 → 138****8888 */
  function maskMobile(mobile: string): string {
    if (mobile.length < 7) return mobile
    return `${mobile.slice(0, 3)}****${mobile.slice(-4)}`
  }

  /** 顶栏展示名：优先昵称，无昵称时回落为脱敏手机号（FR-016） */
  const displayName = computed(() => {
    if (!isLogin.value) return ''
    const nickname = member.value?.nickname
    if (nickname) return nickname
    const mobile = member.value?.mobile
    return mobile ? maskMobile(mobile) : ''
  })

  /** 登录成功后调用：同步令牌并拉取会员信息 */
  async function loadMember(): Promise<void> {
    token.value = getAccessToken()
    if (!token.value) {
      member.value = null
      return
    }
    try {
      member.value = await getMemberUser()
    } catch {
      // 会员信息拉取失败不改写登录态 —— 令牌可能仍有效（例如偶发网络问题），
      // 真正的失效由响应拦截器的 401 分支处理
      member.value = null
    }
  }

  /** 登录成功后：令牌已由登录接口写入，这里同步 store 并拉取信息 */
  async function syncAfterLogin(): Promise<void> {
    token.value = getAccessToken()
    await loadMember()
  }

  /**
   * **冷启动恢复**（FR-013）。令牌是从本地存储同步读出来的，会员信息却要等接口，
   * 所以必须在 store 创建时就发起一次 —— 否则带着令牌刷新页面时 `member` 恒为 null，
   * `displayName` 回落成空串，顶栏那个 `.login` 会渲染成**一片空白**：
   * 看着像登录态，却连名字都没有。
   *
   * 这里**只发起、不 await**：首屏不该为它让路，信息回来时 `displayName` 会自动更新。
   * `loadMember()` 自己吞掉异常，故不会产生未处理的拒绝。
   */
  if (token.value) void loadMember()

  async function logout(): Promise<void> {
    try {
      if (getAccessToken()) await apiLogout()
    } catch {
      // 后端登出失败也要清本地状态，不能让用户卡在"退不出去"
    }
    clearTokens()
    token.value = ''
    member.value = null
  }

  function setAgreed(v: boolean): void {
    agreedToTerms.value = v
  }

  return {
    member,
    agreedToTerms,
    isLogin,
    displayName,
    loadMember,
    syncAfterLogin,
    logout,
    setAgreed,
  }
})
