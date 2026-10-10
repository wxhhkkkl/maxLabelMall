import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import AccountSidebar from './AccountSidebar.vue'

/**
 * 个人中心侧栏（FR-011e / SC-024）。
 *
 * ⚠️ 本次从「平铺 5 项」改成**按主题分三组** —— 这正是所有者说的"整理排布"：
 * 原先 5 项并列，看不出「资料 / 交易 / 权益」的结构。
 *
 * 高亮规则沿用既有约定（有子路由的项精确匹配），本次不动。
 */
const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/account', component: { template: '<div/>' } },
    { path: '/account/address', component: { template: '<div/>' } },
    { path: '/account/after-sale', component: { template: '<div/>' } },
    { path: '/account/points', component: { template: '<div/>' } },
    { path: '/order', component: { template: '<div/>' } },
    { path: '/order/:id', component: { template: '<div/>' } },
    { path: '/coupon', component: { template: '<div/>' } },
    { path: '/coupon/mine', component: { template: '<div/>' } },
  ],
})

async function mountAt(path: string) {
  await router.push(path)
  const w = mount(AccountSidebar, { global: { plugins: [router] } })
  await router.isReady()
  return w
}

/** 某一组下的入口文案（按组标题定位，断言归属关系而不是只看全量列表） */
function labelsOf(groupTitle: string, w: ReturnType<typeof mount>): string[] {
  const group = w.findAll('.s-group').find((g) => g.text().includes(groupTitle))
  return group ? group.findAll('.s-item').map((a) => a.text().trim()) : []
}

beforeEach(async () => {
  await router.push('/account')
})

describe('AccountSidebar —— 按主题分三组', () => {
  it('**渲染三个分组标题**：账户资料 / 我的交易 / 我的权益', async () => {
    const w = await mountAt('/account')
    const titles = w.findAll('.s-group-title').map((t) => t.text().trim())
    expect(titles).toEqual(['账户资料', '我的交易', '我的权益'])
  })

  it('**入口归属正确**（这是"整理排布"的实质）', async () => {
    const w = await mountAt('/account')
    expect(labelsOf('账户资料', w)).toEqual(['个人中心', '收货地址'])
    expect(labelsOf('我的交易', w)).toEqual(['我的订单', '我的售后'])
    expect(labelsOf('我的权益', w)).toEqual(['我的券', '领券中心', '积分与等级'])
  })

  it('**两个新入口都在**，且指向新的两条路由', async () => {
    const w = await mountAt('/account')
    const hrefs = w.findAll('.s-item').map((a) => a.attributes('href'))
    expect(hrefs).toContain('/account/after-sale')
    expect(hrefs).toContain('/account/points')
  })

  it('**进入子页后侧栏仍在**，且对应项高亮（SC-024）', async () => {
    for (const [path, label] of [
      ['/account/after-sale', '我的售后'],
      ['/account/points', '积分与等级'],
      ['/account/address', '收货地址'],
    ]) {
      const w = await mountAt(path)
      const active = w.findAll('.s-item.active').map((a) => a.text().trim())
      expect(active).toEqual([label])
    }
  })
})

describe('AccountSidebar —— 高亮规则（既有约定，本次不动）', () => {
  it('`/account` 精确匹配 —— 在 /account/address 时「个人中心」不该亮', async () => {
    const w = await mountAt('/account/address')
    const active = w.findAll('.s-item.active').map((a) => a.text().trim())
    expect(active).not.toContain('个人中心')
  })

  it('`/coupon` 精确匹配 —— 在 /coupon/mine 时「领券中心」不该亮', async () => {
    const w = await mountAt('/coupon/mine')
    const active = w.findAll('.s-item.active').map((a) => a.text().trim())
    expect(active).toEqual(['我的券'])
  })

  it('订单详情（/order/:id）仍让「我的订单」保持高亮', async () => {
    const w = await mountAt('/order/762')
    const active = w.findAll('.s-item.active').map((a) => a.text().trim())
    expect(active).toEqual(['我的订单'])
  })
})
