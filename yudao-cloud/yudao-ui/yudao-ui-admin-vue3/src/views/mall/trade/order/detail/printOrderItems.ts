import type * as TradeOrderApi from '@/api/mall/trade/order'
import { formatDate } from '@/utils/formatTime'

/**
 * 订单详情「打印商品明细」（2026-11-18）
 *
 * 用途：运营/仓库按单拣货、随货发出。只打**商品与数量**（含规格），不打价格与售后状态 ——
 * 这张纸是给仓库和物流看的，不应带出成交价。
 *
 * 之所以生成独立 HTML 再 `print()`，而不是给当前页面加 `@media print`：
 * 管理端页面套着侧边栏/标签页/弹窗，隐藏工作量大且易漏；独立窗口的打印结果稳定可控。
 */

/** HTML 转义：商品名、地址来自商品库与客户填写，直接拼进打印页会有 XSS 风险 */
export function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function specText(item: TradeOrderApi.OrderItemRespVO): string {
  return (item.properties ?? [])
    .map((property) => `${property.propertyName}：${property.valueName}`)
    .join(' / ')
}

/** 生成打印页 HTML（纯函数，便于单测） */
export function buildOrderItemsPrintHtml(
  order: TradeOrderApi.OrderVO,
  now: Date = new Date()
): string {
  const items = order.items ?? []
  const rows = items
    .map((item, index) => {
      const spec = specText(item)
      return `<tr>
        <td class="idx">${index + 1}</td>
        <td>${escapeHtml(item.spuName)}${spec ? `<div class="spec">${escapeHtml(spec)}</div>` : ''}</td>
        <td class="num">${item.count ?? 0}</td>
      </tr>`
    })
    .join('')
  const totalCount = items.reduce((sum, item) => sum + (item.count ?? 0), 0)
  const address = `${order.receiverAreaName ?? ''} ${order.receiverDetailAddress ?? ''}`.trim()
  // 商家备注（2026-11-19 所有者要求）：拣货/随货要看得到「这单怎么发」；没填就不占一行
  const remark = String(order.remark ?? '').trim()

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8" />
<title>发货明细 ${escapeHtml(order.no)}</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 24px; color: #16233f; font: 14px/1.6 -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif; }
  h1 { margin: 0 0 16px; font-size: 22px; }
  .meta { margin: 2px 0; color: #3c4c68; }
  .meta b { color: #16233f; }
  table { width: 100%; margin-top: 16px; border-collapse: collapse; }
  th, td { border: 1px solid #b8c4d6; padding: 8px 10px; text-align: left; vertical-align: top; }
  th { background: #f2f6fc; font-weight: 600; }
  .idx { width: 40px; text-align: center; }
  .num { width: 72px; text-align: center; }
  .spec { margin-top: 2px; color: #6b7a92; font-size: 12px; }
  .foot { margin-top: 20px; color: #6b7a92; font-size: 12px; }
  @page { margin: 12mm; }
</style>
</head>
<body>
  <h1>发货明细</h1>
  <div class="meta"><b>订单号：</b>${escapeHtml(order.no)}　<b>下单时间：</b>${escapeHtml(formatDate(order.createTime))}</div>
  <div class="meta"><b>收货人：</b>${escapeHtml(order.receiverName)}　<b>电话：</b>${escapeHtml(order.receiverMobile)}</div>
  <div class="meta"><b>收货地址：</b>${escapeHtml(address)}</div>
  ${remark ? `<div class="meta"><b>商家备注：</b>${escapeHtml(remark)}</div>` : ''}
  <table>
    <thead>
      <tr><th class="idx">序号</th><th>商品</th><th class="num">数量</th></tr>
    </thead>
    <tbody>
      ${rows || '<tr><td colspan="3">本订单没有商品明细</td></tr>'}
    </tbody>
    <tfoot>
      <tr><td colspan="2">合计</td><td class="num">${totalCount}</td></tr>
    </tfoot>
  </table>
  <div class="foot">打印时间：${escapeHtml(formatDate(now))}</div>
</body>
</html>`
}

/** 打开打印窗口；被浏览器拦截时返回 false，由调用方提示 */
export function printOrderItems(order: TradeOrderApi.OrderVO): boolean {
  const win = window.open('', '_blank', 'width=900,height=650')
  if (!win) {
    return false
  }
  win.document.write(buildOrderItemsPrintHtml(order))
  win.document.close()
  win.focus()
  win.print()
  return true
}
