import { describe, expect, it } from 'vitest'

import { diffRemovedFilterValues } from './filterTag'

/**
 * 「已移除 N 个不再适用的筛选标签」的唯一计算处（特性 003 / US14 / T274）。
 *
 * 背景（契约 contracts/admin-api.md §3.3）：服务端**不返回**剔除数量 ——
 * 那会要求为一个只有商品编辑页用的信息去改一个全局共用的返回结构。
 * 所以这句话由前端**对比保存前后的 `filterValueIds`** 得出，也就是本函数。
 *
 * 为什么要专门测它：商品改分类后标签被静默清理，是**没有报错**的数据变化。
 * 如果这句话算错或干脆不显示，运营会以为标签还在，直到顾客反馈"筛不出来"。
 */
describe('diffRemovedFilterValues', () => {
  it('改分类后被清掉的标签要能算出来', () => {
    // 原来挂着 3 个，改到「打印机」后只剩 1 个存活的
    expect(diffRemovedFilterValues([3, 7, 11], [7])).toEqual([3, 11])
  })

  it('全被清掉时返回全部原值', () => {
    expect(diffRemovedFilterValues([3, 7], [])).toEqual([3, 7])
  })

  it('没有任何变化时返回空数组（不要误报）', () => {
    expect(diffRemovedFilterValues([3, 7], [3, 7])).toEqual([])
  })

  it('顺序不影响判断', () => {
    expect(diffRemovedFilterValues([3, 7], [7, 3])).toEqual([])
  })

  it('保存前本来就没有标签时返回空', () => {
    expect(diffRemovedFilterValues([], [3])).toEqual([])
  })

  /**
   * ⚠️ 关键一条：**后端没返回这个字段时，不能宣称"移除了一堆"**。
   * 契约要求前端容忍字段缺失（旧版后端不返回它）—— 那时我们根本无从知道，
   * 正确做法是**不报**，而不是把"不知道"当成"全被删了"。
   */
  it('响应里没有 filterValueIds 时，不报任何移除', () => {
    expect(diffRemovedFilterValues([3, 7], undefined)).toEqual([])
    expect(diffRemovedFilterValues([3, 7], null)).toEqual([])
  })

  it('输入本身为空/未定义时不炸', () => {
    expect(diffRemovedFilterValues(undefined, [3])).toEqual([])
    expect(diffRemovedFilterValues(null, null)).toEqual([])
  })
})
