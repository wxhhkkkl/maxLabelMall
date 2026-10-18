import { describe, expect, it } from 'vitest'

import { INVOICE_STATUS, INVOICE_STATUS_OPTIONS, canIssue, hasRefund, invoiceFileName } from './invoice'

/**
 * 开票申请的状态映射与两个判定（006 管理端 / T400）。
 *
 * ⚠️ 状态取值**必须与后端 `TradeOrderInvoiceStatusEnum` 逐字一致** ——
 * 前端算错一位，筛选下拉就会筛出空列表，而**不会报错**。
 */
describe('管理端 · 开票申请状态', () => {
  it('取值与后端枚举一致，文案不造同义词', () => {
    expect(INVOICE_STATUS.WAITING).toBe(0)
    expect(INVOICE_STATUS.ISSUED).toBe(10)
    expect(INVOICE_STATUS.CANCELED).toBe(20)
    expect(INVOICE_STATUS_OPTIONS.map((o) => o.label)).toEqual(['待开票', '已开票', '已作废'])
    // 筛选选项必须覆盖全部三个状态
    expect(INVOICE_STATUS_OPTIONS.map((o) => o.value)).toEqual([0, 10, 20])
  })

  it('订单退过款 → 要提示（部分退款时申请的金额与订单净额不再一致）', () => {
    // `amount` 是申请时的快照，部分退款**不会**改它（Q33）——
    // 运营开具前必须看得见"这笔已经退过一部分"
    expect(hasRefund({ orderRefundPrice: 3000 })).toBe(true)
    expect(hasRefund({ orderRefundPrice: 0 })).toBe(false)
    expect(hasRefund({})).toBe(false)
  })

  it('发票文件只显示短名（时间戳）—— 原始名又长又是 URL 编码的，看不懂', () => {
    // 线上真实形态：微信里存下来的图片，OSS key 里带着 URL 编码的原始文件名
    const url =
      'https://img.yuwangchenfa.com/20261012/%E5%BE%AE%E4%BF%A1%E5%9B%BE%E7%89%87_20261012143256_109_114.png'
    expect(invoiceFileName(url)).toBe('20261012143256')

    // 没带时间戳的文件名 → 原样（解码后）用最后一段，别把它显示成空
    expect(invoiceFileName('https://x.com/invoice/2026/abc.pdf')).toBe('abc.pdf')
    expect(invoiceFileName('')).toBe('')
    expect(invoiceFileName(null)).toBe('')
    // 带 query 的地址（预签名 URL 常见）也要能取对
    expect(invoiceFileName('https://x.com/a/invoice_20260101120000.pdf?sign=xyz')).toBe('20260101120000')
  })

  it('已作废的不能开具；待开票与已开票都可以（已开票 = 重新上传）', () => {
    expect(canIssue(INVOICE_STATUS.CANCELED)).toBe(false)
    expect(canIssue(INVOICE_STATUS.WAITING)).toBe(true)
    expect(canIssue(INVOICE_STATUS.ISSUED)).toBe(true)
  })
})
