import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'

/**
 * 订单导出的快捷时间范围（2026-11-10）
 *
 * 「近 1 周 / 近 1 个月 / 近半年」都是**包含今天**的滚动窗口：
 * 从 N 天前的 00:00:00 到今天 23:59:59，避免运营选完还漏掉今天下午的订单。
 */
export type ExportQuickRangeKey = 'week' | 'month' | 'halfYear'

export const EXPORT_QUICK_RANGES: Array<{ key: ExportQuickRangeKey; label: string }> = [
  { key: 'week', label: '近 1 周' },
  { key: 'month', label: '近 1 个月' },
  { key: 'halfYear', label: '近半年' }
]

export function buildExportQuickRange(
  key: ExportQuickRangeKey,
  now: Dayjs = dayjs()
): [string, string] {
  const start =
    key === 'week'
      ? now.subtract(6, 'day').startOf('day')
      : key === 'month'
        ? now.subtract(1, 'month').startOf('day')
        : now.subtract(6, 'month').startOf('day')
  return [start.format('YYYY-MM-DD HH:mm:ss'), now.endOf('day').format('YYYY-MM-DD HH:mm:ss')]
}
