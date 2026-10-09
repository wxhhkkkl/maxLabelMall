import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

const login = vi.fn()
const smsLogin = vi.fn()
const sendSmsCode = vi.fn()
const resetPassword = vi.fn()
vi.mock('@/api/member', () => ({
  login: (...a: unknown[]) => login(...a),
  smsLogin: (...a: unknown[]) => smsLogin(...a),
  sendSmsCode: (...a: unknown[]) => sendSmsCode(...a),
  resetPassword: (...a: unknown[]) => resetPassword(...a),
  getMemberUser: vi.fn(),
  logout: vi.fn(),
  updatePassword: vi.fn(),
  SMS_SCENE_MEMBER_LOGIN: 1,
  SMS_SCENE_UPDATE_PASSWORD: 3,
  SMS_SCENE_RESET_PASSWORD: 4,
}))

// 图形验证码：**默认关闭**（与本地开发一致），所以既有的断言全部不受影响；
// 需要验证「先弹滑块」的用例再把它打开。
const isCaptchaEnabled = vi.fn()
const getCaptcha = vi.fn()
const checkCaptcha = vi.fn()
vi.mock('@/api/captcha', () => ({
  CAPTCHA_TYPE_SLIDE: 'blockPuzzle',
  isCaptchaEnabled: (...a: unknown[]) => isCaptchaEnabled(...a),
  getCaptcha: (...a: unknown[]) => getCaptcha(...a),
  checkCaptcha: (...a: unknown[]) => checkCaptcha(...a),
}))

const LoginDialog = (await import('./LoginDialog.vue')).default

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/account', component: { template: '<div/>' } },
    { path: '/agreement/user', component: { template: '<div/>' } },
    { path: '/agreement/privacy', component: { template: '<div/>' } },
  ],
})

function mountDialog() {
  return mount(LoginDialog, {
    props: { open: true },
    global: { plugins: [router] },
  })
}

/** 勾选协议（唯一合法的"已同意"路径） */
async function agree(w: ReturnType<typeof mountDialog>) {
  await w.get('.ml-check input').setValue(true)
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  login.mockReset()
  smsLogin.mockReset()
  sendSmsCode.mockReset()
  sendSmsCode.mockResolvedValue(true)
  resetPassword.mockReset()
  resetPassword.mockResolvedValue(true)
  // 默认与本地开发一致：图形验证码关闭 → 点「获取验证码」直接发，不弹滑块
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
})

describe('协议门禁 —— 未勾选不得发码也不得登录（SC-015 / FR-050）', () => {
  it('初始状态：「获取验证码」与「登录」都不可执行', () => {
    const w = mountDialog()
    expect(w.get('#getCodeBtn').attributes('disabled')).toBeDefined()
    expect(w.get('#loginSubmit').attributes('disabled')).toBeDefined()
  })

  it('填了合法手机号但**未勾选协议**，仍然两个按钮都不可用', async () => {
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    expect(w.get('#getCodeBtn').attributes('disabled')).toBeDefined()
    expect(w.get('#loginSubmit').attributes('disabled')).toBeDefined()
  })

  it('勾选后「获取验证码」可用，并能真正发出请求', async () => {
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    await agree(w)
    expect(w.get('#getCodeBtn').attributes('disabled')).toBeUndefined()
    await w.get('#getCodeBtn').trigger('click')
    await flushPromises()
    expect(sendSmsCode).toHaveBeenCalledWith('13800000000', 1)
  })

  it('**取消勾选后立即回到不可执行** —— 不因"之前勾过一次"而放行', async () => {
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    await agree(w)
    expect(w.get('#getCodeBtn').attributes('disabled')).toBeUndefined()

    await w.get('.ml-check input').setValue(false)
    await flushPromises()
    expect(w.get('#getCodeBtn').attributes('disabled')).toBeDefined()
    expect(w.get('#loginSubmit').attributes('disabled')).toBeDefined()
  })

  it('未勾选时点击也不会发出任何请求（按钮只是不可用还不够）', async () => {
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    await w.get('#loginCode').setValue('9999')
    await w.get('#getCodeBtn').trigger('click')
    await w.get('#loginSubmit').trigger('click')
    await flushPromises()
    expect(sendSmsCode).not.toHaveBeenCalled()
    expect(smsLogin).not.toHaveBeenCalled()
    expect(login).not.toHaveBeenCalled()
  })

  it('提供《用户协议》与《隐私政策》的可达链接', () => {
    const w = mountDialog()
    const hrefs = w.findAll('.agree-links a').map((a) => a.attributes('href'))
    expect(hrefs).toContain('/agreement/user')
    expect(hrefs).toContain('/agreement/privacy')
  })
})

