import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createRouter, createWebHistory } from 'vue-router'

const getCartCount = vi.fn()
vi.mock('@/api/category', () => ({ listCategories: vi.fn().mockResolvedValue([]), buildCategoryTree: (cats: unknown[]) => cats }))
vi.mock('@/api/cart', () => ({
  getCartCount: (...a: unknown[]) => getCartCount(...a),
  addToCart: vi.fn(),
  listCart: vi.fn(),
  updateCartCount: vi.fn(),
  updateCartSelected: vi.fn(),
  deleteCartItems: vi.fn(),
}))

const getMemberUser = vi.fn()
vi.mock('@/api/member', () => ({
  getMemberUser: (...a: unknown[]) => getMemberUser(...a),
  login: vi.fn(),
  smsLogin: vi.fn(),
  sendSmsCode: vi.fn(),
  logout: vi.fn().mockResolvedValue(true),
  SMS_SCENE_MEMBER_LOGIN: 1,
}))

import SiteHeader from './SiteHeader.vue'

/**
 * 顶栏的**静态结构**测试。
 *
 * ⚠️ 本文件刻意**只断言结构**（logo、五个导航项、入口都渲染）。
 * 实时登录态与购物车角标的断言分别在 T072 / T086 追加 —— 各随所属故事，
 * 避免在这里断言后续阶段才存在的行为（那会让本阶段的测试永远红）。
 */

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/mall', component: { template: '<div/>' } },
    { path: '/software', component: { template: '<div/>' } },
    { path: '/solutions', component: { template: '<div/>' } },
    { path: '/support', component: { template: '<div/>' } },
    { path: '/account', component: { template: '<div/>' } },
  ],
})

beforeEach(() => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  getMemberUser.mockReset()
  getMemberUser.mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' })
  getCartCount.mockReset()
  getCartCount.mockResolvedValue(0)
})

function mountHeader() {
  return mount(SiteHeader, { global: { plugins: [router] } })
}

async function mountLoggedIn() {
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
  const w = mountHeader()
  const { useUserStore } = await import('@/store/user')
  await useUserStore().loadMember()
  await flushPromises()
  return w
}

