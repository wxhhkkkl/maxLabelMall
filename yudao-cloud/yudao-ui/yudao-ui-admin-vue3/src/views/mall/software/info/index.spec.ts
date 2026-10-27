import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/mall/software', () => ({
  getSoftwarePage: vi.fn(),
  getSoftwareCategoryList: vi.fn(),
  deleteSoftware: vi.fn(),
  getSoftware: vi.fn(),
  createSoftware: vi.fn(),
  updateSoftware: vi.fn()
}))

import { getSoftwareCategoryList, getSoftwarePage } from '@/api/mall/software'
import SoftwareIndex from './index.vue'

const getPage = vi.mocked(getSoftwarePage)
const getCategories = vi.mocked(getSoftwareCategoryList)
const directive = { mounted: () => {} }

const mountIndex = () =>
  mount(SoftwareIndex, {
    global: { plugins: [createPinia()], directives: { hasPermi: directive } }
  })

beforeEach(() => {
  getPage.mockReset()
  getCategories.mockReset()
  getCategories.mockResolvedValue([{ id: 1, name: '打印驱动', sort: 0, status: 0 }])
})

describe('软件分发列表', () => {
  it('展示软件、分类名、类型与在服务支持页的可见性', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [
        {
          id: 10,
          categoryId: 1,
          title: 'Zebra 驱动',
          type: 2,
          showInSupport: true,
          status: 0
        }
      ]
    })
    const wrapper = mountIndex()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('Zebra 驱动')
    expect(text).toContain('打印驱动')
    expect(text).toContain('展示')
    expect(text).toContain('启用')
  })

  it('没有软件时列表为空', async () => {
    getPage.mockResolvedValue({ total: 0, list: [] })
    const wrapper = mountIndex()
    await flushPromises()

    expect(wrapper.findAll('.el-table__row')).toHaveLength(0)
  })

  it('搜索时回到第一页并带上筛选条件', async () => {
    getPage.mockResolvedValue({ total: 0, list: [] })
    const wrapper = mountIndex()
    await flushPromises()
    getPage.mockClear()

    await wrapper.find('input[placeholder="请输入标题"]').setValue('Zebra')
    await wrapper.findAll('button').find((b) => b.text().includes('搜索'))!.trigger('click')
    await flushPromises()

    expect(getPage).toHaveBeenCalledWith(expect.objectContaining({ pageNo: 1, title: 'Zebra' }))
  })
})
