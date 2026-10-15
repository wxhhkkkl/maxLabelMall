import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AboutView from './AboutView.vue'

describe('关于公司', () => {
  it('以公司全称为主角，介绍已提供的业务背景和团队', () => {
    const w = mount(AboutView)
    expect(w.get('h1').text()).toBe('北京御旺宸发科技有限公司')
    for (const text of ['软件开发', '网站建设', '移动应用开发', 'ERP', '人才培养', '以客户为中心']) expect(w.text()).toContain(text)
    expect(w.find('#company-introduction').exists()).toBe(true)
  })
  it('不再重复产品、商城和行业方案推广，不编造成立时间和规模', () => {
    const w = mount(AboutView)
    expect(w.findAll('a').map(a => a.attributes('href'))).toEqual(['#company-introduction'])
    expect(w.text()).not.toMatch(/一站配齐|选购|标签耗材|行业方案|\b(19|20)\d{2}\b|50,?000\+|99\.9%|20XX/)
    expect(w.get('#future-title').text()).toBe('立足当下，持续向前')
  })
})
