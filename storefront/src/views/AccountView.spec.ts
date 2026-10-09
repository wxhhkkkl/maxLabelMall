import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

const updatePassword = vi.fn()
const sendSmsCode = vi.fn()
const getMemberUser = vi.fn()
vi.mock('@/api/member', () => ({
  updatePassword: (...a: unknown[]) => updatePassword(...a),
  sendSmsCode: (...a: unknown[]) => sendSmsCode(...a),
  getMemberUser: (...a: unknown[]) => getMemberUser(...a),
  logout: vi.fn().mockResolvedValue(true),
  resetPassword: vi.fn(),
  SMS_SCENE_MEMBER_LOGIN: 1,
  SMS_SCENE_UPDATE_PASSWORD: 3,
  SMS_SCENE_RESET_PASSWORD: 4,
}))

// 图形验证码：默认关闭（与本地开发一致）→ 既有断言一字不变
const isCaptchaEnabled = vi.fn()
const getCaptcha = vi.fn()
const checkCaptcha = vi.fn()
vi.mock('@/api/captcha', () => ({
  CAPTCHA_TYPE_SLIDE: 'blockPuzzle',
  isCaptchaEnabled: (...a: unknown[]) => isCaptchaEnabled(...a),
  getCaptcha: (...a: unknown[]) => getCaptcha(...a),
  checkCaptcha: (...a: unknown[]) => checkCaptcha(...a),
}))

import AccountView from './AccountView.vue'

/**
 * 个人中心 —— 设置密码。
 *
 * ⚠️ 本文件断的是**请求体**，这正是它存在的理由：早先这条路径的测试只断言 UI，
 * 于是「只传 `password`、漏传 `code`」这个漏写在一路绿灯的情况下活了下来，
 * 而真实链路里后端一律回 `请求参数不正确:手机验证码不能为空` ——
 * FR-011 / SC-011 实际从未成立。
 */

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/account', component: { template: '<div/>' } },
    { path: '/order', component: { template: '<div/>' } },
    { path: '/account/address', component: { template: '<div/>' } },
    { path: '/coupon/mine', component: { template: '<div/>' } },
    { path: '/agreement/user', component: { template: '<div/>' } },
    { path: '/agreement/privacy', component: { template: '<div/>' } },
  ],
})

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  updatePassword.mockReset()
  sendSmsCode.mockReset()
  getMemberUser.mockReset()
  getMemberUser.mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' })
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
  isCaptchaEnabled.mockReset()
  isCaptchaEnabled.mockResolvedValue(false)
  getCaptcha.mockReset()
  getCaptcha.mockResolvedValue({
    originalImageBase64: 'BG',
    jigsawImageBase64: 'PIECE',
    token: 'tk-1',
    secretKey: 'Iir1lkUSLYB43kaG',
  })
  checkCaptcha.mockReset()
  checkCaptcha.mockResolvedValue({ success: true, msg: null })
  await router.push('/account')
})

async function mountView() {
  const w = mount(AccountView, { global: { plugins: [router] } })
  await flushPromises()
  return w
}

describe('AccountView —— 侧栏入口（T113 / T125）', () => {
  it('**侧栏含「我的订单」与「领券中心」** —— 这两处入口归个人中心，不往顶栏加', () => {
    // 2026-09-27：它们一度加在顶栏主导航里，但顶栏加到 7 项后 1101–1200px
    // 会挤到每个词都竖排折行；所有者反馈"放个人中心更合理"，于是移到此处。
    return mountView().then((w) => {
      const items = w
        .findAll('.sidebar .s-item')
        .map((a) => [a.text(), a.attributes('href')])
      expect(items).toContainEqual(['我的订单', '/order'])
      expect(items).toContainEqual(['领券中心', '/coupon'])
    })
  })

  it('侧栏仍保留个人中心 / 收货地址 / 我的券', async () => {
    const w = await mountView()
    const labels = w.findAll('.sidebar .s-item').map((a) => a.text())
    expect(labels).toEqual(['个人中心', '我的订单', '领券中心', '收货地址', '我的券'])
  })
})

