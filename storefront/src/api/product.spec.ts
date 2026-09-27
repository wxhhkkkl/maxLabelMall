import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ProductSpu } from '@/types'

const get = vi.fn()
vi.mock('@/config/http', () => ({
  get: (...args: unknown[]) => get(...args),
}))

const { getProductDetail, listProductsByIds, pageProducts, SORT_FIELD } = await import('./product')

function spu(over: Partial<ProductSpu> = {}): ProductSpu {
  return {
    id: 1,
    name: '三防热敏标签纸',
    introduction: '副标题',
    categoryId: 10,
    picUrl: 'http://x/a.png',
    sliderPicUrls: ['http://x/b.png'],
    specType: false,
    price: 1290,
    marketPrice: 1990,
    stock: 100,
    salesCount: 5,
    deliveryTypes: [1],
    ...over,
  }
}

beforeEach(() => {
  get.mockReset()
})

describe('SORT_FIELD —— 排序字段必须小驼峰', () => {
  // 这条是本项目一个真实的坑：后端有 @AssertTrue 白名单，
  // 传大写 SALES_COUNT / PRICE 会被判「排序字段不合法」。
  it('只有两个合法值，且均为小驼峰', () => {
    expect(SORT_FIELD.sales).toBe('salesCount')
    expect(SORT_FIELD.price).toBe('price')
    // 排除的是**全大写**写法（后端会判为「排序字段不合法」）；
    // 驼峰本身含大写字母（salesCount 的 C），所以不能简单地禁大写。
    expect(Object.values(SORT_FIELD)).not.toContain('SALES_COUNT')
    expect(Object.values(SORT_FIELD)).not.toContain('PRICE')
    // createTime 虽有同名常量，但**不在后端的白名单里**，同样不能用
    expect(Object.values(SORT_FIELD)).not.toContain('createTime')
  })
})

describe('pageProducts —— 商品分页检索', () => {
  it('综合排序：**不传 sortField**（后端默认按 sort DESC, id DESC）', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageProducts({ pageNo: 1, pageSize: 12 })
    const [, params] = get.mock.calls[0] as [string, Record<string, unknown>]
    expect(get.mock.calls[0]?.[0]).toBe('/product/spu/page')
    expect(params.sortField).toBeUndefined()
  })

  it('销量优先：sortField=salesCount 且降序', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageProducts({ pageNo: 1, pageSize: 12, sortField: 'salesCount', sortAsc: false })
    const [, params] = get.mock.calls[0] as [string, Record<string, unknown>]
    expect(params.sortField).toBe('salesCount')
    expect(params.sortAsc).toBe(false)
  })

  it('价格从低到高：sortField=price 且升序', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageProducts({ pageNo: 2, pageSize: 12, sortField: 'price', sortAsc: true })
    const [, params] = get.mock.calls[0] as [string, Record<string, unknown>]
    expect(params.sortField).toBe('price')
    expect(params.sortAsc).toBe(true)
    expect(params.pageNo).toBe(2)
  })

  it('关键词走**后端全量检索** —— keyword 原样传给后端，不是前端过滤当前页', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageProducts({ keyword: '碳带' })
    const [, params] = get.mock.calls[0] as [string, Record<string, unknown>]
    expect(params.keyword).toBe('碳带')
  })

  it('分类筛选传 categoryId（后端**不支持**价格区间与服务标签，不传这些维度）', async () => {
    get.mockResolvedValue({ list: [], total: 0 })
    await pageProducts({ categoryId: 10 })
    const [, params] = get.mock.calls[0] as [string, Record<string, unknown>]
    expect(params.categoryId).toBe(10)
    const keys = Object.keys(params)
    for (const forbidden of ['minPrice', 'maxPrice', 'priceRange', 'tags', 'service']) {
      expect(keys).not.toContain(forbidden)
    }
  })

  it('返回分页结果，total 供列表头部「共 N 件商品」使用', async () => {
    get.mockResolvedValue({ list: [spu()], total: 48 })
    const res = await pageProducts({})
    expect(res.total).toBe(48)
    expect(res.list).toHaveLength(1)
  })
})

describe('getProductDetail —— 字段缺失兜底', () => {
  it('调 /product/spu/get-detail 并带 id', async () => {
    get.mockResolvedValue(spu())
    await getProductDetail(7)
    expect(get.mock.calls[0]?.[0]).toBe('/product/spu/get-detail')
    expect((get.mock.calls[0]?.[1] as Record<string, unknown>).id).toBe(7)
  })

  // ⚠️ 只有 description 与 skus 是详情独有；introduction 与 sliderPicUrls
  //    在列表接口同样返回（AppProductSpuRespVO:19,28）。不得断言它们不存在。
  it('详情独有的是 description 与 skus（非 introduction / sliderPicUrls）', async () => {
    get.mockResolvedValue(spu({ description: '<p>详情</p>', skus: [] }))
    const d = await getProductDetail(1)
    expect(d.description).toBe('<p>详情</p>')
    expect(d.skus).toEqual([])
    // 这两个在列表里也有，详情里同样应有
    expect(d.introduction).toBe('副标题')
    expect(d.sliderPicUrls).toEqual(['http://x/b.png'])
  })

  it('缺 description / skus 时不抛错（规格类型字段可能是单规格）', async () => {
    get.mockResolvedValue(spu())
    const d = await getProductDetail(1)
    expect(d.description).toBeUndefined()
    expect(d.skus).toBeUndefined()
  })
})

describe('listProductsByIds', () => {
  it('按编号批量取，ids 以逗号连接', async () => {
    get.mockResolvedValue([spu({ id: 1 }), spu({ id: 2 })])
    const list = await listProductsByIds([1, 2])
    expect(get.mock.calls[0]?.[0]).toBe('/product/spu/list-by-ids')
    expect(get.mock.calls[0]?.[1]).toEqual({ ids: '1,2' })
    expect(list).toHaveLength(2)
  })
})
