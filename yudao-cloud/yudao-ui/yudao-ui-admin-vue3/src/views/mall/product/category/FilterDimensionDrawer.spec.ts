import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import FilterDimensionDrawer from './FilterDimensionDrawer.vue'

/**
 * 筛选维度抽屉的结构测试（特性 003 / US13 / T261）。
 *
 * 它只钉一件事，但这件事是**安全阀**：
 * **「继承自父分类」那一段必须是只读的。**
 *
 * 维度沿分类树向下继承（Q10=B）。若把继承来的维度渲染成可编辑的，运营在「蜡基」页
 * "删掉"一条，实际删的是「碳带」上的配置 —— 混合基与全树脂两个兄弟分类会**静默**跟着变。
 * 所以这里断言：自有段有改名/删除入口，继承段**一个都没有**，且逐条标明来源。
 */
vi.mock('@/api/mall/product/filterDimension', () => ({
  getDimensionList: vi.fn(),
  createDimension: vi.fn(),
  updateDimension: vi.fn(),
  deleteDimension: vi.fn(),
  getDimensionDeleteImpact: vi.fn()
}))
vi.mock('@/api/mall/product/filterValue', () => ({
  getValueList: vi.fn(),
  createValue: vi.fn(),
  updateValue: vi.fn(),
  deleteValue: vi.fn(),
  getValueDeleteImpact: vi.fn()
}))
// ⚠️ 这里**故意不 mock** `useMessage`。
// 早先我按自己以为的形状 mock 成 `{ message: { success } }`，而真实的 `useMessage()`
// 返回的是**一层直接的方法**（`success` / `error` / `warning`）—— 于是组件里
// `const { message } = useMessage()` 拿到 undefined，线上一点「新增维度」就报
// `Cannot read properties of undefined (reading 'success')`，而测试全绿。
// **用自己假设的形状去 mock，测的是假设，不是现实。** 现在让它用真的。

import { ElMessageBox } from 'element-plus'

import { createDimension, getDimensionList, updateDimension } from '@/api/mall/product/filterDimension'
import { getValueList } from '@/api/mall/product/filterValue'

const OWN_DIMENSION = { id: 21, categoryId: 114, name: '宽度', sort: 1 }
const INHERITED_DIMENSION = {
  id: 12,
  categoryId: 113,
  name: '长度',
  sort: 1,
  inheritedFromCategoryName: '碳带'
}

async function mountDrawer() {
  vi.mocked(getDimensionList).mockResolvedValue({
    own: [OWN_DIMENSION],
    inherited: [INHERITED_DIMENSION]
  } as never)
  vi.mocked(getValueList).mockResolvedValue([{ id: 3, dimensionId: 1, name: '110', sort: 1 }] as never)

  const wrapper = mount(FilterDimensionDrawer, {
    props: { modelValue: true, categoryId: 114, categoryName: '蜡基' },
    global: {
      // el-drawer 默认 teleport 到 body，桩掉它才能直接在 wrapper 上断言
      stubs: { 'el-drawer': { template: '<div><slot /></div>' } }
    }
  })
  await flushPromises()
  return wrapper
}

describe('FilterDimensionDrawer', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('自有维度出现在「本分类自有」段里', async () => {
    const wrapper = await mountDrawer()

    const ownSection = wrapper.find('.filter-section--own')
    expect(ownSection.text()).toContain('宽度')
  })

  it('自有维度带改名与删除入口', async () => {
    const wrapper = await mountDrawer()

    const ownSection = wrapper.find('.filter-section--own')
    expect(ownSection.find('.filter-dimension-rename').exists()).toBe(true)
    expect(ownSection.find('.filter-dimension-delete').exists()).toBe(true)
  })

  it('继承来的维度出现在「继承自父分类」段里，并标明来源', async () => {
    const wrapper = await mountDrawer()

    const inheritedSection = wrapper.find('.filter-section--inherited')
    expect(inheritedSection.text()).toContain('长度')
    expect(inheritedSection.text()).toContain('继承自「碳带」')
  })

  /** ⚠️ 本文件的核心断言：继承段一个编辑入口都不能有 */
  it('继承来的维度**没有**任何改名/删除入口', async () => {
    const wrapper = await mountDrawer()

    const inheritedSection = wrapper.find('.filter-section--inherited')
    expect(inheritedSection.find('.filter-dimension-rename').exists()).toBe(false)
    expect(inheritedSection.find('.filter-dimension-delete').exists()).toBe(false)
    expect(inheritedSection.find('.filter-dimension-create').exists()).toBe(false)
    // 值的增删入口同样不能出现在继承段 —— 值也是跟着维度一起继承的
    expect(inheritedSection.find('.filter-value-create').exists()).toBe(false)
    expect(inheritedSection.find('.filter-value-tag.closable').exists()).toBe(false)
  })

  it('按分类拉取维度配置，且继承段也把可选值显示出来', async () => {
    const wrapper = await mountDrawer()

    expect(getDimensionList).toHaveBeenCalledWith(114)
    // 两段都取了值：不显示的话，运营看不到前台到底会渲染出什么
    expect(getValueList).toHaveBeenCalledWith(OWN_DIMENSION.id)
    expect(getValueList).toHaveBeenCalledWith(INHERITED_DIMENSION.id)
    expect(wrapper.find('.filter-section--inherited').text()).toContain('110')
  })
})

