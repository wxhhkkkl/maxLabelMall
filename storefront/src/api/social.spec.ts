import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: vi.fn(),
  del: vi.fn(),
}))

const { SOCIAL_TYPE_WECHAT_MP, bindSocialUser, getSocialAuthRedirectUrl, getSocialUser } = await import(
  './social'
)

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

/**
 * 微信公众号的社交接口（`/app-api/member/**`）。
 *
 * ⚠️ 这三个接口是**微信 JSAPI 支付的前置**：没有 openid 就发不出 JSAPI 下单请求
 * （`WxPubPayClient` 会直接抛「支付请求的 openid 不能为空」）。所以这里必须钉住
 * `type: 31`（`SocialTypeEnum.WECHAT_MP`）—— 传错类型会拿到别家平台的 openid，
 * 表现是微信侧报「下单账号与支付账号不一致」，很难查。
 */
describe('社交接口', () => {
  it('type 是 31（WECHAT_MP）—— 类型错了拿到的 openid 不属于支付用的公众号', () => {
    expect(SOCIAL_TYPE_WECHAT_MP).toBe(31)
  })

  it('拿授权页地址：redirectUri 是回跳的页面地址，走 query 传参', async () => {
    get.mockResolvedValue('https://open.weixin.qq.com/connect/oauth2/authorize?...')
    const url = await getSocialAuthRedirectUrl('https://new.yuwangchenfa.com/order/1')
    expect(get).toHaveBeenCalledWith('/member/auth/social-auth-redirect', {
      type: 31,
      redirectUri: 'https://new.yuwangchenfa.com/order/1',
    })
    expect(url).toContain('open.weixin.qq.com')
  })

  it('绑定并用 code 换 openid：body 是 { type, code, state }，返回 openid 字符串', async () => {
    post.mockResolvedValue('o-1')
    const openid = await bindSocialUser('C-1', 'S-1')
    expect(post).toHaveBeenCalledWith('/member/social-user/bind', {
      type: 31,
      code: 'C-1',
      state: 'S-1',
    })
    expect(openid).toBe('o-1')
  })

  it('绑定失败要向上抛 —— 组件靠它把后端文案（如「社交授权失败，原因是…」）显示出来', async () => {
    post.mockRejectedValue(new Error('社交授权失败，原因是：invalid code'))
    await expect(bindSocialUser('bad', 'S-1')).rejects.toThrow('社交授权失败')
  })

  it('查当前会员已绑定的 openid', async () => {
    get.mockResolvedValue({ openid: 'o-1', nickname: '微信用户', avatar: '' })
    const info = await getSocialUser()
    expect(get).toHaveBeenCalledWith('/member/social-user/get', { type: 31 })
    expect(info?.openid).toBe('o-1')
  })

  it('未绑定时后端给 null，前端归一成 null 而不是抛错', async () => {
    get.mockResolvedValue(null)
    expect(await getSocialUser()).toBeNull()
  })
})
