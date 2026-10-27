import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/mall/software', () => ({
  getSoftwareCategoryPage: vi.fn(),
  createSoftwareCategory: vi.fn(),
  updateSoftwareCategory: vi.fn(),
  deleteSoftwareCategory: vi.fn()
}))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    confirm: vi.fn(async () => true),
    delConfirm: vi.fn(async () => true)
  })
}))

import { createSoftwareCategory, getSoftwareCategoryPage } from '@/api/mall/software'
import CategoryIndex from './index.vue'

const getPage = vi.mocked(getSoftwareCategoryPage)
const createCategory = vi.mocked(createSoftwareCategory)
const directive = { mounted: () => {} }

const mountIndex = () =>
  mount(CategoryIndex, {
    global: { plugins: [createPinia()], directives: { hasPermi: directive } }
  })

beforeEach(() => {
  getPage.mockReset()
  createCategory.mockReset()
})

describe('软件分类管理页（013 / US50）', () => {
  it('展示分类、排序与状态', async () => {
    getPage.mockResolvedValue({
      total: 1,
      list: [{ id: 1, name: '打印驱动', sort: 10, status: 0 }]
    })
    const wrapper = mountIndex()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('打印驱动')
    expect(text).toContain('10')
    expect(text).toContain('启用')
  })

  it('没有分类时列表为空', async () => {
    getPage.mockResolvedValue({ total: 0, list: [] })
    const wrapper = mountIndex()
    await flushPromises()

    expect(wrapper.findAll('.el-table__row')).toHaveLength(0)
  })

  it('新增分类时把名称、排序、状态提交出去', async () => {
    getPage.mockResolvedValue({ total: 0, list: [] })
    createCategory.mockResolvedValue(1)
    const wrapper = mountIndex()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.openForm()
    vm.formData.name = '标签软件'
    vm.formData.sort = 20
    vm.formData.status = 0
    await vm.submitForm()

    expect(createCategory).toHaveBeenCalledWith(
      expect.objectContaining({ name: '标签软件', sort: 20, status: 0 })
    )
  })
})