describe('FilterDimensionDrawer —— 新增维度的完整路径', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  /**
   * ⚠️ 这条是**回归测试**，针对 2026-10-11 线上那个 bug。
   *
   * 组件里曾写成 `const { message } = useMessage()`，而真实 `useMessage()` 返回的是
   * **一层直接的方法**（`success` / `error` / `warning`，没有 `message` 字段）——
   * 于是运营一点「新增维度」就 `Cannot read properties of undefined (reading 'success')`。
   *
   * 而当时 21 条测试全绿：因为既没人点这个按钮，mock 又按我**以为的**形状写。
   * 所以这条**必须真的把处理器跑一遍**，而不是只断言渲染结果。
   */
  it('点「新增维度」→ 调接口 + 弹成功提示，不会崩', async () => {
    vi.mocked(getDimensionList).mockResolvedValue({ own: [], inherited: [] } as never)
    vi.mocked(createDimension).mockResolvedValue(1 as never)
    const promptSpy = vi
      .spyOn(ElMessageBox, 'prompt')
      .mockResolvedValue({ value: '宽度', action: 'confirm' } as never)

    const wrapper = mount(FilterDimensionDrawer, {
      props: { modelValue: true, categoryId: 113, categoryName: '碳带' },
      global: { stubs: { 'el-drawer': { template: '<div><slot /></div>' } } }
    })
    await flushPromises()

    // ⚠️ **直接 await 处理器返回的 promise**，不要只 trigger('click')。
    // 起初我写的是 `trigger('click')` → 它**不等**这个 async 处理器，
    // 于是处理器里的异常变成一个"没人观察的 promise rejection"，
    // 测试照样全绿 —— 那个 bug 就是这么溜到线上的。await 它才会真的失败。
    await expect((wrapper.vm as unknown as { onCreateDimension: () => Promise<void> })
        .onCreateDimension()).resolves.toBeUndefined()

    expect(promptSpy).toHaveBeenCalled()
    expect(createDimension).toHaveBeenCalledWith(
      expect.objectContaining({ categoryId: 113, name: '宽度' })
    )
  })
})

describe('FilterDimensionDrawer —— 改名与排序的完整路径（2026-10-16 线上 bug）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  /**
   * ⚠️ **回归测试**，针对 2026-10-16 线上：**改名点不动**，前端只看到
   * `Uncaught (in promise) error`（排序同样坏，只是没人报）。
   *
   * 根因：`updateDimension` 的请求体少了 `categoryId`，而后端的
   * `ProductFilterDimensionSaveReqVO` 挂着 `@NotNull("归属分类不能为空")` ——
   * 请求**在进业务代码之前**就被参数校验拦掉了。
   * 讽刺的是后端 `updateDimension` **根本不读**这个字段（它自己的注释写着
   * "本接口不接受改分类"，重名校验用的是库里那条记录的分类）—— 一个后端不用的字段却强制要传。
   *
   * 与上面那条"新增"的回归测试同一个教训：**只断言渲染、不跑处理器 → 测试全绿**。
   * 所以这里 `await` 处理器返回的 promise，并**逐字段断言请求体**。
   */
  it('改名：请求体带上 categoryId，且不崩', async () => {
    vi.mocked(getDimensionList).mockResolvedValue({ own: [OWN_DIMENSION], inherited: [] } as never)
    vi.mocked(updateDimension).mockResolvedValue(true as never)
    const promptSpy = vi
      .spyOn(ElMessageBox, 'prompt')
      .mockResolvedValue({ value: '宽度（新）', action: 'confirm' } as never)

    const wrapper = mount(FilterDimensionDrawer, {
      props: { modelValue: true, categoryId: 114, categoryName: '蜡基' },
      global: { stubs: { 'el-drawer': { template: '<div><slot /></div>' } } }
    })
    await flushPromises()

    await expect(
      (
        wrapper.vm as unknown as {
          onRenameDimension: (d: typeof OWN_DIMENSION) => Promise<void>
        }
      ).onRenameDimension(OWN_DIMENSION)
    ).resolves.toBeUndefined()

    expect(promptSpy).toHaveBeenCalled()
    expect(updateDimension).toHaveBeenCalledWith({
      id: 21,
      categoryId: 114, // ← 漏了它就会被后端的参数校验拦掉（这正是本次的 bug）
      name: '宽度（新）',
      sort: 1
    })
  })

  it('排序：交换两项时**两次**请求都带上 categoryId，且不崩', async () => {
    const second = { id: 22, categoryId: 114, name: '厚度', sort: 2 }
    vi.mocked(getDimensionList).mockResolvedValue({
      own: [OWN_DIMENSION, second],
      inherited: []
    } as never)
    vi.mocked(updateDimension).mockResolvedValue(true as never)

    const wrapper = mount(FilterDimensionDrawer, {
      props: { modelValue: true, categoryId: 114, categoryName: '蜡基' },
      global: { stubs: { 'el-drawer': { template: '<div><slot /></div>' } } }
    })
    await flushPromises()

    await expect(
      (wrapper.vm as unknown as { onMoveDimension: (i: number, o: number) => Promise<void> })
        .onMoveDimension(0, 1)
    ).resolves.toBeUndefined()

    expect(updateDimension).toHaveBeenNthCalledWith(1, {
      id: 21,
      categoryId: 114,
      name: '宽度',
      sort: 2
    })
    expect(updateDimension).toHaveBeenNthCalledWith(2, {
      id: 22,
      categoryId: 114,
      name: '厚度',
      sort: 1
    })
  })
})
