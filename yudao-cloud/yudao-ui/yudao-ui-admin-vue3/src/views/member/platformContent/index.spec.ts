import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/platformContent', () => ({
  PlatformContentApi: {
    getPage: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    upload: vi.fn(),
    updateStatus: vi.fn(),
    delete: vi.fn()
  }
}))

import { PlatformContentApi } from '@/api/member/platformContent'
import PlatformContentIndex from './index.vue'

const getPage = vi.mocked(PlatformContentApi.getPage)
const directive = { mounted: () => {} }

beforeEach(() => {
  getPage.mockReset()
})

describe('平台共享内容页', () => {
  it('展示名称、类型、状态与操作入口', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [{
        id: 8, contentType: 1, name: '标准物流面单.msdx', sizeBytes: 2048,
        category: '物流', keywords: '面单', status: 1,
        publishedTime: '2026-11-05T10:00:00'
      }]
    })

    const wrapper = mount(PlatformContentIndex, {
      global: { plugins: [createPinia()], directives: { hasPermi: directive } }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('标准物流面单.msdx')
    expect(wrapper.text()).toContain('模板')
    expect(wrapper.text()).toContain('上架')
    expect(wrapper.text()).toContain('编辑')
    expect(wrapper.text()).toContain('删除')
  })

  it('下架条目展示上架按钮', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [{
        id: 9, contentType: 5, name: '样例.png', sizeBytes: 1024,
        category: '', keywords: '', status: 0
      }]
    })

    const wrapper = mount(PlatformContentIndex, {
      global: { plugins: [createPinia()], directives: { hasPermi: directive } }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('图片')
    expect(wrapper.text()).toContain('下架')
    expect(wrapper.text()).toContain('上架')
  })
})