describe('两种登录方式', () => {
  it('默认是验证码登录', () => {
    const w = mountDialog()
    expect(w.find('#loginCode').exists()).toBe(true)
    expect(w.find('#loginPassword').exists()).toBe(false)
  })

  it('可切换到密码登录，且界面元素随之切换', async () => {
    const w = mountDialog()
    await w.findAll('.switch-tab')[1]?.trigger('click')
    expect(w.find('#loginPassword').exists()).toBe(true)
    expect(w.find('#loginCode').exists()).toBe(false)
    // 密码登录不需要「获取验证码」
    expect(w.find('#getCodeBtn').exists()).toBe(false)
  })

  it('切换登录方式后协议勾选状态**不保留为已同意**（避免绕过门禁）', async () => {
    const w = mountDialog()
    await agree(w)
    await w.findAll('.switch-tab')[1]?.trigger('click')
    expect(w.get('#loginSubmit').attributes('disabled')).toBeDefined()
  })
})

describe('验证码提示可区分（FR-010b）', () => {
  it('触发发送频率限制时**告知还需等待多久**', async () => {
    sendSmsCode.mockRejectedValue({ code: 429, message: '请求过于频繁，请 60 秒后再试' })
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    await agree(w)
    await w.get('#getCodeBtn').trigger('click')
    await flushPromises()
    const text = w.get('.login-error').text()
    expect(text).toMatch(/秒|分钟/)
  })

  it('验证码错误与验证码过期给出**不同**的提示', async () => {
    smsLogin.mockRejectedValueOnce({ code: 400, message: '验证码不正确' })
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    await w.get('#loginCode').setValue('1111')
    await agree(w)
    await w.get('#loginSubmit').trigger('click')
    await flushPromises()
    const wrong = w.get('.login-error').text()

    smsLogin.mockRejectedValueOnce({ code: 400, message: '验证码已过期' })
    await w.get('#loginSubmit').trigger('click')
    await flushPromises()
    const expired = w.get('.login-error').text()

    expect(wrong).not.toBe(expired)
  })
})

describe('密码登录的错误提示不暴露账号是否存在（FR-012）', () => {
  it('后端区分"账号不存在"与"密码错误"时，前端**展示同一条通用提示**', async () => {
    const w = mountDialog()
    await w.findAll('.switch-tab')[1]?.trigger('click')
    await w.get('#loginMobile').setValue('13800000000')
    await w.get('#loginPassword').setValue('whatever')
    await agree(w)

    login.mockRejectedValueOnce({ code: 400, message: '账号不存在' })
    await w.get('#loginSubmit').trigger('click')
    await flushPromises()
    const notExist = w.get('.login-error').text()

    login.mockRejectedValueOnce({ code: 400, message: '密码错误' })
    await w.get('#loginSubmit').trigger('click')
    await flushPromises()
    const wrongPassword = w.get('.login-error').text()

    // 两者对外必须完全一致
    expect(notExist).toBe(wrongPassword)
    // 且不能把后端那句"账号不存在"直接透出去
    expect(notExist).not.toContain('不存在')
  })

  it('未设过密码的账号尝试密码登录时，提示引导改用验证码（不是笼统的"凭据错误"）', async () => {
    const w = mountDialog()
    await w.findAll('.switch-tab')[1]?.trigger('click')
    await w.get('#loginMobile').setValue('13800000000')
    await w.get('#loginPassword').setValue('whatever')
    await agree(w)
    login.mockRejectedValueOnce({ code: 400, message: '密码未设置' })
    await w.get('#loginSubmit').trigger('click')
    await flushPromises()
    expect(w.get('.login-error').text()).toContain('验证码')
  })
})

/**
 * 图形验证码闸门（滑块）。
 *
 * ⚠️ 开关由**服务端**给（`isCaptchaEnabled`），前端不读自己的环境变量 ——
 * 否则会出现「前端弹了滑块、服务端没校验」或反过来的错配。
 * 上面所有用例都在「关闭」这条路上，这里补「开启」那条。
 */
describe('发短信前先过滑块', () => {
  it('开关关闭时**不弹滑块**，直接发码（本地开发与 e2e 走的就是这条）', async () => {
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    await agree(w)
    await w.get('#getCodeBtn').trigger('click')
    await flushPromises()

    expect(w.find('#captchaSlider').exists()).toBe(false)
    expect(sendSmsCode).toHaveBeenCalledWith('13800000000', 1)
  })

  it('开关开启时**先弹滑块，且此时一个字节的短信请求都没发**', async () => {
    isCaptchaEnabled.mockResolvedValue(true)
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    await agree(w)
    await w.get('#getCodeBtn').trigger('click')
    await flushPromises()

    expect(w.find('#captchaSlider').exists()).toBe(true)
    // 关键：滑块还没过，绝不能先发短信
    expect(sendSmsCode).not.toHaveBeenCalled()
  })

  it('**滑块通过后才发码，并把凭据带上** —— 不带凭据服务端会拒', async () => {
    isCaptchaEnabled.mockResolvedValue(true)
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    await agree(w)
    await w.get('#getCodeBtn').trigger('click')
    await flushPromises()

    // 直接驱动子组件的 success 事件（拖动本身由 CaptchaSlider.spec.ts 覆盖）
    const slider = w.findComponent({ name: 'CaptchaSlider' })
    slider.vm.$emit('success', 'the-verification')
    await flushPromises()

    expect(sendSmsCode).toHaveBeenCalledWith('13800000000', 1, 'the-verification')
    // 通过后弹层收起，并开始倒计时
    expect(w.find('#captchaSlider').exists()).toBe(false)
    expect(w.get('#getCodeBtn').attributes('disabled')).toBeDefined()
  })

  it('关闭滑块弹层不会发码', async () => {
    isCaptchaEnabled.mockResolvedValue(true)
    const w = mountDialog()
    await w.get('#loginMobile').setValue('13800000000')
    await agree(w)
    await w.get('#getCodeBtn').trigger('click')
    await flushPromises()

    const slider = w.findComponent({ name: 'CaptchaSlider' })
    slider.vm.$emit('close')
    await flushPromises()

    expect(w.find('#captchaSlider').exists()).toBe(false)
    expect(sendSmsCode).not.toHaveBeenCalled()
  })
})

