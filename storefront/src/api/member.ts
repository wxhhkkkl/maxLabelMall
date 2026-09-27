import { post, get, put } from '@/config/http'
import type { AuthLoginResp, MemberUser } from '@/types'
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

/** 发送验证码 */
export function sendSmsCode(mobile: string, scene = SMS_SCENE_MEMBER_LOGIN): Promise<boolean> {
  return post<boolean>('/member/auth/send-sms-code', { mobile, scene })
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
