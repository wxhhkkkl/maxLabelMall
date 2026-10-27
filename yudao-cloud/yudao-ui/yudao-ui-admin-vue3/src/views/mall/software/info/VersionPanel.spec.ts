import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/mall/software', () => ({
  getSoftwareVersionPage: vi.fn(async () => ({ list: [], total: 0 })),
  getPresignedUploadUrl: vi.fn(),
  registerSoftwareVersion: vi.fn(),
  publishSoftwareVersion: vi.fn(),
  unpublishSoftwareVersion: vi.fn(),
  deleteSoftwareVersion: vi.fn()
}))
vi.mock('./sha512', () => ({ sha512OfFile: vi.fn(async () => 'SHA512-BASE64') }))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    confirm: vi.fn(async () => true),
    delConfirm: vi.fn(async () => true)
  })
}))

import { getPresignedUploadUrl, registerSoftwareVersion } from '@/api/mall/software'
import VersionPanel from './VersionPanel.vue'

const presign = vi.mocked(getPresignedUploadUrl)
const register = vi.mocked(registerSoftwareVersion)

beforeEach(() => {
  presign.mockReset()
  register.mockReset()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 200 }))
})

async function pickFile(wrapper: ReturnType<typeof mount>) {
  const input = wrapper.find('input[type="file"]')
  const file = new File(['abc'], 'MaxLabel-Setup-1.0.23.exe', { type: 'application/octet-stream' })
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
}

describe('版本面板 · 上传时序', () => {
  it('算校验和 → 取直传地址 → PUT → 登记，四步都带上正确参数', async () => {
    const wrapper = mount(VersionPanel, { global: { plugins: [createPinia()] } })
    const vm = wrapper.vm as any
    await vm.open(10, 'MaxLabel')
    await flushPromises()

    presign.mockResolvedValue({
      configId: 25,
      path: 'software/windows-x64/1.0.23/MaxLabel-Setup-1.0.23.exe',
      uploadUrl: 'https://oss.example.com/signed-put',
      url: 'https://img.yuwangchenfa.com/software/windows-x64/1.0.23/MaxLabel-Setup-1.0.23.exe'
    })

    await pickFile(wrapper)
    vm.form.version = '1.0.23'
    vm.form.platform = 'windows'
    vm.form.arch = 'x64'
    await vm.handleUpload()
    await flushPromises()

    // 1. 目录规则：software/{platform}-{arch}/{version}
    expect(presign).toHaveBeenCalledWith({
      name: 'MaxLabel-Setup-1.0.23.exe',
      directory: 'software/windows-x64/1.0.23'
    })

    // 2. PUT 到预签名地址（不在应用服务器中转）
    const fetchMock = vi.mocked(fetch)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][0]).toBe('https://oss.example.com/signed-put')
    expect((fetchMock.mock.calls[0][1] as RequestInit).method).toBe('PUT')

    // 3. 登记时回填 path / url / 大小 / 校验和
    expect(register).toHaveBeenCalledWith({
      softwareId: 10,
      version: '1.0.23',
      platform: 'windows',
      arch: 'x64',
      fileName: 'MaxLabel-Setup-1.0.23.exe',
      fileSize: 3,
      sha512: 'SHA512-BASE64',
      objectKey: 'software/windows-x64/1.0.23/MaxLabel-Setup-1.0.23.exe',
      fileUrl: 'https://img.yuwangchenfa.com/software/windows-x64/1.0.23/MaxLabel-Setup-1.0.23.exe',
      releaseNotes: '',
      releaseTime: expect.any(String)
    })
  })

  it('没选文件时不发起任何请求', async () => {
    const wrapper = mount(VersionPanel, { global: { plugins: [createPinia()] } })
    const vm = wrapper.vm as any
    await vm.open(10, 'MaxLabel')

    await vm.handleUpload()

    expect(presign).not.toHaveBeenCalled()
    expect(register).not.toHaveBeenCalled()
  })

  it('PUT 失败时报错且不登记', async () => {
    const wrapper = mount(VersionPanel, { global: { plugins: [createPinia()] } })
    const vm = wrapper.vm as any
    await vm.open(10, 'MaxLabel')
    presign.mockResolvedValue({ configId: 25, path: 'p', uploadUrl: 'https://oss.example.com/x', url: 'https://img.yuwangchenfa.com/p' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 403 }))

    await pickFile(wrapper)
    vm.form.version = '1.0.24'
    await vm.handleUpload()

    expect(register).not.toHaveBeenCalled()
  })
})