/**
 * 忘记密码（新增）。
 *
 * 后端 `PUT /member/user/reset-password` 是可用的，但前端一直没有入口 ——
 * 用户忘了密码就彻底进不来（登录弹层是唯一入口，且没有注册/找回）。
 */
describe('忘记密码', () => {
  async function openReset(w: ReturnType<typeof mountDialog>) {
    await w.findAll('.switch-tab')[1]?.trigger('click') // 切到密码登录
    await w.get('.link-btn').trigger('click') // 点「忘记密码？」
    await flushPromises()
  }

  it('密码登录那栏有入口，点开切到重置表单', async () => {
    const w = mountDialog()
    await openReset(w)
    expect(w.find('#resetPassword').exists()).toBe(true)
    expect(w.find('#resetCodeBtn').exists()).toBe(true)
    expect(w.find('#loginPassword').exists()).toBe(false)
  })

  it('**协议门禁对它一视同仁** —— 没勾协议不能发码也不能提交', async () => {
    const w = mountDialog()
    await openReset(w)
    await w.get('#loginMobile').setValue('13800000000')
    expect(w.get('#resetCodeBtn').attributes('disabled')).toBeDefined()
    expect(w.get('#loginSubmit').attributes('disabled')).toBeDefined()
  })

  it('发码走**场景 4**（忘记密码），不是登录的 1、也不是改密的 3', async () => {
    const w = mountDialog()
    await openReset(w)
    await w.get('#loginMobile').setValue('13800000000')
    await agree(w)
    await w.get('#resetCodeBtn').trigger('click')
    await flushPromises()
    expect(sendSmsCode).toHaveBeenCalledWith('13800000000', 4)
  })

  it('提交重置：body 是 手机号 + 验证码 + 新密码', async () => {
    const w = mountDialog()
    await openReset(w)
    await w.get('#loginMobile').setValue('13800000000')
    await w.get('#resetCode').setValue('9999')
    await w.get('#resetPassword').setValue('NewPass123')
    await agree(w)
    await w.get('#loginSubmit').trigger('click')
    await flushPromises()

    expect(resetPassword).toHaveBeenCalledWith('13800000000', '9999', 'NewPass123')
  })

  it('**重置成功后不自动登录**（该接口不发令牌），而是提示用新密码登录', async () => {
    const w = mountDialog()
    await openReset(w)
    await w.get('#loginMobile').setValue('13800000000')
    await w.get('#resetCode').setValue('9999')
    await w.get('#resetPassword').setValue('NewPass123')
    await agree(w)
    await w.get('#loginSubmit').trigger('click')
    await flushPromises()

    expect(smsLogin).not.toHaveBeenCalled()
    expect(login).not.toHaveBeenCalled()
    expect(w.text()).toContain('密码已重置')
    // 回到密码登录，手机号保留（用户接着用新密码登）
    expect(w.find('#loginPassword').exists()).toBe(true)
    expect((w.get('#loginMobile').element as HTMLInputElement).value).toBe('13800000000')
  })

  it('重置失败时**直接透出后端文案**（「手机号未注册用户」是可操作信息）', async () => {
    resetPassword.mockRejectedValue({ message: '手机号未注册用户' })
    const w = mountDialog()
    await openReset(w)
    await w.get('#loginMobile').setValue('13800000000')
    await w.get('#resetCode').setValue('9999')
    await w.get('#resetPassword').setValue('NewPass123')
    await agree(w)
    await w.get('#loginSubmit').trigger('click')
    await flushPromises()

    expect(w.get('.login-error').text()).toContain('手机号未注册用户')
  })

  it('切回登录会清掉同意状态（与另两种方式同一口径）', async () => {
    const w = mountDialog()
    await openReset(w)
    await agree(w)
    await w.get('.link-btn').trigger('click') // 返回登录
    expect(w.get('#loginSubmit').attributes('disabled')).toBeDefined()
  })
})