describe('AccountView —— 设置密码（FR-011）', () => {
  it('提交时把**密码与手机验证码一起**送出', async () => {
    updatePassword.mockResolvedValue(true)
    const w = await mountView()

    await w.get('#newPassword').setValue('Maxlabel123')
    await w.get('#newPassword2').setValue('Maxlabel123')
    await w.get('#setPasswordCode').setValue('9999')
    await w.get('#setPasswordBtn').trigger('click')
    await flushPromises()

    expect(updatePassword).toHaveBeenCalledWith('Maxlabel123', '9999')
    expect(w.get('.acct-ok').text()).toContain('密码已设置')
  })

  it('「获取验证码」用**改密场景 3**发码，且发到当前登录用户的手机号', async () => {
    sendSmsCode.mockResolvedValue(true)
    const w = await mountView()

    await w.get('#getPasswordCodeBtn').trigger('click')
    await flushPromises()

    // 场景必须是 3（改密），不能复用登录的场景 1 —— 后端按场景核销，用错必然失败
    expect(sendSmsCode).toHaveBeenCalledWith('13800008888', 3)
    // 发完进入倒计时
    expect(w.get('#getPasswordCodeBtn').attributes('disabled')).toBeDefined()
  })

  it('未填验证码时**不发请求**，并给出提示', async () => {
    const w = await mountView()

    await w.get('#newPassword').setValue('Maxlabel123')
    await w.get('#newPassword2').setValue('Maxlabel123')
    await w.get('#setPasswordBtn').trigger('click')
    await flushPromises()

    expect(updatePassword).not.toHaveBeenCalled()
    expect(w.get('.ml-error').text()).toContain('验证码')
  })

  it('会员信息还没回来时「获取验证码」不可点 —— 否则不知道发给谁', async () => {
    // 冷启动时令会员信息**永不返回**，模拟接口未就绪
    getMemberUser.mockReset()
    getMemberUser.mockReturnValue(new Promise(() => {}))

    const w = await mountView()
    expect(w.get('#getPasswordCodeBtn').attributes('disabled')).toBeDefined()
  })
})

/**
 * 改密发码前的图形验证码闸门（与登录弹层同一口径：开关由服务端给）。
 */
describe('AccountView —— 改密发码前先过滑块', () => {
  it('开关关闭时直接发码，不弹滑块（既有行为不变）', async () => {
    const w = await mountView()
    await w.get('#getPasswordCodeBtn').trigger('click')
    await flushPromises()
    expect(w.find('#captchaSlider').exists()).toBe(false)
    expect(sendSmsCode).toHaveBeenCalledWith('13800008888', 3)
  })

  it('开关开启时先弹滑块，且**此时不发短信**', async () => {
    isCaptchaEnabled.mockResolvedValue(true)
    const w = await mountView()
    await w.get('#getPasswordCodeBtn').trigger('click')
    await flushPromises()
    expect(w.find('#captchaSlider').exists()).toBe(true)
    expect(sendSmsCode).not.toHaveBeenCalled()
  })

  it('**滑块通过后才发码，并把凭据带上**', async () => {
    isCaptchaEnabled.mockResolvedValue(true)
    const w = await mountView()
    await w.get('#getPasswordCodeBtn').trigger('click')
    await flushPromises()

    w.findComponent({ name: 'CaptchaSlider' }).vm.$emit('success', 'the-verification')
    await flushPromises()

    expect(sendSmsCode).toHaveBeenCalledWith('13800008888', 3, 'the-verification')
    expect(w.find('#captchaSlider').exists()).toBe(false)
  })

  it('关掉滑块不发音 —— 不能绕过闸门拿到短信', async () => {
    isCaptchaEnabled.mockResolvedValue(true)
    const w = await mountView()
    await w.get('#getPasswordCodeBtn').trigger('click')
    await flushPromises()

    w.findComponent({ name: 'CaptchaSlider' }).vm.$emit('close')
    await flushPromises()

    expect(sendSmsCode).not.toHaveBeenCalled()
  })
})
