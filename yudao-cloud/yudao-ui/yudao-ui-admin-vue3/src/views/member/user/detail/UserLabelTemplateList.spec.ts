import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/member/template', () => ({
  getTemplatePage: vi.fn(),
  getTemplateContent: vi.fn()
}))

import { getTemplateContent, getTemplatePage } from '@/api/member/template'
import UserLabelTemplateList from './UserLabelTemplateList.vue'

const getPage = vi.mocked(getTemplatePage)
const getContent = vi.mocked(getTemplateContent)

beforeEach(() => {
  getPage.mockReset()
  getContent.mockReset()
})

describe('会员标签模板页签', () => {
  it('展示模板元数据与共享状态，并支持预览', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [{
        id: 9, name: '顺丰面单', sizeBytes: 48211, widthMm: 100, heightMm: 150,
        category: '物流', keywords: '面单', shareStatus: 1,
        thumb: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciLz4=',
        createTime: '2026-10-20T10:00:00', updateTime: '2026-10-20T10:00:00'
      }]
    })
    getContent.mockResolvedValue({
      id: 9, name: '顺丰面单', sizeBytes: 48211, widthMm: 100, heightMm: 150,
      category: '物流', keywords: '面单', shareStatus: 1,
      thumb: 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciLz4=',
      createTime: '', updateTime: '', content: '{"format":"maxlabel-msdx"}'
    })

    const wrapper = mount(UserLabelTemplateList, {
      props: { userId: 1001 },
      global: { plugins: [createPinia()] }
    })
    await flushPromises()
    expect(wrapper.text()).toContain('示意图')
    expect(wrapper.find('img').exists()).toBe(true)
    expect(wrapper.text()).toContain('顺丰面单')
    expect(wrapper.text()).toContain('已公开')
    expect(wrapper.text()).toContain('100 × 150 mm')

    await wrapper.findAll('button').find((button) => button.text() === '预览')!.trigger('click')
    await flushPromises()
    expect(getContent).toHaveBeenCalledWith(9)
  })
})
