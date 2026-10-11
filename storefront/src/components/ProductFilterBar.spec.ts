import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProductFilterBar from './ProductFilterBar.vue'
import type { FilterDimension } from '@/types/filter'

/**
 * 商城页筛选条（特性 003 / US15 / T290）。
 *
 * 这个组件**只由 props 决定渲染什么**，一条网络请求都不发 —— 于是它天然满足几条
 * 最容易在实现里跑偏的需求：
 *   · **选项集合稳定**（FR-111 / Q11=A）：不因为"当前选了什么"或"有没有商品"而增减；
 *   · **单选替换**（FR-099 / Q9=B）：点一个新值就是换掉那一行的旧值，不需要先取消；
 *   · **没有全局「清除筛选」按钮**（FR-114 / Q12）：清除靠每行行首的「全部」。
 */
const dimensions = (): FilterDimension[] => [
  { dimensionId: 12, dimensionName: '宽度', sort: 1, values: [
    { valueId: 3, name: '110', sort: 1 },
    { valueId: 4, name: '100', sort: 2 },
  ] },
  { dimensionId: 13, dimensionName: '长度', sort: 2, values: [
    { valueId: 5, name: '300m', sort: 1 },
    { valueId: 6, name: '70m', sort: 2 },
  ] },
]

function mountBar(selected: number[] = [], dims = dimensions()) {
  return mount(ProductFilterBar, {
    props: { dimensions: dims, selectedValueIds: selected },
  })
}

/** 读出每一行的文本，用于比对"选项集合"有没有变 */
function rowTexts(wrapper: ReturnType<typeof mountBar>): string[] {
  return wrapper.findAll('.filter-row').map((row) => row.text())
}

describe('ProductFilterBar —— 渲染', () => {
  it('每个维度一行，行首是「全部」', () => {
    const wrapper = mountBar()

    const rows = wrapper.findAll('.filter-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].find('.filter-row__all').text()).toContain('全部')
    expect(rows[0].text()).toContain('宽度')
    expect(rows[1].text()).toContain('长度')
  })

  it('没有任何维度时整块不渲染（不留空白块，FR-104）', () => {
    const wrapper = mountBar([], [])

    expect(wrapper.find('.product-filter-bar').exists()).toBe(false)
  })

  it('某个维度下没有可选值时，那一行整行不出现（FR-090）', () => {
    const wrapper = mountBar([], [
      { dimensionId: 12, dimensionName: '宽度', sort: 1, values: [] },
      { dimensionId: 13, dimensionName: '长度', sort: 2, values: [{ valueId: 5, name: '300m', sort: 1 }] },
    ])

    expect(wrapper.findAll('.filter-row')).toHaveLength(1)
    expect(wrapper.text()).not.toContain('宽度')
  })

  it('值按 sort 排，不依赖接口给的顺序', () => {
    const wrapper = mountBar([], [
      { dimensionId: 12, dimensionName: '宽度', sort: 1, values: [
        { valueId: 3, name: '110', sort: 2 },
        { valueId: 4, name: '100', sort: 1 },
      ] },
    ])

    const names = wrapper.findAll('.filter-row .filter-value').map((v) => v.text())
    expect(names).toEqual(['100', '110'])
  })

  it('页面上**没有**全局的「清除筛选」按钮（FR-114）', () => {
    const wrapper = mountBar([3])

    expect(wrapper.text()).not.toContain('清除筛选')
    expect(wrapper.findAll('button').filter((b) => b.text().includes('清除'))).toHaveLength(0)
  })
})

describe('ProductFilterBar —— 选项集合必须稳定（FR-111 / Q11=A）', () => {
  it('选中了某个值之后，同一行的其它值照常展示', () => {
    const wrapper = mountBar([3])

    expect(rowTexts(wrapper)).toEqual(rowTexts(mountBar([])))
  })

  it('换个选中值，选项集合逐字不变', () => {
    const withOne = rowTexts(mountBar([3]))
    const withAnother = rowTexts(mountBar([4]))
    const withTwoRows = rowTexts(mountBar([3, 5]))

    expect(withAnother).toEqual(withOne)
    expect(withTwoRows).toEqual(withOne)
  })
})

describe('ProductFilterBar —— 单选语义（FR-099 / Q9=B）', () => {
  it('点一个值就把它选上', async () => {
    const wrapper = mountBar([])

    await wrapper.findAll('.filter-row')[0].findAll('.filter-value')[0].trigger('click')

    expect(wrapper.emitted('update:selectedValueIds')?.[0]).toEqual([[3]])
  })

  it('该行已有选中时，点另一个值是**替换**（不是叠加，也不用先取消）', async () => {
    const wrapper = mountBar([3])

    await wrapper.findAll('.filter-row')[0].findAll('.filter-value')[1].trigger('click')

    expect(wrapper.emitted('update:selectedValueIds')?.[0]).toEqual([[4]])
  })

  it('选中态只在被点的那一行变化，别的行不受影响', async () => {
    const wrapper = mountBar([3, 5])

    await wrapper.findAll('.filter-row')[1].findAll('.filter-value')[1].trigger('click')

    expect(wrapper.emitted('update:selectedValueIds')?.[0]).toEqual([[3, 6]])
  })

  it('点「全部」清掉该行——且**只**清该行', async () => {
    const wrapper = mountBar([3, 5])

    await wrapper.findAll('.filter-row')[0].find('.filter-row__all').trigger('click')

    expect(wrapper.emitted('update:selectedValueIds')?.[0]).toEqual([[5]])
  })

  it('该行没选中时「全部」是高亮的', () => {
    const wrapper = mountBar([3])

    const rows = wrapper.findAll('.filter-row')
    expect(rows[0].find('.filter-row__all').classes()).not.toContain('active')
    expect(rows[1].find('.filter-row__all').classes()).toContain('active')
  })
})
