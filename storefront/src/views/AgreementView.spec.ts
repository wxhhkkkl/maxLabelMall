import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import AgreementView from './AgreementView.vue'

/** 协议页 MUST 不依赖任何接口（FR-051 / SC-016）—— 从模块层面禁止它引入 api */
vi.mock('@/api/member', () => {
  throw new Error('协议页不得引入任何 api 模块')
})

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/agreement/user', component: { template: '<div/>' } },
    { path: '/agreement/privacy', component: { template: '<div/>' } },
  ],
})

function mountDoc(kind: 'user' | 'privacy') {
  return mount(AgreementView, { props: { kind }, global: { plugins: [router] } })
}

describe('AgreementView —— 纯静态内容页', () => {
  it('用户协议：渲染标题与生效日期', () => {
    const w = mountDoc('user')
    expect(w.text()).toContain('用户协议')
    expect(w.text()).toContain('生效日期')
  })

  it('隐私政策：渲染标题、生效日期与**四项必述要点**（FR-052）', () => {
    const w = mountDoc('privacy')
    expect(w.text()).toContain('隐私政策')
    const text = w.text()
    expect(text).toContain('手机号')
    expect(text).toContain('使用目的')
    expect(text).toContain('保存')
    expect(text).toContain('权利')
  })

  it('两种内容是**同一个组件**的两种呈现（不是两个页面文件）', () => {
    const user = mountDoc('user')
    const privacy = mountDoc('privacy')
    expect(user.text()).not.toBe(privacy.text())
    // 隐私政策页才有的要点清单
    expect(user.find('.agr-list').exists()).toBe(false)
    expect(privacy.find('.agr-list').exists()).toBe(true)
  })

  it('**正文是占位**：不代拟条款，且带占位标记（FR-054）', () => {
    const w = mountDoc('privacy')
    expect(w.find('.agr-pending [data-content-pending]').exists()).toBe(true)
  })

  it('正文由业务方提供 —— 页面自己不含任何写死的条款段落', () => {
    const w = mountDoc('user')
    // 不该出现"第一条""第二条"这类法务措辞
    expect(w.text()).not.toMatch(/第[一二三四五六七八九十]条/)
  })
})
