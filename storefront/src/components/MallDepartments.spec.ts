import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CategoryNode } from '@/types'
import MallDepartments from './MallDepartments.vue'

const pageProducts = vi.hoisted(() => vi.fn())
vi.mock('@/api/product', () => ({ pageProducts }))
enableAutoUnmount(afterEach)
const categories: CategoryNode[] = [{ id: 10, parentId: 0, name: '标签耗材', picUrl: '', children: [{ id: 11, parentId: 10, name: '热敏标签', picUrl: '', children: [] }] }, { id: 20, parentId: 0, name: '打印设备', picUrl: '', children: [] }]
const global = { stubs: { ProductCard: { props: ['spu'], template: '<div>{{ spu.name }}</div>' }, RouterLink: { template: '<a><slot /></a>' } } }
describe('商城品类分区', () => {
  it('各分类独立查询，包含子分类，并可进入具体品类', async () => {
    pageProducts.mockReset().mockImplementation(async params => ({ list: [{ id: params.categoryId, name: `商品 ${params.categoryId}` }], total: 100 }))
    const w = mount(MallDepartments, { props: { categories }, global })
    await flushPromises()
    expect(pageProducts).toHaveBeenCalledWith({ categoryId: 10, pageNo: 1, pageSize: 4 })
    expect(pageProducts).toHaveBeenCalledWith({ categoryId: 20, pageNo: 1, pageSize: 4 })
    expect(w.findAll('.mall-department')).toHaveLength(2)
    expect(w.text()).toContain('商品 20')
    await w.get('.mall-department-links button').trigger('click')
    expect(w.emitted('select')?.[0]).toEqual([11])
  })
  it('一个分区失败不影响其他商品，且可重试', async () => {
    pageProducts.mockReset().mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ list: [{ id: 20, name: '打印机' }], total: 1 })
    const w = mount(MallDepartments, { props: { categories }, global })
    await flushPromises()
    expect(w.text()).toContain('暂时加载失败')
    expect(w.text()).toContain('打印机')
    await w.get('.mall-department-empty button').trigger('click')
    await flushPromises()
    expect(w.text()).not.toContain('暂时加载失败')
  })
})

