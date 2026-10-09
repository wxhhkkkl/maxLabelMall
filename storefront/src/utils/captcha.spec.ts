import { describe, expect, it } from 'vitest'

import { aesEncrypt, buildCaptchaVerification, slideOffset, slidePointJson } from './captcha'

/**
 * 图形验证码（aj-captcha 滑块）用到的纯函数。
 *
 * ⚠️ AES 那两个期望值是**用 Node 的 `crypto` 独立算出来**的（不是拿 crypto-js 自证）：
 * 算法是 AES-128-ECB/PKCS7、密钥直接取 `secretKey` 的 UTF-8 字节。
 * 加密方式一旦改动，服务端就解不出来了 —— 它比对的是密文本身。
 */
const KEY = 'Iir1lkUSLYB43kaG'

describe('aesEncrypt —— aj-captcha 协议的加密方式', () => {
  it('AES-128-ECB/PKCS7，密钥是 secretKey 的 UTF-8 字节', () => {
    expect(aesEncrypt('{"x":123,"y":5}', KEY)).toBe('IS1Q18/JeM4Lg4JFrwbhEg==')
  })

  it('同一份输入永远得到同一份密文（ECB，无随机 IV —— 服务端才比得上）', () => {
    expect(aesEncrypt('abc', KEY)).toBe(aesEncrypt('abc', KEY))
  })

  it('换密钥结果就变', () => {
    expect(aesEncrypt('abc', KEY)).not.toBe(aesEncrypt('abc', 'ybcu6rgB4iGFTDh1'))
  })
})

describe('slidePointJson —— 滑块的作答', () => {
  it('就是 {x, y:5} 的 JSON —— y 恒为 5，服务端只比 x', () => {
    expect(slidePointJson(123)).toBe('{"x":123,"y":5}')
  })

  it('x 是整数（拖动像素换算到原图坐标系后取整）', () => {
    expect(slidePointJson(slideOffset(62, 310))).toBe('{"x":62,"y":5}')
  })
})

describe('slideOffset —— 把渲染像素换算回原图坐标（原图宽 310）', () => {
  it('渲染宽度就是 310 时，1:1', () => {
    expect(slideOffset(123, 310)).toBe(123)
  })

  it('渲染被缩放时要按比例还原 —— 否则服务端对不上缺口位置', () => {
    // 渲染成 620 宽（放大一倍）：拖 246px 相当于原图 123
    expect(slideOffset(246, 620)).toBe(123)
    // 渲染成 155 宽（缩小一半）：拖 61.5px 相当于原图 123
    expect(slideOffset(61.5, 155)).toBe(123)
  })

  it('渲染宽度拿不到（jsdom 里 offsetWidth 为 0）时不产生 Infinity/NaN', () => {
    expect(slideOffset(50, 0)).toBe(0)
  })
})

describe('buildCaptchaVerification —— 给业务接口的二次校验凭据', () => {
  it('是 `token---{x,y}` 的密文，且 token 用的是**明文**拼接', () => {
    expect(buildCaptchaVerification('3f2a1b', 123, KEY)).toBe(
      'tDg/z5off8zbC3YzaBhHKfzEWpfCxT873zpiWRlZD8s=',
    )
  })

  it('x 不同 → 凭据不同（服务端据此知道缺口位置）', () => {
    expect(buildCaptchaVerification('t', 100, KEY)).not.toBe(
      buildCaptchaVerification('t', 200, KEY),
    )
  })
})
