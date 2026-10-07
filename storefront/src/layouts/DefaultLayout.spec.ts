import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/api/cart', () => ({
  addToCart: vi.fn(),
  getCartCount: vi.fn().mockResolvedValue(0),
  listCart: vi.fn(),
  updateCartCount: vi.fn(),
  updateCartSelected: vi.fn(),
  deleteCartItems: vi.fn(),
}))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue(null),
  login: vi.fn(),
  smsLogin: vi.fn(),
  sendSmsCode: vi.fn(),
  logout: vi.fn().mockResolvedValue(true),
  SMS_SCENE_MEMBER_LOGIN: 1,
}))

const DefaultLayout = (await import('./DefaultLayout.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/', component: { template: '<div/>' } }],
})

beforeEach(() => {
  setActivePinia(createPinia())
  window.localStorage.clear()
})

function mountLayout() {
  return mount(DefaultLayout, { global: { plugins: [router, createPinia()] } })
}

describe('DefaultLayout —— 顶部通告栏已下线', () => {
  /**
   * 通告栏文案是 `inheritedClaims.promises[0]`（「新用户注册即享专业版 30 天免费试用」），
   * 属**未经确认的营销承诺**。按所有者要求暂时下线，待软件上线后再开 ——
   * 所以这里断言的是"不渲染"，而不是"删掉代码"。design.css 里的 `.topbar` 样式保留，
   * 恢复时只需把这块模板加回来。
   */
  it('不渲染 .topbar', () => {
    expect(mountLayout().find('.topbar').exists()).toBe(false)
  })

  it('只下线通告栏，顶栏与页脚仍在', () => {
    const w = mountLayout()
    expect(w.find('.header').exists()).toBe(true)
    expect(w.find('.footer').exists()).toBe(true)
  })
})
