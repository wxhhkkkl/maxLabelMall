import { mount, enableAutoUnmount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import PrintManagementView from './PrintManagementView.vue'
import { printManagementScreens } from '@/data/printManagement'

enableAutoUnmount(afterEach)
function mountPage() { return mount(PrintManagementView, { global: { stubs: { RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' } } } }) }

describe('打印管理系统介绍页', () => {
  it('切换功能页签同步更新截图、说明与无障碍选中状态', async () => {
    const w = mountPage()
    for (const [index, screen] of printManagementScreens.entries()) {
      await w.findAll('[role="tab"]')[index]!.trigger('click')
      expect(w.get('[role="tabpanel"] h3').text()).toBe(screen.title)
      expect(w.get('.pm-screenshot-button img').attributes('src')).toContain(screen.image)
      expect(w.findAll('[aria-selected="true"]')).toHaveLength(1)
      expect(w.get('[role="tabpanel"]').attributes('aria-labelledby')).toBe(`print-tab-${index}`)
    }
  })
  it('支持键盘切换，演示信息有明确说明，咨询链接可用', async () => {
    const w = mountPage()
    await w.findAll('[role="tab"]')[0]!.trigger('keydown', { key: 'End' })
    expect(w.get('[aria-selected="true"]').attributes('aria-label')).toBe('单码追溯')
    await w.findAll('[role="tab"]')[4]!.trigger('keydown', { key: 'ArrowRight' })
    expect(w.get('[aria-selected="true"]').attributes('aria-label')).toBe('码量看板')
    expect(w.get('.pm-preview-note').text()).toContain('演示示例')
    expect(w.get('.pm-contact a').attributes('href')).toBe('/support/contact')
  })
  it('上一个和下一个按钮循环切换，并更新核心特点', async () => {
    const w = mountPage()
    await w.get('button[aria-label="上一个功能"]').trigger('click')
    expect(w.get('[aria-selected="true"]').attributes('aria-label')).toBe('单码追溯')
    expect(w.get('.pm-feature-benefit').text()).toContain('有记录可核对')
    await w.get('button[aria-label="下一个功能"]').trigger('click')
    expect(w.get('[aria-selected="true"]').attributes('aria-label')).toBe('码量看板')
    expect(w.get('.pm-preview-toolbar').text()).toContain('01 / 05')
  })
})
