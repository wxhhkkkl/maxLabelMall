import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const uploadFile = vi.fn()
vi.mock('@/api/upload', () => ({
  uploadFile: (...a: unknown[]) => uploadFile(...a),
}))

const MultiImageUploader = (await import('./MultiImageUploader.vue')).default

const F = (name: string) => new File(['x'], name, { type: 'image/png' })

/** 给 file input 塞文件并触发 change（jsdom 里 files 只读，得 defineProperty） */
async function pick(w: ReturnType<typeof mount>, files: File[]) {
  const input = w.get('#commentPics')
  Object.defineProperty(input.element, 'files', { value: files, configurable: true })
  await input.trigger('change')
  await flushPromises()
}

function mountUploader(modelValue: string[] = []) {
  return mount(MultiImageUploader, { props: { modelValue } })
}

beforeEach(() => {
  uploadFile.mockReset()
  uploadFile.mockResolvedValue('https://img.example.com/a.png')
})

/**
 * 评价配图上传（FR-076）。
 *
 * 与 `AvatarUploader` 的关键差别：那个**只支持单张**（硬读 `files[0]`、没有 `multiple`、
 * `modelValue` 是字符串），所以这里是独立实现而不是改它 —— 改它会动到个人中心在用的组件。
 *
 * 两条不能错的行为：
 * - **超过 9 张要在上传前拦住**（不是传完再报错）；
 * - **中途失败不能把已经传成功的那几张丢掉** —— 那等于用户白传一遍。
 */
describe('MultiImageUploader —— 上传', () => {
  it('选多张时逐个上传，并交出全部 URL', async () => {
    uploadFile.mockResolvedValueOnce('u1.png').mockResolvedValueOnce('u2.png')
    const w = mountUploader()
    await pick(w, [F('a.png'), F('b.png')])

    expect(uploadFile).toHaveBeenCalledTimes(2)
    expect(w.emitted('update:modelValue')!.at(-1)).toEqual([['u1.png', 'u2.png']])
  })

  it('已有图片时把新传的追加在后面，不覆盖', async () => {
    uploadFile.mockResolvedValue('u-new.png')
    const w = mountUploader(['old.png'])
    await pick(w, [F('a.png')])
    expect(w.emitted('update:modelValue')!.at(-1)).toEqual([['old.png', 'u-new.png']])
  })

  it('**只剩 1 个名额时选 2 张：只传 1 张**，并提示上限', async () => {
    uploadFile.mockResolvedValue('u.png')
    const w = mountUploader(Array.from({ length: 8 }, (_, i) => `p${i}.png`))
    await pick(w, [F('a.png'), F('b.png')])

    expect(uploadFile).toHaveBeenCalledTimes(1)
    expect(w.emitted('update:modelValue')!.at(-1)![0]).toHaveLength(9)
    expect(w.text()).toContain('9')
  })

  it('**已满 9 张时不再上传**，直接提示', async () => {
    const w = mountUploader(Array.from({ length: 9 }, (_, i) => `p${i}.png`))
    await pick(w, [F('a.png')])

    expect(uploadFile).not.toHaveBeenCalled()
    expect(w.text()).toContain('9')
  })
})

describe('MultiImageUploader —— 失败与删除', () => {
  it('**中途失败不丢已传成功的图**，并把后端文案透出来', async () => {
    uploadFile.mockResolvedValueOnce('u1.png').mockRejectedValueOnce({ message: '文件大小超出限制' })
    const w = mountUploader(['old.png'])
    await pick(w, [F('a.png'), F('b.png')])

    expect(w.text()).toContain('文件大小超出限制')
    // 已经传成功的 u1 保住了，旧的 old 也还在
    expect(w.emitted('update:modelValue')!.at(-1)).toEqual([['old.png', 'u1.png']])
  })

  it('第一张就失败时保留原值，不 emit 空数组', async () => {
    uploadFile.mockRejectedValue({ message: '上传失败' })
    const w = mountUploader(['old.png'])
    await pick(w, [F('a.png')])
    expect(w.emitted('update:modelValue')).toBeFalsy()
  })

  it('能删除已上传的一张', async () => {
    const w = mountUploader(['a.png', 'b.png'])
    await w.findAll('.mi-del')[0]?.trigger('click')
    expect(w.emitted('update:modelValue')!.at(-1)).toEqual([['b.png']])
  })

  it('上传中显示状态并禁用再次选择', async () => {
    let release: ((v: string) => void) | null = null
    uploadFile.mockImplementation(() => new Promise<string>((r) => (release = r)))
    const w = mountUploader()
    const input = w.get('#commentPics')
    Object.defineProperty(input.element, 'files', { value: [F('a.png')], configurable: true })
    await input.trigger('change')
    await flushPromises()

    expect(w.get('#commentPics').attributes('disabled')).toBeDefined()

    release!('u.png')
    await flushPromises()
    expect(w.get('#commentPics').attributes('disabled')).toBeUndefined()
  })
})
