import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 微信扫码登录「补全页」的测试（T352 / US17）
 *
 * 它测试的是**这个页面该做什么**，不是后端的建号逻辑（那在 T351 里测过了）：
 * 手机号必填、验证码要走既有发码接口、提交成功后把（可能有的）自选头像交给上层、
 * 关掉时把这次扫码作废。
 */

const completeWechatScan = vi.fn()
const abandonWechatScanTicket = vi.fn()
vi.mock('@/api/wechatLogin', () => ({
  completeWechatScan: (...a: unknown[]) => completeWechatScan(...a),
  abandonWechatScanTicket: (...a: unknown[]) => abandonWechatScanTicket(...a),
}))

const isCaptchaEnabled = vi.fn()
vi.mock('@/api/captcha', () => ({
  isCaptchaEnabled: (...a: unknown[]) => isCaptchaEnabled(...a),
}))

const sendSmsCode = vi.fn()
vi.mock('@/api/member', () => ({
  sendSmsCode: (...a: unknown[]) => sendSmsCode(...a),
  SMS_SCENE_MEMBER_LOGIN: 1,
}))

const ProfileCompletionDialog = (await import('./ProfileCompletionDialog.vue')).default

async function mountDialog(props: Record<string, unknown> = {}) {
  const w = mount(ProfileCompletionDialog, {
    props: { open: true, ticket: 't-1', wxNickname: '张三', wxAvatar: 'https://wx/a.png', ...props },
  })
  await flushPromises()
  return w
}

async function fillValidForm(w: Awaited<ReturnType<typeof mountDialog>>) {
  await w.get('#pcMobile').setValue('13800138000')
  await w.get('#pcCode').setValue('1234')
}

beforeEach(() => {
  completeWechatScan.mockReset()
  completeWechatScan.mockResolvedValue(true)
  abandonWechatScanTicket.mockReset()
  abandonWechatScanTicket.mockResolvedValue(true)
  sendSmsCode.mockReset()
  sendSmsCode.mockResolvedValue(true)
  isCaptchaEnabled.mockReset()
  isCaptchaEnabled.mockResolvedValue(false)
})

describe('ProfileCompletionDialog —— 首次微信登录补全资料（T352 / US17）', () => {
  it('展示微信带来的昵称与头像（客户不用自己传）', async () => {
    const w = await mountDialog()
    expect(w.text()).toContain('张三')
    expect(w.get('#pcAvatarPreview').attributes('src')).toBe('https://wx/a.png')
  })

  it('**手机号是必填**：没填完不让提交', async () => {
    const w = await mountDialog()
    await w.get('#pcSubmit').trigger('click')
    await flushPromises()

    expect(completeWechatScan).not.toHaveBeenCalled()
    expect(w.emitted('completed')).toBeUndefined()
  })

  it('「获取验证码」走既有发码接口，场景是登录（1）', async () => {
    const w = await mountDialog()
    await w.get('#pcMobile').setValue('13800138000')
    await w.get('#pcGetCode').trigger('click')
    await flushPromises()

    expect(sendSmsCode).toHaveBeenCalledWith('13800138000', 1)
  })

  it('**服务端开着图形验证码时，先弹滑块、绝不直接发码**（否则会被后端拒）', async () => {
    // ⚠️ 这条是 2026-10-14 真机测出来的：补全页原先直接调发码接口，
    //    而后端 `sendSmsCode` 第一步就是图形验证码闸门（线上开关是开的），
    //    于是客户看到「图形验证码校验未通过，请重新验证」。
    //    闸门开关由**服务端**给（isCaptchaEnabled），前端不读自己的环境变量。
    isCaptchaEnabled.mockResolvedValue(true)
    const w = await mountDialog()
    await w.get('#pcMobile').setValue('13800138000')
    await w.get('#pcGetCode').trigger('click')
    await flushPromises()

    expect(sendSmsCode).not.toHaveBeenCalled()
    expect(w.findComponent({ name: 'CaptchaSlider' }).props('open')).toBe(true)
  })

  it('**滑块通过之后才发码，并把凭据带上**', async () => {
    isCaptchaEnabled.mockResolvedValue(true)
    const w = await mountDialog()
    await w.get('#pcMobile').setValue('13800138000')
    await w.get('#pcGetCode').trigger('click')
    await flushPromises()

    // 直接驱动子组件的 success 事件（拖动本身由 CaptchaSlider.spec.ts 覆盖）
    await w.findComponent({ name: 'CaptchaSlider' }).vm.$emit('success', 'VERIFY-1')
    await flushPromises()

    expect(sendSmsCode).toHaveBeenCalledWith('13800138000', 1, 'VERIFY-1')
  })

  it('提交成功后把票据、手机号、验证码与公司名一起交上去，并通知上层', async () => {
    const w = await mountDialog()
    await fillValidForm(w)
    await w.get('#pcCompany').setValue('某某印刷厂')
    await w.get('#pcSubmit').trigger('click')
    await flushPromises()

    expect(completeWechatScan).toHaveBeenCalledWith('t-1', '13800138000', '1234', '某某印刷厂')
    // 没选自选头像 → 交给上层的是 null（默认用微信头像）
    expect(w.emitted('completed')).toEqual([[null]])
  })

  it('客户选了自选头像时，把文件交给上层 —— **不在这里上传**（那时还没登录）', async () => {
    const w = await mountDialog()
    await fillValidForm(w)
    const file = new File(['x'], 'me.png', { type: 'image/png' })
    const input = w.get('#pcAvatarInput')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await w.get('#pcSubmit').trigger('click')
    await flushPromises()

    expect(w.emitted('completed')).toEqual([[file]])
  })

  it('**验证码不过时不通知上层**（后端拒绝了，就不该往下走）', async () => {
    completeWechatScan.mockRejectedValue(new Error('验证码已过期'))
    const w = await mountDialog()
    await fillValidForm(w)
    await w.get('#pcSubmit').trigger('click')
    await flushPromises()

    expect(w.emitted('completed')).toBeUndefined()
    expect(w.text()).toContain('验证码已过期')
  })

  it('**该手机号已绑过别的微信时，把后端的原话透出来**（不吞成"操作失败"）', async () => {
    // 后端 `bindSocialUserByOpenid` 会拒绝并给出这句话（Q21=C / FR-146）——
    // 客户需要知道"换个微信号"还是"换个手机号"，笼统的"失败"帮不上忙
    completeWechatScan.mockRejectedValue(new Error('该账号已绑定其他微信'))
    const w = await mountDialog()
    await fillValidForm(w)
    await w.get('#pcSubmit').trigger('click')
    await flushPromises()

    expect(w.text()).toContain('该账号已绑定其他微信')
    expect(w.emitted('completed')).toBeUndefined()
  })

  it('关掉补全页 → **把这次扫码作废**（不建会员、不建绑定、不留登录态）', async () => {
    const w = await mountDialog()
    await w.get('#pcClose').trigger('click')
    await flushPromises()

    expect(abandonWechatScanTicket).toHaveBeenCalledWith('t-1')
    expect(w.emitted('close')).toBeTruthy()
  })

  it('公司名称是**选填**：不填也能提交', async () => {
    const w = await mountDialog()
    await fillValidForm(w)
    await w.get('#pcSubmit').trigger('click')
    await flushPromises()

    expect(completeWechatScan).toHaveBeenCalledWith('t-1', '13800138000', '1234', undefined)
    expect(w.emitted('completed')).toBeTruthy()
  })
})
