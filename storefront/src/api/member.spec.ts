import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: (...a: unknown[]) => put(...a),
  del: vi.fn(),
}))

const { login, smsLogin, logout, updatePassword, SMS_SCENE_MEMBER_LOGIN, SMS_SCENE_UPDATE_PASSWORD } =
  await import('./member')
const { getAccessToken, getRefreshToken } = await import('@/utils/auth')

const RESP = { accessToken: 'at-1', refreshToken: 'rt-1', expiresTime: 0, userId: 7 }

beforeEach(() => {
  window.localStorage.clear()
  get.mockReset()
  post.mockReset()
})

/**
 * 令牌落盘的责任在**登录接口**，不在调用方。
 *
 * `store/user.ts#syncAfterLogin` 的注释写的就是「令牌已由登录接口写入」。
 * 早先这里漏了落盘，而组件测试自己 `setTokens` 造假登录态，于是测试全绿、
 * 真实链路里「登录成功但刷新一下又变成未登录」——**只有 e2e 能发现**。
 */
describe('登录成功后必须把令牌写进本地存储', () => {
  it('验证码登录（兼隐式注册）', async () => {
    post.mockResolvedValue(RESP)
    const resp = await smsLogin('13900001111', '9999')
    expect(post).toHaveBeenCalledWith('/member/auth/sms-login', {
      mobile: '13900001111',
      code: '9999',
    })
    expect(getAccessToken()).toBe('at-1')
    expect(getRefreshToken()).toBe('rt-1')
    // 返回值原样透出，调用方不需要再从存储里反查
    expect(resp).toEqual(RESP)
  })

  it('密码登录', async () => {
    post.mockResolvedValue(RESP)
    await login('13900001111', 'secret')
    expect(post).toHaveBeenCalledWith('/member/auth/login', {
      mobile: '13900001111',
      password: 'secret',
    })
    expect(getAccessToken()).toBe('at-1')
    expect(getRefreshToken()).toBe('rt-1')
  })

  it('登录失败时**不留下半截登录态**', async () => {
    post.mockRejectedValue({ code: 400, message: '验证码不存在' })
    await expect(smsLogin('13900001111', '0000')).rejects.toBeTruthy()
    expect(getAccessToken()).toBe('')
    expect(getRefreshToken()).toBe('')
  })
})

describe('登出', () => {
  it('只负责调后端登出；**清本地令牌是 store 的事**（userStore.logout）', async () => {
    post.mockResolvedValue(true)
    await logout()
    expect(post).toHaveBeenCalledWith('/member/auth/logout')
  })
})

/**
 * ⚠️ 后端 `AppMemberUserUpdatePasswordReqVO` 的 `code` 是**必填**
 * （`@NotEmpty(message = "手机验证码不能为空")`），服务端还会 `useSmsCode(scene=3)`
 * 实际核销这张码。
 *
 * 早先前端只传了 `password`，于是「设置密码」这条路径**从未真正可用** ——
 * 后端一律回 `请求参数不正确:手机验证码不能为空`。而它一直没被发现，是因为
 * 组件测试只断言 UI、**没有断言请求体**。下面这条就是钉住请求体的。
 */
describe('设置 / 修改密码', () => {
  it('**必须带上手机验证码** —— 只传 password 会被后端判 400', async () => {
    put.mockResolvedValue(true)
    await updatePassword('Maxlabel123', '9999')
    expect(put).toHaveBeenCalledWith('/member/user/update-password', {
      password: 'Maxlabel123',
      code: '9999',
    })
  })

  it('改密场景是 3，**与登录场景 1 不是同一张码**（不可互换）', () => {
    expect(SMS_SCENE_UPDATE_PASSWORD).toBe(3)
    expect(SMS_SCENE_UPDATE_PASSWORD).not.toBe(SMS_SCENE_MEMBER_LOGIN)
  })
})
