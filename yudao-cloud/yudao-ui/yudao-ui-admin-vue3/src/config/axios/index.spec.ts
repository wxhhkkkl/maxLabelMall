import { beforeEach, describe, expect, it, vi } from 'vitest'

const service = vi.fn()
vi.mock('./service', () => ({
  service: (...args: unknown[]) => service(...args)
}))

const request = (await import('./index')).default

describe('axios request.upload（017 后台图片上传修复）', () => {
  beforeEach(() => {
    service.mockReset()
    service.mockResolvedValue({ data: 'https://img.example.com/a.png' })
  })

  it('普通对象会被转成 FormData，文件与目录字段都在', async () => {
    const file = new File(['x'], 'a.png', { type: 'image/png' })
    await request.upload({
      url: '/infra/file/upload',
      data: { file, directory: 'product' }
    })

    const option = service.mock.calls[0][0]
    expect(option.data).toBeInstanceOf(FormData)
    expect((option.data as FormData).get('file')).toBe(file)
    expect((option.data as FormData).get('directory')).toBe('product')
    expect(option.headers['Content-Type']).toBe('multipart/form-data')
  })

  it('已经是 FormData 的调用方（CRM / 工资 / 平台内容）不会被覆盖', async () => {
    const formData = new FormData()
    formData.append('file', new File(['x'], 'a.png', { type: 'image/png' }))
    await request.upload({ url: '/crm/customer/import', data: formData })

    expect(service.mock.calls[0][0].data).toBe(formData)
  })
})
