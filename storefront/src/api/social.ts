import { get, post } from '@/config/http'

/**
 * 微信公众号的社交接口（`/app-api/member/**`）。
 *
 * 这一组的存在意义很窄：**微信 JSAPI 支付（渠道码 `wx_pub`）需要 openid**。
 * 后端 `WxPubPayClient.getOpenid` 拿不到就抛「支付请求的 openid 不能为空」，
 * 所以付款前必须先让当前登录会员绑上公众号、换来 openid。
 *
 * 为什么不自己写一套：yudao 会员模块已经把「跳授权 → 用 code 换 openid → 绑定」
 * 做完了，这里只是把它接出来（宪法原则 III：复用既有能力）。
 *
 * ⚠️ `type` 必须是 {@link SOCIAL_TYPE_WECHAT_MP}，且**同一个公众号**的 openid 才能
 * 用来支付 —— 换错类型会拿到别家平台的 openid，表现是微信侧报
 * 「下单账号与支付账号不一致」，很不好查。
 */

/** 社交平台类型 —— `SocialTypeEnum.WECHAT_MP`（微信公众号） */
export const SOCIAL_TYPE_WECHAT_MP = 31

export interface SocialUserInfo {
  openid: string
  nickname?: string
  avatar?: string
}

/**
 * 拿微信授权页面的地址。
 *
 * `redirectUri` 是**用户授权后回跳的前端页面地址**（完整 URL），必须落在公众号后台
 * 配的「网页授权域名」下，否则微信会拒绝跳转。返回值直接拿去整页跳转。
 */
export function getSocialAuthRedirectUrl(redirectUri: string): Promise<string> {
  return get<string>('/member/auth/social-auth-redirect', {
    type: SOCIAL_TYPE_WECHAT_MP,
    redirectUri,
  })
}

/**
 * 用回跳 URL 上的 `code`/`state` 换取 openid，并把该公众号绑定到**当前登录会员**。
 *
 * ⚠️ 副作用（后端 `SocialUserServiceImpl.bindSocialUser` 的行为，不是本文件引入的）：
 * 该 openid 原先若绑过别的会员会被**解绑**。同一台手机的同一个微信号本来就是同一个
 * openid，而 yudao 的模型是「1 openid → 1 会员」，所以会「抢绑定」—— 可接受：
 * 订单归属按会员 id 不按 openid，不会丢单丢钱，被抢的一方下次支付会自动重新授权。
 */
export function bindSocialUser(code: string, state: string): Promise<string> {
  return post<string>('/member/social-user/bind', {
    type: SOCIAL_TYPE_WECHAT_MP,
    code,
    state,
  })
}

/**
 * 查当前会员已绑定的公众号 openid。未绑定时后端给 `null`。
 *
 * 有了它，老用户第二次付款**不用再走一次整页授权**。
 */
export async function getSocialUser(): Promise<SocialUserInfo | null> {
  const info = await get<SocialUserInfo | null>('/member/social-user/get', {
    type: SOCIAL_TYPE_WECHAT_MP,
  })
  return info ?? null
}
