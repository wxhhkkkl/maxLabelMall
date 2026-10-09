import { get, post } from '@/config/http'

/**
 * 图形验证码接口（`/app-api/system/captcha/**`）。
 *
 * 这一组**不是**管理端那套 `/admin-api/system/captcha/*` —— C 端只走 `/app-api`。
 * 服务端为此新增了 app 端控制器，并把 aj-captcha 原本的 `{repCode, repData}`
 * 重新包成了项目的 `CommonResult` 信封，所以这里能走常规解包，不需要给 http 层开洞。
 *
 * 用途很窄：**发短信之前先过一道滑块**，挡住脚本刷短信。校验点在服务端的
 * `/member/auth/send-sms-code`，前端这套只是把凭据拿回来。
 */

/** 滑块拼图（`aj.captcha.type` 的取值之一） */
export const CAPTCHA_TYPE_SLIDE = 'blockPuzzle'

export interface CaptchaData {
  /** 底图（带缺口）base64 */
  originalImageBase64: string
  /** 拼图块 base64（47×155） */
  jigsawImageBase64: string
  /** 本次验证码的 token，校验时原样带回 */
  token: string
  /** 本次验证码的 AES 密钥 */
  secretKey: string
}

export interface CaptchaCheckResp {
  success: boolean
  /** 不通过的原因，可直接展示 */
  msg?: string
}

/**
 * 图形验证码开关（服务端的 `yudao.captcha.enable`）。
 *
 * **开关状态的唯一权威在服务端**：前端不读自己的环境变量，避免出现「前端弹了滑块、
 * 服务端没校验」或反过来的错配。查不到时按「不开启」处理 —— 少弹一层总比把
 * 发验证码整条路卡死要好（服务端没开启时，不传凭据也能发码）。
 */
export async function isCaptchaEnabled(): Promise<boolean> {
  try {
    return (await get<boolean>('/system/captcha/enable')) === true
  } catch {
    return false
  }
}

/** 取一张新验证码 */
export function getCaptcha(captchaType: string = CAPTCHA_TYPE_SLIDE): Promise<CaptchaData> {
  return post<CaptchaData>('/system/captcha/get', { captchaType })
}

/**
 * 校验滑块的作答。
 *
 * ⚠️ `pointJson` 要传**密文**（`@/utils/captcha` 的 `encryptSlidePoint`）——
 * 服务端拿本次的 secretKey 解密后才比对缺口位置。
 */
export function checkCaptcha(p: {
  token: string
  pointJson: string
  captchaType?: string
}): Promise<CaptchaCheckResp> {
  return post<CaptchaCheckResp>('/system/captcha/check', {
    captchaType: p.captchaType ?? CAPTCHA_TYPE_SLIDE,
    token: p.token,
    pointJson: p.pointJson,
  })
}
