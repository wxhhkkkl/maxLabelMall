import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import Diagram from './CustomizationDiagram.vue'

describe('定制排版示意', () => {
  it.each([1, 2])('卷筒 %i 列铺满可用宽度，不因标签比例留下半边空白', (columns) => {
    const w = mount(Diagram, { props: { kind: 'layout', form: 1, columns, width: 10, height: 100 } })
    const labels = w.findAll('.layout-label').slice(0, columns)
    const first = labels[0]!
    const last = labels[columns - 1]!
    expect(Number(first.attributes('x'))).toBe(48)
    expect(Number(last.attributes('x')) + Number(last.attributes('width'))).toBe(232)
    expect(w.text()).not.toContain('210 × 297')
  })

  it('平张按 A4 与单枚毫米尺寸等比例绘制', () => {
    const w = mount(Diagram, { props: { kind: 'layout', form: 2, columns: 2, rows: 3, width: 60, height: 40 } })
    const label = w.get('.layout-label')
    expect(Number(label.attributes('width')) / Number(label.attributes('height'))).toBeCloseTo(1.5)
    expect(Number(label.attributes('width'))).toBeCloseTo(60 * 140 / 297)
    expect(w.text()).toContain('210 × 297 mm')
  })
})
