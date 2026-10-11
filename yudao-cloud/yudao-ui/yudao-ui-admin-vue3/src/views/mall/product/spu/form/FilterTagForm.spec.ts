import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import FilterTagForm from './FilterTagForm.vue'

/**
 * 商品筛选标签子表单（特性 003 / US14 / T282）。
 *
 * 钉两件事：
 *   ① **商品侧是多选** —— 同一维度下可以同时挂多个值（"这件货有这两个规格"）；
 *   ② **改分类后要被剔除、并且要说出来** —— 服务端会静默清理掉不在新祖先链上的标签
 *      （FR-097，不报错）。前端若不吭声，运营会以为标签还在，
 *      直到顾客反馈"筛选里搜不到这件商品"。
 */
vi.mock('@/api/mall/product/spu', () => ({
  getSpuFilterValueOptions: vi.fn()
}))

import { ElMessage } from 'element-plus'

import { getSpuFilterValueOptions } from '@/api/mall/product/spu'

/**
 * ⚠️ **故意不 mock `useMessage`**，改成 spy 真实的 `ElMessage`。
 *
 * 早先我按自己以为的形状把 `useMessage` mock 成 `{ message: { warning } }`，
 * 而真实返回的是**一层直接的方法**（`success` / `error` / `warning`，没有 `message` 字段）。
 * 于是组件里 `const { message } = useMessage()` 拿到 undefined ——
 * 线上一点按钮就炸，测试却全绿。**用自己假设的形状去 mock，测的是假设，不是现实。**
 */
const mockWarning = vi.spyOn(ElMessage, 'warning').mockImplementation(() => undefined as never)

/** 「碳带」上配的「宽度」——商品挂在子分类「蜡基」时，它能挂这些值 */
const WIDTH_OPTION = {
  dimensionId: 12,
  dimensionName: '宽度',
  fromCategoryName: '碳带',
  values: [
    { valueId: 3, name: '110' },
    { valueId: 4, name: '100' }
  ]
}
/** 「蜡基」自身的「轴心」 */
const CORE_OPTION = {
  dimensionId: 13,
  dimensionName: '轴心',
  fromCategoryName: '蜡基',
  values: [{ valueId: 7, name: '1英寸' }]
}

function mountForm(formData: Record<string, any>) {
  return mount(FilterTagForm, {
    props: { propFormData: formData, isDetail: false }
  })
}

describe('FilterTagForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getSpuFilterValueOptions).mockResolvedValue([WIDTH_OPTION, CORE_OPTION] as never)
  })

  it('按维度分组渲染，并标出每个维度来自哪个分类', async () => {
    const formData = { id: 9001, categoryId: 114, filterValueIds: [] }
    const wrapper = mountForm(formData)
    await flushPromises()

    expect(wrapper.text()).toContain('宽度')
    expect(wrapper.text()).toContain('（来自「碳带」）')
    expect(wrapper.text()).toContain('轴心')
    // 按分类 + 商品 id 一起问，服务端据此算辖域
    expect(getSpuFilterValueOptions).toHaveBeenCalledWith({ spuId: 9001, categoryId: 114 })
  })

  it('拉不到任何可挂维度时给出明确说明，而不是一片空白', async () => {
    vi.mocked(getSpuFilterValueOptions).mockResolvedValue([] as never)
    const wrapper = mountForm({ id: 9001, categoryId: 114, filterValueIds: [] })
    await flushPromises()

    expect(wrapper.text()).toContain('还没有配置任何筛选维度')
  })

  it('已挂的标签会回显为选中', async () => {
    const formData = { id: 9001, categoryId: 114, filterValueIds: [3, 7] }
    const wrapper = mountForm(formData)
    await flushPromises()

    const checked = wrapper.findAll('.el-checkbox.is-checked')
    expect(checked.length).toBe(2)
  })

  it('首次加载**不会**误报"移除了标签" —— 库里存的本就是合法的', async () => {
    mountForm({ id: 9001, categoryId: 114, filterValueIds: [3, 7] })
    await flushPromises()

    expect(mockWarning).not.toHaveBeenCalled()
  })

  /**
   * ⚠️ 本文件的核心用例：商品从「蜡基」改到别的分类后，
   * 原来挂在「碳带」维度上的值不再适用 —— 要被去掉，**并且说出来**。
   */
  it('改分类后剔除不再适用的标签，并明确告知移除了几个', async () => {
    // 改到新分类后，只剩「轴心」可挂，「宽度」的两个值都不再适用
    vi.mocked(getSpuFilterValueOptions)
      .mockResolvedValueOnce([WIDTH_OPTION, CORE_OPTION] as never) // 首次加载（蜡基）
      .mockResolvedValueOnce([CORE_OPTION] as never) // 改分类后

    // ⚠️ 必须是 **reactive** 的：真实用法里 formData 就是响应式对象，
    // 本表单靠 watch 它的 categoryId 来感知"运营改了分类"。
    // 用普通对象的话改它不会触发 watcher，测的就不是真实行为。
    const formData = reactive({ id: 9001, categoryId: 114, filterValueIds: [3, 4, 7] })
    mountForm(formData)
    await flushPromises()

    // 模拟运营在「基础设置」里改了分类
    formData.categoryId = 117
    await flushPromises()

    // 不适用的两个（110 / 100）被去掉，只留下「轴心」的 1英寸
    expect(formData.filterValueIds).toEqual([7])
    expect(mockWarning).toHaveBeenCalledTimes(1)
    expect(mockWarning.mock.calls[0][0]).toContain('已移除 2 个')
  })

  it('改分类后若没有标签被剔除，就不打扰运营', async () => {
    vi.mocked(getSpuFilterValueOptions)
      .mockResolvedValueOnce([WIDTH_OPTION, CORE_OPTION] as never)
      .mockResolvedValueOnce([WIDTH_OPTION, CORE_OPTION] as never)

    const formData = reactive({ id: 9001, categoryId: 114, filterValueIds: [3] })
    mountForm(formData)
    await flushPromises()

    formData.categoryId = 115
    await flushPromises()

    expect(formData.filterValueIds).toEqual([3])
    expect(mockWarning).not.toHaveBeenCalled()
  })

  it('只读态（详情页）把勾选框禁用', async () => {
    const wrapper = mount(FilterTagForm, {
      props: { propFormData: { id: 9001, categoryId: 114, filterValueIds: [3] }, isDetail: true }
    })
    await flushPromises()

    // element-plus 的 checkbox-group 把 disabled 透传到每个 checkbox 上，根元素不带该属性
    expect(wrapper.findAll('.el-checkbox').length).toBeGreaterThan(0)
    expect(wrapper.findAll('.el-checkbox.is-disabled').length).toBe(wrapper.findAll('.el-checkbox').length)
  })
})
