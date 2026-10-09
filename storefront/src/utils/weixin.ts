/**
 * 微信公众号 JSAPI 支付（渠道码 `wx_pub`）用到的一小组工具。
 *
 * 三件事，各自独立：
 *   1. **判断是不是在微信里** —— `wx_pub` 只能在微信内置浏览器里跑；
 *   2. **解析后端给的 JSAPI 参数并唤起收银台**；
 *   3. **记一个「授权前正在付哪笔单」的标记** —— 微信授权是整页跳转，
 *      回来时页面已重载，不记就接不上。
 *
 * 唤起收银台用的是微信内置的 `WeixinJSBridge.invoke('getBrandWCPayRequest')`，
 * **刻意不引 `weixin-js-sdk`**：`jweixin.chooseWXPay` 要先 `jweixin.config()` 拿到
 * JSSDK 签名（还要在公众号后台配「JS 安全域名」），而 bridge 是微信浏览器自带的，
 * 不需要签名、也不需要那条后台配置。参数两者一致。
 *
 * ⚠️ 这里的 `bridge` / `whenReady` 都设计成**可注入** —— 否则 jsdom 里根本测不了
 * 「到底有没有真的去唤起收银台」。
 */

const WECHAT_UA_RE = /micromessenger/i

/** 是否在微信内置浏览器里。默认读 `navigator.userAgent`，测试可显式传。 */
export function isWechatBrowser(ua: string = navigator.userAgent): boolean {
  return WECHAT_UA_RE.test(ua)
}

/** 后端 `displayContent` 反序列化后的 JSAPI 下单参数（字段名即微信的返回名） */
export interface WxJsapiParams {
  appId: string
  timeStamp: string
  nonceStr: string
  /** 微信的 `prepay_id=***`，提交给收银台时要改叫 `package` */
  packageValue: string
  signType: string
  paySign: string
}

const JSAPI_FIELDS = [
  'appId',
  'timeStamp',
  'nonceStr',
  'packageValue',
  'signType',
  'paySign',
] as const

/**
 * 解析提交支付返回的 `displayContent`。
 *
 * 坏 JSON 或字段缺失一律**抛错** —— 静默降级会去唤起一个参数不全的收银台，
 * 手机上只弹一句「参数错误」，比直接报错难查得多。
 */
export function parseWxJsapiParams(displayContent: string): WxJsapiParams {
  let raw: unknown
  try {
    raw = JSON.parse(displayContent)
  } catch {
    throw new Error('微信支付参数解析失败')
  }
  if (!raw || typeof raw !== 'object') {
    throw new Error('微信支付参数解析失败')
  }
  const o = raw as Record<string, unknown>
  const missing = JSAPI_FIELDS.filter((f) => typeof o[f] !== 'string' || !(o[f] as string))
  if (missing.length) {
    throw new Error(`微信支付参数缺少字段：${missing.join('、')}`)
  }
  return {
    appId: o.appId as string,
    timeStamp: o.timeStamp as string,
    nonceStr: o.nonceStr as string,
    packageValue: o.packageValue as string,
    signType: o.signType as string,
    paySign: o.paySign as string,
  }
}

/** 用户在收银台上的三种结局。`ok` **不等于已付款** —— 状态仍以后端回调为准。 */
export type WxPayOutcome = 'ok' | 'cancel' | 'fail'

export interface WxBridge {
  invoke(api: string, params: Record<string, string>, cb: (res: { err_msg?: string }) => void): void
}

/** 等 `WeixinJSBridge` 就绪：老 WebView 里它可能晚于页面脚本注入 */
export type WxBridgeReady = (cb: () => void) => void

function globalBridge(): WxBridge | undefined {
  return (globalThis as { WeixinJSBridge?: WxBridge }).WeixinJSBridge
}

function whenBridgeReady(cb: () => void): void {
  if (globalBridge()) {
    cb()
    return
  }
  document.addEventListener('WeixinJSBridgeReady', () => cb(), { once: true })
}

export interface WxPayHooks {
  bridge?: WxBridge
  whenReady?: WxBridgeReady
}

/**
 * 唤起微信收银台。返回的是**收银台上的操作结果**，不是订单是否已支付。
 */
export function invokeWxPay(p: WxJsapiParams, hooks: WxPayHooks = {}): Promise<WxPayOutcome> {
  return new Promise((resolve, reject) => {
    const ready = hooks.whenReady ?? whenBridgeReady
    ready(() => {
      const bridge = hooks.bridge ?? globalBridge()
      if (!bridge) {
        reject(new Error('当前环境不支持微信支付，请在微信中打开本页'))
        return
      }
      bridge.invoke(
        'getBrandWCPayRequest',
        {
          appId: p.appId,
          timeStamp: p.timeStamp,
          nonceStr: p.nonceStr,
          // ⚠️ 微信要的字段名是 `package`，不是后端的 `packageValue`
          package: p.packageValue,
          signType: p.signType,
          paySign: p.paySign,
        },
        (res) => {
          const msg = res?.err_msg ?? ''
          if (msg.endsWith(':ok')) resolve('ok')
          else if (msg.includes('cancel')) resolve('cancel')
          else resolve('fail')
        },
      )
    })
  })
}

/** 整页跳转（去微信授权页）。抽出来是为了让组件测试能替换掉它。 */
export function redirectTo(url: string): void {
  window.location.href = url
}

// ========== 待续跑标记 ==========

/**
 * 用 sessionStorage 而不是 localStorage：这个标记只在**当前这一次**授权跳转的
 * 往返里有意义，关掉标签页就该失效。
 */
const PENDING_KEY = 'maxlabel:wx-pay-pending'

function store(): Storage | null {
  try {
    return window.sessionStorage
  } catch {
    return null
  }
}

export function markWxPayPending(payOrderId: number): void {
  store()?.setItem(PENDING_KEY, String(payOrderId))
}

/** 读不到、或读出来不是正常数字时一律按「没有标记」处理 */
export function readWxPayPending(): number | null {
  const raw = store()?.getItem(PENDING_KEY)
  if (!raw) return null
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
}

export function clearWxPayPending(): void {
  store()?.removeItem(PENDING_KEY)
}
