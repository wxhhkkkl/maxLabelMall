import { describe, expect, it } from 'vitest'

import type { FilterDimension } from '@/types/filter'
import { retainApplicableSelections } from './filter'

/**
 * 筛选状态在**切换分类**时的处置（特性 003 / US15 / T289 / FR-103）。
 *
 * 规则有两面，缺一不可：
 *   · **仍然适用的选择要留住** —— 「蜡基」选的「宽度=110」，「混合基」也继承同一个维度
 *     （Q10=B：维度在兄弟分类间是同一条），切过去没理由把它清掉；
 *   · **不再适用的要重置** —— 切到「打印机」后「宽度」根本不存在了，
 *     留着它就会带着一个前台无法解释的条件去查询（结果大概率是空的，而且不知道为什么）。
 */
const dimensions = (...specs: [number, string, number[]][]): FilterDimension[] =>
  specs.map(([dimensionId, dimensionName, valueIds]) => ({
    dimensionId,
    dimensionName,
    sort: 1,
    values: valueIds.map((valueId, i) => ({ valueId, name: `v${valueId}`, sort: i + 1 })),
  }))

describe('retainApplicableSelections', () => {
  it('仍在新分类维度里的选择被保留', () => {
    const next = dimensions([12, '宽度', [3, 4]])

    expect(retainApplicableSelections([3], next)).toEqual([3])
  })

  it('新分类下已经没有的选择被重置', () => {
    const next = dimensions([13, '类型', [9]])

    // 3 是旧分类「宽度」下的值，新分类里没有它
    expect(retainApplicableSelections([3], next)).toEqual([])
  })

  it('多个选择里只留适用的那些', () => {
    const next = dimensions([13, '类型', [9]])

    expect(retainApplicableSelections([3, 9], next)).toEqual([9])
  })

  it('新分类一个维度都没有时全部清掉', () => {
    expect(retainApplicableSelections([3, 9], [])).toEqual([])
  })

  it('本来就没选任何东西时返回空', () => {
    expect(retainApplicableSelections([], dimensions([12, '宽度', [3]]))).toEqual([])
  })

  it('维度下没有可选值时，它的值自然也不该被保留', () => {
    const next = dimensions([12, '宽度', []])

    expect(retainApplicableSelections([3], next)).toEqual([])
  })
})
