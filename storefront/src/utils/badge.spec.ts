import { describe, expect, it } from 'vitest'

import type { ProductSpu } from '@/types'

import { deriveBadges, HOT_LIMIT, NEW_LIMIT } from './badge'

function spu(id: number, salesCount: number): ProductSpu {
  return {
    id,
    name: `商品 ${id}`,
    introduction: '',
    categoryId: 1,
    picUrl: '',
    sliderPicUrls: [],
    specType: false,
    price: 100,
    marketPrice: 200,
    stock: 10,
    salesCount,
    deliveryTypes: [1],
  }
}

describe('deriveBadges —— 角标必须由真实数据算出', () => {
  it('「热销」是当前列表内销量最高的前 3 个', () => {
    const list = [spu(1, 5), spu(2, 100), spu(3, 50), spu(4, 80), spu(5, 1)]
    const b = deriveBadges(list)
    // 销量前 3：id 2(100)、4(80)、3(50)
    expect(b.hot.sort()).toEqual([2, 3, 4])
    expect(b.hot).toHaveLength(HOT_LIMIT)
  })

  it('「新品」是编号最大的若干（创建顺序的近似）', () => {
    const list = [spu(11, 0), spu(12, 0), spu(13, 0), spu(14, 0)]
    const b = deriveBadges(list)
    // 编号最大的是 14、13
    expect(b.fresh.sort()).toEqual([13, 14])
    expect(b.fresh).toHaveLength(NEW_LIMIT)
  })

  it('**永不产出「旗舰」与「订阅」** —— 后端无对应字段，也没有任何可推导依据', () => {
    const b = deriveBadges([spu(1, 9), spu(2, 8), spu(3, 7), spu(4, 6)])
    expect(Object.keys(b)).toEqual(['hot', 'fresh'])
    // 类型上就不存在这两个键
    expect('flagship' in b).toBe(false)
    expect('subscription' in b).toBe(false)
  })

  // ── 边界：不得铺满整页 ────────────────────────────────────────────
  it('销量全为 0 时不产出「热销」', () => {
    const b = deriveBadges([spu(1, 0), spu(2, 0), spu(3, 0), spu(4, 0), spu(5, 0)])
    expect(b.hot).toEqual([])
  })

  it('商品数少于名额时“有几件算几件”，不铺满', () => {
    const b = deriveBadges([spu(1, 3), spu(2, 2)])
    // 只有两件，热销就拿两件（不是硬凑到 3）
    expect(b.hot).toHaveLength(2)
    // 且两件都已是热销 → 按 FR-008b「热销优先」，新品为空（同一商品不得挂两个角标）
    expect(b.fresh).toEqual([])
  })

  it('只有一件商品时，它只挂一个角标', () => {
    const b = deriveBadges([spu(1, 5)])
    expect(b.hot).toEqual([1])
    expect(b.fresh).toEqual([])
  })

  it('空列表不产出任何角标', () => {
    expect(deriveBadges([])).toEqual({ hot: [], fresh: [] })
  })

  it('销量相同的情况下也不会把整页都标成热销（按 id 稳定排序取前 3）', () => {
    const list = Array.from({ length: 8 }, (_, i) => spu(i + 1, 7))
    expect(deriveBadges(list).hot).toHaveLength(HOT_LIMIT)
  })
})

describe('角标冲突 —— 同一商品不得同时挂两个（FR-008b）', () => {
  it('冲突时「热销」优先：新品里剔除已经是热销的', () => {
    // 4 件商品：销量前 3 为 1/2/3；编号最大为 4、3 → 3 冲突，应让给热销
    const list = [spu(1, 100), spu(2, 90), spu(3, 80), spu(4, 70)]
    const b = deriveBadges(list)
    expect(b.hot).toContain(3)
    expect(b.fresh).not.toContain(3)
    // 交集必须为空
    expect(b.hot.filter((id) => b.fresh.includes(id))).toEqual([])
  })

  it('任意列表下「热销」与「新品」都不相交', () => {
    const list = Array.from({ length: 12 }, (_, i) => spu(i + 1, i * 3))
    const b = deriveBadges(list)
    const overlap = b.hot.filter((id) => b.fresh.includes(id))
    expect(overlap).toEqual([])
  })
})

describe('作用域 —— 只作用于当前展示的那一页', () => {
  it('同一商品在别处页面（不同的列表分片）可以有不同的角标判定', () => {
    const page1 = [spu(1, 100), spu(2, 90), spu(3, 80)]
    const page2 = [spu(4, 10), spu(5, 20), spu(6, 30)]
    expect(deriveBadges(page1).hot).toEqual([1, 2, 3])
    expect(deriveBadges(page2).hot).toEqual([6, 5, 4])
  })
})