describe('SiteHeader —— 静态结构', () => {
  it('悬停展开子菜单，离开或 Escape 收起', async () => {
    vi.useFakeTimers()
    const w = mountHeader()
    const item = w.findAll('.nav-item')[3]!
    await item.trigger('mouseenter')
    expect(w.get('#nav-submenu-solutions').isVisible()).toBe(true)
    expect(w.get('#nav-submenu-solutions').text()).toContain('生产制造')
    await item.trigger('keydown', { key: 'Escape' })
    expect(w.get('#nav-submenu-solutions').attributes('style')).toContain('display: none')
    await item.trigger('focusin')
    expect(w.get('#nav-submenu-solutions').isVisible()).toBe(true)
    await item.trigger('mouseleave')
    expect(w.get('#nav-submenu-solutions').isVisible()).toBe(true)
    await w.get('#nav-submenu-solutions').trigger('mouseenter')
    await vi.advanceTimersByTimeAsync(400)
    expect(w.get('#nav-submenu-solutions').isVisible()).toBe(true)
    await w.get('#nav-submenu-solutions').trigger('mouseleave')
    await vi.advanceTimersByTimeAsync(300)
    expect(w.get('#nav-submenu-solutions').attributes('style')).toContain('display: none')
    w.unmount()
    vi.useRealTimers()
  })

  it('渲染 logo 图片与中英文品牌名', () => {
    const w = mountHeader()
    expect(w.get('img.logo-img').attributes('src')).toBe('/assets/logo.png')
    expect(w.get('.logo-cn').text()).toBe('赋签')
    expect(w.get('.logo-en').text()).toBe('MaxLabel')
  })

  it('公共导航包含公司介绍入口', () => {
    const labels = mountHeader()
      .findAll('.nav .nav-primary')
      .map((a) => a.text().replace('⌄', '').trim())
    expect(labels).toEqual(['首页', '商城', '标签软件', '行业方案', '服务支持', '公司介绍'])
  })

  it('**顶栏不再出现「领券中心」「我的订单」**（它们归个人中心）', () => {
    const w = mountHeader()
    expect(w.find('.header a[href="/coupon"]').exists()).toBe(false)
    expect(w.find('.header a[href="/order"]').exists()).toBe(false)
  })

  it('导航项指向正确的路由（不是设计稿里的 .html）', () => {
    const w = mountHeader()
    expect(w.find('#nav-submenu-software').exists()).toBe(false)
    expect(w.find('#nav-submenu-support').exists()).toBe(false)
    expect(w.get('.mobile-nav a[href="/software"]').attributes('data-mobile-primary')).toBeDefined()
    expect(w.get('.mobile-nav a[href="/support"]').attributes('data-mobile-primary')).toBeDefined()
    const hrefs = w
      .findAll('.nav .nav-primary')
      .map((a) => a.attributes('href'))
    expect(hrefs).toEqual(['/', '/mall', '/software', '/solutions', '/support', '/about'])
  })

  it('渲染购物车入口、登录入口与主按钮', () => {
    const w = mountHeader()
    expect(w.find('.cart-chip').exists()).toBe(true)
    expect(w.find('.login').exists()).toBe(true)
    expect(w.find('.btn-primary').exists()).toBe(true)
  })



  // 设计稿写死「购物车 (2)」；这个是必须清掉的写死内容（FR-043）
  it('购物车入口**不出现写死的数字** —— 未登录时本就不应显示角标', () => {
    const w = mountHeader()
    expect(w.get('.cart-chip').text()).not.toMatch(/\(\s*\d+\s*\)/)
    expect(w.text()).not.toContain('购物车 (2)')
  })

  it('移动端导航包含相同的公司介绍入口', () => {
    const labels = mountHeader()
      .findAll('.mobile-nav [data-mobile-primary]')
      .map((a) => a.text().replace('＋', '').trim())
    expect(labels).toEqual(['首页', '商城', '标签软件', '行业方案', '服务支持', '公司介绍'])
  })

  it('**移动端汉堡菜单里必须有登录入口** —— `.login` 在 ≤768px 是隐藏的', () => {
    // 设计稿有一条 `@media (max-width:768px) { .login { display:none } }`，
    // 而 `.login` 是**登录入口与个人中心入口的同一个元素** ——
    // 于是手机上既没登录入口、登录后也进不去个人中心。汉堡菜单里补一个兜底。
    const w = mountHeader()
    const mLogin = w.find('.mobile-nav .m-login')
    expect(mLogin.exists()).toBe(true)
    expect(mLogin.text()).toContain('登录')
  })

  it('登录后汉堡菜单里给的是**个人中心**入口', async () => {
    const w = await mountLoggedIn()
    const mLogin = w.find('.mobile-nav .m-login')
    expect(mLogin.attributes('href')).toBe('/account')
    // 顶栏按**脱敏手机号**展示（所有者 2026-10-16 指定；脱敏是 FR-016 的 MUST）
    expect(mLogin.text()).toContain('138****8888')
  })

  it('渲染汉堡按钮，点击切换 body 的 nav-open（沿用设计稿机制）', async () => {
    const w = mountHeader()
    const btn = w.get('.nav-toggle')
    await btn.trigger('click')
    expect(document.body.classList.contains('nav-open')).toBe(true)
    await btn.trigger('click')
    expect(document.body.classList.contains('nav-open')).toBe(false)
  })
})

