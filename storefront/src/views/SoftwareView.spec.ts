import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SoftwareView from './SoftwareView.vue'

describe('软件介绍页的开发中展示', () => {
  it('突出主题并展示开发中状态，不提供未发布软件的试用与报价', () => {
    const view = mount(SoftwareView)
    expect(view.get('h1').text()).toBe('让标签设计极致简单')
    expect(view.get('.preview-announcement').text()).toContain('敬请期待')
    expect(view.get('.preview-announcement').text()).toContain('MaxLabel · 开发中')
    expect(view.findAll('.plan-card, .price-cards, a[href*="download"]')).toHaveLength(0)
    expect(view.text()).not.toMatch(/30 天|全平台|2,000|立即免费试用/)
  })
  it('三份涂料数据与生成标签中的产品、色号和批次一致', () => {
    const view = mount(SoftwareView)
    const labels = view.findAll('.data-labels .coating-label')
    const rows = view.findAll('tbody tr')
    expect(rows).toHaveLength(3)
    expect(labels).toHaveLength(3)
    rows.forEach((row, index) => {
      row.findAll('td').forEach((cell) => expect(labels[index]!.text()).toContain(cell.text()))
    })
    expect(view.findAll('.application-example img')).toHaveLength(3)
  })
})
