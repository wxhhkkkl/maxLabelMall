import CryptoJS from 'crypto-js'

/**
 * 图形验证码（aj-captcha 滑块）用到的纯函数。
 *
 * 协议要点（三处都**必须逐字对齐服务端**，错一处滑块就永远「验证失败」）：
 *
 * 1. 加密是 **AES-128-ECB + PKCS7**，密钥直接取服务端返回的 `secretKey` 的 UTF-8 字节，
 *    默认密钥是 `XwKsGlMcdPMEhR1B`（服务端没下发 secretKey 时才用）。ECB 没有随机 IV，
 *    所以同一份明文永远得到同一份密文 —— 服务端就是拿密文本身去比对的。
 * 2. 滑块的作答是 `{"x":<整数>,"y":5}`；**y 恒为 5**，服务端只比 x。
 * 3. 给业务接口的二次校验凭据是 `token---{x,y}` 的密文，其中 token 用**明文**拼接。
 *
 * ⚠️ 这里刻意用 `crypto-js` 而不是浏览器原生的 WebCrypto：后者**不提供 ECB 模式**。
 * 实现与上游管理端的 `Verifition/src/utils/ase.ts` 完全一致。
 */

/** aj-captcha 在没下发 secretKey 时使用的默认密钥（与服务端 `AESUtil` 的默认值一致） */
const DEFAULT_SECRET_KEY = 'XwKsGlMcdPMEhR1B'

/** 原图宽度 —— 服务端按这个坐标系比较缺口位置 */
export const ORIGINAL_WIDTH = 310

export function aesEncrypt(word: string, secretKey: string = DEFAULT_SECRET_KEY): string {
  const key = CryptoJS.enc.Utf8.parse(secretKey || DEFAULT_SECRET_KEY)
  const srcs = CryptoJS.enc.Utf8.parse(word)
  return CryptoJS.AES.encrypt(srcs, key, {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7,
  }).toString()
}

/**
 * 把「拖动像素」换算回原图坐标。
 *
 * 渲染宽度会被响应式缩放，而服务端比的是原图（310 宽）里的缺口位置 ——
 * 不还原就会出现「看着对准了、后端说不对」。渲染宽度拿不到时返回 0，不产生 NaN/Infinity。
 */
export function slideOffset(dragPx: number, renderedWidth: number): number {
  if (!renderedWidth || renderedWidth <= 0) return 0
  return Math.round((dragPx * ORIGINAL_WIDTH) / renderedWidth)
}

/** 滑块的作答明文（`check` 请求里要的是它的密文） */
export function slidePointJson(x: number): string {
  return JSON.stringify({ x, y: 5.0 })
}

/**
 * 滑块的作答密文 —— `check` 请求的 `pointJson` 字段。
 */
export function encryptSlidePoint(x: number, secretKey: string): string {
  return aesEncrypt(slidePointJson(x), secretKey)
}

/**
 * 二次校验凭据 —— 业务接口（发短信）的 `captchaVerification` 字段。
 *
 * 服务端在 `check` 通过时就把它算好存进缓存，业务接口只做「存在与否」的比对，
 * 所以**这一串必须与服务端算出来的完全一致**。
 */
export function buildCaptchaVerification(token: string, x: number, secretKey: string): string {
  return aesEncrypt(`${token}---${slidePointJson(x)}`, secretKey)
}
