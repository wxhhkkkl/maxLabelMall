import { describe, expect, it } from 'vitest'

import { parseReorderItems } from './reorder'

/**
 * 「再次购买」URL 参数解析。
 *
 * 结算页的三条来源里，只有它是**把用户给的数字直接拼进 query** 的，
 * 所以非法值必须在进结算请求前就被丢掉，而不是把 `NaN` 发给后端。
 */
describe('parseReorderItems —— skuIds / counts 两列按下标配对', () => {
  it('正常解析成 skuId + count', () => {
    expect(parseReorderItems('100,200', '2,3')).toEqual([
      { skuId: 100, count: 2 },
      { skuId: 200, count: 3 },
    ])
  })

  it('数量缺失 → 按 1 件处理（库存与限购仍由后端裁决）', () => {
    expect(parseReorderItems('100,200', '2')).toEqual([
      { skuId: 100, count: 2 },
      { skuId: 200, count: 1 },
    ])
  })

  it('非法 skuId（空、0、负数、小数、非数字）整条丢弃', () => {
    expect(parseReorderItems('100,,0,-1,1.5,abc,200', '1,2,3,4,5,6,7')).toEqual([
      { skuId: 100, count: 1 },
      { skuId: 200, count: 7 },
    ])
  })

  it('数量非法时回落为 1，而不是把该商品丢掉', () => {
    expect(parseReorderItems('100', '0')).toEqual([{ skuId: 100, count: 1 }])
    expect(parseReorderItems('100', 'abc')).toEqual([{ skuId: 100, count: 1 }])
  })

  it('参数缺失 → 空数组（结算页会停在错误态，而不是拿 skuId=0 去下单）', () => {
    expect(parseReorderItems(undefined, undefined)).toEqual([])
    expect(parseReorderItems('', '')).toEqual([])
  })
})
