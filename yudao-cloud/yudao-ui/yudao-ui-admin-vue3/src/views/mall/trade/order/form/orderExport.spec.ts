import dayjs from 'dayjs'
import { describe, expect, it } from 'vitest'
import { buildExportQuickRange } from './orderExport'

/**
 * 订单导出的快捷时间范围。
 *
 * 三个快捷项都要**包含今天**：结束时间固定为「今天 23:59:59」，否则运营下午导出
 * 会漏掉当天的订单（2026-11-10 所有者要求）。
 */
describe('buildExportQuickRange', () => {
  const NOW = dayjs('2026-11-10 15:30:00')

  it('近 1 周：6 天前 00:00 → 今天 23:59:59（含今天共 7 天）', () => {
    expect(buildExportQuickRange('week', NOW)).toEqual([
      '2026-11-04 00:00:00',
      '2026-11-10 23:59:59'
    ])
  })

  it('近 1 个月：一个月前 00:00 → 今天 23:59:59', () => {
    expect(buildExportQuickRange('month', NOW)).toEqual([
      '2026-10-10 00:00:00',
      '2026-11-10 23:59:59'
    ])
  })

  it('近半年：六个月前 00:00 → 今天 23:59:59', () => {
    expect(buildExportQuickRange('halfYear', NOW)).toEqual([
      '2026-05-10 00:00:00',
      '2026-11-10 23:59:59'
    ])
  })
})
