import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/api/software', () => ({
  listSoftwareCategories: vi.fn(),
  listSoftwareByCategory: vi.fn()
}))

import { listSoftwareByCategory, listSoftwareCategories } from '@/api/software'
import SupportSoftwareView from './SupportSoftwareView.vue'

const categories = vi.mocked(listSoftwareCategories)
const byCategory = vi.mocked(listSoftwareByCategory)

beforeEach(() => {
  categories.mockReset()
  byCategory.mockReset()
  push.mockReset()
})

describe('服务支持 · 软件与驱动列表（013 / US49）', () => {
  it('默认选中第一个分类并展示软件', async () => {
    categories.mockResolvedValue([{ id: 1, name: '打印驱动', sort: 0 }])
    byCategory.mockResolvedValue([
      { id: 10, categoryId: 1, title: 'Zebra 驱动', introduction: '适用于 ZD 系列', type: 2, latestVersion: '1.0.1', platforms: ['windows'] }
    ])

    const wrapper = mount(SupportSoftwareView)
    await flushPromises()

    expect(byCategory).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('Zebra 驱动')
    expect(wrapper.text()).toContain('1.0.1')
  })

  it('切换分类时按新分类重新拉取', async () => {
    categories.mockResolvedValue([
      { id: 1, name: '打印驱动', sort: 0 },
      { id: 2, name: '标签软件', sort: 1 }
    ])
    byCategory.mockResolvedValue([])

    const wrapper = mount(SupportSoftwareView)
    await flushPromises()
    await wrapper.findAll('.category-tab')[1]!.trigger('click')
    await flushPromises()

    expect(byCategory).toHaveBeenLastCalledWith(2)
  })

  it('没有分类时给出空态', async () => {
    categories.mockResolvedValue([])
    const wrapper = mount(SupportSoftwareView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无可用下载')
  })

  it('页面不提供搜索框（Q88）', async () => {
    categories.mockResolvedValue([{ id: 1, name: '打印驱动', sort: 0 }])
    byCategory.mockResolvedValue([])
    const wrapper = mount(SupportSoftwareView)
    await flushPromises()

    expect(wrapper.find('input[type="search"]').exists()).toBe(false)
  })
})
