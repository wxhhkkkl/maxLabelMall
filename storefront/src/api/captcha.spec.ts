import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...a: unknown[]) => get(...a),
  post: (...a: unknown[]) => post(...a),
  put: vi.fn(),
  del: vi.fn(),
}))

const { CAPTCHA_TYPE_SLIDE, checkCaptcha, getCaptcha, isCaptchaEnabled } = await import('./captcha')

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

/**
 * 图形验证码接口（`/app-api/system/captcha/**`）。
 *
 * ⚠️ 这一组是**在服务端新加的 app-api 控制器**（管理端那套挂在 `/admin-api` 下，
 * C 端不能去碰）。它把 aj-captcha 的 `{repCode, repData}` 重新包成了项目的
 * `CommonResult` 信封，所以这里可以走常规的 `get/post` 解包。
 */
describe('图形验证码接口', () => {
  it('查询开关 —— 前端据此决定要不要弹滑块', async () => {
    get.mockResolvedValue(true)
    expect(await isCaptchaEnabled()).toBe(true)
    expect(get).toHaveBeenCalledWith('/system/captcha/enable')
  })

  it('开关返回异常时按「不开启」处理 —— 宁可少弹一层，也不能把发验证码卡死', async () => {
    get.mockRejectedValue(new Error('boom'))
    expect(await isCaptchaEnabled()).toBe(false)
  })

  it('取验证码：默认滑块类型，body 只带 captchaType', async () => {
    post.mockResolvedValue({
      originalImageBase64: 'bg',
      jigsawImageBase64: 'piece',
      token: 'tk',
      secretKey: 'sk',
    })
    const data = await getCaptcha()
    expect(post).toHaveBeenCalledWith('/system/captcha/get', {
      captchaType: CAPTCHA_TYPE_SLIDE,
    })
    expect(CAPTCHA_TYPE_SLIDE).toBe('blockPuzzle')
    expect(data.token).toBe('tk')
    expect(data.secretKey).toBe('sk')
  })

  it('校验：body 是 { captchaType, token, pointJson } —— pointJson 是**密文**，由调用方加密', async () => {
    post.mockResolvedValue({ success: true, msg: null })
    const resp = await checkCaptcha({ token: 'tk', pointJson: 'cipher' })
    expect(post).toHaveBeenCalledWith('/system/captcha/check', {
      captchaType: 'blockPuzzle',
      token: 'tk',
      pointJson: 'cipher',
    })
    expect(resp.success).toBe(true)
  })

  it('校验不通过时 success 为 false 且带原因 —— 界面据此提示并刷新', async () => {
    post.mockResolvedValue({ success: false, msg: '验证码错误' })
    const resp = await checkCaptcha({ token: 'tk', pointJson: 'bad' })
    expect(resp.success).toBe(false)
    expect(resp.msg).toBe('验证码错误')
  })
})
