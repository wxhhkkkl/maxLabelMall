import { get, post } from '@/config/http'

/**
 * 微信扫码登录（`/app-api/member/auth/wechat/**`）。
 *
 * 契约见 `specs/005-wechat-scan-login/contracts/app-api.md`。
 *
 * 三个调用的分工：
 * - **电脑端**：`createWechatScanTicket` 拿二维码 → `queryWechatScanTicket` 轮询
 * - **手机端**（回跳页上）：`wechatScanCallback` 把 code + state 交给服务端
 */

/** 电脑端拿到的票据 */
export interface WechatScanTicket {
  /** 票据号。**不可猜测**，在待补全期间等价于登录凭据 —— 只存在内存里，别写进 URL */
  ticket: string
  /** 二维码内容（微信授权页地址，已带上本次的 state） */
  qrContent: string
  /** 票据存活秒数 */
  expiresIn: number
}

/**
 * 轮询返回的状态。
 *
 * 状态取值见契约 §2，**共 5 个**：`待扫 / 待补全 / 已完成 / 已过期 / 已作废`。
 * ⚠️ **`待补全` 之前不会有 `accessToken`**（FR-150）。
 */
export interface WechatScanStatus {
  status: string
  /** 补全页的默认展示（仅「待补全」时有值） */
  wxNickname?: string
  wxAvatar?: string
  /** 仅「已完成」时有值 */
  accessToken?: string
  refreshToken?: string
}

/**
 * 微信授权后的**回跳页地址**（跑在手机上）。
 *
 * ⚠️ 它必须落在公众号后台配的「网页授权域名」下，否则微信会拒绝跳转。
 */
export function wechatCallbackUrl(): string {
  return `${window.location.origin}/wechat/callback`
}

/** 电脑端点「微信登录」时调：签发一张票据，拿到二维码内容 */
export function createWechatScanTicket(): Promise<WechatScanTicket> {
  return post<WechatScanTicket>('/member/auth/wechat/scan/create', {
    redirectUri: wechatCallbackUrl(),
  })
}

/** 电脑端轮询：看这次扫码走到哪一步了 */
export function queryWechatScanTicket(ticket: string): Promise<WechatScanStatus> {
  return get<WechatScanStatus>('/member/auth/wechat/scan/query', { ticket })
}

/** 手机端（回跳页上）调：把微信给的 code 交上去 —— 服务端在这一步只识别身份，不建号 */
export function wechatScanCallback(code: string, state: string): Promise<boolean> {
  return post<boolean>('/member/auth/wechat/callback', { code, state })
}

/** 客户关掉补全页时调：让这次扫码彻底作废（不建会员、不建绑定、不留登录态） */
export function abandonWechatScanTicket(ticket: string): Promise<boolean> {
  return post<boolean>('/member/auth/wechat/abandon', { ticket })
}

/**
 * 提交补全页 —— **这一步才建号（或绑到既有会员）、才发令牌**。
 *
 * ⚠️ 令牌不从这里返回：电脑端继续轮询 `queryWechatScanTicket`，轮询到「已完成」才算登录成功。
 * ⚠️ 这里**没有头像**：补全页出现时客户还没登录、传不了头像（FR-151），
 * 自选头像由上层在**提交成功之后**再传。
 */
export function completeWechatScan(
  ticket: string,
  mobile: string,
  smsCode: string,
  company?: string,
): Promise<boolean> {
  const body: { ticket: string; mobile: string; smsCode: string; company?: string } = {
    ticket,
    mobile,
    smsCode,
  }
  if (company) {
    body.company = company
  }
  return post<boolean>('/member/auth/wechat/complete', body)
}
