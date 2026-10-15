import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { CategoryNode } from '@/types'
import MallHome from './MallHome.vue'

enableAutoUnmount(afterEach)
// 品类分区自己发商品请求，与左侧目录无关 —— 桩掉（它有自己的用例）
const global = { stubs: { MallBanner: true, MallDepartments: true, RouterLink: { template: '<a><slot /></a>' } } }
const categories: CategoryNode[] = [{ id: 101, parentId: 0, name: '卷筒标签', picUrl: '', children: [{ id: 102, parentId: 101, name: '经济热敏', picUrl: '', children: [] }, { id: 103, parentId: 101, name: '优等热敏', picUrl: '', children: [] }] }, { id: 113, parentId: 0, name: '碳带', picUrl: '', children: [] }]

function mountHome() {
  return mount(MallHome, { props: { categories, banners: [] }, global })
}
/** 目录里第 index 个一级分类按钮（第 0 个是「全部商品分类」，不属于 category） */
function rootButton(w: ReturnType<typeof mountHome>, index: number) {
  return w.findAll('.mall-home-directory > button')[index + 1]!
}
function submenuItems(w: ReturnType<typeof mountHome>) {
  return w.get('.mall-home-submenu').findAll('button')
}

describe('商城首页左侧分类目录', () => {
  it('悬停一级分类展开它的二级分类，点击二级分类进入该分类', async () => {
    const w = mountHome()
    expect(w.find('.mall-home-submenu').exists()).toBe(false)
    await rootButton(w, 0).trigger('mouseenter')
    expect(submenuItems(w).map(b => b.text())).toEqual(['经济热敏', '优等热敏'])
    await submenuItems(w)[1]!.trigger('click')
    expect(w.emitted('select')?.[0]).toEqual([103])
  })

  it('悬停到没有二级分类的一级分类上，浮层收起（不留上一个分类的内容）', async () => {
    const w = mountHome()
    await rootButton(w, 0).trigger('mouseenter')
    await rootButton(w, 1).trigger('mouseenter')
    expect(w.find('.mall-home-submenu').exists()).toBe(false)
  })

  it('鼠标移出目录区后浮层收起', async () => {
    const w = mountHome()
    await rootButton(w, 0).trigger('mouseenter')
    expect(w.find('.mall-home-submenu').exists()).toBe(true)
    await w.get('.mall-home-directory-wrap').trigger('mouseleave')
    expect(w.find('.mall-home-submenu').exists()).toBe(false)
  })

  it('键盘聚焦一级分类同样能展开并进入二级分类', async () => {
    const w = mountHome()
    await rootButton(w, 0).trigger('focus')
    await submenuItems(w)[0]!.trigger('click')
    expect(w.emitted('select')?.[0]).toEqual([102])
  })

  it('点击一级分类本身仍进入该一级分类', async () => {
    const w = mountHome()
    await rootButton(w, 0).trigger('click')
    expect(w.emitted('select')?.[0]).toEqual([101])
  })
})