describe('SiteHeader —— 登录态（T072 追加；随 US2 交付）', () => {
  it('未登录时显示「登录 / 注册」，且不显示用户名', () => {
    const w = mountHeader()
    expect(w.get('.login').text()).toContain('登录')
    expect(w.text()).not.toContain('张三')
  })

  it('未登录时点「登录 / 注册」打开登录弹层', async () => {
    const w = mountHeader()
    expect(w.find('.ml-modal').exists()).toBe(false)
    await w.get('.login').trigger('click')
    await flushPromises()
    expect(w.find('.ml-modal').exists()).toBe(true)
  })

  it('**弹层不在 `.header` 之内** —— 否则窄屏登录按钮会被顶栏规则误伤', async () => {
    // 现场：design.css 有一条 `@media (max-width:480px) { .header .btn-primary { display:none } }`
    // （本意是藏顶栏的「免费试用软件」）。弹层一度被渲染在 <nav class="header"> 里面，
    // 于是**登录弹层的提交按钮**（也是 .btn-primary）在 ≤480px 被一起藏掉 ——
    // 手机上表现为"弹层能打开、但点不动提交"。
    const w = mountHeader()
    await w.get('.login').trigger('click')
    await flushPromises()

    const modal = w.get('.ml-modal')
    expect(w.get('.header').element.contains(modal.element)).toBe(false)
  })

  it('**已登录时显示脱敏手机号，入口变为个人中心**（FR-016 / FR-014）', async () => {
    // 会员**有昵称**（'张三'）时也显示手机号 —— 这正是本次改动的点
    const w = await mountLoggedIn()
    expect(w.get('.login').text()).toContain('138****8888')
    expect(w.get('.login').text()).not.toContain('张三')
    expect(w.get('.login').attributes('href')).toBe('/account')
  })

  it('**冷启动只带令牌也要渲染出用户名**（FR-013）—— 不能是空白入口', async () => {
    // ⚠️ 刻意**不调** loadMember()：真实冷启动时没有任何调用方会替我们调它。
    // 只恢复令牌、不恢复会员信息的话 displayName 回落成空串，这里会渲染出一个
    // **空文本的 .login** —— 看着是登录态，却连名字都没有（顶栏空白）。
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    const w = mountHeader()
    await flushPromises()
    expect(w.get('.login').text()).toBe('138****8888')
  })

  it('**有令牌但会员信息暂时拉不到时，入口也不能是空文本**', async () => {
    // 网络异常时登录态**保留**（FR-013），此时 displayName 为空 ——
    // 若入口没有兜底文案，就会渲染成一个 0×0 的空白链接，
    // 用户看到的就是"个人中心不见了"（实测确认过：a 标签、文字为空、宽高 0）。
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    getMemberUser.mockReset()
    getMemberUser.mockRejectedValue(new Error('Network Error'))
    const w = mountHeader()
    await flushPromises()
    expect(w.get('.login').text().trim()).not.toBe('')
  })

  it('退出登录后立即恢复未登录态', async () => {
    const w = await mountLoggedIn()
    const { useUserStore } = await import('@/store/user')
    await useUserStore().logout()
    await flushPromises()
    expect(w.get('.login').text()).toContain('登录 / 注册')
    expect(w.get('.login').attributes('href')).not.toBe('/account')
  })

  it('**没有手机号时**回落昵称（不会渲染成空白），旁边展示后台等级', async () => {
    getMemberUser.mockResolvedValue({ id: 1, nickname: '张三', mobile: '', level: { id: 2, name: '黄金会员' } })
    const w = await mountLoggedIn()
    expect(w.get('.header-member .login').text()).toBe('张三')
    expect(w.get('.header-member-level').text()).toBe('黄金会员')
    expect(w.get('.mobile-member .header-member-level').text()).toBe('黄金会员')
  })

  it('未配置等级如实展示，可通过头部文字按钮退出并清空登录状态', async () => {
    const w = await mountLoggedIn()
    expect(w.get('.header-member-level').text()).toBe('暂无等级')
    await w.get('.header-member .header-logout').trigger('click')
    await flushPromises()
    expect(w.find('.header-member').exists()).toBe(false)
    expect(w.find('.mobile-member').exists()).toBe(false)
    expect(w.get('.login').text()).toContain('登录 / 注册')
    const { getAccessToken } = await import('@/utils/auth')
    expect(getAccessToken()).toBe('')
  })

  it('带 ?login=1 进入时自动打开登录弹层（路由守卫/入口统一走这条路）', async () => {
    await router.push('/?login=1')
    const w = mountHeader()
    await flushPromises()
    expect(w.find('.ml-modal').exists()).toBe(true)
    await router.push('/')
  })
})

describe('SiteHeader —— 购物车角标（T086 追加；随 US3 交付）', () => {
  it('未登录时**不显示角标数字**，也不出现设计稿写死的「购物车 (2)」', async () => {
    const w = mountHeader()
    await flushPromises()
    expect(w.get('.cart-chip').text()).not.toMatch(/\(\s*\d+\s*\)/)
    expect(w.text()).not.toContain('购物车 (2)')
  })

  it('已登录且有件数时显示真实数字', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    getCartCount.mockResolvedValue(3)
    const w = mountHeader()
    await flushPromises()
    expect(w.get('.cart-chip').text()).toContain('(3)')
  })

  it('件数为 0 时不显示「(0)」—— 零不该有角标', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    getCartCount.mockResolvedValue(0)
    const w = mountHeader()
    await flushPromises()
    expect(w.get('.cart-chip').text()).not.toContain('(0)')
  })

  it('角标数量来自后端 `get-count`，不是本地累加', async () => {
    const { setTokens } = await import('@/utils/auth')
    setTokens('at-1', 'rt-1')
    getCartCount.mockResolvedValue(9)
    mountHeader()
    await flushPromises()
    expect(getCartCount).toHaveBeenCalled()
  })
})
