import { mount, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MallHome from './MallHome.vue'

enableAutoUnmount(afterEach)
afterEach(() => vi.useRealTimers())
function mountHome() {
  return mount(MallHome, { props: { banners: [], categories: [
    { id: 1, parentId: 0, name: '卷筒标签', picUrl: '', children: [{ id: 2, parentId: 1, name: '热敏标签', picUrl: '', children: [] }, { id: 3, parentId: 1, name: '铜版标签', picUrl: '', children: [] }] },
    { id: 4, parentId: 0, name: '打印机', picUrl: '', children: [] },
  ] }, global: { stubs: { MallDepartments: true, MallBanner: true, RouterLink: { template: '<a><slot /></a>' } } } })
}
describe('商城首页分类悬停', () => {
  it('显示关联二级分类，点击传出二级分类 ID 并收起面板', async () => {
    const w = mountHome()
    await w.findAll('.mall-directory-item')[0]!.trigger('mouseenter')
    expect(w.get('.mall-category-flyout').text()).toContain('热敏标签')
    expect(w.get('.mall-category-flyout').text()).not.toContain('全部卷筒标签')
    await w.findAll('.mall-category-flyout-links button')[1]!.trigger('click')
    expect(w.emitted('select')?.[0]).toEqual([3])
    expect(w.find('.mall-category-flyout').exists()).toBe(false)
  })
  it('鼠标从分类移入面板不会关闭，离开后延迟收起', async () => {
    vi.useFakeTimers()
    const w = mountHome()
    await w.findAll('.mall-directory-item')[0]!.trigger('mouseenter')
    await w.get('.mall-home-directory').trigger('mouseleave')
    await vi.advanceTimersByTimeAsync(200)
    await w.get('.mall-category-flyout').trigger('mouseenter')
    await vi.advanceTimersByTimeAsync(400)
    expect(w.find('.mall-category-flyout').exists()).toBe(true)
    await w.get('.mall-category-flyout').trigger('mouseleave')
    await vi.advanceTimersByTimeAsync(300)
    expect(w.find('.mall-category-flyout').exists()).toBe(false)
  })
  it('支持键盘打开和 Esc 关闭，无子分类时不显示空面板', async () => {
    const w = mountHome()
    await w.findAll('.mall-directory-item button')[0]!.trigger('focus')
    expect(w.find('.mall-category-flyout').exists()).toBe(true)
    await w.get('.mall-home-hero').trigger('keydown', { key: 'Escape' })
    expect(w.find('.mall-category-flyout').exists()).toBe(false)
    await w.findAll('.mall-directory-item')[1]!.trigger('mouseenter')
    expect(w.find('.mall-category-flyout').exists()).toBe(false)
  })
})
