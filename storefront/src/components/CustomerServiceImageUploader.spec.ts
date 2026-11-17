import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 客户服务图片上传（021 / FR-580 / T069）
 *
 * 钉两件事：超过 10MB 的图片不上传；合法图片上传后回写 URL。
 */
const uploadFile = vi.fn()
vi.mock('@/api/upload', () => ({ uploadFile: (...a: unknown[]) => uploadFile(...a) }))

const Uploader = (await import('./CustomerServiceImageUploader.vue')).default

function file(name: string, size: number, type = 'image/jpeg') {
  const f = new File(['x'], name, { type })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

async function pick(w: ReturnType<typeof mount>, files: File[]) {
  const input = w.get('input[type="file"]')
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
  await flushPromises()
}

beforeEach(() => {
  uploadFile.mockReset()
  uploadFile.mockResolvedValue('https://img.yuwangchenfa.com/a.jpg')
})

describe('客户服务图片上传', () => {
  it('超过 10MB 的图片按原始大小拦下，不上传', async () => {
    const w = mount(Uploader, { props: { modelValue: [] } })

    await pick(w, [file('big.jpg', 11 * 1024 * 1024)])

    expect(uploadFile).not.toHaveBeenCalled()
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })

  it('合法图片上传后回写 URL', async () => {
    const w = mount(Uploader, { props: { modelValue: [] } })

    await pick(w, [file('ok.jpg', 1024)])

    expect(uploadFile).toHaveBeenCalledTimes(1)
    expect(w.emitted('update:modelValue')![0][0]).toEqual(['https://img.yuwangchenfa.com/a.jpg'])
  })
})
