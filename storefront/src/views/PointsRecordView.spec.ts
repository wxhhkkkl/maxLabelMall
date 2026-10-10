import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

const pagePointRecords = vi.fn()
vi.mock('@/api/point', () => ({
  pagePointRecords: (...a: unknown[]) => pagePointRecords(...a),
}))
vi.mock('@/api/member', () => ({
  getMemberUser: vi.fn().mockResolvedValue({ id: 1, nickname: '张三', mobile: '13800008888' }),
  logout: vi.fn(),
}))

import PointsRecordView from './PointsRecordView.vue'
import { getMemberUser } from '@/api/member'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/account', component: { template: '<div/>' } },
    { path: '/account/address', component: { template: '<div/>' } },
    { path: '/account/after-sale', component: { template: '<div/>' } },
    { path: '/account/points', component: { template: '<div/>' } },
    { path: '/order', component: { template: '<div/>' } },
    { path: '/coupon', component: { template: '<div/>' } },
    { path: '/coupon/mine', component: { template: '<div/>' } },
  ],
})

/** 一条积分记录；时间字段是 **epoch 毫秒**（与线上一致） */
function record(over: Record<string, unknown> = {}) {
  return {
    id: 1,
    title: '下单赠送',
    description: '',
    point: 10,
    createTime: new Date(2026, 9, 9, 17, 30).getTime(),
    ...over,
  }
}

async function mountView() {
  const w = mount(PointsRecordView, { global: { plugins: [router, createPinia()] } })
  await flushPromises()
  return w
}

beforeEach(async () => {
  setActivePinia(createPinia())
  window.localStorage.clear()
  const { setTokens } = await import('@/utils/auth')
  setTokens('at-1', 'rt-1')
  pagePointRecords.mockReset()
  pagePointRecords.mockResolvedValue({ list: [record()], total: 1 })
  await router.push('/account/points')
})

/**
 * 积分明细（FR-011d / 澄清 Q1）。
 *
 * ⚠️ `point` 是**变动值**（正负即增减），不是余额 —— 界面上要能一眼看出是加还是减。
 */
describe('PointsRecordView —— 列表渲染', () => {
  it('概览读取会员余额与等级，不把当前页的积分变动当作余额', async () => {
    vi.mocked(getMemberUser).mockResolvedValueOnce({
      id: 1, nickname: '张三', mobile: '13800008888', point: 350, experience: 600,
      level: { id: 2, name: '黄金会员', level: 2 },
    })
    const w = await mountView()
    const overview = w.get('.pt-overview').text()
    expect(overview).toContain('350')
    expect(overview).toContain('600')
    expect(overview).toContain('黄金会员')
    expect(w.get('.pt-delta').text()).toBe('+10')
  })

  it('展示 时间 / 事由 / 变动值', async () => {
    const w = await mountView()
    expect(w.text()).toContain('2026-10-09 17:30')
    expect(w.text()).toContain('下单赠送')
    expect(w.text()).toContain('+10')
  })

  it('**减少的记录带负号**（不能都显示成正数）', async () => {
    pagePointRecords.mockResolvedValue({
      list: [record({ id: 2, title: '兑换商品', point: -20 })],
      total: 1,
    })
    const w = await mountView()
    expect(w.text()).toContain('-20')
  })

  it('分页：把 pageNo 传给接口，翻页后重拉', async () => {
    pagePointRecords.mockResolvedValue({ list: [record()], total: 50 })
    const w = await mountView()
    expect(pagePointRecords).toHaveBeenCalledWith({ pageNo: 1, pageSize: expect.any(Number) })

    await w.get('.pg-next').trigger('click')
    await flushPromises()
    expect(pagePointRecords.mock.calls.length).toBeGreaterThan(1)
  })
})

describe('PointsRecordView —— 空 / 错误态', () => {
  it('**没有记录时给空状态与解释**（租户 162 现状就是空 —— 但必须是"暂无"而不是无数据报错）', async () => {
    pagePointRecords.mockResolvedValue({ list: [], total: 0 })
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('暂无')
  })

  it('加载失败给可重试的错误态', async () => {
    pagePointRecords.mockRejectedValue(new Error('Network Error'))
    const w = await mountView()
    expect(w.find('.empty-state').exists()).toBe(true)
    expect(w.text()).toContain('重新加载')
  })
})

describe('PointsRecordView —— 共用侧栏（FR-011e）', () => {
  it('进入本页侧栏仍在，本页入口高亮', async () => {
    const w = await mountView()
    expect(w.find('.account-sidebar').exists()).toBe(true)
    expect(w.findAll('.s-item.active').map((a) => a.text().trim())).toEqual(['积分与等级'])
  })
})
