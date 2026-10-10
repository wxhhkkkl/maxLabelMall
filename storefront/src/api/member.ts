import { post, get, put } from '@/config/http'
import type { AuthLoginResp, MemberUser, ProfileUpdateReq } from '@/types'
import { setTokens } from '@/utils/auth'

/**
 * 会员认证接口（`/app-api/member/**`）。
 *
 * ⚠️ 两个易错点：
 *
 * 1. **没有独立的注册接口**。注册由「手机号 + 验证码登录」隐式完成 ——
 *    后端 `smsLogin` 走 `createUserIfAbsent`：手机号不存在时自动建号。
 *    因此前端不提供注册表单，也不会有「账号已存在」这种报错。
 *
 * 2. **`refresh-token` 的 `refreshToken` 是 query 参数**（后端 `@RequestParam`），
 *    不是 body。刷新逻辑在 `config/http/service.ts` 里单独处理（用裸 axios
 *    以避免递归），此处不重复暴露。
 */

/** 短信场景：1 = 会员登录（后端 SmsSceneEnum.MEMBER_LOGIN） */
export const SMS_SCENE_MEMBER_LOGIN = 1

/** 短信场景：3 = 修改密码（后端 SmsSceneEnum.MEMBER_UPDATE_PASSWORD） */
export const SMS_SCENE_UPDATE_PASSWORD = 3

/** 短信场景：4 = 忘记密码（后端 SmsSceneEnum.MEMBER_RESET_PASSWORD） */
export const SMS_SCENE_RESET_PASSWORD = 4

/**
 * ⚠️ **令牌落盘是本模块的责任，不是调用方的。**
 *
 * 登录接口拿到的 `accessToken` / `refreshToken` 必须当场写进本地存储（`utils/auth`），
 * 因为 `store/user.ts#syncAfterLogin` 就是按「令牌已由登录接口写入」实现的。
 *
 * 漏了这一步的表现很隐蔽：**登录看起来成功了**（弹层关闭、后端也建了号），但刷新
 * 一下又变回未登录、后续请求不带令牌。而组件测试通常自己 `setTokens` 造假登录态，
 * 根本发现不了 —— 这个漏写在 5 个单元测试套件全绿的情况下活了两个阶段，最后是
 * 订单 e2e 走到「提交订单」时才暴露出来的。
 */
async function persistLogin(resp: AuthLoginResp): Promise<AuthLoginResp> {
  setTokens(resp.accessToken, resp.refreshToken)
  return resp
}

/** 密码登录 */
export async function login(mobile: string, password: string): Promise<AuthLoginResp> {
  return persistLogin(await post<AuthLoginResp>('/member/auth/login', { mobile, password }))
}

/** 验证码登录 —— **手机号不存在时即完成注册** */
export async function smsLogin(mobile: string, code: string): Promise<AuthLoginResp> {
  return persistLogin(await post<AuthLoginResp>('/member/auth/sms-login', { mobile, code }))
}

/**
 * 发送验证码。
 *
 * `captchaVerification` 是**滑块通过后**拿到的凭据（见 `@/api/captcha`）。
 * 服务端在 `MemberAuthServiceImpl.sendSmsCode` 的第一步就校验它 —— 短信是真金白银，
 * 这道闸门就是为了挡脚本刷短信。
 *
 * ⚠️ **没传时不能把 `captchaVerification: undefined` 放进 body**：验证码开关关闭时
 * （本地开发 / e2e）前端根本不传它，body 必须与从前一字不差。
 */
export function sendSmsCode(
  mobile: string,
  scene = SMS_SCENE_MEMBER_LOGIN,
  captchaVerification?: string,
): Promise<boolean> {
  const body: { mobile: string; scene: number; captchaVerification?: string } = { mobile, scene }
  if (captchaVerification) {
    body.captchaVerification = captchaVerification
  }
  return post<boolean>('/member/auth/send-sms-code', body)
}

/** 登出（后端会作废当前令牌） */
export function logout(): Promise<boolean> {
  return post<boolean>('/member/auth/logout')
}

/** 当前会员信息 */
export function getMemberUser(): Promise<MemberUser> {
  return get<MemberUser>('/member/user/get')
}

/**
 * 设置 / 修改密码（登录后）。
 *
 * ⚠️ **`code` 是必填的**（scene 3 的短信验证码）。后端
 * `AppMemberUserUpdatePasswordReqVO` 上 `code` 标着 `@NotEmpty("手机验证码不能为空")`，
 * 且 `MemberUserServiceImpl#updateUserPassword` 会 `useSmsCode(scene=3)` 实际核销它。
 * 只传 `password` 会被判 `请求参数不正确` —— 这个漏写在"组件测试只断言 UI、
 * 不断言请求体"的情况下活了两个阶段。
 *
 * 场景 3 与登录的场景 1 **不是同一张码**，不能拿登录时那张来复用。
 */
export function updatePassword(password: string, code: string): Promise<boolean> {
  return put<boolean>('/member/user/update-password', { password, code })
}

/**
 * 忘记密码（**未登录**可用，后端 `@PermitAll`）。
 *
 * 与 {@link updatePassword} 是两个接口，别混：
 *   · 这个传 `mobile`（用户自己填），走 scene 4；
 *   · 改密那个从登录态取手机号，走 scene 3。
 *
 * 后端的错误是**可区分**的：「手机号未注册用户」与「验证码不正确」文案不同，
 * 调用方直接透出即可（不像密码登录那样必须收敛文案 —— 这里本来就要用户
 * 确认手机号是不是自己注册过的那个）。
 */
export function resetPassword(mobile: string, code: string, password: string): Promise<boolean> {
  return put<boolean>('/member/user/reset-password', { password, code, mobile })
}

/**
 * 修改个人信息（登录后）。
 *
 * ⚠️ **只传改动过的字段**：后端 `AppMemberUserUpdateReqVO` 的四个字段**都不是必填**
 * （没有任何 `@NotNull`），而更新走 MyBatis-Plus 默认的 `NOT_NULL` 策略 ——
 * **省略 = 保留原值**。把没改的字段也发回去，会用**陈旧值覆盖**别处的改动。
 * ⚠️ **清空要发空串**（`@Email`/`@URL` 都放行空串），省略是清不掉的。
 *
 * 请求体怎么构造见 `utils/profile.buildProfileUpdate`（它负责"只挑改动项"）。
 */
export function updateProfile(req: ProfileUpdateReq): Promise<boolean> {
  return put<boolean>('/member/user/update', req)
}
