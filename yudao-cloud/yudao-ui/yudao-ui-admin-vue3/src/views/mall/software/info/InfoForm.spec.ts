import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/mall/software', () => ({
  getSoftwareCategoryList: vi.fn(),
  getSoftware: vi.fn(),
  createSoftware: vi.fn(),
  updateSoftware: vi.fn()
}))
vi.mock('@/hooks/web/useMessage', () => ({
  useMessage: () => ({ success: vi.fn(), error: vi.fn(), warning: vi.fn(), delConfirm: vi.fn(async () => true), confirm: vi.fn(async () => true) })
}))

import { createSoftware, getSoftware, getSoftwareCategoryList, updateSoftware } from '@/api/mall/software'
import InfoForm from './InfoForm.vue'

const getCategories = vi.mocked(getSoftwareCategoryList)
const getSoftwareMock = vi.mocked(getSoftware)
const createSoftwareMock = vi.mocked(createSoftware)
const updateSoftwareMock = vi.mocked(updateSoftware)

const mountForm = () => mount(InfoForm, { global: { plugins: [] } })

beforeEach(() => {
  vi.clearAllMocks()
  getCategories.mockResolvedValue([{ id: 1, name: '打印驱动', sort: 0, status: 0 }])
})

describe('软件表单', () => {
  it('新增时把分类、标题、类型、展示开关提交出去', async () => {
    const wrapper = mountForm()
    await (wrapper.vm as any).open()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.formData.categoryId = 1
    vm.formData.title = 'Zebra 驱动'
    vm.formData.type = 2
    vm.formData.showInSupport = false
    await vm.submitForm()

    expect(createSoftwareMock).toHaveBeenCalledWith(
      expect.objectContaining({ categoryId: 1, title: 'Zebra 驱动', type: 2, showInSupport: false })
    )
  })

  it('编辑时带出原有内容并走更新接口', async () => {
    getSoftwareMock.mockResolvedValue({
      id: 10, categoryId: 1, title: 'MaxLabel', introduction: '介绍',
      type: 1, showInSupport: true, status: 0, versions: []
    })
    const wrapper = mountForm()
    await (wrapper.vm as any).open(10)
    await flushPromises()

    const vm = wrapper.vm as any
    expect(vm.formData.title).toBe('MaxLabel')

    vm.formData.title = 'MaxLabel 2'
    await vm.submitForm()

    expect(updateSoftwareMock).toHaveBeenCalledWith(expect.objectContaining({ id: 10, title: 'MaxLabel 2' }))
  })
})
