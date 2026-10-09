import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const getCaptcha = vi.fn()
const checkCaptcha = vi.fn()
vi.mock('@/api/captcha', () => ({
  CAPTCHA_TYPE_SLIDE: 'blockPuzzle',
  getCaptcha: (...a: unknown[]) => getCaptcha(...a),
  checkCaptcha: (...a: unknown[]) => checkCaptcha(...a),
  isCaptchaEnabled: vi.fn(),
}))

const CaptchaSlider = (await import('./CaptchaSlider.vue')).default

/** 一张假的验证码：只需要字段齐全，图随便给 */
const DATA = {
  originalImageBase64: 'BG',
  jigsawImageBase64: 'PIECE',
  token: 'tk-1',
  secretKey: 'Iir1lkUSLYB43kaG',
}

/**
 * 给元素塞一个「布局尺寸」。
 *
 * ⚠️ jsdom 不做布局，`offsetWidth` 恒为 0 —— 那样最大可拖距离就是 0，拖动永远等于没动，
 * 「拖得越远凭据越不同」这类断言会假绿。所以这里显式把三个元素的尺寸钉住：
 * 轨道 320、手柄 40（可拖 280）、舞台 310（等于原图宽，像素换算 1:1）。
 */
function stubLayout(w: ReturnType<typeof mount>) {
  const set = (sel: string, width: number) => {
    const el = w.get(sel).element
    Object.defineProperty(el, 'offsetWidth', { configurable: true, get: () => width })
  }
  set('.cap-track', 320)
  set('#captchaHandle', 40)
  set('.cap-stage', 310)
}

/** 模拟一次拖动：按下手柄 → 移动 → 松手（松手才提交） */
async function drag(w: ReturnType<typeof mount>, deltaX: number) {
  await w.get('#captchaHandle').trigger('pointerdown', { clientX: 0 })
  window.dispatchEvent(new MouseEvent('pointermove', { clientX: deltaX }))
  window.dispatchEvent(new MouseEvent('pointerup'))
  await flushPromises()
}

beforeEach(() => {
  getCaptcha.mockReset()
  getCaptcha.mockResolvedValue(DATA)
  checkCaptcha.mockReset()
  checkCaptcha.mockResolvedValue({ success: true, msg: null })
})

describe('CaptchaSlider —— 滑块图形验证码', () => {
  it('打开时就去取一张验证码', async () => {
    mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    expect(getCaptcha).toHaveBeenCalledTimes(1)
  })

  it('没打开时不取 —— 别在用户没要验证码时白打接口', async () => {
    mount(CaptchaSlider, { props: { open: false } })
    await flushPromises()
    expect(getCaptcha).not.toHaveBeenCalled()
  })

  it('**拖动后把作答的密文交给 check**，而不是明文坐标', async () => {
    const w = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    stubLayout(w)

    await drag(w, 60)

    expect(checkCaptcha).toHaveBeenCalledTimes(1)
    const arg = checkCaptcha.mock.calls[0][0] as { token: string; pointJson: string }
    expect(arg.token).toBe('tk-1')
    // 密文：既不是明文 JSON，也不为空
    expect(arg.pointJson).not.toContain('{')
    expect(arg.pointJson.length).toBeGreaterThan(10)
  })

  it('**校验通过后把 captchaVerification 交出去** —— 调用方要靠它发短信', async () => {
    const w = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    stubLayout(w)

    await drag(w, 60)

    const emitted = w.emitted('success')
    expect(emitted).toBeTruthy()
    const verification = emitted![0][0] as string
    expect(typeof verification).toBe('string')
    expect(verification.length).toBeGreaterThan(10)
    // 不是明文 token---json
    expect(verification).not.toContain('---')
  })

  it('**拖动距离不同，交出去的凭据就不同**（服务端据此判断缺口）', async () => {
    const w1 = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    stubLayout(w1)
    await drag(w1, 60)

    const w2 = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    stubLayout(w2)
    await drag(w2, 120)

    expect(w1.emitted('success')![0][0]).not.toBe(w2.emitted('success')![0][0])
  })

  it('校验不通过：给出原因、**不交凭据**，并换一张新图', async () => {
    checkCaptcha.mockResolvedValue({ success: false, msg: '验证码错误' })
    const w = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    stubLayout(w)

    await drag(w, 60)

    expect(w.emitted('success')).toBeFalsy()
    expect(w.text()).toContain('验证码错误')
    // 一次性验证码：失败必须重新取一张，不能让人拿同一张图反复试
    expect(getCaptcha).toHaveBeenCalledTimes(2)
  })

  it('check 抛错时也不交凭据，并给出可重试的提示', async () => {
    checkCaptcha.mockRejectedValue(new Error('网络异常'))
    const w = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    stubLayout(w)

    await drag(w, 60)

    expect(w.emitted('success')).toBeFalsy()
    expect(w.text()).toContain('网络异常')
  })

  it('取图失败时给出提示，不白屏', async () => {
    getCaptcha.mockRejectedValue(new Error('服务不可用'))
    const w = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    expect(w.text()).toContain('服务不可用')
  })

  it('通过之后不再响应拖动 —— 防止重复提交', async () => {
    const w = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    await drag(w, 60)
    expect(checkCaptcha).toHaveBeenCalledTimes(1)

    await drag(w, 80)
    expect(checkCaptcha).toHaveBeenCalledTimes(1)
  })

  it('点「换一张」重新取图', async () => {
    const w = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    await w.get('.cap-refresh').trigger('click')
    await flushPromises()
    expect(getCaptcha).toHaveBeenCalledTimes(2)
  })

  it('关闭时 emit close（遮罩/Esc/× 都走这里）', async () => {
    const w = mount(CaptchaSlider, { props: { open: true } })
    await flushPromises()
    await w.get('.ml-modal-close').trigger('click')
    expect(w.emitted('close')).toBeTruthy()
  })
})
