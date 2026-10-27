import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/template', () => ({
  getSharedTemplatePage: vi.fn(),
  getSharedTemplate: vi.fn(),
  approveSharedTemplate: vi.fn(),
  rejectSharedTemplate: vi.fn(),
  offlineSharedTemplate: vi.fn()
}))

import { getSharedTemplatePage } from '@/api/member/template'
import ReviewIndex from './index.vue'

const getPage = vi.mocked(getSharedTemplatePage)
const directive = { mounted: () => {} }

beforeEach(() => {
  getPage.mockReset()
})

describe('共享模板审核页', () => {
  it('展示待审核条目、发布会员与审核按钮', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [{
        id: 7, sourceTemplateId: 9, publisherMemberId: 1001,
        publisherNickname: '张三', publisherMobile: '138****0000',
        name: '标准物流面单', sizeBytes: 40120, widthMm: 100, heightMm: 150,
        category: '物流', keywords: '面单', auditStatus: 0,
        thumb: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciLz4=',
        createTime: '2026-10-20T12:00:00'
      }]
    })

    const wrapper = mount(ReviewIndex, {
      global: { plugins: [createPinia()], directives: { hasPermi: directive } }
    })
    await flushPromises()

    expect(wrapper.text()).toContain('示意图')
    expect(wrapper.find('img').exists()).toBe(true)
    expect(wrapper.text()).toContain('标准物流面单')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('待审核')
    expect(wrapper.text()).toContain('通过')
    expect(wrapper.text()).toContain('驳回')
  })

  it('已通过条目只显示下架入口', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [{
        id: 8, sourceTemplateId: 10, publisherMemberId: 1002,
        publisherNickname: '李四', name: '已公开模板', sizeBytes: 1000,
        widthMm: 80, heightMm: 60, category: '', keywords: '', auditStatus: 1,
        createTime: '2026-10-20T12:00:00'
      }]
    })
    const wrapper = mount(ReviewIndex, {
      global: { plugins: [createPinia()], directives: { hasPermi: directive } }
    })
    await flushPromises()
    expect(wrapper.text()).toContain('已通过')
    expect(wrapper.text()).toContain('下架')
    expect(wrapper.text()).not.toContain('驳回')
  })
})
