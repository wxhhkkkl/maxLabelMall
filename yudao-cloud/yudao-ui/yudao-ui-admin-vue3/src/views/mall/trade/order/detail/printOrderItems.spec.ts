import { afterEach, describe, expect, it, vi } from 'vitest'

import { buildOrderItemsPrintHtml, escapeHtml, printOrderItems } from './printOrderItems'

/**
 * 订单详情「打印商品明细」（2026-11-18）
 *
 * 钉住打印内容的口径：随货/拣货用 → 只打订单号、收货信息、商家备注、商品（含规格）与数量，
 * **不打价格**；商品名/地址/备注都经过 HTML 转义，不能被当成标签执行。
 */
const order = {
  id: 1,
  no: 'NO-20261118-0001',
  createTime: new Date(2026, 10, 18, 10, 30, 0),
  receiverName: '张三',
  receiverMobile: '13800008888',
  receiverAreaName: '北京市 朝阳区',
  receiverDetailAddress: 'xx 路 1 号',
  items: [
    {
      id: 11,
      spuName: '热敏纸 100×50mm',
      count: 10,
      properties: [
        { propertyId: 1, propertyName: '规格', valueId: 11, valueName: '940枚/卷' }
      ]
    },
    { id: 12, spuName: '碳带 90mm', count: 3 }
  ]
} as any

afterEach(() => {
  vi.restoreAllMocks()
})

describe('buildOrderItemsPrintHtml', () => {
  it('带上订单号、收货信息、商品与规格、数量与合计', () => {
    const html = buildOrderItemsPrintHtml(order, new Date(2026, 10, 18, 11, 0, 0))

    expect(html).toContain('发货明细')
    expect(html).toContain('NO-20261118-0001')
    expect(html).toContain('张三')
    expect(html).toContain('13800008888')
    expect(html).toContain('北京市 朝阳区 xx 路 1 号')
    expect(html).toContain('热敏纸 100×50mm')
    expect(html).toContain('规格：940枚/卷')
    expect(html).toContain('碳带 90mm')
    // 数量列 + 合计 13
    expect(html).toContain('<td class="num">10</td>')
    expect(html).toContain('<td class="num">13</td>')
    expect(html).toContain('打印时间：2026-11-18 11:00:00')
  })

  it('**不带成交价**（这张纸随货发出，不能泄露价格）', () => {
    const html = buildOrderItemsPrintHtml(order)

    expect(html).not.toContain('¥')
    expect(html).not.toContain('金额')
    expect(html).not.toContain('单价')
  })

  it('商品名与地址做 HTML 转义', () => {
    const html = buildOrderItemsPrintHtml({
      ...order,
      receiverDetailAddress: '<script>alert(1)</script>',
      items: [{ id: 1, spuName: '<img src=x onerror=alert(1)>', count: 1 }]
    } as any)

    expect(html).not.toContain('<script>alert(1)</script>')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(html).not.toContain('<img src=x')
  })

  it('没有商品明细时给出明确文字，不渲染空表', () => {
    const html = buildOrderItemsPrintHtml({ ...order, items: [] } as any)

    expect(html).toContain('本订单没有商品明细')
  })

  it('有商家备注时打印出来并做 HTML 转义', () => {
    const html = buildOrderItemsPrintHtml({
      ...order,
      remark: '分两箱发 <b>顺丰</b>'
    } as any)

    expect(html).toContain('商家备注：')
    expect(html).toContain('分两箱发 &lt;b&gt;顺丰&lt;/b&gt;')
    expect(html).not.toContain('<b>顺丰</b>')
  })

  it('没有商家备注时不渲染该行', () => {
    expect(buildOrderItemsPrintHtml(order)).not.toContain('商家备注')

    const blank = buildOrderItemsPrintHtml({ ...order, remark: '   ' } as any)
    expect(blank).not.toContain('商家备注')
  })
})

describe('escapeHtml', () => {
  it('转义 & < > " \'', () => {
    expect(escapeHtml(`<a href="x">'&'</a>`)).toBe(
      '&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;'
    )
  })
})

describe('printOrderItems', () => {
  it('弹窗被拦截 → 返回 false，由调用方提示', () => {
    vi.spyOn(window, 'open').mockReturnValue(null)

    expect(printOrderItems(order)).toBe(false)
  })

  it('正常打开 → 写入内容并调用 print()', () => {
    const fakeWindow = {
      document: { write: vi.fn(), close: vi.fn() },
      focus: vi.fn(),
      print: vi.fn()
    }
    vi.spyOn(window, 'open').mockReturnValue(fakeWindow as any)

    expect(printOrderItems(order)).toBe(true)
    expect(fakeWindow.document.write).toHaveBeenCalledWith(expect.stringContaining('发货明细'))
    expect(fakeWindow.document.close).toHaveBeenCalled()
    expect(fakeWindow.print).toHaveBeenCalled()
  })
})
