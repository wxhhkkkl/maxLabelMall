import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import MlRate from './MlRate.vue'

/**
 * C11 五星评分。两种模式共用一个组件：
 * - **只读**（评价列表里展示别人给的分数）—— 点不动；
 * - **可交互**（写评价时自己打分）—— 点了发 `update:modelValue`。
 *
 * 只读模式必须真的是"点不动"，不能只是样式看起来灰 —— 那样键盘与自动化仍能改到它。
 */
describe('MlRate —— 只读模式（展示）', () => {
  it('渲染 5 颗星，按分值填充', () => {
    const w = mount(MlRate, { props: { modelValue: 3, readonly: true } })
    const stars = w.findAll('.ml-star')
    expect(stars).toHaveLength(5)
    expect(w.findAll('.ml-star.is-on')).toHaveLength(3)
  })

  it('0 分时 5 颗全空（不留半颗之类的模糊态）', () => {
    const w = mount(MlRate, { props: { modelValue: 0, readonly: true } })
    expect(w.findAll('.ml-star.is-on')).toHaveLength(0)
  })

  it('**点不动**：只读时不发 update', async () => {
    const w = mount(MlRate, { props: { modelValue: 3, readonly: true } })
    await w.findAll('.ml-star')[4]?.trigger('click')
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })
})

describe('MlRate —— 可交互模式（打分）', () => {
  it('点第 4 颗发 4 分', async () => {
    const w = mount(MlRate, { props: { modelValue: 5 } })
    await w.findAll('.ml-star')[3]?.trigger('click')
    expect(w.emitted('update:modelValue')).toEqual([[4]])
  })

  it('点第 1 颗发 1 分（下界）', async () => {
    const w = mount(MlRate, { props: { modelValue: 5 } })
    await w.findAll('.ml-star')[0]?.trigger('click')
    expect(w.emitted('update:modelValue')).toEqual([[1]])
  })

  it('点第 5 颗发 5 分（上界）', async () => {
    const w = mount(MlRate, { props: { modelValue: 1 } })
    await w.findAll('.ml-star')[4]?.trigger('click')
    expect(w.emitted('update:modelValue')).toEqual([[5]])
  })
})
