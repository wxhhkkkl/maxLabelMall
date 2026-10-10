import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const uploadFile = vi.fn()
vi.mock('@/api/upload', () => ({
  uploadFile: (...a: unknown[]) => uploadFile(...a),
}))

const AvatarUploader = (await import('./AvatarUploader.vue')).default

const FILE = new File(['x'], 'avatar.png', { type: 'image/png' })

/** 给 file input 塞一个文件再触发 change（jsdom 里 files 是只读的，得 defineProperty） */
async function pick(w: ReturnType<typeof mount>, file = FILE) {
  const input = w.get('#avatarFile')
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
}

function mountUploader(modelValue = '') {
  return mount(AvatarUploader, { props: { modelValue, nickname: '张三' } })
}

beforeEach(() => {
  uploadFile.mockReset()
  uploadFile.mockResolvedValue('https://img.example.com/new.png')
})

/**
 * 头像上传控件。
 *
 * ⚠️ 只做「选文件 → 调上传 → 把得到的 **URL** 交出去」；**不负责**清空头像
 * （本期没有"移除头像"的需求，且后端 `avatar` 是可空字段）。
 */
describe('AvatarUploader', () => {
  it('有头像时渲染图片；没有时显示占位（不是空白）', async () => {
    const withAvatar = mountUploader('https://img.example.com/a.png')
    expect(withAvatar.get('.av-img').attributes('src')).toBe('https://img.example.com/a.png')

    const without = mountUploader('')
    expect(without.find('.av-img').exists()).toBe(false)
    expect(without.text()).toContain('未设置')
  })

  it('选中文件 → 调上传 → **把返回的 URL 交出去**', async () => {
    const w = mountUploader('')
    await pick(w)

    expect(uploadFile).toHaveBeenCalledWith(FILE)
    expect(w.emitted('update:modelValue')![0]).toEqual(['https://img.example.com/new.png'])
  })

  it('**上传中禁止再触发**（避免并发上传产生两个 URL）', async () => {
    let release: ((v: string) => void) | null = null
    uploadFile.mockImplementation(() => new Promise<string>((r) => (release = r)))

    const w = mountUploader('')
    const input = w.get('#avatarFile')
    Object.defineProperty(input.element, 'files', { value: [FILE], configurable: true })
    await input.trigger('change')
    await flushPromises()

    // 真正拦住重复触发的是 file input 的 disabled（label 本身没有 disabled 属性）
    expect(w.get('#avatarFile').attributes('disabled')).toBeDefined()
    expect(w.text()).toContain('上传中')

    release!('https://img.example.com/new.png')
    await flushPromises()
    expect(w.get('#avatarFile').attributes('disabled')).toBeUndefined()
  })

  it('**上传失败：原样透出后端文案，且不 emit**（保留原值，别把头像弄丢）', async () => {
    uploadFile.mockRejectedValue({ message: '文件大小超出限制' })
    const w = mountUploader('https://img.example.com/old.png')
    await pick(w)

    expect(w.text()).toContain('文件大小超出限制')
    expect(w.emitted('update:modelValue')).toBeFalsy()
    // 预览仍是原来那张
    expect(w.get('.av-img').attributes('src')).toBe('https://img.example.com/old.png')
  })

  it('【反例防护】上传成功前不得 emit —— 否则会把一个还没 URL 的头像提交上去', async () => {
    let release: ((v: string) => void) | null = null
    uploadFile.mockImplementation(() => new Promise<string>((r) => (release = r)))
    const w = mountUploader('')
    const input = w.get('#avatarFile')
    Object.defineProperty(input.element, 'files', { value: [FILE], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(w.emitted('update:modelValue')).toBeFalsy()
    release!('https://img.example.com/new.png')
    await flushPromises()
    expect(w.emitted('update:modelValue')).toBeTruthy()
  })
})
