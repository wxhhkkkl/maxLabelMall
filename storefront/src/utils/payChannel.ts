import { isWechatBrowser } from './weixin'

/**
 * 支付渠道的**展示映射** —— 编码 → 中文名 + 展示顺序。
 *
 * ⚠️ **可用性由后端说了算**（`/app-api/pay/channel/get-enable-code-list`），
 * 前端自己判断等于把后台的开关绕过去了。本模块只在后端返回的启用集合**内部**
 * 做减法（见下面的环境过滤），永远不做加法。
 *
 * 编码取自 yudao pay 模块的 `PayChannelEnum`，与 `pay_channel.code` 一一对应：
 *   · `alipay_pc` —— 支付宝电脑网站支付（PC 浏览器）
 *   · `wx_native` —— 微信原生扫码支付（PC 浏览器里扫码）
 *   · `wx_pub`    —— 微信公众号 JSAPI 支付（**必须在微信内置浏览器里**，
 *     要有该会员的 openid，见 `@/utils/weixin`）
 * 将来要加别的渠道，在下面加一行即可；顺序即界面上从左到右的顺序。
 */

const CHANNEL_LABELS: Record<string, string> = {
  alipay_pc: '支付宝',
  wx_native: '微信支付',
  wx_pub: '微信支付（公众号）',
}

/**
 * 只能在特定环境执行的渠道。
 *
 * 环境不匹配时**整条不渲染** —— 不能渲染成 `comingSoon`：那是「后台没启用」的语义，
 * 而这里的情况是「后台启用了、但当前浏览器根本执行不了」，两者的含义完全不同。
 */
const CHANNEL_ENV: Record<string, 'wechat' | 'non-wechat'> = {
  wx_pub: 'wechat',
  wx_native: 'non-wechat',
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
 * 三类都保留：
 *   · 已知渠道（即使没启用）—— 占位，避免界面上突然少一个入口；
 *   · 后端返回但前端没登记过的编码 —— 也要显示（回落用编码当名字），
 *     否则新配的渠道会在界面上凭空消失；
 *   · 已知渠道但**当前环境执行不了**的 —— 直接不渲染（见 {@link CHANNEL_ENV}）。
 *
 * `inWechat` 默认取当前 UA，调用方一般不用传；测试里显式传以便覆盖两种环境。
 */
export function buildChannelOptions(
  enabledCodes: string[],
  inWechat: boolean = isWechatBrowser(),
): ChannelOption[] {
  const enabled = new Set(enabledCodes)
  const unknown = enabledCodes.filter((c) => !KNOWN_ORDER.includes(c))
  const codes = [...KNOWN_ORDER, ...unknown]

  return codes
    .filter((code) => {
      const env = CHANNEL_ENV[code]
      return env === undefined || (env === 'wechat') === inWechat
    })
    .map((code) => ({
      code,
      label: CHANNEL_LABELS[code] ?? code,
      enabled: enabled.has(code),
      comingSoon: !enabled.has(code) && code in CHANNEL_LABELS,
    }))
}
