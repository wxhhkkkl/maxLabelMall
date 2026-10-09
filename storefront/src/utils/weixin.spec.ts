import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  clearWxPayPending,
  invokeWxPay,
  isWechatBrowser,
  markWxPayPending,
  parseWxJsapiParams,
  readWxPayPending,
  type WxBridge,
} from './weixin'

const UA_WECHAT =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 MicroMessenger/8.0.44(0x18002c2c) NetType/WIFI'
const UA_CHROME =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36'

/** 一份典型的 JSAPI 下单结果（后端 `WxPubPayClient` 的 `displayContent`） */
const JSAPI_JSON = JSON.stringify({
  appId: 'wx1234567890',
  timeStamp: '1730000000',
  nonceStr: 'nonce-abc',
  packageValue: 'prepay_id=wx201410272009395522657a690389285100',
  signType: 'MD5',
  paySign: 'SIGN-xyz',
})

function fakeBridge(errMsg = 'getBrandWCPayRequest:ok') {
  const invoke = vi.fn((_api: string, _params: Record<string, string>, cb: (r: { err_msg: string }) => void) =>
    cb({ err_msg: errMsg }),
  )
  return { invoke } as unknown as WxBridge & { invoke: ReturnType<typeof vi.fn> }
}

describe('isWechatBrowser —— 只有微信内才能跑 wx_pub', () => {
  it('微信内置浏览器（含 MicroMessenger）判为真', () => {
    expect(isWechatBrowser(UA_WECHAT)).toBe(true)
  })

  it('桌面 Chrome 判为假', () => {
    expect(isWechatBrowser(UA_CHROME)).toBe(false)
  })

  it('不传参数时取 navigator.userAgent（默认给界面用）', () => {
    const spy = vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue(UA_WECHAT)
    expect(isWechatBrowser()).toBe(true)
    spy.mockRestore()
  })
})

describe('parseWxJsapiParams —— 解析 displayContent', () => {
  it('六个字段按原样取出（packageValue 此处**不**改名，改名是 invoke 的事）', () => {
    expect(parseWxJsapiParams(JSAPI_JSON)).toEqual({
      appId: 'wx1234567890',
      timeStamp: '1730000000',
      nonceStr: 'nonce-abc',
      packageValue: 'prepay_id=wx201410272009395522657a690389285100',
      signType: 'MD5',
      paySign: 'SIGN-xyz',
    })
  })

  it('坏 JSON 要抛错 —— 不能静默返回空参数去唤起一个空收银台', () => {
    expect(() => parseWxJsapiParams('<html>502 Bad Gateway</html>')).toThrow()
  })

  it('缺字段要抛错，并把缺的名字说出来', () => {
    const broken = JSON.stringify({ appId: 'wx1', timeStamp: '1', nonceStr: 'n', packageValue: 'p' })
    expect(() => parseWxJsapiParams(broken)).toThrow(/signType/)
  })

  it('字段为 null/空串也算缺', () => {
    const broken = JSON.parse(JSAPI_JSON) as Record<string, unknown>
    broken.paySign = ''
    expect(() => parseWxJsapiParams(JSON.stringify(broken))).toThrow(/paySign/)
  })
})

describe('invokeWxPay —— 唤起微信收银台', () => {
  it('调 getBrandWCPayRequest，且把 packageValue 映射成微信要的 package', async () => {
    const bridge = fakeBridge()
    const outcome = await invokeWxPay(parseWxJsapiParams(JSAPI_JSON), {
      bridge,
      whenReady: (cb) => cb(),
    })

    expect(bridge.invoke).toHaveBeenCalledTimes(1)
    const [api, params] = bridge.invoke.mock.calls[0]
    expect(api).toBe('getBrandWCPayRequest')
    expect(params).toEqual({
      appId: 'wx1234567890',
      timeStamp: '1730000000',
      nonceStr: 'nonce-abc',
      package: 'prepay_id=wx201410272009395522657a690389285100',
      signType: 'MD5',
      paySign: 'SIGN-xyz',
    })
    // 微信不认 packageValue，传错了会在手机上弹「参数错误」
    expect(params).not.toHaveProperty('packageValue')
    expect(outcome).toBe('ok')
  })

  it('用户取消 → cancel；失败 → fail', async () => {
    const p = parseWxJsapiParams(JSAPI_JSON)
    await expect(
      invokeWxPay(p, { bridge: fakeBridge('getBrandWCPayRequest:cancel'), whenReady: (cb) => cb() }),
    ).resolves.toBe('cancel')
    await expect(
      invokeWxPay(p, { bridge: fakeBridge('getBrandWCPayRequest:fail'), whenReady: (cb) => cb() }),
    ).resolves.toBe('fail')
  })

  it('err_msg 为空时算 fail（拿不到明确成功就不当成功）', async () => {
    const p = parseWxJsapiParams(JSAPI_JSON)
    await expect(invokeWxPay(p, { bridge: fakeBridge(''), whenReady: (cb) => cb() })).resolves.toBe('fail')
  })

  it('环境里没有 WeixinJSBridge → 明确报错，而不是静默什么都不做', async () => {
    await expect(
      invokeWxPay(parseWxJsapiParams(JSAPI_JSON), { whenReady: (cb) => cb() }),
    ).rejects.toThrow(/微信/)
  })

  it('bridge 尚未注入时要等 ready 之后再调 —— 老 WebView 里它晚于页面脚本', async () => {
    const bridge = fakeBridge()
    let fire: (() => void) | null = null
    const promise = invokeWxPay(parseWxJsapiParams(JSAPI_JSON), {
      bridge,
      whenReady: (cb) => {
        fire = cb
      },
    })

    // ready 尚未触发 → 一次都不该调
    expect(bridge.invoke).not.toHaveBeenCalled()
    fire!()
    await expect(promise).resolves.toBe('ok')
    expect(bridge.invoke).toHaveBeenCalledTimes(1)
  })

  it('不注入 hooks 时读全局 WeixinJSBridge（真机路径）', async () => {
    const bridge = fakeBridge()
    const g = globalThis as { WeixinJSBridge?: WxBridge }
    g.WeixinJSBridge = bridge
    try {
      await expect(invokeWxPay(parseWxJsapiParams(JSAPI_JSON))).resolves.toBe('ok')
      expect(bridge.invoke).toHaveBeenCalledTimes(1)
    } finally {
      delete g.WeixinJSBridge
    }
  })
})

describe('待续跑标记 —— 授权是整页跳转，回来要知道刚才在付哪一笔', () => {
  beforeEach(() => {
    clearWxPayPending()
  })

  it('写进去能读出来（数字，不是字符串）', () => {
    markWxPayPending(8899)
    expect(readWxPayPending()).toBe(8899)
  })

  it('没标记时读出来是 null', () => {
    expect(readWxPayPending()).toBeNull()
  })

  it('清掉之后读不到 —— 续跑只能发生一次', () => {
    markWxPayPending(8899)
    clearWxPayPending()
    expect(readWxPayPending()).toBeNull()
  })

  it('写的是脏数据时不抛错，按「没有标记」处理', () => {
    window.sessionStorage.setItem('maxlabel:wx-pay-pending', 'oops')
    expect(readWxPayPending()).toBeNull()
  })
})
