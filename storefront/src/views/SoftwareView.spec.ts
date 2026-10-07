import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import SoftwareView from './SoftwareView.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/templates', component: { template: '<div/>' } },
    { path: '/support', component: { template: '<div/>' } },
    { path: '/account', component: { template: '<div/>' } },
  ],
})

function mountView() {
  return mount(SoftwareView, { global: { plugins: [router] } })
}

describe('SoftwareView —— 版本价格区暂时下线', () => {
  /**
   * 三档价格（¥0 / ¥199/年 / ¥899/年）与能力声明（「200 次打印/月」「无限次打印」）
   * 都是**对外的报价与承诺**，业务方尚未确认。按所有者要求先把价格区换成一句
   * 「马上上线，敬请期待」—— 与其展示了再改，不如等软件真正上线。
   */
  it('不再渲染三档价格卡', () => {
    const w = mountView()
    expect(w.find('.price-cards').exists()).toBe(false)
    expect(w.findAll('.plan-card')).toHaveLength(0)
  })

  it('原位置改为「马上上线，敬请期待」', () => {
    expect(mountView().text()).toContain('马上上线，敬请期待')
  })

  it('页面其余部分保留（hero 与「软件能做什么」不动）', () => {
    const w = mountView()
    expect(w.find('.sw-hero').exists()).toBe(true)
    expect(w.text()).toContain('软件能做什么')
  })
})
