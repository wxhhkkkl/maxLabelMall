import { beforeEach, describe, expect, it, vi } from 'vitest'

const post = vi.fn()
vi.mock('@/config/http', () => ({
  get: vi.fn(),
  post: (...a: unknown[]) => post(...a),
  put: vi.fn(),
  del: vi.fn(),
}))

const { uploadFile } = await import('./upload')

beforeEach(() => {
  post.mockReset()
})

/**
 * 文件上传（`/app-api/infra/file/upload`）—— 本期只服务于头像。
 *
 * ⚠️ 两个易错点：
 * 1. **字段名必须是 `file`**（后端 `AppFileUploadReqVO.getFile()`）；
 * 2. **绝不能手写 `Content-Type`** —— axios 传 `FormData` 时会自动带上
 *    含 boundary 的 `multipart/form-data`；手写成不带 boundary 的那串会把上传打坏。
 */
describe('上传文件', () => {
  it('POST 到 /infra/file/upload，body 是 FormData 且字段名为 file', async () => {
    post.mockResolvedValue('https://img.example.com/a.png')
    const file = new File(['x'], 'avatar.png', { type: 'image/png' })

    const url = await uploadFile(file)

    expect(post).toHaveBeenCalledTimes(1)
    const [path, body] = post.mock.calls[0]
    expect(path).toBe('/infra/file/upload')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
    // 返回的就是文件的访问 URL —— 直接写进 avatar
    expect(url).toBe('https://img.example.com/a.png')
  })

  it('**不手写 Content-Type**（交给 axios 自己带 boundary）', async () => {
    post.mockResolvedValue('https://img.example.com/a.png')
    await uploadFile(new File(['x'], 'a.png'))
    const config = post.mock.calls[0][2] as { headers?: Record<string, string> } | undefined
    expect(config?.headers?.['Content-Type']).toBeUndefined()
  })

  it('失败要向上抛 —— 界面靠后端文案提示（上传上限由后台配置决定，前端不编数字）', async () => {
    post.mockRejectedValue({ message: '文件大小超出限制' })
    await expect(uploadFile(new File(['x'], 'big.png'))).rejects.toMatchObject({
      message: '文件大小超出限制',
    })
  })
})
