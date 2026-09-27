import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

const login = vi.fn()
const smsLogin = vi.fn()
const sendSmsCode = vi.fn()
vi.mock('@/api/member', () => ({
  login: (...a: unknown[]) => login(...a),
  smsLogin: (...a: unknown[]) => smsLogin(...a),
  sendSmsCode: (...a: unknown[]) => sendSmsCode(...a),
  getMemberUser: vi.fn(),
  logout: vi.fn(),
  SMS_SCENE_MEMBER_LOGIN: 1,
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
