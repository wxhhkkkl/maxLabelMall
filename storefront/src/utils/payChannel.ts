/**
 * 支付渠道的**展示映射** —— 编码 → 中文名 + 展示顺序。
 *
 * ⚠️ 这里**只做展示映射，不判断可用性**。哪个渠道能用由后端说了算
 * （`/app-api/pay/channel/get-enable-code-list`），前端自己判断等于把
 * 后台的开关绕过去了。
 *
 * 编码取自 yudao pay 模块的 `PayChannelEnum`，与 `pay_channel.code` 一一对应：
 *   · `alipay_pc` —— 支付宝电脑网站支付（PC 浏览器）
 *   · `wx_native` —— 微信原生扫码支付（PC 浏览器里扫码，注意不是 `wx_pub`，
 *     那个是公众号内支付，需要微信内打开）
 * 将来要加别的渠道，在下面加一行即可；顺序即界面上从左到右的顺序。
 */

const CHANNEL_LABELS: Record<string, string> = {
  alipay_pc: '支付宝',
  wx_native: '微信支付',
}

/** 已知渠道的展示顺序 —— 微信排在支付宝后面 */
const KNOWN_ORDER = Object.keys(CHANNEL_LABELS)

export interface ChannelOption {
  code: string
  label: string
  /** 后端说这个渠道启用了、可以提交 */
  enabled: boolean
  /**
   * 已知渠道、但后端没启用 —— 界面上**置灰占位**并提示「即将上线」。
   * 这样后台一配好，前端不用改代码就会变成可选。
   */
  comingSoon: boolean
}

/**
 * 把后端返回的「启用的渠道码」转成界面上的选项列表。
 *
 * 两类都保留：
 *   · 已知渠道（即使没启用）—— 占位，避免界面上突然少一个入口；
 *   · 后端返回但前端没登记过的编码 —— 也要显示（回落用编码当名字），
 *     否则新配的渠道会在界面上凭空消失。
 */
export function buildChannelOptions(enabledCodes: string[]): ChannelOption[] {
  const enabled = new Set(enabledCodes)
  const unknown = enabledCodes.filter((c) => !KNOWN_ORDER.includes(c))
  const codes = [...KNOWN_ORDER, ...unknown]

  return codes.map((code) => ({
    code,
    label: CHANNEL_LABELS[code] ?? code,
    enabled: enabled.has(code),
    comingSoon: !enabled.has(code) && code in CHANNEL_LABELS,
  }))
}
